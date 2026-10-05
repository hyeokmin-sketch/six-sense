package com.example.six_sense.model;

/** 기기 제어 화면의 방 필터. ALL은 필터 없음. */
public enum Room {
    ALL("전체"),
    LIVING("거실"),
    KITCHEN("주방"),
    BATH("욕실");

    private final String label;

    Room(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
