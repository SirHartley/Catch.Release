package catchrelease.campaign.crime;

import catchrelease.campaign.fish.FishingTaboo;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.util.Misc;

/**
 * Where the lamps may be run, and what it costs when they are run anywhere else.
 * <p>
 * A breach lamp does not illuminate anything. It burns a window through the fabric, and the fabric
 * it burns through is the one everybody is standing in - which is tolerable out past the last
 * colony where the only thing to destabilise is vacuum, and is not tolerable at all over somewhere
 * with people underneath. Every faction that polices anything treats it the way they would treat
 * discharging a weapon into a habitat: not as smuggling, as an attack that has not landed yet.
 * <p>
 * The ladder is the point. A first offence is a warning, because the lamps look like survey gear to
 * anybody who has not seen one open. After that it is a fine, then the hold, then the guns - and the
 * last rung is the only one on a clock, because "again, this month" is a different conversation from
 * "again, eventually".
 * <p>
 * Modelled on vanilla's transponder response, which is the same shape of problem: a passive thing
 * the player leaves switched on, patrols that notice it, and a conversation that escalates.
 */
public class LampOffence {

    /**
     * Per-system history key stems. Each system center keeps one pair per enforcing faction.
     * The old sector-global values used these bare keys; they are deliberately no longer read,
     * because assigning that shared history to any one system or faction would preserve the leak.
     */
    public static final String COUNT_KEY = "$catchrelease_lampOffences";
    public static final String LAST_KEY = "$catchrelease_lampLast";

    /** Set on a patrol that has seen the lamps lit somewhere they are not allowed. */
    public static final String SAW_KEY = "$catchrelease_sawLamps";

    /** Transient latch set when a patrol's current stop opens; per-burn history is below. */
    public static final String STOPPED_KEY = "$catchrelease_lampStopped";

    /**
     * Which burn of the lamps a given crew has already had the conversation about.
     * <p>
     * The stopped flag on its own said "this crew has dealt with you", full stop, and a crew that
     * had once watched the player put the lamps out would then watch them relight and say nothing
     * for a month. That is the wrong unit. What a stop settles is <i>that</i> burn - the player
     * agreed to stop doing the thing - and lighting them again is a new offence, not a continuation
     * of the settled one. So the run is counted and the crew remembers a number rather than a
     * boolean; when the numbers differ they have not been told about this one yet.
     */
    public static final String RUN_KEY = "$catchrelease_lampRun";
    public static final String STOPPED_RUN_KEY = "$catchrelease_lampStoppedRun";

    /** The burn already claimed by whichever pursuing patrol reached the player first. */
    public static final String RESOLVED_RUN_KEY = "$catchrelease_lampResolvedRun";

    /** How close to somewhere inhabited counts as over it. */
    public static final float PLANET_RANGE = 3000f;

    /** Strikes lapse if nothing happens for this long; the fourth rung asks about a month. */
    public static final float FORGET_DAYS = 90f;
    public static final float REPEAT_DAYS = 30f;

    /** What the second rung costs. */
    public static final int FINE = 25000;

    /**
     * What being stopped costs in standing, and what refusing costs on top.
     * <p>
     * Three points and six, in the figures the relations screen shows - the API takes the same
     * number over a hundred. Charged on every stop rather than from the second, which is vanilla's
     * own handling of the transponder: the warning is still an entry in somebody's file.
     */
    public static final float REP_LOSS = 0.03f;
    public static final float REP_REFUSE = 0.06f;

    /**
     * Factions that do not need a planet underneath to object.
     * <p>
     * The Church reads a hole burned in creation as exactly that. The Path does not bother
     * distinguishing between the lamp and the person carrying it. Both come out to meet you
     * anywhere they hold, which is the difference between a regulation and a doctrine.
     */
    public static boolean hatesLampsAnywhere(String factionId) {
        return FishingTaboo.isTaboo(factionId);
    }

    //---------------------------------------------------------------- where it is illegal

