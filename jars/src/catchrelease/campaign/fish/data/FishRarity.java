package catchrelease.campaign.fish.data;

import java.awt.Color;

/** Rarity ladder, and the mote colour that goes with it. */
public enum FishRarity {

    COMMON(Color.GRAY),
    UNCOMMON(Color.GREEN),
    RARE(Color.BLUE),
    EPIC(new Color(163, 53, 238)),
    LEGENDARY(new Color(255, 128, 0));

    public final Color color;

    FishRarity(Color color) {
        this.color = color;
    }

    public static FishRarity parse(String name, FishRarity fallback) {
        if (name == null) return fallback;

        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
