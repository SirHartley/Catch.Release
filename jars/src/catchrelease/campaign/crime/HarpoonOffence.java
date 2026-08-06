package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Records who was harpooned, when, and how often. Reputation and hostility fallout; dialogue and
 * patrol response are handled elsewhere.
 */
public class HarpoonOffence {

    /** Per-faction history of harpoonings (all incidents, not just the last), kept between sessions. */
    public static final String INCIDENTS_KEY = "$catchrelease_harpoonIncidents";

    /** Per-faction debts not yet answered for; separate from {@link #INCIDENTS_KEY}, which is never cleared. */
    public static final String OUTSTANDING_KEY = "$catchrelease_harpoonOutstanding";

    /** Refusals to answer for a harpooning, and when they happened; charged after {@link #EVASION_DELAY_DAYS}. */
    public static final String EVASIONS_KEY = "$catchrelease_harpoonEvasions";

    /** Days between refusing a patrol and the reputation hit landing. */
    public static final float EVASION_DELAY_DAYS = 4f;

    /** Rep cost of refusing to answer; twice {@link #REP_LOSS}. */
    public static final float EVASION_REP_LOSS = 0.1f;

    /** Set on the victim fleet's memory with a {@link #MEMORY_DAYS} ttl. */
    public static final String VICTIM_FLAG = "$catchrelease_harpooned";

    /** Per-fleet hit count (not per-faction) - the second hit on the same hull is what ends talking. */
    public static final String HIT_COUNT_KEY = "$catchrelease_harpoonHits";
    public static final String HOSTILE_FLAG = "$catchrelease_harpoonHostile";

    /** Hit count at which a crew turns hostile. */
    public static final int HITS_BEFORE_HOSTILE = 2;

    /** Reason tag the hostility memory flags are set under. */
    public static final String REASON = "catchreleaseHarpoon";

    /** Days the hostility flags stay set. */
    public static final float HOSTILE_DAYS = 15f;

    /** Rep loss per harpooning, floored at {@link RepLevel#HOSTILE} so it can't tank a faction relationship. */
    public static final float REP_LOSS = 0.05f;

    /** Days a harpooning stays on the books; also the window for counting repeats. */
    public static final float MEMORY_DAYS = 30f;

    /**
     * Books a harpooning against the hull's owning faction. No-op for a faction-less fleet or the
     * player's own.
     *
     * @return whether this was an offence against anyone
     */
    public static boolean record(CampaignFleetAPI victim) {
        FactionAPI faction = getOffendedFaction(victim);
        if (faction == null) return false;

        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        //set before the rep loss so anything reacting to the rep hit already sees the flag
        mem.set(VICTIM_FLAG, true, MEMORY_DAYS);

        int hits = mem.getInt(HIT_COUNT_KEY) + 1;
        mem.set(HIT_COUNT_KEY, hits, MEMORY_DAYS);

        if (hits >= HITS_BEFORE_HOSTILE) turnHostile(victim);

        remember(faction.getId());
        owe(faction.getId());

        //a fresh harpooning resets any patrol retry wait, so it isn't silently absorbed into the last one
        HarpoonPatrolResponse.clearRetryWait(faction.getId());

        applyRepLoss(faction.getId());

        return true;
    }

