package com.example.six_sense.ui.devices;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.six_sense.R;
import com.example.six_sense.SixSenseApp;
import com.example.six_sense.ai.ClimateGuide;
import com.example.six_sense.data.DemoSimulator;
import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.data.FridgeInventory;
import com.example.six_sense.data.SettingsStore;
import com.example.six_sense.databinding.ActivityDeviceDetailBinding;
import com.example.six_sense.model.Device;
import com.example.six_sense.model.DeviceType;
import com.example.six_sense.ui.BaseActivity;
import com.example.six_sense.ui.DeviceIcons;
import com.example.six_sense.ui.widget.VerticalSeekBar;

/**
 * 기기 하나의 상세 조절 화면.
 *
 * 공통: 이름/상태, 큰 상태 버튼(button_power), 즐겨찾기 별.
 * 큰 버튼의 글자는 기기마다 다르다 — 조명/에어컨/보일러/오븐은 ON·OFF, 욕조는 물 받기/물 받는 중,
 * 청소기는 청소 시작/청소 중/청소 완료/충전 중, 냉장고는 정상/임박.
 *
 * 꺼져 있으면 조절 블록은 회색·반투명·비활성 (켜져 있다는 걸 먼저 보게).
 */
public class DeviceDetailActivity extends BaseActivity implements DeviceRepository.Listener {

    private static final String EXTRA_ID = "device_id";
    private static final float OFF_ALPHA = 0.55f;
    private static final float DISABLED_ALPHA = 0.45f;

    private static final String[] COFFEE_MENUS = {"아메리카노", "에스프레소", "라떼"};
    private final DemoSimulator sim = DemoSimulator.get();

    public static Intent intent(Context ctx, String deviceId) {
        return new Intent(ctx, DeviceDetailActivity.class).putExtra(EXTRA_ID, deviceId);
    }

    private ActivityDeviceDetailBinding b;
    private final DeviceRepository devices = DeviceRepository.getInstance();
    private SettingsStore settings;
    private Device device;

    private int coffeeMenu = 0;   // 0 아메리카노, 1 에스프레소, 2 라떼

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDeviceDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        settings = SixSenseApp.get().settings();

        device = devices.find(getIntent().getStringExtra(EXTRA_ID));
        if (device == null) {
            finish();
            return;
        }

        b.buttonBack.setOnClickListener(v -> finish());
        b.buttonFavorite.setOnClickListener(v -> toggleFavorite());
        // 큰 버튼 = 전원. 냉장고는 끄고 켜는 기기가 아니라 상태 표시만
        b.buttonPower.setOnClickListener(v -> onPowerClick());

