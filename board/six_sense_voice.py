"""SIX-SENSE 보드 음성 서비스 (보드에서 실행).

  USB 마이크 -> 음성 구간 검출(VAD) -> 음성 인식(SenseVoice) -> 중계 서버(치환 사전 + 모델)
                                                                    |
  스피커 <- 안내 문장 말하기(TTS) <- 대시보드가 보여 준 문장(없으면 기본 문장) <-+

마음AI 패키지의 모델(~/voice/models)을 그대로 쓴다. 파이썬 라이브러리: sherpa-onnx, numpy.
시험용 옵션
  --text "에어컨 꺼라"      마이크·인식을 건너뛰고 글자를 바로 보낸다
  --wav 파일.wav            마이크 대신 wav 파일로 한 번 시험하고 끝낸다
  --no-audio                스피커로 말하지 않고 로그만 남긴다
"""
import argparse, hashlib, json, os, re, select, subprocess, sys, tempfile, threading, time, wave
import urllib.request
import numpy as np

# 대시보드(devices.js)와 같은 기본 안내 문장. 대시보드가 켜져 있으면 대시보드가 만든 문장(상태 반영)을 대신 말한다.
REPLY = {
    "six_0:1:1": "조명을 켤게요.", "six_0:1:2": "조명을 끌게요.",
    "six_1:1:1": "조명을 더 밝게 할게요.", "six_1:1:2": "조명을 은은하게 할게요.",
    "six_0:2:1": "에어컨을 켤게요. 24도로 맞출게요.", "six_0:2:2": "에어컨을 끌게요.",
    "six_1:2:1": "온도를 올릴게요.", "six_1:2:2": "온도를 내릴게요.",
    "six_1:2:3": "바람을 세게 할게요.", "six_1:2:4": "바람을 약하게 할게요.",
    "six_0:3:1": "블라인드를 올릴게요.", "six_0:3:2": "블라인드를 내릴게요.",
    "six_0:3:3": "블라인드를 멈출게요.", "six_0:3:4": "블라인드를 반쯤 내릴게요.",
}
ASK_REPLY = "어떤 기기를 말씀하시는 건가요?"
NONE_REPLY = "제가 도와드릴 수 있는 일이 아니에요."
CALL_RE = re.compile(r"<(six_\d)>\(([^)]*)\)")


def default_reply(content):
    acts, ask = [], False
    for tag, args in CALL_RE.findall(content or ""):
        kv = dict(re.findall(r"(\w+)\s*=\s*(-?\d+)", args))
        obj, typ = kv.get("object"), kv.get("type")
        if tag == "six_0" and obj == "-1":
            ask = True
        elif f"{tag}:{obj}:{typ}" in REPLY:
            acts.append(REPLY[f"{tag}:{obj}:{typ}"])
    if acts:
        return " ".join(acts)
    return ASK_REPLY if ask else NONE_REPLY


def http_json(url, payload=None, timeout=30):
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


