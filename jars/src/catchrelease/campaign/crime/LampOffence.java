package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
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

    /** How many times the player has been stopped for this, and when the last one was. */
    public static final String COUNT_KEY = "$catchrelease_lampOffences";
    public static final String LAST_KEY = "$catchrelease_lampLast";

    /** Set on a patrol that has seen the lamps lit somewhere they are not allowed. */
    public static final String SAW_KEY = "$catchrelease_sawLamps";

    /** Set once a patrol has had the conversation, so one stop is one stop. */
    public static final String STOPPED_KEY = "$catchrelease_lampStopped";

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
        return Factions.LUDDIC_CHURCH.equals(factionId) || Factions.LUDDIC_PATH.equals(factionId);
    }

    //---------------------------------------------------------------- where it is illegal

    /**
     * Whether running lamps here is an offence against this faction at all.
     * <p>
     * System-bound first, the way the transponder law is: a flag polices the systems it holds
     * something in and nowhere else, so a Hegemony picket three jumps from anything Hegemony has
     * nothing to say about what the player is doing with the fabric out there.
     * <p>
     * Inside their own space there are two ways to be in trouble. Everybody objects to a window
     * opened over people, which means close enough to somewhere inhabited to be over it. The ones
     * who object on principle - see {@link #hatesLampsAnywhere} - object anywhere in the system,
     * planet or no planet, which is the difference between a regulation and a doctrine.
     */
    public static boolean isIllegalHere(CampaignFleetAPI player, String factionId) {
        if (player == null) return false;

        LocationAPI where = player.getContainingLocation();
        if (!(where instanceof StarSystemAPI)) return false;

        if (!ownsSystem((StarSystemAPI) where, factionId)) return false;

        if (hatesLampsAnywhere(factionId)) return true;

        return getNearbyInhabited(player) != null;
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
     * How many times this has happened, forgetting the whole business after long enough.
     * <p>
     * Read rather than ticked: nothing has to run on a clock for a count that is only ever asked
     * about during a conversation, and the arithmetic is the same either way.
     */
    public static int getCount() {
        Object last = Global.getSector().getMemoryWithoutUpdate().get(LAST_KEY);

        if (last instanceof Long
                && Global.getSector().getClock().getElapsedDaysSince((Long) last) > FORGET_DAYS) {

            return 0;
        }

        return Global.getSector().getMemoryWithoutUpdate().getInt(COUNT_KEY);
    }

    /** Whether the last stop was recent enough that this one counts as "again, this month". */
    public static boolean isRepeatWithinMonth() {
        Object last = Global.getSector().getMemoryWithoutUpdate().get(LAST_KEY);

        return last instanceof Long
                && Global.getSector().getClock().getElapsedDaysSince((Long) last) <= REPEAT_DAYS;
    }

    /**
     * Which rung this stop is on: 1 warning, 2 fine, 3 the hold, 4 the guns.
     * <p>
     * The fourth is only reached by doing it again inside a month. Anything slower than that holds
     * at the third rung rather than escalating forever - they want the lamps off, not a war.
     */
    public static int getRung() {
        int count = getCount();

        if (count <= 0) return 1;
        if (count == 1) return 2;
        if (count == 2) return 3;

        return isRepeatWithinMonth() ? 4 : 3;
    }

    /** Books this stop, which is what moves the ladder on. */
    public static void record() {
        //read before the timestamp is moved, since getCount() decides on the old one whether the
        //whole business has lapsed
        int next = getCount() + 1;

        Global.getSector().getMemoryWithoutUpdate().set(COUNT_KEY, next);
        Global.getSector().getMemoryWithoutUpdate().set(LAST_KEY,
                Global.getSector().getClock().getTimestamp());
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
