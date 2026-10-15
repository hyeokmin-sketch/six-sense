# -*- coding: utf-8 -*-
"""표준어 TTS 평가셋(학습데이터/std/{general,domain}.jsonl)을 SenseVoice 모델로 인식 → 지표 + 결과 csv.

지표
- CER: 공백·문장부호 빼고 글자 편집거리 / 정답 글자 수. 숫자는 "이십육 도"/"26도" 중 가까운 쪽을 정답으로
- 완전일치: CER 0인 문장 비율
- 키워드 인식률(도메인): 기기(블라인드·에어컨·조명류) / 동작(켜·꺼·올려·제습…) / 숫자가 출력에 그대로 들어있는 비율
- RTF: 인식 시간 / 음성 길이 (CPU, 4스레드)
결과: 학습데이터/std/eval/<모델>_<셋>.csv, 학습데이터/std/eval/summary.json
실행: python stt\\eval_std.py [--models <models 폴더>] [--only 원본]
"""
import argparse
import csv
import json
import os
import sys
import time
from collections import defaultdict

import sherpa_onnx

sys.path.insert(0, os.path.dirname(__file__))
from eval_cer import edit, load_wav, norm  # noqa: E402

DATA = r"학습데이터/std"
OUT = r"학습데이터/std/eval"
MODELS = {"원본": "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17", "tts_a1": "sense-voice-ko-tts_a1"}
BATCH = 32


def n2(s):
    return norm(s.replace("%", "퍼센트")).lower()


def score(r, hyp):
    """정답 표기(text/alt) 중 편집거리가 작은 쪽 기준 (오류 수, 정답 글자 수, 사용한 정답)"""
    h = n2(hyp)
    best = None
    for ref in [r["text"]] + ([r["alt"]] if "alt" in r else []):
        g = n2(ref)
        e = edit(g, h)
        if best is None or e < best[0]:
            best = (e, len(g), ref)
    return best


def kw_hits(r, hyp):
    h = n2(hyp)
    out = {}
    for cat, kws in r.get("keywords", {}).items():
        out[cat] = [any(n2(a) in h for a in kw.split("|")) for kw in kws]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", default=os.environ.get("SIXSENSE_MODELS", "models"))
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--threads", type=int, default=4)
    a = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    summary = {}
    for mname, mdir in MODELS.items():
        if a.only and mname not in a.only:
            continue
        d = os.path.join(a.models, mdir)
        rec = sherpa_onnx.OfflineRecognizer.from_sense_voice(
            model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", language="ko", use_itn=True,
            num_threads=a.threads)
        for setname in ("general", "domain"):
            rows = [json.loads(l) for l in open(os.path.join(DATA, f"{setname}.jsonl"), encoding="utf-8")]
            hyps, dec_sec = [], 0.0
            for i in range(0, len(rows), BATCH):
                chunk = rows[i:i + BATCH]
                streams = []
                for r in chunk:
                    sr, x = load_wav(r["source"])
                    s = rec.create_stream()
                    s.accept_waveform(sr, x)
                    streams.append(s)
                t0 = time.perf_counter()
                rec.decode_streams(streams)
                dec_sec += time.perf_counter() - t0
                hyps += [s.result.text for s in streams]
            agg = defaultdict(lambda: defaultdict(float))
            out = []
            for r, hyp in zip(rows, hyps):
                e, n, ref = score(r, hyp)
                kh = kw_hits(r, hyp)
                c = e / max(1, n)
                for key in ("전체", f"voice={r['voice']}", f"group={r['group']}"):
                    t = agg[key]
                    t["n"] += 1; t["err"] += e; t["chars"] += n; t["exact"] += e == 0; t["sec"] += r["sec"]
                    t["ok10"] += c < .1; t["bad30"] += c >= .3
                    for cat, hits in kh.items():
                        t[f"{cat}_n"] += len(hits); t[f"{cat}_hit"] += sum(hits)
                out.append({"key": r["key"], "id": r["id"], "voice": r["voice"], "group": r["group"], "ref": ref,
                            "text": r["text"], "hyp": hyp, "err": e, "chars": n, "cer": round(c, 3),
                            **{f"kw_{cat}": f"{sum(h)}/{len(h)}" for cat, h in kh.items()}})
            fields = list(dict.fromkeys(k for o in out for k in o))
            with open(os.path.join(OUT, f"{mname}_{setname}.csv"), "w", encoding="utf-8-sig", newline="") as f:
                w = csv.DictWriter(f, fieldnames=fields)
                w.writeheader()
                w.writerows(out)
            res = {}
            for key, t in agg.items():
                res[key] = {"n": int(t["n"]), "CER": round(t["err"] / t["chars"], 4),
                            "완전일치": round(t["exact"] / t["n"], 4), "CER<10%": round(t["ok10"] / t["n"], 4),
                            "CER>=30%": round(t["bad30"] / t["n"], 4)}
                for cat in ("device", "action", "number"):
                    if t[f"{cat}_n"]:
                        res[key][f"{cat}_acc"] = round(t[f"{cat}_hit"] / t[f"{cat}_n"], 4)
            res["전체"]["RTF"] = round(dec_sec / agg["전체"]["sec"], 4)
            res["전체"]["ms_per_utt"] = round(dec_sec / len(rows) * 1000, 1)
            summary[f"{mname}/{setname}"] = res
            t = res["전체"]
            print(f"{mname:7} {setname:8} n={t['n']} CER={t['CER']:.1%} 완전일치={t['완전일치']:.1%} "
                  + " ".join(f"{k}={v:.1%}" for k, v in t.items() if k.endswith("_acc"))
                  + f" RTF={t['RTF']}", flush=True)
    with open(os.path.join(OUT, "summary.json"), "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
