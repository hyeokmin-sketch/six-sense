# -*- coding: utf-8 -*-
"""ABC 엑셀의 A(경상도 사투리) 문장 → VITS TTS 음성(16kHz wav) + 학습/검증 jsonl.

- 문장마다 말 속도·억양 랜덤성을 바꿔 VARIANTS 벌 생성 (단일 화자 TTS라 다양성 확보용)
- 검증은 '문장 단위'로 분리 (학습에 없는 문장으로 평가) — 기기/동작별 약 20%
- espeak-ng 가 한글 경로를 못 읽어 TTS 모델을 TTS_DIR(영문 경로)로 복사해 사용
실행: .venv\\Scripts\\python.exe stt\\make_tts_data.py
"""
import json
import os
import random
import shutil
import wave

import numpy as np
import openpyxl
import sherpa_onnx
from scipy.signal import resample_poly

XLSX = "문서/SIX-SENSE_ABC_데이터셋_경상도.xlsx"
TTS_SRC = r"models/vits-mimic3-ko_KO-kss_low"
TTS_DIR = os.environ.get("SIXSENSE_TTS_DIR", r"C:\Users\kimyo\sixsense_tts\vits-mimic3-ko_KO-kss_low")
OUT = r"학습데이터/tts"
VAL_RATIO = 0.2
# (length_scale: 클수록 느림, noise_scale, noise_scale_w)
VARIANTS = [(1.0, 0.667, 0.8), (0.85, 0.667, 0.8), (1.15, 0.667, 0.8),
            (0.95, 0.9, 1.0), (1.1, 0.4, 0.6), (0.9, 0.8, 0.9)]
SEED = 1004


def load_sentences():
    ws = openpyxl.load_workbook(XLSX, read_only=True)["데이터셋"]
    rows = []
    for r in ws.iter_rows(min_row=2, values_only=True):
        cat, text, _, tag, author, note = r[:6]
        if cat == "A" and text:
            rows.append({"text": str(text).strip(), "group": note or "", "example": author == "(예시)"})
    return rows


def split(rows):
    rnd = random.Random(SEED)
    by = {}
    for i, r in enumerate(rows):
        by.setdefault(r["group"], []).append(i)
    val = set()
    for g, idx in by.items():
        rnd.shuffle(idx)
        val.update(idx[:max(1, round(len(idx) * VAL_RATIO))] if len(idx) >= 3 else [])
    return val


def tts_engine(length_scale, noise_scale, noise_scale_w):
    if not os.path.exists(TTS_DIR):
        shutil.copytree(TTS_SRC, TTS_DIR)
    cfg = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(
            model=os.path.join(TTS_DIR, "ko_KO-kss_low.onnx"), tokens=os.path.join(TTS_DIR, "tokens.txt"),
            data_dir=os.path.join(TTS_DIR, "espeak-ng-data"), lexicon="",
            length_scale=length_scale, noise_scale=noise_scale, noise_scale_w=noise_scale_w),
        num_threads=4))
    return sherpa_onnx.OfflineTts(cfg)


def save16k(path, samples, sr):
    a = resample_poly(np.asarray(samples, dtype=np.float32), 16000, sr) if sr != 16000 else np.asarray(samples)
    a = np.clip(a / max(1e-6, np.abs(a).max()) * 0.9, -1, 1)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes((a * 32767).astype(np.int16).tobytes())
    return len(a)


def main():
    rows = load_sentences()
    val = split(rows)
    os.makedirs(os.path.join(OUT, "wav"), exist_ok=True)
    out = {"train": [], "val": []}
    for v, (ls, ns, nw) in enumerate(VARIANTS):
        tts = tts_engine(ls, ns, nw)
        for i, r in enumerate(rows):
            part = "val" if i in val else "train"
            key = f"A{i + 1:03d}_v{v}"
            path = os.path.abspath(os.path.join(OUT, "wav", key + ".wav"))
            if not os.path.exists(path):
                a = tts.generate(r["text"], sid=0, speed=1.0)
                save16k(path, a.samples, a.sample_rate)
            with wave.open(path) as w:
                n = w.getnframes()
            out[part].append({"key": key, "source": path, "source_len": n // 160, "target": r["text"],
                              "target_len": len(r["text"]), "text_language": "<|ko|>",
                              "emo_target": "<|NEUTRAL|>", "event_target": "<|Speech|>",
                              "with_or_wo_itn": "<|withitn|>", "group": r["group"], "sent_id": i + 1})
        print(f"variant {v} done")
    for part, recs in out.items():
        with open(os.path.join(OUT, f"{part}.jsonl"), "w", encoding="utf-8") as f:
            for x in recs:
                f.write(json.dumps(x, ensure_ascii=False) + "\n")
        print(part, len(recs), "utts /", len({x['sent_id'] for x in recs}), "sentences")


if __name__ == "__main__":
    main()
