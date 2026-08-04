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
 * Putting a line into somebody else's hull, and what it costs.
 * <p>
 * The fishing gear does not know the difference between a mote and a freighter, but the people
 * aboard the freighter do. A harpoon in the side is not a fishing accident to them; it is being
 * shot at with a rope, and the faction it belongs to takes it that way.
 * <p>
 * This is the bookkeeping half - who was wronged, when, and how often. What is done about it is the
 * dialogue's problem and the patrols' problem.
 */
public class HarpoonOffence {

    /**
     * When each faction has been harpooned, kept between sessions.
     * <p>
     * Every incident rather than only the last one, because the question asked of it is how many
     * there have been lately, and a single timestamp cannot answer that - overwritten by the
     * harpooning that prompts the question, it can only ever say "just now".
     */
    public static final String INCIDENTS_KEY = "$catchrelease_harpoonIncidents";

    /**
     * The ones nobody has answered for yet, and when they happened.
     * <p>
     * Separate from the record above because the two are asked different questions. That one is
     * history and is never cleared - it is what makes a second harpooning a pattern rather than an
     * accident. This one is a debt, and a debt is something that can be settled.
     */
    public static final String OUTSTANDING_KEY = "$catchrelease_harpoonOutstanding";

    /**
     * Harpoonings the player was asked about and did not answer for, and when they were asked.
     * <p>
     * The bill for walking away from a patrol, held back rather than charged on the spot. Somebody
     * has to fly home, file it, and have it read - and a consequence that lands while the player is
     * still looking at the fleet that caused it is not a consequence they got away from.
     */
    public static final String EVASIONS_KEY = "$catchrelease_harpoonEvasions";

    /** Days between refusing a patrol and the reputation catching up with it. */
    public static final float EVASION_DELAY_DAYS = 4f;

    /**
     * What refusing costs, once it lands.
     * <p>
     * Twice the harpooning itself. The rope was arguably an accident; declining to discuss it with
     * the people who came to ask was not.
     */
    public static final float EVASION_REP_LOSS = 0.1f;

    /**
     * Set on the fleet that was hit, so anything that talks to it afterwards knows why it is in a
     * mood. Carries a clock: a crew that was harpooned a season ago has other things on its mind.
     */
    public static final String VICTIM_FLAG = "$catchrelease_harpooned";

    /**
     * How many times this particular crew has been hit, and the flag set once they stop talking.
     * <p>
     * Counted on the fleet rather than against the faction: the faction files paperwork, the people
     * who were actually shot at take it personally, and it is the second harpoon in the same hull
     * that ends the conversation - not the second one anywhere in the sector.
     */
    public static final String HIT_COUNT_KEY = "$catchrelease_harpoonHits";
    public static final String HOSTILE_FLAG = "$catchrelease_harpoonHostile";

    /** The one that stops being an incident and starts being a fight. */
    public static final int HITS_BEFORE_HOSTILE = 2;

    /** What the hostility flags are held under, so they lift together and on their own. */
    public static final String REASON = "catchreleaseHarpoon";

    /** Days a crew stays willing to come after the player over it. */
    public static final float HOSTILE_DAYS = 15f;

    /**
     * Reputation lost with the faction whose hull it was.
     * <p>
     * Between refusing a cargo scan and outright piracy, which is about where firing a harpoon at
     * someone sits. Floored at hostile rather than allowed to run to vengeful: this is meant to be
     * a mess the player can dig out of, not a way to end a faction relationship by accident.
     */
    public static final float REP_LOSS = 0.05f;

    /**
     * Days a harpooning stays on the books.
     * <p>
     * Also the window a second one counts as a repeat within, so the two cannot drift apart - the
     * thing the patrols escalate on and the thing the crew is still angry about are the same event.
     */
    public static final float MEMORY_DAYS = 30f;

    /**
     * Books a harpooning against whoever owns the hull.
     * <p>
     * Nothing happens for a fleet with nobody behind it - a derelict has no opinion and no faction
     * to be offended on its behalf - and nothing happens for the player's own.
     *
     * @return whether this was an offence against anyone
     */
    public static boolean record(CampaignFleetAPI victim) {
        FactionAPI faction = getOffendedFaction(victim);
        if (faction == null) return false;

        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        //flagged before the reputation moves, so anything that reacts to the rep hit already sees a
        //fleet that knows what happened to it
        mem.set(VICTIM_FLAG, true, MEMORY_DAYS);

        int hits = mem.getInt(HIT_COUNT_KEY) + 1;
        mem.set(HIT_COUNT_KEY, hits, MEMORY_DAYS);

        if (hits >= HITS_BEFORE_HOSTILE) turnHostile(victim);

        remember(faction.getId());
        owe(faction.getId());
        applyRepLoss(faction.getId());

        return true;
    }

