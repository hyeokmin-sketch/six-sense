# SIX-SENSE STT 작업정리 (인수인계)

> 마지막 갱신: 2026-10-05 · 작성: Claude (사용자 김용희와 작업)
> **새 세션은 이 문서부터 읽는다.** 이 문서만으로 지금 상태를 그대로 이어갈 수 있게 썼다.
> 2026-10-03 이전 문서(작업정리 PDF 01~03, LLM 명령 모델 cmd_A~E)는 **계획 변경으로 전부 삭제됨** — 참고하지 말 것.

---

## 0. 한 줄 현재 상태

경상도 사투리 문장 178개를 TTS로 음성화해 **SenseVoiceSmall을 파인튜닝(tts_a1)** → 보드용 int8 ONNX(`models/sense-voice-ko-tts_a1`)까지 완료.
사용자 실제 녹음, 학습에 안 쓴 문장 5개에서 **CER 원본 48.5% → tts_a1 3.0%**. 다음 단계는 사용자에게 제안만 해 둔 상태(9절).

---

## 1. 프로젝트와 내 역할

- 한성대 모바일시스템응용프로젝트 **SIX-SENSE** (8반 4팀 "3김한 취뽀"), 산업체 멘토 마음AI(maum.ai).
- 최종 시스템: Orange Pi 5 Plus(RK3588)에서 완전 오프라인 음성 스마트홈 비서. 마이크 → VAD → **STT** → LLM(NPU) → TTS.
- **사용자(김용희) = STT 담당.** 목표: **경상도 사투리 음성 → 최대한 온전한 사투리 텍스트** (표준어 변환·명령 생성은 STT 역할 아님). 최종 목표는 오류 최소화.
- 팀원 역할(수행계획서 기준): 한혁민(팀장·아키텍처), 김형균(온디바이스 AI·톤/감정), 김용희, 김여진(UX).

## 2. 확정된 결정 (사용자 지시, 2026-10-04)

| 항목 | 결정 |
|---|---|
| STT 모델 | 특강 PDF에 나온 **SenseVoice**만 사용. 보드 = sherpa-onnx `model.int8.onnx`. 파인튜닝 = PyTorch 원본 **SenseVoiceSmall** (HF `FunAudioLLM/SenseVoiceSmall` = `iic/SenseVoiceSmall`) |
| 사투리 지역 | **경상도로 통일** (충청도 안 씀. 수행계획서의 "경상도·충청도" 문구는 나중에 고쳐야 함) |
| IoT 기기 | **블라인드·조명·에어컨 3개**만 |
| 출력 언어 | 한국어·영어만 (일본어·한자 출력 차단) |
| 데이터셋 양식 | `문서/데이터셋_양식.png` 양식: 카테고리 / 입력 발화 / 정답(제어 명령, 자연어 한 문장) / 지역·감정 태그 / 작성자 / 비고. A=경상도 사투리, B=모호한 표준어, C=톤 기반 감정추론 |
| 이전 작업 | LLM 모델·데이터·스크립트, AI Hub 원본(131GB), 이전 녹음 전부 **사용자 지시로 삭제** (10/04) |

- 근거 문서: `한성대학교_특강2_통합_LLM학습_양자화_포팅.pdf` 34~36쪽(STT = sherpa-onnx SenseVoice int8, CPU 4스레드, `language="ko"`, `use_itn=True`, "전 팀 동일 모델"), 수행계획서 4쪽("소량 자체 음성 샘플로 STT 후처리 사전 또는 **제한적 파인튜닝**으로 보정").
- ⚠ 특강은 "전 팀 동일 모델"이라고 함 → 파인튜닝 모델로 바꿔 써도 되는지 **멘토 확인 필요**(아직 안 함).
- 특강은 LLM 학습만 다루고 **STT 학습 방법은 없음**. FunASR 파인튜닝은 내 판단(공식 SenseVoice 방식).

## 3. 데이터셋 (엑셀)

- `문서/SIX-SENSE_ABC_데이터셋_경상도.xlsx` — 생성 스크립트 `data_build/make_abc_dataset.py` (문장 목록이 코드 안에 있음. 수정 후 재실행하면 엑셀 재생성. 엑셀이 열려 있으면 저장 실패 → 닫고 실행).
- 예시 3행(이미지 그대로) + 307문장: **A 177(전부 경상도) / B 75 / C 55**. 작성자 열 = "Claude 초안"(팀 검수 필요).
- 비고 열 = "기기 / 동작" (예: `에어컨 / 조정(온도↓)`). 동작: 조명 켜기·끄기·밝게·어둡게, 블라인드 올리기·내리기·반만·멈추기, 에어컨 켜기·끄기·온도↑↓·바람↑↓, 복합(여러 기기).
- 정답 문장은 동작별 통일(예: 에어컨 켜기 = "에어컨을 켜고 24도로 설정할게요."). 온도 수치(24도, 2도)는 내가 임의로 정한 것 — 팀 기준 있으면 바꿀 것.
- STT 학습에는 **A 178문장(예시 "불 좀 꺼주이소" 포함)만** 사용. 정답 텍스트 = 입력 발화 그대로(사투리 철자).

