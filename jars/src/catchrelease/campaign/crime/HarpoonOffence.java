package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
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

    /** Where the record of who has been harpooned, and when, is kept between sessions. */
    public static final String OFFENCES_KEY = "$catchrelease_harpoonOffences";

    /**
     * Set on the fleet that was hit, so anything that talks to it afterwards knows why it is in a
     * mood. Carries a clock: a crew that was harpooned a season ago has other things on its mind.
     */
    public static final String VICTIM_FLAG = "$catchrelease_harpooned";

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

        //flagged before the reputation moves, so anything that reacts to the rep hit already sees a
        //fleet that knows what happened to it
        victim.getMemoryWithoutUpdate().set(VICTIM_FLAG, true, MEMORY_DAYS);

        remember(faction.getId());
        applyRepLoss(faction.getId());

        return true;
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
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = -REP_LOSS;
        impact.limit = RepLevel.HOSTILE;

        Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact, null, true), factionId);
    }

    /** Writes down that it happened, and when, for anything that cares whether it happened before. */
    protected static void remember(String factionId) {
        getOffences().put(factionId, Global.getSector().getClock().getTimestamp());
    }

    /**
     * Whether this faction has been harpooned before, recently enough to count as a pattern.
     * <p>
     * Asked before the current one is written down, so "again" means the one before this one.
     */
    public static boolean isRepeatOffence(String factionId) {
        Long last = getOffences().get(factionId);
        if (last == null) return false;

        return Global.getSector().getClock().getElapsedDaysSince(last) <= MEMORY_DAYS;
    }

    /** How long ago the last one was, in days, or -1 if there has not been one. */
    public static float getDaysSinceLast(String factionId) {
        Long last = getOffences().get(factionId);
        if (last == null) return -1f;

        return Global.getSector().getClock().getElapsedDaysSince(last);
    }

    /** Whether this particular fleet is one that was harpooned and has not got over it. */
    public static boolean wasHarpooned(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(VICTIM_FLAG);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Long> getOffences() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(OFFENCES_KEY);
        if (stored instanceof Map) return (Map<String, Long>) stored;

        Map<String, Long> offences = new HashMap<>();
        data.put(OFFENCES_KEY, offences);

        return offences;
    }
}
