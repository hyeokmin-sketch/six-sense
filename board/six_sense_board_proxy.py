"""SIX-SENSE 보드용 중계 서버 v2 (보드에서 실행).

  글자(대시보드 입력)  ─┐
                        ├─> [치환 사전 + system 문장] ─> 마음AI FastAPI 서버(:8080, NPU 양자화 모델)
  음성(보드 마이크) ────┘          │
                                    └─> 음성 명령은 '이벤트'로 쌓아 두고, 대시보드가 1초마다 가져간다

엔드포인트
  POST /v1/chat/completions   대시보드 글자 입력 (OpenAI 형식, 기존과 같음)
  POST /voice/utterance       보드 음성 서비스가 STT 글자를 넣는다 -> 사전 + 모델 -> 이벤트 저장
  GET  /voice/events?after=N  대시보드가 새 음성 이벤트를 가져간다 (peek=1 이면 최신 번호만)
  POST /voice/reply           대시보드가 화면에 보여 준 안내 문장을 알려 준다 (보드가 그대로 말한다)
  GET  /voice/reply?id=N&wait=S  음성 서비스가 안내 문장을 기다린다
  GET  /health

파이썬 기본 라이브러리만 사용. 마음AI 코드는 수정하지 않는다.
실행:  python3 six_sense_board_proxy.py [--port 8000] [--no-dict]
"""
import argparse, importlib.util, json, os, threading, time, urllib.parse, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SYSTEM = '당신은 스마트홈 제어 비서입니다. 사용자의 사투리와 모호한 표현을 이해해 기능 호출 형식으로만 답하세요.'
HERE = os.path.dirname(os.path.abspath(__file__))
DASH_ALIVE_S = 3.0          # 대시보드가 이 시간 안에 가져갔으면 '연결됨'으로 본다
KEEP_EVENTS = 100


def load_dictionary():
    spec = importlib.util.spec_from_file_location('apply_substitution', os.path.join(HERE, 'scripts', 'apply_substitution.py'))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.apply


class Voice:
    """음성 이벤트 저장소 (메모리). 대시보드와 음성 서비스가 이걸로 만난다."""
    def __init__(self):
        self.cv = threading.Condition()
        self.events = []
        self.next_id = 1
        self.replies = {}
        self.last_poll = 0.0

    def add(self, ev):
        with self.cv:
            ev['id'] = self.next_id
            self.next_id += 1
            ev['ts'] = time.time()
            self.events.append(ev)
            del self.events[:-KEEP_EVENTS]
            self.cv.notify_all()
            return ev

    def since(self, after):
        with self.cv:
            self.last_poll = time.time()
            return [e for e in self.events if e['id'] > after], self.next_id - 1

    def dashboard_alive(self):
        return time.time() - self.last_poll < DASH_ALIVE_S

    def set_reply(self, eid, text):
        with self.cv:
            self.replies[eid] = text
            for k in [k for k in self.replies if k < eid - KEEP_EVENTS]:
                del self.replies[k]
            self.cv.notify_all()

    def wait_reply(self, eid, wait):
        end = time.time() + wait
        with self.cv:
            while eid not in self.replies:
                left = end - time.time()
                if left <= 0:
                    return None
                self.cv.wait(left)
            return self.replies[eid]


