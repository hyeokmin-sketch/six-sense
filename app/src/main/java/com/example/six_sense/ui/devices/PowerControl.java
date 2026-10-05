package com.example.six_sense.ui.devices;

import android.content.Context;
import android.widget.Toast;

import com.example.six_sense.R;
import com.example.six_sense.data.DemoSimulator;
import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.model.Device;

/**
 * ON/OFF 스위치를 밀었을 때의 규칙. 기기 목록과 상세 화면이 같은 규칙을 쓰도록 한 군데 모았다.
 *
 *   욕조   : 켜기 = 물 받기 시작. 물이 차 있으면 스위치로 못 끈다 ([물 빼기]로)
 *   청소기 : 켜기 = 청소 시작. 청소 중·완료! 동안은 못 끈다. [청소 중단] 후에 끄면 충전기로
 *   커피   : 내리는 중·완성! 동안은 못 끈다
 *   그 외  : 그냥 켜고 끈다
 *
 * 거절한 경우 안내 토스트를 띄우고 false. 스위치는 호출한 쪽에서 기기 상태로 다시 맞춘다.
 */
public final class PowerControl {

    private PowerControl() {
    }

    public static boolean request(Context ctx, Device d, boolean wantOn) {
        DeviceRepository devices = DeviceRepository.getInstance();
        DemoSimulator sim = DemoSimulator.get();

        switch (d.getType()) {
            case BATH:
                if (wantOn && !d.isOn()) {
                    sim.fillBath();
                    return true;
                }
                if (!wantOn && d.isOn()) return refuse(ctx, R.string.detail_switch_bath_hint);
                return false;

            case VACUUM:
                if (wantOn && !d.isOn()) {
                    sim.startVacuum();
                    return true;
                }
                if (!wantOn && sim.isVacuumPaused(d)) {
                    sim.stopVacuum();
                    return true;
                }
                if (!wantOn && d.isOn()) return refuse(ctx, R.string.detail_switch_vacuum_hint);
                return false;

            case COFFEE:
                boolean busy = d.isOn() && (d.getAux() > 0 || sim.isCoffeeDone(d));
                if (!wantOn && busy) return refuse(ctx, R.string.detail_switch_coffee_hint);
                devices.setOn(d.getId(), wantOn);
                return true;

            default:
                devices.setOn(d.getId(), wantOn);
                return true;
        }
    }

    private static boolean refuse(Context ctx, int messageRes) {
        Toast.makeText(ctx, messageRes, Toast.LENGTH_SHORT).show();
        return false;
    }
}
