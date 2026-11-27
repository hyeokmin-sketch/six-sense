# SIX-SENSE 양자화·보드 평가 전달 자료 (Llama-3.2-1B, v2dict3)

작성: 2026.11.8 · 요청하신 1~8번에 대한 답과 파일입니다.

## 한눈에 보기

| # | 요청 | 상태 | 어디에 있나 |
|---|---|---|---|
| 1 | 병합 모델 폴더 | **Colab에서 저장 후 공유** (혁민이 15번 셀 실행) | Drive `six-sense/runs/merged-llama-v2dict3` |
| 2 | 입력 문자열 · 시스템 프롬프트 | system prompt **사용함** / jsonl 원본·시스템 프롬프트는 이 폴더, `apply_chat_template` 출력은 Colab 셀 ①로 확인 | `system_prompt.txt`, `input_sample.jsonl` |
| 3 | 종료 토큰 · 생성 설정 | 아래 표 | 이 문서 |
| 4 | 학습 데이터 4,931개 | 있음 (+ 보정 문장 예시 500개) | `../data/train_v2_호출형식.jsonl`, `calib_sample_500.jsonl` |
| 5 | 평가 문장 전체 | 있음 | `../data/eval/` |
| 6 | 평가셋 + 원본 점수 | 평가셋·집계 점수 있음 / **문장별 원본 예측은 Colab 셀 ②로 생성** | `../data/eval/`, 아래 6번 표 |
| 7 | 채점 코드 | 있음 | `score.py` |
| 8 | 동작 → 정답 호출 대응표 | 있음 | `action_call_table.csv` |

**"먼저 주면 시험 변환을 시작할 수 있는" 1~3번**은 혁민이 Colab에서 셀 3개(① 입력 확인, ② 문장별 예측, 15번 모델 저장)를 실행하면 같이 나옵니다. 그 전에는 이 폴더의 자료로 3번과 2번의 시스템 프롬프트까지 확인하실 수 있습니다.

---

## 1. 병합 모델

- 노트북 15번 셀이 `merge_and_unload()`로 LoRA를 합친 **전체 모델**을 `runs/merged-llama-v2dict3`에 저장합니다. 토크나이저 파일도 같은 폴더에 같이 저장됩니다.
- 베이스: `meta-llama/Llama-3.2-1B-Instruct`, **fp16**으로 불러와 합쳤습니다 (약 2.5GB).
- 라이선스: Llama 3.2 Community License가 적용됩니다. 팀 내 공유는 문제없지만, 외부 배포 전에는 약관 확인이 필요합니다.

## 2. 학습 때 입력 형식

- **system prompt를 썼습니다.** 짧은 한 줄이고 `system_prompt.txt`에 원문이 있습니다.
  `당신은 스마트홈 제어 비서입니다. 사용자의 사투리와 모호한 표현을 이해해 기능 호출 형식으로만 답하세요.`
- 학습·평가 모두 아래처럼 만들었습니다 (사용자 문장 `r['text']`):
  ```python
  tok.apply_chat_template([{'role':'system','content':SYSTEM},{'role':'user','content':r['text']}],
                          tokenize=False, add_generation_prompt=True, enable_thinking=False)
  ```
  `enable_thinking`은 Qwen용 옵션이라 Llama 템플릿에서는 무시됩니다.
- 토크나이즈는 `add_special_tokens=False`입니다. 템플릿 문자열에 `<|begin_of_text|>`가 이미 들어 있기 때문입니다.
- 학습 때 정답 부분은 `출력 + 종료 토큰(<|eot_id|>)`이고, **손실은 정답 부분에만** 걸었습니다.
- **Llama의 날짜 줄 (중요)**: Llama 3.2 템플릿은 system 메시지가 있어도 `Cutting Knowledge Date / Today Date` 줄을 앞에 붙이고, `Today Date`는 **실행한 날짜로 자동 채워집니다.** Colab 셀 ①의 실제 출력(11/8 실행)은 아래와 같습니다.
  ```
  '<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\nCutting Knowledge Date: December 2023\nToday Date: 08 Nov 2026\n\n당신은 스마트홈 제어 비서입니다. 사용자의 사투리와 모호한 표현을 이해해 기능 호출 형식으로만 답하세요.<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n에어컨 돌리라.<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n'
  ```
  - v2dict3는 11/7에 학습했으므로 **학습·첫 평가 때의 날짜 줄은 `Today Date: 07 Nov 2026`**이었을 가능성이 큽니다 (Colab 시계 기준 추정).
  - **보드에서는 날짜를 자동으로 바꾸지 말고 `07 Nov 2026`으로 고정**해 주세요. HF 템플릿에서는 `apply_chat_template(..., date_string='07 Nov 2026')`로 지정할 수 있고, 템플릿을 직접 만들 때는 위 문자열의 날짜만 그대로 쓰면 됩니다.
  - 날짜가 점수에 얼마나 영향을 주는지는 `colab_cells.py`의 "셀 ①-2"가 확인합니다 (혁민 Colab 실행). 결과는 따로 전달합니다.
