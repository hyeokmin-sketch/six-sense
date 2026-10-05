package com.example.six_sense.ui;

import androidx.annotation.DrawableRes;

import com.example.six_sense.R;
import com.example.six_sense.model.DeviceType;

/** 기기 종류 → 아이콘. 여러 화면에서 쓰여서 한 군데로 뺌 */
public final class DeviceIcons {

    private DeviceIcons() {
    }

    @DrawableRes
    public static int of(DeviceType type) {
        if (type == null) return R.drawable.ic_grid;
        switch (type) {
            case LIGHT:  return R.drawable.ic_bulb;
            case AC:     return R.drawable.ic_ac;
            case BOILER: return R.drawable.ic_flame;
            case BLIND:  return R.drawable.ic_blind;
            case OVEN:   return R.drawable.ic_oven;
            case BATH:   return R.drawable.ic_bath;
            case VACUUM: return R.drawable.ic_vacuum;
            case COFFEE: return R.drawable.ic_coffee;
            case FRIDGE: return R.drawable.ic_fridge;
            default:     return R.drawable.ic_grid;
        }
    }
}
