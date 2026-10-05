package com.example.six_sense.data;

import android.os.Handler;
import android.os.Looper;

import com.example.six_sense.model.Device;

/**
 * 실제 가전이 없으니 시간이 흐르는 것처럼 보이게 하는 데모용 시뮬레이터.
 *
 *   청소기: 청소 60분 코스. 데모에선 1초 = 1분으로 돌린다. 끝나면 "청소 완료", 배터리는 12분마다 5% 소모
 *   커피:   10초 동안 0→100%로 차오르고, "완성!" 3초 보여준 뒤 꺼진다
 *
 * 전부 메인 스레드 Handler라서 Repository 리스너를 그대로 UI에서 써도 된다.
 * 실제 기기 붙으면 이 클래스는 통째로 빠진다.
 */
public final class DemoSimulator {

    public static final int VACUUM_TOTAL_MIN = 60;
    private static final long VACUUM_TICK_MS = 1_000L;     // 1초 = 1분

    private static final long COFFEE_TICK_MS = 100L;
    private static final int COFFEE_TOTAL_TICKS = 100;     // 10초
    private static final long COFFEE_DONE_HOLD_MS = 3_000L;

    public static final String VACUUM_DONE = "청소 완료!";
    public static final String VACUUM_PAUSED = "청소 멈춤";

    public static final String BATH_FILLING = "물 받는 중";
    public static final String BATH_PAUSED = "물 받기 멈춤";
    public static final String BATH_FILLED = "물 받기 완료!";
    public static final String BATH_DRAINING = "물 빼는 중";
    public static final String BATH_DRAIN_PAUSED = "물 빼기 멈춤";
    public static final String BATH_DRAINED = "물 빼기 완료!";
    private static final long BATH_TICK_MS = 100L;      // 받기 100틱 = 10초, 빼기 50틱 = 5초
    private static final long DONE_HOLD_MS = 3_000L;   // "완료!" 문구를 보여주는 시간

    private static DemoSimulator instance;