## 4. 환경 (집 PC)

- Windows 11, RTX 4060 8GB, 프로젝트 폴더 `C:\Users\kimyo\Documents\애옹` (git 아님).
- **항상 `.venv\Scripts\python.exe`** (Python 3.11). 주요 패키지: torch 2.11+cu128, funasr 1.4.16, onnx 1.23.1, onnxruntime 1.30.0, kaldi-native-fbank 1.22.3, sherpa-onnx 1.13.x, PyAV, openpyxl, scipy, modelscope(funasr 의존).
- **Windows 스마트 앱 컨트롤 주의**:
  - pyarrow `.pyd`가 차단됨 → datasets·pyarrow를 venv에서 지웠음. **다시 설치하지 말 것.**
  - numba `_devicearray`도 차단됨 → funasr import 때 ImportError 로그가 여러 줄 나오지만 **무해**(학습·추론 정상). 로그 볼 때 필터링.
  - 새로 막히면 이벤트 뷰어 `Microsoft-Windows-CodeIntegrity/Operational` 3033/3077.
- **espeak-ng(TTS)는 한글 경로를 못 읽음** → TTS 모델을 `C:\Users\kimyo\sixsense_tts\vits-mimic3-ko_KO-kss_low`로 복사해 사용(`make_tts_data.py`가 자동 복사, 환경변수 `SIXSENSE_TTS_DIR`로 변경 가능).
- ffmpeg 없음 → 오디오 디코딩은 PyAV(`transcribe.py`의 `load_audio`). funasr의 "ffmpeg is not installed" 안내는 무시해도 됨.
- 사용자는 게임·Wallpaper Engine 등을 켜 둠 → VRAM 경쟁. 학습 bs 8에서 약 7GB 사용(문제없었음). 사용자 프로그램은 끄지 말 것.
- PowerShell 5.1: 와일드카드 `Remove-Item 경로\*`는 차단됨 → 파일을 하나씩 `-LiteralPath`로.

## 5. 폴더·파일 지도

```
애옹/
├─ 문서/
│  ├─ SIX-SENSE_STT_작업정리.md          ← 이 문서
│  ├─ SIX-SENSE_STT_진행요약_v1.0.md     ← 팀·멘토 공유용 요약 (버전 올릴 때 새 파일 + 변경 이력 표 갱신)
│  ├─ SIX-SENSE_ABC_데이터셋_경상도.xlsx   ← ABC 데이터셋
│  ├─ 데이터셋_양식.png                   ← 팀 데이터셋 양식 (구 image.png)
│  └─ 참고자료/  특강2 PDF / 수행계획서 PDF / 3·4주차 발표 pptx / HomeIoT 데이터 시트 xlsx
├─ models/
│  ├─ sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/  ← 원본 보드용(int8)+test_wavs+공식 export-onnx.py
│  ├─ sense-voice-ko-en/          ← 원본 + 한국어·영어 마스크
│  ├─ sense-voice-ko-tts_a1/      ← ★ 파인튜닝 + int8 + 마스크 (현재 최신, 239MB) = 보드에 올릴 후보
│  ├─ SenseVoiceSmall/            ← 학습용 PyTorch 원본(model.pt, config.yaml, am.mvn, bpe.model). HF rev 3847d57b
│  └─ vits-mimic3-ko_KO-kss_low/  ← 한국어 VITS TTS (단일 화자, 표준 억양)
├─ 학습데이터/
│  ├─ tts/   wav/(1,068개) train.jsonl(852) val.jsonl(216)  ← jsonl source는 절대경로
│  ├─ unseen/  엑셀에 없는 새 문장 60 × 2 = 120개 (고정 평가셋, 학습 금지)
│  ├─ runs/tts_a1/  best.pt(epoch 9) train.log best_val_hyps.jsonl
│  ├─ eval/  base_*.csv(원본) tts_a1_*.csv
│  └─ 녹음/테스트/  사용자 실제 녹음 7개 + 정답.txt
├─ data_build/make_abc_dataset.py      ← 엑셀 생성
├─ stt_eval/run_stt_eval.py            ← (구) AI Hub zip용 CER 측정. 데이터 삭제돼 지금은 못 씀
└─ stt/   (코드만)
   ├─ make_ko_en_model.py   ← 마스크 붙이기 (apply_mask 함수, export_onnx가 재사용)
   ├─ make_tts_data.py      ← 엑셀 A → TTS wav + train/val.jsonl
   ├─ train_sensevoice.py   ← 파인튜닝
   ├─ export_onnx.py        ← best.pt → int8 ONNX + 마스크
   ├─ eval_cer.py           ← jsonl 평가 (CER·완전일치·csv)
   ├─ compare_models.py     ← test_wavs(또는 지정 wav)로 모델별 출력 비교
   ├─ transcribe.py         ← 녹음 1개 → 3개 모델 결과 (+ --text 정답 CER)
   ├─ make_unseen_data.py   ← 처음 보는 문장 평가셋 생성
   ├─ eval_all.py           ← 세 모델 × 학습/검증/처음 보는 문장 표
   └─ transcribe_batch.py   ← 녹음 여러 개 (+ --labels "파일=문장", 없으면 엑셀에서 정답 추정)
```
- 2026-10-05 폴더 정리: 문서는 `문서/`, 학습 데이터·체크포인트·평가 결과는 `학습데이터/`로 이동. 스크립트 기본 경로와 jsonl 절대경로도 함께 수정함.
## 6. 기술 메모 (다시 할 때 필요한 것)

