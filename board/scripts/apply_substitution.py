"""STT 결과 -> 치환 사전 적용 -> LLM 입력.  사용: python scripts/apply_substitution.py "고맙거라"
사전: data/substitution_dict.json (STT 후보 424개 중 승인 106쌍, TTS 음성 기준. 실제 사람 음성으로 재검증 필요)"""
import json, re, os, sys
_H = os.path.dirname(os.path.abspath(__file__))
D = json.load(open(os.path.join(_H, "..", "data", "substitution_dict.json"), encoding="utf-8"))["supertonic_v1"]
# 공백 무시 매칭: STT가 "좀 꺼삐라"처럼 띄어 써도 사전 키 "좀꺼삐라"에 걸리도록 글자 사이에 공백을 허용한다. 긴 키 먼저.
_PAT = [(re.compile(r"\s*".join(map(re.escape, k))), v) for k, v in sorted(D.items(), key=lambda kv: -len(kv[0]))]

def apply(text):
    for p, v in _PAT:
        text = p.sub(v, text)
    return re.sub(r"\s+", " ", text).strip()

if __name__ == "__main__":
    print(apply(" ".join(sys.argv[1:])))
