# -*- coding: utf-8 -*-
"""eval_std.py 결과 csv → STT 오인식 사전 ("STT가 이렇게 잘못 쓰면 원래 이 말이다").

정답·출력을 공백 빼고 글자 단위로 정렬한 뒤, 틀린 글자가 걸친 정답 어절 묶음과
그 자리에 나온 출력 조각을 (오인식 → 정답) 쌍으로 모은다. 예: "오븐 켜 줘" / "5분 켜 줘" → 5분 → 오븐
- 바로 치환: 2회 이상 나왔고, 오인식 표기가 평가 문장 4,000개 어디에도 정상 표현으로 안 나옴
- 문맥 확인: 오인식 표기가 다른 문장에선 정상 단어(예: 거↔꺼, 금방↔큰방) → LLM이 문맥으로 판단
- 신뢰도: 두 목소리 모두에서 나옴=높음 / SAPI만=중간 / VITS만=낮음(VITS 발음이 뭉개진 탓일 수 있어 치환표에서 제외)
결과: 문서/평가지표/STT_오인식사전_<모델>.json(전체), 문서/평가지표/STT_오인식사전_LLM용_<모델>.json(프롬프트용 축약)
실행: python stt\\build_confusion_dict.py [--model 원본] [--min-count 2]
"""
import argparse
import csv
import json
import os
import re
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(__file__))
from eval_std import n2  # noqa: E402

EVAL = r"학습데이터/std/eval"
SENT = r"학습데이터/std/sentences.jsonl"
OUT = r"문서/평가지표"
DROP = re.compile(r"[\s\.,!?~·…'\"]")
OPPOSITE = [{"켜", "꺼"}, {"켜", "끄"}, {"올려", "내려"}, {"올리", "내리"}, {"열어", "닫아"}, {"열", "닫"},
            {"높여", "낮춰"}, {"밝게", "어둡게"}, {"세게", "약하게"}, {"켜져", "꺼져"}]
DOMAIN_WORDS = ["블라인드", "에어컨", "조명", "전등", "형광등", "무드등", "스탠드", "전구", "불", "등",
                "켜", "꺼", "끄", "올려", "내려", "열어", "닫아", "걷어", "멈춰", "틀어", "밝기", "온도", "바람", "풍량",
                "세기", "냉방", "제습", "송풍", "취침", "절전", "자동", "모드", "회전", "방향", "예약", "반만", "끝까지",
                "세게", "약하게", "바꿔", "맞춰", "꺼지", "퍼센트",
                "방", "거실", "침실", "서재", "부엌", "주방", "현관", "베란다", "복도", "화장실"]


def chars(s):
    """정규화 글자 목록 + 각 글자의 원문 위치"""
    s = s.replace("%", "퍼센트").lower()
    return s, [(c, i) for i, c in enumerate(s) if not DROP.match(c)]


