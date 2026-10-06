package com.example.six_sense.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.six_sense.R;

import com.example.six_sense.data.SettingsStore;

/**
 * 모든 Activity의 부모. 설정의 "글자 크기"를 fontScale로 적용한다.
 *
 * sp 단위를 전부 쓰고 있어서 fontScale만 바꿔주면 레이아웃 전체가 같이 커진다.
 * 설정에서 값을 바꾸면 그 Activity는 recreate() 해야 반영됨 (SettingsFragment 참고).
 *
 * 화면 테마도 여기서 정한다. 지금은 두 모드(한눈에 / 자세히) 모두 글래스 테마(Theme.SixSense.Glass).
 * 모드별로 다르게 하고 싶으면 아래 조건만 되살리면 된다. 기존 단색 테마는 Theme.SixSense에 그대로 있다.
 * setTheme은 super.onCreate() 전에 불러야 레이아웃에 적용된다.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        SettingsStore settings = new SettingsStore(newBase);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.fontScale = settings.getFontSize().scale;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 모드별로 나눌 때: if (new SettingsStore(this).getUiMode() == SettingsStore.UiMode.GLANCE)
        setTheme(R.style.Theme_SixSense_Glass);
        super.onCreate(savedInstanceState);
    }
}
