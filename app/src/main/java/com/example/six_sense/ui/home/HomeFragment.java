package com.example.six_sense.ui.home;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Calendar;
import java.util.List;

import com.example.six_sense.R;
import com.example.six_sense.ai.ClimateGuide;
import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.data.HistoryRepository;
import com.example.six_sense.databinding.FragmentHomeBinding;
import com.example.six_sense.model.Device;
import com.example.six_sense.model.DeviceType;
import com.example.six_sense.ui.listen.ListenActivity;

/**
 * 화면 01. 홈 · 대기.
 *
 * 위: 마이크 + 자주 쓰는 말 2개 (기록에서 횟수순)
 * 아래 "지금 집 상태":
 *   온도   - 현재 온도는 보기만. 옆에 희망 온도(−/+)와 에어컨/보일러 선택.
 *            희망 온도가 이달 권장치보다 높으면 보일러, 낮으면 에어컨 쪽을 키워서 강조
 *   조명   - 공간별 타일. 켜짐 청록 / 꺼짐 주홍. 꺼짐은 투명도도 낮춤
 *   블라인드 - ON/OFF 버튼 하나
 *
 * 켜짐/꺼짐 색은 적록색약 구분을 위해 초록/빨강 대신 Okabe-Ito 청록/주홍 (colors.xml 참고).
 */
public class HomeFragment extends Fragment implements DeviceRepository.Listener, HistoryRepository.Listener {

    /** 꺼진 타일/버튼의 투명도. 색 구분이 어려운 사용자도 켜짐과 차이를 느끼게 */
    private static final float OFF_ALPHA = 0.55f;

    private FragmentHomeBinding binding;
    private final DeviceRepository devices = DeviceRepository.getInstance();
    private final HistoryRepository history = HistoryRepository.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.textGreeting.setText(greetingForNow());

        binding.buttonMic.setOnClickListener(v ->
                startActivity(ListenActivity.intentForVoice(requireContext())));

        // 희망 온도. 처음 들어오면 이달 권장 온도로 시작
        if (devices.getDesiredTemperature() < 0) {
            devices.setDesiredTemperature(ClimateGuide.recommendedNow());
        }
        binding.buttonTempMinus.setOnClickListener(v ->
                devices.setDesiredTemperature(devices.getDesiredTemperature() - 1));
        binding.buttonTempPlus.setOnClickListener(v ->
                devices.setDesiredTemperature(devices.getDesiredTemperature() + 1));

        binding.buttonChoiceAc.setOnClickListener(v -> runClimate(false));
        binding.buttonChoiceBoiler.setOnClickListener(v -> runClimate(true));

