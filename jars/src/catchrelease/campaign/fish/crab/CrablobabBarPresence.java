package catchrelease.campaign.fish.crab;

import catchrelease.campaign.fish.FishingTaboo;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.util.Misc;

import java.util.Random;

/** Per-port roll and lifetime for Crablobab's rules-authored bar appearance. */
public final class CrablobabBarPresence {

    /** Vanilla's per-market bar selection cache; its expiry is the bar's ordinary re-roll. */
    public static final String VANILLA_ROLL_KEY = "$BarCMD_shownEvents";
    public static final String ROLL_KEY = "$catchrelease_crablobab_bar_roll";
    public static final float MAX_ROLL_DAYS = 60f;
    private static final String SEED_SALT = "catchrelease_crablobab_bar";

    private CrablobabBarPresence() {
    }

    /**
     * Dev mode deliberately puts the stall in every bar. In an ordinary campaign each eligible
     * market owns its own saved result, so several ports can host him at once. The result lasts
     * until vanilla expires that market's current bar selection, with an independent two-month
     * ceiling in case another mod extends the bar cache beyond its normal 20-40 days.
     */
    public static boolean isAvailable(InteractionDialogAPI dialog) {
        if (Global.getSettings().isDevMode()) return true;

        SectorEntityToken target = dialog == null ? null : dialog.getInteractionTarget();
        MarketAPI market = target == null ? null : target.getMarket();
        if (market == null || FishingTaboo.isTaboo(market)
                || !FishingIntro.isAtLeast(FishingIntro.DONE)) return false;

        MemoryAPI memory = market.getMemoryWithoutUpdate();
        if (memory.contains(ROLL_KEY) && memory.contains(VANILLA_ROLL_KEY)) {
            return memory.getBoolean(ROLL_KEY);
        }

        boolean present = roll(target, market);
        memory.set(ROLL_KEY, present, MAX_ROLL_DAYS);
        return present;
    }

    /** Uses vanilla's bar seed and its configured chance for one more event. */
    private static boolean roll(SectorEntityToken target, MarketAPI market) {
        BarEventManager manager = BarEventManager.getInstance();
        long seed = manager == null ? Misc.genRandomSeed()
                : manager.getSeed(target, null, SEED_SALT + ":" + market.getId());
        float chance = Global.getSettings().getFloat("barEventProbOneMore");
        chance = Math.max(0f, Math.min(1f, chance));

        return new Random(seed).nextFloat() < chance;
    }
}
