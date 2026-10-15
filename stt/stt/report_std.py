# -*- coding: utf-8 -*-
"""eval_std.py summary + 오인식 사전 → 문서/평가지표/SIX-SENSE_STT_표준어평가.md
실행: python stt\\report_std.py
"""
import json
import os

SUM = r"학습데이터/std/eval/summary.json"
OUT = r"문서/평가지표/SIX-SENSE_STT_표준어평가.md"
MODELS = ["원본", "tts_a1"]
# 2026-10-15 평가 결과를 보고 쓴 정리 (숫자를 다시 뽑으면 같이 확인할 것)
FINDINGS = """## 0. 핵심 정리

1. **원본 SenseVoice는 깨끗한 표준어에 강하다.** 자연스러운 TTS(SAPI) 기준 일반 문장 CER 4.2%, 블라인드·에어컨·조명 명령 5.2%,
   기기명 인식 94.5%, 동작 인식 85.9%. VITS 목소리 CER(약 20%)은 대부분 TTS 발음이 뭉개진 탓이라 STT 성능으로 보면 안 된다.
2. **tts_a1(경상도 파인튜닝)은 표준어에서 나빠졌다.** SAPI 기준 일반 4.2% → 8.1%, 도메인 5.2% → 7.2%.
   표준어 문장에 사투리 어미를 붙인다("했어"→"했소", "있어"→"있소", "올려 줘"→"올려주소", "볼 거니까"→"볼 꺼니까").
   숫자는 tts_a1이 낫다(도메인 SAPI 67% → 87%, "퍼센트"를 덜 빠뜨림). 표준어 사용자가 섞이면 원본+사전 보정이 더 안전하다.
3. **켜↔꺼, 올려↔내려가 서로 뒤바뀌는 오류는 거의 없다** (원본 6,000개 중 1건). 대신 형태가 깨진다:
   - **꺼 → 거**: SAPI 음성에서 "꺼"가 들어간 문장 278개 중 274개가 "거"로 나옴 ("거 줘", "거라", "거 지게", "거졌어")
   - **줘 → 죠 / 져 / 쳐**: "켜 줘" → "켜 죠", "맞춰 줘" → "맞춰 져"
4. **된소리·거센소리 혼동이 기기·방 이름을 깨뜨린다.** 블라인드→**플**라인드, 안방→안**팡**, 작은방→작은 **빵**, 큰방→**근**방/**금**방,
   형광등→**현**광등, 밝기→**발**기, 전구→전**국**/전**고**, 틀어→**들**어, 서재→서**제**, 부엌→부**업**, 세기→**색**이
5. **ITN(숫자 변환) 부작용**: "세게" → "3 개", "오십 퍼센트로" → "50 로"(**퍼센트가 빠짐**). 블라인드·밝기 문맥의 "숫자 + 로/만"은 퍼센트로 해석해야 함.
6. **반복 출력**: "저녁은" → "저녁녁은", "보내" → "보내 내", "끝까지" → "끝까지 까지".

### LLM 쪽 반영 제안
- `문서/평가지표/STT_오인식사전_LLM용_원본.json`의 `치환표`를 프롬프트(또는 전처리 치환)에 넣는다. `문맥주의`에 있는 표기(예: "거", "금방")는 명령 문맥일 때만 바꾼다.
- 위 3~5번을 규칙으로 프롬프트에 명시: "기기 명령에서 '거'는 '꺼(끄다)'", "블라인드/밝기 뒤 숫자+로는 퍼센트", "'3 개'는 '세게'".
- LLM 학습 데이터에 STT 출력 그대로의 문장(`학습데이터/std/eval/원본_domain.csv`의 hyp 열)을 섞으면 보정 능력이 생긴다 (마음AI 데이터 시트 방식).

### 한계
- 사람 목소리가 아니라 TTS 2종(여성 1명씩). 실제 사람·소음·원거리 마이크에서는 더 나쁠 것. 사전은 실제 녹음으로 보완해야 함.
- "바로치환"은 이 평가 문장 4,000개에 정상 표기로 안 나왔다는 뜻일 뿐 — "모래"(→모레)처럼 일반 단어일 수 있음.
"""


def pct(v):
    return "-" if v is None else f"{v:.1%}"


