// IF-2: 발화 → 호출 문자열.
// llmEndpoint가 있으면 LLM 서버에 묻고, 없으면 아래 규칙 엔진으로 흉내 낸다.
// 규칙 엔진은 시연용이다. 학습한 LLM의 성능을 대신하지 않는다.
(function (S) {
  "use strict";

  const LIGHT = 1, AC = 2, BLIND = 3;
  const call = (t, object, type) => ({ tag: "six_" + t, object, type });
  const ASK = { tag: "six_0", object: -1 };
  const NONE = { tag: "six_2" };

  const DEVICES = [
    [LIGHT, /조명|전등|형광등|스탠드|불빛|(?:^|[^가-힣])불(?![편안만행가능러])/],
    [AC, /에어컨|에어콘|냉방|온도|바람/],
    [BLIND, /블라인드|블라인더|커튼|버티칼/],
  ];
  const UNSUPPORTED = /티비|테레비|텔레비|tv|선풍기|보일러|가스|도어락|현관문|세탁기|냉장고|라디오|음악|노래|전기장판|히터|공기청정기|밥솥|청소기/i;

  // 기본 우선순위는 위에서부터
  const INTENTS = [
    ["half", /반쯤|반만|절반|중간쯤/],
    ["stop", /멈춰|멈추|멈차|스톱|고만|그만|세아라|세워/],
    ["off", /꺼|끄/],
    ["on", /켜|키(?:라|아|뿌|도|주|이|소|고|자|래|면)|써라|써\s?도|써\s?주|틀(?:어|라|아|고|자|래)|돌려/],
    ["up", /올려|올리|올라|높여|높이|밝게|밝히|환하게|훤하게|따뜻|따시|뜨시|세게|쎄게|씨게|강하게|키워|걷어|열어|열아/],
    ["down", /내려|내리|낮춰|낮차|낮추|낮게|은은|어둡게|흐리게|시원하게|시원케|약하게|살살|줄여|줄이|닫아|가려|가리|쳐라|치라/],
  ];
  const INTENT_ORDER = INTENTS.map(([name]) => name);
  const WIND_WORDS = /바람|세게|쎄게|씨게|강하게|약하게|살살/;

  // 기기 이름 없이 상황만 말할 때. fallback은 명령이 하나도 없을 때만 쓴다.
  const CUES = [
    { device: AC, re: /덥|더워|더버|찜통|후덥|땀/,
      fallback: s => (s.ac.on ? call(1, AC, 2) : call(0, AC, 1)) },
    { device: AC, re: /춥|추워|추버|얼어/,
      fallback: s => (s.ac.on ? call(1, AC, 1) : null) },
    { device: BLIND, re: /눈부시|눈이\s?부시|햇빛|햇볕|땡볕|해가/,
      fallback: () => call(0, BLIND, 2) },
    { device: LIGHT, re: /어둡(?:다|네|데|노|구|어)|깜깜|캄캄|컴컴/,
      fallback: s => (s.light.on ? call(1, LIGHT, 1) : call(0, LIGHT, 1)) },
    { device: LIGHT, re: /눈\s?아프|눈이\s?아파|눈\s?시리/,
      fallback: s => (s.light.on ? call(1, LIGHT, 2) : null) },
  ];

  const ALL = /싹\s?다|전부|모두|몽땅|다\s?(?:꺼|끄|켜|키)/;
  const NARRATIVE = /옆집|윗집|아랫집|친구|며느리|뉴스|(?:더라|드라|카더라|했다|샀다|봤다|갔다|왔다|었다|았다|였다|났다)$/;

  // 되묻기 뒤에 이어서 들을 내용: { intent } 또는 { devices }
  let pending = null;

  function splitClauses(text) {
    return text
      .split(/[,.!?~\n]+|\s+(?:그리고|그라고|그다음에?)\s+/)
      .flatMap(part => part.split(/(?<=[가-힣])고\s+/))
      .map(s => s.trim())
      .filter(Boolean);
  }

  function pickIntent(intents, device, clause) {
    const order = device === AC && WIND_WORDS.test(clause) ? ["off", "up", "down", "on"] : INTENT_ORDER;
    return order.find(name => intents.includes(name)) || null;
  }

  function mapIntent(device, intent, clause) {
    if (device === LIGHT) {
      const t = { on: [0, 1], off: [0, 2], up: [1, 1], down: [1, 2] }[intent];
      return t ? call(t[0], LIGHT, t[1]) : null;
    }
    if (device === AC) {
      const wind = WIND_WORDS.test(clause);
      if (intent === "on") return call(0, AC, 1);
      if (intent === "off") return call(0, AC, 2);
      if (intent === "up") return wind ? call(1, AC, 3) : call(1, AC, 1);
      if (intent === "down") return wind ? call(1, AC, 4) : call(1, AC, 2);
      return null;
    }
    const t = { up: 1, down: 2, stop: 3, half: 4 }[intent];
    return t ? call(0, BLIND, t) : null;
  }

  function interpretByRules(rawText, state) {
    const text = rawText.normalize("NFC").trim();
    if (NARRATIVE.test(text.replace(/[.!?~\s]+$/, ""))) { pending = null; return [NONE]; }

    const calls = [];
    const seen = [];
    let context = pending && pending.devices ? pending.devices[0] : null;
    let firstCue = null, sawIntent = false, sawUnsupported = false, ask = null;

    for (const clause of splitClauses(text)) {
      const devices = DEVICES.filter(([, re]) => re.test(clause)).map(([d]) => d);
      const cue = CUES.find(c => c.re.test(clause)) || null;
      const intents = INTENTS.filter(([, re]) => re.test(clause)).map(([name]) => name);
      devices.forEach(d => { if (!seen.includes(d)) seen.push(d); });
      if (cue && !firstCue) firstCue = cue;

      if (UNSUPPORTED.test(clause) && !devices.length) { sawUnsupported = true; context = null; continue; }
      if (!intents.length) {
        if (devices.length) context = devices[devices.length - 1];
        else if (cue) context = cue.device;
        continue;
      }
      sawIntent = true;

      let targets = devices;
      if (!targets.length && ALL.test(clause) && (intents.includes("off") || intents.includes("on"))) targets = [LIGHT, AC];
      if (!targets.length && cue) targets = [cue.device];
      if (!targets.length && context) targets = [context];
      if (!targets.length) { ask = { intent: intents[0] }; continue; }

      for (const d of targets) {
        const c = mapIntent(d, pickIntent(intents, d, clause), clause);
        if (c) calls.push(c); else ask = { devices: [d] };
      }
      context = targets[targets.length - 1];
    }

    // "그거 좀 꺼도" → 되묻기 → "에어컨"
    if (!calls.length && pending && pending.intent && !sawIntent) {
      for (const d of seen) { const c = mapIntent(d, pending.intent, text); if (c) calls.push(c); }
    }

    pending = null;
    if (calls.length) return calls;
    if (ask) { pending = ask; return [ASK]; }
    if (sawUnsupported) return [NONE];
    if (firstCue) return [firstCue.fallback(state) || NONE];
    if (seen.length) { pending = { devices: seen }; return [ASK]; }
    return [NONE];
  }

  async function callLlm(text) {
    const cfg = S.config;
    const messages = [];
    if (cfg.systemPrompt) messages.push({ role: "system", content: cfg.systemPrompt });
    messages.push({ role: "user", content: text });

    const t0 = performance.now();
    for (let attempt = 0; attempt < 2; attempt++) {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), cfg.timeoutMs);
      try {
        const res = await fetch(cfg.llmEndpoint, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ model: cfg.llmModel, messages, temperature: 0, max_tokens: 80 }),
          signal: ctrl.signal,
        });
        if (!res.ok) throw new Error("HTTP " + res.status);
        const data = await res.json();
        return { content: data.choices[0].message.content, extra: data.six_sense || null, ms: Math.round(performance.now() - t0) };
      } catch (err) {
        if (attempt === 1) throw err;
      } finally {
        clearTimeout(timer);
      }
    }
  }

  async function interpret(text, state) {
    if (S.config.llmEndpoint) {
      try {
        const r = await callLlm(text);
        return { source: "llm", output: r.content, trace: { extra: r.extra, ms: r.ms } };
      } catch (err) {
        return { source: "llm", output: null, error: "E03" };
      }
    }
    const calls = interpretByRules(text, state);
    return { source: "rule", output: calls.map(S.parser.formatCall).join("") };
  }

  S.engine = { interpret, getPending: () => pending };
})(window.SixSense);

