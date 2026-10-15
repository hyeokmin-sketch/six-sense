# SIX-SENSE STT — 경상도 사투리 SenseVoice 파인튜닝

담당: 김용희. 진행 요약은 [`문서/SIX-SENSE_STT_진행요약_v1.0.md`](문서/SIX-SENSE_STT_진행요약_v1.0.md), 전체 기록은 [`문서/SIX-SENSE_STT_작업정리.md`](문서/SIX-SENSE_STT_작업정리.md).

## 현재 성능 (tts_a1, CER)

| 평가 데이터 | 원본 | tts_a1 |
|---|---|---|
| 학습 문장 (852) | 41.1% | 5.2% |
| 검증 문장 (216) | 42.0% | 19.0% |
| 처음 보는 문장 (120) | 38.0% | 22.0% |
| 실제 녹음 (7) | 56.8% | 2.3% |

## 표준어 성능 · 오인식 사전 (2026-10-15)

표준어 TTS 8,000개(일반 1,000문장 + 블라인드·에어컨·조명 3,000문장 × 목소리 2개)로 평가했습니다.
원본 모델의 CER은 일반 4.2%, 도메인 5.2%입니다(SAPI 목소리 기준).
결과는 [`문서/평가지표/SIX-SENSE_STT_표준어평가.md`](문서/평가지표/SIX-SENSE_STT_표준어평가.md)에 있습니다.
LLM 보정용 사전은 `문서/평가지표/STT_오인식사전_LLM용_원본.json`입니다(예: `거`→`꺼`, `플라인드`→`블라인드`, `3 개`→`세게`).

```powershell
python stt\std_sentences.py; python stt\make_std_tts.py
python stt\eval_std.py --models <models 폴더>
python stt\build_confusion_dict.py --model 원본; python stt\build_confusion_dict.py --model tts_a1
python stt\report_std.py; python stt\make_dict_xlsx.py
```

## 폴더

```
stt/                 ← 모든 명령은 이 폴더에서 실행
├─ 문서/              요약 · 작업정리 · ABC 데이터셋(xlsx) · 데이터셋 양식
│  └─ 평가지표/       표준어 평가 보고서 · 평가요약.txt · stt_딕셔너리.xlsx · 오인식 사전 json
├─ stt/               학습·변환·평가 코드
├─ data_build/        ABC 데이터셋 엑셀 생성
└─ 학습데이터/
   ├─ tts/            train.jsonl · val.jsonl (wav는 저장소에 없음)
   ├─ unseen/         처음 보는 문장 평가셋 jsonl (학습 금지)
   ├─ 녹음/테스트/     실제 녹음 7개 + 정답.txt
   ├─ runs/tts_a1/    학습 로그 · 검증 출력
   └─ eval/           평가 결과 csv
```

## 저장소에 없는 것 (용량)

| 항목 | 크기 | 받는 법 |
|---|---|---|
| `models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/` | 1.1GB | sherpa-onnx 공식 릴리스 (보드 `~/voice/models`에도 있음) |
| `models/SenseVoiceSmall/` | 0.9GB | Hugging Face `FunAudioLLM/SenseVoiceSmall` |
| `models/vits-mimic3-ko_KO-kss_low/` | 60MB | sherpa-onnx TTS 모델 릴리스 |
| `models/sense-voice-ko-en/` | 239MB | `python stt/make_ko_en_model.py` |
| `models/sense-voice-ko-tts_a1/` (보드용 최신) | 239MB | 김용희에게 따로 받기, 또는 아래 순서로 재생성 |
| `학습데이터/tts/wav/`, `학습데이터/unseen/wav/` | 63MB | `make_tts_data.py`, `make_unseen_data.py` |
| `학습데이터/runs/tts_a1/best.pt` | 936MB | `train_sensevoice.py` |

jsonl의 `source`는 원래 PC의 절대경로라서, 다른 PC에서는 `make_tts_data.py`를 다시 돌려 wav와 jsonl을 새로 만들어야 합니다.

## 재현 순서

환경: Python 3.11, torch(cu128), funasr 1.4.16, onnx, onnxruntime, sherpa-onnx 1.13, kaldi-native-fbank, PyAV, openpyxl, scipy.

```powershell
python data_build\make_abc_dataset.py              # 엑셀 재생성 (선택)
python stt\make_tts_data.py                        # TTS 학습·검증 음성
python stt\train_sensevoice.py --out 학습데이터\runs\tts_a1 --epochs 10
python stt\export_onnx.py --ckpt 학습데이터\runs\tts_a1\best.pt --dst models\sense-voice-ko-tts_a1
python stt\make_unseen_data.py                     # 처음 보는 문장 평가셋
python stt\eval_all.py                             # 학습/검증/처음 보는 문장 성능 표
```
