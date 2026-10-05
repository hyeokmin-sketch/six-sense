package com.example.six_sense;

import android.app.Application;

import com.example.six_sense.ai.AiClient;
import com.example.six_sense.ai.MockAiClient;
import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.data.HistoryRepository;
import com.example.six_sense.data.SettingsStore;

/**
 * 앱 전역 객체 보관. DI 라이브러리 쓸 규모는 아니라서 여기서 직접 만든다.
 */
public class SixSenseApp extends Application {

    /**
     * true면 서버 없이 MockAiClient로 돈다.
     * 보드 서버 올라오면 false로 바꾸고 RemoteAiClient 주소 확인할 것.
     */
    private static final boolean USE_MOCK_AI = true;

    private static SixSenseApp instance;

    private SettingsStore settings;
    private AiClient aiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        settings = new SettingsStore(this);

        DeviceRepository devices = DeviceRepository.getInstance();
        HistoryRepository.getInstance();   // 데모 기록 미리 채워두려고 한 번 건드림

        if (USE_MOCK_AI) {
            aiClient = new MockAiClient(devices);
        } else {
            aiClient = new com.example.six_sense.ai.RemoteAiClient(devices);
        }
    }

    public static SixSenseApp get() {
        return instance;
    }

    public SettingsStore settings() {
        return settings;
    }

    public AiClient aiClient() {
        return aiClient;
    }
}
