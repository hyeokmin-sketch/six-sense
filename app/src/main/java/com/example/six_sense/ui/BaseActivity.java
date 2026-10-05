package com.example.six_sense.ui;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatActivity;

import com.example.six_sense.data.SettingsStore;

/**
 * 모든 Activity의 부모. 설정의 "글자 크기"를 fontScale로 적용한다.
 *
 * sp 단위를 전부 쓰고 있어서 fontScale만 바꿔주면 레이아웃 전체가 같이 커진다.
 * 설정에서 값을 바꾸면 그 Activity는 recreate() 해야 반영됨 (SettingsFragment 참고).
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        SettingsStore settings = new SettingsStore(newBase);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.fontScale = settings.getFontSize().scale;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }
}
