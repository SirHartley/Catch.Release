package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;

import java.awt.Color;

/**
 * The categories a player actually filters by: what kind of thing it is, read off the table's
 * tags the same way {@link FishSpec#getTypeName()} reads them. Each carries the colour its
 * merged waters are drawn in - four hues far enough apart to survive being striped together.
 */
public enum FishType {

    FISH("Fish", new Color(100, 165, 255)),
    CRAB("Crab", new Color(255, 140, 80)),
    MOLLUSC("Mollusc", new Color(190, 115, 240)),
    OTHER("Other", new Color(170, 195, 205));

    public final String label;
    public final Color color;

    FishType(String label, Color color) {
        this.label = label;
        this.color = color;
    }

    protected String iconPath;
    protected boolean iconChecked = false;

    /** The first of this type's species that owns art - a face for the chip, found once. */
    public String getIconPath() {
        if (iconChecked) return iconPath;
        iconChecked = true;

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || of(spec) != this) continue;

            if (spec.icon != null && !spec.icon.isEmpty()) {
                iconPath = spec.icon;
                break;
            }
        }

        return iconPath;
    }

    public static FishType of(FishSpec spec) {
        if (spec.tags.contains("crab")) return CRAB;
        if (spec.tags.contains("mollusc")) return MOLLUSC;
        if (spec.tags.contains("fish")) return FISH;

        return OTHER;
    }
}
