package com.example.six_sense.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 청소기 타이머 링.
 * 12시 방향에서 시계 방향으로 "지난 시간"은 회색, "남은 시간"은 색으로 그린다.
 * 가운데 그림/글자는 레이아웃에서 이 뷰 위에 따로 올린다 (FrameLayout).
 */
public class RingTimerView extends View {

    private final Paint remaining = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint elapsed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private float progress = 0f;   // 0 = 시작 전, 1 = 끝
    private float strokeDp = 14f;

    public RingTimerView(Context context) {
        super(context);
        init();
    }

    public RingTimerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float stroke = strokeDp * getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[]{remaining, elapsed}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        remaining.setColor(0xFFC9566F);   // rose
        elapsed.setColor(0xFFCFC4C9);     // 회색
    }

    /** 0.0 ~ 1.0 (지난 비율) */
    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    public void setColors(int remainingColor, int elapsedColor) {
        remaining.setColor(remainingColor);
        elapsed.setColor(elapsedColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float stroke = remaining.getStrokeWidth();
        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f + stroke / 2f;
        float top = (getHeight() - size) / 2f + stroke / 2f;
        box.set(left, top, left + size - stroke, top + size - stroke);

        float elapsedSweep = 360f * progress;
        // 남은 쪽을 먼저 전체로 깔고, 지난 쪽을 그 위에 회색으로 덮는다
        c.drawArc(box, -90f, 360f, false, remaining);
        if (elapsedSweep > 0f) {
            c.drawArc(box, -90f, elapsedSweep, false, elapsed);
        }
    }
}
