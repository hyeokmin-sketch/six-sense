// SIX-SENSE 가상 원룸 설정
// llmEndpoint가 null이면 브라우저 안의 규칙 엔진으로 모의 해석한다.
// 보드가 준비되면 마음AI FastAPI 서버(OpenAI 호환, 내부망) 주소를 넣는다.
window.SixSense = window.SixSense || {};
window.SixSense.config = {
  llmEndpoint: "http://192.168.2.3:8000/v1/chat/completions",   // 모델 서버를 켠 PC 주소 (null이면 규칙 엔진)
  llmModel: "six-sense",
  systemPrompt: "당신은 스마트홈 제어 비서입니다. 사용자의 사투리와 모호한 표현을 이해해 기능 호출 형식으로만 답하세요.",   // 학습 때 쓴 프롬프트 그대로
  voice: true,                // 보드 마이크 음성 명령을 중계 서버에서 받아 화면에 보여 준다 (false면 끔)
  timeoutMs: 5000,            // 넘기면 1회 재시도 후 안내 (E03)
};

// 보드 없이 화면만 볼 때: 주소 끝에 ?demo=1 을 붙이면(또는 인터넷에 올린 페이지에서는 자동으로) 규칙 엔진으로 동작한다.
if (/[?&]demo=1/.test(location.search) || /^https?:$/.test(location.protocol) && location.hostname !== "192.168.2.3" && !/^(192\.168\.|10\.|localhost|127\.)/.test(location.hostname)) {
  window.SixSense.config.llmEndpoint = null;
}

