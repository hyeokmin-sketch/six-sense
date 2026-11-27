"""음성 환경 점검 — 모델 3종 로드 · 오디오 장치 · TTS→STT 왕복.  실행: cd ~/voice && python3 check_voice.py"""
import os, time, wave, numpy as np, sherpa_onnx
M = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")
for f in ("silero_vad.onnx", "sense-voice/model.int8.onnx", "sense-voice/tokens.txt", "tts-ko/model.onnx", "tts-ko/tokens.txt", "tts-ko/espeak-ng-data"):
    print(("  OK   " if os.path.exists(f"{M}/{f}") else "  FAIL ") + f"models/{f}")
try:
    import sounddevice as sd
    print("  input :", sd.query_devices(kind="input")["name"]); print("  output:", sd.query_devices(kind="output")["name"])
except Exception as e:
    print("  WARN  오디오 장치 없음 —", str(e).split("\n")[0], "(voice_agent.py 는 --wav 로만 점검 가능)")
t = time.time()
tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=sherpa_onnx.OfflineTtsVitsModelConfig(
    model=f"{M}/tts-ko/model.onnx", tokens=f"{M}/tts-ko/tokens.txt", lexicon="", data_dir=f"{M}/tts-ko/espeak-ng-data"), num_threads=2)))
asr = sherpa_onnx.OfflineRecognizer.from_sense_voice(model=f"{M}/sense-voice/model.int8.onnx", tokens=f"{M}/sense-voice/tokens.txt", language="ko", use_itn=True, num_threads=4)
print(f"  OK   모델 로드 {time.time()-t:.1f}s")
a = tts.generate("공부할 게 너무 많아", sid=0, speed=1.0); x = np.asarray(a.samples, np.float32)
with wave.open("check.wav", "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(a.sample_rate); w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
st = asr.create_stream(); st.accept_waveform(a.sample_rate, x); asr.decode_stream(st)
print(f"  OK   TTS→STT 왕복: '공부할 게 너무 많아' -> {st.result.text.strip()!r}  (check.wav 저장 — voice_agent.py --wav check.wav 로 전체 점검)")
