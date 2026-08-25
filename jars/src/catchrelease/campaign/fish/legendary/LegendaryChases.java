package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishHabitat;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.map.FishPresence;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Every legendary is one fish, somewhere. It holds exactly one host system at a time,
 * re-occurs there until caught, moves on ninety days after a sighting, and once landed
 * it never spawns again. {@link catchrelease.campaign.fish.data.FishRanges} answers all
 * legendary range questions from here instead of the sheet's quadrants.
 */
public class LegendaryChases {

    public static final String KEY = "$catchrelease_legendary_chases";
    public static final float RELOCATION_DAYS = 90f;

    public static class Chase implements Serializable {

        public String systemId;
        public long seenAt;
        public boolean caught;

        // shield bookkeeping, semantics per species in LegendaryShields:
        // units = orbit motes or deflection charges, -1 until first initialised
        public boolean shieldPopped;
        public int shieldUnits = -1;
        public long shieldStampAt;
    }

    /** Ledger row without host assignment - shield state may be read before a host exists. */
    public static Chase getState(String speciesId) {
        if (speciesId == null || Global.getSector() == null) return new Chase();

        return getLedger().computeIfAbsent(speciesId, k -> new Chase());
    }

    public static boolean matches(FishSpec spec, LocationAPI where) {
        if (where == null) return false;

        Chase chase = getChase(spec);
        if (chase == null || chase.caught || chase.systemId == null) return false;

        return chase.systemId.equals(where.getId());
    }

    public static boolean isCaught(String speciesId) {
        Chase chase = speciesId == null ? null : getLedger().get(speciesId);

        return chase != null && chase.caught;
    }

    public static String getHostSystemId(FishSpec spec) {
        Chase chase = getChase(spec);

        return chase == null || chase.caught ? null : chase.systemId;
    }

    public static void noteSeen(FishSpec spec) {
        Chase chase = getChase(spec);
        if (chase == null || chase.caught) return;

        chase.seenAt = Global.getSector().getClock().getTimestamp();
    }

    public static void onCatch(catchrelease.campaign.fish.data.FishCatch specimen) {
        if (specimen == null) return;

        FishSpec spec = specimen.getSpec();
        if (spec == null || spec.rarity != FishRarity.LEGENDARY) return;

        noteCaught(spec.id);
    }

    public static void noteCaught(String speciesId) {
        if (speciesId == null || Global.getSector() == null) return;

        Chase chase = getLedger().computeIfAbsent(speciesId, k -> new Chase());
        chase.caught = true;
        chase.systemId = null;
    }

    protected static Chase getChase(FishSpec spec) {
        if (spec == null || spec.rarity != FishRarity.LEGENDARY
                || Global.getSector() == null) {
            return null;
        }

        Map<String, Chase> ledger = getLedger();
        Chase chase = ledger.get(spec.id);

        if (chase == null) {
            chase = new Chase();
            chase.systemId = pickHost(spec, null);
            ledger.put(spec.id, chase);
        } else if (!chase.caught && isDueToMove(chase) && !isPlayerIn(chase.systemId)) {
            // the cooldown ran out unseen-side only: it never moves out from under a chase
            chase.systemId = pickHost(spec, chase.systemId);
            chase.seenAt = 0L;
        }

        return chase;
    }

    protected static boolean isDueToMove(Chase chase) {
        return chase.seenAt > 0L && Global.getSector().getClock()
                .getElapsedDaysSince(chase.seenAt) >= RELOCATION_DAYS;
    }

    protected static boolean isPlayerIn(String systemId) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        return player != null && systemId != null
                && player.getContainingLocation() != null
                && systemId.equals(player.getContainingLocation().getId());
    }

    protected static String pickHost(FishSpec spec, String avoid) {
        boolean abyssal = spec.regions.contains(SectorRegion.ABYSSAL);

        // sheet gates first; a sector that never rolled a matching system relaxes rung by rung
        for (int relax = FishSpec.RELAX_NONE; relax <= FishSpec.RELAX_REGIONS; relax++) {
            List<String> candidates = new ArrayList<>();

            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (!abyssal && !FishPresence.isChartable(system)) continue;
                if (system.getId().equals(avoid)) continue;
                if (spec.matches(FishHabitat.of(system), null, relax)) {
                    candidates.add(system.getId());
                }
            }

            if (!candidates.isEmpty()) {
                return candidates.get(new Random().nextInt(candidates.size()));
            }
        }

        return avoid;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Chase> getLedger() {
        if (Global.getSector() == null) return new LinkedHashMap<>();

        Object stored = Global.getSector().getPersistentData().get(KEY);
        if (stored instanceof Map) return (Map<String, Chase>) stored;

        Map<String, Chase> ledger = new LinkedHashMap<>();
        Global.getSector().getPersistentData().put(KEY, ledger);

        return ledger;
    }
}
