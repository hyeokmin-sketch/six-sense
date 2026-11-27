// 아이소메트릭 가상 원룸 (SVG). 벽 두 면을 연 인형의 집 구도.
// 좌표: x는 오른쪽 벽(A)을 따라, y는 왼쪽 벽(B)을 따라, z는 높이. 단위 1 ≈ 30cm.
(function (S) {
  "use strict";

  const SC = 32;
  const P = (x, y, z) => [(x - y) * SC * 0.866, (x + y) * SC * 0.5 - z * SC];
  const pts = list => list.map(p => P(...p).map(n => n.toFixed(1)).join(",")).join(" ");
  const poly = (list, fill, extra = "") => `<polygon points="${pts(list)}" fill="${fill}" ${extra}/>`;
  const line = (a, b, stroke, w = 1) =>
    `<line x1="${P(...a)[0].toFixed(1)}" y1="${P(...a)[1].toFixed(1)}" x2="${P(...b)[0].toFixed(1)}" y2="${P(...b)[1].toFixed(1)}" stroke="${stroke}" stroke-width="${w}" stroke-linecap="round"/>`;

  function shade(hex, f) {
    const n = parseInt(hex.slice(1), 16);
    const c = [n >> 16, (n >> 8) & 255, n & 255].map(v => Math.round(v * f));
    return `rgb(${c.join(",")})`;
  }

  // 축 정렬 상자. 보이는 면은 위, 앞-왼쪽(y 최대), 앞-오른쪽(x 최대) 셋뿐이다.
  function box(x, y, z, w, d, h, color) {
    const top = [[x, y, z + h], [x + w, y, z + h], [x + w, y + d, z + h], [x, y + d, z + h]];
    const left = [[x, y + d, z], [x + w, y + d, z], [x + w, y + d, z + h], [x, y + d, z + h]];
    const right = [[x + w, y, z], [x + w, y + d, z], [x + w, y + d, z + h], [x + w, y, z + h]];
    return poly(left, shade(color, 0.93)) + poly(right, shade(color, 0.84)) + poly(top, color);
  }

  // 테마별 원룸 색. box()가 hex를 어둡게 만들어 옆면을 그리므로 6자리 hex만 쓴다.
  const PALETTES = {
    rose: {
      slab: "#e6a4b4", floor: "#f4dcc8", plank: "rgba(150,90,70,.10)", rug: "#f7e7ea",
      wallA: "#f6dde4", wallAEnd: "#e3aebd", wallATop: "#eebfcb", baseA: "#ecc4ce",
      wallB: "#fbe8ee", wallBEnd: "#e8b5c2", wallBTop: "#f2c9d4", baseB: "#f0ccd6",
      trim: "#ffffff", valance: "#d7647f", frame: "#c9506b", picture: "#fde6c6",
      door: "#e58ea4", doorPanel: "#ea9fb2", wardrobe: "#faf1f4", wardrobeLine: "#e6d3d9", handle: "#d3b8c0",
      pot: "#d98b6c", leaf: "#5f9870", leaf2: "#78b086", dresser: "#f6e3e8", dresserTop: "#dfe9ef",
      fridgeLine: "#e3dade", nightstand: "#f3c9d3", headboard: "#c9506b", bedFrame: "#e7a3b4",
      pillow: "#fdf3f5", blanket: "#d9667f", cushion: "#f2b8c6", table: "#f7eadf",
      acLine: "#d9cdd1", cord: "#b9a3aa", shade: "#c9506b", bulbOff: "#e9dfe2",
      cloth: "#f6c3cf", clothLine: "#e28aa0", clothBar: "#c94f6d",
    },
    mono: {
      slab: "#9a9a9a", floor: "#e2e2e2", plank: "rgba(0,0,0,.07)", rug: "#f6f6f6",
      wallA: "#ededed", wallAEnd: "#c6c6c6", wallATop: "#d8d8d8", baseA: "#d4d4d4",
      wallB: "#f6f6f6", wallBEnd: "#cdcdcd", wallBTop: "#dfdfdf", baseB: "#dcdcdc",
      trim: "#ffffff", valance: "#3a3a3a", frame: "#262626", picture: "#e9e9e9",
      door: "#8d8d8d", doorPanel: "#a3a3a3", wardrobe: "#f3f3f3", wardrobeLine: "#dadada", handle: "#b0b0b0",
      pot: "#6e6e6e", leaf: "#4a4a4a", leaf2: "#6c6c6c", dresser: "#e8e8e8", dresserTop: "#d0d0d0",
      fridgeLine: "#dddddd", nightstand: "#cccccc", headboard: "#262626", bedFrame: "#9e9e9e",
      pillow: "#f5f5f5", blanket: "#454545", cushion: "#bdbdbd", table: "#dadada",
      acLine: "#cfcfcf", cord: "#9a9a9a", shade: "#262626", bulbOff: "#e0e0e0",
      cloth: "#d2d2d2", clothLine: "#a5a5a5", clothBar: "#262626",
    },
    navy: {
      slab: "#8fb1d1", floor: "#e1e8ef", plank: "rgba(40,70,110,.10)", rug: "#f3f7fb",
      wallA: "#e1edf8", wallAEnd: "#a9c4de", wallATop: "#c3d7ea", baseA: "#c9dbed",
      wallB: "#edf5fc", wallBEnd: "#b1cbe3", wallBTop: "#cddff0", baseB: "#d0e1f1",
      trim: "#ffffff", valance: "#2a4a7f", frame: "#1f3a68", picture: "#fde6c6",
      door: "#5b7fa8", doorPanel: "#6f92b9", wardrobe: "#f2f6fa", wardrobeLine: "#d9e3ed", handle: "#b3c4d6",
      pot: "#c98a6a", leaf: "#5f9870", leaf2: "#78b086", dresser: "#e4edf5", dresserTop: "#d3e2ee",
      fridgeLine: "#dbe2e9", nightstand: "#c4d7ea", headboard: "#1f3a68", bedFrame: "#8fb1d1",
      pillow: "#f4f8fc", blanket: "#2f5d8f", cushion: "#a9c8e6", table: "#f1ebe1",
      acLine: "#cfd8e2", cord: "#9fb0c2", shade: "#1f3a68", bulbOff: "#dfe6ee",
      cloth: "#c6dbef", clothLine: "#7fa6cc", clothBar: "#1f3a68",
    },
    beige: {
      slab: "#b89474", floor: "#dcc1a0", plank: "rgba(110,70,40,.14)", rug: "#f4ebdf",
      wallA: "#f2e8da", wallAEnd: "#d6bfa2", wallATop: "#e4d3bd", baseA: "#e0cdb4",
      wallB: "#f8f1e6", wallBEnd: "#dbc6aa", wallBTop: "#e9dac6", baseB: "#e6d5bf",
      trim: "#fffdf9", valance: "#9a6a45", frame: "#7a5233", picture: "#f3e6cf",
      door: "#a87b56", doorPanel: "#b78c67", wardrobe: "#f6efe5", wardrobeLine: "#e3d6c5", handle: "#c4ab8e",
      pot: "#b5744f", leaf: "#6d8f5e", leaf2: "#88a874", dresser: "#efe3d3", dresserTop: "#e3e8e4",
      fridgeLine: "#e3dbd0", nightstand: "#dcc5a8", headboard: "#7a5233", bedFrame: "#c6a27f",
      pillow: "#fbf6ef", blanket: "#a8744d", cushion: "#e2c9a8", table: "#ead9c2",
      acLine: "#ddd2c5", cord: "#b9a58f", shade: "#8b5e3c", bulbOff: "#ece3d8",
      cloth: "#e6d2b8", clothLine: "#c09a74", clothBar: "#8b5e3c",
    },
  };

  function plant(x, y, size, c) {
    const [cx, cy] = P(x + 0.3, y + 0.3, 0.7 + size * 0.03);
    const r = 12 + size;
    return box(x, y, 0, 0.6, 0.6, 0.7, c.pot) +
      `<circle cx="${cx}" cy="${cy - r * 0.6}" r="${r}" fill="${c.leaf}"/>` +
      `<circle cx="${cx - r * 0.35}" cy="${cy - r * 0.95}" r="${r * 0.55}" fill="${c.leaf2}"/>`;
  }

  const ROOM_X = 10, ROOM_Y = 9, ROOM_H = 6.2, WALL = 0.35;
  const LAMP = [5.2, 4.4];

  function build(c) {
    const floor = [[0, 0, 0], [ROOM_X, 0, 0], [ROOM_X, ROOM_Y, 0], [0, ROOM_Y, 0]];
    let planks = "";
    for (let y = 1; y < ROOM_Y; y++) planks += line([0, y, 0], [ROOM_X, y, 0], c.plank);

    const [lx, ly] = P(LAMP[0], LAMP[1], 4.75);
    const [px, py] = P(LAMP[0], LAMP[1], 0);
    let wind = "";
    for (const x of [3.4, 4.2, 5.0]) {
      const a = P(x, 0.85, 4.75), c = P(x, 2.0, 4.6), b = P(x + 0.3, 3.0, 3.0);
      wind += `<path d="M${a} Q${c} ${b}" />`;
    }
    const [mx, my] = P(0, 4.9, 4.3);
    const [sx, sy] = P(0, 3.0, 4.2);

    return `
<svg viewBox="-275 -245 565 585" role="img" aria-label="김말순 님 원룸. 조명, 에어컨, 블라인드를 누를 수 있어요.">
  <defs>
    <linearGradient id="sky-morning" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffd3a6"/><stop offset="1" stop-color="#cfe7ff"/></linearGradient>
    <linearGradient id="sky-noon" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8fd0ff"/><stop offset="1" stop-color="#e6f6ff"/></linearGradient>
    <linearGradient id="sky-evening" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1c2550"/><stop offset="1" stop-color="#4b3d78"/></linearGradient>
    <radialGradient id="pool"><stop offset="0" stop-color="#fff1b8" stop-opacity=".85"/><stop offset="1" stop-color="#fff1b8" stop-opacity="0"/></radialGradient>
    <radialGradient id="halo"><stop offset="0" stop-color="#fff4c2" stop-opacity=".9"/><stop offset="1" stop-color="#fff4c2" stop-opacity="0"/></radialGradient>
  </defs>
  <g id="scene">
    ${box(0, 0, -0.6, ROOM_X, ROOM_Y, 0.6, c.slab)}
    ${poly(floor, c.floor)}
    ${planks}
    ${poly([[3.6, 4.4, 0.01], [7.8, 4.4, 0.01], [7.8, 7.8, 0.01], [3.6, 7.8, 0.01]], c.rug)}
    ${poly([[0, 2.2, 0.01], [0, 5.6, 0.01], [2.6, 6.6, 0.01], [2.6, 3.2, 0.01]], "#fff1bd", 'id="sun-patch" opacity="0"')}
    <ellipse id="lamp-pool" cx="${px}" cy="${py}" rx="190" ry="105" fill="url(#pool)" opacity="0"/>

    <!-- 벽 A (오른쪽 뒤) -->
    ${poly([[-WALL, 0, 0], [ROOM_X, 0, 0], [ROOM_X, 0, ROOM_H], [-WALL, 0, ROOM_H]], c.wallA)}
    ${poly([[ROOM_X, -WALL, 0], [ROOM_X, 0, 0], [ROOM_X, 0, ROOM_H], [ROOM_X, -WALL, ROOM_H]], c.wallAEnd)}
    ${poly([[-WALL, -WALL, ROOM_H], [ROOM_X, -WALL, ROOM_H], [ROOM_X, 0, ROOM_H], [-WALL, 0, ROOM_H]], c.wallATop)}
    ${poly([[0, 0.01, 0], [ROOM_X, 0.01, 0], [ROOM_X, 0.01, 0.25], [0, 0.01, 0.25]], c.baseA)}
    <!-- 벽 B (왼쪽 뒤) -->
    ${poly([[0, 0, 0], [0, ROOM_Y, 0], [0, ROOM_Y, ROOM_H], [0, 0, ROOM_H]], c.wallB)}
    ${poly([[-WALL, ROOM_Y, 0], [0, ROOM_Y, 0], [0, ROOM_Y, ROOM_H], [-WALL, ROOM_Y, ROOM_H]], c.wallBEnd)}
    ${poly([[-WALL, 0, ROOM_H], [0, 0, ROOM_H], [0, ROOM_Y, ROOM_H], [-WALL, ROOM_Y, ROOM_H]], c.wallBTop)}
    ${poly([[0.01, 0, 0], [0.01, ROOM_Y, 0], [0.01, ROOM_Y, 0.25], [0.01, 0, 0.25]], c.baseB)}

    <!-- 창문 + 블라인드 -->
    <g data-device="3" tabindex="0" role="button" aria-label="블라인드 올리고 내리기">
      ${poly([[0.01, 2.0, 2.0], [0.01, 5.8, 2.0], [0.01, 5.8, 5.0], [0.01, 2.0, 5.0]], c.trim)}
      ${poly([[0.02, 2.2, 2.2], [0.02, 5.6, 2.2], [0.02, 5.6, 4.8], [0.02, 2.2, 4.8]], "url(#sky-noon)", 'id="glass"')}
      <circle id="moon" cx="${mx}" cy="${my}" r="7" fill="#fff6d6" opacity="0"/>
      <circle id="sun-disc" cx="${sx}" cy="${sy}" r="9" fill="#ffb86b" opacity="0"/>
      ${line([0.03, 3.9, 2.2], [0.03, 3.9, 4.8], c.trim, 3)}
      ${line([0.03, 2.2, 3.5], [0.03, 5.6, 3.5], c.trim, 3)}
      ${box(0, 2.0, 1.85, 0.35, 3.8, 0.15, c.trim)}
      <g id="blind-cloth"></g>
      ${box(0, 2.0, 4.85, 0.3, 3.8, 0.28, c.valance)}
    </g>
    <!-- 액자 -->
    ${poly([[0.01, 6.6, 3.0], [0.01, 7.7, 3.0], [0.01, 7.7, 4.0], [0.01, 6.6, 4.0]], c.frame)}
    ${poly([[0.02, 6.75, 3.15], [0.02, 7.55, 3.15], [0.02, 7.55, 3.85], [0.02, 6.75, 3.85]], c.picture)}

    <!-- 문 -->
    ${poly([[5.9, 0.01, 0], [7.2, 0.01, 0], [7.2, 0.01, 4.4], [5.9, 0.01, 4.4]], c.door)}
    ${poly([[6.05, 0.02, 2.5], [7.05, 0.02, 2.5], [7.05, 0.02, 4.2], [6.05, 0.02, 4.2]], c.doorPanel)}
    <circle cx="${P(7.0, 0.02, 2.1)[0]}" cy="${P(7.0, 0.02, 2.1)[1]}" r="3" fill="${c.trim}"/>

    <!-- 가구 (뒤에서 앞 순서) -->
    ${box(0, 0, 0, 2.4, 1.3, 5.0, c.wardrobe)}
    ${line([1.2, 1.31, 0.15], [1.2, 1.31, 4.85], c.wardrobeLine, 1.5)}
    ${line([1.0, 1.31, 2.3], [1.0, 1.31, 2.9], c.handle, 2.5)}
    ${line([1.4, 1.31, 2.3], [1.4, 1.31, 2.9], c.handle, 2.5)}
    ${plant(2.55, 0.3, 4, c)}
    ${box(7.4, 0, 0, 1.3, 1.1, 2.1, c.dresser)}
    ${poly([[7.65, 0.2, 2.11], [8.45, 0.2, 2.11], [8.45, 0.8, 2.11], [7.65, 0.8, 2.11]], c.dresserTop)}
    ${box(8.75, 0, 0, 1.2, 1.2, 3.8, c.trim)}
    ${line([8.85, 1.21, 2.6], [9.85, 1.21, 2.6], c.fridgeLine, 1.5)}
    ${box(0.1, 4.6, 0, 1.0, 1.0, 1.2, c.nightstand)}
    ${box(0, 5.8, 0, 0.25, 3.2, 2.0, c.headboard)}
    ${box(0.25, 5.8, 0, 4.0, 3.2, 0.7, c.bedFrame)}
    ${box(0.3, 5.9, 0.7, 3.9, 3.0, 0.45, c.trim)}
    ${box(0.45, 6.3, 1.15, 0.9, 2.2, 0.28, c.pillow)}
    ${box(1.6, 5.85, 0.72, 2.7, 3.1, 0.5, c.blanket)}
    ${box(4.7, 6.75, 0, 0.9, 0.9, 0.2, c.cushion)}
    ${box(6.75, 5.2, 0, 0.9, 0.9, 0.2, c.cushion)}
    ${box(4.6, 5.0, 0, 1.9, 1.4, 0.75, c.table)}
    ${box(5.3, 5.4, 0.75, 0.3, 0.3, 0.3, c.trim)}
    ${plant(8.6, 7.6, 9, c)}

    <!-- 에어컨: 옷장(x ≤ 2.4)보다 앞(x ≥ 2.9)이고 바람은 방 안으로 나오니 가구 뒤에 그린다 -->
    <g data-device="2" tabindex="0" role="button" aria-label="에어컨 켜고 끄기">
      ${box(2.9, 0, 4.7, 2.6, 0.75, 0.95, c.trim)}
      ${line([3.1, 0.76, 4.85], [5.3, 0.76, 4.85], c.acLine, 2)}
      <circle id="ac-led" cx="${P(5.25, 0.76, 5.45)[0]}" cy="${P(5.25, 0.76, 5.45)[1]}" r="2.6" fill="${c.acLine}"/>
      <g id="ac-wind" class="ac-wind" opacity="0">${wind}</g>
    </g>

    <!-- 조명 (가장 위) -->
    <g data-device="1" tabindex="0" role="button" aria-label="조명 켜고 끄기">
      <line x1="${lx}" y1="${P(LAMP[0], LAMP[1], 6.2)[1]}" x2="${lx}" y2="${ly - 12}" stroke="${c.cord}" stroke-width="1.5"/>
      <circle id="lamp-halo" cx="${lx}" cy="${ly + 10}" r="70" fill="url(#halo)" opacity="0"/>
      <path d="M${lx - 12} ${ly - 12} L${lx + 12} ${ly - 12} L${lx + 24} ${ly + 6} L${lx - 24} ${ly + 6} Z" fill="${c.shade}"/>
      <ellipse id="lamp-bulb" cx="${lx}" cy="${ly + 6}" rx="24" ry="6" fill="${c.bulbOff}"/>
      <rect x="${lx - 30}" y="${ly - 20}" width="60" height="40" fill="transparent"/>
    </g>
  </g>
  <g id="pins"></g>
</svg>`;
  }

  function blindMarkup(open, c) {
    const top = 4.85, full = 2.75;
    const bottom = top - full * (1 - open / 100);
    let s = "";
    if (top - bottom > 0.02) {
      s += poly([[0.12, 2.1, top], [0.12, 5.7, top], [0.12, 5.7, bottom], [0.12, 2.1, bottom]], c.cloth);
      for (let z = top - 0.2; z > bottom + 0.05; z -= 0.2) s += line([0.12, 2.1, z], [0.12, 5.7, z], c.clothLine, 1.2);
    }
    s += poly([[0.14, 2.05, bottom], [0.14, 5.75, bottom], [0.14, 5.75, bottom - 0.1], [0.14, 2.05, bottom - 0.1]], c.clothBar);
    return s;
  }

  const TIMES = {
    morning: { base: 0.94, sun: 0.55 },
    noon: { base: 1.0, sun: 0.4 },
    evening: { base: 0.5, sun: 0 },
  };

  function mount(container, { onDevice, theme }) {
    let c = PALETTES[theme] || PALETTES.rose;
    let last = null;
    container.innerHTML = build(c);
    const $ = id => container.querySelector("#" + id);

    // 테마를 바꾸면 SVG를 새로 그리므로 이벤트는 바깥 컨테이너에 건다
    container.addEventListener("click", e => {
      const g = e.target.closest("[data-device]");
      if (g) onDevice(Number(g.dataset.device));
    });
    container.addEventListener("keydown", e => {
      const g = e.target.closest("[data-device]");
      if (g && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); onDevice(Number(g.dataset.device)); }
    });

    function pin(anchor, on, label, value) {
      const [x, y] = P(...anchor);
      return `<g class="pin" data-x="${x}" data-y="${y}"><rect y="-14" height="28" rx="14"/>` +
        `<circle cx="15" cy="0" r="4.5" fill="${on ? "#1f9b57" : "#e0661e"}"/>` +
        `<text x="26" y="4.5"><tspan class="pin-label">${label}</tspan>` +
        (value ? `<tspan class="pin-val" dx="5">${value}</tspan>` : "") + `</text></g>`;
    }

    function update(state, opts) {
      last = [state, opts];
      const { time, showPins } = opts;
      const { light, ac, blind } = state;
      const t = TIMES[time];
      const lamp = light.on ? S.devices.LIGHT_LEVELS[light.level] / 100 : 0;
      const evening = time === "evening";

      let b = t.base - (1 - blind.open / 100) * (evening ? 0 : 0.12) + lamp * (evening ? 0.5 : 0.08);
      b = Math.min(1.05, Math.max(0.42, b));
      $("scene").style.filter = `brightness(${b.toFixed(3)}) saturate(${(0.8 + 0.2 * b).toFixed(3)})`;

      $("glass").setAttribute("fill", `url(#sky-${time})`);
      $("moon").setAttribute("opacity", evening ? 1 : 0);
      $("sun-disc").setAttribute("opacity", time === "morning" ? 0.9 : 0);
      $("sun-patch").setAttribute("opacity", (t.sun * blind.open / 100).toFixed(2));
      $("blind-cloth").innerHTML = blindMarkup(blind.open, c);

      $("lamp-bulb").setAttribute("fill", light.on ? "#ffe9a3" : c.bulbOff);
      $("lamp-halo").setAttribute("opacity", lamp.toFixed(2));
      $("lamp-pool").setAttribute("opacity", (lamp * (evening ? 0.75 : 0.35)).toFixed(2));

      $("ac-led").setAttribute("fill", ac.on ? "#35c27a" : c.acLine);
      const windEl = $("ac-wind");
      windEl.setAttribute("opacity", ac.on ? 1 : 0);
      windEl.style.setProperty("--wind-speed", ["1.9s", "1.1s", "0.6s"][ac.wind]);

      const pins = $("pins");
      if (!showPins) { pins.innerHTML = ""; return; }
      const pct = S.devices.LIGHT_LEVELS[light.level];
      pins.innerHTML =
        pin([0, 3.9, 5.75], blind.open > 0, blind.open > 0 ? "블라인드 열림" : "블라인드 닫힘", blind.open > 0 ? `${blind.open}%` : "") +
        pin([4.2, 0.4, 6.4], ac.on, ac.on ? "에어컨 켜짐" : "에어컨 꺼짐", ac.on ? `${ac.target}°C` : "") +
        pin([LAMP[0], LAMP[1], 3.75], light.on, light.on ? "조명 켜짐" : "조명 꺼짐", light.on ? `${pct}%` : "");
      pins.querySelectorAll(".pin").forEach(g => {
        const w = g.querySelector("text").getBBox().width + 38;
        g.querySelector("rect").setAttribute("width", w);
        g.setAttribute("transform", `translate(${(g.dataset.x - w / 2).toFixed(1)},${g.dataset.y})`);
      });
    }

    function setTheme(name) {
      c = PALETTES[name] || PALETTES.rose;
      container.innerHTML = build(c);
      if (last) update(...last);
    }

    return { update, setTheme };
  }

  S.room = { mount, THEMES: Object.keys(PALETTES) };
})(window.SixSense);

