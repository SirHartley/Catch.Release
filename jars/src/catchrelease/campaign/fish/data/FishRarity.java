package catchrelease.campaign.fish.data;

import java.awt.Color;

public enum FishRarity {

    COMMON(0, new Color(232, 211, 168), 1f, 1f),
    UNCOMMON(1, Color.GREEN, 1.15f, 1.3f),
    RARE(2, Color.BLUE, 1.35f, 1.7f),
    EPIC(3, new Color(163, 53, 238), 1.6f, 2.2f),
    LEGENDARY(4, new Color(235, 55, 50), 1.9f, 2.8f);

    public final int rank;

    public final Color color;

    public final float speedMult;
    public final float wanderMult;

    FishRarity(int rank, Color color, float speedMult, float wanderMult) {
        this.rank = rank;
        this.color = color;
        this.speedMult = speedMult;
        this.wanderMult = wanderMult;
    }

    public static FishRarity ofRank(int rank) {
        for (FishRarity rarity : values()) {
            if (rarity.rank == rank) return rarity;
        }

        throw new IllegalArgumentException("No fish rarity at rank " + rank);
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
