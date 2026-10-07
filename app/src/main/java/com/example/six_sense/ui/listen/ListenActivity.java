package com.example.six_sense.ui.listen;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.six_sense.R;
import com.example.six_sense.SixSenseApp;
import com.example.six_sense.ai.AiClient;
import com.example.six_sense.ai.ExecutionPolicy;
import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.data.HistoryRepository;
import com.example.six_sense.databinding.ActivityListenBinding;
import com.example.six_sense.model.Device;
import com.example.six_sense.model.DeviceAction;
import com.example.six_sense.model.HistoryEntry;
import com.example.six_sense.model.Interpretation;
import com.example.six_sense.ui.BaseActivity;
import com.example.six_sense.ui.DeviceIcons;
import com.example.six_sense.ui.result.ResultActivity;

/**
 * 화면 02. 듣는 중 → 해석 확인.
 *
 * 흐름:
 *   1. 음성(또는 예시 문장) 입력
 *   2. AiClient로 해석 (백그라운드)
 *   3. ExecutionPolicy로 분기
 *        IMMEDIATE : 바로 실행하고 결과 화면
 *        CONFIRM   : 3초 카운트다운 후 자동 실행, 그 사이 취소 가능
 *        ASK       : 되묻는 질문 보여주고(읽어주고) 다시 듣기
 *
 * 들어오는 Intent는 intentForVoice / intentForText 두 종류.
 */
public class ListenActivity extends BaseActivity {

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_TEXT = "text";
    private static final int MODE_VOICE = 0;
    private static final int MODE_TEXT = 1;

    /** 음성 인식이 안 될 때 데모용으로 흘려보낼 문장 */
    private static final String FALLBACK_UTTERANCE = "불 좀 꺼주이소";

    public static Intent intentForVoice(Context ctx) {
        return new Intent(ctx, ListenActivity.class).putExtra(EXTRA_MODE, MODE_VOICE);
    }

    public static Intent intentForText(Context ctx, String utterance) {
        return new Intent(ctx, ListenActivity.class)
                .putExtra(EXTRA_MODE, MODE_TEXT)
                .putExtra(EXTRA_TEXT, utterance);
    }

    private ActivityListenBinding binding;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private final DeviceRepository devices = DeviceRepository.getInstance();
    private final HistoryRepository history = HistoryRepository.getInstance();
    private AiClient ai;

    private SpeechInput speech;
    private TextToSpeech tts;
    private boolean ttsReady;

    private Interpretation current;
    private CountDownTimer countdown;
    private boolean executed;   // 두 번 실행되는 일 없게

