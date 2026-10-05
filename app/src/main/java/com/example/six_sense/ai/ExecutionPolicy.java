package com.example.six_sense.ai;

import com.example.six_sense.model.Interpretation;

/**
 * 확신도에 따라 어떻게 실행할지 정하는 규칙. UI 흐름도의 "확신도 분기" 마름모가 이 클래스다.
 *
 *   높음(>= HIGH)      : 즉시 실행하고 결과만 알림
 *   중간(LOW ~ HIGH)   : 확인 화면 3초 보여주고 자동 실행 (취소 가능)
 *   낮음(< LOW)        : TTS로 되묻기
 *
 * 설정의 "확인하고 실행" 모드에서는 높음이어도 확인 화면을 거친다.
 *
 * 임계값 0.90 / 0.70은 설계용 가정치. 파인튜닝 후 카테고리별 평가셋 정확도 보고
 * 형균이랑 다시 정하기로 함. 숫자 바꿀 땐 settings 문구(mode_immediate_desc)도 같이.
 */
public final class ExecutionPolicy {

    public static final float HIGH = 0.90f;
    public static final float LOW = 0.70f;

    public enum Decision {
        IMMEDIATE,
        CONFIRM,
        ASK
    }

    private ExecutionPolicy() {
    }

    /**
     * @param interpretation 해석 결과
     * @param immediateMode  설정 > 실행 방식이 "바로 실행"인지
     */
    public static Decision decide(Interpretation interpretation, boolean immediateMode) {
        float c = interpretation.getConfidence();

        // 되묻는 질문이 이미 있으면 확신도와 상관없이 되묻는다 (서버가 명시적으로 ASK를 줄 때)
        if (interpretation.getAskBack() != null && !interpretation.getAskBack().isEmpty()) {
            return Decision.ASK;
        }
        if (c < LOW) {
            return Decision.ASK;
        }
        if (c >= HIGH && immediateMode) {
            return Decision.IMMEDIATE;
        }
        return Decision.CONFIRM;
    }

    /** 확인 화면에서 자동 실행까지 기다리는 시간(ms). 시안 기준 3초 */
    public static final long CONFIRM_COUNTDOWN_MS = 3_000L;

    /** 결과 화면에서 홈으로 자동 복귀하는 시간(ms). 시안 기준 5초 */
    public static final long RESULT_AUTO_HOME_MS = 5_000L;
}
