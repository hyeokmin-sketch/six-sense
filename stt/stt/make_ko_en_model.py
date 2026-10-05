# -*- coding: utf-8 -*-
"""SenseVoice int8 ONNX 에 출력 마스크를 붙여 한국어·영어(ASCII)만 나오게 만든다.

logits 마지막에 Add(mask) 노드 하나만 추가 → 한자·가나·기타 문자 토큰은 -1e4 로 눌려 절대 선택되지 않음.
입출력 이름/모양이 그대로라 sherpa-onnx(보드 voice_agent.py 포함)에서 파일만 바꿔 쓰면 된다.
원본은 건드리지 않고 models/sense-voice-ko-en/ 에 새로 저장.
실행: .venv\\Scripts\\python.exe stt\\make_ko_en_model.py
"""
import os
import shutil

import numpy as np
import onnx
from onnx import helper, numpy_helper

SRC = r"models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"
DST = r"models/sense-voice-ko-en"
NEG = -1e4


def allowed(piece: str) -> bool:
    if piece.startswith("<") and piece.endswith(">"):  # <unk>(blank), <|ko|>, <|NEUTRAL|> 등 특수 토큰
        return True
    for ch in piece.replace("▁", ""):
        o = ord(ch)
        if o < 128:  # 영어·숫자·기본 기호
            continue
        if 0xAC00 <= o <= 0xD7A3 or 0x1100 <= o <= 0x11FF or 0x3130 <= o <= 0x318F:  # 한글
            continue
        return False
    return True


def apply_mask(src_onnx, tokens_txt, dst_onnx):
    """src_onnx 의 logits 뒤에 한국어·영어 마스크를 붙여 dst_onnx 로 저장 (같은 경로면 덮어씀)."""
    pieces = []
    for line in open(tokens_txt, encoding="utf-8"):
        p, i = line.rstrip("\n").rsplit(" ", 1)
        pieces.append((int(i), p))
    vocab = max(i for i, _ in pieces) + 1
    mask = np.full(vocab, NEG, dtype=np.float32)
    for i, p in pieces:
        if allowed(p):
            mask[i] = 0.0
    print(f"vocab {vocab}: 허용 {int((mask == 0).sum())}, 차단 {int((mask != 0).sum())}")

    m = onnx.load(src_onnx)
    g = m.graph
    assert g.output[0].name == "logits"
    for n in g.node:  # 기존 logits 를 내부 이름으로 바꾸고, 마스크를 더한 결과를 logits 로 내보냄
        n.output[:] = ["logits_raw" if o == "logits" else o for o in n.output]
    g.initializer.append(numpy_helper.from_array(mask, "ko_en_mask"))
    g.node.append(helper.make_node("Add", ["logits_raw", "ko_en_mask"], ["logits"], name="ko_en_mask_add"))
    m.metadata_props.append(onnx.StringStringEntryProto(key="ko_en_mask", value="hangul+ascii+special only"))

    onnx.save(m, dst_onnx)
    onnx.checker.check_model(dst_onnx)


def main():
    os.makedirs(DST, exist_ok=True)
    apply_mask(os.path.join(SRC, "model.int8.onnx"), os.path.join(SRC, "tokens.txt"), os.path.join(DST, "model.int8.onnx"))
    shutil.copy(os.path.join(SRC, "tokens.txt"), os.path.join(DST, "tokens.txt"))
    print("저장:", DST)


if __name__ == "__main__":
    main()
