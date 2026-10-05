package com.example.six_sense.ai;

import java.util.Calendar;

/**
 * 월별 권장 실내 온도와 냉난방 추천.
 *
 * 기준: 정부 에너지 절약 권장치(여름 26°C 이상, 겨울 20°C 이하)와
 * 질병관리청 건강수칙(겨울 18~20°C, 여름 26~28°C)의 경계값을 쓰고,
 * 봄·가을은 그 사이를 월별로 나눴다. 정확한 수치보다 "환절기엔 22°C 안팎" 정도의 기준선 역할.
 */
public final class ClimateGuide {

    /** 인덱스 0 = 1월 */
    private static final int[] RECOMMENDED = {
            20, 20, 21, 22, 23, 25, 26, 26, 24, 22, 21, 20
    };

    public enum Suggestion {
        AC,       // 희망 온도가 권장보다 낮다 → 냉방
        BOILER,   // 희망 온도가 권장보다 높다 → 난방
        NONE      // 권장 온도 그대로
    }

    private ClimateGuide() {
    }

    /** @param month 1~12 */
    public static int recommendedFor(int month) {
        int i = Math.max(1, Math.min(12, month)) - 1;
        return RECOMMENDED[i];
    }

    public static int recommendedNow() {
        return recommendedFor(Calendar.getInstance().get(Calendar.MONTH) + 1);
    }

    public static int currentMonth() {
        return Calendar.getInstance().get(Calendar.MONTH) + 1;
    }

    /**
     * 희망 온도를 이달 권장 온도와 비교해서 어느 쪽을 강조할지.
     * (현재 온도가 아니라 권장 온도 기준인 건 기획 요구사항. 바꾸려면 여기만 고치면 됨)
     */
    public static Suggestion suggest(int desired) {
        int rec = recommendedNow();
        if (desired > rec) return Suggestion.BOILER;
        if (desired < rec) return Suggestion.AC;
        return Suggestion.NONE;
    }
}
