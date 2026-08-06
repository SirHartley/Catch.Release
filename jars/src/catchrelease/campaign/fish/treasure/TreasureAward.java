package catchrelease.campaign.fish.treasure;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes what a treasure award contained, for display; items are already granted by the time
 * this exists (a receipt, not the goods).
 */
public class TreasureAward {

    /** One thing that was handed over: its name, how many, and the icon the hold shows it with. */
    public static class Item {
        public final String name;

        /** A sprite path, or null when nothing sensible exists - the card falls back to its own art. */
        public final String sprite;
        public final int count;

        public Item(String name, String sprite, int count) {
            this.name = name;
            this.sprite = sprite;
            this.count = Math.max(1, count);
        }
    }

    public final TreasureRarity rarity;
    public final List<Item> items = new ArrayList<>();

    public TreasureAward(TreasureRarity rarity) {
        this.rarity = rarity;
    }

    /** The contents as one line, for anywhere that wants words rather than a card. */
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