    /**
     * Whether running lamps here is an offence against this faction at all.
     * <p>
     * A window opened over people is an immediate hazard rather than a jurisdiction question: any
     * patrol that sees it comes over, regardless of whose flag is on the nearby world. Away from an
     * inhabited world, the transponder-law shape still applies. A flag polices only systems where it
     * holds something, and only the factions that object on principle - see
     * {@link #hatesLampsAnywhere} - object throughout those systems.
     */
    public static boolean isIllegalHere(CampaignFleetAPI player, String factionId) {
        if (player == null) return false;

        LocationAPI where = player.getContainingLocation();
        if (!(where instanceof StarSystemAPI)) return false;

        if (getNearbyInhabited(player) != null) return true;

        return ownsSystem((StarSystemAPI) where, factionId) && hatesLampsAnywhere(factionId);
    }

    /** The inhabited world the player is currently hanging over, if any. */
    public static MarketAPI getNearbyInhabited(CampaignFleetAPI player) {
        if (player == null || player.getContainingLocation() == null) return null;

        for (MarketAPI market : Misc.getMarketsInLocation(player.getContainingLocation())) {
            if (market.isPlanetConditionMarketOnly()) continue;

            SectorEntityToken at = market.getPrimaryEntity();
            if (at == null) continue;

            if (Misc.getDistance(player.getLocation(), at.getLocation()) <= PLANET_RANGE) {
                return market;
            }
        }

        return null;
    }

    /**
     * The nearest inhabited world, at any distance, for the rows that name what is being burned
     * over.
     * <p>
     * Separate from {@link #getNearbyInhabited} because the range test is about whether there is an
     * offence and this is about what to call it. A patrol that set off while the player was over
     * somewhere and arrives after they have drifted off it is still stopping them about that place,
     * and a line with a blank in it where a world should be would be worse than one naming a world
     * the player has just left.
     */
    public static String getClosestInhabitedName(CampaignFleetAPI player) {
        if (player == null || player.getContainingLocation() == null) return "";

        MarketAPI closest = null;
        float best = Float.MAX_VALUE;

        for (MarketAPI market : Misc.getMarketsInLocation(player.getContainingLocation())) {
            if (market.isPlanetConditionMarketOnly()) continue;

            SectorEntityToken at = market.getPrimaryEntity();
            if (at == null) continue;

            float distance = Misc.getDistance(player.getLocation(), at.getLocation());
            if (distance >= best) continue;

            best = distance;
            closest = market;
        }

        return closest == null ? player.getContainingLocation().getName() : closest.getName();
    }

    /** Whether a faction holds anything in this system, which is what makes it their space. */
    public static boolean ownsSystem(StarSystemAPI system, String factionId) {
        if (system == null || factionId == null) return false;

        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (factionId.equals(market.getFactionId())) return true;
        }

