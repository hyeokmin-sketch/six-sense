# 대시보드 ↔ LLM 연동 안내

## 구조

```
대시보드(virtual-home)  --글자-->  모델 서버(server/six_sense_server.py)  --호출 문자열-->  대시보드
 말하기 입력창                       병합 모델(Llama v2dict3)                          파서 → 기기 상태 → 화면
```

대시보드에는 우리 호출 형식 파서(`js/parser.js`)와 14개 호출→기기 상태 변환(`js/devices.js`)이 이미 있어서, LLM 서버 주소(`js/config.js`의 `llmEndpoint`)만 연결하면 규칙 엔진 대신 실제 모델이 호출을 만듭니다. 이 저장소의 `config.js`, `engine.js`는 이미 연동용으로 수정되어 있습니다.

## 저장소에서 바꿔 둔 것

- `virtual-home/js/config.js`: `llmEndpoint`(localhost:8000)와 `systemPrompt`(학습 때 쓴 프롬프트)를 채움
- `virtual-home/js/engine.js`: `max_tokens` 64 → 80 (복합 명령이 잘리지 않게)

## 실행 순서

루트 `README.md`의 "실행 방법"을 따릅니다. (모델 받기 → 서버 켜기 → `index.html` 열기)

## 꼭 지킬 것

- **시스템 프롬프트와 날짜 줄:** 대시보드가 `systemPrompt`를 보내고, 서버가 Llama 템플릿의 날짜 줄을 학습 때와 같은 `07 Nov 2026`으로 고정합니다. 학습 때와 달라지면 정확도가 떨어질 수 있습니다.
- **생성 설정:** 서버가 greedy(do_sample=False), 최대 80토큰, 종료 토큰 `<|eot_id|>`로 고정합니다. 병합 모델의 기본 설정은 샘플링이라 서버에서 막아야 합니다.
- **서버가 꺼져 있으면:** 대시보드가 "응답이 늦어지고 있어요(E03)"를 띄웁니다. 시연 전에 서버를 먼저 켜세요. 규칙 엔진으로 돌아가려면 `llmEndpoint: null`.

## 알아 둘 점

- 서버의 요청·응답 형식은 가짜 모델로 테스트했고, 실제 병합 모델로 대시보드까지 돌려 본 결과는 이 문서에 반영되어 있지 않습니다. 시연 전에 실제 모델로 몇 문장을 입력해 확인하세요.
- 마이크 입력은 아직 없고 글자 입력만 됩니다.
- 보드를 받으면 `llmEndpoint`를 보드의 서버 주소로만 바꾸면 되고 대시보드는 그대로 씁니다.
- Colab에 올라온 모델을 임시로 열어 시험하려면 `training/colab_server_cell.py`를 씁니다. (주소가 실행할 때마다 바뀜)
