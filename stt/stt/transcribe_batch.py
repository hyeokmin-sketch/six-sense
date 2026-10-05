# -*- coding: utf-8 -*-
"""녹음 여러 개를 세 모델로 인식 + 정답 문장 추정(엑셀 A 문장 중 세 모델 출력과 가장 가까운 것) + CER.
정답을 아는 경우 --labels "파일=문장" 로 지정하면 추정 대신 그 문장을 씀.
실행: .venv\\Scripts\\python.exe stt\\transcribe_batch.py "학습데이터\\녹음\\테스트\\녹음.m4a" ... [--labels "녹음.m4a=불 좀 키도"]
"""
import argparse
import os
import sys

import openpyxl
import sherpa_onnx

sys.path.insert(0, os.path.dirname(__file__))
from eval_cer import edit, norm  # noqa: E402
from transcribe import MODELS, load_audio, where  # noqa: E402

XLSX = "문서/SIX-SENSE_ABC_데이터셋_경상도.xlsx"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("audio", nargs="+")
    ap.add_argument("--labels", nargs="*", default=[])
    a = ap.parse_args()
    labels = dict(x.split("=", 1) for x in a.labels)
    ws = openpyxl.load_workbook(XLSX, read_only=True)["데이터셋"]
    sents = [str(r[1]).strip() for r in ws.iter_rows(min_row=2, values_only=True) if r[0] == "A" and r[1]]
    recs = {k: sherpa_onnx.OfflineRecognizer.from_sense_voice(
        model=f"{d}/model.int8.onnx", tokens=f"{d}/tokens.txt", language="ko", use_itn=True, num_threads=4)
        for k, d in MODELS.items()}
    tot = {k: [0, 0] for k in MODELS}
    for p in a.audio:
        x = load_audio(p)
        hyps = {}
        for k, r in recs.items():
            s = r.create_stream()
            s.accept_waveform(16000, x)
            r.decode_stream(s)
            hyps[k] = s.result.text
        name = os.path.basename(p)
        if name in labels:
            gold, how = labels[name], "지정"
        else:
            gold = min(sents, key=lambda t: min(edit(norm(t), norm(h)) / max(1, len(norm(t))) for h in hyps.values()))
            how = "추정"
        g = norm(gold)
        print(f"\n[{name}] {len(x) / 16000:.1f}초 · 정답({how}): {gold}  — {where(gold)}")
        for k, h in hyps.items():
            e = edit(g, norm(h))
            tot[k][0] += e
            tot[k][1] += len(g)
            print(f"  {k:6}: {h}   (CER {e / max(1, len(g)):.0%})")
    print("\n전체 CER: " + " | ".join(f"{k} {e / n:.1%}" for k, (e, n) in tot.items()))


if __name__ == "__main__":
    main()
