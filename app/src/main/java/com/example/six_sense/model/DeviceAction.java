package com.example.six_sense.model;

import java.io.Serializable;

/**
 * LLM이 내놓는 제어 명령 한 건. "에어컨 켬 · 24°C" 같은 단위.
 * 하나의 발화가 여러 Action으로 풀릴 수 있다 (더우면 에어컨 + 블라인드).
 * Interpretation 안에 담겨 Intent로 넘어가므로 Serializable.
 */
public class DeviceAction implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Kind {
        TURN_ON,
        TURN_OFF,
        SET_VALUE      // 온도/밝기 변경. value 필요
    }

    private final String deviceId;
    private final Kind kind;
    private final int value;        // SET_VALUE가 아니면 무시
    private final String title;     // 결과 화면 제목 줄 ("에어컨 켬 · 24°C")
    private final String detail;    // 결과 화면 설명 줄 ("냉방 · 바람 보통")

    public DeviceAction(String deviceId, Kind kind, int value, String title, String detail) {
        this.deviceId = deviceId;
        this.kind = kind;
        this.value = value;
        this.title = title;
        this.detail = detail;
    }

    public static DeviceAction on(String deviceId, String title, String detail) {
        return new DeviceAction(deviceId, Kind.TURN_ON, 0, title, detail);
    }

    public static DeviceAction off(String deviceId, String title, String detail) {
        return new DeviceAction(deviceId, Kind.TURN_OFF, 0, title, detail);
    }

    public static DeviceAction set(String deviceId, int value, String title, String detail) {
        return new DeviceAction(deviceId, Kind.SET_VALUE, value, title, detail);
    }

    public String getDeviceId() { return deviceId; }
    public Kind getKind() { return kind; }
    public int getValue() { return value; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
}
