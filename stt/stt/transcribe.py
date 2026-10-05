# -*- coding: utf-8 -*-
"""녹음 파일(m4a/mp3/wav 등) 하나를 원본·ko-en·파인튜닝 모델로 인식해 비교.
정답 문장을 주면 CER 과, 그 문장이 학습/검증 중 어디에 있었는지도 표시.
실행: .venv\\Scripts\\python.exe stt\\transcribe.py 녹음.m4a [--text "정답 문장"]
"""
import argparse
import json
import os
import sys

import av
import numpy as np
import sherpa_onnx

sys.path.insert(0, os.path.dirname(__file__))
from eval_cer import edit, norm  # noqa: E402

MODELS = {"원본": r"models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17",
          "ko-en": r"models/sense-voice-ko-en",
          "tts_a1": r"models/sense-voice-ko-tts_a1"}


def load_audio(path):
    """PyAV 로 디코딩 → 16kHz 모노 float32 (ffmpeg 없이)."""
    out = []
    with av.open(path) as c:
        rs = av.AudioResampler(format="flt", layout="mono", rate=16000)
        for frame in c.decode(audio=0):
            for f in rs.resample(frame):
                out.append(f.to_ndarray().reshape(-1))
        for f in rs.resample(None):
            out.append(f.to_ndarray().reshape(-1))
    return np.concatenate(out).astype(np.float32)


def where(text):
    for part in ("train", "val"):
        p = f"학습데이터/tts/{part}.jsonl"
        if os.path.exists(p) and any(json.loads(l)["target"] == text for l in open(p, encoding="utf-8")):
            return {"train": "학습에 쓴 문장", "val": "검증용(학습 안 한) 문장"}[part]
    return "데이터셋에 없는 문장"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("audio")
    ap.add_argument("--text")
    a = ap.parse_args()
    x = load_audio(a.audio)
    print(f"파일: {a.audio}  길이 {len(x) / 16000:.1f}초  최대음량 {np.abs(x).max():.2f}")
    if a.text:
        print(f"정답: {a.text}  ({where(a.text.strip())})")
    for name, d in MODELS.items():
        r = sherpa_onnx.OfflineRecognizer.from_sense_voice(
            model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", language="ko", use_itn=True, num_threads=4)
        s = r.create_stream()
        s.accept_waveform(16000, x)
        r.decode_stream(s)
        hyp = s.result.text
        extra = ""
        if a.text:
            g = norm(a.text)
            extra = f"   (CER {edit(g, norm(hyp)) / max(1, len(g)):.0%})"
        print(f"  {name:6}: {hyp}{extra}")


if __name__ == "__main__":
    main()