        return false;
    }

    //---------------------------------------------------------------- the ladder

    /**
     * The system-local memory that owns an offence history.
     * <p>
     * A system center is persistent and unique to the location, while the faction suffix below
     * keeps co-located authorities from inheriting one another's warning ladder.
     */
    protected static MemoryAPI getHistoryMemory(CampaignFleetAPI player) {
        if (player == null || !(player.getContainingLocation() instanceof StarSystemAPI)) {
            return null;
        }

        SectorEntityToken center = ((StarSystemAPI) player.getContainingLocation()).getCenter();
        return center == null ? null : center.getMemoryWithoutUpdate();
    }

    /** A faction's slot inside one system's history memory. */
    protected static String historyKey(String stem, String factionId) {
        return stem + "_" + factionId;
    }

    /**
     * How many times this faction has stopped the player in this system, forgetting the whole
     * business after long enough.
     * <p>
     * Read rather than ticked: nothing has to run on a clock for a count that is only ever asked
     * about during a conversation, and the arithmetic is the same either way.
     */
    public static int getCount(CampaignFleetAPI player, String factionId) {
        MemoryAPI history = getHistoryMemory(player);
        if (history == null || factionId == null) return 0;

        Object last = history.get(historyKey(LAST_KEY, factionId));

        if (last instanceof Long
                && Global.getSector().getClock().getElapsedDaysSince((Long) last) > FORGET_DAYS) {

            return 0;
        }

        return history.getInt(historyKey(COUNT_KEY, factionId));
    }

    /** Whether this faction's last local stop was recent enough to count as "again, this month". */
    public static boolean isRepeatWithinMonth(CampaignFleetAPI player, String factionId) {
        MemoryAPI history = getHistoryMemory(player);
        if (history == null || factionId == null) return false;

        Object last = history.get(historyKey(LAST_KEY, factionId));

        return last instanceof Long
                && Global.getSector().getClock().getElapsedDaysSince((Long) last) <= REPEAT_DAYS;
    }

    /**
     * Which rung this stop is on: 1 warning, 2 fine, 3 the hold, 4 the guns.
     * <p>
     * The fourth is only reached by doing it again inside a month. Anything slower than that holds
     * at the third rung rather than escalating forever - they want the lamps off, not a war.
     */
    public static int getRung(CampaignFleetAPI player, String factionId) {
        int count = getCount(player, factionId);

        if (count <= 0) return 1;
        if (count == 1) return 2;
        if (count == 2) return 3;

        return isRepeatWithinMonth(player, factionId) ? 4 : 3;
    }

    /** Books this faction's stop in this system, which is what moves its local ladder on. */
    public static void record(CampaignFleetAPI player, String factionId) {
        MemoryAPI history = getHistoryMemory(player);
        if (history == null || factionId == null) return;

        //read before the timestamp is moved, since getCount() decides on the old one whether the
        //whole business has lapsed
        int next = getCount(player, factionId) + 1;

        history.set(historyKey(COUNT_KEY, factionId), next);
        history.set(historyKey(LAST_KEY, factionId),
                Global.getSector().getClock().getTimestamp());
    }

    /**
     * Which burn of the lamps this is. Starts at one, so a crew that has never been stopped - and
     * therefore has no stored number at all, which reads as zero - never matches the current run.
     */
    public static int getRun() {
        return Math.max(1, Global.getSector().getMemoryWithoutUpdate().getInt(RUN_KEY));
    }

    /** Called when the lamps come on somewhere they should not be, which starts a fresh offence. */
    public static void beginRun() {
        Global.getSector().getMemoryWithoutUpdate().set(RUN_KEY, getRun() + 1);
    }

    /** Whether one of the patrols pursuing this burn has already opened the stop. */
    public static boolean isRunResolved() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(RESOLVED_RUN_KEY) == getRun();
    }

    /** Lets the first patrol to reach the player release every other responder to its old work. */
    public static void markRunResolved() {
        Global.getSector().getMemoryWithoutUpdate().set(RESOLVED_RUN_KEY, getRun());
    }

    /** Whether this crew has already had the conversation about the burn currently in progress. */
    public static boolean hasBeenTold(MemoryAPI mem) {
        return mem != null && mem.getInt(STOPPED_RUN_KEY) == getRun();
    }

    /** Books this crew as having said their piece about this burn and no other. */
    public static void markTold(MemoryAPI mem) {
        if (mem != null) mem.set(STOPPED_RUN_KEY, getRun());
    }

    /**
     * Takes this stop back off the ladder, for a player who talked their way out of it being
     * written down at all.
     * <p>
     * The stop is booked when the encounter opens, because by then it has happened. A story point
     * does not undo the encounter - the lamps still go out - but it does buy the crew deciding not
     * to file it, and a rung that stayed climbed after that would make the point worthless.
     */
    public static void forgive(CampaignFleetAPI player, String factionId) {
        MemoryAPI history = getHistoryMemory(player);
        if (history == null || factionId == null) return;

        history.set(historyKey(COUNT_KEY, factionId),
                Math.max(0, getCount(player, factionId) - 1));
    }

    /**
     * The standing cost of being stopped, printed into the conversation it happened in.
     * <p>
     * A custom impact rather than one of vanilla's named actions, floored at hostile so a player
     * who will not put the lamps out cannot be driven past the bottom of the scale by them alone.
     */
    public static void applyRepLoss(String factionId, float amount, TextPanelAPI text) {
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = -amount;
        impact.limit = RepLevel.HOSTILE;

        Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact, text, true), factionId);
    }
}