def main():
    s = json.load(open(SUM, encoding="utf-8"))
    dicts = {m: json.load(open(f"문서/평가지표/STT_오인식사전_{m}.json", encoding="utf-8"))
             for m in MODELS if os.path.exists(f"문서/평가지표/STT_오인식사전_{m}.json")}
    L = ["# SIX-SENSE STT 표준어 성능 평가 (SenseVoice)", "",
         "`stt/std_sentences.py` → `make_std_tts.py` → `eval_std.py` → `build_confusion_dict.py` → `report_std.py` 로 재현.", "",
         FINDINGS, "## 1. 평가 방법", "",
         "| 항목 | 내용 |", "|---|---|",
         "| 일반 문장 | 1,000문장: 일상 대화 · 질문 · 감정 · 비서 명령(알람·날씨·문자·일정…). 블라인드·에어컨·조명 어휘 제외 |",
         "| 도메인 문장 | 3,000문장: 블라인드·에어컨·조명 명령(켜라/꺼라/줘/주세요…) · 상황+명령 · 질문 · 복합 · 예약 |",
         "| 음성 | 문장마다 TTS 2개: **SAPI**(Windows Heami, 자연스러운 발음) · **VITS**(kss_low, 기존 tts_a1 학습에 쓴 목소리) → 총 8,000개 |",
         "| 모델 | 원본(sherpa-onnx SenseVoice int8, 보드와 동일) · tts_a1(경상도 파인튜닝) |",
         "| CER | 공백·문장부호 빼고 글자 오류율. 숫자는 \"이십육 도\"/\"26도\" 둘 다 정답 인정 |",
         "| 키워드 인식률 | 기기명(블라인드·에어컨·불/조명류) · 동작(켜·꺼·올려·제습…) · 숫자가 출력에 그대로 있는 비율 |", ""]
    L += ["## 2. 전체 결과", "",
          "| 모델 | 문장셋 | 목소리 | 개수 | CER | 완전일치 | CER<10% 문장 | 기기 | 동작 | 숫자 |",
          "|---|---|---|---|---|---|---|---|---|---|"]
    for m in MODELS:
        for setname in ("general", "domain"):
            res = s.get(f"{m}/{setname}")
            if not res:
                continue
            for key, label in (("voice=sapi", "SAPI"), ("voice=vits", "VITS"), ("전체", "**합계**")):
                t = res[key]
                L.append(f"| {m} | {'일반' if setname == 'general' else '도메인'} | {label} | {t['n']} | {pct(t['CER'])} | "
                         f"{pct(t['완전일치'])} | {pct(t['CER<10%'])} | {pct(t.get('device_acc'))} | "
                         f"{pct(t.get('action_acc'))} | {pct(t.get('number_acc'))} |")
    L += ["", "속도(CPU 8스레드, 이 PC): " + ", ".join(
        f"{k} RTF {v['전체']['RTF']} ({v['전체']['ms_per_utt']}ms/문장)" for k, v in s.items()), ""]
    L += ["## 3. 문장 종류별 CER", "", "| 문장셋 | 종류 | 개수 | " + " | ".join(MODELS) + " |",
          "|---|---|---|" + "---|" * len(MODELS)]
    for setname in ("general", "domain"):
        base = s[f"{MODELS[0]}/{setname}"]
        for key in sorted(k for k in base if k.startswith("group=")):
            cells = [pct(s[f"{m}/{setname}"][key]["CER"]) if f"{m}/{setname}" in s else "-" for m in MODELS]
            L.append(f"| {'일반' if setname == 'general' else '도메인'} | {key[6:]} | {base[key]['n']} | " + " | ".join(cells) + " |")
    L.append("")
    for m, d in dicts.items():
        L += [f"## 4-{m}. 자주 틀리는 표현 — {m} 모델 (`문서/평가지표/STT_오인식사전_{m}.json`)", "",
              f"전체 {len(d['상세'])}쌍 (2회 이상) / 바로치환 {len(d['바로치환'])} / 문맥확인 {len(d['문맥확인'])}", "",
              "### 블라인드·에어컨·조명 관련 (신뢰도 높음·중간)", "", "| 오인식 | 정답 | 횟수 | SAPI | VITS | 구분 |", "|---|---|---|---|---|---|"]
        dom = [e for e in d["상세"] if e["도메인어휘"] and e["신뢰도"] != "낮음_VITS만"][:45]
        for e in dom:
            L.append(f"| {e['오인식']} | {e['정답']} | {e['횟수']} | {e['목소리'].get('sapi', 0)} | "
                     f"{e['목소리'].get('vits', 0)} | {e['구분']} |")
        L += ["", "### 일반 표현 (신뢰도 높음·중간)", "", "| 오인식 | 정답 | 횟수 | SAPI | VITS | 구분 |", "|---|---|---|---|---|---|"]
        gen = [e for e in d["상세"] if not e["도메인어휘"] and e["신뢰도"] != "낮음_VITS만"][:30]
        for e in gen:
            L.append(f"| {e['오인식']} | {e['정답']} | {e['횟수']} | {e['목소리'].get('sapi', 0)} | "
                     f"{e['목소리'].get('vits', 0)} | {e['구분']} |")
        L.append("")
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")
    print(OUT)


if __name__ == "__main__":
    main()