    public static synchronized DemoSimulator get() {
        if (instance == null) instance = new DemoSimulator();
        return instance;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DeviceRepository devices = DeviceRepository.getInstance();

    private Runnable vacuumTick;
    private Runnable chargeTick;
    private Runnable coffeeTick;
    private Runnable bathTick;

    private DemoSimulator() {
    }

    // ---------------------------------------------------------------------------------------------
    // 청소기: OFF → 청소 중(60분) → "청소 완료!" 3초 → OFF. 꺼져 있는 동안은 충전된다
    // ---------------------------------------------------------------------------------------------

    /** 처음부터 청소 */
    public void startVacuum() {
        runVacuumFrom(0);
    }

    /** 멈춘 곳(지난 분)부터 다시 */
    public void resumeVacuum() {
        Device d = devices.find(DeviceRepository.ID_VACUUM);
        runVacuumFrom(d == null ? 0 : d.getAux());
    }

    /** 청소 중단: 진행 분은 그대로 두고 멈춘다. 전원은 켜진 채 (충전기로 가지 않음) */
    public void pauseVacuum() {
        stopVacuumTimers();
        devices.update(DeviceRepository.ID_VACUUM, true, VACUUM_PAUSED, null);
    }

    public boolean isVacuumPaused(Device d) {
        return d.isOn() && VACUUM_PAUSED.equals(d.getNote());
    }

    private void runVacuumFrom(int elapsedMin) {
        stopVacuumTimers();
        devices.update(DeviceRepository.ID_VACUUM, true, "", elapsedMin);
        vacuumTick = new Runnable() {
            @Override
            public void run() {
                Device d = devices.find(DeviceRepository.ID_VACUUM);
                if (d == null || !d.isOn() || VACUUM_PAUSED.equals(d.getNote())) return;
                int elapsed = d.getAux() + 1;
                if (elapsed % 12 == 0) d.setBattery(d.getBattery() - 5);
                if (elapsed >= VACUUM_TOTAL_MIN) {
                    devices.update(DeviceRepository.ID_VACUUM, true, VACUUM_DONE, VACUUM_TOTAL_MIN);
                    handler.postDelayed(() -> {
                        devices.update(DeviceRepository.ID_VACUUM, false, "", 0);
                        ensureCharging();
                    }, DONE_HOLD_MS);
                    return;
                }
                devices.setAux(DeviceRepository.ID_VACUUM, elapsed);
                handler.postDelayed(this, VACUUM_TICK_MS);
            }
        };
        handler.postDelayed(vacuumTick, VACUUM_TICK_MS);
    }

    /** 청소 중에 끄면 바로 OFF, 충전기로 돌아간다 */
    public void stopVacuum() {
        stopVacuumTimers();
        devices.update(DeviceRepository.ID_VACUUM, false, "", 0);
        ensureCharging();
    }

    /** 꺼져 있고 100% 미만이면 2초마다 5%씩 충전. 이미 돌고 있으면 그대로 */
    public void ensureCharging() {
        if (chargeTick != null) return;
        chargeTick = new Runnable() {
            @Override
            public void run() {
                Device d = devices.find(DeviceRepository.ID_VACUUM);
                if (d == null || d.isOn() || d.getBattery() >= 100) {
                    chargeTick = null;
                    return;
                }
                devices.setBattery(DeviceRepository.ID_VACUUM, d.getBattery() + 5);
                handler.postDelayed(this, 2_000L);
            }
        };
        handler.postDelayed(chargeTick, 2_000L);
    }

    private void stopVacuumTimers() {
        if (vacuumTick != null) {
            handler.removeCallbacks(vacuumTick);
            vacuumTick = null;
        }
        if (chargeTick != null) {
            handler.removeCallbacks(chargeTick);
            chargeTick = null;
        }
    }

    public boolean isVacuumDoneShowing(Device d) {
        return d.isOn() && VACUUM_DONE.equals(d.getNote());
    }

    // ---------------------------------------------------------------------------------------------
    // 욕조: 물 받기 → 물 받는 중 → (누르면) 물 받기 완료! → 물 빼기 → 물 빼는 중 → 물 빼기 완료! 3초 → OFF
    // ---------------------------------------------------------------------------------------------

    /** 물 받기: 10초 동안 aux 0→100. 멈춰 있었으면 그 수위부터 이어서. 다 차면 "물 받기 완료!" */
    public void fillBath() {
        stopBath();
        Device cur = devices.find(DeviceRepository.ID_BATH);
        int from = (cur != null && BATH_PAUSED.equals(cur.getNote())) ? cur.getAux() : 0;
        devices.update(DeviceRepository.ID_BATH, true, BATH_FILLING, from);
        bathTick = new Runnable() {
            @Override
            public void run() {
                Device d = devices.find(DeviceRepository.ID_BATH);
                if (d == null || !BATH_FILLING.equals(d.getNote())) return;
                int next = d.getAux() + 1;
                if (next >= 100) {
                    devices.update(DeviceRepository.ID_BATH, true, BATH_FILLED, 100);
                    return;
                }
                devices.setAux(DeviceRepository.ID_BATH, next);
                handler.postDelayed(this, BATH_TICK_MS);
            }
        };
        handler.postDelayed(bathTick, BATH_TICK_MS);
    }

    /** 받는 중 멈춤. 수위는 그대로 두고 물만 잠근다 */
    public void pauseFilling() {
        stopBath();
        devices.update(DeviceRepository.ID_BATH, true, BATH_PAUSED, null);
    }

    /** 받는 중에 큰 버튼을 한 번 더 누르면 그 자리에서 완료 */
    public void finishFilling() {
        stopBath();
        devices.update(DeviceRepository.ID_BATH, true, BATH_FILLED, 100);
    }

    /** 물 빼기: 지금 수위에서 0까지 (가득 찬 물 기준 5초). 끝나면 "물 빼기 완료!" 3초 → OFF */
    public void drainBath() {
        stopBath();
        Device cur = devices.find(DeviceRepository.ID_BATH);
        int level = cur == null ? 100 : Math.max(1, cur.getAux());
        devices.update(DeviceRepository.ID_BATH, true, BATH_DRAINING, level);
        bathTick = new Runnable() {
            @Override
            public void run() {
                Device d = devices.find(DeviceRepository.ID_BATH);
                if (d == null || !BATH_DRAINING.equals(d.getNote())) return;
                int next = d.getAux() - 2;   // 50틱 = 5초에 100 → 0
                if (next <= 0) {
                    devices.update(DeviceRepository.ID_BATH, true, BATH_DRAINED, 0);
                    bathTick = () -> devices.update(DeviceRepository.ID_BATH, false, "", 0);
                    handler.postDelayed(bathTick, DONE_HOLD_MS);
                    return;
                }
                devices.setAux(DeviceRepository.ID_BATH, next);
                handler.postDelayed(this, BATH_TICK_MS);
            }
        };
        handler.postDelayed(bathTick, BATH_TICK_MS);
    }

    /** 빼는 중 멈춤. 수위는 그대로 */
    public void pauseDraining() {
        stopBath();
        devices.update(DeviceRepository.ID_BATH, true, BATH_DRAIN_PAUSED, null);
    }

    private void stopBath() {
        if (bathTick != null) {
            handler.removeCallbacks(bathTick);
            bathTick = null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 커피
    // ---------------------------------------------------------------------------------------------

    public void brewCoffee(String menu) {
        stopCoffee();
        devices.update(DeviceRepository.ID_COFFEE, true, menu + " 내리는 중", 0);
        coffeeTick = new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                Device d = devices.find(DeviceRepository.ID_COFFEE);
                if (d == null || !d.isOn()) return;
                ticks++;
                int percent = ticks * 100 / COFFEE_TOTAL_TICKS;
                if (ticks >= COFFEE_TOTAL_TICKS) {
                    devices.update(DeviceRepository.ID_COFFEE, true, menu + " 완성!", 100);
                    handler.postDelayed(() ->
                            devices.update(DeviceRepository.ID_COFFEE, false, "대기 · 물 충분", 0), COFFEE_DONE_HOLD_MS);
                    return;
                }
                devices.setAux(DeviceRepository.ID_COFFEE, percent);
                handler.postDelayed(this, COFFEE_TICK_MS);
            }
        };
        handler.postDelayed(coffeeTick, COFFEE_TICK_MS);
    }

    public boolean isCoffeeDone(Device d) {
        return d.isOn() && d.getNote() != null && d.getNote().endsWith("완성!");
    }

    private void stopCoffee() {
        if (coffeeTick != null) {
            handler.removeCallbacks(coffeeTick);
            coffeeTick = null;
        }
    }
}