    /**
     * The second one in a row, from the same crew's point of view.
     * <p>
     * Once is an accident somebody will complain about. Twice is being shot at, and there is nothing
     * further to discuss - they come at you, and they mean it. Aggressive as well as hostile because
     * hostility alone only decides how they feel about a fight they happen to be in; the pair
     * together is what vanilla's own encounter check reads as "engage regardless".
     * <p>
     * Marked low rep impact, because the fight that follows is one they started. The player has
     * already paid for the harpoons in reputation twice over, and paying full price again for
     * defending themselves against the consequence would be charging for the same act three times.
     */
    protected static void turnHostile(CampaignFleetAPI victim) {
        MemoryAPI mem = victim.getMemoryWithoutUpdate();

        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE, REASON, true, HOSTILE_DAYS);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, REASON, true, HOSTILE_DAYS);
        Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, REASON, true, HOSTILE_DAYS);

        mem.set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true, HOSTILE_DAYS);

        //on the hostility's clock rather than the harpooning's, so the line about there being
        //nothing left to say cannot outlive the fleet's willingness to act on it. Once it lapses
        //the crew is back to being merely furious, and the other comm line takes over
        mem.set(HOSTILE_FLAG, true, HOSTILE_DAYS);
    }

    /** Whether this crew has given up on talking about it. */
    public static boolean hasTurnedHostile(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(HOSTILE_FLAG);
    }

    /**
     * Who is owed an apology, or null if nobody is.
     * <p>
     * A fleet with no faction, or the player's own, or one already at war - the last because a
     * faction that is shooting at you anyway is not going to file a complaint about the rope.
     */
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

    /**
     * Files a refusal, to be charged for in a few days.
     * <p>
     * Overwrites rather than stacks: one unanswered question is one report, and a player who gets
     * asked twice before the first report lands has not done anything twice.
     */
    public static void noteEvasion(String factionId) {
        getMap(EVASIONS_KEY).put(factionId, Global.getSector().getClock().getTimestamp());
    }

    /**
     * Charges for any refusal that has had time to be read by somebody who matters.
     * <p>
     * Driven off the patrol script's own tick rather than a script of its own, so there is nothing
     * extra in the save and nothing to register: the thing that files these is the thing that runs.
     */
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

    /** Writes down that it happened, and when, for anything that cares whether it happened before. */
    protected static void remember(String factionId) {
        getIncidents(factionId).add(Global.getSector().getClock().getTimestamp());
    }

    /**
     * How many harpoonings this faction has had to put up with lately.
     * <p>
     * Counted after the current one is written down, so the harpooning being asked about is one of
     * them - the second in the window is the one that means the talking is over.
     */
    public static int getIncidentCount(String factionId) {
        return getIncidents(factionId).size();
    }

    /** Whether this is not the first one they have had to deal with inside the window. */
    public static boolean isRepeatOffence(String factionId) {
        return getIncidentCount(factionId) > 1;
    }

    /** How long ago the last one was, in days, or -1 if there has not been one. */
    public static float getDaysSinceLast(String factionId) {
        List<Long> incidents = getIncidents(factionId);
        if (incidents.isEmpty()) return -1f;

        return Global.getSector().getClock().getElapsedDaysSince(incidents.get(incidents.size() - 1));
    }

    /**
     * This faction's incidents, with anything older than the window dropped on the way out.
     * <p>
     * Pruned on read rather than swept on a timer: nobody asks about a faction that has not been
     * harpooned, so the list that needs tidying is exactly the list somebody is looking at.
     */
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

    /** Puts a harpooning on the faction's slate, where it stays until somebody answers for it. */
    protected static void owe(String factionId) {
        getOutstanding().put(factionId, Global.getSector().getClock().getTimestamp());
    }

    /**
     * Whether this faction is still owed an answer for a harpooning.
     * <p>
     * Lapses on its own after the memory window, and clears the entry on the way past rather than
     * leaving a debt nobody will ever collect sitting in the save.
     */
    public static boolean isOutstanding(String factionId) {
        Long when = getOutstanding().get(factionId);
        if (when == null) return false;

        if (Global.getSector().getClock().getElapsedDaysSince(when) > MEMORY_DAYS) {
            getOutstanding().remove(factionId);
            return false;
        }

        return true;
    }

    /** Every faction with a harpooning still to collect on, newest not distinguished from oldest. */
    public static List<String> getOwedFactions() {
        List<String> owed = new ArrayList<>();

        //over a copy, because the lapse check writes back into the map it is reading
        for (String factionId : new ArrayList<>(getOutstanding().keySet())) {
            if (isOutstanding(factionId)) owed.add(factionId);
        }

        return owed;
    }

    /**
     * Marks a harpooning as answered for - paid, fought over, or otherwise done with.
     * <p>
     * Only the debt. The history stays, because settling up is not the same as it never having
     * happened, and the next one is still the second one.
     */
    public static void settle(String factionId) {
        getOutstanding().remove(factionId);
    }

    /** Whether this particular fleet is one that was harpooned and has not got over it. */
    public static boolean wasHarpooned(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(VICTIM_FLAG);
    }

    protected static Map<String, Long> getOutstanding() {
        return getMap(OUTSTANDING_KEY);
    }

    /** A map in the save, keyed by faction and created on first use. */
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
