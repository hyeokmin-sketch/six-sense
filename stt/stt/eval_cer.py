# -*- coding: utf-8 -*-
"""sherpa-onnx SenseVoice 모델로 jsonl(source/target) 음성 인식 → CER, 문장 완전일치율, 결과 csv.
CER 은 공백·문장부호 제거 후 글자 단위 편집거리 / 정답 글자 수.
실행: .venv\\Scripts\\python.exe stt\\eval_cer.py --model models\\sense-voice-ko-en --data 학습데이터\\tts\\val.jsonl [--out x.csv]
"""
import argparse
import csv
import json
import re
import wave

import numpy as np
import sherpa_onnx


def norm(s):
    return re.sub(r"[\s\.,!?~·…'\"]", "", s)


def edit(a, b):
    d = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        prev, d[0] = d[0], i
        for j, cb in enumerate(b, 1):
            prev, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, prev + (ca != cb))
    return d[-1]


def load_wav(path):
    with wave.open(path) as w:
        return w.getframerate(), np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--data", required=True)
    ap.add_argument("--out")
    ap.add_argument("--lang", default="ko")
    a = ap.parse_args()
    rec = sherpa_onnx.OfflineRecognizer.from_sense_voice(
        model=f"{a.model}/model.int8.onnx", tokens=f"{a.model}/tokens.txt", language=a.lang, use_itn=True, num_threads=4)
    rows = [json.loads(l) for l in open(a.data, encoding="utf-8")]
    errs = chars = exact = 0
    out = []
    for r in rows:
        sr, x = load_wav(r["source"])
        s = rec.create_stream()
        s.accept_waveform(sr, x)
        rec.decode_stream(s)
        hyp = s.result.text
        g, h = norm(r["target"]), norm(hyp)
        e = edit(g, h)
        errs, chars, exact = errs + e, chars + len(g), exact + (e == 0)
        out.append({"key": r["key"], "target": r["target"], "hyp": hyp, "cer": round(e / max(1, len(g)), 3)})
    print(f"{a.model} | {a.data}: n={len(rows)} CER={errs / chars:.1%} 완전일치={exact / len(rows):.1%}")
    if a.out:
        with open(a.out, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=list(out[0]))
            w.writeheader()
            w.writerows(out)


if __name__ == "__main__":
    main()