def make_handler(llm_url, dict_apply):
    voice = Voice()

    def pipeline(user, system=SYSTEM, max_tokens=80):
        """사전 -> (system + 글자) -> 모델. 반환: (사전 적용 글자, 모델 답, 사전 ms, 모델 ms)"""
        t0 = time.time()
        fixed = dict_apply(user) if dict_apply else user
        t1 = time.time()
        payload = {'model': 'six-sense', 'max_tokens': max_tokens, 'stream': False,
                   'messages': [{'role': 'system', 'content': system}, {'role': 'user', 'content': fixed}]}
        r = urllib.request.Request(llm_url, data=json.dumps(payload, ensure_ascii=False).encode('utf-8'),
                                   headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(r, timeout=30) as resp:
            out = json.loads(resp.read().decode('utf-8'))
        text = out['choices'][0]['message']['content'].strip()
        t2 = time.time()
        return fixed, text, round((t1 - t0) * 1000, 1), round((t2 - t1) * 1000)

    class H(BaseHTTPRequestHandler):
        def _send(self, code, obj=None):
            body = json.dumps(obj, ensure_ascii=False).encode('utf-8') if obj is not None else b''
            self.send_response(code)
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
            self.send_header('Access-Control-Allow-Headers', 'Content-Type')
            if body:
                self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _json(self):
            return json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')

        def do_OPTIONS(self):
            self._send(204)

        def do_GET(self):
            u = urllib.parse.urlparse(self.path)
            q = {k: v[0] for k, v in urllib.parse.parse_qs(u.query).items()}
            if u.path == '/health':
                return self._send(200, {'ok': True, 'llm': llm_url, 'dict': dict_apply is not None,
                                        'dashboard': voice.dashboard_alive(), 'latest': voice.next_id - 1})
            if u.path == '/voice/events':
                evs, latest = voice.since(int(q.get('after', -1)))
                return self._send(200, {'latest': latest, 'events': [] if q.get('peek') else evs})
            if u.path == '/voice/reply':
                text = voice.wait_reply(int(q.get('id', 0)), min(float(q.get('wait', 0)), 10))
                return self._send(200, {'id': int(q.get('id', 0)), 'text': text})
            self._send(404, {'error': 'not found'})

        def do_POST(self):
            try:
                req = self._json()
            except Exception:
                return self._send(400, {'error': 'bad json'})
            if self.path == '/v1/chat/completions':
                msgs = req.get('messages', [])
                system = next((m['content'] for m in msgs if m.get('role') == 'system' and m.get('content')), SYSTEM)
                user = next((m['content'] for m in reversed(msgs) if m.get('role') == 'user'), '')
                t0 = time.time()
                try:
                    fixed, text, dict_ms, llm_ms = pipeline(user, system, req.get('max_tokens', 80))
                except Exception as e:
                    print(f'[오류] LLM 서버 호출 실패: {type(e).__name__}: {e}', flush=True)
                    return self._send(502, {'error': f'LLM 서버 호출 실패: {type(e).__name__}: {e}'})
                shown = f'"{user}"' if fixed == user else f'"{user}" => 사전 적용 "{fixed}"'
                print(f'[{time.time() - t0:.2f}s] {shown} -> {text}', flush=True)
                return self._send(200, {'object': 'chat.completion', 'model': req.get('model', 'six-sense'),
                                        'choices': [{'index': 0, 'message': {'role': 'assistant', 'content': text}, 'finish_reason': 'stop'}],
                                        'six_sense': {'original': user, 'corrected': fixed, 'dict_used': dict_apply is not None,
                                                      'dict_ms': dict_ms, 'llm_ms': llm_ms}})
            if self.path == '/voice/utterance':
                user = str(req.get('text', '')).strip()
                if not user:
                    return self._send(400, {'error': 'empty text'})
                try:
                    fixed, text, dict_ms, llm_ms = pipeline(user)
                except Exception as e:
                    print(f'[오류] 음성 명령 처리 실패: {type(e).__name__}: {e}', flush=True)
                    return self._send(502, {'error': f'{type(e).__name__}: {e}'})
                stt_ms = int(req.get('stt_ms', 0))
                ev = voice.add({'original': user, 'corrected': fixed, 'content': text, 'dict_used': dict_apply is not None,
                                'dict_ms': dict_ms, 'llm_ms': llm_ms, 'stt_ms': stt_ms,
                                'total_ms': stt_ms + int(dict_ms) + llm_ms, 'source': 'voice'})
                shown = f'"{user}"' if fixed == user else f'"{user}" => 사전 적용 "{fixed}"'
                print(f'[음성 #{ev["id"]}] {shown} -> {text}  (인식 {stt_ms}ms, 모델 {llm_ms}ms)', flush=True)
                return self._send(200, dict(ev, dashboard=voice.dashboard_alive()))
            if self.path == '/voice/reply':
                voice.set_reply(int(req.get('id', 0)), str(req.get('text', '')))
                return self._send(200, {'ok': True})
            self._send(404, {'error': 'not found'})

        def log_message(self, *a):
            pass
    return H


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--port', type=int, default=8000)
    ap.add_argument('--llm', default='http://127.0.0.1:8080/v1/chat/completions', help='마음AI FastAPI 서버 주소')
    ap.add_argument('--no-dict', action='store_true')
    a = ap.parse_args()
    d = None if a.no_dict else load_dictionary()
    print('치환 사전:', '사용 안 함' if a.no_dict else '적용')
    print(f'중계 서버 시작: 0.0.0.0:{a.port}  ->  {a.llm}', flush=True)
    ThreadingHTTPServer(('0.0.0.0', a.port), make_handler(a.llm, d)).serve_forever()
