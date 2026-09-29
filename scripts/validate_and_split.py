"""
3주차 산출물 — 데이터 검증 + train/val 8:2 분할 스크립트

역할:
  1. data/pilot_data.json (또는 지정한 파일)의 스키마를 검증한다.
     - messages: [system, user, assistant] role 순서/필드 확인
     - meta.category 값이 A_dialect/B_ambiguous/C_emotion/D_context 중 하나인지 확인
     - assistant.content가 비어있지 않은지, 너무 길지 않은지(느슨한 휴리스틱) 확인
  2. 문제 없는 레코드만 골라 8:2로 train/val 분할해
     data/train.jsonl, data/val.jsonl 로 저장한다. (jsonl = 한 줄에 레코드 하나)

사용법 (맥북 venv 활성화 후):
    python scripts/validate_and_split.py
    python scripts/validate_and_split.py --input data/pilot_data.json --seed 42
"""

import argparse
import json
import random
import sys
from collections import Counter
from pathlib import Path

VALID_CATEGORIES = {"A_dialect", "B_ambiguous", "C_emotion", "D_context"}
VALID_INPUT_SOURCES = {"voice", "sensor"}


def load_records(path: Path):
    with path.open(encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError("최상위 구조는 리스트([...])여야 합니다.")
    return data


def validate_record(idx: int, rec: dict) -> list[str]:
    """레코드 하나를 검사해서 문제 메시지 목록을 반환한다. 비어있으면 통과."""
    errors = []

    messages = rec.get("messages")
    if not isinstance(messages, list) or len(messages) != 3:
        errors.append("messages는 [system, user, assistant] 3개 항목이어야 함")
        return errors  # 구조가 깨지면 이후 검사는 의미 없음

    roles = [m.get("role") for m in messages]
    if roles != ["system", "user", "assistant"]:
        errors.append(f"messages role 순서가 잘못됨: {roles}")

    for role, m in zip(roles, messages):
        content = m.get("content")
        if not content or not isinstance(content, str) or not content.strip():
            errors.append(f"{role}.content가 비어 있음")

    assistant_content = messages[2].get("content", "")
    if assistant_content:
        if len(assistant_content) > 80:
            errors.append(f"assistant.content가 너무 김({len(assistant_content)}자) — 설명문 없이 한 문장 규칙 확인 필요")
        if assistant_content.count(".") + assistant_content.count("!") + assistant_content.count("?") > 2:
            errors.append("assistant.content에 문장이 여러 개 들어있을 가능성 (마침표/느낌표/물음표 과다)")

    meta = rec.get("meta")
    if not isinstance(meta, dict):
        errors.append("meta 필드가 없거나 dict가 아님")
    else:
        category = meta.get("category")
        # v1 pilot 데이터는 category 필드가 없을 수 있음(구 스키마) — 경고만 남기고 통과시킴
        if category is not None and category not in VALID_CATEGORIES:
            errors.append(f"meta.category 값이 유효하지 않음: {category}")
        input_source = meta.get("input_source")
        if input_source is not None and input_source not in VALID_INPUT_SOURCES:
            errors.append(f"meta.input_source 값이 유효하지 않음: {input_source}")

    return errors


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", default="data/pilot_data.json", help="검증할 원본 데이터 파일")
    ap.add_argument("--outdir", default="data", help="train.jsonl/val.jsonl을 저장할 폴더")
    ap.add_argument("--val-ratio", type=float, default=0.2, help="검증셋 비율 (기본 0.2 = 8:2 분할)")
    ap.add_argument("--seed", type=int, default=42, help="분할 셔플 시드 (재현성용)")
    args = ap.parse_args()

    in_path = Path(args.input)
    if not in_path.exists():
        print(f"[오류] 입력 파일을 찾을 수 없습니다: {in_path}", file=sys.stderr)
        sys.exit(1)

    records = load_records(in_path)
    print(f"총 {len(records)}건 로드: {in_path}")

    ok_records, bad_count = [], 0
    for i, rec in enumerate(records):
        errs = validate_record(i, rec)
        if errs:
            bad_count += 1
            print(f"  [레코드 {i}] 문제 {len(errs)}건:")
            for e in errs:
                print(f"    - {e}")
        else:
            ok_records.append(rec)

    print()
    print(f"검증 통과: {len(ok_records)}건 / 문제 있음: {bad_count}건")

    # 카테고리 분포 확인 (메타에 category가 있는 레코드만)
    cats = Counter(rec.get("meta", {}).get("category") for rec in ok_records)
    if any(cats):
        print("카테고리 분포:", dict(cats))
    regions = Counter(rec.get("meta", {}).get("region") for rec in ok_records if rec.get("meta", {}).get("region"))
    if regions:
        print("지역 분포:", dict(regions))

    if bad_count:
        print("\n※ 문제 있는 레코드는 분할에서 제외됩니다. 위 목록을 보고 원본 파일을 먼저 수정하는 걸 권장합니다.")

    if not ok_records:
        print("[오류] 검증을 통과한 레코드가 없어 분할을 진행할 수 없습니다.", file=sys.stderr)
        sys.exit(1)

    random.seed(args.seed)
    shuffled = ok_records[:]
    random.shuffle(shuffled)
    n_val = max(1, round(len(shuffled) * args.val_ratio))
    val_set = shuffled[:n_val]
    train_set = shuffled[n_val:]

    outdir = Path(args.outdir)
    outdir.mkdir(parents=True, exist_ok=True)
    train_path = outdir / "train.jsonl"
    val_path = outdir / "val.jsonl"

    with train_path.open("w", encoding="utf-8") as f:
        for rec in train_set:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    with val_path.open("w", encoding="utf-8") as f:
        for rec in val_set:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

    print(f"\n분할 완료 (val_ratio={args.val_ratio}, seed={args.seed})")
    print(f"  train: {len(train_set)}건 -> {train_path}")
    print(f"  val:   {len(val_set)}건 -> {val_path}")


if __name__ == "__main__":
    main()
