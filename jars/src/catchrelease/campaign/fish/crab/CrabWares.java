package catchrelease.campaign.fish.crab;

import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.ShopEntry;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import com.fs.starfarer.api.Global;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What Crablobab has in the coat, and what he wants for it.
 * <p>
 * Deliberately not shop stock. The outfitter sells the rig - things that make the gear better at
 * what the gear is for, priced off a ladder, restocked forever. These are two objects, sold once
 * each by one man who is not in the equipment business, and neither of them makes anything better:
 * one is a novelty and the other takes the harpoon's whole purpose away and replaces it.
 * <p>
 * Each ware answers where its own ownership lives, because they do not agree. A module is the
 * tackle system's to remember; a novelty has nowhere else to be kept and gets a line in the save.
 */
public enum CrabWares {

    /**
     * The flourish over a landed fish. Bought rather than issued: the flash, the backlight and the
     * word for it are the readout announcing itself, and this is the part that is purely for show.
     */
    CONFETTI("Celebration Charges", 15000, 3,
            "\"Little cartridges. You land something and they go off - paper, all colours, all over"
                    + " the readout. No, it does not help you catch anything. That is not what it"
                    + " is for. You have caught the fish already, that is the whole point of it.\"") {
        @Override
        public boolean isOwned() {
            return isBought(name());
        }

        @Override
        public void grant() {
            markBought(name());
        }
    },

    /**
     * The charge behind the barb. Granted through {@link ShopEntry}, which is the one place that
     * knows a running rig has to be stopped so it comes back up reading the module it now has.
     */
    EXPLOSIVE_HEAD("Explosive Head", 40000, 6,
            "\"This one I will not pretend about. It does not catch. It goes on the harpoon and then"
                    + " whatever the harpoon touches is gone, and so is the harpoon. People buy it."
                    + " I have stopped asking them what for.\"") {
        @Override
        public boolean isOwned() {
            return TackleManager.isOwned(Tackle.EXPLOSIVE_HEAD);
        }

        @Override
        public void grant() {
            ShopEntry.of(Tackle.EXPLOSIVE_HEAD, Tackle.Fit.HARPOON).grant();
        }
    };

    /** Everything bought that has nowhere else to be remembered, by constant name. */
    public static final String BOUGHT_KEY = "$catchrelease_crabWares";

    /** What he calls it, what it costs, how many crabs go with that, and how he sells it. */
    public final String name;
    public final int credits;
    public final int crabs;
    public final String pitch;

    CrabWares(String name, int credits, int crabs, String pitch) {
        this.name = name;
        this.credits = credits;
        this.crabs = crabs;
        this.pitch = pitch;
    }

    public abstract boolean isOwned();

    public abstract void grant();

    /**
     * The crabs half of the price, as the same kind of ask the shop and the jobs are written in - so
     * it counts the hold, breaks bundles and describes itself without any of that being rewritten.
     */
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

    /**
     * Takes both halves and hands the thing over, or takes neither.
     * <p>
     * The crabs go first and the credits only once they are spent, since fish are the half that can
     * fail - a hold counted a moment ago can be short by the time it is asked to pay if a bundle was
     * split in between, and charging for a purchase that then did not happen is the one outcome
     * worth writing an order for.
     *
     * @return whether it was paid for
     */
    public boolean buy() {
        if (isOwned() || !canAfford()) return false;

        if (!FishCurrency.spend(getCatch())) return false;

        if (credits > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(credits);
        }

        grant();

        return true;
    }

    /** Whether there is anything left for him to sell at all. */
    public static boolean isAnythingLeft() {
        for (CrabWares ware : values()) {
            if (!ware.isOwned()) return true;
        }

        return false;
    }

    protected static boolean isBought(String id) {
        return getBought().contains(id);
    }

    protected static void markBought(String id) {
        getBought().add(id);
    }

    /**
     * The bought set, held as constant names rather than as enum constants - a name that no longer
     * matches anything is a ware nobody owns, which is a failure this can afford, where an unreadable
     * enum in a save takes the whole set with it.
     */
    @SuppressWarnings("unchecked")
    protected static Set<String> getBought() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(BOUGHT_KEY);
        if (stored instanceof Set) return (Set<String>) stored;

        Set<String> bought = new LinkedHashSet<>();
        data.put(BOUGHT_KEY, bought);

        return bought;
    }
}
