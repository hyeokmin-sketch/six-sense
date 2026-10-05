package com.example.six_sense.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 대화 기록 한 줄. 화면 05에서 쓰고, "잘못됐어요" 교정이 여기 붙는다.
 * 교정된 항목은 나중에 학습 데이터(JSONL)로 내보낼 예정이라 원문/조치/교정문을 전부 들고 있는다.
 */
public class HistoryEntry {

    /** 실행된 방식. 확신도 분기 기준을 나중에 조정할 때 참고하려고 기록해 둔다. */
    public enum Mode {
        IMMEDIATE,   // 즉시 실행
        CONFIRMED,   // 확인 화면 거쳐 실행 (자동 또는 버튼)
        ASKED        // 되물었음
    }

    private final long id;
    private final long timestamp;
    private final String heard;
    private final String actionSummary;     // "에어컨 24°C 켬 · 블라인드 내림"
    private final int confidencePercent;
    private final Mode mode;
    private final boolean monologue;
    private final List<Device> snapshotBefore;   // 되돌리기용. 조치 전 상태

    private String correction;              // 사용자가 "잘못됐어요"로 적은 내용. 없으면 null

    public HistoryEntry(long id, long timestamp, String heard, String actionSummary,
                        int confidencePercent, Mode mode, boolean monologue,
                        List<Device> snapshotBefore) {
        this.id = id;
        this.timestamp = timestamp;
        this.heard = heard;
        this.actionSummary = actionSummary;
        this.confidencePercent = confidencePercent;
        this.mode = mode;
        this.monologue = monologue;
        this.snapshotBefore = snapshotBefore == null ? new ArrayList<>() : new ArrayList<>(snapshotBefore);
    }

    public long getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public String getHeard() { return heard; }
    public String getActionSummary() { return actionSummary; }
    public int getConfidencePercent() { return confidencePercent; }
    public Mode getMode() { return mode; }
    public boolean isMonologue() { return monologue; }
    public List<Device> getSnapshotBefore() { return snapshotBefore; }

    public String getCorrection() { return correction; }
    public void setCorrection(String correction) { this.correction = correction; }
    public boolean isCorrected() { return correction != null && !correction.trim().isEmpty(); }
}