- 정답 부분은 `<six_0>(object=2, type=1)<six_end><|eot_id|>`처럼 호출 뒤에 `<|eot_id|>`가 붙은 형태로 학습했습니다.

## 3. 종료 토큰 · 생성 설정

| 항목 | 값 |
|---|---|
| 종료 토큰 | `<|eot_id|>` (id **128009**, 셀 ① 출력으로 확인) · pad 토큰 `<|finetune_right_pad_id|>` (128004) |
| 생성 방식 | **greedy** (`do_sample=False`) — **반드시 명시.** 병합 모델의 generation_config 기본값이 `do_sample=true, temperature=0.6, top_p=0.9`라서 그대로 두면 샘플링됩니다 |
| eos 토큰 | 정지는 `128009`(`<|eot_id|>`). 기본 설정에는 128001, 128008, 128009가 함께 들어 있음 |
| 최대 생성 길이 | 학습·평가 모두 `max_new_tokens=80` (정답은 호출 1~3개, 최장 102자) |
| 보드 권장 | 종료 토큰에서 멈추므로 넉넉히 128 정도로 두어도 결과는 같습니다 |
| 디코딩 | 생성 부분만 `skip_special_tokens=True` 후 `strip()` |
| 패딩 | 배치 평가는 left padding, 문장 1개씩이면 불필요 |

## 4. 학습 데이터와 보정 문장

- `../data/train_v2_호출형식.jsonl` (키: text, output, functions, category, source, id)
  - 구성: 단일 3,072 · 복합 1,044 · 해당 없음 644 · 되묻기 171
  - `source`가 `stt_aug:`로 시작하면 TTS→STT를 거친 글자가 깨진 입력, 아니면 템플릿 문장입니다.
- **일부 학습 문장은 평가 문장과 글자가 같습니다.** 평가 문장과 겹치는 306개를 제외한 풀(4,625개)에서 `calib_sample_500.jsonl`을 뽑아 두었습니다 (유형·STT 비율을 학습 데이터와 비슷하게 맞춤, STT 오류 문장 126개 포함). 300~1,000개가 필요하면 같은 방식으로 다시 뽑아 드릴 수 있습니다.

## 5. 평가 문장 목록 (`../data/eval/`)

| 파일 | 사용 문장 수 | 설명 |
|---|---|---|
| `val_v2.jsonl` | 400 | 검증셋 |
| `eval_50대_대구_v2.jsonl` | 36 (60 중 `needs_review` 제외) | 50대 평가 |
| `eval_v3_20_30s.jsonl` | 89 (108 중 제외) | 20·30대 |
| `eval_v3_해당없음_50대검수.jsonl` | 29 (30 중 제외) | 해당 없음 |
| `eval_해당없음_신규물건.jsonl` | 54 | 학습에 없는 물건의 해당 없음 |
| `eval_stt_dict_holdout.jsonl` | 330 | 사전 적용 STT 글자 holdout |
| `eval_stt_aug.jsonl` | 708 | 글자가 깨진 STT 입력 (옛 TTS) |
| `eval_real50_dict.jsonl` / `eval_real50_raw.jsonl` | 95 / 95 | **실제 50대 녹음** STT 결과 (사전 적용 / 미적용). 팀 대외비 자료라 **이 공개 저장소에는 포함하지 않았습니다.** |

