// IF-3: 호출 문자열 ↔ [{tag, object, type}]
(function (S) {
  "use strict";

  // 서버가 특수 토큰을 지워서 <six_end>가 빠지는 경우도 받는다
  const CALL_RE = /<(six_[0-9]+)>\(([^)]*)\)(?:<six_end>)?/g;

  // 형식이 하나라도 어긋나면 E01 (화면에서는 되묻기로 처리)
  function parseCalls(text) {
    const src = String(text || "").trim();
    const calls = [];
    let rest = src;
    for (const m of src.matchAll(CALL_RE)) {
      const args = {};
      for (const part of m[2].split(",")) {
        const [k, v] = part.split("=").map(s => s.trim());
        if (!k) continue;
        if (v === undefined || !/^-?\d+$/.test(v)) return { calls: [], error: "E01" };
        args[k] = Number(v);
      }
      calls.push({ tag: m[1], object: args.object, type: args.type });
      rest = rest.replace(m[0], "");
    }
    if (!calls.length || rest.trim()) return { calls: [], error: "E01" };
    return { calls, error: null };
  }

  function formatCall(call) {
    if (call.tag === "six_2") return "<six_2>()<six_end>";
    if (call.object === -1) return "<six_0>(object=-1)<six_end>";
    return `<${call.tag}>(object=${call.object}, type=${call.type})<six_end>`;
  }

  S.parser = { parseCalls, formatCall };
})(window.SixSense);

