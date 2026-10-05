package com.example.six_sense.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 냉장고 안 재고. 지금은 데모용 고정 목록.
 * 실제론 냉장고 카메라/재고 API(마음AI 버추얼 홈)에서 받아와야 한다.
 */
public final class FridgeInventory {

    /** 이 일수 이하로 남으면 "임박" */
    public static final int EXPIRING_DAYS = 2;

    public static class Item {
        public final String name;       // "우유 1팩"
        public final int daysLeft;      // 유통기한 없으면 -1

        Item(String name, int daysLeft) {
            this.name = name;
            this.daysLeft = daysLeft;
        }

        public boolean isExpiring() {
            return daysLeft >= 0 && daysLeft <= EXPIRING_DAYS;
        }

        /** 알림용 짧은 이름. "우유 1팩" → "우유" */
        public String shortName() {
            return name.split(" ")[0];
        }
    }

    private static final List<Item> ITEMS = new ArrayList<>();

    static {
        ITEMS.add(new Item("우유 1팩", 1));
        ITEMS.add(new Item("계란 6개", 9));
        ITEMS.add(new Item("사과 3개", -1));
        ITEMS.add(new Item("김치", -1));
        ITEMS.add(new Item("두부 1모", 2));
    }

    private FridgeInventory() {
    }

    public static List<Item> items() {
        return ITEMS;
    }

    /** 임박한 것들의 짧은 이름. 비어 있으면 정상 */
    public static List<String> expiringNames() {
        List<String> out = new ArrayList<>();
        for (Item i : ITEMS) {
            if (i.isExpiring()) out.add(i.shortName());
        }
        return out;
    }

    /** 버튼에 쓸 한 줄 상태. "정상" 또는 "우유 · 두부 임박" */
    public static String statusLabel() {
        List<String> ex = expiringNames();
        return ex.isEmpty() ? "정상" : String.join(" · ", ex) + " 임박";
    }

    public static boolean hasExpiring() {
        return !expiringNames().isEmpty();
    }
}