def align(a, b):
    """글자 편집거리 정렬 → [(op, i, j)] op: M 일치 S 치환 D 삭제 I 삽입"""
    n, m = len(a), len(b)
    d = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1):
        d[i][0] = i
    for j in range(m + 1):
        d[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            d[i][j] = min(d[i - 1][j - 1] + (a[i - 1] != b[j - 1]), d[i - 1][j] + 1, d[i][j - 1] + 1)
    ops, i, j = [], n, m
    while i or j:
        if i and j and d[i][j] == d[i - 1][j - 1] + (a[i - 1] != b[j - 1]):
            ops.append(("M" if a[i - 1] == b[j - 1] else "S", i - 1, j - 1)); i -= 1; j -= 1
        elif i and d[i][j] == d[i - 1][j] + 1:
            ops.append(("D", i - 1, None)); i -= 1
        else:
            ops.append(("I", None, j - 1)); j -= 1
    return ops[::-1]


def error_pairs(ref, hyp):
    """[(정답 어절 묶음, 그 자리 출력)]"""
    words = [DROP.sub("", w) for w in ref.replace("%", "퍼센트").lower().split()]
    words = [w for w in words if w]
    rc, wid = [], []
    for k, w in enumerate(words):
        rc += list(w); wid += [k] * len(w)
    h2, hc = chars(hyp)
    ops = align(rc, [c for c, _ in hc])
    anchor, last = [], 0
    for op, i, _ in ops:  # 각 연산이 걸린 정답 어절 (삽입은 앞 어절에 붙임)
        if i is not None:
            last = wid[i]
        anchor.append(last)
    spans = [[w] for w in sorted({anchor[k] for k, (op, _, _) in enumerate(ops) if op != "M"})]
    out = []
    for sp in spans:
        js = [j for k, (op, _, j) in enumerate(ops) if anchor[k] in sp and j is not None]
        hyp_txt = h2[hc[min(js)][1]:hc[max(js)][1] + 1].strip() if js else ""
        hyp_txt = re.sub(r"[\.,!?~·…'\"]", "", hyp_txt).strip()
        out.append((" ".join(words[w] for w in sp), hyp_txt))
    return out


def core(right, wrong):
    """공통 꼬리(조사·어미) 떼고 핵심만: ("켜라", "꺼라") → ("켜", "꺼")"""
    r, w = right.replace(" ", ""), wrong.replace(" ", "")
    while r and w and r[-1] == w[-1] and len(r) > 1 and len(w) > 1:
        r, w = r[:-1], w[:-1]
    return r, w


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="원본")
    ap.add_argument("--min-count", type=int, default=2)
    a = ap.parse_args()
    sents = [json.loads(l) for l in open(SENT, encoding="utf-8")]
    corpus = "\n".join(n2(s["text"]) + "\n" + n2(s.get("alt", "")) for s in sents)
    word_freq = Counter()
    rows = []
    for setname in ("general", "domain"):
        for r in csv.DictReader(open(os.path.join(EVAL, f"{a.model}_{setname}.csv"), encoding="utf-8-sig")):
            rows.append(r)
            for w in r["ref"].replace("%", "퍼센트").lower().split():
                word_freq[DROP.sub("", w)] += 1

    pairs = defaultdict(lambda: {"count": 0, "voices": Counter(), "sets": Counter(), "examples": []})
    for r in rows:
        if r["err"] == "0":
            continue
        for right, wrong in error_pairs(r["ref"], r["hyp"]):
            if not wrong or n2(wrong) == n2(right):
                continue
            p = pairs[(wrong, right)]
            p["count"] += 1
            p["voices"][r["voice"]] += 1
            p["sets"]["general" if r["id"].startswith("G") else "domain"] += 1
            if len(p["examples"]) < 3:
                p["examples"].append({"정답": r["ref"], "출력": r["hyp"]})

    entries = []
    for (wrong, right), p in pairs.items():
        if p["count"] < a.min_count:
            continue
        cr, cw = core(right, wrong)
        nw = n2(wrong)
        legit = len(nw) <= 1 or nw in corpus
        opposite = any({cr, cw} == o for o in OPPOSITE)
        total = sum(word_freq[w] for w in [right.replace(" ", "")]) or None
        # VITS(kss_low)는 발음이 뭉개지는 경우가 많아 VITS에서만 나온 오류는 TTS 탓일 수 있다
        trust = "높음" if len(p["voices"]) == 2 else ("중간" if "sapi" in p["voices"] else "낮음_VITS만")
        kind = "반대의미_주의" if opposite else ("문맥확인" if legit else "바로치환")
        if kind == "바로치환" and trust == "낮음_VITS만":
            kind = "참고_TTS의심"
        entries.append({
            "오인식": wrong, "정답": right, "핵심": f"{cw} → {cr}", "횟수": p["count"],
            "정답어절_등장": total, "오인식률": round(p["count"] / total, 3) if total and " " not in right else None,
            "목소리": dict(p["voices"]), "신뢰도": trust,
            "도메인어휘": any(dw in right for dw in DOMAIN_WORDS), "구분": kind, "예시": p["examples"]})
    entries.sort(key=lambda e: (-e["횟수"], e["정답"]))

    simple = {}
    for e in entries:  # 같은 오인식 표기가 여러 정답으로 갈리면 가장 많은 쪽만
        if e["구분"] == "바로치환" and e["오인식"] not in simple:
            simple[e["오인식"]] = e["정답"]
    ambiguous = defaultdict(list)
    for e in entries:
        ambiguous[e["오인식"]].append(e["정답"])
    result = {
        "설명": ("SenseVoice가 표준어 TTS 음성(일반 1,000 + 블라인드·에어컨·조명 3,000문장, 목소리 2개)을 인식할 때 "
                 "반복해서 틀린 표현. '바로치환'은 STT 출력에서 그대로 바꿔도 되는 것, '문맥확인'/'반대의미_주의'는 "
                 "정상 단어와 겹치므로 LLM이 문맥으로 판단해야 하는 것."),
        "모델": a.model, "최소횟수": a.min_count,
        "바로치환": simple,
        "문맥확인": [{k: e[k] for k in ("오인식", "정답", "횟수", "구분")} for e in entries
                     if e["구분"] in ("문맥확인", "반대의미_주의") and e["신뢰도"] != "낮음_VITS만"],
        "여러정답": {w: rs for w, rs in ambiguous.items() if len(rs) > 1},
        "상세": entries,
    }
    path = os.path.join(OUT, f"STT_오인식사전_{a.model}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)

    # LLM 프롬프트에 넣기 좋은 축약판: SAPI에서도 나온 것(신뢰도 높음·중간), 3회 이상, 오인식 표기당 정답 하나
    llm, ctx = {}, []
    for e in entries:
        if e["신뢰도"] == "낮음_VITS만" or e["횟수"] < 3 or e["오인식"] in llm:
            continue
        llm[e["오인식"]] = e["정답"]
        if e["구분"] != "바로치환":
            ctx.append(e["오인식"])
    with open(os.path.join(OUT, f"STT_오인식사전_LLM용_{a.model}.json"), "w", encoding="utf-8") as f:
        json.dump({"설명": f"SenseVoice({a.model}) 출력 → 원래 말. '문맥주의'에 있는 표기는 정상 단어이기도 하니 "
                           "블라인드·에어컨·조명 명령 문맥일 때만 바꿀 것.",
                   "치환표": llm, "문맥주의": ctx}, f, ensure_ascii=False, indent=1)
    print(f"{path}: 쌍 {len(entries)}개 (바로치환 {len(simple)}, 문맥확인 {len(result['문맥확인'])})")
    for e in entries[:40]:
        print(f"  {e['횟수']:4}  {e['오인식']} → {e['정답']}  [{e['구분']}/{e['신뢰도']}] {e['목소리']}")


if __name__ == "__main__":
    main()
