package com.example.six_sense.model;

/**
 * 제어 대상 가전 8종.
 * 녹음 대본(세트 1~3)에 나오는 기기 + 계획서의 조명을 합친 목록이라, 여기 없는 기기는
 * 학습 데이터에도 없다고 보면 된다. 추가할 땐 대본 쪽도 같이 봐야 함.
 */
public enum DeviceType {
    LIGHT("조명"),
    AC("에어컨"),
    BOILER("보일러"),
    BLIND("블라인드"),
    OVEN("오븐"),
    BATH("욕조"),
    VACUUM("청소기"),
    COFFEE("커피머신"),
    FRIDGE("냉장고");

    private final String label;

    DeviceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 온도 값을 갖는 기기인지 (에어컨, 오븐, 욕조) */
    public boolean hasTemperature() {
        return this == AC || this == BOILER || this == OVEN || this == BATH;
    }
}
