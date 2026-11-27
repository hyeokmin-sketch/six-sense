"""제공 음성 에이전트 (프로세스 B) — 마이크 → VAD → STT → LLM(HTTP) → TTS → 스피커.
실행:  cd ~/voice && python3 voice_agent.py              # 마이크·스피커 직결 (기본)
       python3 voice_agent.py --wav 파일.wav             # 마이크 대신 wav 파일 입력 (마이크 없을 때 점검용)
       python3 voice_agent.py --wav 파일.wav --no-audio  # 스피커 없으면 합성 결과를 out_*.wav 로 저장
팀이 바꾸는 것: SYSTEM (팀 역할 지시) 과 URL (서버 주소). 모델 3종은 전 팀 동일."""
import argparse, json, os, sys, time, wave
import numpy as np, requests, sherpa_onnx

SYSTEM = "친절하게 학습 계획을 도와주세요."              # Part 1 학습·평가와 동일한 지시 (팀별로 교체)
URL = "http://127.0.0.1:8080/v1/chat/completions"     # 제공 FastAPI 서버
MAX_TOKENS = 128                                      # 음성 답변은 짧게 — 길면 듣는 사람이 기다린다
MODELS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")

ap = argparse.ArgumentParser()
ap.add_argument("--wav", help="마이크 대신 입력할 16kHz/22kHz mono wav")
ap.add_argument("--no-audio", action="store_true", help="스피커로 재생하지 않고 out_<n>.wav 로 저장")
ap.add_argument("--no-llm", action="store_true", help="STT 결과만 출력 (서버 없이 점검)")
args = ap.parse_args()
if not args.no_audio or not args.wav:
    import sounddevice as sd

# ── VAD + STT (PPT 36쪽) ──
cfg = sherpa_onnx.VadModelConfig(); cfg.sample_rate = 16000
cfg.silero_vad.model = f"{MODELS}/silero_vad.onnx"; cfg.silero_vad.threshold = 0.5
cfg.silero_vad.min_silence_duration = 0.5      # s, 발화 종료 판정 (0.3~0.5 조정)
cfg.silero_vad.min_speech_duration = 0.25
vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=30)
asr = sherpa_onnx.OfflineRecognizer.from_sense_voice(
    model=f"{MODELS}/sense-voice/model.int8.onnx", tokens=f"{MODELS}/sense-voice/tokens.txt",
    language="ko", use_itn=True, num_threads=4)

# ── TTS (PPT 38쪽) ──
tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
    model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(
            model=f"{MODELS}/tts-ko/model.onnx", tokens=f"{MODELS}/tts-ko/tokens.txt",
            lexicon="", data_dir=f"{MODELS}/tts-ko/espeak-ng-data"),
        num_threads=2)))
_n = [0]; speaking = [False]


def speak(text):
    text = text.strip()
    if not any(c.isalpha() for c in text): return    # "1." 같은 번호·기호만 있는 조각은 읽지 않는다
    a = tts.generate(text, sid=0, speed=1.0)
    pcm = np.asarray(a.samples, dtype="float32")
    if args.no_audio:
        _n[0] += 1; p = f"out_{_n[0]}.wav"
        with wave.open(p, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(a.sample_rate)
            w.writeframes((np.clip(pcm, -1, 1) * 32767).astype(np.int16).tobytes())
        print(f"   [tts] {text!r} -> {p} ({len(pcm)/a.sample_rate:.2f}s)")
    else:
        speaking[0] = True                       # 재생 중에는 마이크 입력 무시 (에코 방지)
        sd.play(pcm, a.sample_rate); sd.wait()
        speaking[0] = False


# ── LLM 호출 (PPT 37쪽) — 스트리밍 응답을 문장 단위로 TTS ──
def handle(text):
    print(f"[stt] {text}")
    if args.no_llm: return
    body = {"model": "rkllm", "stream": True, "max_tokens": MAX_TOKENS,
            "messages": [{"role": "system", "content": SYSTEM},
                         {"role": "user",   "content": text}]}
    buf = ""; full = ""; t0 = time.time(); first = None
    with requests.post(URL, json=body, stream=True, timeout=120) as r:
        for line in r.iter_lines():
            if not line.startswith(b"data:") or line.strip() == b"data: [DONE]": continue
            tok = json.loads(line[5:])["choices"][0]["delta"].get("content", "")
            if tok and first is None: first = time.time() - t0
            buf += tok; full += tok
            if tok and tok[-1] in ".?!。\n":   # 문장이 끝나면 바로 말하기
                speak(buf); buf = ""
    if buf: speak(buf)
    print(f"[llm] {full.strip()}   (첫 토큰 {first or 0:.2f}s, 전체 {time.time()-t0:.2f}s)")


def feed(chunk):
    """16kHz float32 청크를 VAD 에 넣고, 발화 하나가 끝날 때마다 STT → handle"""
    vad.accept_waveform(chunk)
    while not vad.empty():                      # 발화 하나가 끝났다
        seg = vad.front.samples; vad.pop()
        st = asr.create_stream(); st.accept_waveform(16000, seg)
        asr.decode_stream(st); handle(st.result.text.strip())


if args.wav:                                     # 파일 입력 모드
    with wave.open(args.wav) as w:
        sr = w.getframerate(); x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
        if w.getnchannels() > 1: x = x.reshape(-1, w.getnchannels())[:, 0]
    if sr != 16000:
        n = int(len(x) * 16000 / sr); x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
    x = np.concatenate([np.zeros(16000, np.float32), x, np.zeros(16000, np.float32)])
    for i in range(0, len(x), 512): feed(x[i:i + 512])
    vad.flush(); feed(np.zeros(512, np.float32))
    sys.exit(0)

print("[voice_agent] 듣는 중… (Ctrl+C 종료)")
with sd.InputStream(samplerate=16000, channels=1, dtype="float32", blocksize=512) as mic:
    while True:
        chunk, _ = mic.read(512)
        if speaking[0]: continue                 # 스피커 재생 중 입력 무시
        feed(chunk[:, 0])
