package com.example.six_sense.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.List;

import com.example.six_sense.MainActivity;
import com.example.six_sense.R;
import com.example.six_sense.SixSenseApp;
import com.example.six_sense.ai.ClimateGuide;
import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.data.SettingsStore;
import com.example.six_sense.databinding.FragmentHomeGlanceBinding;
import com.example.six_sense.databinding.ItemFavoriteBinding;
import com.example.six_sense.databinding.ItemFavoriteEmptyBinding;
import com.example.six_sense.model.Device;
import com.example.six_sense.ui.DeviceIcons;
import com.example.six_sense.ui.devices.DeviceDetailActivity;
import com.example.six_sense.ui.devices.PowerControl;
import com.example.six_sense.ui.listen.ListenActivity;

/**
 * 화면 01. 홈 — 한눈에 모드. 스크롤 없이 한 화면.
 *
 *   마이크
 *   온도 카드  : 현재 온도 / 희망 온도(−/+) + 권장 온도 / 에어컨·보일러 상태 타일
 *   즐겨찾기 2칸: 기기 화면에서 별표한 기기. 비면 "기기를 추가하기"
 *
 * 에어컨/보일러는 미리 추천 강조를 하지 않는다. 희망 온도를 마지막으로 건드린 지 2초 지나면
 * "켤까요?" 창이 뜨고, 켜진 뒤에야 타일이 ON(청록)으로 바뀐다.
 */
public class GlanceHomeFragment extends Fragment implements DeviceRepository.Listener {

    private static final float OFF_ALPHA = 0.55f;
    private static final long ASK_DELAY_MS = 2_000L;

    private FragmentHomeGlanceBinding binding;
    private final DeviceRepository devices = DeviceRepository.getInstance();
    private SettingsStore settings;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable askClimate = this::showClimateDialog;
    private AlertDialog climateDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeGlanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        settings = SixSenseApp.get().settings();
        binding.textGreeting.setText(greetingForNow());

        binding.buttonMic.setOnClickListener(v ->
                startActivity(ListenActivity.intentForVoice(requireContext())));

        if (devices.getDesiredTemperature() < 0) {
            devices.setDesiredTemperature(ClimateGuide.recommendedNow());
        }
        binding.buttonTempMinus.setOnClickListener(v -> nudgeDesired(-1));
        binding.buttonTempPlus.setOnClickListener(v -> nudgeDesired(+1));

