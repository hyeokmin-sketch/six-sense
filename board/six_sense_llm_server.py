"""SIX-SENSE LLM 서버 (보드용). 마음AI fastapi_server.py 와 같은 주소·형식(:8080, /v1/chat/completions)으로 응답하되,
마음AI binding 을 그대로 불러 repeat_penalty 를 직접 넘긴다. 마음AI 파일은 수정하지 않는다.

  python3 six_sense_llm_server.py --model ~/rkllm/sixsense-smarthome_w8a8_opt1_cal499.rkllm [--port 8080] [--repeat-penalty 1.0]

- 평가의 rp10 모드(원본과 같은 조건)와 똑같이 top_k=1(greedy), repeat_penalty=1.0 으로 생성한다.
- system 은 요청의 system 메시지를 쓰고(없으면 --system 파일), 모델의 .rkllm.json 템플릿에 넣는다. 바뀔 때만 다시 설정한다.
- 한 번에 한 요청만 처리한다. 처리 중에 다른 요청이 오면 503.
"""
import argparse, json, os, sys, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ap = argparse.ArgumentParser()
ap.add_argument("--model", required=True)
ap.add_argument("--server-dir", default="~/rkllm/server", help="마음AI rkllm_binding.py 와 lib/ 가 있는 폴더")
ap.add_argument("--system", default="~/sixsense_eval/system_prompt.txt", help="요청에 system 이 없을 때 쓸 문장")
ap.add_argument("--port", type=int, default=8080)
ap.add_argument("--host", default="0.0.0.0")
ap.add_argument("--repeat-penalty", type=float, default=1.0)
ap.add_argument("--max-tokens", type=int, default=128)
a = ap.parse_args()

model_path = os.path.abspath(os.path.expanduser(a.model))
server_dir = os.path.abspath(os.path.expanduser(a.server_dir))
side_path = model_path + ".json"
if not os.path.exists(side_path):
    sys.exit(f"사이드카 없음: {side_path}")
SIDE = json.load(open(side_path, encoding="utf-8"))
if SIDE.get("mode") != "chat_template" or not SIDE.get("system_supported"):
    sys.exit(f"사이드카 mode={SIDE.get('mode')} 가 예상과 다릅니다")
DEFAULT_SYSTEM = open(os.path.expanduser(a.system), encoding="utf-8").read().strip() if os.path.exists(os.path.expanduser(a.system)) else ""

os.chdir(server_dir)
sys.path.insert(0, server_dir)
import rkllm_binding as rk  # noqa: E402

model = rk.RKLLM(model_path, None, None, "rk3588", max_context_len=SIDE.get("max_context") or 4096)
LOCK = threading.Lock()
CUR = {"system": None}

def sampling():
    sp = rk.RKLLMSamplingParam()
    sp.top_k, sp.top_p, sp.temperature = 1, 0.9, 0.8
    sp.repeat_penalty, sp.frequency_penalty, sp.presence_penalty = a.repeat_penalty, 0.0, 0.0
    sp.mirostat, sp.mirostat_tau, sp.mirostat_eta = 0, 5.0, 0.1
    return sp

def set_system(system):
    if CUR["system"] != system:
        model.set_chat_template(SIDE["system_fmt"].replace("{system}", system), SIDE["prefix"], SIDE["postfix"])
        CUR["system"] = system

def generate(system, user, max_tokens):
    set_system(system)
    return rk.run_inference(model, user, "user", False, sampling(), max_tokens).strip()

def text_of(c):
    if isinstance(c, list):
        return "".join(p.get("text", "") for p in c if isinstance(p, dict))
    return c or ""

class H(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        for k, v in (("Access-Control-Allow-Origin", "*"), ("Access-Control-Allow-Headers", "*"), ("Access-Control-Allow-Methods", "GET,POST,OPTIONS")):
            self.send_header(k, v)
        self.end_headers()

    def do_GET(self):
        if self.path.rstrip("/") in ("/health", "/v1/models", ""):
            return self._send(200, {"ok": True, "repeat_penalty": a.repeat_penalty, "model": os.path.basename(model_path)})
        self._send(404, {"error": "not found"})

    def do_POST(self):
        if not self.path.startswith("/v1/chat/completions"):
            return self._send(404, {"error": "not found"})
        try:
            req = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            msgs = req.get("messages") or []
            system = next((text_of(m.get("content")) for m in msgs if m.get("role") == "system"), "") or DEFAULT_SYSTEM
            user = next((text_of(m.get("content")) for m in reversed(msgs) if m.get("role") == "user"), "")
        except Exception as e:
            return self._send(400, {"error": f"bad request: {e}"})
        if not user.strip():
            return self._send(400, {"error": "user 메시지가 없습니다"})
        if not LOCK.acquire(blocking=False):
            return self._send(503, {"error": "busy"})
        try:
            t = time.time()
            out = generate(system.strip(), user.strip(), int(req.get("max_tokens") or a.max_tokens))
            ms = int((time.time() - t) * 1000)
        except Exception as e:
            return self._send(500, {"error": f"{type(e).__name__}: {e}"})
        finally:
            LOCK.release()
        self._send(200, {"id": "six-sense", "object": "chat.completion", "created": int(time.time()), "model": "six-sense",
                         "choices": [{"index": 0, "message": {"role": "assistant", "content": out}, "finish_reason": "stop"}],
                         "usage": {"completion_ms": ms}})

    def log_message(self, *args):
        pass

print(f"[six-sense llm] {os.path.basename(model_path)} · repeat_penalty={a.repeat_penalty} · http://{a.host}:{a.port}", flush=True)
try:
    ThreadingHTTPServer((a.host, a.port), H).serve_forever()
finally:
    model.release()
