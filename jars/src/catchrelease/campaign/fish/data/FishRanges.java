package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.map.FishPresence;
import catchrelease.campaign.fish.shop.FishAsker;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.TransientMemory;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a species can actually spawn in this sector, right now. Sits between the sheet and
 * every runtime question: pinned quest targets answer from a frozen system list, everything
 * else answers from {@link FishSpec#matches} at the species' current relaxation level.
 * Reassessed at the end of each month because coherence sources move - gates light, slipstorms
 * shift - and a sheet row whose gates found no systems in this particular sector is relaxed
 * one rung at a time until it has somewhere to live.
 */
public class FishRanges {

    public static final String RELAX_KEY = "$catchrelease_range_relax";
    public static final String PIN_KEY = "$catchrelease_range_pins";
    public static final String STAMP_KEY = "$catchrelease_range_stamp";

    public static final int MIN_HOMES = 3;
    public static final int SYSTEM_CAP = 15;

    public static class Reassessor extends BaseCampaignEventListener {

        public Reassessor() {
            super(false);
        }

        @Override
        public void reportEconomyMonthEnd() {
            reassess();
        }
    }

    public static void register() {
        Global.getSector().addTransientListener(new Reassessor());

        if (!Global.getSector().getPersistentData().containsKey(STAMP_KEY)) reassess();
    }

    public static boolean matches(FishSpec spec, LocationAPI where, CatchImplement how) {
        if (spec == null || where == null) return false;

        Set<String> frozen = getPins().get(spec.id);
        if (frozen != null) return frozen.contains(where.getId()) && spec.canBeReachedBy(how);

        return spec.matches(FishHabitat.of(where), how, getRelax(spec.id));
    }

    public static int getRelax(String speciesId) {
        Object level = speciesId == null ? null : getRelaxMap().get(speciesId);

        return level instanceof Integer ? (Integer) level : FishSpec.RELAX_NONE;
    }

    public static boolean isPinned(String speciesId) {
        return speciesId != null && getPins().containsKey(speciesId);
    }

    public static void reassess() {
        Map<String, Set<String>> pins = getPins();
        Set<String> questSpecies = collectQuestSpecies();

        // freeze against the pre-reassessment world, so a quest keeps the range it was rolled in
        for (String id : questSpecies) {
            if (!pins.containsKey(id)) pins.put(id, snapshotHomes(id));
        }
        pins.keySet().retainAll(questSpecies);

        // habitats re-read from here on: coherence is the input that moves under a running game
        TransientMemory.getInstance().unset(FishHabitat.CACHE_KEY);

        recomputeRelax(pins);

        Global.getSector().getPersistentData()
                .put(STAMP_KEY, Global.getSector().getClock().getTimestamp());
    }

    protected static Set<String> collectQuestSpecies() {
        Set<String> out = new LinkedHashSet<>();
        if (Global.getSector() == null) return out;

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
            if (intel.isEnding() || intel.isEnded()) continue;
            if (!(intel instanceof FishAsker asker)) continue;

            for (FishRequirement ask : asker.getAsks()) {
                if (ask == null) continue;
                if (ask.speciesId != null) out.add(ask.speciesId);

                for (FishRequirement alternative : ask.anyOf) {
                    if (alternative != null && alternative.speciesId != null) {
                        out.add(alternative.speciesId);
                    }
                }
            }
        }

        return out;
    }

    protected static Set<String> snapshotHomes(String speciesId) {
        Set<String> homes = new LinkedHashSet<>();

        FishSpec spec = FishSpecLoader.getFishSpec(speciesId);
        if (spec == null) return homes;

        int relax = getRelax(speciesId);
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (spec.matches(FishHabitat.of(system), null, relax)) homes.add(system.getId());
        }

        return homes;
    }

    protected static void recomputeRelax(Map<String, Set<String>> pins) {
        List<StarSystemAPI> systems = new ArrayList<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (FishPresence.isChartable(system)) systems.add(system);
        }

        Map<String, Integer> load = new HashMap<>();
        for (Set<String> frozen : pins.values()) {
            for (String id : frozen) load.merge(id, 1, Integer::sum);
        }

        List<FishSpec> free = new ArrayList<>();
        Map<String, Set<String>> baseHomes = new HashMap<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || pins.containsKey(spec.id)) continue;
            if (spec.regions.contains(SectorRegion.ABYSSAL)) continue;

            Set<String> homes = homesAt(spec, systems, FishSpec.RELAX_NONE);
            baseHomes.put(spec.id, homes);
            for (String id : homes) load.merge(id, 1, Integer::sum);
            free.add(spec);
        }

        // most starved first, so they claim cap headroom before anyone else needs it
        free.sort((a, b) -> Integer.compare(baseHomes.get(a.id).size(), baseHomes.get(b.id).size()));

        Map<String, Object> relax = new LinkedHashMap<>();
        for (FishSpec spec : free) {
            Set<String> homes = baseHomes.get(spec.id);
            int level = FishSpec.RELAX_NONE;

            while (homes.size() < MIN_HOMES && level < FishSpec.RELAX_REGIONS) {
                Set<String> widened = homesAt(spec, systems, level + 1);

                boolean fits = true;
                for (String id : widened) {
                    if (!homes.contains(id) && load.getOrDefault(id, 0) >= SYSTEM_CAP) {
                        fits = false;
                        break;
                    }
                }
                if (!fits) break;

                for (String id : widened) {
                    if (!homes.contains(id)) load.merge(id, 1, Integer::sum);
                }
                homes = widened;
                level++;
            }

            if (level > FishSpec.RELAX_NONE) relax.put(spec.id, level);
        }

        Global.getSector().getPersistentData().put(RELAX_KEY, relax);
    }

    protected static Set<String> homesAt(FishSpec spec, List<StarSystemAPI> systems, int relax) {
        Set<String> homes = new LinkedHashSet<>();

        for (StarSystemAPI system : systems) {
            if (spec.matches(FishHabitat.of(system), null, relax)) homes.add(system.getId());
        }

        return homes;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> getRelaxMap() {
        if (Global.getSector() == null) return new LinkedHashMap<>();

        Object stored = Global.getSector().getPersistentData().get(RELAX_KEY);
        if (stored instanceof Map) return (Map<String, Object>) stored;

        Map<String, Object> relax = new LinkedHashMap<>();
        Global.getSector().getPersistentData().put(RELAX_KEY, relax);

        return relax;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Set<String>> getPins() {
        if (Global.getSector() == null) return new LinkedHashMap<>();

        Object stored = Global.getSector().getPersistentData().get(PIN_KEY);
        if (stored instanceof Map) return (Map<String, Set<String>>) stored;

        Map<String, Set<String>> pins = new LinkedHashMap<>();
        Global.getSector().getPersistentData().put(PIN_KEY, pins);

        return pins;
    }
}