### 6-1. 한국어·영어 마스크 (`make_ko_en_model.py`)
- SenseVoice int8 ONNX의 마지막 출력 `logits`(N, T, 25055) 뒤에 `Add(mask)` 노드 1개 추가. 입출력 이름·모양 그대로 → sherpa-onnx / 보드 `voice_agent.py` 코드 수정 없이 파일만 교체.
- 허용 14,717 = 한글 4,830 + ASCII 9,712 + 특수 `<...>` 174 + `▁`. 차단 10,338 = 한자 10,123 + 가나 173 + 기타 42(…, “ ” 등). 차단 값 -1e4.
- 효과/한계: 한국어·영어 출력은 원본과 동일, 일본어로 나오던 사투리는 빈칸·엉뚱한 한글이 됨 → **뜻 복원은 파인튜닝이 해야 함.**

### 6-2. TTS 데이터 (`make_tts_data.py`)
- 문장마다 VITS 6변형 (length_scale, noise_scale, noise_scale_w) = (1.0,.667,.8) (0.85,.667,.8) (1.15,.667,.8) (0.95,.9,1.0) (1.1,.4,.6) (0.9,.8,.9). 22.05kHz → 16kHz(scipy resample_poly), 피크 0.9 정규화.
- **검증은 문장 단위 분리**: 비고(기기/동작) 그룹마다 20%(3문장 이상인 그룹만), seed 1004 → 학습 142문장 / 검증 36문장.
- jsonl 형식(FunASR SenseVoice 공식과 같음): `key, source(절대경로), source_len, target, target_len, text_language "<|ko|>", emo_target "<|NEUTRAL|>", event_target "<|Speech|>", with_or_wo_itn "<|withitn|>"` + `group, sent_id`.

### 6-3. 학습 (`train_sensevoice.py`)
- FunASR `AutoModel.build_model(model="models/SenseVoiceSmall")`로 모델·tokenizer·frontend 로드 → **직접 짠 학습 루프** (FunASR train_ds/torchrun은 Windows에서 번거로워 안 씀).
- 정답 ids = `tok.encode("<|ko|><|NEUTRAL|><|Speech|><|withitn|>")`(4개) + `tok.encode(문장)`. 패딩 -1. loss = CTC(문장) + CE(태그 4개) — `model.forward` 그대로.
- 특징: `funasr.utils.load_utils.extract_fbank` (kaldi-native-fbank 필요 — sherpa와 같은 fbank).
- 설정: AdamW lr 2e-5(공식 config 값), wd 0.01, warmup 5% + cosine, bs 8, 10 epoch, grad clip 5, bf16 autocast. epoch당 약 35초, 전체 약 6분.
- 파형 증강: 속도 0.9~1.1(50%), 음량 0.3~1.0, 잡음 SNR 10~35dB(60%), 앞뒤 무음(30%).
- 매 epoch 검증 CER + 원본 test_wavs ko/en 인식(망각 확인), 최저 CER을 `best.pt`(state_dict, 936MB)로 저장.

