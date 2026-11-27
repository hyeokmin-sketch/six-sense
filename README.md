# SIX-SENSE

**경상도 사투리를 알아듣고 조명·에어컨·블라인드를 제어하는 온디바이스 스마트홈 음성 비서**입니다.
음성이 집 밖으로 나가지 않고, Orange Pi 5 Plus 보드 안에서 인식부터 제어까지 모두 처리합니다.

- 과목: 한성대학교 모바일시스템응용프로젝트 (2026학년도 2학기 산학협력프로젝트)
- 팀: 8분반 4팀 **3김한 취뽀**
- 시연 영상: https://youtu.be/F_dDNRDMvJg

## 팀 소개

| 이름 | 역할 |
|---|---|
| 한혁민 (팀장) | 시스템 아키텍처 설계 / 모델 학습 및 평가 / 보드 통합 |
| 김형균 | 데이터 전처리 / STT 측정 / 모델 양자화 |
| 김용희 | 데이터 수집 / 웹 대시보드·버추얼 환경 제작 |
| 김여진 | 데이터 수집 / UX 디자인 |

- 지도교수: 정병덕 (컴퓨터공학부 모바일소프트웨어트랙)
- 산업체 멘토: (주)마음AI 김민선, 한하평

## 프로젝트 소개

기존 음성 비서는 "불 좀 키라", "꺼뿌라" 같은 사투리 명령을 알아듣지 못하고, 음성이 클라우드로 전송되어 프라이버시 부담이 있습니다.
SIX-SENSE는 사투리 한마디로 집 안 기기를 제어하도록, 작은 LLM(Llama-3.2-1B)에 LoRA로 사투리 문장과 호출 형식을 학습시켰습니다.
모델이 문장 전체가 아니라 짧은 호출 형식만 출력하기 때문에 보드에서도 빠르게 동작합니다.

```
마이크 → VAD → STT(SenseVoice) → 치환 사전 → LLM(Llama-3.2-1B + LoRA, W8A8) → 파서 → 가상 원룸 앱 → (TTS)
```

### 호출 형식

```
기기 번호  1 조명 / 2 에어컨 / 3 블라인드 / -1 알 수 없음
<six_0>(object=N, type=N)<six_end>   기기 제어  (켜기·끄기, 블라인드 올리기·내리기·멈추기·반쯤 내리기)
<six_1>(object=N, type=N)<six_end>   조절      (조명 밝기, 에어컨 온도·바람)
<six_0>(object=-1)<six_end>          되묻기    (어떤 기기인지 모를 때)
<six_2>()<six_end>                   해당 없음 (기기와 상관없는 말 → 아무것도 하지 않음)
복합 명령: 호출을 말한 순서대로 이어 붙임
```

예) "에어컨 좀 켜 봐라" → `<six_0>(object=2, type=1)<six_end>`

### 주요 결과

| 항목 | 결과 |
|---|---|
| 정확도 (테스트 1,836문장) | 원본 94.3%, 양자화(W8A8) 94.3% (손실 없음) |
| 보드 속도 | 약 22.7 tokens/s, 인식부터 모델 출력까지 약 0.9초 |
| 치환 사전 | 후보 424개 중 검수를 거쳐 최종 113개 적용 (처리 약 0.2 ms) |
| 메모리 | LLM 서버 약 1.5GB (보드 16GB, 실측) |

## 폴더 구조

| 폴더 | 내용 |
|---|---|
| `board/` | **Orange Pi 5 Plus에서 실제로 돌아가는 코드**: 음성(VAD·STT·TTS), proxy(치환 사전·프롬프트), LLM 서버, systemd 서비스 파일 |
| `virtual-home/` | 가상 원룸 대시보드(웹). 호출 파서, 기기 상태, 화면 |
| `server/` | 파인튜닝한 모델을 PC에서 열어 대시보드가 부를 수 있게 하는 서버 |
| `control/` | 호출 문자열을 검증해 기기 명령 메시지로 바꾸는 제어 계층 프로토타입 |
| `training/` | Colab 파인튜닝(LoRA) 노트북과 사용법 |
| `quantization/` | RKLLM 양자화·보드 평가 자료 (채점 코드, 보정 문장, 입력 형식) |
| `data/` | 학습 데이터(4,931문장), 평가셋(`eval/`), 치환 사전 |
| `scripts/` | 치환 사전 적용 스크립트 |
| `docs/` | 버전별 요약, 모델 선택(Llama vs Qwen), 대시보드 연동 안내 |
| `docs/dev-docs/` | **개발 문서 6종** (아래 목록) |
| `models/` | 모델 파일 위치 안내 (모델 파일은 Git에 올리지 않음) |

