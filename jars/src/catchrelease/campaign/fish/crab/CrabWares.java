package catchrelease.campaign.fish.crab;

import catchrelease.campaign.fish.colony.BreachConservatory;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.ShopEntry;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.ids.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum CrabWares {

    CELEBRATION("Celebration Charges", 15000, 3,
            "A landed catch gets the full treatment: flash, confetti, the specimen kicked upward,"
                    + " and a slanted celebration label across the card. Pure spectacle; the catch"
                    + " and its information stay exactly the same.") {
        @Override
        public boolean isOwned() {
            return isBought(name());
        }

        @Override
        public void grant() {
            markBought(name());
        }

        @Override
        public boolean isSwitchable() {
            return true;
        }
    },
    EARMUFFS("Earmuffs", 8000, 2,
            "Thick enough for cold decks, comfortable enough for a long watch. In sufficiently thin"
                    + " fabric, the wearer no longer hears the whispering, though the sound itself"
                    + " continues.") {
        @Override
        public boolean isOwned() {
            return isBought(name());
        }

        @Override
        public void grant() {
            markBought(name());
        }

        @Override
        public boolean isSwitchable() {
            return true;
        }
    },
    CHICKEN_PROFILE("Chicken Profile", 12000, 3,
            "Swaps the generic unidentified-pattern marker for a chicken. If a Sonar Head has"
                    + " identified the hooked species, the chicken loses the argument.") {
        @Override
        public boolean isOwned() {
            return isBought(name());
        }

        @Override
        public void grant() {
            markBought(name());
        }

        @Override
        public boolean isSwitchable() {
            return true;
        }
    },
    EXPLOSIVE_HEAD("Explosive Head", 40000, 6,
            "A single-use Harpoon Tip containing a shaped charge. It detonates on contact,"
                    + " destroying ordinary struck patterns instead of landing them and damaging"
                    + " struck fleets. Legendary patterns may react differently. An unfired tip can"
                    + " be fitted or removed normally. Detonation consumes it.") {
        @Override
        public boolean isOwned() {
            return TackleManager.isOwned(Tackle.EXPLOSIVE_HEAD);
        }

        @Override
        public void grant() {
            ShopEntry.of(Tackle.EXPLOSIVE_HEAD, Tackle.Fit.HARPOON).grant();
        }
    },
    CONSERVATORY("Breach Conservatory Plans", 60000, 8,
            "A blueprint chip for a Breach Conservatory: a civic pressure-glass aquarium with a"
                    + " local fishing outfitter and trade desk. It lets the colony join the fishing"
                    + " trade and display specimens from the administrator's own catch. You are"
                    + " buying the plans, not the building.") {
        @Override
        public boolean isOwned() {
            return isBought(name())
                    || Global.getSector().getPlayerFaction().knowsIndustry(BreachConservatory.ID);
        }

        @Override
        public void grant() {
            markBought(name());

            Global.getSector().getPlayerFleet().getCargo().addSpecial(
                    new SpecialItemData(Items.INDUSTRY_BP, BreachConservatory.ID), 1);
        }
    };

    public static final String BOUGHT_KEY = "$catchrelease_crabWares";

    public static final String OFF_KEY = "$catchrelease_crabWaresOff";

    public static final String LAST_EXPLOSIVE_TARGET_KEY = "$catchrelease_crabLastExplosiveTarget";
    public static final String EXPLOSIVE_TARGET_PENDING_KEY = "$catchrelease_crabExplosiveTargetPending";

    public static final String FALLBACK_CRAB_ID = "crab";
    public static final int FALLBACK_CRAB_CREDITS = 10000;
    public static final int FALLBACK_CRAB_CRABS = 1;

    public final String name;
    public final int credits;
    public final int crabs;
    public final String description;

    CrabWares(String name, int credits, int crabs, String description) {
        this.name = name;
        this.credits = credits;
        this.crabs = crabs;
        this.description = description;
    }

    public abstract boolean isOwned();

    public abstract void grant();

    public boolean isSwitchable() {
        return false;
    }

    public boolean isOn() {
        if (!isOwned()) return false;

        return !isSwitchable() || !getFlags(OFF_KEY).contains(name());
    }

    public void setOn(boolean on) {
        if (!isSwitchable()) return;

        if (on) getFlags(OFF_KEY).remove(name());
        else getFlags(OFF_KEY).add(name());
    }

    public FishRequirement getCatch() {
        FishRequirement req = new FishRequirement();

        req.tag = "crab";
        req.count = crabs;

        return req;
    }

    public boolean hasCatch() {
        return FishCurrency.count(getCatch()) >= crabs;
    }

    public boolean hasCredits() {
        if (Global.getSector().getPlayerFleet() == null) return false;

        return Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= credits;
    }

    public boolean canAfford() {
        return hasCredits() && hasCatch();
    }

    public boolean buy() {
        if (isOwned() || !canAfford()) return false;

        if (!FishCurrency.spend(getCatch())) return false;

        if (credits > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(credits);
        }

        grant();
        Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                "Traded crabs for " + name());

        return true;
    }

    public static boolean isAnythingLeft() {
        for (CrabWares ware : values()) {
            if (!ware.isOwned()) return true;
        }

        return false;
    }

    public static boolean hasConservatoryPlans() {
        return CONSERVATORY.isOwned();
    }

    public static FishRequirement getFallbackBassCatch() {
        FishRequirement req = new FishRequirement();
        req.tag = "crab";
        req.count = FALLBACK_CRAB_CRABS;
        return req;
    }

    public static boolean canAffordFallbackBass() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return false;

        return Global.getSector().getPlayerFleet().getCargo().getCredits().get()
                >= FALLBACK_CRAB_CREDITS
                && FishCurrency.count(getFallbackBassCatch()) >= FALLBACK_CRAB_CRABS;
    }

    public static FishCatch createFallbackBass() {
        FishSpec bass = FishSpecLoader.getFishSpec(FALLBACK_CRAB_ID);
        if (bass == null) return null;

        return new FishCatch(bass.id, bass.lengthMin, bass.weightMin, 0f);
    }

    public static String getFallbackBassName() {
        FishSpec bass = FishSpecLoader.getFishSpec(FALLBACK_CRAB_ID);
        return bass == null ? "crab" : bass.getDisplayName();
    }

    public static String getFallbackBassDescription() {
        return "A terrible-quality " + getFallbackBassName()
                + ". It is extravagantly overpriced.";
    }

    public static boolean buyFallbackBass() {
        FishCatch bass = createFallbackBass();
        if (bass == null || !canAffordFallbackBass()) return false;
        if (!FishCurrency.spend(getFallbackBassCatch())) return false;

        Global.getSector().getPlayerFleet().getCargo().getCredits()
                .subtract(FALLBACK_CRAB_CREDITS);
        FishItems.addToPlayerCargo(bass);
        return true;
    }

    public static List<CrabWares> getSwitchable() {
        List<CrabWares> out = new ArrayList<>();

        for (CrabWares ware : values()) {
            if (ware.isSwitchable() && ware.isOwned()) out.add(ware);
        }

        return out;
    }

    public static void recordExplosiveUse(String target) {
        if (Global.getSector() == null) return;

        String remembered = target == null || target.trim().isEmpty() ? "Something" : target.trim();
        Global.getSector().getMemoryWithoutUpdate().set(LAST_EXPLOSIVE_TARGET_KEY, remembered);
        Global.getSector().getMemoryWithoutUpdate().set(EXPLOSIVE_TARGET_PENDING_KEY, true);
    }

    public static boolean hasUnmentionedExplosiveUse() {
        return Global.getSector() != null && Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(EXPLOSIVE_TARGET_PENDING_KEY);
    }

    public static String getLastExplosiveTarget() {
        if (Global.getSector() == null) return "Something";

        String target = Global.getSector().getMemoryWithoutUpdate()
                .getString(LAST_EXPLOSIVE_TARGET_KEY);
        return target == null || target.trim().isEmpty() ? "Something" : target;
    }

    public static void acknowledgeExplosiveUse() {
        if (Global.getSector() == null) return;

        Global.getSector().getMemoryWithoutUpdate().unset(EXPLOSIVE_TARGET_PENDING_KEY);
    }

    protected static boolean isBought(String id) {
        return getFlags(BOUGHT_KEY).contains(id);
    }

    protected static void markBought(String id) {
        getFlags(BOUGHT_KEY).add(id);
    }

    @SuppressWarnings("unchecked")
    protected static Set<String> getFlags(String key) {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(key);
        if (stored instanceof Set) return (Set<String>) stored;

        Set<String> flags = new LinkedHashSet<>();
        data.put(key, flags);

        return flags;
    }
}
