package com.example.six_sense.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.six_sense.model.Device;
import com.example.six_sense.model.DeviceAction;
import com.example.six_sense.model.DeviceType;
import com.example.six_sense.model.Room;

/**
 * 가전 상태 저장소. 앱 안에서 유일하게 상태를 바꾸는 곳.
 *
 * 지금은 메모리에만 들고 있고, 마음AI 버추얼 홈 연동이 되면 apply()에서 실제 제어 API를 부르고
 * 상태는 서버에서 받아오는 식으로 바꿀 예정. 그래서 UI는 이 클래스만 보게 해두었다.
 *
 * 변경 알림은 Listener로. UI 스레드 보장은 안 하니까 Fragment 쪽에서 post() 할 것.
 */
public class DeviceRepository {

    // 기기 ID. AI 서버 응답의 "device" 값과 그대로 맞춘다.
    public static final String ID_LIGHT_LIVING = "light_living";
    public static final String ID_LIGHT_KITCHEN = "light_kitchen";
    public static final String ID_AC = "ac";
    public static final String ID_BOILER = "boiler";
    public static final String ID_BLIND = "blind";
    public static final String ID_OVEN = "oven";
    public static final String ID_BATH = "bath";
    public static final String ID_VACUUM = "vacuum";
    public static final String ID_COFFEE = "coffee";
    public static final String ID_FRIDGE = "fridge";

    public interface Listener {
        void onDevicesChanged();
    }

    private static DeviceRepository instance;

    public static synchronized DeviceRepository getInstance() {
        if (instance == null) {
            instance = new DeviceRepository();
        }
        return instance;
    }

    private final List<Device> devices = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /** 실내 온도 센서 값. 원룸 기준이라 하나. 지금은 고정값, 시뮬레이터 붙으면 갱신 */
    private int roomTemperature = 28;

    /** 사용자가 홈에서 고른 희망 온도. 처음엔 이달 권장 온도로 시작 (HomeFragment에서 세팅) */
    private int desiredTemperature = -1;

    private DeviceRepository() {
        seed();
    }

    /** 시안 홈 화면의 초기 상태와 같게 맞춘 기본값 */
    private void seed() {
        devices.add(new Device(ID_LIGHT_LIVING, DeviceType.LIGHT, "조명", Room.LIVING, true, 70, null));
        devices.add(new Device(ID_LIGHT_KITCHEN, DeviceType.LIGHT, "스탠드", Room.LIVING, true, 100, null));
        devices.add(new Device(ID_AC, DeviceType.AC, "에어컨", Room.LIVING, false, 24, null));
        devices.add(new Device(ID_BOILER, DeviceType.BOILER, "보일러", Room.LIVING, false, 22, null));
        devices.add(new Device(ID_BLIND, DeviceType.BLIND, "블라인드", Room.LIVING, false, 100, null));   // 100 = 다 올림
        devices.add(new Device(ID_OVEN, DeviceType.OVEN, "오븐", Room.KITCHEN, false, 180, null));
        devices.add(new Device(ID_BATH, DeviceType.BATH, "욕조", Room.BATH, false, 38, null));
        Device vacuum = new Device(ID_VACUUM, DeviceType.VACUUM, "청소기", Room.LIVING, false, 0, null);
        vacuum.setBattery(85);   // 시연 때 충전되는 모습이 보이게
        devices.add(vacuum);
        devices.add(new Device(ID_COFFEE, DeviceType.COFFEE, "커피머신", Room.KITCHEN, false, 0, "대기 · 물 충분"));
        devices.add(new Device(ID_FRIDGE, DeviceType.FRIDGE, "냉장고", Room.KITCHEN, true, 3, null));
    }

    // ---------------------------------------------------------------------------------------------
    // 조회
    // ---------------------------------------------------------------------------------------------

    public List<Device> getAll() {
        return Collections.unmodifiableList(devices);
    }

    /** 방 필터. ALL이면 전부. 기기 제어 화면 그리드용 */
    public List<Device> getByRoom(Room room) {
        if (room == Room.ALL) return getAll();
        List<Device> out = new ArrayList<>();
        for (Device d : devices) {
            if (d.getRoom() == room) out.add(d);
        }
        return out;
    }

    public Device find(String id) {
        for (Device d : devices) {
            if (d.getId().equals(id)) return d;
        }
        return null;
    }

    public int getRoomTemperature() {
        return roomTemperature;
    }

    /** -1이면 아직 안 고른 것 */
    public int getDesiredTemperature() {
        return desiredTemperature;
    }

    public void setDesiredTemperature(int temp) {
        desiredTemperature = Math.max(16, Math.min(30, temp));
        notifyChanged();
    }

    /** 켜진 조명 개수. 홈 화면 "2곳 켜짐" */
    public int countLightsOn() {
        int n = 0;
        for (Device d : devices) {
            if (d.getType() == DeviceType.LIGHT && d.isOn()) n++;
        }
        return n;
    }