- 사용 문장 수는 노트북이 `needs_review`가 붙은 문장을 뺀 수입니다. `채점`할 때 같은 필터를 적용해 주세요 (파일 안의 `needs_review` 값이 참인 행 제외).
- 실제 녹음 95문장은 학습·보정에 쓰지 않았습니다. 새로 녹음하는 문장도 보정 풀과 섞이지 않게 해 주세요.

## 6. 원본 점수 (Colab, T4, fp16, LoRA 적용 모델, 정확 일치 기준, 11/7 실행 = 날짜 줄 `07 Nov 2026`)

| 평가셋 | 점수 |
|---|---|
| val | 99.0% (396/400) |
| 50대 v2 | 100% (36/36) |
| 20·30대 v3 | 97.8% (87/89) |
| 해당 없음 50대검수 | 조작 오류 **0/29** |
| 해당 없음 신규물건 | 조작 오류 **0/54**, 그 외 오답 0 |
| 사전 holdout | 98.2% (324/330) |
| eval_stt_aug | 88.4% (626/708), 해당 없음 조작 오류 3/83 |
| 실제 50대 녹음 (사전 적용) | 94.7% (90/95) |
| 실제 50대 녹음 (사전 미적용) | 94.7% (90/95) |

- **문장별 원본 예측**은 `colab_cells.py`의 셀 ②가 `runs/original_preds_llama_v2dict3.csv`로 저장합니다 (열: set, id, text, gold, pred, correct). 양자화 모델도 같은 형식의 CSV로 만들면 `score.py`로 바로 비교됩니다.
- 위 점수는 **병합 전 LoRA 적용 모델** 기준입니다. 병합 후 fp16 모델은 반올림 차이로 몇 문장이 달라질 수 있으니, 병합본을 PC에서 한 번 돌려 **병합본 기준 점수**를 기준선으로 삼는 것을 권합니다 (전체 약 2,100문장, 실제 녹음 95문장만 먼저 봐도 됩니다).

## 7. 채점 코드

`score.py` (노트북의 `score` / `lenient`와 같은 기준)

```
python score.py original_preds_llama_v2dict3.csv                 # 원본 점수표
python score.py original_preds_llama_v2dict3.csv quant_preds.csv  # 원본 vs 양자화 비교표 (바뀐 문장 수 포함)
```

기준: **정확 일치**(출력 문자열이 정답과 완전히 같음), **형식 준수**(호출 형식 정규식), **조작 오류**(정답이 해당 없음인데 기기를 조작하는 호출을 냄), **앞부분 인정**(첫 호출들만 맞아도 인정).

## 8. 동작 → 정답 호출 대응표

`action_call_table.csv`

| 기기 | 동작 | 정답 호출 |
|---|---|---|
| 조명 | 켜기 / 끄기 | `<six_0>(object=1, type=1)<six_end>` / `type=2` |
| 조명 | 더 밝게 / 은은하게 | `<six_1>(object=1, type=1)<six_end>` / `type=2` |
| 에어컨 | 켜기 / 끄기 | `<six_0>(object=2, type=1)<six_end>` / `type=2` |
| 에어컨 | 온도 올리기 / 내리기 | `<six_1>(object=2, type=1)<six_end>` / `type=2` |
| 에어컨 | 바람 세게 / 약하게 | `<six_1>(object=2, type=3)<six_end>` / `type=4` |
| 블라인드 | 올리기 / 내리기 | `<six_0>(object=3, type=1)<six_end>` / `type=2` |
| 블라인드 | 멈추기 / 반쯤 내리기 | `<six_0>(object=3, type=3)<six_end>` / `type=4` |
| (되묻기) | 어떤 기기인지 불분명 | `<six_0>(object=-1)<six_end>` |
| (해당 없음) | 기기와 관계없는 말 | `<six_2>()<six_end>` |

- **복합 명령**은 말한 순서대로 호출을 이어 붙입니다. 예: 블라인드 올리고 불 켜기 → `<six_0>(object=3, type=1)<six_end><six_0>(object=1, type=1)<six_end>` (순서가 다르면 오답으로 채점됩니다).
- 새로 녹음한 문장에 정답을 붙일 때, 실제로 학습·평가에 많이 쓴 동작은 에어컨 켜기/끄기/온도↑↓, 조명 켜기/끄기, 블라인드 올리기/내리기 8가지입니다.
