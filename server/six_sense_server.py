"""SIX-SENSE 모델 서버: 병합 모델(merged-llama-v2dict3)을 virtual-home 대시보드가 부를 수 있게 한다.
OpenAI 호환 /v1/chat/completions 한 개만 제공한다. (대시보드 engine.js의 callLlm 형식)

실행 (Mac/PC):
  pip install transformers torch accelerate   (서버는 파이썬 기본 라이브러리만 사용)
  python server/six_sense_server.py --model models/merged-llama-v2dict3 --port 8000   (--no-dict 를 붙이면 치환 사전 끔)
그 다음 virtual-home/js/config.js 에서
  llmEndpoint: "http://localhost:8000/v1/chat/completions"
"""
import argparse, importlib.util, json, os, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SYSTEM = '당신은 스마트홈 제어 비서입니다. 사용자의 사투리와 모호한 표현을 이해해 기능 호출 형식으로만 답하세요.'
DATE_TRAIN = '07 Nov 2026'      # 학습 때 날짜 줄(Llama 템플릿 Today Date)과 맞춘다
EOT = 128009                    # <|eot_id|>
MAX_NEW = 80                    # 학습·평가 때와 같은 값


def load_dictionary():
    """scripts/apply_substitution.py 의 apply()를 불러온다. (STT 글자 -> 치환 사전 -> LLM)"""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'scripts', 'apply_substitution.py')
    spec = importlib.util.spec_from_file_location('apply_substitution', path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.apply


def load_generator(model_path):
    import torch
    from transformers import AutoTokenizer, AutoModelForCausalLM
    dev = 'cuda' if torch.cuda.is_available() else ('mps' if torch.backends.mps.is_available() else 'cpu')
    dtype = torch.float32 if dev == 'cpu' else torch.float16
    tok = AutoTokenizer.from_pretrained(model_path)
    model = AutoModelForCausalLM.from_pretrained(model_path, torch_dtype=dtype).to(dev).eval()
    print(f'모델 로드 완료: {model_path} | device={dev} dtype={dtype}')

    def generate(system, user):
        prompt = tok.apply_chat_template([{'role': 'system', 'content': system}, {'role': 'user', 'content': user}],
                                         tokenize=False, add_generation_prompt=True, date_string=DATE_TRAIN)
        ids = tok(prompt, return_tensors='pt', add_special_tokens=False).to(dev)   # 템플릿에 <|begin_of_text|>가 이미 있음
        with torch.no_grad():
            out = model.generate(**ids, max_new_tokens=MAX_NEW, do_sample=False, eos_token_id=EOT,
                                 pad_token_id=tok.pad_token_id or EOT)
        return tok.decode(out[0][ids['input_ids'].shape[1]:], skip_special_tokens=True).strip()
    return generate


def make_handler(generate, dict_apply=None):
    class H(BaseHTTPRequestHandler):
        def _send(self, code, obj=None):
            body = json.dumps(obj, ensure_ascii=False).encode('utf-8') if obj is not None else b''
            self.send_response(code)
            self.send_header('Access-Control-Allow-Origin', '*')          # file:// 로 연 대시보드에서도 호출 가능
            self.send_header('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
            self.send_header('Access-Control-Allow-Headers', 'Content-Type')
            if body:
                self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_OPTIONS(self):
            self._send(204)

        def do_GET(self):
            self._send(200, {'ok': True}) if self.path == '/health' else self._send(404, {'error': 'not found'})

        def do_POST(self):
            if self.path != '/v1/chat/completions':
                return self._send(404, {'error': 'not found'})
            req = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
            msgs = req.get('messages', [])
            system = next((m['content'] for m in msgs if m.get('role') == 'system' and m.get('content')), SYSTEM)
            user = next((m['content'] for m in reversed(msgs) if m.get('role') == 'user'), '')
            t0 = time.time()
            fixed = dict_apply(user) if dict_apply else user          # 치환 사전 적용 (없으면 그대로)
            t1 = time.time()
            text = generate(system, fixed)
            t2 = time.time()
            shown = f'"{user}"' if fixed == user else f'"{user}" => 사전 적용 "{fixed}"'
            print(f'[{time.time()-t0:.2f}s] {shown} -> {text}', flush=True)
            self._send(200, {'object': 'chat.completion', 'model': req.get('model', 'six-sense'),
                             'choices': [{'index': 0, 'message': {'role': 'assistant', 'content': text}, 'finish_reason': 'stop'}],
                             # 대시보드 처리 과정 패널용 부가 정보 (OpenAI 형식에는 영향 없음)
                             'six_sense': {'original': user, 'corrected': fixed, 'dict_used': dict_apply is not None,
                                           'dict_ms': round((t1 - t0) * 1000, 1), 'llm_ms': round((t2 - t1) * 1000)}})

        def log_message(self, *a):
            pass
    return H


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--model', required=True, help='병합 모델 폴더 (merged-llama-v2dict3)')
    ap.add_argument('--port', type=int, default=8000)
    ap.add_argument('--no-dict', action='store_true', help='치환 사전을 적용하지 않음 (기본: 적용)')
    a = ap.parse_args()
    gen = load_generator(a.model)
    dict_apply = None if a.no_dict else load_dictionary()
    print('치환 사전:', '사용 안 함' if a.no_dict else '적용 (data/substitution_dict.json)')
    print(f'서버 시작: http://localhost:{a.port}/v1/chat/completions')
    ThreadingHTTPServer(('0.0.0.0', a.port), make_handler(gen, dict_apply)).serve_forever()
