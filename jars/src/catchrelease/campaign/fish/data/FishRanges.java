package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.legendary.LegendaryChases;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a species can actually spawn in this sector, right now. Sits between the sheet and
 * every runtime question: pinned quest targets answer from a frozen system list, everything
 * else answers from {@link FishSpec#matches} at the species' current relaxation level, limited
 * to the species each charted system admits under {@link #SYSTEM_CAP}.
 * Reassessed at the end of each month because coherence sources move - gates light, slipstorms
 * shift - and a sheet row whose gates found no systems in this particular sector is relaxed
 * one rung at a time until it has somewhere to live.
 */
public class FishRanges {

    public static final String RELAX_KEY = "$catchrelease_range_relax";
    public static final String PIN_KEY = "$catchrelease_range_pins";
    public static final String STAMP_KEY = "$catchrelease_range_stamp";
    public static final String ADMIT_KEY = "$catchrelease_range_admit";

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

        Map<String, Object> data = Global.getSector().getPersistentData();
        if (!data.containsKey(STAMP_KEY) || !data.containsKey(ADMIT_KEY)) reassess();
    }

    public static boolean matches(FishSpec spec, LocationAPI where, CatchImplement how) {
        if (spec == null || where == null) return false;

        // a legendary is one fish in one system; the ledger, not the sheet, says which
        if (spec.rarity == FishRarity.LEGENDARY) {
            return LegendaryChases.matches(spec, where) && spec.canBeReachedBy(how);
        }

        Set<String> frozen = getPins().get(spec.id);
        if (frozen != null) return frozen.contains(where.getId()) && spec.canBeReachedBy(how);
        if (!isAdmitted(spec.id, where)) return false;

        return spec.matches(FishHabitat.of(where), how, getRelax(spec.id));
    }

    protected static boolean isAdmitted(String speciesId, LocationAPI where) {
        List<String> ranked = getAdmissions().get(where.getId());
        // no list: outside the cap, like the Abyss and systems cut off from hyperspace
        if (ranked == null) return true;

        // a legendary is a temporary guest: while hosted here it takes the last admitted slot
        int slots = SYSTEM_CAP - LegendaryChases.countHostedIn(where.getId());
        int index = ranked.indexOf(speciesId);

        return index >= 0 && index < slots;
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

        // freeze against the pre-reassessment world, so a quest keeps the range it was rolled in;
        // legendaries never pin - their single-host ledger already outranks pins in matches()
        for (String id : questSpecies) {
            FishSpec spec = FishSpecLoader.getFishSpec(id);
            if (spec != null && spec.rarity == FishRarity.LEGENDARY) continue;
            if (!pins.containsKey(id)) pins.put(id, snapshotHomes(id));
        }
        pins.keySet().retainAll(questSpecies);

        // habitats re-read from here on: coherence is the input that moves under a running game
        TransientMemory.getInstance().unset(FishHabitat.CACHE_KEY);

        recomputeRelax(pins);
        recomputeAdmissions(pins);

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

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (matches(spec, system, null)) homes.add(system.getId());
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
            // mechanism rows (the Quorum's splinter) never spawn from ranges: no cap slot
            if (spec.spawnWeight <= 0f) continue;

            // a legendary never relaxes, but its one host still occupies a cap slot
            if (spec.rarity == FishRarity.LEGENDARY) {
                String host = LegendaryChases.getHostSystemId(spec);
                if (host != null) load.merge(host, 1, Integer::sum);
                continue;
            }

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

    // Ranks each chartable system's species; matches() admits the first SYSTEM_CAP, minus hosted legendaries.
    // Pinned quest species rank first, then species with the fewest homes, which lose the most when dropped.
    // A species that only starts matching later in the month has no rank until the next reassessment.
    protected static void recomputeAdmissions(Map<String, Set<String>> pins) {
        List<StarSystemAPI> systems = new ArrayList<>();
        Map<String, List<String>> ranked = new LinkedHashMap<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!FishPresence.isChartable(system)) continue;

            systems.add(system);
            ranked.put(system.getId(), new ArrayList<>());
        }

        Map<String, Integer> homeCounts = new HashMap<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (spec.rarity == FishRarity.LEGENDARY || spec.spawnWeight <= 0f) continue;

            Set<String> frozen = pins.get(spec.id);
            Set<String> homes = frozen != null ? frozen : homesAt(spec, systems, getRelax(spec.id));
            homeCounts.put(spec.id, homes.size());

            for (String id : homes) {
                List<String> species = ranked.get(id);
                if (species != null) species.add(spec.id);
            }
        }

        Comparator<String> priority = Comparator
                .comparing((String id) -> !pins.containsKey(id))
                .thenComparing(id -> homeCounts.getOrDefault(id, 0))
                .thenComparing(id -> id);
        for (List<String> species : ranked.values()) {
            species.sort(priority);
        }

        Global.getSector().getPersistentData().put(ADMIT_KEY, ranked);
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
    protected static Map<String, List<String>> getAdmissions() {
        if (Global.getSector() == null) return new LinkedHashMap<>();

        Object stored = Global.getSector().getPersistentData().get(ADMIT_KEY);
        return stored instanceof Map ? (Map<String, List<String>>) stored : new LinkedHashMap<>();
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