### 6-4. 보드용 변환 (`export_onnx.py`)
- sherpa 공식 `export-onnx.py`와 같은 `modified_forward`(언어·이벤트/감정·itn 쿼리 4개 앞에 붙임) + opset 13 + `dynamo=False`.
- 메타데이터(cmvn, lfr 7/6, 언어 id 등)는 원본 sherpa int8 모델 것을 복사, comment만 변경. `quantize_dynamic(MatMul, QUInt8)`(QInt8은 C++ onnxruntime에서 결과 틀림 — sherpa 주석). fp32 중간 파일 삭제, tokens.txt는 원본 복사, 마지막에 마스크 적용.

## 7. 실험 결과

### 7-1. TTS 검증셋 (학습에 안 쓴 36문장 × 6변형 = 216개)
| 모델 | CER | 문장 완전일치 |
|---|---|---|
| 원본(마스크) | 42.0% | 2.8% |
| **tts_a1 (int8)** | **19.0%** | **23.6%** |
| (참고) tts_a1 학습 문장 852개 | 5.2% | 64.6% |

학습 로그 검증 CER: 42.0 → 32.6 → 26.2 → 22.5 → 20.7 → 20.0 → 19.8 → 19.5 → 19.3 → **19.2(ep9, best)** → 19.3. PyTorch 19.2% ≈ int8 19.0% → 양자화 손실 없음.

### 7-2. 사용자 본인 실제 녹음 (`학습데이터/녹음/테스트/`, 정답은 `정답.txt`)
| 녹음 | 문장 | 구분 | 원본 | tts_a1 |
|---|---|---|---|---|
| 녹음 (163) | 불 좀 키도 | 학습 | 볼 좀 기도. | 불 좀 키도 ✔ |
| 녹음 (164) | 와 이리 컴컴하노 | 학습 | ワりここマの。 | 와 이리 컴컴하노 ✔ |
| 녹음 | 거실 불 좀 키래이 | 검증 | 거실 불 좀 기레이이. | 거실 불 좀 키래이 ✔ |
| 녹음 (2) | 블라인드 좀 내리도 | 검증 | 블라인드 좀 내리둬. | 블라인드 좀 내리도 ✔ |
| 녹음 (3) | 마 불 꺼삐라 | 검증 | 마 볼거라. | 마 불 꺼삐라 ✔ |
| 녹음 (4) | 추버 죽겠네 | 검증 | 추어 죽 겠 네. | 추버 죽 겠네 ✔ |
| 녹음 (5) | 스톱 스톱 고마해라 | 검증 | ストストゴマ라。 | 시톱 스톱 고마 해라 |

- 검증 문장 5개 전체 CER: **원본 48.5% / ko-en 45.5% / tts_a1 3.0%** (사용자가 "학습에 안 쓴 문장을 녹음했다"고 확인).
- 녹음 음량 최대 1.04~1.28(살짝 클리핑) — 다음엔 마이크와 거리 두기 권장.

### 7-2b. 처음 보는 문장 평가 (2026-10-05, `stt/eval_all.py`)
- `stt/make_unseen_data.py`: 엑셀에 없는 새 문장 60개(경상도 기기 30 / 경상도 일상 15 / 표준어 15) × 학습에 안 쓴 TTS 변형 2개 = 120개 → `학습데이터/unseen/`. **학습에 절대 쓰지 말 것(고정 평가셋).**
- 원본과 ko-en은 TTS 음성에서 출력이 완전히 같음(마스크는 실제 녹음에서만 차이).

| 셋 | n | 원본 CER / 완전일치 | tts_a1 CER / 완전일치 |
|---|---|---|---|
| 학습 문장 | 852 | 41.1% / 3.4% | 5.2% / 64.6% |
| 검증 문장 | 216 | 42.0% / 2.8% | 19.0% / 23.6% |
| 처음 보는 문장 전체 | 120 | 38.0% / 5.0% | 22.0% / 20.0% |
| └ 경상도 기기(새 표현) | 60 | 44.0% / 0.0% | 22.5% / 11.7% |
| └ 경상도 일상(기기 무관) | 30 | 47.1% / 0.0% | 33.8% / 6.7% |
| └ 표준어 | 30 | 15.2% / 20.0% | 11.2% / 50.0% |

- 결과 csv: `학습데이터/eval/all_<모델>_<셋>.csv`. 실제 녹음 재측정(7개 전체): 원본 56.8% / ko-en 47.7% / tts_a1 2.3%.
- 표준어는 나빠지지 않았지만 개별 퇴행 있음("오늘 날씨 어때"→"오늘 안 쉬 어", "외출할"→"매출액").

