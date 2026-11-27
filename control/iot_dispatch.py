"""SIX-SENSE 제어 계층 프로토타입: LLM 호출 문자열 -> 기기 명령 메시지 (MQTT 형식 설계 검증용)
- LLM 출력(<six_0>/<six_1>/<six_2> 호출)을 해석해 기기별 명령 메시지를 만든다.
- 정의되지 않은 호출(예: object=4)은 실행하지 않고 거부한다. (파서 방어)
- 메시지는 MQTT 토픽/페이로드 형태로만 만들고, 실제 발행은 하지 않는다. (보드 연동은 보드 수령 후)
"""
import re, json

CALL = re.compile(r'<six_([012])>\(([^)]*)\)<six_end>')
DEVICE = {1: 'light', 2: 'aircon', 3: 'blind'}
# 호출 종류별 (object, type) -> 명령.  six_0 = 기본 동작, six_1 = 조절
CMD = {
    0: {(1, 1): 'power_on', (1, 2): 'power_off',
        (2, 1): 'power_on', (2, 2): 'power_off',
        (3, 1): 'up', (3, 2): 'down', (3, 3): 'stop', (3, 4): 'half_down'},
    1: {(1, 1): 'brighter', (1, 2): 'dim',
        (2, 1): 'temp_up', (2, 2): 'temp_down', (2, 3): 'fan_up', (2, 4): 'fan_down'},
}

def parse(text):
    """호출 문자열 -> 호출 목록. 형식이 어긋나는 부분이 남으면 전체 거부."""
    text = text.strip()
    calls, pos = [], 0
    for m in CALL.finditer(text):
        if m.start() != pos:
            return None
        pos = m.end()
        kind = int(m.group(1)); args = dict(a.strip().split('=') for a in m.group(2).split(',') if a.strip())
        calls.append((kind, {k: int(v) for k, v in args.items()}))
    return calls if calls and pos == len(text) else None

def dispatch(text, room='room1'):
    """호출 문자열 -> 실행 결과. 반환: dict(status, messages, reason)"""
    calls = parse(text)
    if calls is None:
        return dict(status='reject', messages=[], reason='형식 오류')
    msgs = []
    for kind, a in calls:
        if kind == 2:                                   # 해당 없음
            return dict(status='ignore', messages=[], reason='해당 없음')
        if kind == 0 and a.get('object') == -1:         # 되묻기 (어떤 기기인지 불분명)
            return dict(status='clarify', messages=[], reason='기기 불분명, 사용자에게 되묻기')
        key = (a.get('object'), a.get('type'))
        cmd = CMD.get(kind, {}).get(key)
        if cmd is None:                                 # 정의되지 않은 호출은 실행하지 않는다
            return dict(status='reject', messages=[], reason=f'정의되지 않은 호출 {key}')
        msgs.append(dict(topic=f'sixsense/{room}/{DEVICE[key[0]]}/set', payload=dict(cmd=cmd), qos=1))
    return dict(status='execute', messages=msgs, reason='')

if __name__ == '__main__':
    for t in ['<six_0>(object=2, type=2)<six_end>',
              '<six_0>(object=3, type=1)<six_end><six_0>(object=1, type=1)<six_end>',
              '<six_0>(object=-1)<six_end>', '<six_2>()<six_end>',
              '<six_0>(object=4, type=1)<six_end>', '켜겠습니다']:
        print(t, '->', json.dumps(dispatch(t), ensure_ascii=False))
