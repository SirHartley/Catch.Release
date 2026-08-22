package catchrelease.campaign.fish.treasure;

import java.util.ArrayList;
import java.util.List;

public class TreasureAward {
    public final TreasureRarity rarity;
    public final List<Item> items = new ArrayList<>();

    public static class Item {
        public final String name;
        public final String sprite;
        public final int count;

        public Item(String name, String sprite, int count) {
            this.name = name;
            this.sprite = sprite;
            this.count = Math.max(1, count);
        }
    }

    public TreasureAward(TreasureRarity rarity) {
        this.rarity = rarity;
    }

    public String describe() {
        StringBuilder out = new StringBuilder();

        for (Item item : items) {
            if (out.length() > 0) out.append(", ");

            out.append(item.name);
            if (item.count > 1) out.append(" x").append(item.count);
        }

        return out.length() == 0 ? "nothing" : out.toString();
    }
}