        setupForType();
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        devices.addListener(this);
    }

    @Override
    protected void onStop() {
        devices.removeListener(this);
        super.onStop();
    }

    @Override
    public void onDevicesChanged() {
        runOnUiThread(this::render);
    }

    // ---------------------------------------------------------------------------------------------
    // 큰 버튼 동작 (기기마다 다름)
    // ---------------------------------------------------------------------------------------------

    /**
     * 큰 버튼을 눌렀을 때. 기기마다 "꺼도 되는 때"가 달라서 PowerControl이 거른다.
     *   욕조: 꺼져 있으면 물 받기 시작. 물이 있는 동안은 아래 멈춤/물 빼기 버튼으로만
     *   청소기: 꺼져 있으면 청소 시작. 청소 중엔 안 눌림, 중단 상태에서 누르면 OFF
     *   커피: 내리는 중·완성! 동안은 안 눌림
     */
    private void onPowerClick() {
        if (device.getType() == DeviceType.FRIDGE) return;
        PowerControl.request(this, device, !device.isOn());
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------------------------------------
    // 종류별 섹션 (한 번만)
    // ---------------------------------------------------------------------------------------------

    private void setupForType() {
        switch (device.getType()) {
            case LIGHT:
                b.sectionLight.setVisibility(View.VISIBLE);
                b.seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                        if (fromUser) b.textBrightness.setText(progress * 10 + "%");
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar s) { }

                    @Override
                    public void onStopTrackingTouch(SeekBar s) {
                        devices.setValue(device.getId(), Math.max(10, s.getProgress() * 10), true);
                    }
                });
                b.buttonPreset1.setOnClickListener(v -> devices.setValue(device.getId(), 30, true));
                b.buttonPreset2.setOnClickListener(v -> devices.setValue(device.getId(), 70, true));
                b.buttonPreset3.setOnClickListener(v -> devices.setValue(device.getId(), 100, true));
                break;

            case AC:
            case BOILER:
                b.sectionClimate.setVisibility(View.VISIBLE);
                int month = ClimateGuide.currentMonth();
                b.textClimateRecLabel.setText(getString(R.string.detail_recommended, month));
                b.textClimateRec.setText(getString(R.string.unit_celsius, ClimateGuide.recommendedFor(month)));
                b.buttonClimateMinus.setOnClickListener(v -> stepValue(-1, 16, 30, true));
                b.buttonClimatePlus.setOnClickListener(v -> stepValue(+1, 16, 30, true));
                break;

            case OVEN:
                b.sectionOven.setVisibility(View.VISIBLE);
                b.buttonOvenMinus.setOnClickListener(v -> stepValue(-10, 100, 250, false));
                b.buttonOvenPlus.setOnClickListener(v -> stepValue(+10, 100, 250, false));
                // 프리셋은 온도와 시간을 같이 맞춘다
                b.buttonOven1.setOnClickListener(v -> ovenPreset(180, 25));
                b.buttonOven2.setOnClickListener(v -> ovenPreset(200, 40));
                b.buttonOven3.setOnClickListener(v -> ovenPreset(220, 10));
                b.sectionOvenTimer.setVisibility(View.VISIBLE);
                b.buttonOvenTime5.setOnClickListener(v -> addOvenTime(5));
                b.buttonOvenTime10.setOnClickListener(v -> addOvenTime(10));
                b.buttonOvenTime30.setOnClickListener(v -> addOvenTime(30));
                b.buttonOvenTimeReset.setOnClickListener(v -> devices.setAux(device.getId(), 0));
                break;

            case BATH:
                b.sectionBath.setVisibility(View.VISIBLE);
                b.buttonBathMinus.setOnClickListener(v -> stepValue(-1, 30, 45, false));
                b.buttonBathPlus.setOnClickListener(v -> stepValue(+1, 30, 45, false));
                b.buttonSecondary.setText(R.string.detail_bath_drain);
                b.buttonSecondary.setOnClickListener(v -> sim.drainBath());
                b.buttonBathPause.setOnClickListener(v -> {
                    if (isBathState(DemoSimulator.BATH_FILLING)) sim.pauseFilling();
                    else if (isBathState(DemoSimulator.BATH_PAUSED)) sim.fillBath();
                    else if (isBathState(DemoSimulator.BATH_DRAINING)) sim.pauseDraining();
                    else if (isBathState(DemoSimulator.BATH_DRAIN_PAUSED)) sim.drainBath();
                });
                break;

            case BLIND:
                b.powerFrame.setVisibility(View.GONE);   // 블라인드는 ON/OFF 대신 올림/내림
                b.sectionBlind.setVisibility(View.VISIBLE);
                b.seekBlind.setListener(new VerticalSeekBar.Listener() {
                    @Override
                    public void onChanging(int progress) {
                        drawBlind(progress * 10);
                    }

                    @Override
                    public void onRelease(int progress) {
                        devices.setBlindOpen(progress * 10);
                    }
                });
                b.windowFrame.post(() -> drawBlind(device.getValue()));
                b.buttonBlindUp.setOnClickListener(v -> devices.setBlindOpen(100));
                b.buttonBlindDown.setOnClickListener(v -> devices.setBlindOpen(0));
                break;

            case VACUUM:
                b.sectionVacuum.setVisibility(View.VISIBLE);
                b.sectionBattery.setVisibility(View.VISIBLE);
                sim.ensureCharging();   // 꺼져 있고 100% 미만이면 충전이 돌아가게
                b.buttonVacuumStop.setOnClickListener(v -> {
                    if (sim.isVacuumPaused(device)) sim.resumeVacuum();
                    else sim.pauseVacuum();
                });
                break;

            case COFFEE:
                b.sectionCoffee.setVisibility(View.VISIBLE);
                b.buttonCoffee1.setOnClickListener(v -> pickCoffee(0));
                b.buttonCoffee2.setOnClickListener(v -> pickCoffee(1));
                b.buttonCoffee3.setOnClickListener(v -> pickCoffee(2));
                b.buttonCoffeeBrew.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.state_on)));
                b.buttonCoffeeBrew.setOnClickListener(v -> sim.brewCoffee(COFFEE_MENUS[coffeeMenu]));
                break;

            case FRIDGE:
                b.sectionFridge.setVisibility(View.VISIBLE);
                b.buttonPower.setTextSize(22f);
                for (FridgeInventory.Item item : FridgeInventory.items()) {
                    TextView row = new TextView(this);
                    String days = item.daysLeft < 0 ? "" : "  (" + item.daysLeft + "일 남음)";
                    row.setText("· " + item.name + days);
                    row.setTextSize(17f);
                    row.setTextColor(getColor(item.isExpiring() ? R.color.amber : R.color.ink_soft));
                    if (item.isExpiring()) row.setTypeface(null, Typeface.BOLD);
                    row.setPadding(0, dp(6), 0, dp(6));
                    b.listItems.addView(row);
                }
                break;
        }
    }

    /** 온도 −/+. turnOn이면 값을 바꿀 때 켜진다 (에어컨/보일러) */
    private void stepValue(int delta, int min, int max, boolean turnOn) {
        int next = Math.max(min, Math.min(max, device.getValue() + delta));
        devices.setValue(device.getId(), next, turnOn);
    }

    private void ovenPreset(int temp, int minutes) {
        devices.setValue(device.getId(), temp, true);
        devices.setAux(device.getId(), minutes);
    }

    private void addOvenTime(int minutes) {
        devices.setAux(device.getId(), Math.min(180, device.getAux() + minutes));
    }

    private void pickCoffee(int index) {
        coffeeMenu = index;
        render();
    }

    private void toggleFavorite() {
        boolean was = settings.isFavorite(device.getId());
        boolean now = settings.toggleFavorite(device.getId());
        if (!was && !now) {
            Toast.makeText(this, R.string.device_favorite_full, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, now ? R.string.device_favorite_added : R.string.device_favorite_removed,
                Toast.LENGTH_SHORT).show();
        render();
    }

    // ---------------------------------------------------------------------------------------------
    // 상태 반영
    // ---------------------------------------------------------------------------------------------

    private void render() {
        b.imageIcon.setImageResource(DeviceIcons.of(device.getType()));
        b.textName.setText(device.getName());
        b.textState.setText(device.describeState());

        boolean fav = settings.isFavorite(device.getId());
        b.buttonFavorite.setImageResource(fav ? R.drawable.ic_star_filled : R.drawable.ic_star);
        b.buttonFavorite.setImageTintList(ColorStateList.valueOf(getColor(fav ? R.color.rose : R.color.muted)));

        boolean on = device.isOn();
        b.buttonSecondary.setVisibility(View.GONE);

        switch (device.getType()) {
            case LIGHT:
                paintPower(on ? R.string.state_on : R.string.state_off, on);
                b.seekBrightness.setProgress(device.getValue() / 10);
                b.textBrightness.setText(device.getValue() + "%");
                paintPreset(b.buttonPreset1, device.getValue() == 30);
                paintPreset(b.buttonPreset2, device.getValue() == 70);
                paintPreset(b.buttonPreset3, device.getValue() == 100);
                setSectionEnabled(b.sectionLight, on);
                break;

            case AC:
            case BOILER:
                paintPower(on ? R.string.state_on : R.string.state_off, on);
                b.textClimateNow.setText(getString(R.string.unit_celsius, devices.getRoomTemperature()));
                b.textClimateSet.setText(getString(R.string.unit_celsius, device.getValue()));
                int diff = device.getValue() - ClimateGuide.recommendedNow();
                if (diff > 0) b.textClimateCompare.setText(getString(R.string.detail_vs_higher, diff));
                else if (diff < 0) b.textClimateCompare.setText(getString(R.string.detail_vs_lower, -diff));
                else b.textClimateCompare.setText(R.string.detail_vs_same);
                setSectionEnabled(b.sectionClimate, on);
                break;

            case OVEN:
                paintPower(on ? R.string.state_on : R.string.state_off, on);
                b.textOvenTemp.setText(getString(R.string.unit_celsius, device.getValue()));
                paintPreset(b.buttonOven1, device.getValue() == 180 && device.getAux() == 25);
                paintPreset(b.buttonOven2, device.getValue() == 200 && device.getAux() == 40);
                paintPreset(b.buttonOven3, device.getValue() == 220 && device.getAux() == 10);
                b.textOvenTimer.setText(device.getAux() > 0
                        ? getString(R.string.detail_oven_minutes, device.getAux()) : "–");
                setSectionEnabled(b.sectionOven, on);
                setSectionEnabled(b.sectionOvenTimer, on);
                break;

            case BATH:
                renderBathButton();
                // 물이 들어 있는 동안(받는 중/완료/빼는 중) 글자 양옆에 물결
                if (on) {
                    android.graphics.drawable.Drawable wave = getDrawable(R.drawable.ic_water).mutate();
                    wave.setTint(getColor(isBathState(DemoSimulator.BATH_DRAINED) ? R.color.white : R.color.ink));
                    int s = dp(30);
                    wave.setBounds(0, 0, s, s);
                    b.buttonPower.setCompoundDrawables(wave, null, wave, null);
                } else {
                    b.buttonPower.setCompoundDrawables(null, null, null, null);
                }
                b.textBathTemp.setText(getString(R.string.unit_celsius, device.getValue()));
                // 물 빼기는 물이 차 있을 때만 (받는 중이거나 다 받은 뒤)
                boolean filling = isBathState(DemoSimulator.BATH_FILLING);
                boolean fillPaused = isBathState(DemoSimulator.BATH_PAUSED);
                boolean draining = isBathState(DemoSimulator.BATH_DRAINING);
                boolean drainPaused = isBathState(DemoSimulator.BATH_DRAIN_PAUSED);

                // 멈춤 버튼: 받는 중/받기 멈춤/빼는 중/빼기 멈춤일 때만. 다 차거나 다 빠지면 사라진다
                b.buttonBathPause.setVisibility(filling || fillPaused || draining || drainPaused ? View.VISIBLE : View.GONE);
                if (filling) b.buttonBathPause.setText(R.string.detail_bath_pause);
                else if (fillPaused) b.buttonBathPause.setText(R.string.detail_bath_resume);
                else if (draining) b.buttonBathPause.setText(R.string.detail_bath_drain_pause);
                else if (drainPaused) b.buttonBathPause.setText(R.string.detail_bath_drain_resume);

                // 물 빼기: 물이 있고 빼는 중이 아닐 때 (받는 중·받기 멈춤·받기 완료)
                boolean canDrain = filling || fillPaused || isBathState(DemoSimulator.BATH_FILLED);
                b.buttonSecondary.setVisibility(canDrain ? View.VISIBLE : View.GONE);
                setSectionEnabled(b.sectionBath, on);
                break;

            case BLIND:
                b.seekBlind.setProgress(device.getValue() / 10);
                drawBlind(device.getValue());
                boolean fullyOpen = device.getValue() >= 100;
                boolean fullyClosed = device.getValue() <= 0;
                b.buttonBlindUp.setEnabled(!fullyOpen);
                b.buttonBlindUp.setAlpha(fullyOpen ? 0.5f : 1f);
                b.buttonBlindDown.setEnabled(!fullyClosed);
                b.buttonBlindDown.setAlpha(fullyClosed ? 0.5f : 1f);
                break;

            case VACUUM:
                renderVacuum();
                break;

            case COFFEE:
                renderCoffee();
                break;

            case FRIDGE:
                renderFridge();
                break;
        }
    }

    private void paintPower(int textRes, boolean on) {
        b.buttonPower.setText(textRes);
        b.buttonPower.setTextColor(getColor(R.color.white));
        b.buttonPower.setBackgroundResource(on ? R.drawable.bg_state_on : R.drawable.bg_state_off);
        b.buttonPower.setAlpha(on ? 1f : OFF_ALPHA);
    }

    /** 세로 선택 줄. 고른 것은 채움 */
    private void paintPreset(TextView row, boolean selected) {
        row.setBackgroundResource(selected ? R.drawable.bg_choice_on : R.drawable.bg_choice);
        row.setTextColor(getColor(selected ? R.color.white : R.color.ink));
        row.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    /** 섹션 전체 비활성 + 회색/반투명 */
    private void setSectionEnabled(View section, boolean enabled) {
        setEnabledDeep(section, enabled);
        section.setAlpha(enabled ? 1f : DISABLED_ALPHA);
        section.setBackgroundResource(enabled ? R.drawable.bg_card : R.drawable.bg_card_disabled);
    }

    private static void setEnabledDeep(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), enabled);
        }
    }

    // ---- 블라인드 그림

    private void drawBlind(int openPercent) {
        int inner = b.windowFrame.getHeight() - b.windowFrame.getPaddingTop() - b.windowFrame.getPaddingBottom();
        if (inner <= 0) return;
        int closed = 100 - openPercent;
        int h = Math.round(inner * closed / 100f);

        ViewGroup.LayoutParams lp = b.viewBlind.getLayoutParams();
        lp.height = h;
        b.viewBlind.setLayoutParams(lp);
        b.viewBlind.setVisibility(h == 0 ? View.INVISIBLE : View.VISIBLE);

        int slats = closed / 10;
        b.viewBlind.removeAllViews();
        for (int i = 0; i < slats; i++) {
            b.viewBlind.addView(new View(this),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        b.textBlindValue.setText(getString(R.string.detail_blind_open_format, openPercent));
    }

    // ---- 욕조

    /** note가 비어 있는데 켜져 있으면(음성으로 켠 경우) "물 받는 중"으로 본다 */
    private boolean isBathState(String state) {
        String note = device.getNote();
        if ((note == null || note.isEmpty()) && device.isOn()) note = DemoSimulator.BATH_FILLING;
        return state.equals(note);
    }

    private void renderBathButton() {
        if (!device.isOn()) {
            clearProgress();
            paintPower(R.string.detail_bath_fill, false);
        } else if (isBathState(DemoSimulator.BATH_FILLING)) {
            // 물이 왼쪽부터 차오른다
            showProgress(getString(R.string.detail_bath_filling) + " " + device.getAux() + "%",
                    device.getAux(), R.drawable.bg_state_water);
            b.buttonPower.setTextColor(getColor(R.color.ink));
        } else if (isBathState(DemoSimulator.BATH_PAUSED)) {
            showProgress(getString(R.string.detail_bath_paused) + " " + device.getAux() + "%",
                    device.getAux(), R.drawable.bg_state_water);
            b.buttonPower.setTextColor(getColor(R.color.ink));
        } else if (isBathState(DemoSimulator.BATH_DRAINING)) {
            // 찬 물이 줄어든다
            showProgress(getString(R.string.detail_bath_draining) + " " + device.getAux() + "%",
                    device.getAux(), R.drawable.bg_state_water);
            b.buttonPower.setTextColor(getColor(R.color.ink));
        } else if (isBathState(DemoSimulator.BATH_DRAIN_PAUSED)) {
            showProgress(getString(R.string.detail_bath_drain_paused) + " " + device.getAux() + "%",
                    device.getAux(), R.drawable.bg_state_water);
            b.buttonPower.setTextColor(getColor(R.color.ink));
        } else if (isBathState(DemoSimulator.BATH_FILLED)) {
            clearProgress();
            paintPower(R.string.detail_bath_filled, true);
            b.buttonPower.setBackgroundResource(R.drawable.bg_state_water);
            b.buttonPower.setTextColor(getColor(R.color.ink));
        } else {
            clearProgress();
            paintPower(R.string.detail_bath_drained, true);
        }
    }

    // ---- 청소기: 타이머 링 + 캐릭터 / 배터리 카드

    private void renderVacuum() {
        boolean on = device.isOn();
        boolean paused = sim.isVacuumPaused(device);
        boolean doneShowing = sim.isVacuumDoneShowing(device);
        boolean cleaning = on && !paused && !doneShowing;

        // 큰 버튼: OFF / 청소 중 / 청소 멈춤 / 청소 완료!
        if (doneShowing) paintPower(R.string.detail_vacuum_done_bang, true);
        else if (paused) {
            // 멈춘 상태는 빨간 배경으로 (꺼진 건 아니라 투명도는 낮추지 않는다)
            paintPower(R.string.detail_vacuum_paused, true);
            b.buttonPower.setBackgroundResource(R.drawable.bg_state_off);
        }
        else if (cleaning) paintPower(R.string.detail_vacuum_cleaning, true);
        else paintPower(R.string.state_off, false);

        // 링: 지난 시간 회색, 남은 시간 로즈. 멈춰도 멈춘 자리 그대로
        int total = DemoSimulator.VACUUM_TOTAL_MIN;
        int elapsed = on ? device.getAux() : 0;
        b.ringVacuum.setProgress(elapsed / (float) total);
        if (doneShowing) b.textVacuumTime.setText(R.string.detail_vacuum_done_bang);
        else b.textVacuumTime.setText(getString(R.string.detail_vacuum_time_format, total - elapsed, total));

        // 캐릭터: 쉬는 동안 잠자고(z Z), 청소 중엔 깨어 있다
        boolean sleeping = !cleaning;
        b.imageVacuumRobot.setImageResource(sleeping ? R.drawable.ic_robot_sleep : R.drawable.ic_robot_awake);
        b.textVacuumZzz.setVisibility(sleeping ? View.VISIBLE : View.INVISIBLE);

        // 원 아래 버튼: 청소 중이면 [청소 중단](주홍), 멈췄으면 [청소 다시 시작](청록). 완료!·OFF면 숨김
        b.buttonVacuumStop.setVisibility(cleaning || paused ? View.VISIBLE : View.GONE);
        b.buttonVacuumStop.setText(paused ? R.string.detail_vacuum_resume : R.string.detail_vacuum_stop);
        int btnColor = getColor(paused ? R.color.state_on : R.color.state_off);
        b.buttonVacuumStop.setBackgroundResource(paused ? R.drawable.bg_state_on : R.drawable.bg_state_off);
        b.buttonVacuumStop.setBackgroundTintList(ColorStateList.valueOf(btnColor));

        renderBattery(on);
    }

    /** 배터리 카드. 5% 단위. 꺼져 있으면 충전 중/충전 완료!, 청소 중이면 남은 % */
    private void renderBattery(boolean cleaning) {
        int battery = (device.getBattery() / 5) * 5;
        if (cleaning) {
            b.textBattery.setText(getString(R.string.detail_battery_using, battery));
            b.textBatteryHint.setText(R.string.detail_battery_hint_using);
        } else if (battery >= 100) {
            b.textBattery.setText(R.string.detail_battery_full);
            b.textBatteryHint.setText(R.string.detail_battery_hint_full);
        } else {
            b.textBattery.setText(getString(R.string.detail_battery_charging, battery));
            b.textBatteryHint.setText(R.string.detail_battery_hint_charging);
        }
        b.viewBatteryFill.setBackgroundResource(battery <= 20 ? R.drawable.bg_battery_fill_low : R.drawable.bg_battery_fill);

        b.batteryShell.post(() -> {
            int inner = b.batteryShell.getWidth() - b.batteryShell.getPaddingLeft() - b.batteryShell.getPaddingRight();
            ViewGroup.LayoutParams lp = b.viewBatteryFill.getLayoutParams();
            lp.width = Math.max(0, Math.round(inner * battery / 100f));
            b.viewBatteryFill.setLayoutParams(lp);
        });
    }

    // ---- 커피: 메뉴(로즈) / 내리기(청록) 구분, 진행 채움

    private void renderCoffee() {
        boolean on = device.isOn();
        boolean brewing = on && device.getAux() > 0 && !sim.isCoffeeDone(device);
        boolean doneShowing = sim.isCoffeeDone(device);

        paintMenu(b.buttonCoffee1, 0);
        paintMenu(b.buttonCoffee2, 1);
        paintMenu(b.buttonCoffee3, 2);

        if (doneShowing) {
            // "아메리카노 완성!" 3초 (시뮬레이터가 3초 뒤 OFF로 돌린다)
            clearProgress();
            b.buttonPower.setText(device.getNote());
            b.buttonPower.setBackgroundResource(R.drawable.bg_state_on);
            b.buttonPower.setAlpha(1f);
        } else if (brewing) {
            showProgress(device.getNote(), device.getAux(), R.drawable.bg_state_on);
        } else {
            clearProgress();
            paintPower(on ? R.string.state_on : R.string.state_off, on);
        }

        // 꺼져 있거나 내리는 중이면 메뉴/내리기 막기
        boolean usable = on && !brewing && !doneShowing;
        setSectionEnabled(b.sectionCoffee, on);
        b.buttonCoffeeBrew.setEnabled(usable);
        b.buttonCoffeeBrew.setAlpha(usable ? 1f : 0.5f);
    }

    /** 커피 메뉴: 선택되면 연분홍 바탕 + 진한 테두리 + ✓. 내리기 버튼(청록 채움)과 확실히 다르게 */
    private void paintMenu(TextView row, int index) {
        boolean selected = coffeeMenu == index;
        row.setText((selected ? "✓  " : "") + COFFEE_MENUS[index]);
        row.setBackgroundResource(selected ? R.drawable.bg_menu_selected : R.drawable.bg_choice);
        row.setTextColor(getColor(selected ? R.color.rose_deep : R.color.ink));
        row.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    /**
     * 큰 버튼을 진행 바로 쓴다 (커피 내리기, 욕조 물 받기/빼기).
     * 회색 바탕(power_frame) 위로 percent만큼 fillRes 색이 왼쪽부터 찬다. 버튼 자체는 투명하게 두고 글자만 올린다.
     */
    private void showProgress(String text, int percent, int fillRes) {
        b.buttonPower.setText(text);
        b.buttonPower.setTextColor(getColor(R.color.white));
        b.buttonPower.setBackground(null);
        b.buttonPower.setAlpha(1f);
        b.powerFrame.setBackgroundResource(R.drawable.bg_state_gray);
        b.powerFill.setBackgroundResource(fillRes);
        b.powerFill.setVisibility(View.VISIBLE);
        final int p = Math.max(0, Math.min(100, percent));
        b.powerFrame.post(() -> {
            ViewGroup.LayoutParams lp = b.powerFill.getLayoutParams();
            lp.width = Math.round(b.powerFrame.getWidth() * p / 100f);
            b.powerFill.setLayoutParams(lp);
        });
    }

    private void clearProgress() {
        b.powerFrame.setBackground(null);
        b.powerFill.setVisibility(View.GONE);
    }

    // ---- 냉장고

    private void renderFridge() {
        b.buttonPower.setText(FridgeInventory.statusLabel());
        b.buttonPower.setBackgroundResource(FridgeInventory.hasExpiring() ? R.drawable.bg_state_warn : R.drawable.bg_state_on);
        b.buttonPower.setAlpha(1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
