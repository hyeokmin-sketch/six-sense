package com.example.six_sense.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 설정 화면 값 저장. SharedPreferences 얇게 감싼 것.
 */
public class SettingsStore {

    private static final String PREF = "sixsense_settings";

    private static final String KEY_FONT = "font_size";
    private static final String KEY_DIALECT = "dialect_index";
    private static final String KEY_VOICE = "voice_index";
    private static final String KEY_IMMEDIATE = "immediate_mode";
    private static final String KEY_UI_MODE = "ui_mode";
    private static final String KEY_FAVORITES = "favorite_devices";

    /** 홈에 보여줄 즐겨찾기 기기 최대 개수 */
    public static final int MAX_FAVORITES = 2;

    /**
     * 화면 모드. "누구용"이 아니라 "어떻게 보이는지"로 이름을 붙였다.
     *   GLANCE : 한눈에 모드. 큰 글씨, 꼭 필요한 것만. 세부 조절은 기기 상세 화면에서
     *   DETAIL : 자세히 모드. 한 화면에 상태와 수치를 모두
     */
    public enum UiMode {
        GLANCE,
        DETAIL
    }

    /** 글자 크기 3단계. 기본값은 고령 사용자 기준으로 '크게' */
    public enum FontSize {
        NORMAL(1.0f),
        LARGE(1.15f),
        XLARGE(1.3f);

        public final float scale;

        FontSize(float scale) {
            this.scale = scale;
        }

        static FontSize fromOrdinal(int i) {
            FontSize[] v = values();
            return (i >= 0 && i < v.length) ? v[i] : LARGE;
        }
    }

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public FontSize getFontSize() {
        return FontSize.fromOrdinal(prefs.getInt(KEY_FONT, FontSize.LARGE.ordinal()));
    }

    public void setFontSize(FontSize size) {
        prefs.edit().putInt(KEY_FONT, size.ordinal()).apply();
    }

    /** R.array.dialect_regions 인덱스. 기본 0 = 경상도 */
    public int getDialectIndex() {
        return prefs.getInt(KEY_DIALECT, 0);
    }

    public void setDialectIndex(int index) {
        prefs.edit().putInt(KEY_DIALECT, index).apply();
    }

    /** R.array.voice_options 인덱스 */
    public int getVoiceIndex() {
        return prefs.getInt(KEY_VOICE, 0);
    }

    public void setVoiceIndex(int index) {
        prefs.edit().putInt(KEY_VOICE, index).apply();
    }

    /** true면 확신도 높을 때 확인 없이 바로 실행. 기본은 확인하고 실행 */
    public boolean isImmediateMode() {
        return prefs.getBoolean(KEY_IMMEDIATE, false);
    }

    public void setImmediateMode(boolean immediate) {
        prefs.edit().putBoolean(KEY_IMMEDIATE, immediate).apply();
    }

    /** 기본은 한눈에 모드 */
    public UiMode getUiMode() {
        String v = prefs.getString(KEY_UI_MODE, UiMode.GLANCE.name());
        try {
            return UiMode.valueOf(v);
        } catch (IllegalArgumentException e) {
            return UiMode.GLANCE;
        }
    }

    public void setUiMode(UiMode mode) {
        prefs.edit().putString(KEY_UI_MODE, mode.name()).apply();
    }

    // ---- 즐겨찾기 (홈 "지금 집 상태"에 올라가는 기기 2개)

    /** 순서 유지가 필요해서 StringSet 대신 콤마로 저장 */
    public java.util.List<String> getFavorites() {
        String raw = prefs.getString(KEY_FAVORITES, "");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : raw.split(",")) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    public boolean isFavorite(String deviceId) {
        return getFavorites().contains(deviceId);
    }

    /**
     * 즐겨찾기 켜고 끄기.
     * @return 바꾼 뒤 즐겨찾기 상태. 꽉 차서 못 넣었으면 false (호출한 쪽에서 안내)
     */
    public boolean toggleFavorite(String deviceId) {
        java.util.List<String> list = getFavorites();
        boolean nowFavorite;
        if (list.contains(deviceId)) {
            list.remove(deviceId);
            nowFavorite = false;
        } else {
            if (list.size() >= MAX_FAVORITES) return false;
            list.add(deviceId);
            nowFavorite = true;
        }
        prefs.edit().putString(KEY_FAVORITES, String.join(",", list)).apply();
        return nowFavorite;
    }
}
