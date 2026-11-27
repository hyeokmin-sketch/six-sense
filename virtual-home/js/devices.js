// IF-4: (object, type) → 기기 상태 변경. 실기기 대신 이 상태를 가상 원룸에 그린다.
(function (S) {
  "use strict";

  const LIGHT_LEVELS = [30, 70, 100];               // 은은하게, 보통, 밝게
  const WIND_LABELS = ["약하게", "보통", "세게"];
  const AC_DEFAULT = 24, AC_MIN = 18, AC_MAX = 30;
  const ROOM_BASE = 28, RECOMMENDED = 26;

  const state = {
    light: { on: true, level: 1 },
    ac: { on: false, target: AC_DEFAULT, wind: 1, room: ROOM_BASE },
    blind: { open: 40, target: 40 },
  };

  // 요구사항 정의서 1.3의 16종 호출 (되묻기·해당 없음은 execute에서 따로 처리)
  const CALLS = {
    "six_0:1:1": { name: "조명 켜기", reply: "조명을 켤게요." },
    "six_0:1:2": { name: "조명 끄기", reply: "조명을 끌게요." },
    "six_1:1:1": { name: "조명 더 밝게", reply: "조명을 더 밝게 할게요." },
    "six_1:1:2": { name: "조명 은은하게", reply: "조명을 은은하게 할게요." },
    "six_0:2:1": { name: "에어컨 켜기", reply: `에어컨을 켤게요. ${AC_DEFAULT}°C로 맞출게요.` },
    "six_0:2:2": { name: "에어컨 끄기", reply: "에어컨을 끌게요." },
    "six_1:2:1": { name: "온도 올리기", reply: "온도를 올릴게요." },
    "six_1:2:2": { name: "온도 내리기", reply: "온도를 내릴게요." },
    "six_1:2:3": { name: "바람 세게", reply: "바람을 세게 할게요." },
    "six_1:2:4": { name: "바람 약하게", reply: "바람을 약하게 할게요." },
    "six_0:3:1": { name: "블라인드 올리기", reply: "블라인드를 올릴게요." },
    "six_0:3:2": { name: "블라인드 내리기", reply: "블라인드를 내릴게요." },
    "six_0:3:3": { name: "블라인드 멈추기", reply: "블라인드를 멈출게요." },
    "six_0:3:4": { name: "블라인드 반쯤 내리기", reply: "블라인드를 반쯤 내릴게요." },
  };

  const listeners = [];
  function emit() { listeners.forEach(fn => fn(state)); }
  function onChange(fn) { listeners.push(fn); fn(state); }

  // 한계에 걸리면 기본 응답 대신 쓸 문장을 돌려준다
  function apply(key) {
    const { light, ac, blind } = state;
    switch (key) {
      case "six_0:1:1": light.on = true; break;
      case "six_0:1:2": light.on = false; break;
      case "six_1:1:1":
        if (light.on && light.level === 2) return "지금이 가장 밝아요.";
        light.level = Math.min(2, light.level + 1); light.on = true; break;
      case "six_1:1:2":
        if (light.on && light.level === 0) return "지금이 가장 은은해요.";
        light.level = Math.max(0, light.level - 1); light.on = true; break;
      case "six_0:2:1": ac.on = true; ac.target = AC_DEFAULT; break;
      case "six_0:2:2": ac.on = false; break;
      case "six_1:2:1":
        if (ac.on && ac.target === AC_MAX) return `이미 가장 높은 온도예요(${AC_MAX}°C).`;
        ac.on = true; ac.target = Math.min(AC_MAX, ac.target + 1);
        return `온도를 ${ac.target}°C로 올릴게요.`;
      case "six_1:2:2":
        if (ac.on && ac.target === AC_MIN) return `이미 가장 낮은 온도예요(${AC_MIN}°C).`;
        ac.on = true; ac.target = Math.max(AC_MIN, ac.target - 1);
        return `온도를 ${ac.target}°C로 내릴게요.`;
      case "six_1:2:3":
        if (ac.on && ac.wind === 2) return "바람이 이미 가장 세요.";
        ac.on = true; ac.wind = Math.min(2, ac.wind + 1); break;
      case "six_1:2:4":
        if (ac.on && ac.wind === 0) return "바람이 이미 가장 약해요.";
        ac.on = true; ac.wind = Math.max(0, ac.wind - 1); break;
      case "six_0:3:1": blind.target = 100; break;
      case "six_0:3:2": blind.target = 0; break;
      case "six_0:3:3":
        if (blind.open === blind.target) return "블라인드가 이미 멈춰 있어요.";
        blind.target = blind.open; break;
      case "six_0:3:4": blind.target = 50; break;
    }
    return null;
  }

  function execute(call) {
    if (call.tag === "six_2") return { kind: "none", reply: "제가 도와드릴 수 있는 일이 아니에요." };
    if (call.tag === "six_0" && call.object === -1) return { kind: "ask", reply: "어떤 기기를 말씀하시는 건가요?" };
    const key = `${call.tag}:${call.object}:${call.type}`;
    const spec = CALLS[key];
    if (!spec) return { kind: "none", code: "E02", reply: "제가 도와드릴 수 있는 일이 아니에요." };
    const override = apply(key);
    emit();
    return { kind: "action", key, name: spec.name, device: call.object, reply: override || spec.reply };
  }

  // 화면의 슬라이더·버튼용 직접 조작
  function setLightLevel(level) { state.light.on = true; state.light.level = level; emit(); }
  function setWind(wind) { state.ac.wind = wind; emit(); }
  function setBlind(open) { state.blind.open = state.blind.target = open; emit(); }

  // 블라인드는 천천히 움직여야 '멈추기'가 의미가 있다 (끝까지 약 6초)
  setInterval(() => {
    const b = state.blind;
    if (b.open !== b.target) { b.open += Math.sign(b.target - b.open); emit(); }
  }, 60);

  // 실내 온도: 켜져 있으면 설정 온도로, 꺼지면 원래 온도로 1°C씩 움직인다
  setInterval(() => {
    const a = state.ac;
    const goal = a.on ? a.target : ROOM_BASE;
    if (a.room !== goal) { a.room += Math.sign(goal - a.room); emit(); }
  }, 4000);

  S.devices = {
    state, CALLS, LIGHT_LEVELS, WIND_LABELS, AC_DEFAULT, AC_MIN, AC_MAX, RECOMMENDED,
    execute, onChange, setLightLevel, setWind, setBlind,
  };
})(window.SixSense);

