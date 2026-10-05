package com.example.six_sense.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatSeekBar;

/**
 * 위아래로 움직이는 SeekBar. 위가 max, 아래가 0.
 *
 * 안드로이드 기본 SeekBar는 가로뿐이라 캔버스를 90도 돌려서 그린다 (흔히 쓰는 방식).
 * 터치는 직접 Y 좌표로 계산해서 progress를 넣고, 리스너는 따로 둔다
 * (setProgress로 바꾸면 OnSeekBarChangeListener의 fromUser가 false로 와서 못 쓰기 때문).
 */
public class VerticalSeekBar extends AppCompatSeekBar {

    public interface Listener {
        /** 드래그 중 */
        void onChanging(int progress);
        /** 손 뗐을 때 */
        void onRelease(int progress);
    }

    private Listener listener;

    public VerticalSeekBar(Context context) {
        super(context);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(h, w, oldh, oldw);
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
    }

    @Override
    protected synchronized void onDraw(Canvas c) {
        c.rotate(-90);
        c.translate(-getHeight(), 0);
        super.onDraw(c);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);   // 스크롤뷰 안에서도 잡히게
                setProgress(progressFor(event.getY()));
                if (listener != null) listener.onChanging(getProgress());
                break;
            case MotionEvent.ACTION_UP:
                setProgress(progressFor(event.getY()));
                if (listener != null) listener.onRelease(getProgress());
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return true;
    }

    private int progressFor(float y) {
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (h <= 0) return getProgress();
        float ratio = 1f - (y - getPaddingTop()) / h;   // 위가 max
        return Math.round(Math.max(0f, Math.min(1f, ratio)) * getMax());
    }

    @Override
    public synchronized void setProgress(int progress) {
        super.setProgress(progress);
        onSizeChanged(getWidth(), getHeight(), 0, 0);   // 회전 상태에서 바로 다시 그리게
    }
}
