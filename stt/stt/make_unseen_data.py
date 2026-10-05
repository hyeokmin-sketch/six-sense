# -*- coding: utf-8 -*-
"""엑셀에 없는 새 문장(아예 처음 보는 데이터) → TTS wav + 학습데이터/unseen/unseen.jsonl.

- 그룹: 경상도_기기(새 표현) / 경상도_일상(기기 무관) / 표준어
- TTS 변형도 학습에 안 쓴 값 2개 사용. 엑셀 문장과 완전히 같은 문장이 있으면 중단.
실행: .venv\\Scripts\\python.exe stt\\make_unseen_data.py
"""
import json
import os
import sys
import wave

import openpyxl

sys.path.insert(0, os.path.dirname(__file__))
from eval_cer import edit, norm  # noqa: E402
from make_tts_data import XLSX, save16k, tts_engine  # noqa: E402

OUT = r"학습데이터/unseen"
VARIANTS = [(1.05, 0.75, 0.85), (0.92, 0.55, 0.7)]
SENTS = {
    "경상도_기기": [
        "안방 불 좀 키주소", "화장실 불 꺼뒀나", "불 키놓고 나갔제", "테레비 볼 때는 불 쪼매 낮차도",
        "와 이리 불이 번쩍번쩍하노", "방이 억수로 어둡데이", "불 좀 밝히주이소", "침대 옆에 불만 키도",
        "블라인드 와 안 올라가노", "블라인드 좀 걷어주이소", "햇빛 땜에 모니터가 하나도 안 보인데이",
        "블라인드 쪼매만 더 내리봐라", "밖에 비 오나 블라인드 열어봐라", "아까 그 블라인드 우째 됐노",
        "에어컨 몇 도로 돼 있노", "에어컨 바람 쫌 위로 올리라", "에어컨 제습으로 돌리도",
        "방이 꿉꿉하다 에어컨 좀 키바라", "오늘따라 와 이리 후덥지근하노", "땀띠 나겠다 시원하게 해도",
        "에어컨 바람이 억수로 차갑데이", "에어컨 고마 끄고 문 열자", "잘 때는 에어컨 약하게 해놔라",
        "무릎이 시리다 에어컨 쫌 줄이라", "나 씻고 올 동안 에어컨 키놔라", "손님 온다 불 키고 에어컨 틀어라",
        "자기 전에 불이랑 에어컨 다 끄래이", "아침에 블라인드 올리고 불 꺼도", "집에 아무도 없다 다 꺼삐라",
        "부엌 불 좀 키주라",
    ],
    "경상도_일상": [
        "밥 뭇나", "니 어데 가노", "내 오늘 억수로 피곤타", "그거 머라카노", "와 이래 늦게 왔노",
        "아이고 마 됐다", "내일 비 온다 카더라", "니 그거 했나 안 했나", "오늘 장 보러 가야 된데이",
        "할매 집에 계시나", "우리 아들 언제 오노", "그래가 우째 됐는데", "단디 해라", "쪼매만 기다리래이",
        "이거 얼마고",
    ],
    "표준어": [
        "거실 조명 좀 켜 줘", "불 좀 꺼 줄래", "블라인드 올려 줘", "블라인드를 반만 내려 주세요", "에어컨 켜 줘",
        "에어컨 온도 좀 낮춰 줄래", "바람 세기를 약하게 해 줘", "너무 더워서 잠이 안 와", "오늘 날씨 어때",
        "내일 아침에 깨워 줘", "방이 좀 어두운 것 같아", "에어컨이랑 불 다 꺼 줘", "외출할 거니까 다 꺼 줘",
        "햇빛이 너무 강해서 눈이 부셔", "창문 좀 닫아 줄래",
    ],
}


def main():
    ws = openpyxl.load_workbook(XLSX, read_only=True)["데이터셋"]
    known = [norm(str(r[1])) for r in ws.iter_rows(min_row=2, values_only=True) if r[1]]
    for g, ss in SENTS.items():
        for s in ss:
            n = norm(s)
            near = min(known, key=lambda k: edit(n, k))
            if edit(n, near) == 0:
                sys.exit(f"엑셀에 이미 있는 문장: {s}")
    os.makedirs(os.path.join(OUT, "wav"), exist_ok=True)
    recs = []
    for v, (ls, ns, nw) in enumerate(VARIANTS):
        tts = tts_engine(ls, ns, nw)
        i = 0
        for g, ss in SENTS.items():
            for s in ss:
                i += 1
                key = f"U{i:03d}_v{v}"
                path = os.path.abspath(os.path.join(OUT, "wav", key + ".wav"))
                if not os.path.exists(path):
                    a = tts.generate(s, sid=0, speed=1.0)
                    save16k(path, a.samples, a.sample_rate)
                with wave.open(path) as w:
                    n = w.getnframes()
                recs.append({"key": key, "source": path, "source_len": n // 160, "target": s,
                             "target_len": len(s), "group": g, "sent_id": i})
    with open(os.path.join(OUT, "unseen.jsonl"), "w", encoding="utf-8") as f:
        for x in recs:
            f.write(json.dumps(x, ensure_ascii=False) + "\n")
    print("unseen", len(recs), "utts")


if __name__ == "__main__":
    main()
