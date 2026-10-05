package com.example.six_sense.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.six_sense.R;
import com.example.six_sense.SixSenseApp;
import com.example.six_sense.data.SettingsStore;
import com.example.six_sense.databinding.FragmentSettingsBinding;

/**
 * 화면 06. 설정.
 * 글자 크기 / 사투리 지역 / 응답 목소리 / 실행 방식. 개인정보 영역은 표시만.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsStore settings;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        settings = SixSenseApp.get().settings();

        // 글자 크기
        binding.fontNormal.setOnClickListener(v -> changeFont(SettingsStore.FontSize.NORMAL));
        binding.fontLarge.setOnClickListener(v -> changeFont(SettingsStore.FontSize.LARGE));
        binding.fontXlarge.setOnClickListener(v -> changeFont(SettingsStore.FontSize.XLARGE));
        renderFontSegment(settings.getFontSize());

        // 사투리 지역 / 목소리: 단일 선택 다이얼로그
        String[] regions = getResources().getStringArray(R.array.dialect_regions);
        String[] voices = getResources().getStringArray(R.array.voice_options);
        binding.textDialectValue.setText(regions[settings.getDialectIndex()]);
        binding.textVoiceValue.setText(voices[settings.getVoiceIndex()]);

        binding.rowDialect.setOnClickListener(v -> pick(R.string.settings_dialect, regions,
                settings.getDialectIndex(), i -> {
                    settings.setDialectIndex(i);
                    binding.textDialectValue.setText(regions[i]);
                }));
        binding.rowVoice.setOnClickListener(v -> pick(R.string.settings_voice, voices,
                settings.getVoiceIndex(), i -> {
                    settings.setVoiceIndex(i);
                    binding.textVoiceValue.setText(voices[i]);
                }));

        // 화면 모드. 바꾸면 Fragment 구성이 달라져서 MainActivity를 새로 띄운다
        binding.groupUiMode.check(settings.getUiMode() == SettingsStore.UiMode.DETAIL
                ? R.id.radio_ui_detail : R.id.radio_ui_glance);
        binding.groupUiMode.setOnCheckedChangeListener((group, checkedId) -> {
            SettingsStore.UiMode next = checkedId == R.id.radio_ui_detail
                    ? SettingsStore.UiMode.DETAIL : SettingsStore.UiMode.GLANCE;
            if (next == settings.getUiMode()) return;
            settings.setUiMode(next);
            com.example.six_sense.MainActivity.restart(requireContext(), R.id.nav_settings);
            requireActivity().finish();
        });

        // 실행 방식
        binding.groupMode.check(settings.isImmediateMode() ? R.id.radio_immediate : R.id.radio_confirm);
        binding.groupMode.setOnCheckedChangeListener((group, checkedId) ->
                settings.setImmediateMode(checkedId == R.id.radio_immediate));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ---------------------------------------------------------------------------------------------

    private void changeFont(SettingsStore.FontSize size) {
        if (size == settings.getFontSize()) return;
        settings.setFontSize(size);
        renderFontSegment(size);
        Toast.makeText(requireContext(), R.string.settings_font_applied, Toast.LENGTH_SHORT).show();
        // fontScale은 Activity 만들 때(attachBaseContext) 적용되므로 다시 만들어야 한다.
        // 탭 선택 상태와 Fragment는 시스템이 복원해주니 설정 탭 그대로 돌아온다.
        requireActivity().recreate();
    }

    private void renderFontSegment(SettingsStore.FontSize selected) {
        style(binding.fontNormal, selected == SettingsStore.FontSize.NORMAL);
        style(binding.fontLarge, selected == SettingsStore.FontSize.LARGE);
        style(binding.fontXlarge, selected == SettingsStore.FontSize.XLARGE);
    }

    private void style(TextView segment, boolean selected) {
        segment.setBackgroundResource(selected ? R.drawable.bg_segment_selected : 0);
        segment.setTextColor(requireContext().getColor(selected ? R.color.white : R.color.ink_soft));
        segment.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private interface OnPick {
        void onPick(int index);
    }

    private void pick(int titleRes, String[] options, int current, OnPick onPick) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setSingleChoiceItems(options, current, (dialog, which) -> {
                    onPick.onPick(which);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.correction_cancel, null)
                .show();
    }
}