class Speaker:
    """TTS -> 스피커. 같은 문장은 한 번만 합성해 두고 다시 쓴다."""
    def __init__(self, models, card, enabled):
        self.enabled, self.dev = enabled, f"plughw:CARD={card},DEV=0"
        self.cache_dir = os.path.join(tempfile.gettempdir(), "sixsense_tts")
        os.makedirs(self.cache_dir, exist_ok=True)
        self.tts = None
        self.busy_until = 0.0
        if enabled:
            import sherpa_onnx
            self.tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=f"{models}/tts-ko/model.onnx", tokens=f"{models}/tts-ko/tokens.txt",
                                                           lexicon="", data_dir=f"{models}/tts-ko/espeak-ng-data"), num_threads=2)))

    def wav_path(self, text):
        p = os.path.join(self.cache_dir, hashlib.md5(text.encode("utf-8")).hexdigest() + ".wav")
        if not os.path.exists(p):
            a = self.tts.generate(text, sid=0, speed=1.0)
            x = np.clip(np.asarray(a.samples, np.float32), -1, 1)
            with wave.open(p, "wb") as w:
                w.setnchannels(1); w.setsampwidth(2); w.setframerate(a.sample_rate)
                w.writeframes((x * 32767).astype(np.int16).tobytes())
        return p

    def speak(self, text):
        text = (text or "").strip()
        if not text or not any(c.isalpha() for c in text):
            return
        if not self.enabled:
            print(f"   [말하기 생략] {text}", flush=True)
            return
        t = time.time()
        path = self.wav_path(text)
        self.busy_until = time.time() + 30           # 재생 중에는 마이크 입력을 버린다 (최대 30초)
        try:
            r = subprocess.run(["aplay", "-q", "-D", self.dev, path], stdin=subprocess.DEVNULL,
                               stderr=subprocess.PIPE, text=True, timeout=25)
            if r.returncode:
                print(f"[경고] 스피커 재생 실패: {r.stderr.strip()[:150]}", flush=True)
        except Exception as e:
            print(f"[경고] 스피커 재생 오류: {type(e).__name__}: {e}", flush=True)
        finally:
            self.busy_until = time.time() + 0.5      # 울림이 잦아들 때까지 조금 더
        print(f"   [말하기] {text}  ({time.time() - t:.2f}s)", flush=True)

    def warm(self, texts):
        if self.enabled:
            for t in texts:
                self.wav_path(t)