    private final ActivityResultLauncher<String> micPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    listen();
                } else {
                    Toast.makeText(this, R.string.listen_mic_denied, Toast.LENGTH_SHORT).show();
                    interpret(FALLBACK_UTTERANCE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ai = SixSenseApp.get().aiClient();
        speech = new SpeechInput(this);
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS
                    && tts.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE;
        });

        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonCancel.setOnClickListener(v -> finish());
        binding.buttonRun.setOnClickListener(v -> execute(HistoryEntry.Mode.CONFIRMED));
        binding.buttonRetry.setOnClickListener(v -> startVoice());

        showWaiting();

        int mode = getIntent().getIntExtra(EXTRA_MODE, MODE_VOICE);
        if (mode == MODE_TEXT) {
            String text = getIntent().getStringExtra(EXTRA_TEXT);
            interpret(text == null ? FALLBACK_UTTERANCE : text);
        } else {
            startVoice();
        }
    }

    @Override
    protected void onDestroy() {
        cancelCountdown();
        speech.stop();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------------------
    // 1. 입력
    // ---------------------------------------------------------------------------------------------

    private void startVoice() {
        cancelCountdown();
        showWaiting();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            listen();
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void listen() {
        if (!SpeechInput.isAvailable(this)) {
            Toast.makeText(this, R.string.listen_no_speech, Toast.LENGTH_SHORT).show();
            interpret(FALLBACK_UTTERANCE);
            return;
        }
        speech.start(new SpeechInput.Callback() {
            @Override
            public void onText(String text) {
                main.post(() -> interpret(text));
            }

            @Override
            public void onUnavailable(String reason) {
                // 무음 타임아웃 등. 데모가 끊기지 않게 예시 문장으로 넘어간다
                main.post(() -> {
                    Toast.makeText(ListenActivity.this, R.string.listen_no_speech, Toast.LENGTH_SHORT).show();
                    interpret(FALLBACK_UTTERANCE);
                });
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // 2. 해석
    // ---------------------------------------------------------------------------------------------

    private void interpret(String utterance) {
        binding.textHeard.setText("“" + utterance + "”");
        binding.textListening.setText(R.string.listen_thinking);

        worker.execute(() -> {
            try {
                Interpretation result = ai.interpret(utterance);
                main.post(() -> onInterpreted(result));
            } catch (IOException e) {
                main.post(() -> {
                    Toast.makeText(this, getString(R.string.listen_ai_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void onInterpreted(Interpretation it) {
        if (isFinishing()) return;
        current = it;
        executed = false;
        render(it);

        boolean immediateMode = SixSenseApp.get().settings().isImmediateMode();
        switch (ExecutionPolicy.decide(it, immediateMode)) {
            case IMMEDIATE:
                execute(HistoryEntry.Mode.IMMEDIATE);
                break;
            case CONFIRM:
                startCountdown();
                break;
            case ASK:
                showAskBack(it);
                break;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 3. 화면 그리기
    // ---------------------------------------------------------------------------------------------

    /** 아직 들은 말이 없을 때. 카드/카운트다운 숨기고 기다리는 문구만 */
    private void showWaiting() {
        binding.textListening.setText(R.string.listen_listening);
        binding.textHeard.setText(R.string.listen_waiting);
        binding.textUnderstoodLabel.setVisibility(View.INVISIBLE);
        binding.cardInterpretation.setVisibility(View.INVISIBLE);
        binding.textAskBack.setVisibility(View.GONE);
        binding.groupCountdown.setVisibility(View.INVISIBLE);
        binding.buttonRun.setEnabled(false);
        binding.buttonRun.setAlpha(0.5f);
    }

    private void render(Interpretation it) {
        binding.textListening.setText(R.string.listen_understood);
        binding.textUnderstoodLabel.setVisibility(View.VISIBLE);
        binding.cardInterpretation.setVisibility(View.VISIBLE);
        binding.textAskBack.setVisibility(View.GONE);      // 직전에 되물었던 흔적 지움
        binding.buttonRetry.setVisibility(View.VISIBLE);
        binding.buttonRun.setEnabled(true);
        binding.buttonRun.setAlpha(1f);

        binding.textSummary.setText(it.getSummary());

        if (it.getDialectNote() != null) {
            binding.textDialect.setText(it.getDialectNote());
            binding.textDialect.setVisibility(View.VISIBLE);
        } else {
            binding.textDialect.setVisibility(View.GONE);
        }

        // 아이콘은 첫 번째 조치의 기기로
        List<DeviceAction> actions = it.getActions();
        if (!actions.isEmpty()) {
            Device d = devices.find(actions.get(0).getDeviceId());
            binding.imageDevice.setImageResource(DeviceIcons.of(d == null ? null : d.getType()));
        } else {
            binding.imageDevice.setImageResource(R.drawable.ic_mic);
        }

        // 태그 칩: 말투 / 확신도 / 상황
        binding.groupTags.removeAllViews();
        addTag("말투 · " + it.getTone());
        addTag("확신도 " + it.getConfidencePercent() + "%");
        if (it.getContextNote() != null) addTag(it.getContextNote());
        if (it.isMonologue()) addTag(getString(R.string.listen_monologue_tag));
    }

    private void addTag(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setClickable(false);
        chip.setCheckable(false);
        // 시안처럼 반투명 흰 유리 칩 + 흰 테두리
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0x99FFFFFF));
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(0xE6FFFFFF));
        chip.setChipStrokeWidth(getResources().getDisplayMetrics().density);
        chip.setTextColor(getColor(R.color.ink_soft));
        chip.setTextSize(14f);
        chip.setEnsureMinTouchTargetSize(false);
        binding.groupTags.addView(chip);
    }

    /** 확신도가 낮을 때. 실행 버튼 대신 다시 듣기를 크게 */
    private void showAskBack(Interpretation it) {
        cancelCountdown();
        binding.groupCountdown.setVisibility(View.INVISIBLE);
        binding.textAskBack.setText(it.getAskBack());
        binding.textAskBack.setVisibility(View.VISIBLE);
        binding.textListening.setText(R.string.listen_ask_again_title);

        binding.buttonRun.setText(R.string.listen_speak_again);
        binding.buttonRun.setOnClickListener(v -> startVoice());
        binding.buttonRetry.setVisibility(View.GONE);

        speak(it.getAskBack());
        // 되물은 것도 기록에 남긴다 (분기 기준 조정에 쓰려고)
        history.record(it, HistoryEntry.Mode.ASKED, null);
    }

    // ---------------------------------------------------------------------------------------------
    // 4. 카운트다운 → 실행
    // ---------------------------------------------------------------------------------------------

    private void startCountdown() {
        cancelCountdown();
        binding.groupCountdown.setVisibility(View.VISIBLE);
        binding.buttonRun.setText(R.string.listen_run_now);
        binding.buttonRun.setOnClickListener(v -> execute(HistoryEntry.Mode.CONFIRMED));
        binding.buttonRetry.setVisibility(View.VISIBLE);

        final long total = ExecutionPolicy.CONFIRM_COUNTDOWN_MS;
        int totalSec = (int) (total / 1000);
        binding.textCountdown.setText(getString(R.string.listen_countdown_format, totalSec));

        countdown = new CountDownTimer(total, 50) {
            @Override
            public void onTick(long remaining) {
                // 올림 처리해서 3, 2, 1로 보이게
                int sec = (int) Math.ceil(remaining / 1000.0);
                binding.textCountdownRemaining.setText(getString(R.string.listen_countdown_remaining, sec));
                binding.progressCountdown.setProgress((int) (1000 - remaining * 1000 / total));
            }

            @Override
            public void onFinish() {
                binding.progressCountdown.setProgress(1000);
                execute(HistoryEntry.Mode.CONFIRMED);
            }
        }.start();
    }

    private void cancelCountdown() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
    }

    private void execute(HistoryEntry.Mode mode) {
        if (current == null || executed) return;
        executed = true;
        cancelCountdown();

        List<Device> before = devices.apply(current.getActions());
        HistoryEntry entry = history.record(current, mode, before);

        startActivity(ResultActivity.intent(this, current, entry.getId()));
        finish();
    }

    private void speak(String text) {
        if (ttsReady && text != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ask");
        }
    }
}
