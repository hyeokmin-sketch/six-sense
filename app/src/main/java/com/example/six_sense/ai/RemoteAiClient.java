package com.example.six_sense.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.example.six_sense.data.DeviceRepository;
import com.example.six_sense.model.DeviceAction;
import com.example.six_sense.model.Interpretation;

/**
 * Orange Pi 위에서 도는 LLM 서버(FastAPI, OpenAI 호환 /v1/chat/completions) 호출.
 *
 * 서버 쪽 voice_agent.py가 STT까지 처리하는 구조라, 폰에서는 텍스트만 보내고 JSON으로 해석 결과를 받는다.
 * 응답 JSON 형식은 아래 SYSTEM_PROMPT에 적어둔 대로 서버와 맞춰야 함.
 *
 * TODO(형균): 서버 주소·포트, 모델 이름 확정되면 DEFAULT_BASE_URL / MODEL 바꿀 것.
 * TODO: 보드 지급 전까지는 MockAiClient 사용. SixSenseApp.aiClient() 참고.
 */
public class RemoteAiClient implements AiClient {

    private static final String DEFAULT_BASE_URL = "http://192.168.0.10:8000";
    private static final String MODEL = "sixsense-llama-1b";
    private static final int TIMEOUT_MS = 8_000;

    // 서버가 이 형식 그대로 돌려주도록 프롬프트에 박아둔다. 키 이름 바꾸면 parse()도 같이.
    private static final String SYSTEM_PROMPT =
            "너는 스마트홈 음성 비서다. 사용자의 발화(사투리·혼잣말 포함)를 해석해서 아래 JSON만 출력한다.\n"
            + "{\"summary\": \"이해한 뜻 한 줄\", \"dialect\": \"사투리 해석 또는 null\", \"tone\": \"말투\", "
            + "\"confidence\": 0.0~1.0, \"monologue\": true/false, \"reasoning\": \"혼잣말일 때 추론 한 줄 또는 null\", "
            + "\"current_temp\": 숫자 또는 null, \"target_temp\": 숫자 또는 null, "
            + "\"actions\": [{\"device\": \"light_living|ac|blind|oven|bath|vacuum|coffee|fridge\", "
            + "\"kind\": \"on|off|set\", \"value\": 숫자, \"title\": \"조치 한 줄\", \"detail\": \"설명 또는 null\"}], "
            + "\"response\": \"사용자에게 읽어줄 응답\", \"ask_back\": \"확신이 낮을 때 되물을 질문 또는 null\"}";

    private final String baseUrl;
    private final DeviceRepository devices;

    public RemoteAiClient(DeviceRepository devices) {
        this(DEFAULT_BASE_URL, devices);
    }

    public RemoteAiClient(String baseUrl, DeviceRepository devices) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.devices = devices;
    }

    @Override
    public Interpretation interpret(String utterance) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", MODEL);
            body.put("temperature", 0.2);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
            // 현재 집 상태를 같이 보내야 "28도 → 24도" 같은 판단을 할 수 있다
            messages.put(new JSONObject().put("role", "user")
                    .put("content", "실내 온도: " + devices.getRoomTemperature() + "도\n발화: " + utterance));
            body.put("messages", messages);
        } catch (JSONException e) {
            throw new IOException("요청 JSON 생성 실패", e);
        }

        String raw = post(baseUrl + "/v1/chat/completions", body.toString());

        try {
            JSONObject root = new JSONObject(raw);
            String content = root.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
            return parse(utterance, new JSONObject(stripCodeFence(content)));
        } catch (JSONException e) {
            throw new IOException("응답 파싱 실패: " + raw, e);
        }
    }

    private Interpretation parse(String heard, JSONObject j) throws JSONException {
        Interpretation.Builder b = Interpretation.builder(heard)
                .summary(j.optString("summary", ""))
                .tone(j.optString("tone", "보통"))
                .confidence((float) j.optDouble("confidence", 0.5))
                .response(j.optString("response", ""));

        if (!j.isNull("dialect")) b.dialect(j.getString("dialect"));
        if (!j.isNull("ask_back")) b.askBack(j.getString("ask_back"));
        if (j.optBoolean("monologue", false)) b.monologue(j.optString("reasoning", null));
        if (!j.isNull("current_temp") && !j.isNull("target_temp")) {
            b.temperature(j.getInt("current_temp"), j.getInt("target_temp"));
        }

        JSONArray actions = j.optJSONArray("actions");
        if (actions != null) {
            for (int i = 0; i < actions.length(); i++) {
                JSONObject a = actions.getJSONObject(i);
                String deviceId = a.getString("device");
                String title = a.optString("title", "");
                String detail = a.isNull("detail") ? null : a.getString("detail");
                switch (a.optString("kind", "on")) {
                    case "off":
                        b.action(DeviceAction.off(deviceId, title, detail));
                        break;
                    case "set":
                        b.action(DeviceAction.set(deviceId, a.optInt("value", 0), title, detail));
                        break;
                    default:
                        b.action(DeviceAction.on(deviceId, title, detail));
                }
            }
        }
        return b.build();
    }

    private static String post(String url, String json) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code / 100 != 2) {
                throw new IOException("서버 응답 " + code);
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
    }

    /** 모델이 ```json ... ``` 으로 감싸서 줄 때가 있어서 벗겨낸다 */
    private static String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int first = t.indexOf('\n');
            int last = t.lastIndexOf("```");
            if (first > 0 && last > first) {
                t = t.substring(first + 1, last).trim();
            }
        }
        return t;
    }
}
