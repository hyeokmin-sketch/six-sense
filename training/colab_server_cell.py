# ============================================================
# Colab 셀: 지금 Colab에 올라온 모델을 대시보드(Mac, VS Code)가 부를 수 있게 서버로 연다.
# 실행 조건: 1~5번 셀(설정·모델 불러오기) 실행 후, 변수 model, tok, SYSTEM 이 있는 상태.
#   · RUN_TRAIN=False, MODEL_KEY='llama', VERSION='v2dict3' (어댑터를 불러온 모델 그대로 사용 가능, 15번 병합 셀은 안 돌려도 됨)
# 이 셀은 실행하면 끝나지 않고 서버가 계속 돕니다. 시연이 끝날 때까지 이 셀을 멈추지 마세요.
# ============================================================
import json, time, subprocess, threading, re, functools, os, torch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

EOT, MAX_NEW, DATE_TRAIN = 128009, 80, '07 Nov 2026'    # 학습·평가 때와 같은 값
model.eval()

def generate(system, user):
    prompt = tok.apply_chat_template([{'role': 'system', 'content': system}, {'role': 'user', 'content': user}],
                                     tokenize=False, add_generation_prompt=True, date_string=DATE_TRAIN)
    ids = tok(prompt, return_tensors='pt', add_special_tokens=False).to(model.device)
    with torch.no_grad():
        out = model.generate(**ids, max_new_tokens=MAX_NEW, do_sample=False, eos_token_id=EOT,
                             pad_token_id=tok.pad_token_id or EOT)
    return tok.decode(out[0][ids['input_ids'].shape[1]:], skip_special_tokens=True).strip()

def make_handler(generate):
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
            text = generate(system, user)
            print(f'[{time.time()-t0:.2f}s] "{user}" -> {text}', flush=True)
            self._send(200, {'object': 'chat.completion', 'model': req.get('model', 'six-sense'),
                             'choices': [{'index': 0, 'message': {'role': 'assistant', 'content': text}, 'finish_reason': 'stop'}]})

        def log_message(self, *a):
            pass
    return H



PORT = 8000
threading.Thread(target=ThreadingHTTPServer(('0.0.0.0', PORT), make_handler(generate)).serve_forever, daemon=True).start()
print('로컬 서버 시작 (Colab 안):', PORT)

# --- 인터넷에서 부를 수 있는 임시 주소 만들기 (cloudflared 무료 터널) ---
if not os.path.exists('cloudflared'):
    subprocess.run('wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -O cloudflared && chmod +x cloudflared', shell=True, check=True)
tun = subprocess.Popen(['./cloudflared', 'tunnel', '--url', f'http://localhost:{PORT}', '--no-autoupdate'],
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
url = None
for line in tun.stdout:
    m = re.search(r'https://[a-z0-9-]+\.trycloudflare\.com', line)
    if m: url = m.group(0); break
print('\n대시보드 js/config.js 의 llmEndpoint 에 아래 주소를 넣으세요:\n')
print(f'   llmEndpoint: "{url}/v1/chat/completions",\n')
print('이 셀은 계속 실행 중이어야 합니다. 요청이 오면 아래에 문장별로 찍힙니다.')
while True:
    time.sleep(3600)
