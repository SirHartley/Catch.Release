package catchrelease.memory.charges;

import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import java.util.HashMap;
import java.util.Map;

/**
 * Charges for abilities that fire in bursts rather than on a cooldown.
 * <p>
 * A cooldown asks you to wait the same amount every time. A pool asks you to decide: fire three
 * harpoons at one pass and wait for all three, or spend one and keep two in hand. That is a more
 * interesting question than "is it up yet", and it is the whole reason this exists rather than the
 * ability's own rearm timer being turned down.
 * <p>
 * Charges regenerate continuously rather than in whole steps, so the pool is a float and a partial
 * charge is real progress rather than a hidden timer. Both the size of the pool and how fast it
 * fills come off the upgrade sheet, so this knows nothing about either ability.
 */
public class ChargeManager implements EveryFrameScript {

    public static final String KEY = "$catchrelease_charges";

    /** Installed once. Idempotent, so calling it on every load is safe. */
    public static void register() {
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof ChargeManager) return;
        }

        Global.getSector().addScript(new ChargeManager());
    }

    /**
     * How many charges are in hand, rounded down - a pool at 2.9 can be spent twice.
     *
     * @param maxStat     upgrade id for how big the pool is
     * @param maxFallback what it is without a row in the sheet
     */
    public static int getCharges(String abilityId, String maxStat, float maxFallback) {
        return (int) Math.floor(getPool(abilityId, maxStat, maxFallback));
    }

    public static boolean hasCharge(String abilityId, String maxStat, float maxFallback) {
        return getCharges(abilityId, maxStat, maxFallback) >= 1;
    }

    /**
     * Takes one, if there is one.
     *
     * @return false if the pool was empty, in which case nothing was taken
     */
    public static boolean spend(String abilityId, String maxStat, float maxFallback) {
        float pool = getPool(abilityId, maxStat, maxFallback);
        if (pool < 1f) return false;

        getPools().put(abilityId, pool - 1f);

        return true;
    }

    /** 0 to 1 towards the next charge, for anything that wants to show the wait. */
    public static float getProgressToNext(String abilityId, String maxStat, float maxFallback) {
        float pool = getPool(abilityId, maxStat, maxFallback);

        return pool - (float) Math.floor(pool);
    }

    /**
     * A new pool starts full. Anything else means a save that has never fired the ability begins by
     * waiting for something it has not spent.
     */
    protected static float getPool(String abilityId, String maxStat, float maxFallback) {
        Map<String, Float> pools = getPools();

        float max = getMax(maxStat, maxFallback);

        Float pool = pools.get(abilityId);
        if (pool == null) {
            pools.put(abilityId, max);
            return max;
        }

        //an upgrade that shrinks the pool should not leave more in it than it holds
        if (pool > max) {
            pools.put(abilityId, max);
            return max;
        }

        return pool;
    }

    protected static float getMax(String maxStat, float maxFallback) {
        return Math.max(1f, UpgradeManager.getValue(maxStat, maxFallback));
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Float> getPools() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(KEY);
        if (stored instanceof Map) return (Map<String, Float>) stored;

        Map<String, Float> pools = new HashMap<>();
        data.put(KEY, pools);

        return pools;
    }

    /** Every pool that has been used, refilling. Registered abilities add themselves by spending. */
    protected final Map<String, Refill> refills = new HashMap<>();

    /**
     * What a pool needs to know to refill itself: which upgrade says how big it is and which says
     * how long a charge takes. Registered by the ability rather than listed here, so this class
     * never has to be edited to add another one.
     */
    public static class Refill {
        public final String maxStat;
        public final float maxFallback;
        public final String rateStat;
        public final float rateFallback;

        public Refill(String maxStat, float maxFallback, String rateStat, float rateFallback) {
            this.maxStat = maxStat;
            this.maxFallback = maxFallback;
            this.rateStat = rateStat;
            this.rateFallback = rateFallback;
        }
    }

    /** Tells the manager how a pool refills. Safe to call repeatedly; the last call wins. */
    public static void define(String abilityId, Refill refill) {
        ChargeManager manager = getManager();
        if (manager == null) return;

        manager.refills.put(abilityId, refill);
    }

    protected static ChargeManager getManager() {
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof ChargeManager) return (ChargeManager) script;
        }

        return null;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    /** Charges come back while the game is paused too - a pool is a clock, not an action. */
    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        Map<String, Float> pools = getPools();

        for (Map.Entry<String, Refill> entry : refills.entrySet()) {
            Refill refill = entry.getValue();

            float max = getMax(refill.maxStat, refill.maxFallback);
            float pool = getPool(entry.getKey(), refill.maxStat, refill.maxFallback);
            if (pool >= max) continue;

            float seconds = Math.max(0.1f, UpgradeManager.getValue(refill.rateStat, refill.rateFallback));

            pools.put(entry.getKey(), Math.min(max, pool + amount / seconds));
        }
    }
}
