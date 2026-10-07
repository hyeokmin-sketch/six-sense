package com.example.six_sense.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 발화 하나를 LLM이 해석한 결과. 화면 02(해석 확인)와 03(실행 결과)이 전부 이 객체를 그린다.
 *
 * "들은 말 → 이해한 뜻 → 조치"가 설계 원칙이라, 세 가지가 각각 필드로 들어 있어야 한다.
 *   heard      : STT 원문 (사투리 그대로)
 *   summary    : 이해한 뜻 ("조명 끄기")
 *   actions    : 실제 조치 목록
 *
 * Activity 간에 Intent로 넘겨야 해서 Serializable. (Parcelable이 정석이지만 필드가 자주 바뀌는
 * 단계라 일단 이렇게 둠)
 */
public class Interpretation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String heard;
    private final String summary;
    private final String dialectNote;     // "'꺼주이소' → '꺼 주세요' (경상도)". 사투리가 아니면 null
    private final String tone;            // "차분함", "불편함" 등. STT 감정 태그에서 옴
    private final String contextNote;     // "밤이라 은은하게" 같은 보조 정보. 없으면 null
    private final float confidence;       // 0.0 ~ 1.0
    private final boolean monologue;      // 명령이 아니라 혼잣말/불평으로 분류됐는지
    private final String reasoning;       // 혼잣말일 때 "덥다고 느끼시는 것 같아요". 명령이면 null
    private final Integer currentTemp;    // 상황 인지에 쓴 실내 온도. 없으면 null
    private final Integer targetTemp;
    private final List<DeviceAction> actions;
    private final String response;        // TTS로 읽어줄 응답 문장
    private final String askBack;         // 확신도가 낮을 때 되묻는 질문. 아니면 null

    private Interpretation(Builder b) {
        this.heard = b.heard;
        this.summary = b.summary;
        this.dialectNote = b.dialectNote;
        this.tone = b.tone;
        this.contextNote = b.contextNote;
        this.confidence = b.confidence;
        this.monologue = b.monologue;
        this.reasoning = b.reasoning;
        this.currentTemp = b.currentTemp;
        this.targetTemp = b.targetTemp;
        this.actions = new ArrayList<>(b.actions);
        this.response = b.response;
        this.askBack = b.askBack;
    }

    public String getHeard() { return heard; }
    public String getSummary() { return summary; }
    public String getDialectNote() { return dialectNote; }
    public String getTone() { return tone; }
    public String getContextNote() { return contextNote; }
    public float getConfidence() { return confidence; }
    public boolean isMonologue() { return monologue; }
    public String getReasoning() { return reasoning; }
    public Integer getCurrentTemp() { return currentTemp; }
    public Integer getTargetTemp() { return targetTemp; }
    public List<DeviceAction> getActions() { return actions; }
    public String getResponse() { return response; }
    public String getAskBack() { return askBack; }

    /** 화면에 찍는 퍼센트 (92) */
    public int getConfidencePercent() {
        return Math.round(confidence * 100);
    }

    public boolean hasTemperatureChange() {
        return currentTemp != null && targetTemp != null;
    }

    public static Builder builder(String heard) {
        return new Builder(heard);
    }

    public static class Builder {
        private final String heard;
        private String summary = "";
        private String dialectNote;
        private String tone = "보통";
        private String contextNote;
        private float confidence = 0.5f;
        private boolean monologue;
        private String reasoning;
        private Integer currentTemp;
        private Integer targetTemp;
        private final List<DeviceAction> actions = new ArrayList<>();
        private String response = "";
        private String askBack;

        Builder(String heard) {
            this.heard = heard;
        }

        public Builder summary(String s) { this.summary = s; return this; }
        public Builder dialect(String s) { this.dialectNote = s; return this; }
        public Builder tone(String s) { this.tone = s; return this; }
        public Builder context(String s) { this.contextNote = s; return this; }
        public Builder confidence(float c) { this.confidence = Math.max(0f, Math.min(1f, c)); return this; }
        public Builder monologue(String reasoning) { this.monologue = true; this.reasoning = reasoning; return this; }
        public Builder temperature(int current, int target) { this.currentTemp = current; this.targetTemp = target; return this; }
        public Builder action(DeviceAction a) { this.actions.add(a); return this; }
        public Builder response(String s) { this.response = s; return this; }
        public Builder askBack(String s) { this.askBack = s; return this; }

        public Interpretation build() {
            return new Interpretation(this);
        }
    }
}