### 7-3. 알려진 한계·부작용
- 학습·검증 모두 **같은 TTS 목소리(표준 억양)**, 실제 녹음은 **사용자 1명·짧은 문장 5개** → 수치 신뢰도 낮음.
- 학습 문장(5%) vs 처음 보는 문장(19%) 차이 → 문장 수가 적어 학습 문장에 맞춰짐. 학습에 없는 단어·어미는 틀릴 가능성 큼.
- 학습 정답에 문장부호가 없어 **출력에서 마침표 사라짐**.
- 표준어도 사투리 철자로 약간 끌림: "편할 거야"→"편할꺼야", auto 모드 "조금만"→"쪼금만". 영어는 유지("code"→"gold"로 오히려 정답).

## 8. 자주 쓰는 명령

```powershell
# 엑셀 재생성
.venv\Scripts\python.exe data_build\make_abc_dataset.py
# TTS 데이터 (이미 있는 wav는 건너뜀 — 문장 바꾸면 학습데이터\tts\wav 정리 후 실행)
.venv\Scripts\python.exe stt\make_tts_data.py
# 학습 (백그라운드 권장, 로그 학습데이터\runs\<이름>\train.log)
.venv\Scripts\python.exe stt\train_sensevoice.py --out 학습데이터\runs\tts_a2 --epochs 10
# 보드용 변환
.venv\Scripts\python.exe stt\export_onnx.py --ckpt 학습데이터\runs\tts_a2\best.pt --dst models\sense-voice-ko-tts_a2
# 평가
.venv\Scripts\python.exe stt\eval_cer.py --model models\sense-voice-ko-tts_a2 --data 학습데이터\tts\val.jsonl --out 학습데이터\eval\tts_a2_val.csv
# 실제 녹음 (정답 지정)
.venv\Scripts\python.exe stt\transcribe_batch.py "학습데이터\녹음\테스트\녹음.m4a" "학습데이터\녹음\테스트\녹음 (2).m4a" --labels "녹음.m4a=거실 불 좀 키래이" "녹음 (2).m4a=블라인드 좀 내리도"
```
- 새 모델을 비교 대상에 넣으려면 `stt/transcribe.py`와 `stt/compare_models.py`의 `MODELS` 딕셔너리에 추가.
- 녹음 앱은 `녹음 (N).m4a`로 저장함. m4a/mp3/wav 모두 처리 가능.

## 9. 다음 할 일 (사용자에게 제안, 승인 대기)

사용자 질문 "확실하게 하려면 경상도 문장 몇 개?"에 대한 내 답:
- 핵심은 개수보다 **어미·어휘 커버리지**(-노 -나 -도 -래이 -삐라 -그라 -제 -데이 -는교 -카이 / 더버 추버 쪼매 억수로 고마 인자 우째 와 …)와 **화자 다양성**(TTS는 1명·표준 억양).
- ① TTS 문장 확장: 178 → **500~600문장** (기기×동작×어미 체계적 조합, 6변형이면 약 3,500개) — 바로 가능.
- ② 실제 녹음: 팀원 3~4명 × 각자 다른 150문장 ≈ **500~600개** (1인 15~20분) — 효과가 가장 클 것으로 예상.
- ③ (선택) AI Hub 경상도 일부 5,000~10,000개 재다운로드 — 자유발화 일반화·과적합 방지.
- **평가셋 고정**: 학습에 절대 안 쓰는 문장 **100개 × 화자 2~3명**(학습 녹음 안 한 사람 1명 포함). 한 번 정하면 바꾸지 않음.
- 마지막 제안: "①을 하면서 평가용 100문장을 떼어 두고, 팀원 녹음 대본(세트별 PDF)까지 만들까요?" → **답 대기 중.**

기타 미해결:
- 멘토에게 "파인튜닝한 SenseVoice 사용 가능 여부" 확인.
- 엑셀 문장(특히 사투리)·정답 문장(온도 수치) 팀 검수.
- 보드 이식: `models/sense-voice-ko-tts_a1/` 폴더를 보드 `~/voice/models`의 sense-voice 자리로 복사(파일명 동일). 보드 CPU에서 속도·정확도 재확인 필요.
- 문장부호 없는 출력이 LLM 쪽에 문제 되는지 팀(LLM 담당)과 확인.

## 10. 작업 방식 메모 (사용자 선호)
- 한국어로 소통. 사용자는 짧게 지시함 → 의도 파악해 바로 진행, 되돌리기 어려운 작업(삭제 등)만 먼저 확인.
- 사람이 볼 결과물: 이번엔 엑셀을 직접 요청함(예전엔 "엑셀 못 씀 → PDF" 였음). 요청 형식을 따를 것.
- 결과 보고 시 한계(표본 크기, TTS 편향, 학습/검증 구분)를 함께 말하기.
