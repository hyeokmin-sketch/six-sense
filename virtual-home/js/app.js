// 화면 연결: 입력 → 엔진(IF-2) → 파서(IF-3) → 기기(IF-4) → 가상 원룸·카드·응답
(function (S) {
  "use strict";

  const D = S.devices;
  const $ = sel => document.querySelector(sel);
  const NAMES = { 1: "조명", 2: "에어컨", 3: "블라인드" };
  const OBJ = { 1: "조명을", 2: "에어컨을", 3: "블라인드를" };
  const CHOICES = { 1: "켤까요, 끌까요?", 2: "켤까요, 끌까요?", 3: "올릴까요, 내릴까요?" };

  const SCENARIOS = [
    { group: "아침", time: "morning", items: [["기상", "아이고 인자 일났다, 블라인드 좀 올리도"], ["예외", "아이고 눈부시다, 퍼뜩 좀 가려봐라"]] },
    { group: "점심", time: "noon", items: [["더위", "아 덥다 아이가, 이거 좀 우예 해봐라"]] },
    { group: "저녁", time: "evening", items: [["식후", "불 좀 낮차라, 눈 아프다"], ["취침", "인자 잘란다, 싹 다 꺼라"]] },
    { tag: "되묻기", text: "그거 좀 꺼도" },
    { tag: "STT 오인식", text: "에어컨 고맙거라" },
    { tag: "해당 없음", text: "옆집은 에어컨 샀다더라" },
  ];

  let time = "noon";
  let showPins = true;

  const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

  // ---------- 테마 ----------
  // 색 값은 style.css(:root[data-theme])와 room.js(PALETTES)에 있다. 여기는 이름과 견본 색만.
  const THEMES = [
    { id: "rose", label: "로즈", sw: ["#fdf3f6", "#c94f6d"] },
    { id: "mono", label: "모노", sw: ["#ffffff", "#1f1f1f"] },
    { id: "navy", label: "네이비", sw: ["#cfe4f5", "#1f3a68"] },
    { id: "beige", label: "베이지", sw: ["#ede0cd", "#8b5e3c"] },
  ];
  const THEME_KEY = "sixsense-theme";
  let theme = THEMES.some(t => t.id === document.documentElement.dataset.theme)
    ? document.documentElement.dataset.theme : "rose";

  const THEME_HTML = THEMES.map(t =>
    `<button role="radio" data-theme-pick="${t.id}" aria-checked="${t.id === theme}" style="--sw1:${t.sw[0]};--sw2:${t.sw[1]}"><span class="swatch"></span>${t.label}</button>`
  ).join("");
  document.querySelectorAll(".theme-picker").forEach(p => { p.innerHTML = THEME_HTML; });;
  document.addEventListener("click", e => {
    const btn = e.target.closest("[data-theme-pick]");
    if (btn) setTheme(btn.dataset.themePick);
  });

  function setTheme(next) {
    theme = next;
    document.documentElement.dataset.theme = theme;
    document.querySelectorAll("[data-theme-pick]").forEach(b =>
      b.setAttribute("aria-checked", b.dataset.themePick === theme));
    try { localStorage.setItem(THEME_KEY, theme); } catch (e) {}
    room.setTheme(theme);
  }

  // ---------- 고령자 모드: 큰 글씨 + 높은 대비 ----------
  const SENIOR_KEY = "sixsense-senior";
  function setSenior(on) {
    if (on) document.documentElement.dataset.senior = "1"; else delete document.documentElement.dataset.senior;
    const b = $("#senior-toggle");
    b.setAttribute("aria-pressed", on);
    b.querySelector("span:last-child").textContent = on ? "고령자 모드 켜짐" : "고령자 모드";
    try { localStorage.setItem(SENIOR_KEY, on ? "1" : "0"); } catch (e) {}
  }
  $("#senior-toggle").addEventListener("click", () => setSenior(document.documentElement.dataset.senior !== "1"));
  setSenior(document.documentElement.dataset.senior === "1");

  // ---------- 가상 원룸 ----------
  const room = S.room.mount($("#room"), { onDevice: toggleFromRoom, theme });

  function toggleFromRoom(device) {
    const { light, ac, blind } = D.state;
    if (device === 1) runKey(light.on ? "six_0:1:2" : "six_0:1:1");
    if (device === 2) runKey(ac.on ? "six_0:2:2" : "six_0:2:1");
    if (device === 3) runKey(blind.open !== blind.target ? "six_0:3:3" : blind.open > 0 ? "six_0:3:2" : "six_0:3:1");
  }

  function runKey(key) {
    const [tag, object, type] = key.split(":");
    return D.execute({ tag, object: Number(object), type: Number(type) });
  }

  document.querySelectorAll("[data-time]").forEach(btn =>
    btn.addEventListener("click", () => setTime(btn.dataset.time)));

  function setTime(next) {
    time = next;
    document.querySelectorAll("[data-time]").forEach(b => {
      const active = b.dataset.time === time;
      b.classList.toggle("active", active);
      b.setAttribute("aria-selected", active);
    });
    render(D.state);
  }

  $("#toggle-pins").addEventListener("click", e => {
    showPins = !showPins;
    e.currentTarget.setAttribute("aria-pressed", showPins);
    render(D.state);
  });

  // ---------- 기기 카드 ----------
  document.addEventListener("click", e => {
    const btn = e.target.closest("[data-call]");
    if (btn && !btn.disabled) runKey(btn.dataset.call);
  });
  $("#ac-toggle").addEventListener("click", () => runKey(D.state.ac.on ? "six_0:2:2" : "six_0:2:1"));
  document.querySelectorAll("[data-level]").forEach(b =>
    b.addEventListener("click", () => D.setLightLevel(Number(b.dataset.level))));
  document.querySelectorAll("[data-wind]").forEach(b =>
    b.addEventListener("click", () => D.setWind(Number(b.dataset.wind))));

  // 세로 슬라이더. 값은 0~1, 1이 위쪽.
  function makeVSlider(el, { steps, onInput, label }) {
    el.tabIndex = 0;
    el.setAttribute("role", "slider");
    el.setAttribute("aria-label", label);
    el.setAttribute("aria-orientation", "vertical");
    const read = e => {
      const r = el.getBoundingClientRect();
      let v = Math.min(1, Math.max(0, 1 - (e.clientY - r.top) / r.height));
      if (steps) v = Math.round(v * (steps - 1)) / (steps - 1);
      return v;
    };
    el.addEventListener("pointerdown", e => {
      el.setPointerCapture(e.pointerId);
      el.classList.add("dragging");
      onInput(read(e));
      const move = ev => onInput(read(ev));
      el.addEventListener("pointermove", move);
      el.addEventListener("pointerup", () => {
        el.removeEventListener("pointermove", move);
        el.classList.remove("dragging");
      }, { once: true });
    });
    el.addEventListener("keydown", e => {
      const step = steps ? 1 / (steps - 1) : 0.1;
      const v = Number(el.style.getPropertyValue("--v")) || 0;
      if (e.key === "ArrowUp" || e.key === "ArrowRight") { e.preventDefault(); onInput(Math.min(1, v + step)); }
      if (e.key === "ArrowDown" || e.key === "ArrowLeft") { e.preventDefault(); onInput(Math.max(0, v - step)); }
    });
    return {
      set(v, text) {
        el.style.setProperty("--v", v);
        el.setAttribute("aria-valuenow", Math.round(v * 100));
        el.setAttribute("aria-valuetext", text);
      },
    };
  }

  const lightSlider = makeVSlider($("#light-slider"), {
    steps: 3, label: "조명 밝기", onInput: v => D.setLightLevel(Math.round(v * 2)),
  });
  const blindSlider = makeVSlider($("#blind-slider"), {
    label: "블라인드 열림 정도", onInput: v => D.setBlind(Math.round(v * 100)),
  });

  function renderThermo(ac) {
    const y = t => 16 + (30 - t) * 10;          // 30°C → 16, 18°C → 136
    const roomY = y(ac.room), recY = y(D.RECOMMENDED), setY = y(ac.target);
    const close = Math.abs(roomY - recY) < 14;
    const fill = ac.on ? "t-fill" : "t-fill off";
    $("#thermo").innerHTML = `
      <text x="104" y="${y(30) + 4}" class="t-scale">30°C</text>
      <text x="104" y="${y(18) + 4}" class="t-scale">18°C</text>
      <rect x="66" y="8" width="16" height="140" rx="8" class="t-tube"/>
      <rect x="70" y="${roomY}" width="8" height="${150 - roomY}" rx="4" class="${fill}"/>
      <circle cx="74" cy="154" r="12" class="${fill} t-bulb" stroke-width="3"/>
      <line x1="52" x2="66" y1="${roomY}" y2="${roomY}" class="t-line"/>
      <circle cx="74" cy="${roomY}" r="3.5" class="t-dot"/>
      <text x="50" y="${roomY + 4}" text-anchor="end" class="t-mark">실내 ${ac.room}°C</text>
      <line x1="52" x2="66" y1="${recY}" y2="${recY}" class="t-line faint"/>
      <circle cx="74" cy="${recY}" r="3.5" class="t-dot faint"/>
      <text x="50" y="${recY + 4 + (close ? 13 : 0)}" text-anchor="end" class="t-mark faint">권장 ${D.RECOMMENDED}°C</text>
      <line x1="82" x2="94" y1="${setY}" y2="${setY}" class="t-set-line ${ac.on ? "" : "off"}" stroke-width="2"/>
      <text x="98" y="${setY - 6}" class="t-set-label">설정</text>
      <text x="98" y="${setY + 14}" class="t-set ${ac.on ? "" : "off"}">${ac.target}°C</text>`;
  }

  let prev = null;
  function pulse(id) {
    const el = $(id);
    el.classList.remove("pulse");
    void el.offsetWidth;
    el.classList.add("pulse");
  }

  function render(state) {
    const { light, ac, blind } = state;

    // 조명
    const pct = D.LIGHT_LEVELS[light.level];
    $("#card-light").classList.toggle("off", !light.on);
    $("#light-sub").textContent = light.on ? `켜짐, 밝기 ${pct}%` : "꺼짐";
    $("#light-pct").textContent = light.on ? `${pct}%` : "꺼짐";
    $("#light-on").classList.toggle("is-current", light.on);
    $("#light-off").classList.toggle("is-current", !light.on);
    document.querySelectorAll("[data-level]").forEach(b =>
      b.classList.toggle("active", Number(b.dataset.level) === light.level));
    lightSlider.set(light.level / 2, ["은은하게", "보통", "밝게"][light.level]);

    // 에어컨
    $("#card-ac").classList.toggle("off", !ac.on);
    $("#ac-sub").textContent = `${ac.on ? "켜짐" : "꺼짐"}, 희망 ${ac.target}°C`;
    $("#ac-hint").textContent = !ac.on
      ? `에어컨 꺼짐, 켜면 ${D.AC_DEFAULT}°C로 맞춰요`
      : ac.room !== ac.target
        ? `실내 ${ac.room}°C → ${ac.target}°C로 맞추는 중`
        : `실내가 ${ac.target}°C에 맞춰졌어요`;
    const toggle = $("#ac-toggle");
    toggle.textContent = ac.on ? "끄기" : "켜기";
    toggle.className = "btn " + (ac.on ? "orange" : "green");
    document.querySelectorAll("#card-ac [data-call], #card-ac [data-wind]").forEach(b => { b.disabled = !ac.on; });
    document.querySelectorAll("[data-wind]").forEach(b =>
      b.classList.toggle("active", Number(b.dataset.wind) === ac.wind));
    renderThermo(ac);

    // 블라인드
    const moving = blind.open !== blind.target;
    $("#blind-sub").textContent = moving
      ? (blind.target > blind.open ? "올라가는 중" : "내려가는 중")
      : blind.open === 0 ? "닫힘" : blind.open === 100 ? "활짝 열림" : `${blind.open}% 열림`;
    $("#blind-pct").textContent = `${blind.open}% 열림`;
    $("#blind-slats").style.height = `${100 - blind.open}%`;
    blindSlider.set(blind.open / 100, `${blind.open}% 열림`);

    room.update(state, { time, showPins });

    if (prev) {
      if (prev.light !== `${light.on}${light.level}`) pulse("#card-light");
      if (prev.ac !== `${ac.on}${ac.target}${ac.wind}`) pulse("#card-ac");
      if (prev.blind !== blind.target) pulse("#card-blind");
    }
    prev = { light: `${light.on}${light.level}`, ac: `${ac.on}${ac.target}${ac.wind}`, blind: blind.target };
  }

  // ---------- 말하기 ----------
  const sc = $("#scenarios");
  sc.innerHTML = SCENARIOS.map(s => s.group
    ? `<div class="sc-group"><span class="sc-label">${s.group}</span><div class="sc-items">${
        s.items.map(([tag, text]) => `<button class="sc-item" data-say="${esc(text)}" data-time-of="${s.time}"><span class="sc-tag">${tag}</span>${esc(text)}</button>`).join("")
      }</div></div>`
    : `<button class="sc-item wide" data-say="${esc(s.text)}"><span class="sc-tag">${s.tag}</span>${esc(s.text)}</button>`
  ).join("");
  sc.addEventListener("click", e => {
    const btn = e.target.closest("[data-say]");
    if (!btn) return;
    if (btn.dataset.timeOf) setTime(btn.dataset.timeOf);
    $("#say-input").value = btn.dataset.say;
    say(btn.dataset.say);
  });

  $("#say-form").addEventListener("submit", e => {
    e.preventDefault();
    say($("#say-input").value);
  });

  const MIC_IDLE_SUB = "사투리도, 혼잣말도 괜찮아요";
  const MIC_LIVE_SUB = "보드 마이크가 듣고 있어요 · 사투리도 괜찮아요";
  let micTimer = null, voiceOk = false;
  let demoIdx = 0;
  const DEMO_LINES = SCENARIOS.flatMap(s => s.group ? s.items.map(i => i[1]) : [s.text]);
  $("#mic").addEventListener("click", () => {
    const sub = $("#mic-sub");
    if (!voiceOk) {       // 보드가 없을 때는 시나리오 문장을 말한 것처럼 보여 준다
      const line = DEMO_LINES[demoIdx++ % DEMO_LINES.length];
      sub.textContent = "시연 모드: 시나리오 문장을 말한 것처럼 보여 드려요.";
      sub.classList.add("notice");
      clearTimeout(micTimer);
      micTimer = setTimeout(() => { sub.textContent = MIC_IDLE_SUB; sub.classList.remove("notice"); }, 4000);
      say(line);
      return;
    }
    sub.textContent = "보드 마이크가 듣고 있어요. 그냥 말씀해 주세요.";
    sub.classList.add("notice");
    clearTimeout(micTimer);
    micTimer = setTimeout(() => {
      sub.textContent = voiceOk ? MIC_LIVE_SUB : MIC_IDLE_SUB;
      sub.classList.remove("notice");
    }, 4000);
    $("#say-input").focus();
  });

  async function say(raw) {
    const text = raw.trim();
    if (!text) return;
    const submit = $("#say-form button");
    submit.disabled = true;
    const flow = S.shell ? S.shell.playFlow(text) : Promise.resolve();
    const result = await S.engine.interpret(text, D.state);
    await flow;
    submit.disabled = false;
    present(text, result);
    $("#say-input").value = "";
  }

  // 말 한 번을 화면에 풀어 보여 준다. 글자 입력과 보드 음성이 함께 쓴다. 반환: 사용자에게 들려줄 안내 문장
  function present(text, result) {
    if (result.error === "E03") {
      renderPipeline({ text, result, calls: [], outcomes: [], failed: true });
      return showResult({ text, title: "응답이 늦어지고 있어요", detail: "잠시 뒤에 다시 말씀해 주세요.", foot: "아무 기기도 움직이지 않았어요.", meta: "LLM 서버 응답 시간 초과 (E03, 1회 재시도함)" });
    }
    const { calls, error } = S.parser.parseCalls(result.output);
    const outcomes = (error ? [{ tag: "six_0", object: -1 }] : calls).map(D.execute);
    const actions = outcomes.filter(o => o.kind === "action");
    renderPipeline({ text, result, calls: error ? [{ tag: "six_0", object: -1 }] : calls, outcomes, error });
    const source = result.source === "llm" ? "LLM" : "규칙 엔진";
    let meta = `${source} 출력 <code>${esc(result.output)}</code>`;
    if (error) meta += " · 형식이 맞지 않아 되묻기로 처리 (E01)";

    if (actions.length) {
      const names = actions.map(a => a.name);
      return showResult({
        text, meta,
        title: actions.map(a => a.reply).join(" "),
        detail: actions.length > 1 ? `말씀하신 순서대로 했어요: ${names.join(" → ")}` : `‘${names[0]}’로 알아들었어요.`,
        foot: `움직인 기기: ${[...new Set(actions.map(a => NAMES[a.device]))].join(", ")}`,
      });
    } else if (outcomes.some(o => o.kind === "ask")) {
      const pending = result.source === "rule" ? S.engine.getPending() : null;
      const dev = pending && pending.devices && pending.devices.length === 1 ? pending.devices[0] : null;
      return showResult({
        text, meta,
        title: dev ? `${OBJ[dev]} 어떻게 할까요? ${CHOICES[dev]}` : "어떤 기기를 말씀하시는 건가요?",
        detail: dev
          ? "하고 싶은 것만 짧게 말씀해 주세요. 예: ‘꺼라’"
          : pending ? "조명, 에어컨, 블라인드 중에 말씀해 주세요. 기기 이름만 말해도 이어서 해 드릴게요."
          : "조명, 에어컨, 블라인드 중 어떤 기기인지 함께 말씀해 주세요.",
        foot: "아무 기기도 움직이지 않았어요.", ask: true,
      });
    } else {
      const outOfRange = outcomes.some(o => o.code === "E02");
      return showResult({
        text, meta: outOfRange ? meta + " · 범위 밖 값 (E02)" : meta,
        title: "제가 도와드릴 수 있는 일이 아니에요",
        detail: "우리 집 기기를 움직이라는 말은 아니라서 그대로 둘게요.",
        foot: "아무 기기도 움직이지 않았어요.",
      });
    }
  }

  // ---------- 처리 과정 패널 ----------
  const PL_EMPTY = [
    ["1", "음성 인식", "STT가 받아 적은 문장"],
    ["2", "치환 사전", "인식 오류를 바로잡음"],
    ["3", "LLM", "기능 호출 형식으로 변환"],
    ["4", "해석", "기기와 동작으로 풀이"],
    ["5", "기기 반응", "집 안에서 일어난 일"],
  ];
  function renderEmptyPipeline() {
    const html = PL_EMPTY.map(([n, t, d]) =>
      `<li class="pl-step idle"><span class="pl-num">${n}</span><div class="pl-body"><strong>${t}</strong><p class="pl-desc">${d}</p></div></li>`).join("");
    document.querySelectorAll("[data-pl-steps]").forEach(el => { el.innerHTML = html; });
  }
  renderEmptyPipeline();

  // 바뀐 부분만 눈에 띄게: 앞뒤로 같은 글자를 빼고 가운데만 표시
  function diffMarkup(a, b) {
    let i = 0;
    while (i < a.length && i < b.length && a[i] === b[i]) i++;
    let ja = a.length, jb = b.length;
    while (ja > i && jb > i && a[ja - 1] === b[jb - 1]) { ja--; jb--; }
    const wrap = (s, cls) => s ? `<mark class="${cls}">${esc(s)}</mark>` : "";
    return {
      before: esc(a.slice(0, i)) + wrap(a.slice(i, ja), "del") + esc(a.slice(ja)),
      after: esc(b.slice(0, i)) + wrap(b.slice(i, jb), "add") + esc(b.slice(jb)),
    };
  }

  function renderPipeline({ text, result, calls, outcomes, error, failed }) {
    const tr = result.trace || {};
    const x = tr.extra;
    const steps = [];
    const live = result.source === "llm";

    steps.push({ n: "1", t: "음성 인식", html: `<p class="pl-quote">“${esc(x ? x.original : text)}”</p>`, note: x && x.stt_ms ? `보드 마이크 · 인식 ${x.stt_ms} ms` : "STT가 받아 적은 문장", mic: !!(x && x.stt_ms) });

    if (x && x.dict_used) {
      if (x.corrected !== x.original) {
        const d = diffMarkup(x.original, x.corrected);
        steps.push({ n: "2", t: "치환 사전", html: `<p class="pl-quote dim">${d.before}</p><p class="pl-arrow">↓ 바로잡음</p><p class="pl-quote">${d.after}</p>`, note: `${x.dict_ms} ms`, hit: true });
      } else {
        steps.push({ n: "2", t: "치환 사전", html: `<p class="pl-quote">바꿀 말 없음</p>`, note: `${x.dict_ms} ms`, pass: true });
      }
    } else {
      steps.push({ n: "2", t: "치환 사전", html: `<p class="pl-quote dim">${live ? "사용 안 함" : "규칙 엔진에는 없음"}</p>`, note: "건너뜀", pass: true });
    }

    if (failed) {
      steps.push({ n: "3", t: "LLM", html: `<p class="pl-quote">응답 없음 (E03)</p>`, note: "시간 초과", bad: true });
    } else {
      const ms = x ? `${x.llm_ms} ms` : tr.ms ? `${tr.ms} ms` : "";
      steps.push({ n: "3", t: live ? "LLM" : "규칙 엔진(모의)", html: `<code class="pl-code">${esc(result.output)}</code>`, note: ms });
    }

    if (!failed) {
      const rows = calls.map((c, i) => {
        const o = outcomes[i] || {};
        let label;
        if (o.kind === "action") label = o.name;
        else if (o.kind === "ask") label = "기기를 되물음";
        else label = o.code === "E02" ? "범위 밖 값 (E02)" : "해당 없음";
        const cls = o.kind === "action" ? "ok" : o.kind === "ask" ? "ask" : "skip";
        return `<li class="${cls}">${esc(label)}</li>`;
      }).join("");
      steps.push({ n: "4", t: "해석", html: `<ul class="pl-list">${rows || "<li class='skip'>해석할 호출 없음</li>"}</ul>`, note: error ? "형식 오류 → 되묻기 (E01)" : `${calls.length}개 호출` });

      const acts = outcomes.filter(o => o.kind === "action");
      const reply = acts.length ? acts.map(a => a.reply).join(" ")
        : outcomes.some(o => o.kind === "ask") ? "어떤 기기인지 되묻고, 아무것도 움직이지 않음" : "아무 기기도 움직이지 않음";
      steps.push({ n: "5", t: "기기 반응", html: `<p class="pl-quote">${esc(reply)}</p>`, note: acts.length ? [...new Set(acts.map(a => NAMES[a.device]))].join(", ") : "변화 없음", ok: acts.length > 0 });
    } else {
      steps.push({ n: "4", t: "해석", html: `<p class="pl-quote dim">—</p>`, note: "", pass: true });
      steps.push({ n: "5", t: "기기 반응", html: `<p class="pl-quote">아무 기기도 움직이지 않음</p>`, note: "안전하게 멈춤" });
    }

    const total = tr.ms ? (tr.voice ? ` · 인식부터 모델까지 ${tr.ms} ms` : ` · 서버 왕복 ${tr.ms} ms`) : "";
    document.querySelectorAll("[data-pl-sub]").forEach(el => { el.textContent = `“${text}”${total}`; });
    const plHtml = steps.map((s, i) => `
      <li class="pl-step${s.hit ? " hit" : ""}${s.bad ? " bad" : ""}${s.pass ? " pass" : ""}${s.ok ? " done" : ""}" style="--d:${i * 110}ms">
        <span class="pl-num">${s.n}</span>
        <div class="pl-body"><strong>${s.t}</strong>${s.html}<p class="pl-note">${esc(s.note || "")}</p></div>
      </li>`).join("");
    document.querySelectorAll("[data-pl-steps]").forEach(el => { el.innerHTML = plHtml; });
    if (S.shell) S.shell.flowUpdated();
  }

  function showResult({ text, title, detail, foot, meta, ask }) {
    const el = $("#result");
    el.classList.toggle("ask", !!ask);
    el.innerHTML = `
      <h3>${esc(title)}</h3>
      <p class="quote">“${esc(text)}”</p>
      <p class="detail">${esc(detail)}</p>
      <p class="foot">${esc(foot)}</p>
      ${meta ? `<p class="meta">${meta}</p>` : ""}`;
    if (S.shell) S.shell.onResult({ text, title, detail, foot, ask: !!ask });
    return title;
  }

  // ---------- 보드 음성 ----------
  // 보드의 음성 서비스가 인식한 말을 중계 서버에서 0.7초마다 가져와 같은 화면으로 보여 준다.
  const voiceBase = S.config.llmEndpoint ? S.config.llmEndpoint.replace(/\/v1\/chat\/completions.*$/, "") : null;
  let voiceLast = null;

  function setVoiceLink(ok) {
    if (ok === voiceOk) return;
    voiceOk = ok;
    $("#mic").dataset.live = ok ? "1" : "0";
    $("#mic-title").textContent = ok ? "말씀하세요" : "누르고 말씀하세요";
    const sub = $("#mic-sub");
    if (!sub.classList.contains("notice")) sub.textContent = ok ? MIC_LIVE_SUB : MIC_IDLE_SUB;
  }

  async function handleVoice(ev) {
    const result = {
      source: "llm", output: ev.content,
      trace: { ms: ev.total_ms, voice: true, extra: { original: ev.original, corrected: ev.corrected, dict_used: ev.dict_used,
                                                       dict_ms: ev.dict_ms, llm_ms: ev.llm_ms, stt_ms: ev.stt_ms } },
    };
    if (S.shell) S.shell.playFlow(ev.original, { fast: true });
    const spoken = present(ev.original, result);
    try {      // 화면에 보여 준 문장을 보드에 알려 주면, 보드가 같은 문장을 스피커로 말한다
      await fetch(`${voiceBase}/voice/reply`, { method: "POST", headers: { "Content-Type": "application/json" },
                                               body: JSON.stringify({ id: ev.id, text: spoken }) });
    } catch (_) { /* 보드가 기본 문장으로 말한다 */ }
  }

  async function voicePoll() {
    try {
      const first = voiceLast === null;
      const res = await fetch(`${voiceBase}/voice/events?after=${first ? -1 : voiceLast}${first ? "&peek=1" : ""}`);
      if (!res.ok) throw new Error("HTTP " + res.status);
      const data = await res.json();
      if (first) voiceLast = data.latest;
      else for (const ev of data.events) { voiceLast = Math.max(voiceLast, ev.id); await handleVoice(ev); }
      setVoiceLink(true);
    } catch (_) {
      setVoiceLink(false);
    }
    setTimeout(voicePoll, 700);
  }
  if (voiceBase && S.config.voice !== false) voicePoll();

  // ---------- 상단 표시 ----------
  if (S.config.llmEndpoint) {
    $("#mode-badge").classList.add("live");
    $("#mode-text").textContent = "LLM 서버 연결(내부망). 실제 기기 연결 전";
    $("#conn-text").textContent = "보드의 LLM 서버와 연결해 해석하는 중이에요(내부망).";
  }

  D.onChange(render);
})(window.SixSense);

