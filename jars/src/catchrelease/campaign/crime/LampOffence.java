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


public class LampOffence {


    public static final String COUNT_KEY = "$catchrelease_lampOffences";
    public static final String LAST_KEY = "$catchrelease_lampLast";


    public static final String SAW_KEY = "$catchrelease_sawLamps";


    public static final String STOPPED_KEY = "$catchrelease_lampStopped";


    public static final String RUN_KEY = "$catchrelease_lampRun";
    public static final String STOPPED_RUN_KEY = "$catchrelease_lampStoppedRun";


    public static final String RESOLVED_RUN_KEY = "$catchrelease_lampResolvedRun";


    public static final float PLANET_RANGE = 3000f;


    public static final float FORGET_DAYS = 90f;
    public static final float REPEAT_DAYS = 30f;


    public static final int FINE = 25000;


    public static final float REP_LOSS = 0.03f;
    public static final float REP_REFUSE = 0.06f;


    public static boolean hatesLampsAnywhere(String factionId) {
        return FishingTaboo.isTaboo(factionId);
    }


    public static boolean isIllegalHere(CampaignFleetAPI player, String factionId) {
        if (player == null) return false;

        LocationAPI where = player.getContainingLocation();
        if (!(where instanceof StarSystemAPI)) return false;

        if (getNearbyInhabited(player) != null) return true;

        return ownsSystem((StarSystemAPI) where, factionId) && hatesLampsAnywhere(factionId);
    }


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


    public static boolean ownsSystem(StarSystemAPI system, String factionId) {
        if (system == null || factionId == null) return false;

        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (factionId.equals(market.getFactionId())) return true;
        }

        return false;
    }


    protected static MemoryAPI getHistoryMemory(CampaignFleetAPI player) {
        if (player == null || !(player.getContainingLocation() instanceof StarSystemAPI)) {
            return null;
        }

        SectorEntityToken center = ((StarSystemAPI) player.getContainingLocation()).getCenter();
        return center == null ? null : center.getMemoryWithoutUpdate();
    }


    protected static String historyKey(String stem, String factionId) {
        return stem + "_" + factionId;
    }


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


    public static boolean isRepeatWithinMonth(CampaignFleetAPI player, String factionId) {
        MemoryAPI history = getHistoryMemory(player);
        if (history == null || factionId == null) return false;

        Object last = history.get(historyKey(LAST_KEY, factionId));

        return last instanceof Long
                && Global.getSector().getClock().getElapsedDaysSince((Long) last) <= REPEAT_DAYS;
    }


    public static int getRung(CampaignFleetAPI player, String factionId) {
        int count = getCount(player, factionId);

        if (count <= 0) return 1;
        if (count == 1) return 2;
        if (count == 2) return 3;

        return isRepeatWithinMonth(player, factionId) ? 4 : 3;
    }


    public static void record(CampaignFleetAPI player, String factionId) {
        MemoryAPI history = getHistoryMemory(player);
        if (history == null || factionId == null) return;

        // read before the timestamp is moved, since getCount() decides on the old one whether the whole business has lapsed
        int next = getCount(player, factionId) + 1;

        history.set(historyKey(COUNT_KEY, factionId), next);
        history.set(historyKey(LAST_KEY, factionId),
                Global.getSector().getClock().getTimestamp());
    }


    public static int getRun() {
        return Math.max(1, Global.getSector().getMemoryWithoutUpdate().getInt(RUN_KEY));
    }


    public static void beginRun() {
        Global.getSector().getMemoryWithoutUpdate().set(RUN_KEY, getRun() + 1);
    }


    public static boolean isRunResolved() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(RESOLVED_RUN_KEY) == getRun();
    }


    public static void markRunResolved() {
        Global.getSector().getMemoryWithoutUpdate().set(RESOLVED_RUN_KEY, getRun());
    }


    public static boolean hasBeenTold(MemoryAPI mem) {
        return mem != null && mem.getInt(STOPPED_RUN_KEY) == getRun();
    }


    public static void markTold(MemoryAPI mem) {
        if (mem != null) mem.set(STOPPED_RUN_KEY, getRun());
    }


    public static void forgive(CampaignFleetAPI player, String factionId) {
        MemoryAPI history = getHistoryMemory(player);
        if (history == null || factionId == null) return;

        history.set(historyKey(COUNT_KEY, factionId),
                Math.max(0, getCount(player, factionId) - 1));
    }


    public static void applyRepLoss(String factionId, float amount, TextPanelAPI text) {
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = -amount;
        impact.limit = RepLevel.HOSTILE;

        Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact, text, true), factionId);
    }
}
