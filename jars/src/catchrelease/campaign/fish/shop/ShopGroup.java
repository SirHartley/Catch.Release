package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.memory.upgrades.UpgradeStat;

/**
 * The shelves of the shop, in the order they are walked past. The sheet only knows CAMPAIGN from
 * MINIGAME (a slot rule, not a shelf label), so the shelf is derived from the stat id rather than a
 * separate sheet column that would need to be kept in step with it.
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
     * The shelf a piece of tackle sits on, by the rig it bolts onto. Centralised here for the same
     * reason as {@link #forStat} - a new rig should be one line in one place.
     *
     * @return null for anything that is not a rig
     */
    /**
     * The shelf a curio sits on. All of them go on the catch's own shelf, which nothing else uses -
     * the tuning stats that map there are equipment and are dropped before the list is built, so
     * this is the one thing in the shop that is about the catch rather than about the gear.
     */
    public static ShopGroup forWare(CrabWares ware) {
        return THE_CATCH;
    }

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
