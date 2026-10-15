# -*- coding: utf-8 -*-
"""학습데이터/std/sentences.jsonl → 두 목소리 TTS wav + 평가용 jsonl(학습데이터/std/{general,domain}.jsonl).

- vits: 기존 학습에 쓴 VITS kss (sherpa-onnx, espeak-ng). 문장마다 말 속도·억양 변형 하나를 무작위로
- sapi: Windows 내장 Microsoft Heami. 문장마다 속도 -2~2 무작위
두 목소리에서 똑같이 틀리면 TTS 문제가 아니라 STT 문제일 가능성이 높다.
실행: python stt\\make_std_tts.py
"""
import json
import os
import random
import subprocess
import wave
from multiprocessing import Pool

from make_tts_data import save16k, tts_engine

OUT = r"학습데이터/std"
VITS_VARIANTS = [(1.0, 0.667, 0.8), (0.85, 0.667, 0.8), (1.15, 0.667, 0.8), (0.95, 0.9, 1.0),
                 (1.1, 0.4, 0.6), (0.9, 0.8, 0.9), (1.05, 0.75, 0.85), (0.92, 0.55, 0.7)]
SEED = 77


def vits_job(args):
    v, rs, vdir = args
    todo = [r for r in rs if not os.path.exists(os.path.join(vdir, r["id"] + ".wav"))]
    if todo:
        tts = tts_engine(*VITS_VARIANTS[v])
        for r in todo:
            a = tts.generate(r["text"], sid=0, speed=1.0)
            tmp = os.path.join(vdir, r["id"] + ".tmp")
            save16k(tmp, a.samples, a.sample_rate)
            os.replace(tmp, os.path.join(vdir, r["id"] + ".wav"))  # 중단돼도 반쯤 쓴 wav가 남지 않게
    return v, len(todo)


def main():
    rows = [json.loads(l) for l in open(os.path.join(OUT, "sentences.jsonl"), encoding="utf-8")]
    rnd = random.Random(SEED)
    vdir, sdir = (os.path.abspath(os.path.join(OUT, "wav", v)) for v in ("vits", "sapi"))
    os.makedirs(vdir, exist_ok=True)

    by_var = {}
    for r in rows:
        by_var.setdefault(rnd.randrange(len(VITS_VARIANTS)), []).append(r)
    lst = os.path.abspath(os.path.join(OUT, "sapi_list.tsv"))
    with open(lst, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(f"{r['id']}\t{rnd.randint(-2, 2)}\t{r['text']}\n")
    ps1 = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sapi_tts.ps1")
    sapi = subprocess.Popen(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ps1,
                             "-List", lst, "-OutDir", sdir])
    with Pool(5) as pool:
        for v, n in pool.imap_unordered(vits_job, [(v, rs, vdir) for v, rs in sorted(by_var.items())]):
            print(f"vits variant {v}: {n}", flush=True)
    if sapi.wait():
        raise SystemExit("sapi_tts.ps1 실패")
    os.remove(lst)

    for setname in ("general", "domain"):
        with open(os.path.join(OUT, f"{setname}.jsonl"), "w", encoding="utf-8") as f:
            for r in rows:
                if r["set"] != setname:
                    continue
                for voice, d in (("vits", vdir), ("sapi", sdir)):
                    path = os.path.join(d, r["id"] + ".wav")
                    with wave.open(path) as w:
                        sec = w.getnframes() / w.getframerate()
                    f.write(json.dumps({**r, "key": f"{r['id']}_{voice}", "voice": voice, "source": path,
                                        "sec": round(sec, 2)}, ensure_ascii=False) + "\n")
    print("done")


if __name__ == "__main__":
    main()
