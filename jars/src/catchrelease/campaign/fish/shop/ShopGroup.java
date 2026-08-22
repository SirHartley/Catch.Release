package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;

public enum ShopGroup {
    SEARCHLIGHTS("Breach lamps", "Lamps", "shop_lights", "catchrelease_searchlights"),
    DRONES("Drones", "Drones", "shop_rod", "catchrelease_rod"),
    HARPOON("Harpoon", "Harpoon", "shop_harpoon", "catchrelease_harpoon"),
    THE_CATCH("The catch", "Catch", "pane_fish"),
    DRONE_TACKLE("Drone cores", "Drone cores", "shop_rod_modifiers", "catchrelease_rod"),
    HARPOON_TIPS("Harpoon tips", "Harpoon tips", "shop_harpoon", "catchrelease_harpoon"),
    SEARCHLIGHT_RIG("Lens arrays", "Lens arrays", "shop_lights_modifiers", "catchrelease_searchlights");

    public final String title;

    public final String tabTitle;

    public final String iconId;

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

    public boolean isUnlocked() {
        if (needsAbility == null) return true;

        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return false;
        if (!Global.getSector().getPlayerFleet().hasAbility(needsAbility)) return false;

        return catchrelease.campaign.fish.tutorial.FishingIntro.isShelfOpen(this);
    }

    public static ShopGroup forStat(UpgradeStat stat) {
        if (stat.category == UpgradeStat.Category.MINIGAME) return THE_CATCH;

        String id = stat.id == null ? "" : stat.id;

        if (id.startsWith("searchlight")) return SEARCHLIGHTS;
        if (id.startsWith("harpoon")) return HARPOON;
        if (id.startsWith("drone") || id.startsWith("fishing_drone")) return DRONES;

        return THE_CATCH;
    }

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
