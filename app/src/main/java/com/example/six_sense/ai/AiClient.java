package com.example.six_sense.ai;

import java.io.IOException;

import com.example.six_sense.model.Interpretation;

/**
 * 발화 텍스트 → 해석 결과.
 *
 * 구현체는 두 개:
 *   MockAiClient   - 서버 없이 데모용. 시안에 쓴 예문 몇 개를 그대로 돌려준다.
 *   RemoteAiClient - Orange Pi의 FastAPI(OpenAI 호환) 서버 호출.
 *
 * 둘 다 블로킹 호출이므로 UI 스레드에서 직접 부르면 안 된다. (ListenActivity에서 Executor로 감쌈)
 */
public interface AiClient {

    Interpretation interpret(String utterance) throws IOException;
}
