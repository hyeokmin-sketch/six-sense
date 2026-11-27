# ============================================================
# 혁민용: Colab에서 아래 두 셀을 "15번(모델 저장) 셀보다 먼저" 실행하세요.
# 1~5번 셀 실행 후(RUN_TRAIN=False, MODEL_KEY='llama', VERSION='v2dict3') → 셀 ① → 셀 ② → 15번 순서.
# ============================================================

# ---------- 셀 ① 학습 때 입력 문자열 · 종료 토큰 · 생성 설정 출력 ----------
rows_demo = [next(r for r in train if 'dict_A' in str(r.get('source'))),
             next(r for r in train if r['category'] == '복합')]
for r in rows_demo:
    print('=== jsonl 원본 한 줄 ===');  print(json.dumps(r, ensure_ascii=False))
    p = tok.apply_chat_template([{'role': 'system', 'content': SYSTEM}, {'role': 'user', 'content': r['text']}],
                                tokenize=False, add_generation_prompt=True, enable_thinking=False)
    print('=== apply_chat_template 결과 (repr: 줄바꿈 \\n 이 보이게) ==='); print(repr(p))
    print('=== 그대로 출력 ==='); print(p)
    print('=== 학습 때 정답 부분 (이 뒤에 종료 토큰이 붙음) ==='); print(repr(r['output'] + END))
open(f'{ROOT}/runs/system_prompt.txt', 'w', encoding='utf-8').write(SYSTEM)
print('\n시스템 프롬프트(원문):', SYSTEM)
print('종료 토큰:', END, '| id', EOT, '| pad 토큰:', tok.pad_token, tok.pad_token_id)
print('토크나이즈: add_special_tokens=False (템플릿 문자열에 <|begin_of_text|> 가 이미 들어 있음)')
print('생성 설정: max_new_tokens=80, do_sample=False(greedy), eos_token_id=', EOT, '| 디코딩: skip_special_tokens=True 후 strip()')
print('generation_config:', model.generation_config if hasattr(model, 'generation_config') else '-')

# ---------- 셀 ①-2 날짜 줄을 학습 때와 맞추기 (셀 ② 바로 앞에 실행) + 날짜 영향 확인(선택) ----------
# Llama 템플릿의 "Today Date"는 실행 날짜로 바뀝니다. v2dict3는 11/7에 학습했으므로 07 Nov 2026으로 고정합니다.
import functools
_orig_act = tok.apply_chat_template
DATE_TRAIN = '07 Nov 2026'
tok.apply_chat_template = functools.partial(_orig_act, date_string=DATE_TRAIN)
_t = tok.apply_chat_template([{'role': 'system', 'content': SYSTEM}, {'role': 'user', 'content': '에어컨 돌리라.'}], tokenize=False, add_generation_prompt=True)
print(_t[:120].replace('\n', ' / '))     # Today Date: 07 Nov 2026 이 보여야 함
# (선택) 날짜 영향 확인: 실제 녹음 95문장을 오늘 날짜(08 Nov 2026)로도 돌려 비교
_rows = load(find('eval_real50_dict.jsonl'))
for d_ in [DATE_TRAIN, '08 Nov 2026']:
    tok.apply_chat_template = functools.partial(_orig_act, date_string=d_)
    _p = generate(_rows); print(d_, '→ 실제 녹음(사전 적용)', f'{sum(a == r["output"] for r, a in zip(_rows, _p))}/{len(_rows)}')
tok.apply_chat_template = functools.partial(_orig_act, date_string=DATE_TRAIN)     # 이후(셀 ②)에는 학습 날짜로 고정

# ---------- 셀 ② 원본 모델의 문장별 예측 저장 (양자화 비교 기준) ----------
import pandas as pd
SETS = [('val', val), ('50대 v2', ev_clean)]
for name_, pat_ in [('20·30대 v3', 'eval_v3_20_30s.jsonl')]:      # 9번 셀을 실행하지 않았어도 동작하도록 직접 불러옴
    p_ = find(pat_)
    if p_: SETS.append((name_, [r for r in load(p_) if not r.get('needs_review')]))
    else: print(pat_, '없음 → 건너뜀')
for name_, pat_ in [('해당없음 50대검수', 'eval_v3_해당없음_50대검수.jsonl'), ('해당없음 신규물건', 'eval_해당없음_신규물건.jsonl'),
                    ('사전 holdout', 'eval_stt_dict_holdout.jsonl'), ('eval_stt_aug', 'eval_stt_aug.jsonl'),
                    ('실제 50대 녹음(사전 적용)', 'eval_real50_dict.jsonl'), ('실제 50대 녹음(사전 미적용)', 'eval_real50_raw.jsonl')]:
    p_ = find(pat_)
    if p_: SETS.append((name_, [r for r in load(p_) if not r.get('needs_review')]))
    else: print(pat_, '없음 → 건너뜀')
recs = []
for name_, rows_ in SETS:
    preds_ = generate(rows_)
    ok_ = sum(q == r['output'] for r, q in zip(rows_, preds_))
    for r, q in zip(rows_, preds_):
        recs.append(dict(set=name_, id=r.get('id'), text=r['text'], gold=r['output'], pred=q, correct=int(q == r['output'])))
    print(f'{name_}: n={len(rows_)}  정확 일치 {ok_/len(rows_):.1%}')
dfp = pd.DataFrame(recs)
outp = f'{ROOT}/runs/original_preds_{MODEL_KEY}_{VERSION}.csv'
dfp.to_csv(outp, index=False, encoding='utf-8-sig'); print('저장:', outp, '| 행 수', len(dfp))