        // 타일을 직접 누르면 창 없이 켜고 끈다
        binding.buttonChoiceAc.setOnClickListener(v -> toggleClimate(DeviceRepository.ID_AC, false));
        binding.buttonChoiceBoiler.setOnClickListener(v -> toggleClimate(DeviceRepository.ID_BOILER, true));
    }

    @Override
    public void onStart() {
        super.onStart();
        devices.addListener(this);
        renderAll();
    }

    @Override
    public void onResume() {
        super.onResume();
        renderAll();   // 상세 화면에서 별표 누르고 돌아온 경우
    }

    /** 하단 탭은 show/hide로 바꿔서 onStart/onResume이 안 불린다. 기기 탭에서 별표 → 홈 탭 전환을 여기서 잡는다 */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && binding != null) renderAll();
    }

    @Override
    public void onStop() {
        main.removeCallbacks(askClimate);
        if (climateDialog != null) climateDialog.dismiss();
        devices.removeListener(this);
        super.onStop();
    }

    @Override
    public void onDevicesChanged() {
        if (binding == null) return;
        binding.getRoot().post(this::renderAll);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ---------------------------------------------------------------------------------------------
    // 온도
    // ---------------------------------------------------------------------------------------------

    private void nudgeDesired(int delta) {
        devices.setDesiredTemperature(devices.getDesiredTemperature() + delta);
        main.removeCallbacks(askClimate);
        main.postDelayed(askClimate, ASK_DELAY_MS);
    }

    /** 권장 온도와 비교해서 한 쪽만 묻는다. 같으면 현재 온도 기준으로 */
    private void showClimateDialog() {
        if (binding == null || !isResumed()) return;
        int desired = devices.getDesiredTemperature();

        boolean boiler;
        int messageRes;
        switch (ClimateGuide.suggest(desired)) {
            case BOILER:
                boiler = true;
                messageRes = R.string.home_climate_ask_boiler;
                break;
            case AC:
                boiler = false;
                messageRes = R.string.home_climate_ask_ac;
                break;
            default:
                boiler = desired > devices.getRoomTemperature();
                messageRes = R.string.home_climate_ask_none;
        }
        int yesRes = boiler ? R.string.home_climate_turn_on_boiler : R.string.home_climate_turn_on_ac;
        final boolean useBoiler = boiler;

        if (climateDialog != null) climateDialog.dismiss();
        climateDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.home_climate_ask_title, desired))
                .setMessage(messageRes)
                .setPositiveButton(yesRes, (d, w) -> runClimate(useBoiler))
                .setNegativeButton(R.string.home_climate_no, null)
                .show();
    }

    private void runClimate(boolean useBoiler) {
        main.removeCallbacks(askClimate);
        devices.runClimate(useBoiler);
        String name = getString(useBoiler ? R.string.home_choice_boiler : R.string.home_choice_ac);
        Toast.makeText(requireContext(),
                getString(R.string.home_climate_started, name, devices.getDesiredTemperature()),
                Toast.LENGTH_SHORT).show();
    }

    /** 타일 직접 터치: 꺼져 있으면 희망 온도로 켜고(반대쪽 끔), 켜져 있으면 끈다 */
    private void toggleClimate(String id, boolean isBoiler) {
        Device d = devices.find(id);
        if (d == null) return;
        if (d.isOn()) {
            devices.setOn(id, false);
        } else {
            runClimate(isBoiler);
        }
    }

    private void renderTemperature() {
        int month = ClimateGuide.currentMonth();
        binding.textTempNow.setText(getString(R.string.unit_celsius, devices.getRoomTemperature()));
        binding.textTempDesired.setText(getString(R.string.unit_celsius, devices.getDesiredTemperature()));
        binding.textRecommendedLabel.setText(getString(R.string.home_temp_recommended_short, month));
        binding.textRecommendedValue.setText(getString(R.string.unit_celsius, ClimateGuide.recommendedFor(month)));

        paintTile(binding.buttonChoiceAc, getString(R.string.home_choice_ac), devices.find(DeviceRepository.ID_AC));
        paintTile(binding.buttonChoiceBoiler, getString(R.string.home_choice_boiler), devices.find(DeviceRepository.ID_BOILER));
    }

    private void paintTile(TextView tile, String name, @Nullable Device d) {
        boolean on = d != null && d.isOn();
        tile.setText(name + "\n" + getString(on ? R.string.state_on : R.string.state_off));
        paintState(tile, on);
    }

    // ---------------------------------------------------------------------------------------------
    // 즐겨찾기 2칸
    // ---------------------------------------------------------------------------------------------

    private void renderAll() {
        renderTemperature();
        List<String> ids = settings.getFavorites();
        fillSlot(binding.slot1, ids.size() > 0 ? devices.find(ids.get(0)) : null);
        fillSlot(binding.slot2, ids.size() > 1 ? devices.find(ids.get(1)) : null);
    }

    private void fillSlot(FrameLayout slot, @Nullable Device d) {
        slot.removeAllViews();
        if (d == null) {
            ItemFavoriteEmptyBinding empty = ItemFavoriteEmptyBinding.inflate(getLayoutInflater(), slot, false);
            empty.cardRoot.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).selectTab(R.id.nav_devices);
                }
            });
            slot.addView(empty.getRoot());
            return;
        }

        ItemFavoriteBinding tile = ItemFavoriteBinding.inflate(getLayoutInflater(), slot, false);
        tile.imageIcon.setImageResource(DeviceIcons.of(d.getType()));
        tile.textName.setText(d.getName());
        tile.textState.setText(d.describeState());

        if (d.isToggleable()) {
            tile.buttonPower.setText(d.isOn() ? R.string.state_on : R.string.state_off);
            paintState(tile.buttonPower, d.isOn());
            tile.buttonPower.setOnClickListener(v -> PowerControl.request(requireContext(), d, !d.isOn()));
        } else {
            boolean warn = com.example.six_sense.data.FridgeInventory.hasExpiring();
            tile.buttonPower.setText(com.example.six_sense.data.FridgeInventory.statusLabel());
            tile.buttonPower.setTextSize(warn ? 14f : 19f);
            tile.buttonPower.setBackgroundResource(warn ? R.drawable.bg_state_warn : R.drawable.bg_state_on);
            tile.buttonPower.setAlpha(1f);
            tile.buttonPower.setOnClickListener(v -> openDetail(d));
        }
        tile.cardRoot.setOnClickListener(v -> openDetail(d));
        slot.addView(tile.getRoot());
    }

    private void openDetail(Device d) {
        startActivity(DeviceDetailActivity.intent(requireContext(), d.getId()));
    }

    /** 켜짐: 청록 + 불투명. 꺼짐: 주홍 + 반투명 */
    private void paintState(View v, boolean on) {
        v.setBackgroundResource(on ? R.drawable.bg_state_on : R.drawable.bg_state_off);
        v.setAlpha(on ? 1f : OFF_ALPHA);
    }

    private String greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.home_greeting_morning);
        if (hour < 18) return getString(R.string.home_greeting_afternoon);
        return getString(R.string.home_greeting_evening);
    }
}
