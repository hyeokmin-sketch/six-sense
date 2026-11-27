# training/ — Colab 파인튜닝

학습은 Google Colab(T4)에서 합니다. 이 저장소에는 노트북과 데이터만 두고, 학습된 모델은 Drive에 둡니다.

## 순서
1. Drive에 폴더를 만듭니다: `내 드라이브/six-sense/data_v2/`
2. 이 저장소의 `data/train_v2_호출형식.jsonl`(학습 4,931개)과 `data/eval/` 안의 파일들을 그 폴더에 올립니다.
   - 노트북은 `train_v2_*.jsonl`이 **정확히 1개**여야 합니다. 옛 파일이 있으면 이름을 `백업_...`으로 바꾸세요.
3. `SIX-SENSE_LoRA_finetune_colab_v3.ipynb`를 Colab에서 엽니다. 맨 위 설정: `VERSION='v2dict3'`, `MODEL_KEY='llama'`, `RUN_TRAIN=True`(학습) 또는 `False`(저장된 어댑터로 평가만).
4. 0번 셀 실행 후 런타임을 재시작하고 1번 셀부터 순서대로 실행합니다. (Hugging Face 토큰과 Llama 약관 동의가 필요합니다.)
5. **15번 셀(병합 모델 저장)은 맨 마지막에** 실행합니다. 결과: `six-sense/runs/merged-llama-v2dict3`
6. 병합 모델 폴더를 내려받아 이 저장소의 `models/`에 넣으면 로컬 서버(`server/`)에서 쓸 수 있습니다.

## colab_server_cell.py
Colab에 올라온 모델을 임시 주소(터널)로 열어 대시보드가 부르게 하는 셀입니다. 모델을 내려받지 않고 잠깐 시험할 때만 쓰고, 일반적인 실행은 `server/`를 씁니다.
