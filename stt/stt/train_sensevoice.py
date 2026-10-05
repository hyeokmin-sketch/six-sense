# -*- coding: utf-8 -*-
"""SenseVoiceSmall(PyTorch) 파인튜닝 — FunASR 모델 클래스 + 직접 짠 학습 루프 (Windows 단일 GPU용).

정답 형식은 FunASR SenseVoiceCTCDataset 과 동일: [<|ko|>, <|NEUTRAL|>, <|Speech|>, <|withitn|>] + 문장 토큰.
loss = CTC(문장) + CE(앞 4개 태그). 학습률은 공식 config 값(2e-5).
매 epoch: 검증 jsonl CER + 원본 test_wavs(ko/en) 인식 결과(망각 확인) 출력, 검증 CER 최저 epoch 를 best 로 저장.
실행: .venv\\Scripts\\python.exe stt\\train_sensevoice.py --out 학습데이터\\runs\\tts_a1
"""
import argparse
import json
import math
import os
import random
import re
import time
import wave

import numpy as np
import torch
from funasr import AutoModel
from funasr.utils.load_utils import extract_fbank

TAGS = "<|ko|><|NEUTRAL|><|Speech|><|withitn|>"
SANITY = [r"models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/test_wavs/ko.wav",
          r"models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/test_wavs/en.wav"]


def load_wav(p):
    with wave.open(p) as w:
        return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768


def augment(x, rnd):
    if rnd.random() < 0.5:  # 속도 변화 0.9~1.1 (재샘플링)
        r = rnd.uniform(0.9, 1.1)
        idx = np.arange(0, len(x) - 1, r)
        x = np.interp(idx, np.arange(len(x)), x).astype(np.float32)
    x = x * rnd.uniform(0.3, 1.0)  # 음량
    if rnd.random() < 0.6:  # 잡음 SNR 10~35dB
        snr = rnd.uniform(10, 35)
        p = np.mean(x ** 2) + 1e-9
        x = x + np.random.randn(len(x)).astype(np.float32) * math.sqrt(p / 10 ** (snr / 10))
    if rnd.random() < 0.3:  # 앞뒤 무음
        x = np.concatenate([np.zeros(rnd.randint(0, 4000), np.float32), x, np.zeros(rnd.randint(0, 4000), np.float32)])
    return x


def strip_tags(s):
    return re.sub(r"<\|[^|]*\|>", "", s).strip()


def norm(s):
    return re.sub(r"[\s\.,!?~·…'\"]", "", s)


def edit(a, b):
    d = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        prev, d[0] = d[0], i
        for j, cb in enumerate(b, 1):
            prev, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, prev + (ca != cb))
    return d[-1]


@torch.no_grad()
def recognize(model, kw, wavs, bs=16):
    model.eval()
    outs = []
    for i in range(0, len(wavs), bs):
        res = model.inference(data_in=[torch.from_numpy(w) for w in wavs[i:i + bs]], key=[f"k{j}" for j in range(len(wavs[i:i + bs]))],
                              language="ko", use_itn=True, tokenizer=kw["tokenizer"], frontend=kw["frontend"],
                              device=kw["device"])[0]
        outs += [strip_tags(r["text"]) for r in res]
    return outs


def cer(model, kw, recs):
    hyps = recognize(model, kw, [load_wav(r["source"]) for r in recs])
    e = sum(edit(norm(r["target"]), norm(h)) for r, h in zip(recs, hyps))
    n = sum(len(norm(r["target"])) for r in recs)
    return e / n, hyps


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--train", default=r"학습데이터/tts/train.jsonl")
    ap.add_argument("--val", default=r"학습데이터/tts/val.jsonl")
    ap.add_argument("--model", default=r"models/SenseVoiceSmall")
    ap.add_argument("--out", required=True)
    ap.add_argument("--epochs", type=int, default=10)
    ap.add_argument("--bs", type=int, default=8)
    ap.add_argument("--lr", type=float, default=2e-5)
    ap.add_argument("--seed", type=int, default=1004)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    rnd = random.Random(a.seed)
    torch.manual_seed(a.seed)
    np.random.seed(a.seed)

    model, kw = AutoModel.build_model(model=a.model, device="cuda", disable_update=True)
    tok, fe = kw["tokenizer"], kw["frontend"]
    kw["device"] = "cuda"
    tag_ids = tok.encode(TAGS)
    assert len(tag_ids) == 4, tag_ids

    train = [json.loads(l) for l in open(a.train, encoding="utf-8")]
    val = [json.loads(l) for l in open(a.val, encoding="utf-8")]
    for r in train:
        r["ids"] = tag_ids + tok.encode(r["target"])
    sanity_wavs = [load_wav(p) for p in SANITY]

    def log(msg):
        print(msg, flush=True)
        with open(os.path.join(a.out, "train.log"), "a", encoding="utf-8") as f:
            f.write(msg + "\n")

    base_cer, _ = cer(model, kw, val)
    log(f"[epoch 0] val CER {base_cer:.1%} | sanity {recognize(model, kw, sanity_wavs)}")

    opt = torch.optim.AdamW([p for p in model.parameters() if p.requires_grad], lr=a.lr, weight_decay=0.01)
    steps = a.epochs * math.ceil(len(train) / a.bs)
    warm = max(1, steps // 20)
    sched = torch.optim.lr_scheduler.LambdaLR(
        opt, lambda s: min(1.0, (s + 1) / warm) * 0.5 * (1 + math.cos(math.pi * min(1.0, s / steps))))
    best = (base_cer, 0)
    step = 0
    for ep in range(1, a.epochs + 1):
        model.train()
        rnd.shuffle(train)
        t0, tot = time.time(), 0.0
        for i in range(0, len(train), a.bs):
            batch = train[i:i + a.bs]
            wavs = [torch.from_numpy(augment(load_wav(r["source"]), rnd)) for r in batch]
            speech, speech_len = extract_fbank(wavs, data_type="sound", frontend=fe)
            L = max(len(r["ids"]) for r in batch)
            text = torch.full((len(batch), L), -1, dtype=torch.long)
            for j, r in enumerate(batch):
                text[j, :len(r["ids"])] = torch.tensor(r["ids"])
            text_len = torch.tensor([len(r["ids"]) for r in batch], dtype=torch.long)
            with torch.autocast("cuda", dtype=torch.bfloat16):
                loss, stats, _ = model(speech.cuda(), speech_len.cuda(), text.cuda(), text_len.cuda())
            opt.zero_grad(set_to_none=True)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            opt.step()
            sched.step()
            step += 1
            tot += loss.item()
        n_b = math.ceil(len(train) / a.bs)
        v, hyps = cer(model, kw, val)
        log(f"[epoch {ep}] loss {tot / n_b:.3f} | val CER {v:.1%} | {time.time() - t0:.0f}s | "
            f"sanity {recognize(model, kw, sanity_wavs)}")
        if v < best[0]:
            best = (v, ep)
            torch.save(model.state_dict(), os.path.join(a.out, "best.pt"))
            with open(os.path.join(a.out, "best_val_hyps.jsonl"), "w", encoding="utf-8") as f:
                for r, h in zip(val, hyps):
                    f.write(json.dumps({"key": r["key"], "target": r["target"], "hyp": h}, ensure_ascii=False) + "\n")
    log(f"best epoch {best[1]} val CER {best[0]:.1%} (base {base_cer:.1%})")


if __name__ == "__main__":
    main()
