package com.example.six_sense.ui.listen;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 폰 마이크로 한 문장 받아오기. Android 기본 SpeechRecognizer 얇게 감쌈.
 *
 * 최종 구조에서는 STT(SenseVoice)가 Orange Pi에서 돌고 폰은 결과만 받지만,
 * 보드 지급 전엔 이걸로 데모한다. 그래서 사투리 인식률은 기대하지 말 것 — 구글 STT 기준이다.
 */
class SpeechInput {

    interface Callback {
        void onText(String text);
        void onUnavailable(String reason);
    }

    private final Context context;
    private SpeechRecognizer recognizer;

    SpeechInput(Context context) {
        this.context = context.getApplicationContext();
    }

    static boolean isAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    void start(Callback callback) {
        if (!isAvailable(context)) {
            callback.onUnavailable("recognizer not available");
            return;
        }
        stop();
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) {
                    callback.onText(list.get(0));
                } else {
                    callback.onUnavailable("empty result");
                }
            }

            @Override
            public void onError(int error) {
                // 타임아웃/무음도 여기로 온다. 화면 쪽에서 예시 문장으로 넘어가게 함
                callback.onUnavailable("error " + error);
            }

            // 나머지는 지금 안 씀
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(intent);
    }

    void stop() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }
}
