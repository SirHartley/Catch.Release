package catchrelease.campaign.crime;

import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
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

public class HarpoonOffence {

    public static final String INCIDENTS_KEY = "$catchrelease_harpoonIncidents";
    public static final String OUTSTANDING_KEY = "$catchrelease_harpoonOutstanding";
    public static final String EVASIONS_KEY = "$catchrelease_harpoonEvasions";

    public static final float EVASION_DELAY_DAYS = 4f;
    public static final float EVASION_REP_LOSS = 0.1f;

    public static final String VICTIM_FLAG = "$catchrelease_harpooned";
    public static final String HIT_COUNT_KEY = "$catchrelease_harpoonHits";
    public static final String HOSTILE_FLAG = "$catchrelease_harpoonHostile";

    public static final int HITS_BEFORE_HOSTILE = 2;
    public static final int HITS_BEFORE_DEMAND = 2;
    public static final int HITS_BEFORE_FLIGHT = 3;

    public static final int DAMAGES = 8000;
    public static final float DEMAND_DAYS = 10f;
    public static final float FLIGHT_DAYS = 20f;
    public static final String DEMAND_FLAG = "$catchrelease_harpoonDemand";

    public static final String DAMAGES_KEY = "$catchrelease_harpoonDamages";
    public static final String DAMAGES_TEXT_KEY = "$catchrelease_harpoonDamagesDGS";

    public static final String DEMAND_DONE_KEY = "$catchrelease_harpoonDemandDone";
    public static final String FLEEING_FLAG = "$catchrelease_harpoonFleeing";
    public static final String PAID_FLAG = "$catchrelease_harpoonDamagesPaid";
    public static final String REFUSED_FLAG = "$catchrelease_harpoonDamagesRefused";
    public static final String ANSWERED_PENDING = "$catchrelease_damagesAnswered";
    public static final String REASON = "catchreleaseHarpoon";
    public static final float HOSTILE_DAYS = 15f;
    public static final float REP_LOSS = 0.03f;
    public static final float OUTMATCHED_MULT = 1.25f;
    public static final float MEMORY_DAYS = 30f;

    public static boolean record(CampaignFleetAPI victim) {
        return record(victim, false);
    }

    public static boolean record(CampaignFleetAPI victim, boolean explosive) {
        FactionAPI faction = getOffendedFaction(victim);
        if (faction == null) return false;

        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        // set before the rep loss so anything reacting to the rep hit already sees the flag
        mem.set(VICTIM_FLAG, true, MEMORY_DAYS);

        int hits = mem.getInt(HIT_COUNT_KEY) + 1;
        mem.set(HIT_COUNT_KEY, hits, MEMORY_DAYS);

        remember(faction.getId(), victim.getContainingLocation());
        owe(faction.getId(), victim.getContainingLocation());

        // after the debt is on the books, because the third one calls a patrol in about it and there has to be something for that patrol to have come about
        escalate(victim, hits);

        // a fresh harpooning resets any patrol retry wait, so it isn't silently absorbed into the last one
        HarpoonPatrolResponse.clearRetryWait(faction.getId(),
                victim.getContainingLocation());

        report(victim, faction.getId(), explosive);

        applyRepLoss(faction.getId());

        if (!isCombatCrew(victim)
                && faction.isHostileTo(Global.getSector().getPlayerFaction())) {
            turnHostile(victim);
        }

        return true;
    }

    protected static void report(CampaignFleetAPI victim, String factionId, boolean explosive) {
        if (isCombatCrew(victim)) return;

        MemoryAPI mem = victim.getMemoryWithoutUpdate();
        if (mem.getBoolean(DEMAND_FLAG) || mem.getBoolean(FLEEING_FLAG)
                || mem.getBoolean(HOSTILE_FLAG)) return;

        HarpoonWitness.begin(victim, factionId, isPlayerIdentified(), explosive);
    }

