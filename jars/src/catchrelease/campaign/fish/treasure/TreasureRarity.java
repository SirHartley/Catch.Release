package catchrelease.campaign.fish.treasure;

import java.awt.Color;

/**
 * How good a piece of treasure is. Weights are relative, not probabilities - whether treasure
 * appears at all is a separate (low) roll; this only picks which kind once it has.
 */
public enum TreasureRarity {

    COMMON("Salvage", 100f, new Color(180, 190, 205)),
    UNCOMMON("Wreckage", 32f, new Color(120, 220, 140)),
    RARE("Cache", 9f, new Color(110, 170, 255)),
    EPIC("Relic", 1.5f, new Color(163, 90, 220));

    public final String name;
    public final float weight;
    public final Color color;

    TreasureRarity(String name, float weight, Color color) {
        this.name = name;
        this.weight = weight;
        this.color = color;
    }
}
