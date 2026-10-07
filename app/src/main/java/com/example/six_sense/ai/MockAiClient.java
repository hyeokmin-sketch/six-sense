package com.example.six_sense.ai;

import java.util.HashMap;
import java.util.Map;

import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.model.DeviceAction;
import com.example.six_sense.model.Interpretation;

/**
 * 서버 없이 앱 흐름을 돌려보기 위한 가짜 해석기.
 *
 * 시안·녹음 대본에 있는 문장 몇 개는 정답을 박아두고, 나머지는 키워드로 대충 맞춘다.
 * 파인튜닝 모델이 올라오면 RemoteAiClient로 바꾸고 이 클래스는 테스트용으로만 남길 것.
 */
public class MockAiClient implements AiClient {

    /** 실제 추론처럼 보이게 약간 기다린다. 0으로 두면 바로 응답 */
    private static final long FAKE_LATENCY_MS = 700L;

    private final DeviceRepository devices;
    private final Map<String, Interpretation> canned = new HashMap<>();

    public MockAiClient(DeviceRepository devices) {
        this.devices = devices;
        prepareCanned();
    }

    @Override
    public Interpretation interpret(String utterance) {
        sleepQuietly(FAKE_LATENCY_MS);

        String key = normalize(utterance);
        Interpretation hit = canned.get(key);
        if (hit != null) {
            return hit;
        }
        return guessByKeyword(utterance);
    }

    // ---------------------------------------------------------------------------------------------
    // 시안에 쓴 예문. 화면 캡처와 숫자가 맞아야 해서 여기 값은 함부로 바꾸지 말 것.
    // ---------------------------------------------------------------------------------------------
    private void prepareCanned() {
        int roomTemp = devices.getRoomTemperature();

        // A. 사투리 명령
        put(Interpretation.builder("불 좀 꺼주이소")
                .summary("조명 끄기")
                .dialect("‘꺼주이소’ → ‘꺼 주세요’ (경상도)")
                .tone("차분함")
                .confidence(0.92f)
                .action(DeviceAction.off(DeviceRepository.ID_LIGHT_LIVING, "조명 끔", "밝기 70% → 꺼짐"))
                .response("네, 불을 껐어요.")
                .build());

        // C. 사투리 + 혼잣말. 상황 인지로 두 기기 동시 제어
        put(Interpretation.builder("더버 디지기 딱 좋은 날씨고만")
                .summary("덥다는 말씀 → 냉방")
                .dialect("‘더버 디지기’ → ‘더워 죽기’ (경상도)")
                .tone("불편함")
                .confidence(0.81f)
                .monologue("덥다고 느끼시는 것 같아요")
                .temperature(roomTemp, 24)
                .action(DeviceAction.set(DeviceRepository.ID_AC, 24, "에어컨 켬 · 24°C", "냉방 · 바람 보통"))
                .action(DeviceAction.on(DeviceRepository.ID_BLIND, "블라인드 내림", "햇빛 차단"))
                .response("네, 에어컨을 24도로 켜고 블라인드를 내렸어요. 금방 시원해질 거예요.")
                .build());

        // B. 모호한 표준어 표현
        put(Interpretation.builder("방이 좀 답답하네")
                .summary("답답함 → 환기·바람")
                .tone("답답함")
                .confidence(0.76f)
                .monologue("공기가 답답하다고 느끼시는 것 같아요")
                .action(DeviceAction.off(DeviceRepository.ID_BLIND, "블라인드 올림", "빛과 바깥 풍경 들이기"))
                .action(DeviceAction.set(DeviceRepository.ID_AC, 25, "에어컨 켬 · 25°C", "송풍 · 바람 약하게"))
                .response("블라인드를 올리고 바람을 약하게 틀었어요. 좀 나아지실 거예요.")
                .build());

        // C. 톤·감정 기반
        put(Interpretation.builder("아 오늘 진짜 피곤하다")
                .summary("피곤함 → 휴식 모드")
                .tone("지침")
                .confidence(0.74f)
                .monologue("많이 피곤해 보이세요")
                .action(DeviceAction.set(DeviceRepository.ID_LIGHT_LIVING, 30, "조명 은은하게 · 30%", "따뜻한 색으로"))
                .action(DeviceAction.on(DeviceRepository.ID_BLIND, "블라인드 내림", "햇빛 차단"))
                .response("조명을 은은하게 낮추고 블라인드를 내렸어요. 푹 쉬세요.")
                .build());

        // 녹음 대본에 있는 문장들
        put(Interpretation.builder("커피 한 잔 묵자")
                .summary("커피머신 · 아메리카노 1잔")
                .dialect("‘묵자’ → ‘먹자’ (경상도)")
                .tone("차분함")
                .confidence(0.74f)
                .action(DeviceAction.on(DeviceRepository.ID_COFFEE, "커피 추출 · 아메리카노 1잔", "약 1분 걸려요"))
                .response("아메리카노 한 잔 내릴게요.")
                .build());

        put(Interpretation.builder("애들 목욕 시키야 된다")
                .summary("욕조 물 받기 · 38°C")
                .dialect("‘시키야 된다’ → ‘시켜야 한다’ (경상도)")
                .tone("차분함")
                .confidence(0.88f)
                .action(DeviceAction.set(DeviceRepository.ID_BATH, 38, "욕조 물 받기 · 38°C", "아이 목욕 온도"))
                .response("욕조에 38도로 물을 받을게요. 다 받으면 알려드릴게요.")
                .build());

        // 낮은 확신도 → 되묻기. 원룸 기준이라 방은 묻지 않고, 무엇을 끌지 모를 때만 묻는다 (팀 리뷰 10/8)
        put(Interpretation.builder("그거 좀 꺼도")
                .summary("끌 기기가 불분명")
                .dialect("‘꺼도’ → ‘꺼 줘’ (경상도)")
                .tone("차분함")
                .confidence(0.55f)
                .askBack("어떤 기기를 말씀하시는 건가요?")
                .build());
    }

