package catchrelease.campaign.fish.treasure;

import java.awt.Color;

public enum TreasureRarity {

    COMMON("Salvage", 0, 100f, new Color(180, 190, 205)),
    UNCOMMON("Wreckage", 1, 32f, new Color(120, 220, 140)),
    RARE("Cache", 2, 9f, new Color(110, 170, 255)),
    EPIC("Relic", 3, 1.5f, new Color(163, 90, 220));

    public final int rank;

    public final String name;
    public final float weight;
    public final Color color;

    TreasureRarity(String name, int rank, float weight, Color color) {
        this.name = name;
        this.rank = rank;
        this.weight = weight;
        this.color = color;
    }
}