class Agent:
    def __init__(self, a):
        self.a = a
        self.proxy = a.proxy.rstrip("/")
        self.speaker = Speaker(a.models, a.card, not a.no_audio)

    def send(self, text, stt_ms):
        """인식된 글자 -> 중계 서버 -> (대시보드 문장 | 기본 문장) 말하기"""
        t0 = time.time()
        try:
            ev = http_json(self.proxy + "/voice/utterance", {"text": text, "stt_ms": stt_ms})
        except Exception as e:
            print(f"[오류] 중계 서버 호출 실패: {type(e).__name__}: {e}", flush=True)
            self.speaker.speak("잠시 문제가 생겼어요. 다시 말씀해 주세요.")
            return
        reply = None
        if ev.get("dashboard"):
            try:
                reply = http_json(f"{self.proxy}/voice/reply?id={ev['id']}&wait=2.5", timeout=10).get("text")
            except Exception:
                reply = None
        src = "대시보드" if reply else "기본"
        reply = reply or default_reply(ev.get("content"))
        print(f"[#{ev['id']}] \"{text}\" -> {ev.get('content')}  |  {src} 문장: {reply}  (전체 {time.time() - t0:.2f}s)", flush=True)
        self.speaker.speak(reply)

    # ---- 음성 인식 ----
    def load_asr(self):
        import sherpa_onnx
        m = self.a.models
        cfg = sherpa_onnx.VadModelConfig()
        cfg.sample_rate = 16000
        cfg.silero_vad.model = f"{m}/silero_vad.onnx"
        cfg.silero_vad.threshold = self.a.vad_threshold
        cfg.silero_vad.min_silence_duration = self.a.silence
        cfg.silero_vad.min_speech_duration = 0.25
        self.vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=30)
        self.asr = sherpa_onnx.OfflineRecognizer.from_sense_voice(
            model=f"{m}/sense-voice/model.int8.onnx", tokens=f"{m}/sense-voice/tokens.txt",
            language="ko", use_itn=True, num_threads=4)

    def recognize(self, seg):
        x = np.asarray(seg, np.float32)
        peak = float(np.abs(x).max()) if len(x) else 0.0
        if 0.01 < peak < 0.3:                          # 너무 작게 들어온 소리는 키워서 인식시킨다
            x = x * min(10.0, 0.5 / peak)
        t = time.time()
        st = self.asr.create_stream()
        st.accept_waveform(16000, x)
        self.asr.decode_stream(st)
        text = re.sub(r"<\|[^|]*\|>", "", st.result.text).strip()
        return text, int((time.time() - t) * 1000), len(x) / 16000, peak

    def on_segment(self, seg):
        text, ms, dur, peak = self.recognize(seg)
        if len(re.sub(r"[^0-9A-Za-z가-힣]", "", text)) < 2:
            print(f"[무시] 인식 결과가 너무 짧아요: {text!r} (길이 {dur:.1f}s, 크기 {peak:.2f})", flush=True)
            return
        print(f"[인식] {text}  (길이 {dur:.1f}s, 크기 {peak:.2f}, {ms}ms)", flush=True)
        self.send(text, ms)

    def feed(self, chunk):
        self.vad.accept_waveform(chunk)
        while not self.vad.empty():
            seg = self.vad.front.samples
            self.vad.pop()
            self.on_segment(seg)

    # ---- 마이크 ----
    def run_mic(self):
        cmd = ["arecord", "-q", "-D", f"plughw:CARD={self.a.card},DEV=0", "-f", "S16_LE", "-r", "16000", "-c", "1", "-t", "raw"]
        print(f"[음성 서비스] 마이크 듣는 중… (카드 {self.a.card}, 종료 Ctrl+C)", flush=True)
        while True:
            p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            fd, buf = p.stdout.fileno(), b""
            try:
                while True:
                    data = os.read(fd, 4096)
                    if not data:
                        break
                    if time.time() < self.speaker.busy_until:      # 내 목소리(스피커) 울림은 듣지 않는다
                        buf = b""
                        continue
                    buf += data
                    while len(buf) >= 1024:
                        x = np.frombuffer(buf[:1024], np.int16).astype(np.float32) / 32768
                        if self.a.gain != 1.0:
                            x = np.clip(x * self.a.gain, -1.0, 1.0)
                        buf = buf[1024:]
                        before = time.time()
                        self.feed(x)
                        if time.time() - before > 0.3:             # 처리·말하기를 하는 동안 쌓인 소리는 버린다
                            while select.select([fd], [], [], 0)[0]:
                                if not os.read(fd, 65536):
                                    break
                            buf = b""
                            try:
                                self.vad.reset()
                            except Exception:
                                pass
            finally:
                p.kill()
            print("[경고] 마이크 입력이 끊겼어요. 3초 뒤 다시 연결합니다.", flush=True)
            time.sleep(3)

    def run_wav(self, path):
        with wave.open(path) as w:
            sr = w.getframerate()
            x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
            if w.getnchannels() > 1:
                x = x.reshape(-1, w.getnchannels())[:, 0]
        if sr != 16000:
            n = int(len(x) * 16000 / sr)
            x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
        x = np.concatenate([np.zeros(16000, np.float32), x, np.zeros(16000, np.float32)])
        for i in range(0, len(x), 512):
            self.feed(x[i:i + 512])
        self.vad.flush()
        self.feed(np.zeros(512, np.float32))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--card", default="Device", help="USB 사운드카드 ALSA 이름 (arecord -l 의 card N: <이름>)")
    ap.add_argument("--proxy", default="http://127.0.0.1:8000", help="중계 서버 주소")
    ap.add_argument("--models", default=os.path.expanduser("~/voice/models"))
    ap.add_argument("--text", help="마이크·인식 없이 글자를 바로 보낸다 (시험용)")
    ap.add_argument("--wav", help="마이크 대신 wav 파일로 한 번 시험하고 끝낸다")
    ap.add_argument("--no-audio", action="store_true", help="스피커로 말하지 않는다")
    ap.add_argument("--no-hello", action="store_true", help="시작할 때 인사하지 않는다")
    ap.add_argument("--vad-threshold", type=float, default=0.35, help="낮을수록 작은 목소리도 말로 본다 (0.2~0.6)")
    ap.add_argument("--gain", type=float, default=2.5, help="마이크 소리를 소프트웨어로 키우는 배수 (1이면 그대로)")
    ap.add_argument("--silence", type=float, default=0.8, help="이 시간(초)만큼 조용하면 말이 끝난 것으로 본다")
    a = ap.parse_args()

    ag = Agent(a)
    if a.text:
        ag.send(a.text, 0)
        return
    ag.load_asr()
    ag.speaker.warm(sorted(set(REPLY.values()) | {ASK_REPLY, NONE_REPLY}))
    if a.wav:
        ag.run_wav(a.wav)
        return
    if not a.no_hello:
        ag.speaker.speak("음성 비서가 준비됐어요.")
    ag.run_mic()


if __name__ == "__main__":
    main()
