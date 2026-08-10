package catchrelease.campaign.fish.crab;

import catchrelease.campaign.fish.colony.BreachConservatory;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.ShopEntry;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.ids.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What Crablobab has in the coat, and what he wants for it.
 * <p>
 * Deliberately not shop stock. The outfitter sells the rig - things that make the gear better at
 * what the gear is for, priced off a ladder, restocked forever. These are objects sold by one man
 * who is not in the equipment business, and none of them makes the rig better: novelties and plans
 * sold once, plus a replaceable charge that takes the harpoon's whole purpose away and replaces it.
 * <p>
 * Three questions, not one, and they are not the same question asked three ways. For permanent
 * wares, bought is his business and never comes undone. Owned is where a thing is kept afterwards;
 * the repeatable explosive head asks only that live tackle state. Switched on is the player's, and
 * only some wares have it - a module is switched off by taking it out of the slot, so the only thing
 * that needs a switch is something with no slot to leave.
 */
public enum CrabWares {

    /**
     * The whole flourish over a landed fish - flash, backlight, the specimen thrown up over its own
     * box, confetti and the word for it. Bought rather than issued, and switchable afterwards
     * because it is the one thing here somebody might want and then not want.
     */
    CELEBRATION("Celebration Charges", 15000, 3,
            "Goes off over the catch card when something is landed: a flash behind it, the specimen"
                    + " thrown up over its own box, paper everywhere, and the word for it at an"
                    + " angle. Says nothing the readout beside it does not already say.") {
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

    /**
     * A pair of earmuffs, against the whispering that comes with thin water.
     * <p>
     * Switchable for the same reason the confetti is: it is a comfort rather than a capability, and
     * somebody who bought it may want the sound back. What it does is stop the wearer hearing the
     * whispering. It does not stop the whispering - see {@code docs/LORE.md}, and note that the
     * blurb is written not to claim otherwise.
     */
    EARMUFFS("Earmuffs", 8000, 2,
            "Worn out where the fabric is thin, against the sound that comes with it. Thick, warm,"
                    + " and by all accounts very comfortable. You stop hearing it.") {
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

    /**
     * The charge behind the barb. Granted through {@link ShopEntry}, which is the one place that
     * knows a running rig has to be stopped so it comes back up reading the module it now has.
     */
    EXPLOSIVE_HEAD("Explosive Head", 40000, 6,
            "A shaped charge behind the barb. Fitted in the harpoon's own slot until it goes off;"
                    + " an unfired one can still be taken off and put back on.") {
        @Override
        public boolean isOwned() {
            return TackleManager.isOwned(Tackle.EXPLOSIVE_HEAD);
        }

        @Override
        public void grant() {
            ShopEntry.of(Tackle.EXPLOSIVE_HEAD, Tackle.Fit.HARPOON).grant();
        }
    },

    /**
     * The plans for the conservatory, as a blueprint chip rather than as a thing switched on.
     * <p>
     * Vanilla's own industry blueprint: one item id with the industry written into its data, which
     * the game's plugin reads to name itself and to teach the faction when it is used. So nothing
     * here has to know what a blueprint screen looks like - the chip goes into the hold and the rest
     * is the game's.
     * <p>
     * Owned is asked two ways because it can become true two ways. He stops selling it once he has
     * sold it, and also once the faction knows the industry by any other route - a chip found in a
     * hulk teaches the same thing his does, and a man offering to sell what you already have is a
     * man who has not looked.
     */
    CONSERVATORY("Breach Conservatory Plans", 60000, 8,
            "A blueprint chip for a hall of pressure glass and dim water. Half fish market, half"
                    + " public aquarium, and the only way a colony gets into the trade at all.") {
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

    /** Everything bought that has nowhere else to be remembered, by constant name. */
    public static final String BOUGHT_KEY = "$catchrelease_crabWares";

    /**
     * Everything switched off, by constant name. Held as the exception rather than as the state,
     * so a ware that has only ever been bought is on - which is what buying a thing means.
     */
    public static final String OFF_KEY = "$catchrelease_crabWaresOff";

    /**
     * What he calls it, what it costs, how many crabs go with that, and what it is.
     * <p>
     * How he <i>sells</i> it is not here - the pitch is dialogue and lives in {@code rules.csv}
     * under {@code catchrelease_crabWare<Name>}. The description is a tooltip, which is furniture.
     */
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

    /**
     * Whether this can be switched off once bought, as against only owned. False for anything kept
     * in a slot: taking it out is the switch, and a second one beside it would be two answers.
     */
    public boolean isSwitchable() {
        return false;
    }

    /** Bought and not switched off. Anything unswitchable is on for as long as it is owned. */
    public boolean isOn() {
        if (!isOwned()) return false;

        return !isSwitchable() || !getFlags(OFF_KEY).contains(name());
    }

    public void setOn(boolean on) {
        if (!isSwitchable()) return;

        if (on) getFlags(OFF_KEY).remove(name());
        else getFlags(OFF_KEY).add(name());
    }

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

    /** Everything bought that has a switch on it, for the shop shelf that holds them. */
    public static List<CrabWares> getSwitchable() {
        List<CrabWares> out = new ArrayList<>();

        for (CrabWares ware : values()) {
            if (ware.isSwitchable() && ware.isOwned()) out.add(ware);
        }

        return out;
    }

    protected static boolean isBought(String id) {
        return getFlags(BOUGHT_KEY).contains(id);
    }

    protected static void markBought(String id) {
        getFlags(BOUGHT_KEY).add(id);
    }

    /**
     * A named set in the save, created on first use. Constant names rather than enum constants - a
     * name that no longer matches anything is a ware nobody owns, which is a failure this can
     * afford, where an unreadable enum in a save takes the whole set with it.
     */
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
