package com.example.six_sense;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.six_sense.R;
import com.example.six_sense.SixSenseApp;
import com.example.six_sense.ui.BaseActivity;
import com.example.six_sense.databinding.ActivityMainBinding;
import com.example.six_sense.ui.devices.DevicesFragment;
import com.example.six_sense.ui.history.HistoryFragment;
import com.example.six_sense.ui.home.HomeFragment;
import com.example.six_sense.ui.home.GlanceHomeFragment;
import com.example.six_sense.ui.devices.GlanceDevicesFragment;
import com.example.six_sense.data.SettingsStore;
import com.example.six_sense.ui.settings.SettingsFragment;

/**
 * 하단 탭(홈 / 기기 / 기록 / 설정)을 가진 메인 화면.
 * 탭마다 Fragment를 하나씩 두고, 전환할 때 새로 만들지 않고 show/hide만 한다.
 * (기기 토글 상태나 스크롤 위치가 탭 왔다갔다 해도 유지되게)
 */
public class MainActivity extends BaseActivity {

    /** 결과 화면에서 돌아올 때 어느 탭을 열지 넘기는 키 */
    public static final String EXTRA_TAB = "tab";

    private static final String TAG_HOME = "home";
    private static final String TAG_DEVICES = "devices";
    private static final String TAG_HISTORY = "history";
    private static final String TAG_SETTINGS = "settings";

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            int tab = getIntent().getIntExtra(EXTRA_TAB, R.id.nav_home);
            binding.bottomNav.setSelectedItemId(tab);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 결과 화면이 FLAG_ACTIVITY_CLEAR_TOP으로 돌아오면 여기로 온다
        if (intent.hasExtra(EXTRA_TAB)) {
            binding.bottomNav.setSelectedItemId(intent.getIntExtra(EXTRA_TAB, R.id.nav_home));
        }
    }

    /** Fragment에서 탭을 바꿀 때 (홈의 "기기를 추가하기" 등) */
    public void selectTab(@IdRes int itemId) {
        binding.bottomNav.setSelectedItemId(itemId);
    }

    private void showTab(@IdRes int itemId) {
        String tag;
        if (itemId == R.id.nav_devices) {
            tag = TAG_DEVICES;
        } else if (itemId == R.id.nav_history) {
            tag = TAG_HISTORY;
        } else if (itemId == R.id.nav_settings) {
            tag = TAG_SETTINGS;
        } else {
            tag = TAG_HOME;
        }

        FragmentManager fm = getSupportFragmentManager();
        Fragment target = fm.findFragmentByTag(tag);
        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();

        // 보이는 것 전부 숨기고
        for (Fragment f : fm.getFragments()) {
            if (f != target && !f.isHidden()) {
                tx.hide(f);
            }
        }
        // 대상은 없으면 만들고, 있으면 보여준다
        if (target == null) {
            tx.add(R.id.fragment_container, createFragment(tag), tag);
        } else {
            tx.show(target);
        }
        tx.setReorderingAllowed(true);
        tx.commit();
    }

    /**
     * 화면 모드가 바뀌면 Fragment 종류 자체가 달라져서 recreate()로는 안 된다
     * (저장된 Fragment를 그대로 복원해버림). 태스크를 비우고 새로 띄운다.
     */
    public static void restart(android.content.Context ctx, int tab) {
        Intent i = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_TAB, tab);
        ctx.startActivity(i);
    }

    private Fragment createFragment(String tag) {
        boolean glance = SixSenseApp.get().settings().getUiMode() == SettingsStore.UiMode.GLANCE;
        switch (tag) {
            case TAG_DEVICES:
                return glance ? new GlanceDevicesFragment() : new DevicesFragment();
            case TAG_HISTORY:
                return new HistoryFragment();
            case TAG_SETTINGS:
                return new SettingsFragment();
            default:
                return glance ? new GlanceHomeFragment() : new HomeFragment();
        }
    }
}