    public static boolean isPlayerIdentified() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        return player != null && player.isTransponderOn();
    }

    protected static void escalate(CampaignFleetAPI victim, int hits) {
        if (FishermanSpawner.isFisherman(victim)) {
            if (hits >= HITS_BEFORE_DEMAND) demand(victim);
            return;
        }

        if (isCombatCrew(victim)) {
            if (hits >= HITS_BEFORE_HOSTILE) turnHostile(victim);
            return;
        }

        if (isOutmatched(victim) && isCivilised(victim)) {
            flee(victim);
            return;
        }

        if (hits >= HITS_BEFORE_FLIGHT) {
            flee(victim);
            return;
        }

        if (hits >= HITS_BEFORE_DEMAND) demand(victim);
    }

    public static boolean isOutmatched(CampaignFleetAPI victim) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || victim == null) return false;

        return player.getEffectiveStrength() > victim.getEffectiveStrength() * OUTMATCHED_MULT;
    }

    public static boolean canEngagePlayer(CampaignFleetAPI victim) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || victim == null) return false;

        return victim.getEffectiveStrength() > player.getEffectiveStrength() * OUTMATCHED_MULT;
    }

    public static boolean isCivilised(CampaignFleetAPI fleet) {
        FactionAPI faction = fleet == null ? null : fleet.getFaction();
        if (faction == null) return false;

        return !Factions.PIRATES.equals(faction.getId())
                && !Factions.LUDDIC_PATH.equals(faction.getId());
    }

    public static boolean isCombatCrew(CampaignFleetAPI fleet) {
        if (fleet == null) return false;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();

        return mem.getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)
                || mem.getBoolean(MemFlags.MEMORY_KEY_WAR_FLEET)
                || mem.getBoolean(MemFlags.MEMORY_KEY_PIRATE);
    }

    protected static void demand(CampaignFleetAPI victim) {
        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, DEMAND_DAYS);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, true, DEMAND_DAYS);

        // an ordinary hauler would otherwise fly straight past somebody it has no business with
        mem.set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true, DEMAND_DAYS);

        mem.set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true, DEMAND_DAYS);

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null) {
            victim.clearAssignments();
            victim.addAssignment(FleetAssignment.INTERCEPT, player, DEMAND_DAYS,
                    "coming alongside about the damage");

            burn(victim);
        }

        mem.set(DEMAND_FLAG, true, DEMAND_DAYS);
        mem.set(DAMAGES_KEY, DAMAGES, DEMAND_DAYS);
        mem.set(DAMAGES_TEXT_KEY, Misc.getWithDGS(DAMAGES), DEMAND_DAYS);

        mem.unset(DEMAND_DONE_KEY);
    }

    protected static void flee(CampaignFleetAPI victim) {
        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, false, 0f);

        mem.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
        mem.unset(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE);
        mem.unset(DEMAND_FLAG);
        mem.unset(DAMAGES_KEY);
        mem.unset(DAMAGES_TEXT_KEY);

        victim.removeFirstAssignmentIfItIs(FleetAssignment.INTERCEPT);

        // vanilla's own half-hearted avoidance rather than a scripted run - a freighter keeps flying its route, it just stops letting you get near it
        mem.set(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY, true, FLIGHT_DAYS);
        mem.set(FLEEING_FLAG, true, FLIGHT_DAYS);

        burn(victim);

        HarpoonPatrolResponse.callForHelp(victim);
    }

    protected static void burn(CampaignFleetAPI victim) {
        AbilityPlugin burn = victim.getAbility(Abilities.EMERGENCY_BURN);

        if (burn != null && burn.isUsable()) burn.activate();
    }

    public static boolean isFleeing(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(FLEEING_FLAG);
    }

    public static boolean isDemanding(CampaignFleetAPI fleet) {
        if (fleet == null) return false;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();

        return mem.getBoolean(DEMAND_FLAG) && !mem.getBoolean(DEMAND_DONE_KEY);
    }

    public static void settleWith(CampaignFleetAPI victim) {
        if (victim == null) return;

        stopChasing(victim);

        FactionAPI faction = victim.getFaction();
        if (faction != null) settle(faction.getId(), victim.getContainingLocation());
    }

    public static void refuse(CampaignFleetAPI victim) {
        if (victim == null) return;

        stopChasing(victim);

        FactionAPI faction = getOffendedFaction(victim);
        if (faction != null) noteEvasion(faction.getId());
    }

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

    protected static void stopChasing(CampaignFleetAPI victim) {
        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        mem.set(DEMAND_DONE_KEY, true, MEMORY_DAYS);

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, false, 0f);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, false, 0f);

        mem.unset(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER);
    }

    public static void turnHostile(CampaignFleetAPI victim) {
        if (victim == null || FishermanSpawner.isFisherman(victim)) return;

        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE, REASON, true, HOSTILE_DAYS);
        mem.set(HOSTILE_FLAG, true, HOSTILE_DAYS);

        if (!isCombatCrew(victim)) {
            if (canEngagePlayer(victim)) {
                engagePlayer(victim);
            } else if (!mem.getBoolean(FLEEING_FLAG)) {
                flee(victim);
            }
            return;
        }

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, REASON, true, HOSTILE_DAYS);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, HOSTILE_DAYS);
    }

    protected static void engagePlayer(CampaignFleetAPI victim) {
        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        mem.unset(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY);
        mem.unset(FLEEING_FLAG);
        mem.unset(DEMAND_FLAG);
        mem.unset(DAMAGES_KEY);
        mem.unset(DAMAGES_TEXT_KEY);

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, REASON, true, HOSTILE_DAYS);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, HOSTILE_DAYS);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET,
                REASON, true, HOSTILE_DAYS);
        mem.set(MemFlags.FLEET_DO_NOT_IGNORE_PLAYER, true, HOSTILE_DAYS);
        mem.set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true, HOSTILE_DAYS);

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        victim.clearAssignments();
        victim.addAssignment(FleetAssignment.INTERCEPT, player, HOSTILE_DAYS);
        burn(victim);
    }

    public static boolean hasTurnedHostile(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(HOSTILE_FLAG);
    }

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

    public static void noteEvasion(String factionId) {
        getMap(EVASIONS_KEY).put(factionId, Global.getSector().getClock().getTimestamp());
    }

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

    // one claim and one count per faction per system: the ledger keys carry both,
    // so an offence in one place neither escalates nor summons anything elsewhere
    protected static String ledgerKey(String factionId, LocationAPI where) {
        return factionId + "|" + (where == null ? "unknown" : where.getId());
    }

    protected static void remember(String factionId, LocationAPI where) {
        getIncidents(ledgerKey(factionId, where))
                .add(Global.getSector().getClock().getTimestamp());
    }

    public static int getIncidentCount(String factionId, LocationAPI where) {
        return getIncidents(ledgerKey(factionId, where)).size();
    }

    public static boolean isRepeatOffence(String factionId, LocationAPI where) {
        return getIncidentCount(factionId, where) > 1;
    }

    protected static List<Long> getIncidents(String ledgerKey) {
        Map<String, List<Long>> all = getMap(INCIDENTS_KEY);

        List<Long> incidents = all.get(ledgerKey);
        if (incidents == null) {
            incidents = new ArrayList<>();
            all.put(ledgerKey, incidents);
        }

        Iterator<Long> it = incidents.iterator();
        while (it.hasNext()) {
            if (Global.getSector().getClock().getElapsedDaysSince(it.next()) > MEMORY_DAYS) it.remove();
        }

        return incidents;
    }

    protected static void owe(String factionId, LocationAPI where) {
        getOutstanding().put(ledgerKey(factionId, where),
                Global.getSector().getClock().getTimestamp());
    }

    public static boolean isOutstanding(String factionId, LocationAPI where) {
        String key = ledgerKey(factionId, where);
        Long when = getOutstanding().get(key);
        if (when == null) return false;

        if (Global.getSector().getClock().getElapsedDaysSince(when) > MEMORY_DAYS) {
            getOutstanding().remove(key);
            return false;
        }

        return true;
    }

    /** The factions owed for something that happened right here. */
    public static List<String> getOwedFactionsIn(LocationAPI where) {
        List<String> owed = new ArrayList<>();
        if (where == null) return owed;

        String suffix = "|" + where.getId();
        // copy: isOutstanding() mutates the map it iterates
        for (String key : new ArrayList<>(getOutstanding().keySet())) {
            // pre-composite entries from old saves have no home system to match;
            // drop them rather than let them sit unservable forever
            if (!key.contains("|")) {
                getOutstanding().remove(key);
                continue;
            }
            if (!key.endsWith(suffix)) continue;

            String factionId = key.substring(0, key.length() - suffix.length());
            if (isOutstanding(factionId, where)) owed.add(factionId);
        }

        return owed;
    }

    public static void settle(String factionId, LocationAPI where) {
        getOutstanding().remove(ledgerKey(factionId, where));
    }

    public static boolean wasHarpooned(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(VICTIM_FLAG);
    }

    protected static Map<String, Long> getOutstanding() {
        return getMap(OUTSTANDING_KEY);
    }

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
