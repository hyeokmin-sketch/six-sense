package com.example.six_sense.ui.history;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import com.example.six_sense.R;
import com.example.six_sense.databinding.ItemHistoryBinding;
import com.example.six_sense.databinding.ItemHistoryHeaderBinding;
import com.example.six_sense.model.HistoryEntry;

/**
 * 대화 기록 목록. "오늘 / 어제 / 그 전" 헤더가 사이사이 끼어 있어서 뷰 타입이 두 개.
 */
public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnWrong {
        void onWrong(HistoryEntry entry);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ENTRY = 1;

    private final List<Object> rows = new ArrayList<>();   // String(헤더) 또는 HistoryEntry
    private final OnWrong onWrong;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.KOREA);

    public HistoryAdapter(OnWrong onWrong) {
        this.onWrong = onWrong;
    }

    /** 최신순으로 들어온 목록을 날짜별로 묶어서 헤더를 끼운다 */
    public void submit(List<HistoryEntry> entries) {
        rows.clear();
        String lastHeader = null;
        for (HistoryEntry e : entries) {
            String header = headerFor(e.getTimestamp());
            if (!header.equals(lastHeader)) {
                rows.add(header);
                lastHeader = header;
            }
            rows.add(e);
        }
        notifyDataSetChanged();
    }

    private String headerFor(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(ts);

        int dayDiff = now.get(Calendar.DAY_OF_YEAR) - t.get(Calendar.DAY_OF_YEAR)
                + (now.get(Calendar.YEAR) - t.get(Calendar.YEAR)) * 365;
        if (dayDiff == 0) return "오늘";
        if (dayDiff == 1) return "어제";
        return new SimpleDateFormat("M월 d일", Locale.KOREA).format(t.getTime());
    }

    private static boolean isToday(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(ts);
        return now.get(Calendar.YEAR) == t.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_ENTRY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemHistoryHeaderBinding.inflate(inf, parent, false));
        }
        return new EntryHolder(ItemHistoryBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).b.textHeader.setText((String) row);
        } else {
            ((EntryHolder) holder).bind((HistoryEntry) row);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemHistoryHeaderBinding b;

        HeaderHolder(ItemHistoryHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    class EntryHolder extends RecyclerView.ViewHolder {
        private final ItemHistoryBinding b;

        EntryHolder(ItemHistoryBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(HistoryEntry e) {
            b.textHeard.setText("“" + e.getHeard() + "”");
            b.textAction.setText(e.getActionSummary());

            // 메타: 시각 · 실행 방식. 확신도 같은 내부 수치는 사용자한테 안 보여준다
            StringBuilder meta = new StringBuilder(timeFormat.format(e.getTimestamp()));
            switch (e.getMode()) {
                case IMMEDIATE:  meta.append(" · 즉시 실행"); break;
                case CONFIRMED:  meta.append(" · 확인 후 실행"); break;
                case ASKED:      meta.append(" · 되물음"); break;
            }
            b.textMeta.setText(meta);

            if (e.isCorrected()) {
                // 교정된 항목: 원래 조치에 취소선, 교정 내용 배지, 강조 테두리. 버튼은 숨김
                b.textAction.setPaintFlags(b.textAction.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                b.textAction.setTextColor(itemView.getContext().getColor(R.color.muted));
                b.textCorrection.setText(itemView.getContext()
                        .getString(R.string.history_corrected_format, e.getCorrection()));
                b.textCorrection.setVisibility(View.VISIBLE);
                b.cardRoot.setBackgroundResource(R.drawable.bg_card_accent);
                b.buttonWrong.setVisibility(View.GONE);
            } else {
                b.textAction.setPaintFlags(b.textAction.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                b.textAction.setTextColor(itemView.getContext().getColor(R.color.ink));
                b.textCorrection.setVisibility(View.GONE);
                b.cardRoot.setBackgroundResource(R.drawable.bg_card);
                // 교정은 오늘 것만. 되물은 항목은 조치가 없으니 교정할 것도 없음
                boolean canCorrect = e.getMode() != HistoryEntry.Mode.ASKED && isToday(e.getTimestamp());
                b.buttonWrong.setVisibility(canCorrect ? View.VISIBLE : View.GONE);
                b.buttonWrong.setOnClickListener(v -> onWrong.onWrong(e));
            }
        }
    }
}
