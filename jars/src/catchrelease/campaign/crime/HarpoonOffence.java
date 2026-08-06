package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
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

    /** Hit count at which a crew that fights for a living turns hostile. */
    public static final int HITS_BEFORE_HOSTILE = 2;

    /**
     * What a crew with no guns does instead, in order.
     * <p>
     * Once is a complaint made to your face and nothing more - they have a route to fly and no
     * appetite for chasing anybody. Twice and they will run you down for the repair bill, which is
     * as far as a hauler's nerve goes: they want paying, not a fight, and nothing about it makes
     * them hostile. Three times and they have stopped believing there is a conversation to be had,
     * so they get away from you and put it on the local patrol channel on the way out.
     */
    public static final int HITS_BEFORE_DEMAND = 2;
    public static final int HITS_BEFORE_FLIGHT = 3;

    /** What a holed hauler wants for it. Under a patrol's fine - this is a repair, not a charge. */
    public static final int DAMAGES = 8000;

    /** Days a crew keeps trying to catch you about the bill, and days they keep clear afterwards. */
    public static final float DEMAND_DAYS = 10f;
    public static final float FLIGHT_DAYS = 20f;

    /** Set on a crew coming after you for the repair bill, with the figure beside it. */
    public static final String DEMAND_FLAG = "$catchrelease_harpoonDemand";
    public static final String DAMAGES_KEY = "$catchrelease_harpoonDamages";
    public static final String DAMAGES_TEXT_KEY = "$catchrelease_harpoonDamagesDGS";

    /** Set once the bill has been had out either way, so it is not reopened on the next pass. */
    public static final String DEMAND_DONE_KEY = "$catchrelease_harpoonDemandDone";

    /** Set on a crew that has given up on talking and is putting distance between you. */
    public static final String FLEEING_FLAG = "$catchrelease_harpoonFleeing";

    /**
     * How the sheet tells this side what happened, since a rules row can take credits but cannot
     * close a faction's books, file a refusal against a clock, or call a pursuit off.
     * <p>
     * The same handoff the patrol's fine already uses, with one addition: a global marker, because
     * the crew that was talked to is not necessarily anywhere near the player by the time anything
     * reads this back - a player who pays and immediately jumps out would otherwise leave the bill
     * settled on their side and open on everybody else's. The marker makes the expensive search
     * happen only on the handful of ticks where there is something to find.
     */
    public static final String PAID_FLAG = "$catchrelease_harpoonDamagesPaid";
    public static final String REFUSED_FLAG = "$catchrelease_harpoonDamagesRefused";
    public static final String ANSWERED_PENDING = "$catchrelease_damagesAnswered";

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

        remember(faction.getId());
        owe(faction.getId());

        //after the debt is on the books, because the third one calls a patrol in about it and there
        //has to be something for that patrol to have come about
        escalate(victim, hits);

        //a fresh harpooning resets any patrol retry wait, so it isn't silently absorbed into the last one
        HarpoonPatrolResponse.clearRetryWait(faction.getId());

        applyRepLoss(faction.getId());

        return true;
    }

    /**
     * What this crew does about it, which depends entirely on whether they are armed.
     * <p>
     * A patrol, a warfleet or a pirate answers the second one by turning on you, which is the oldest
     * rule here. Everybody else is somebody with a route to fly and a hole in their hull, and the
     * three things they do in turn are the whole of their side of this.
     */
    protected static void escalate(CampaignFleetAPI victim, int hits) {
        if (isCombatCrew(victim)) {
            if (hits >= HITS_BEFORE_HOSTILE) turnHostile(victim);
            return;
        }

        if (hits >= HITS_BEFORE_FLIGHT) {
            flee(victim);
            return;
        }

        if (hits >= HITS_BEFORE_DEMAND) demand(victim);
    }

    /**
     * Whether this crew fights for a living, off vanilla's own three markers.
     * <p>
     * Asked rather than inferred from strength: a heavily escorted convoy is still a convoy and a
     * lone picket is still a picket. What decides how somebody answers being shot at is what they
     * are for, not what they could win.
     */
    public static boolean isCombatCrew(CampaignFleetAPI fleet) {
        if (fleet == null) return false;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();

        return mem.getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)
                || mem.getBoolean(MemFlags.MEMORY_KEY_WAR_FLEET)
                || mem.getBoolean(MemFlags.MEMORY_KEY_PIRATE);
    }

    /**
     * Comes after you for the repair bill, and for nothing else.
     * <p>
     * Pursuit without hostility, which is a state vanilla supports and uses for its own hasslers.
     * The flag makes them intercept and open a conversation; the aggression it carries only decides
     * how they behave in a fight the player would have to start. Nothing here arms them.
     */
    protected static void demand(CampaignFleetAPI victim) {
        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, DEMAND_DAYS);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, true, DEMAND_DAYS);

        //an ordinary hauler would otherwise fly straight past somebody it has no business with
        mem.set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true, DEMAND_DAYS);

        mem.set(DEMAND_FLAG, true, DEMAND_DAYS);
        mem.set(DAMAGES_KEY, DAMAGES, DEMAND_DAYS);
        mem.set(DAMAGES_TEXT_KEY, Misc.getWithDGS(DAMAGES), DEMAND_DAYS);

        //a fresh hole reopens a bill that was already argued about once
        mem.unset(DEMAND_DONE_KEY);
    }

    /**
     * Stops trying to talk to you, starts getting out of the way, and puts it on the channel.
     * <p>
     * The pursuit is taken back explicitly rather than left to lapse: a crew both chasing you for a
     * bill and running from you is doing neither, and the demand going with it is what takes the
     * conversation off the table for good.
     */
    protected static void flee(CampaignFleetAPI victim) {
        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, false, 0f);

        mem.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
        mem.unset(DEMAND_FLAG);
        mem.unset(DAMAGES_KEY);
        mem.unset(DAMAGES_TEXT_KEY);

        //vanilla's own half-hearted avoidance rather than a scripted run - a freighter keeps flying
        //its route, it just stops letting you get near it
        mem.set(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY, true, FLIGHT_DAYS);
        mem.set(FLEEING_FLAG, true, FLIGHT_DAYS);

        HarpoonPatrolResponse.callForHelp(victim);
    }

    /** Whether this crew is running from you rather than talking about it. */
    public static boolean isFleeing(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(FLEEING_FLAG);
    }

    /** Whether this crew is after you for the repair bill and has not been dealt with yet. */
    public static boolean isDemanding(CampaignFleetAPI fleet) {
        if (fleet == null) return false;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();

        return mem.getBoolean(DEMAND_FLAG) && !mem.getBoolean(DEMAND_DONE_KEY);
    }

    /**
     * The crew has been paid for the damage, so the faction's own books close with it - the whole
     * point of settling with the people you holed is that nobody else comes asking.
     */
    public static void settleWith(CampaignFleetAPI victim) {
        if (victim == null) return;

        stopChasing(victim);

        FactionAPI faction = victim.getFaction();
        if (faction != null) settle(faction.getId());
    }

    /**
     * The crew was told no. Nothing is done to them and nothing is done to you here - a hauler has
     * no enforcement behind it - but it goes in the report, which costs a few days later.
     */
    public static void refuse(CampaignFleetAPI victim) {
        if (victim == null) return;

        stopChasing(victim);

        FactionAPI faction = getOffendedFaction(victim);
        if (faction != null) noteEvasion(faction.getId());
    }

    /**
     * Picks up whatever the sheet said about a bill, wherever that crew has got to.
     * <p>
     * Driven off the patrol script's own tick rather than a script of its own, the same way the
     * evasion charges are - and gated on the global marker, so the usual answer is one boolean read
     * rather than a walk of every fleet in the sector.
     */
    public static void resolveAnsweredDemands() {
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (!sector.getBoolean(ANSWERED_PENDING)) return;

        sector.unset(ANSWERED_PENDING);

        for (LocationAPI location : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : location.getFleets()) {
                MemoryAPI mem = fleet.getMemoryWithoutUpdate();

                if (mem.getBoolean(PAID_FLAG)) {
                    mem.unset(PAID_FLAG);
                    settleWith(fleet);
                } else if (mem.getBoolean(REFUSED_FLAG)) {
                    mem.unset(REFUSED_FLAG);
                    refuse(fleet);
                }
            }
        }
    }

    /**
     * Marks the bill as had out and lets the crew get on with their day.
     * <p>
     * The pursuit has to be taken back by hand: it was set for days, and a crew still flying at the
     * player after the conversation that was the whole reason for it is a fleet that has forgotten
     * what it wanted. The done flag runs on the incident's own clock rather than the pursuit's, so a
     * crew cannot be talked to twice about the same hole.
     */
    protected static void stopChasing(CampaignFleetAPI victim) {
        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        mem.set(DEMAND_DONE_KEY, true, MEMORY_DAYS);

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, false, 0f);

        mem.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
    }

    /**
     * Sets both hostile and aggressive (the pair vanilla's encounter check reads as "engage
     * regardless") plus pursue-player. Deliberately not low-rep-impact: that flag downgrades the
     * fight to transponder-off reputation actions, which skip the {@code ensureAtBest} floor at
     * hostile and falsely promise no hostilities in the encounter tooltip.
     * <p>
     * Public because one caller skips the count entirely: an explosive head is not a rope in the
     * side, and there's no version of that where the crew wants to talk first.
     */
    public static void turnHostile(CampaignFleetAPI victim) {
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
