"""SIX-SENSE 채점 코드 (Colab 노트북의 score / lenient 와 같은 기준)
사용법:
  python score.py original_preds_llama_v2dict3.csv                 # 원본 점수표
  python score.py original_preds_llama_v2dict3.csv quant_preds.csv  # 원본 vs 양자화 비교표
CSV 열: set, id, text, gold, pred   (original_preds 파일에는 correct 열도 있음)
- 정확 일치: pred == gold (문자열이 완전히 같아야 함)
- 형식 준수: pred가 호출 형식 정규식과 일치
- 조작 오류(오작동): gold가 해당 없음(<six_2>()<six_end>)인데 pred가 기기를 조작하는 호출을 냄
- 앞부분 인정(lenient): pred 맨 앞 호출들이 gold와 같으면 인정
※ pred 는 모델 출력을 decode(skip_special_tokens=True) 후 strip() 한 문자열이어야 합니다.
"""
import re, sys, csv, collections

VALID = re.compile(r'^(?:<six_0>\(object=[1-3], type=[1-4]\)<six_end>|<six_1>\(object=[1-3], type=[1-4]\)<six_end>|<six_0>\(object=-1\)<six_end>|<six_2>\(\)<six_end>)+$')
CTRL = re.compile(r'<six_[01]>\(object=[1-3]')            # 기기를 실제로 조작하는 호출
LEAD = re.compile(r'^((?:<six_[012]>\([^)]*\)<six_end>)+)')
NA = '<six_2>()<six_end>'

def score_rows(rows):
    """rows: [{'gold':..., 'pred':...}] -> dict"""
    n = len(rows); em = fmt = lead = nn = mis = 0
    for r in rows:
        g, p = r['gold'], r['pred'].strip()
        em += (p == g); fmt += bool(VALID.match(p))
        m = LEAD.match(p); lead += bool(m) and m.group(1) == g
        if g == NA:
            nn += 1; mis += bool(CTRL.search(p))
    return dict(n=n, exact=em / max(n, 1), fmt=fmt / max(n, 1), lead=lead / max(n, 1), mis=mis, na=nn)

def load(path):
    with open(path, encoding='utf-8-sig', newline='') as f:
        return list(csv.DictReader(f))

def by_set(rows):
    d = collections.OrderedDict()
    for r in rows: d.setdefault(r['set'], []).append(r)
    return d

if __name__ == '__main__':
    a = by_set(load(sys.argv[1])); b = by_set(load(sys.argv[2])) if len(sys.argv) > 2 else None
    if b is None:
        print(f'{"평가셋":<28}{"n":>5}{"정확일치":>9}{"형식":>7}{"앞부분":>8}{"조작오류":>10}')
        for k, rows in a.items():
            s = score_rows(rows); print(f'{k:<28}{s["n"]:>5}{s["exact"]:>9.1%}{s["fmt"]:>7.1%}{s["lead"]:>8.1%}{s["mis"]:>6}/{s["na"]:<4}')
    else:
        print(f'{"평가셋":<28}{"n":>5}{"원본":>8}{"양자화":>8}{"차이":>8}{"원본 조작":>10}{"양자화 조작":>12}{"원본→양자화로 바뀐 문장":>16}')
        for k, ra in a.items():
            rb = b.get(k)
            if rb is None: print(f'{k:<28} (양자화 결과에 없음)'); continue
            if [x['text'] for x in ra] != [x['text'] for x in rb]: print(f'{k}: 문장 순서/내용이 달라 비교 불가'); continue
            sa, sb = score_rows(ra), score_rows(rb)
            diff = sum(x['pred'].strip() != y['pred'].strip() for x, y in zip(ra, rb))
            print(f'{k:<28}{sa["n"]:>5}{sa["exact"]:>8.1%}{sb["exact"]:>8.1%}{sb["exact"]-sa["exact"]:>+8.1%}{sa["mis"]:>6}/{sa["na"]:<3}{sb["mis"]:>8}/{sb["na"]:<3}{diff:>12}')
