package catchrelease.memory.charges;

import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

import java.util.HashMap;
import java.util.Map;

public class ChargeManager implements EveryFrameScript {
    public static final String KEY = "$catchrelease_charges";

    protected final Map<String, Refill> refills = new HashMap<>();

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

        public void onChargeGained() {
        }
    }

    public static void register() {
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof ChargeManager) return;
        }

        Global.getSector().addScript(new ChargeManager());
    }

    public static int getCharges(String abilityId, String maxStat, float maxFallback) {
        return (int) Math.floor(getPool(abilityId, maxStat, maxFallback));
    }

    public static boolean hasCharge(String abilityId, String maxStat, float maxFallback) {
        return getCharges(abilityId, maxStat, maxFallback) >= 1;
    }

    public static boolean spend(String abilityId, String maxStat, float maxFallback) {
        float pool = getPool(abilityId, maxStat, maxFallback);
        if (pool < 1f) return false;

        getPools().put(abilityId, pool - 1f);

        return true;
    }

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

    public static float getProgressToNext(String abilityId, String maxStat, float maxFallback) {
        float pool = getPool(abilityId, maxStat, maxFallback);

        return pool - (float) Math.floor(pool);
    }

    protected static float getPool(String abilityId, String maxStat, float maxFallback) {
        Map<String, Float> pools = getPools();

        float max = getMax(maxStat, maxFallback);

        Float pool = pools.get(abilityId);
        if (pool == null) {
            pools.put(abilityId, max);
            return max;
        }

        // clamp: a downgrade shouldn't leave more in the pool than it now holds
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
