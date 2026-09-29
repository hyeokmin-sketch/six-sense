"""
4주차 산출물 — LoRA 파인튜닝 스크립트 골격

주의: GPU 서버가 아직 지급되지 않아 이 스크립트는 "실행 확인"이 안 된 상태다.
      맥북(CPU-only)에서는 베이스 모델이 커서 현실적으로 끝까지 돌리기 어렵다.
      지금은 코드 골격/설정값만 준비해두고, 서버 지급 즉시:
        1) requirements 설치 확인 (transformers, peft, trl, accelerate, bitsandbytes)
        2) --dry-run 없이 실행
      순서로 진행하면 된다.

사용법 (GPU 서버에서, 지급 후):
    python scripts/finetune_lora.py --train data/train.jsonl --val data/val.jsonl
    python scripts/finetune_lora.py --dry-run   # 코드/데이터 로딩만 점검 (모델 다운로드 없이)
"""

import argparse
import json
from pathlib import Path

BASE_MODEL = "meta-llama/Llama-3.2-1B-Instruct"


def load_jsonl(path: Path):
    records = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


def to_chat_text(rec: dict) -> str:
    """messages 배열을 모델이 학습할 하나의 텍스트로 직렬화.
    실제 실행 시에는 tokenizer.apply_chat_template()으로 교체 권장
    (Llama 3.2 Instruct 전용 채팅 템플릿을 그대로 써야 형식이 맞음)."""
    parts = []
    for m in rec["messages"]:
        parts.append(f"<|{m['role']}|>\n{m['content']}")
    return "\n".join(parts)


def build_argparser():
    ap = argparse.ArgumentParser()
    ap.add_argument("--train", default="data/train.jsonl")
    ap.add_argument("--val", default="data/val.jsonl")
    ap.add_argument("--base-model", default=BASE_MODEL)
    ap.add_argument("--output-dir", default="runs/lora-sixsense")
    ap.add_argument("--epochs", type=int, default=3)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--lora-r", type=int, default=16)
    ap.add_argument("--lora-alpha", type=int, default=32)
    ap.add_argument("--batch-size", type=int, default=4)
    ap.add_argument(
        "--dry-run",
        action="store_true",
        help="모델을 다운로드/로딩하지 않고 데이터 로딩·전처리만 점검",
    )
    return ap


def dry_run(args):
    """GPU/모델 없이도 지금 맥북에서 바로 돌려서 데이터 파이프라인만 확인하는 모드."""
    train_path, val_path = Path(args.train), Path(args.val)
    if not train_path.exists() or not val_path.exists():
        print("[오류] train/val jsonl이 없습니다. 먼저 scripts/validate_and_split.py 를 실행하세요.")
        return

    train_records = load_jsonl(train_path)
    val_records = load_jsonl(val_path)
    print(f"train: {len(train_records)}건 / val: {len(val_records)}건 로드 성공")

    sample = train_records[0]
    print("\n--- 직렬화 샘플 (train[0]) ---")
    print(to_chat_text(sample))

    print("\n[dry-run] 여기까지 정상이면 실제 학습 코드(모델 로딩·Trainer)로 넘어가도 됩니다.")
    print(f"  base_model = {args.base_model}")
    print(f"  lora_r={args.lora_r}, lora_alpha={args.lora_alpha}, epochs={args.epochs}, lr={args.lr}")


def real_run(args):
    """실제 파인튜닝. GPU 서버 지급 후 --dry-run 없이 실행."""
    try:
        import torch
        from datasets import Dataset
        from peft import LoraConfig, get_peft_model
        from transformers import (
            AutoModelForCausalLM,
            AutoTokenizer,
            TrainingArguments,
        )
        from trl import SFTTrainer
    except ImportError as e:
        print(f"[오류] 필요한 패키지가 설치되어 있지 않습니다: {e}")
        print("  GPU 서버에서: pip install transformers peft trl accelerate bitsandbytes datasets")
        return

    train_records = load_jsonl(Path(args.train))
    val_records = load_jsonl(Path(args.val))

    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    def to_text(rec):
        return tokenizer.apply_chat_template(rec["messages"], tokenize=False, add_generation_prompt=False)

    train_ds = Dataset.from_list([{"text": to_text(r)} for r in train_records])
    val_ds = Dataset.from_list([{"text": to_text(r)} for r in val_records])

    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        torch_dtype=torch.bfloat16,
        device_map="auto",
    )

    lora_config = LoraConfig(
        r=args.lora_r,
        lora_alpha=args.lora_alpha,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    training_args = TrainingArguments(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        learning_rate=args.lr,
        eval_strategy="epoch",
        save_strategy="epoch",
        logging_steps=10,
        bf16=True,
        report_to="none",
    )

    trainer = SFTTrainer(
        model=model,
        args=training_args,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        dataset_text_field="text",
        tokenizer=tokenizer,
    )

    trainer.train()
    trainer.save_model(args.output_dir)
    print(f"\n학습 완료. LoRA 어댑터 저장 위치: {args.output_dir}")


def main():
    args = build_argparser().parse_args()
    if args.dry_run:
        dry_run(args)
    else:
        real_run(args)


if __name__ == "__main__":
    main()
