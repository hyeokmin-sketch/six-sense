# -*- coding: utf-8 -*-
"""원본 SenseVoice vs 한국어·영어 마스크 모델 출력 비교.
실행: .venv\\Scripts\\python.exe stt\\compare_models.py [wav ...]   (인자 없으면 모델 test_wavs 5개)
"""
import glob
import sys
import wave

import numpy as np
import sherpa_onnx

MODELS = {"원본": r"models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17",
          "ko-en": r"models/sense-voice-ko-en",
          "tts_a1": r"models/sense-voice-ko-tts_a1"}


def load_wav(path):
    with wave.open(path) as w:
        sr, ch = w.getframerate(), w.getnchannels()
        a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
    return (a.reshape(-1, ch).mean(1) if ch > 1 else a), sr


def recognizer(d, lang):
    return sherpa_onnx.OfflineRecognizer.from_sense_voice(
        model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", language=lang, use_itn=True, num_threads=4)


def main():
    wavs = sys.argv[1:] or sorted(glob.glob(MODELS["원본"] + "/test_wavs/*.wav"))
    for lang in ("ko", "auto"):
        recs = {k: recognizer(d, lang) for k, d in MODELS.items()}
        print(f"\n===== language={lang} =====")
        for p in wavs:
            a, sr = load_wav(p)
            print(f"[{p.replace(chr(92), '/').split('/')[-1]}]")
            for k, r in recs.items():
                s = r.create_stream()
                s.accept_waveform(sr, a)
                r.decode_stream(s)
                print(f"  {k:5}: {s.result.text}")


if __name__ == "__main__":
    main()
