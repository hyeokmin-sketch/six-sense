// 폰 앱 껍데기: 탭 전환, 처리 과정 애니메이션, 결과 시트, 대화 기록, 홈 요약 칩
(function (S) {
  "use strict";
  const $ = s => document.querySelector(s);
  const D = S.devices;
  const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const reduce = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;

  // ---------- 시계 ----------
  function tick() {
    const d = new Date();
    $("#clock").textContent = `${d.getHours()}:${String(d.getMinutes()).padStart(2, "0")}`;
  }
  tick(); setInterval(tick, 20000);

  // ---------- 탭 ----------
  const ORDER = ["home", "devices", "flow", "log", "settings"];
  let cur = "home";
  function go(name) {
    if (name === cur) return;
    const from = $("#screen-" + cur), to = $("#screen-" + name);
    const dir = ORDER.indexOf(name) > ORDER.indexOf(cur) ? 1 : -1;
    to.style.setProperty("--from", dir * 28 + "px");
    from.style.setProperty("--to", dir * -28 + "px");
    from.classList.remove("active");
    to.classList.add("active");
    to.scrollTop = 0;
    document.querySelectorAll(".tab").forEach(t => t.classList.toggle("active", t.dataset.tab === name));
    cur = name;
    if (name === "flow") document.querySelector('[data-tab="flow"]').classList.remove("has-new");
    if (name !== "home") hideSheet();
  }
  $("#tabbar").addEventListener("click", e => { const b = e.target.closest("[data-tab]"); if (b) go(b.dataset.tab); });

  // ---------- 홈 요약 칩 ----------
  function chips(state) {
    const { light, ac, blind } = state;
    const items = [
      ["조명", light.on ? `켜짐 · ${D.LIGHT_LEVELS[light.level]}%` : "꺼짐", light.on, "devices"],
      ["에어컨", ac.on ? `${ac.target}°C · 실내 ${ac.room}°C` : "꺼짐", ac.on, "devices"],
      ["블라인드", blind.open === 0 ? "닫힘" : blind.open === 100 ? "활짝 열림" : `${blind.open}% 열림`, blind.open > 0, "devices"],
    ];
    $("#home-chips").innerHTML = items.map(([n, v, on, t]) =>
      `<button class="chip${on ? " on" : ""}" data-go="${t}"><span class="chip-dot"></span><strong>${n}</strong><em>${esc(v)}</em></button>`).join("");
  }
  $("#home-chips").addEventListener("click", e => { const b = e.target.closest("[data-go]"); if (b) go(b.dataset.go); });
  D.onChange(chips);

  // ---------- 처리 과정 (상단 알림 섬) ----------
  let flowSeq = 0;
  async function playFlow(text, opts) {
    const fast = opts && opts.fast, k = reduce ? 0.2 : fast ? 0.55 : 1;
    const id = ++flowSeq, isl = $("#island"), tx = $("#isl-text");
    const steps = [...document.querySelectorAll("#isl-steps li")];
    steps.forEach(li => li.className = "");
    isl.className = "island open listening";
    tx.textContent = "듣는 중…";
    await sleep(650 * k); if (id !== flowSeq) return;
    isl.className = "island open";
    tx.textContent = "";
    const per = Math.min(34, 900 / Math.max(1, text.length)) * k;
    for (let i = 1; i <= text.length; i++) { tx.textContent = "“" + text.slice(0, i) + (i < text.length ? "" : "”"); await sleep(per); if (id !== flowSeq) return; }
    for (const li of steps) { li.className = "on"; await sleep(230 * k); if (id !== flowSeq) return; }
    steps.forEach(li => li.className = "done");
    setTimeout(() => { if (id === flowSeq) isl.className = "island"; }, 1500 * (fast ? 1 : 1));
  }

  // ---------- 결과 시트 ----------
  let sheetTimer = null;
  function showSheet({ title, text, detail, foot, ask }) {
    const el = $("#sheet");
    $("#sheet-body").innerHTML = `
      <p class="sh-quote">“${esc(text)}”</p>
      <h3 class="${ask ? "ask" : ""}">${esc(title)}</h3>
      <p class="sh-detail">${esc(detail)}</p>
      <p class="sh-foot">${esc(foot)}</p>`;
    el.classList.remove("show"); void el.offsetWidth; el.classList.add("show");
    clearTimeout(sheetTimer);
    sheetTimer = setTimeout(hideSheet, ask ? 9000 : 5500);
  }
  function hideSheet() { $("#sheet").classList.remove("show"); }
  $("#sheet").addEventListener("click", hideSheet);

  // ---------- 기록 ----------
  function onResult(r) {
    const log = $("#log");
    const empty = log.querySelector(".log-empty"); if (empty) empty.remove();
    const d = new Date(), t = `${d.getHours()}:${String(d.getMinutes()).padStart(2, "0")}`;
    const li = document.createElement("li");
    li.className = "log-item" + (r.ask ? " ask" : "");
    li.innerHTML = `<time>${t}</time><div class="b me">${esc(r.text)}</div><div class="b ai"><strong>${esc(r.title)}</strong><span>${esc(r.foot)}</span></div>`;
    log.prepend(li);
    if (cur === "home") showSheet(r);
  }
  function flowUpdated() { if (cur !== "flow") document.querySelector('[data-tab="flow"]').classList.add("has-new"); }

  S.shell = { go, playFlow, onResult, flowUpdated };
})(window.SixSense);

