package catchrelease.memory.charges;

import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import java.util.HashMap;
import java.util.Map;

/**
 * Charge pools for abilities that fire in bursts rather than on a cooldown - spend some, keep some
 * in hand. Pools regenerate continuously as a float (not whole steps); size and refill rate come
 * off the upgrade sheet, so this class knows nothing about specific abilities.
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
     * Rounded down - a pool at 2.9 can be spent twice.
     *
     * @param maxStat     upgrade id for pool size
     * @param maxFallback used if the sheet has no row for it
     */
    public static int getCharges(String abilityId, String maxStat, float maxFallback) {
        return (int) Math.floor(getPool(abilityId, maxStat, maxFallback));
    }

    public static boolean hasCharge(String abilityId, String maxStat, float maxFallback) {
        return getCharges(abilityId, maxStat, maxFallback) >= 1;
    }

    /** @return false if the pool was empty; nothing taken in that case */
    public static boolean spend(String abilityId, String maxStat, float maxFallback) {
        float pool = getPool(abilityId, maxStat, maxFallback);
        if (pool < 1f) return false;

        getPools().put(abilityId, pool - 1f);

        return true;
    }

    /**
     * Adds one charge without exceeding the pool's live upgraded maximum. Fractional regeneration
     * is preserved, so retrieving a shot at 0.4 leaves 1.4 rather than discarding progress.
     *
     * @return whether the pool changed; false when it was already full or the definition is invalid
     */
    public static boolean gain(String abilityId, Refill refill) {
        if (abilityId == null || refill == null) return false;

        define(abilityId, refill);

        float max = getMax(refill.maxStat, refill.maxFallback);
        float pool = getPool(abilityId, refill.maxStat, refill.maxFallback);
        float next = Math.min(max, pool + 1f);
        if (next <= pool) return false;

        getPools().put(abilityId, next);

        if ((int) Math.floor(next) > (int) Math.floor(pool)) {
            refill.onChargeGained();
        }

        return true;
    }

    /** 0 to 1 towards the next charge. */
    public static float getProgressToNext(String abilityId, String maxStat, float maxFallback) {
        float pool = getPool(abilityId, maxStat, maxFallback);

        return pool - (float) Math.floor(pool);
    }

    /** A new pool starts full - a save that never fired the ability shouldn't begin waiting. */
    protected static float getPool(String abilityId, String maxStat, float maxFallback) {
        Map<String, Float> pools = getPools();

        float max = getMax(maxStat, maxFallback);

        Float pool = pools.get(abilityId);
        if (pool == null) {
            pools.put(abilityId, max);
            return max;
        }

        //clamp: a downgrade shouldn't leave more in the pool than it now holds
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

    protected final Map<String, Refill> refills = new HashMap<>();

    /** Which upgrades say pool size and refill time; registered by the ability, not listed here. */
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

        /** Called once when a refill step crosses one or more whole-charge boundaries. */
        public void onChargeGained() {
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

            float next = Math.min(max, pool + amount / seconds);
            pools.put(entry.getKey(), next);

            if ((int) Math.floor(next) > (int) Math.floor(pool)) {
                refill.onChargeGained();
            }
        }
    }
}
