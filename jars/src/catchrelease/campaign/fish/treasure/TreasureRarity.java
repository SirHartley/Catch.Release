package catchrelease.campaign.fish.treasure;

import java.awt.Color;

/**
 * How good a piece of treasure is, and how often that happens.
 * <p>
 * The weights are relative to each other, not chances - whether any treasure shows up at all is a
 * separate roll, and a low one. Once something has shown up, this decides what kind.
 */
public enum TreasureRarity {

    COMMON("Salvage", 100f, new Color(180, 190, 205)),
    UNCOMMON("Wreckage", 32f, new Color(120, 220, 140)),
    RARE("Cache", 9f, new Color(110, 170, 255)),
    LEGENDARY("Relic", 1.5f, new Color(255, 165, 60));

    public final String name;
    public final float weight;
    public final Color color;

    TreasureRarity(String name, float weight, Color color) {
        this.name = name;
        this.weight = weight;
        this.color = color;
    }
}
