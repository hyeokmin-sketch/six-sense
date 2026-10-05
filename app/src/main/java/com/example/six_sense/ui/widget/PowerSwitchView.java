package com.example.six_sense.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 좌우로 미는 ON/OFF 스위치 (아이폰 토글 비슷한 모양, 글자 포함).
 *
 *   ON  : 청록 트랙, 왼쪽에 "ON", 손잡이 오른쪽
 *   OFF : 주홍 트랙, 오른쪽에 "OFF", 손잡이 왼쪽
 *
 * 누르면 스스로 상태를 바꾸지 않고 Listener에 "바꿔 달라"고만 알린다.
 * 실제 상태는 기기 상태를 보고 화면에서 setOn()으로 맞춘다
 * (욕조처럼 눌러도 바로 꺼지면 안 되는 기기가 있어서).
 */
public class PowerSwitchView extends View {

    public interface Listener {
        void onToggleRequested(boolean wantOn);
    }

    private static final int COLOR_ON = 0xFF009E73;    // colors.xml state_on
    private static final int COLOR_OFF = 0xFFD55E00;   // colors.xml state_off

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private boolean on;
    private float pos;          // 0 = OFF(왼쪽), 1 = ON(오른쪽). 애니메이션용
    private ValueAnimator anim;
    private Listener listener;

    public PowerSwitchView(Context context) {
        super(context);
        init();
    }

    public PowerSwitchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        knob.setColor(0xFFFFFFFF);
        knobShadow.setColor(0x33000000);
        label.setColor(0xFFFFFFFF);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> {
            if (listener != null) listener.onToggleRequested(!on);
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isOn() {
        return on;
    }

    /** 상태 반영. 처음 그릴 때는 animate=false */
    public void setOn(boolean value, boolean animate) {
        if (on == value && (anim == null || !anim.isRunning()) && pos == (value ? 1f : 0f)) return;
        on = value;
        if (anim != null) anim.cancel();
        float target = value ? 1f : 0f;
        if (!animate || !isLaidOut()) {
            pos = target;
            invalidate();
        } else {
            anim = ValueAnimator.ofFloat(pos, target);
            anim.setDuration(180);
            anim.addUpdateListener(a -> {
                pos = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }
        setContentDescription(value ? "켜짐" : "꺼짐");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float d = getResources().getDisplayMetrics().density;
        int w = resolveSize(Math.round(116 * d), widthMeasureSpec);
        int h = resolveSize(Math.round(54 * d), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = h / 2f;
        float pad = h * 0.08f;

        // 트랙: 위치에 따라 주홍 ↔ 청록 섞기
        track.setColor(blend(COLOR_OFF, COLOR_ON, pos));
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, r, r, track);

        // 글자: ON은 왼쪽, OFF는 오른쪽 (손잡이 반대편)
        label.setTextSize(h * 0.36f);
        float textY = h / 2f - (label.descent() + label.ascent()) / 2f;
        label.setAlpha(Math.round(255 * pos));
        c.drawText("ON", (w - h) / 2f + pad, textY, label);
        label.setAlpha(Math.round(255 * (1f - pos)));
        c.drawText("OFF", h + (w - h) / 2f - pad, textY, label);

        // 손잡이
        float kr = r - pad;
        float cx = r + pos * (w - 2 * r);
        c.drawCircle(cx, r + pad * 0.4f, kr, knobShadow);
        c.drawCircle(cx, r, kr, knob);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(on);
    }
}