    /** 켜진 조명 이름 ("조명 · 스탠드"). 홈 화면 조명 카드 아랫줄. 원룸 기준이라 방 대신 기기 이름 */
    public String describeLightRooms() {
        StringBuilder sb = new StringBuilder();
        for (Device d : devices) {
            if (d.getType() == DeviceType.LIGHT && d.isOn()) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(d.getName());
            }
        }
        return sb.length() == 0 ? "모두 꺼짐" : sb.toString();
    }

    // ---------------------------------------------------------------------------------------------
    // 변경
    // ---------------------------------------------------------------------------------------------

    /**
     * 홈의 "에어컨 / 보일러" 선택. 고른 쪽을 희망 온도로 켜고 다른 쪽은 끈다.
     * 둘 다 켜져 있는 상태는 만들지 않는다 (에너지 낭비 + 사용자 혼란).
     */
    public void runClimate(boolean useBoiler) {
        Device ac = find(ID_AC);
        Device boiler = find(ID_BOILER);
        if (ac == null || boiler == null) return;
        int target = desiredTemperature > 0 ? desiredTemperature : roomTemperature;

        Device on = useBoiler ? boiler : ac;
        Device off = useBoiler ? ac : boiler;
        on.setOn(true);
        on.setValue(target);
        off.setOn(false);
        notifyChanged();
    }

    /** 상세 화면에서 전원만 */
    public void setOn(String id, boolean on) {
        Device d = find(id);
        if (d == null || !d.isToggleable()) return;
        if (d.getType() == DeviceType.BLIND) {
            setBlindOpen(on ? 0 : 100);
            return;
        }
        d.setOn(on);
        notifyChanged();
    }

    /** 상세 화면에서 값(밝기/온도)만. 값을 바꾸면 켜진 것으로 본다 */
    public void setValue(String id, int value, boolean turnOn) {
        Device d = find(id);
        if (d == null) return;
        d.setValue(value);
        if (turnOn) d.setOn(true);
        notifyChanged();
    }

    public void setAux(String id, int aux) {
        Device d = find(id);
        if (d == null) return;
        d.setAux(aux);
        notifyChanged();
    }

    public void setBattery(String id, int percent) {
        Device d = find(id);
        if (d == null) return;
        d.setBattery(percent);
        notifyChanged();
    }

    /** 여러 값을 한 번에 바꾸고 알림은 한 번만 (시뮬레이터용) */
    public void update(String id, Boolean on, String note, Integer aux) {
        Device d = find(id);
        if (d == null) return;
        if (on != null) d.setOn(on);
        if (note != null) d.setNote(note);
        if (aux != null) d.setAux(aux);
        notifyChanged();
    }

    public void setNote(String id, String note) {
        Device d = find(id);
        if (d == null) return;
        d.setNote(note);
        notifyChanged();
    }

    /** 기기 제어 화면 토글 */
    public void toggle(String id) {
        Device d = find(id);
        if (d == null || !d.isToggleable()) return;
        if (d.getType() == DeviceType.BLIND) {
            // 블라인드 ON = 내림. 토글은 끝까지 내리거나 끝까지 올린다
            setBlindOpen(d.isOn() ? 100 : 0);
            return;
        }
        d.setOn(!d.isOn());
        notifyChanged();
    }

    /** 블라인드 열린 정도(0~100, 10% 단위). on 여부는 여기서 같이 맞춘다 */
    public void setBlindOpen(int percent) {
        Device d = find(ID_BLIND);
        if (d == null) return;
        int p = Math.max(0, Math.min(100, Math.round(percent / 10f) * 10));
        d.setValue(p);
        d.setOn(p < 100);
        notifyChanged();
    }

    /**
     * 조치 목록을 한꺼번에 적용하고, 되돌리기용으로 적용 전 상태를 돌려준다.
     * 결과 화면의 "되돌리기"와 기록 저장 둘 다 이 반환값을 쓴다.
     */
    public List<Device> apply(List<DeviceAction> actions) {
        List<Device> before = snapshot();
        for (DeviceAction a : actions) {
            Device d = find(a.getDeviceId());
            if (d == null) continue;   // 서버가 모르는 기기 ID를 주면 그냥 건너뜀

            switch (a.getKind()) {
                case TURN_ON:
                    d.setOn(true);
                    if (d.getType() == DeviceType.BLIND) d.setValue(0);     // 내림
                    break;
                case TURN_OFF:
                    d.setOn(false);
                    if (d.getType() == DeviceType.BLIND) d.setValue(100);   // 올림
                    break;
                case SET_VALUE:
                    d.setOn(true);     // 값을 바꾸는 건 켜는 걸 전제로 한다 (에어컨 24도 = 켜고 24도)
                    d.setValue(a.getValue());
                    break;
            }
            if (a.getDetail() != null) {
                d.setNote(a.getDetail());
            }
        }
        notifyChanged();
        return before;
    }

    /** apply()가 돌려준 스냅샷으로 원복 */
    public void restore(List<Device> snapshot) {
        if (snapshot == null) return;
        for (Device saved : snapshot) {
            Device d = find(saved.getId());
            if (d == null) continue;
            d.setOn(saved.isOn());
            d.setValue(saved.getValue());
            d.setNote(saved.getNote());
        }
        notifyChanged();
    }

    private List<Device> snapshot() {
        List<Device> copy = new ArrayList<>(devices.size());
        for (Device d : devices) copy.add(d.copy());
        return copy;
    }

    // ---------------------------------------------------------------------------------------------
    // 리스너
    // ---------------------------------------------------------------------------------------------

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : listeners) l.onDevicesChanged();
    }
}