    /**
     * Sets both hostile and aggressive (the pair vanilla's encounter check reads as "engage
     * regardless") plus pursue-player. Deliberately not low-rep-impact: that flag downgrades the
     * fight to transponder-off reputation actions, which skip the {@code ensureAtBest} floor at
     * hostile and falsely promise no hostilities in the encounter tooltip.
     */
    protected static void turnHostile(CampaignFleetAPI victim) {
        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE, REASON, true, HOSTILE_DAYS);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, REASON, true, HOSTILE_DAYS);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, HOSTILE_DAYS);

        //ties HOSTILE_FLAG's ttl to the hostility flags' own clock
        mem.set(HOSTILE_FLAG, true, HOSTILE_DAYS);
    }

    public static boolean hasTurnedHostile(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(HOSTILE_FLAG);
    }

    /** Null for a fleet with no faction, the player's own, or one already hostile to the player. */
    public static FactionAPI getOffendedFaction(CampaignFleetAPI victim) {
        if (victim == null) return null;

        FactionAPI faction = victim.getFaction();
        if (faction == null || faction.isPlayerFaction()) return null;
        if (faction.isHostileTo(Global.getSector().getPlayerFaction())) return null;

        return faction;
    }

    protected static void applyRepLoss(String factionId) {
        applyRepLoss(factionId, REP_LOSS);
    }

    protected static void applyRepLoss(String factionId, float amount) {
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = -amount;
        impact.limit = RepLevel.HOSTILE;

        Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact, null, true), factionId);
    }

    /** Files a refusal, to be charged after {@link #EVASION_DELAY_DAYS}. Overwrites, doesn't stack. */
    public static void noteEvasion(String factionId) {
        getMap(EVASIONS_KEY).put(factionId, Global.getSector().getClock().getTimestamp());
    }

    /** Charges any refusal past {@link #EVASION_DELAY_DAYS}. Call from an existing tick (e.g. the patrol script). */
    public static void applyDueEvasions() {
        Map<String, Long> evasions = getMap(EVASIONS_KEY);

        for (String factionId : new ArrayList<>(evasions.keySet())) {
            Long when = evasions.get(factionId);
            if (when == null) continue;

            if (Global.getSector().getClock().getElapsedDaysSince(when) < EVASION_DELAY_DAYS) continue;

            evasions.remove(factionId);
            applyRepLoss(factionId, EVASION_REP_LOSS);
        }
    }

    protected static void remember(String factionId) {
        getIncidents(factionId).add(Global.getSector().getClock().getTimestamp());
    }

    /** Count within {@link #MEMORY_DAYS}, including the current incident once {@link #remember} has run. */
    public static int getIncidentCount(String factionId) {
        return getIncidents(factionId).size();
    }

    public static boolean isRepeatOffence(String factionId) {
        return getIncidentCount(factionId) > 1;
    }

    /** Days since the last incident, or -1 if there hasn't been one. */
    public static float getDaysSinceLast(String factionId) {
        List<Long> incidents = getIncidents(factionId);
        if (incidents.isEmpty()) return -1f;

        return Global.getSector().getClock().getElapsedDaysSince(incidents.get(incidents.size() - 1));
    }

    /** Prunes entries older than {@link #MEMORY_DAYS} on read. */
    protected static List<Long> getIncidents(String factionId) {
        Map<String, List<Long>> all = getMap(INCIDENTS_KEY);

        List<Long> incidents = all.get(factionId);
        if (incidents == null) {
            incidents = new ArrayList<>();
            all.put(factionId, incidents);
        }

        Iterator<Long> it = incidents.iterator();
        while (it.hasNext()) {
            if (Global.getSector().getClock().getElapsedDaysSince(it.next()) > MEMORY_DAYS) it.remove();
        }

        return incidents;
    }

    protected static void owe(String factionId) {
        getOutstanding().put(factionId, Global.getSector().getClock().getTimestamp());
    }

    /** Lapses after {@link #MEMORY_DAYS}, removing the entry as a side effect. */
    public static boolean isOutstanding(String factionId) {
        Long when = getOutstanding().get(factionId);
        if (when == null) return false;

        if (Global.getSector().getClock().getElapsedDaysSince(when) > MEMORY_DAYS) {
            getOutstanding().remove(factionId);
            return false;
        }

        return true;
    }

    public static List<String> getOwedFactions() {
        List<String> owed = new ArrayList<>();

        //copy: isOutstanding() mutates the map it iterates
        for (String factionId : new ArrayList<>(getOutstanding().keySet())) {
            if (isOutstanding(factionId)) owed.add(factionId);
        }

        return owed;
    }

    /** Clears the outstanding debt only; {@link #INCIDENTS_KEY} history is untouched. */
    public static void settle(String factionId) {
        getOutstanding().remove(factionId);
    }

    public static boolean wasHarpooned(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(VICTIM_FLAG);
    }

    protected static Map<String, Long> getOutstanding() {
        return getMap(OUTSTANDING_KEY);
    }

    /** Lazily-created map in {@link Global#getSector()}'s persistent data. */
    @SuppressWarnings("unchecked")
    protected static <T> Map<String, T> getMap(String key) {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(key);
        if (stored instanceof Map) return (Map<String, T>) stored;

        Map<String, T> map = new HashMap<>();
        data.put(key, map);

        return map;
    }
}
