package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.memory.upgrades.UpgradeStat;

/**
 * The shelves of the shop, in the order they are walked past.
 * <p>
 * The sheet only knows CAMPAIGN from MINIGAME, which is a slot rule rather than a shelf label - a
 * buyer thinks in terms of the gear a thing bolts onto. The gear is already in the stat ids, so the
 * shelf comes from the id rather than from a new column that would have to be kept in step with it.
 */
public enum ShopGroup {

    SEARCHLIGHTS("Breach lamps", "Lamps"),
    DRONES("Drones", "Drones"),
    HARPOON("Harpoon", "Harpoon"),
    THE_CATCH("The catch", "Catch"),
    DRONE_TACKLE("Drone tackle", "Drone rig"),
    HARPOON_TIPS("Harpoon tips", "Harpoon"),
    SEARCHLIGHT_RIG("Lamp rig", "Lamp rig");

    public final String title;

    /** What fits on a tab, where the full title would not. */
    public final String tabTitle;

    ShopGroup(String title, String tabTitle) {
        this.title = title;
        this.tabTitle = tabTitle;
    }

    /** The shelf a stat sits on. Anything the ids do not place ends up with the catch. */
    public static ShopGroup forStat(UpgradeStat stat) {
        if (stat.category == UpgradeStat.Category.MINIGAME) return THE_CATCH;

        String id = stat.id == null ? "" : stat.id;

        if (id.startsWith("searchlight")) return SEARCHLIGHTS;
        if (id.startsWith("harpoon")) return HARPOON;
        if (id.startsWith("drone") || id.startsWith("fishing_drone")) return DRONES;

        return THE_CATCH;
    }

    /**
     * The shelf a piece of tackle sits on, by the rig it bolts onto.
     * <p>
     * Here rather than at the point the entry is made, for the same reason {@link #forStat} is: a
     * new rig should be one line in one place, and the thing that has to learn about it is the
     * shelving, not everything that puts something on a shelf.
     *
     * @return null for anything that is not a rig, which has no shelf because nobody owns one
     */
    public static ShopGroup forRig(Tackle.Fit rig) {
        if (rig == null) return null;

        switch (rig) {
            case DRONE: return DRONE_TACKLE;
            case HARPOON: return HARPOON_TIPS;
            case SEARCHLIGHT: return SEARCHLIGHT_RIG;
            default: return null;
        }
    }
}
