package com.example.six_sense.model;

/**
 * 가전 하나의 현재 상태.
 * 실제 상태는 마음AI 버추얼 홈(또는 웹 시뮬레이터)에서 받아와야 하지만,
 * 연동 전까지는 DeviceRepository가 들고 있는 이 객체가 곧 상태다.
 */
public class Device {

    private final String id;
    private final DeviceType type;
    private final String name;      // 화면에 보이는 이름 ("조명")
    private final Room room;

    private boolean on;
    private int value;              // 에어컨/욕조/오븐: 온도, 조명: 밝기(%). 그 외는 안 씀
    private String note;            // 상태 밑에 작게 붙는 설명 ("냉방 · 바람 보통"). 없으면 null
    private int aux;                // 기기별 보조 값. 오븐: 타이머(분), 청소기: 경과(분), 커피: 진행(%)
    private int battery = 100;      // 청소기만 씀 (%)

    public Device(String id, DeviceType type, String name, Room room, boolean on, int value, String note) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.room = room;
        this.on = on;
        this.value = value;
        this.note = note;
    }

    public String getId() { return id; }
    public DeviceType getType() { return type; }
    public String getName() { return name; }
    public Room getRoom() { return room; }
    public boolean isOn() { return on; }
    public int getValue() { return value; }
    public String getNote() { return note; }

    public void setOn(boolean on) { this.on = on; }
    public void setValue(int value) { this.value = value; }
    public void setNote(String note) { this.note = note; }
    public int getAux() { return aux; }
    public void setAux(int aux) { this.aux = aux; }
    public int getBattery() { return battery; }
    public void setBattery(int battery) { this.battery = Math.max(0, Math.min(100, battery)); }

    /**
     * 카드에 표시할 한 줄 상태.
     * 색만으로 켜짐/꺼짐을 구분하지 않기로 했기 때문에 글자로도 반드시 나와야 한다.
     */
    public String describeState() {
        switch (type) {
            case LIGHT:
                return on ? "켜짐 · 밝기 " + value + "%" : "꺼짐";
            case AC:
                return on ? "켜짐 · " + value + "°C 냉방" : "꺼짐 · 마지막 " + value + "°C";
            case BOILER:
                return on ? "켜짐 · " + value + "°C 난방" : "꺼짐 · 마지막 " + value + "°C";
            case BLIND:
                // value = 열린 정도(%). 0 완전히 내림, 100 완전히 올림. on은 value < 100
                if (value >= 100) return "올림 · 햇빛 들어옴";
                if (value <= 0) return "내림 · 햇빛 차단";
                return value + "% 열림";
            case OVEN:
                if (!on) return "꺼짐";
                return aux > 0 ? value + "°C · " + aux + "분" : "예열 중 · " + value + "°C";
            case BATH:
                if (!on) return "비어 있음 · 마지막 " + value + "°C";
                return (note != null ? note : "물 받는 중") + " · " + value + "°C";
            case VACUUM:
                if (on) return (note != null && note.endsWith("!") ? note : "청소 중") + " · 배터리 " + battery + "%";
                return battery >= 100 ? "꺼짐 · 충전 완료" : "꺼짐 · 충전 중 " + battery + "%";
            case COFFEE:
                if (!on) return "꺼짐";
                return note != null ? note : "대기";          // "아메리카노 내리는 중"
            case FRIDGE:
                return "내용물 보기 · " + value + "°C";
            default:
                return on ? "켜짐" : "꺼짐";
        }
    }

    /** 냉장고처럼 켜고 끄는 개념이 없는 기기 */
    public boolean isToggleable() {
        return type != DeviceType.FRIDGE;
    }

    /** 되돌리기용 얕은 복사 */
    public Device copy() {
        Device d = new Device(id, type, name, room, on, value, note);
        d.aux = aux;
        d.battery = battery;
        return d;
    }
}
