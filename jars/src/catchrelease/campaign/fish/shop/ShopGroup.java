package catchrelease.campaign.fish.shop;

import catchrelease.memory.upgrades.UpgradeStat;

/**
 * The shelves of the shop, in the order they are walked past.
 * <p>
 * The sheet only knows CAMPAIGN from MINIGAME, which is a slot rule rather than a shelf label - a
 * buyer thinks in terms of the gear a thing bolts onto. The gear is already in the stat ids, so the
 * shelf comes from the id rather than from a new column that would have to be kept in step with it.
 */
public enum ShopGroup {

    SEARCHLIGHTS("Searchlights"),
    DRONES("Drones"),
    HARPOON("Harpoon"),
    DEPTH_BOMBS("Depth bombs"),
    THE_CATCH("The catch"),
    DRONE_TACKLE("Drone tackle"),
    HARPOON_TIPS("Harpoon tips");

    public final String title;

    ShopGroup(String title) {
        this.title = title;
    }

    /** The shelf a stat sits on. Anything the ids do not place ends up with the catch. */
    public static ShopGroup forStat(UpgradeStat stat) {
        if (stat.category == UpgradeStat.Category.MINIGAME) return THE_CATCH;

        String id = stat.id == null ? "" : stat.id;

        if (id.startsWith("searchlight")) return SEARCHLIGHTS;
        if (id.startsWith("harpoon")) return HARPOON;
        if (id.startsWith("bomb")) return DEPTH_BOMBS;
        if (id.startsWith("drone") || id.startsWith("fishing_drone")) return DRONES;

        return THE_CATCH;
    }
}
