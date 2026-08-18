package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;

/**
 * The shelves of the shop, in the order they are walked past. The sheet only knows CAMPAIGN from
 * MINIGAME (a slot rule, not a shelf label), so the shelf is derived from the stat id rather than a
 * separate sheet column that would need to be kept in step with it.
 */
public enum ShopGroup {

    SEARCHLIGHTS("Breach lamps", "Lamps", "shop_lights", "catchrelease_searchlights"),
    DRONES("Drones", "Drones", "shop_rod", "catchrelease_rod"),
    HARPOON("Harpoon", "Harpoon", "shop_harpoon", "catchrelease_harpoon"),
    THE_CATCH("The catch", "Catch", "pane_fish"),
    DRONE_TACKLE("Drone cores", "Drone cores", "shop_rod_modifiers", "catchrelease_rod"),
    HARPOON_TIPS("Harpoon tips", "Harpoon tips", "shop_harpoon", "catchrelease_harpoon"),
    SEARCHLIGHT_RIG("Lens arrays", "Lens arrays", "shop_lights_modifiers", "catchrelease_searchlights");

    public final String title;

    /** What fits on a tab, where the full title would not. */
    public final String tabTitle;

    /** Settings-registered art shared by this shelf's tab and entries without their own art. */
    public final String iconId;

    /**
     * The ability this shelf is about, or null for shelves that are about the catch itself.
     * <p>
     * A shop that lists upgrades and tackle for a rig the player has never been handed is a shop
     * advertising a game they are not playing yet - and worse, it spoils the one thing the
     * introduction has left to give them. The shelf appears with the gear.
     */
    public final String needsAbility;

    ShopGroup(String title, String tabTitle, String iconId) {
        this(title, tabTitle, iconId, null);
    }

    ShopGroup(String title, String tabTitle, String iconId, String needsAbility) {
        this.title = title;
        this.tabTitle = tabTitle;
        this.iconId = iconId;
        this.needsAbility = needsAbility;
    }

    /**
     * Whether this shelf is on the floor at all.
     * <p>
     * Two conditions, and the second one is the interesting one. Owning the rig is obvious - a shop
     * that lists tackle for a harpoon nobody has is advertising a game the player is not playing.
     * But the deep shelves stay shut for one rung <i>after</i> the lamps and harpoon change hands:
     * the introduction hands over the gear and the errand to use it in the same breath, and the
     * upgrades for it are what completing that errand buys. Opening them at the same moment would
     * make the errand a formality.
     */
    public boolean isUnlocked() {
        if (needsAbility == null) return true;

        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return false;
        if (!Global.getSector().getPlayerFleet().hasAbility(needsAbility)) return false;

        return catchrelease.campaign.fish.tutorial.FishingIntro.isShelfOpen(this);
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

    /**
     * The noun player-facing copy uses for a modifier on this rig. Tackle remains the internal
     * serialized type; keeping the display vocabulary here prevents shop and reward text drifting
     * back toward that implementation name.
     */
    public static String getModuleType(Tackle.Fit rig) {
        if (rig == null) return "rig module";

        switch (rig) {
            case DRONE: return "drone core";
            case HARPOON: return "harpoon tip";
            case SEARCHLIGHT: return "lens array";
            default: return "rig module";
        }
    }
}
