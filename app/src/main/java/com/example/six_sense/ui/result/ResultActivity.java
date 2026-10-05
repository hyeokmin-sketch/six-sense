package com.example.six_sense.ui.result;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import java.util.Locale;

import com.example.six_sense.R;
import com.example.six_sense.ai.ExecutionPolicy;
import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.data.HistoryRepository;
import com.example.six_sense.databinding.ActivityResultBinding;
import com.example.six_sense.databinding.ItemActionBinding;
import com.example.six_sense.model.Device;
import com.example.six_sense.model.DeviceAction;
import com.example.six_sense.model.HistoryEntry;
import com.example.six_sense.model.Interpretation;
import com.example.six_sense.ui.BaseActivity;
import com.example.six_sense.ui.DeviceIcons;
import com.example.six_sense.MainActivity;

/**
 * 화면 03. 실행 결과 · 상황 인지.
 * 조치는 이미 ListenActivity에서 적용된 상태로 들어온다. 여기선 보여주고, 읽어주고, 되돌릴 수 있게만.
 * 5초 지나면 홈으로 자동 복귀 (버튼을 누르면 즉시).
 */
public class ResultActivity extends BaseActivity {

    private static final String EXTRA_INTERPRETATION = "interpretation";
    private static final String EXTRA_HISTORY_ID = "history_id";

    public static Intent intent(Context ctx, Interpretation it, long historyId) {
        return new Intent(ctx, ResultActivity.class)
                .putExtra(EXTRA_INTERPRETATION, it)
                .putExtra(EXTRA_HISTORY_ID, historyId);
    }

    private ActivityResultBinding binding;
    private final DeviceRepository devices = DeviceRepository.getInstance();
    private final HistoryRepository history = HistoryRepository.getInstance();

    private Interpretation interpretation;
    private long historyId;
    private CountDownTimer autoHome;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        interpretation = (Interpretation) getIntent().getSerializableExtra(EXTRA_INTERPRETATION);
        historyId = getIntent().getLongExtra(EXTRA_HISTORY_ID, -1);
        if (interpretation == null) {
            finish();
            return;
        }

        render(interpretation);

        binding.buttonBack.setOnClickListener(v -> goHome());
        binding.buttonGood.setOnClickListener(v -> {
            Toast.makeText(this, R.string.result_thanks, Toast.LENGTH_SHORT).show();
            goHome();
        });
        binding.buttonUndo.setOnClickListener(v -> undo());

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS
                    && tts.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE) {
                tts.speak(interpretation.getResponse(), TextToSpeech.QUEUE_FLUSH, null, "response");
            }
        });

        startAutoHome();
    }

    @Override
    protected void onDestroy() {
        if (autoHome != null) autoHome.cancel();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------------------

    private void render(Interpretation it) {
        binding.textHeard.setText("“" + it.getHeard() + "”");

        StringBuilder meta = new StringBuilder();
        if (it.isMonologue()) meta.append("혼잣말로 들렸어요 · ");
        meta.append("말투: ").append(it.getTone())
                .append(" · 확신도 ").append(it.getConfidencePercent()).append('%');
        binding.textMeta.setText(meta);

        // 추론 카드: 혼잣말이거나 온도 변화가 있을 때만
        boolean showReasoning = it.isMonologue() || it.hasTemperatureChange();
        binding.cardReasoning.setVisibility(showReasoning ? View.VISIBLE : View.GONE);
        if (showReasoning) {
            binding.textReasoning.setText(it.getReasoning() != null ? it.getReasoning() : it.getSummary());
            if (it.hasTemperatureChange()) {
                binding.groupTemperature.setVisibility(View.VISIBLE);
                binding.textTempNow.setText(getString(R.string.unit_celsius, it.getCurrentTemp()));
                binding.textTempTarget.setText(getString(R.string.unit_celsius, it.getTargetTemp()));
            } else {
                binding.groupTemperature.setVisibility(View.GONE);
            }
        }

        // 조치 목록. 개수가 적어서 RecyclerView 안 쓰고 그냥 붙인다
        binding.listActions.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (DeviceAction a : it.getActions()) {
            ItemActionBinding row = ItemActionBinding.inflate(inflater, binding.listActions, false);
            Device d = devices.find(a.getDeviceId());
            row.imageIcon.setImageResource(DeviceIcons.of(d == null ? null : d.getType()));
            row.textTitle.setText(a.getTitle());
            if (a.getDetail() != null) {
                row.textDetail.setText(a.getDetail());
            } else {
                row.textDetail.setVisibility(View.GONE);
            }
            binding.listActions.addView(row.getRoot());
        }

        binding.textResponse.setText("“" + it.getResponse() + "”");
    }

    private void startAutoHome() {
        final long total = ExecutionPolicy.RESULT_AUTO_HOME_MS;
        autoHome = new CountDownTimer(total, 250) {
            @Override
            public void onTick(long remaining) {
                int sec = (int) Math.ceil(remaining / 1000.0);
                binding.textAutoHome.setText(getString(R.string.result_auto_home, sec));
            }

            @Override
            public void onFinish() {
                goHome();
            }
        }.start();
    }

    /** 조치 전 상태로 되돌리고 기록에도 남긴다 */
    private void undo() {
        HistoryEntry entry = history.find(historyId);
        if (entry != null) {
            devices.restore(entry.getSnapshotBefore());
            history.correct(historyId, "되돌림");
        }
        Toast.makeText(this, R.string.result_undone, Toast.LENGTH_SHORT).show();
        goHome();
    }

    private void goHome() {
        if (autoHome != null) {
            autoHome.cancel();
            autoHome = null;
        }
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_TAB, R.id.nav_home);
        startActivity(i);
        finish();
    }
}
