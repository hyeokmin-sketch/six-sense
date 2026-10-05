# -*- coding: utf-8 -*-
"""세 모델 × (학습 / 검증 / 처음 보는 문장) CER·완전일치 표 + 그룹별 표 + 결과 csv(학습데이터/eval/all_<모델>_<셋>.csv).
실행: .venv\\Scripts\\python.exe stt\\eval_all.py
"""
import csv
import json
import os
import sys
from collections import defaultdict

import sherpa_onnx

sys.path.insert(0, os.path.dirname(__file__))
from eval_cer import edit, load_wav, norm  # noqa: E402
from transcribe import MODELS  # noqa: E402

SETS = {"학습": r"학습데이터/tts/train.jsonl", "검증": r"학습데이터/tts/val.jsonl",
        "처음": r"학습데이터/unseen/unseen.jsonl"}
OUT = r"학습데이터/eval"


def main():
    res = defaultdict(lambda: [0, 0, 0, 0])  # (모델, 셋, 그룹) → 오류, 글자, 완전일치, 개수
    for m, d in MODELS.items():
        rec = sherpa_onnx.OfflineRecognizer.from_sense_voice(
            model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", language="ko", use_itn=True, num_threads=4)
        for sname, path in SETS.items():
            rows = []
            for line in open(path, encoding="utf-8"):
                r = json.loads(line)
                sr, x = load_wav(r["source"])
                s = rec.create_stream()
                s.accept_waveform(sr, x)
                rec.decode_stream(s)
                g, h = norm(r["target"]), norm(s.result.text)
                e = edit(g, h)
                grp = r["group"] if sname == "처음" else "전체"
                for k in {(m, sname, "전체"), (m, sname, grp)}:
                    t = res[k]
                    t[0] += e; t[1] += len(g); t[2] += e == 0; t[3] += 1
                rows.append({"key": r["key"], "group": r["group"], "target": r["target"], "hyp": s.result.text,
                             "cer": round(e / max(1, len(g)), 3)})
            with open(os.path.join(OUT, f"all_{m}_{sname}.csv"), "w", encoding="utf-8-sig", newline="") as f:
                w = csv.DictWriter(f, fieldnames=list(rows[0]))
                w.writeheader()
                w.writerows(rows)
            print(f"done {m} {sname}", flush=True)
    keys = sorted({(s, g) for _, s, g in res}, key=lambda k: (list(SETS).index(k[0]), k[1] != "전체", k[1]))
    print("\n셋 | 그룹 | n | " + " | ".join(f"{m} CER / 완전일치" for m in MODELS))
    for s, g in keys:
        n = res[(next(iter(MODELS)), s, g)][3]
        cells = []
        for m in MODELS:
            e, c, ex, k = res[(m, s, g)]
            cells.append(f"{e / c:.1%} / {ex / k:.1%}")
        print(f"{s} | {g} | {n} | " + " | ".join(cells))


if __name__ == "__main__":
    main()
