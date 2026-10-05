# SIX-SENSE 앱 (Android, Java)

스마트홈 AI 비서 모바일 앱. 6주차 UI/UX 시안을 그대로 옮긴 1차 구현.

## 열기

Android Studio (Koala 이상) → Open → 이 폴더. JDK 17, compileSdk 34, minSdk 26.

## 구조

```
com.example.six_sense
├─ SixSenseApp            전역 객체 (설정, AI 클라이언트). USE_MOCK_AI 플래그 여기
├─ model/                 Device, DeviceAction, Interpretation, HistoryEntry
├─ data/                  DeviceRepository(가전 상태), HistoryRepository(대화 기록), SettingsStore
├─ ai/                    AiClient 인터페이스, MockAiClient(데모), RemoteAiClient(보드 서버), ExecutionPolicy(확신도 분기)
└─ ui/
   ├─ MainActivity        하단 탭 4개 (홈 / 기기 / 기록 / 설정)
   ├─ home/               01 홈 · 대기
   ├─ listen/             02 듣는 중 · 해석 확인  (+ SpeechInput)
   ├─ result/             03 실행 결과 · 상황 인지
   ├─ devices/            04 기기 제어
   ├─ history/            05 대화 기록 · 교정
   └─ settings/           06 설정
```

## 화면 모드

설정 > 화면 모드. 이름은 "누구용"이 아니라 "어떻게 보이는지"로 붙였다.

| 모드 | 홈 | 기기 |
|---|---|---|
| 한눈에 모드 (기본) | `GlanceHomeFragment` — 집 상태를 크게, 즐겨찾기 2개 | `GlanceDevicesFragment` — 한 줄에 하나, 큰 ON/OFF. 카드 누르면 `DeviceDetailActivity` |
| 자세히 모드 | `HomeFragment` — 자주 쓰는 말 + 집 상태 | `DevicesFragment` — 2열 그리드, 카드에 수치까지 |

기록/설정은 공통. 모드를 바꾸면 Fragment 종류가 달라져서 `MainActivity.restart()`로 새로 띄운다.

## 흐름

발화 → `AiClient.interpret()` → `ExecutionPolicy.decide()`
- 높음(≥0.90, 바로 실행 모드일 때): 즉시 실행 → 결과 화면
- 중간: 확인 화면 3초 카운트다운 → 자동 실행 (취소 가능)
- 낮음(<0.70): 되묻기 (TTS) → 다시 듣기

실행 = `DeviceRepository.apply(actions)` → `HistoryRepository.record()` → ResultActivity.
되돌리기는 apply()가 돌려준 스냅샷으로 `restore()`.

## 아직 안 된 것

- 실제 가전 연동 (마음AI 버추얼 홈 / 웹 시뮬레이터). 지금은 DeviceRepository 메모리 상태만 바뀜
- RemoteAiClient 주소·응답 포맷 서버와 맞추기 (형균)
- 기록/교정 데이터 영구 저장 + 학습용 JSONL 내보내기 (데이터 포맷은 혁민과)
- 냉장고 내용물 조회, 설정 하위 화면 → 2차 시안

## 참고

- 폰 마이크 STT는 데모용(구글 음성인식). 사투리 인식률 기대하지 말 것. 최종은 보드의 SenseVoice.
- 음성 인식이 안 되는 에뮬레이터에서는 예시 문장("불 좀 꺼주이소")으로 자동 진행된다.
- 홈의 예시 칩 3개를 누르면 그 문장으로 바로 해석 화면이 뜨니 발표 때 이걸로 시연하면 편함.
