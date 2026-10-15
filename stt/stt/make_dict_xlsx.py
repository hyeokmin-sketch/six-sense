# -*- coding: utf-8 -*-
"""오인식 사전 json → 문서/평가지표/stt_딕셔너리.xlsx (원본단어 → 자주 틀리는 단어 표).

시트: 설명 / <모델>_정답별(원본단어 한 줄에 틀린 표기 모음) / <모델>_상세(쌍 한 줄씩, 예문 포함)
VITS 목소리에서만 나온 쌍(TTS 발음 탓 의심)과 3회 미만은 뺀다 — LLM용 json과 같은 기준.
실행: python stt\\make_dict_xlsx.py
"""
import json
from collections import defaultdict

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill

DIR = r"문서/평가지표"
MODELS = ["원본", "tts_a1"]
MIN_COUNT = 3
HEAD = PatternFill("solid", fgColor="DDEBF7")
DOMAIN = PatternFill("solid", fgColor="FFF2CC")
KIND = {"바로치환": "바로 치환", "문맥확인": "문맥 확인(정상 단어와 겹침)", "반대의미_주의": "반대 의미 주의",
        "참고_TTS의심": "참고(TTS 의심)"}


def sheet(wb, title, header, rows, widths, domain_col=None):
    ws = wb.create_sheet(title)
    ws.append(header)
    for c in ws[1]:
        c.font, c.fill = Font(bold=True), HEAD
    for r in rows:
        ws.append(r)
        if domain_col is not None and r[domain_col] == "도메인":
            for c in ws[ws.max_row]:
                c.fill = DOMAIN
    for i, w in enumerate(widths):
        ws.column_dimensions[chr(65 + i)].width = w
    for row in ws.iter_rows(min_row=2):
        for c in row:
            c.alignment = Alignment(vertical="top", wrap_text=True)
    ws.freeze_panes = "B2"
    ws.auto_filter.ref = ws.dimensions


def main():
    wb = Workbook()
    ws = wb.active
    ws.title = "설명"
    for line in [
        "SIX-SENSE STT 오인식 사전 (stt_딕셔너리)",
        "",
        "만든 방법: 표준어 문장 4,000개(일반 1,000 + 블라인드·에어컨·조명 3,000)를 TTS 2종(SAPI Heami, VITS kss)으로 읽혀",
        "SenseVoice로 인식한 뒤, 정답과 출력을 글자 단위로 맞춰 어절별로 틀린 쌍을 모음. 자세한 내용은 SIX-SENSE_STT_표준어평가.md",
        "",
        "시트",
        "  원본_정답별 / tts_a1_정답별 : 원본단어 → 자주 틀리는 단어(횟수). 보드 기본 모델은 '원본'",
        "  원본_상세 / tts_a1_상세     : 틀린 쌍 하나당 한 줄, 목소리별 횟수·신뢰도·예문",
        "",
        "열 설명",
        "  원본단어      : 원래 말한 단어(정답)",
        "  자주 틀리는 단어 : STT가 대신 출력한 표기 (띄어쓰기는 STT 출력 그대로)",
        "  횟수          : 8,000개 음성(문장 4,000 × 목소리 2) 중 나온 횟수",
        "  신뢰도        : 높음 = 두 목소리 모두에서 나옴 / 중간 = SAPI 목소리에서만",
        "  구분          : 바로 치환 = STT 출력에서 그대로 바꿔도 됨",
        "                 문맥 확인 = 정상 단어이기도 함(예: 거, 금방) → 기기 명령 문맥일 때만 바꿀 것",
        "  분류          : 도메인(블라인드·에어컨·조명 관련 단어, 노란색) / 일반",
        "",
        f"제외: 3회 미만, VITS 목소리에서만 나온 쌍(TTS 발음이 뭉개진 탓일 수 있음). 전체는 STT_오인식사전_<모델>.json",
    ]:
        ws.append([line])
    ws["A1"].font = Font(bold=True, size=14)
    ws.column_dimensions["A"].width = 110

    for m in MODELS:
        d = json.load(open(f"{DIR}/STT_오인식사전_{m}.json", encoding="utf-8"))
        es = [e for e in d["상세"] if e["횟수"] >= MIN_COUNT and e["신뢰도"] != "낮음_VITS만"]
        by = defaultdict(list)
        for e in es:
            by[e["정답"]].append(e)
        groups = sorted(by.items(), key=lambda kv: -sum(e["횟수"] for e in kv[1]))
        rows = []
        for right, g in groups:
            g.sort(key=lambda e: -e["횟수"])
            rows.append([right, ", ".join(f"{e['오인식']}({e['횟수']})" for e in g), sum(e["횟수"] for e in g),
                         "도메인" if g[0]["도메인어휘"] else "일반",
                         " / ".join(dict.fromkeys(KIND[e["구분"]] for e in g))])
        sheet(wb, f"{m}_정답별", ["원본단어", "자주 틀리는 단어 (횟수)", "총 횟수", "분류", "구분"], rows,
              [16, 60, 9, 8, 30], domain_col=3)
        rows = []
        for right, g in groups:
            for e in g:
                ex = e["예시"][0]
                rows.append([e["정답"], e["오인식"], e["횟수"], e["오인식률"], e["목소리"].get("sapi", 0),
                             e["목소리"].get("vits", 0), e["신뢰도"], KIND[e["구분"]],
                             "도메인" if e["도메인어휘"] else "일반", ex["정답"], ex["출력"]])
        sheet(wb, f"{m}_상세", ["원본단어", "자주 틀리는 단어", "횟수", "오인식률", "SAPI", "VITS", "신뢰도", "구분",
                                "분류", "예시 정답 문장", "예시 STT 출력"], rows,
              [16, 18, 7, 9, 7, 7, 8, 24, 8, 40, 40], domain_col=8)
        print(m, "정답별", len(groups), "/ 상세", len(rows))
    wb.save(f"{DIR}/stt_딕셔너리.xlsx")
    print(f"{DIR}/stt_딕셔너리.xlsx")


if __name__ == "__main__":
    main()