    private void put(Interpretation i) {
        canned.put(normalize(i.getHeard()), i);
    }

    // ---------------------------------------------------------------------------------------------
    // 모르는 문장은 키워드로 대충. 확신도는 일부러 낮게 준다.
    // ---------------------------------------------------------------------------------------------
    private Interpretation guessByKeyword(String u) {
        Interpretation.Builder b = Interpretation.builder(u).tone("보통");

        if (u.contains("덥") || u.contains("더워") || u.contains("더버")) {
            int room = devices.getRoomTemperature();
            return b.summary("냉방 켜기")
                    .confidence(0.72f)
                    .monologue("덥다고 느끼시는 것 같아요")
                    .temperature(room, 24)
                    .action(DeviceAction.set(DeviceRepository.ID_AC, 24, "에어컨 켬 · 24°C", "냉방"))
                    .response("에어컨을 24도로 켤게요.")
                    .build();
        }
        if (u.contains("춥") || u.contains("추워") || u.contains("시리")) {
            return b.summary("에어컨 끄기")
                    .confidence(0.78f)
                    .action(DeviceAction.off(DeviceRepository.ID_AC, "에어컨 끔", "춥다고 하셔서"))
                    .response("에어컨을 껐어요.")
                    .build();
        }
        if (u.contains("불") && (u.contains("꺼") || u.contains("끄"))) {
            return b.summary("조명 끄기")
                    .confidence(0.80f)
                    .action(DeviceAction.off(DeviceRepository.ID_LIGHT_LIVING, "조명 끔", null))
                    .response("불을 껐어요.")
                    .build();
        }
        if (u.contains("불") && u.contains("켜")) {
            return b.summary("조명 켜기")
                    .confidence(0.80f)
                    .action(DeviceAction.on(DeviceRepository.ID_LIGHT_LIVING, "조명 켬", null))
                    .response("불을 켰어요.")
                    .build();
        }
        if (u.contains("커피")) {
            return b.summary("커피 내리기")
                    .confidence(0.77f)
                    .action(DeviceAction.on(DeviceRepository.ID_COFFEE, "커피 추출", null))
                    .response("커피 내릴게요.")
                    .build();
        }
        if (u.contains("청소")) {
            return b.summary("청소기 돌리기")
                    .confidence(0.79f)
                    .action(DeviceAction.on(DeviceRepository.ID_VACUUM, "청소기 시작", "자동 청소 60분"))
                    .response("청소 시작할게요.")
                    .build();
        }
        if (u.contains("목욕") || u.contains("씻")) {
            return b.summary("욕조 물 받기")
                    .confidence(0.75f)
                    .action(DeviceAction.set(DeviceRepository.ID_BATH, 40, "욕조 물 받기 · 40°C", null))
                    .response("욕조에 물 받을게요.")
                    .build();
        }
        if (u.contains("블라인드") || u.contains("햇빛") || u.contains("눈부")) {
            return b.summary("블라인드 내리기")
                    .confidence(0.76f)
                    .action(DeviceAction.on(DeviceRepository.ID_BLIND, "블라인드 내림", null))
                    .response("블라인드를 내렸어요.")
                    .build();
        }

        // 전혀 모르겠으면 되묻는다
        return b.summary("무슨 말씀인지 잘 모르겠어요")
                .confidence(0.40f)
                .askBack("조명이나 에어컨처럼 기기 이름을 넣어서 다시 한 번 말씀해 주시겠어요?")
                .build();
    }

    /** 띄어쓰기·문장부호 차이로 못 찾는 일이 없게 키를 단순화 */
    private static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s,.!?~…\"'“”‘’]", "");
    }

    private static void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