        binding.buttonBlind.setOnClickListener(v -> devices.toggle(DeviceRepository.ID_BLIND));
    }

    @Override
    public void onStart() {
        super.onStart();
        devices.addListener(this);
        history.addListener(this);
        renderAll();
    }

    @Override
    public void onStop() {
        devices.removeListener(this);
        history.removeListener(this);
        super.onStop();
    }

    @Override
    public void onDevicesChanged() {
        if (binding == null) return;
        binding.getRoot().post(this::renderStatus);
    }

    @Override
    public void onHistoryChanged() {
        if (binding == null) return;
        binding.getRoot().post(this::renderFrequent);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ---------------------------------------------------------------------------------------------
    // 자주 쓰는 말
    // ---------------------------------------------------------------------------------------------

    private void renderAll() {
        renderFrequent();
        renderStatus();
    }

    private void renderFrequent() {
        List<String> top = history.getFrequentUtterances(2);
        bindFrequent(binding.buttonFrequent1, top.size() > 0 ? top.get(0) : null);
        bindFrequent(binding.buttonFrequent2, top.size() > 1 ? top.get(1) : null);
        binding.textFrequentEmpty.setVisibility(top.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void bindFrequent(TextView button, @Nullable String utterance) {
        if (utterance == null) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setText("“" + utterance + "”");
        button.setOnClickListener(v ->
                startActivity(ListenActivity.intentForText(requireContext(), utterance)));
    }

    // ---------------------------------------------------------------------------------------------
    // 집 상태
    // ---------------------------------------------------------------------------------------------

    private void renderStatus() {
        renderTemperature();
        renderLights();
        renderBlind();
    }

    private void renderTemperature() {
        int now = devices.getRoomTemperature();
        int desired = devices.getDesiredTemperature();
        int month = ClimateGuide.currentMonth();

        binding.textTempNow.setText(getString(R.string.unit_celsius, now));
        binding.textTempDesired.setText(getString(R.string.unit_celsius, desired));
        binding.textTempRecommended.setText(
                getString(R.string.home_temp_recommended, month, ClimateGuide.recommendedFor(month)));

        ClimateGuide.Suggestion s = ClimateGuide.suggest(desired);
        emphasize(binding.buttonChoiceAc, s == ClimateGuide.Suggestion.AC);
        emphasize(binding.buttonChoiceBoiler, s == ClimateGuide.Suggestion.BOILER);
    }

    /** 권장되는 쪽: 글자 키우고 굵게, 테두리 색. 아닌 쪽: 기본 */
    private void emphasize(TextView button, boolean on) {
        button.setTextSize(on ? 19f : 15f);
        button.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        button.setTextColor(requireContext().getColor(on ? R.color.rose_deep : R.color.ink));
        button.setBackgroundResource(on ? R.drawable.bg_choice_emphasis : R.drawable.bg_choice);
    }

    private void runClimate(boolean useBoiler) {
        devices.runClimate(useBoiler);
        String name = getString(useBoiler ? R.string.home_choice_boiler : R.string.home_choice_ac);
        Toast.makeText(requireContext(),
                getString(R.string.home_climate_started, name, devices.getDesiredTemperature()),
                Toast.LENGTH_SHORT).show();
    }

    /** 조명 타일을 조명 개수만큼 만든다. 공간 이름을 크게, 아래에 ON/OFF */
    private void renderLights() {
        LinearLayout group = binding.groupLights;
        group.removeAllViews();

        int gap = dp(8);
        boolean first = true;
        for (Device d : devices.getAll()) {
            if (d.getType() != DeviceType.LIGHT) continue;

            TextView tile = new TextView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(76), 1f);
            if (!first) lp.setMarginStart(gap);
            first = false;
            tile.setLayoutParams(lp);
            tile.setGravity(android.view.Gravity.CENTER);
            tile.setTextColor(requireContext().getColor(R.color.white));
            tile.setTypeface(null, Typeface.BOLD);
            tile.setTextSize(20f);
            tile.setLineSpacing(0f, 1.1f);
            tile.setText(d.getRoom().getLabel() + "\n" + getString(d.isOn() ? R.string.state_on : R.string.state_off));
            paintState(tile, d.isOn());
            tile.setContentDescription(d.getName() + " " + (d.isOn() ? "켜짐" : "꺼짐"));
            tile.setOnClickListener(v -> devices.toggle(d.getId()));
            group.addView(tile);
        }
    }

    private void renderBlind() {
        Device blind = devices.find(DeviceRepository.ID_BLIND);
        if (blind == null) return;
        binding.buttonBlind.setText(blind.isOn() ? R.string.state_on : R.string.state_off);
        paintState(binding.buttonBlind, blind.isOn());
    }

    /** 켜짐: 청록 + 불투명. 꺼짐: 주홍 + 반투명 */
    private void paintState(View v, boolean on) {
        v.setBackgroundResource(on ? R.drawable.bg_state_on : R.drawable.bg_state_off);
        v.setAlpha(on ? 1f : OFF_ALPHA);
    }

    // ---------------------------------------------------------------------------------------------

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.home_greeting_morning);
        if (hour < 18) return getString(R.string.home_greeting_afternoon);
        return getString(R.string.home_greeting_evening);
    }
}