## 개발 문서 (6종)

[docs/dev-docs/](docs/dev-docs/)

1. 요구사항 정의서
2. 아키텍처 설계서
3. 모델 설계 및 최적화 문서
4. API 및 인터페이스 정의서
5. 테스트 계획서 및 결과 보고서
6. 배포 및 통합 가이드

## 실행 방법

### 1. 보드 (Orange Pi 5 Plus)

서비스 3개가 부팅 시 자동으로 시작됩니다.

| 서비스 | 역할 |
|---|---|
| `sixsense-llm` (:8080) | LLM 서버 (RKLLM, NPU) |
| `sixsense-proxy` (:8000) | 치환 사전, 시스템 프롬프트, 음성 이벤트 |
| `sixsense-voice` | 마이크 → STT → proxy → 스피커 |

설치 순서 요약:

1. `board/` 폴더를 보드의 `~/board_proxy`로 복사합니다.
2. 양자화 모델(`.rkllm`)을 `~/rkllm/`에, 음성 모델(Silero VAD, SenseVoice, 한국어 VITS)을 `~/voice/models`에 둡니다.
3. `board/systemd/*.service`를 `/etc/systemd/system/`에 복사하고 `sudo systemctl enable --now sixsense-llm sixsense-proxy sixsense-voice`를 실행합니다.
4. 상태 확인: `systemctl status sixsense-llm sixsense-proxy sixsense-voice`

자세한 절차와 문제 해결은 개발 문서 6번(배포 및 통합 가이드)을 참고하세요.

### 2. PC에서 모델 서버 실행 (로컬)

1. 병합 모델을 `models/merged-llama-v2dict3/`에 넣습니다 ([models/README.md](models/README.md)).
2. 환경 준비 (Python 3.10 이상 권장)
   ```
   python -m venv .venv && source .venv/bin/activate
   pip install -r requirements.txt
   ```
3. 모델 서버 실행
   ```
   python server/six_sense_server.py --model models/merged-llama-v2dict3 --port 8000
   ```
4. 서버 확인
   ```
   curl -s localhost:8000/v1/chat/completions -H "Content-Type: application/json" \
     -d '{"messages":[{"role":"user","content":"에어컨 꺼라"}]}'
   ```
   결과에 `<six_0>(object=2, type=2)<six_end>`가 나오면 정상입니다.

### 3. 가상 원룸 대시보드

`virtual-home/index.html`을 브라우저로 엽니다. 서버 없이 규칙 엔진으로 보려면 주소 끝에 `?demo=1`을 붙이거나 `virtual-home/js/config.js`의 `llmEndpoint`를 `null`로 바꿉니다.
보드에 연결할 때는 `config.js`의 `llmEndpoint`에 보드 주소(예: `http://<보드 IP>:8000/v1/chat/completions`)를 적습니다.

## 한계와 다음 계획

- 기기 이름 없이 상황만 말하는 문장(예: "눈부시다")에서 엉뚱한 기기가 움직일 수 있습니다.
- 복합 명령에서 두 번째 동작이 빠지는 경우가 있습니다.
- 다음 단계: 되묻기 학습 보강, 더 다양한 화자 검증, 에어컨 온도의 숫자 지정 기능

## 공개하지 않은 자료

학습·평가에 쓴 실제 음성 녹음과 그 평가 파일, 대용량 모델 파일, 팀 내부 원본 엑셀은 저장소에 포함하지 않았습니다.
