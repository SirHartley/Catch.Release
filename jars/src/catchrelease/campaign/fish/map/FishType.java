package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishSpec;

import java.awt.Color;

public enum FishType {
    FISH("Fish", new Color(100, 165, 255), "pane_fish"),
    CRAB("Crab", new Color(255, 140, 80), "pane_crab"),
    MOLLUSC("Mollusc", new Color(190, 115, 240), "pane_mollusc"),
    OTHER("Other", new Color(170, 195, 205), "pane_misc");

    public final String label;
    public final Color color;
    public final String iconId;

    FishType(String label, Color color, String iconId) {
        this.label = label;
        this.color = color;
        this.iconId = iconId;
    }

    public static FishType of(FishSpec spec) {
        if (spec.tags.contains("crab")) return CRAB;
        if (spec.tags.contains("mollusc")) return MOLLUSC;
        if (spec.tags.contains("fish")) return FISH;

        return OTHER;
    }
}
