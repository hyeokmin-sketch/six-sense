# -*- coding: utf-8 -*-
"""파인튜닝한 SenseVoiceSmall → sherpa-onnx 용 model.int8.onnx (+ 한국어·영어 마스크).

sherpa-onnx 공식 export-onnx.py (models/sherpa-onnx-sense-voice-.../export-onnx.py) 와 같은 forward·메타데이터·
양자화(QUInt8, MatMul) 를 쓰고, 가중치만 파인튜닝 결과(best.pt)로 바꾼다. tokens.txt 는 원본과 동일.
실행: .venv\\Scripts\\python.exe stt\\export_onnx.py --ckpt 학습데이터\\runs\\tts_a1\\best.pt --dst models\\sense-voice-ko-tts_a1
"""
import argparse
import os
import shutil
import sys

import onnx
import torch
from funasr import AutoModel
from onnxruntime.quantization import QuantType, quantize_dynamic

sys.path.insert(0, os.path.dirname(__file__))
from make_ko_en_model import apply_mask  # noqa: E402

ORIG = r"models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"


def modified_forward(self, x, x_length, language, text_norm):
    language_query = self.embed(language).unsqueeze(1)
    text_norm_query = self.embed(text_norm).unsqueeze(1)
    event_emo_query = self.embed(torch.LongTensor([[1, 2]])).repeat(x.size(0), 1, 1)
    x = torch.cat((language_query, event_emo_query, text_norm_query, x), dim=1)
    x_length += 4
    encoder_out, _ = self.encoder(x, x_length)
    if isinstance(encoder_out, tuple):
        encoder_out = encoder_out[0]
    return self.ctc.ctc_lo(encoder_out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--dst", required=True)
    ap.add_argument("--model", default=r"models/SenseVoiceSmall")
    a = ap.parse_args()
    os.makedirs(a.dst, exist_ok=True)

    model, _ = AutoModel.build_model(model=a.model, device="cpu", disable_update=True)
    model.load_state_dict(torch.load(a.ckpt, map_location="cpu"))
    model.eval()
    model.__class__.forward = modified_forward

    fp32 = os.path.join(a.dst, "model.onnx")
    x = torch.randn(2, 100, 560, dtype=torch.float32)
    torch.onnx.export(
        model, (x, torch.tensor([80, 100], dtype=torch.int32), torch.tensor([0, 3], dtype=torch.int32),
                torch.tensor([14, 15], dtype=torch.int32)),
        fp32, opset_version=13, dynamo=False,
        input_names=["x", "x_length", "language", "text_norm"], output_names=["logits"],
        dynamic_axes={"x": {0: "N", 1: "T"}, "x_length": {0: "N"}, "language": {0: "N"},
                      "text_norm": {0: "N"}, "logits": {0: "N", 1: "T"}})

    # 메타데이터는 원본 sherpa 모델 것을 그대로 복사 (cmvn·언어 id·lfr 동일), 출처만 표시
    meta = {p.key: p.value for p in onnx.load(os.path.join(ORIG, "model.int8.onnx"), load_external_data=False).metadata_props}
    meta["comment"] = f"iic/SenseVoiceSmall fine-tuned: {a.ckpt}"
    m = onnx.load(fp32)
    del m.metadata_props[:]
    for k, v in meta.items():
        m.metadata_props.append(onnx.StringStringEntryProto(key=k, value=str(v)))
    onnx.save(m, fp32)

    int8 = os.path.join(a.dst, "model.int8.onnx")
    quantize_dynamic(model_input=fp32, model_output=int8, op_types_to_quantize=["MatMul"], weight_type=QuantType.QUInt8)
    os.remove(fp32)  # 보드는 int8 만 사용
    shutil.copy(os.path.join(ORIG, "tokens.txt"), os.path.join(a.dst, "tokens.txt"))
    apply_mask(int8, os.path.join(a.dst, "tokens.txt"), int8)
    print("저장:", a.dst, f"{os.path.getsize(int8) / 1e6:.0f} MB")


if __name__ == "__main__":
    main()
