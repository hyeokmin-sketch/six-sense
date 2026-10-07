package com.example.six_sense.ui;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;

/**
 * 테마 속성(?attr/...)이 가리키는 drawable id를 꺼낸다.
 * 코드에서 카드 배경을 바꿀 때 R.drawable.bg_card를 직접 쓰면 한눈에 모드(유리)에서도
 * 단색 카드가 나오므로, 대신 이걸로 현재 테마의 값을 가져다 쓴다.
 */
public final class Themed {

    private Themed() {
    }

    @DrawableRes
    public static int drawable(Context context, @AttrRes int attr) {
        TypedValue v = new TypedValue();
        context.getTheme().resolveAttribute(attr, v, true);
        return v.resourceId;
    }
}
