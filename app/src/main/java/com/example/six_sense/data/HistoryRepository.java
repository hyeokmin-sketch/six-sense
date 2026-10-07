package com.example.six_sense.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.example.six_sense.model.Device;
import com.example.six_sense.model.HistoryEntry;
import com.example.six_sense.model.Interpretation;

/**
 * 대화 기록. 최신이 앞에 오도록 유지한다.
 *
 * 메모리 저장이라 앱 끄면 날아감. 교정 데이터가 학습에 들어가야 해서 Room DB나 최소한
 * JSON 파일로 바꿔야 하는데, 그건 데이터 포맷(혁민) 정해지면 같이 하기로.
 */
public class HistoryRepository {

    public interface Listener {
        void onHistoryChanged();
    }

    private static HistoryRepository instance;

    public static synchronized HistoryRepository getInstance() {
        if (instance == null) {
            instance = new HistoryRepository();
        }
        return instance;
    }

    private final List<HistoryEntry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private long nextId = 1;

    private HistoryRepository() {
        seedDemo();
    }

    /**
     * 데모용 기록. 발표 때 빈 화면이 안 나오게.
     * 오늘 1건(교정 가능), 어제 교정된 것 2건 + 보통 1건, 그제 1건.
     * 시각은 "지금 기준 n시간 전"이라 날짜가 바뀌어도 오늘/어제 구분이 유지된다.
     */
    private void seedDemo() {
        long now = System.currentTimeMillis();
        long hour = TimeUnit.HOURS.toMillis(1);
        long day = TimeUnit.DAYS.toMillis(1);

        // 오늘
        entries.add(new HistoryEntry(nextId++, now - 2 * hour,
                "불 좀 꺼주이소", "조명 끔", 92,
                HistoryEntry.Mode.IMMEDIATE, false, null));

        // 어제
        HistoryEntry coffee = new HistoryEntry(nextId++, now - day - 3 * hour,
                "커피 한 잔 묵자", "커피머신 · 에스프레소 2잔", 74,
                HistoryEntry.Mode.CONFIRMED, false, null);
        coffee.setCorrection("아메리카노 1잔");

        HistoryEntry hot = new HistoryEntry(nextId++, now - day - 6 * hour,
                "더버 디지기 딱 좋은 날씨고만", "에어컨 24°C 켬 · 블라인드 내림", 81,
                HistoryEntry.Mode.CONFIRMED, true, null);
        hot.setCorrection("에어컨만 26°C");

        entries.add(coffee);
        entries.add(hot);
        entries.add(new HistoryEntry(nextId++, now - day - 9 * hour,
                "애들 목욕 시키야 된다", "욕조 물 받기 · 38°C", 88,
                HistoryEntry.Mode.CONFIRMED, false, null));

        // 그제
        entries.add(new HistoryEntry(nextId++, now - 2 * day - 4 * hour,
                "청소기 좀 돌리라", "청소기 시작", 90,
                HistoryEntry.Mode.IMMEDIATE, false, null));
    }

    public List<HistoryEntry> getAll() {
        return Collections.unmodifiableList(entries);
    }

    public HistoryEntry find(long id) {
        for (HistoryEntry e : entries) {
            if (e.getId() == id) return e;
        }
        return null;
    }

    /**
     * 실행 결과를 기록으로 남긴다.
     * @param before DeviceRepository.apply()가 돌려준 조치 전 상태 (되돌리기용)
     */
    public HistoryEntry record(Interpretation it, HistoryEntry.Mode mode, List<Device> before) {
        HistoryEntry e = new HistoryEntry(nextId++, System.currentTimeMillis(),
                it.getHeard(), summarizeActions(it), it.getConfidencePercent(),
                mode, it.isMonologue(), before);
        entries.add(0, e);
        notifyChanged();
        return e;
    }

    /** "잘못됐어요" → 교정 내용 저장. 이게 쌓이면 학습 데이터로 내보낸다 */
    public void correct(long id, String correction) {
        HistoryEntry e = find(id);
        if (e == null) return;
        e.setCorrection(correction);
        notifyChanged();
    }

    /**
     * 자주 쓴 말 상위 n개. 홈 화면 바로가기용.
     * 횟수가 같으면 최근 것이 앞. 되물었던 발화(조치 없음)는 뺀다.
     */
    public List<String> getFrequentUtterances(int n) {
        java.util.LinkedHashMap<String, Integer> count = new java.util.LinkedHashMap<>();
        for (HistoryEntry e : entries) {           // entries는 최신순이라 LinkedHashMap 순서가 곧 최근순
            if (e.getMode() == HistoryEntry.Mode.ASKED) continue;
            Integer c = count.get(e.getHeard());
            count.put(e.getHeard(), c == null ? 1 : c + 1);
        }
        List<java.util.Map.Entry<String, Integer>> sorted = new ArrayList<>(count.entrySet());
        // 안정 정렬이라 횟수가 같으면 원래 순서(최근순) 유지
        Collections.sort(sorted, (a, b) -> b.getValue() - a.getValue());
        List<String> out = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : sorted) {
            if (out.size() >= n) break;
            out.add(e.getKey());
        }
        return out;
    }

    /** 교정된 항목만. 나중에 JSONL export 할 때 쓸 것 */
    public List<HistoryEntry> getCorrected() {
        List<HistoryEntry> out = new ArrayList<>();
        for (HistoryEntry e : entries) {
            if (e.isCorrected()) out.add(e);
        }
        return out;
    }

    /** 조치 제목들을 " · "로 이어서 한 줄로 */
    private static String summarizeActions(Interpretation it) {
        if (it.getActions().isEmpty()) {
            return it.getAskBack() != null ? "되물음: " + it.getAskBack() : it.getSummary();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < it.getActions().size(); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(it.getActions().get(i).getTitle());
        }
        return sb.toString();
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : listeners) l.onHistoryChanged();
    }
}
