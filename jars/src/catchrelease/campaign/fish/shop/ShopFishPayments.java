package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.map.FishPresence;
import catchrelease.campaign.fish.map.FishType;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.upgrades.StatIds;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class ShopFishPayments {

    private enum Modifier {
        GRADE, LENGTH, WEIGHT, COHERENCE, METHOD, IMPLEMENT, RARITY
    }

    private static final int[][] RARITY_WEIGHTS = {
            {1, 0, 0, 0}, {3, 2, 0, 0}, {2, 3, 2, 0}, {2, 3, 3, 1}, {1, 2, 4, 3}
    };

    public record Opportunity(FishSpec spec, FishLogEntry.Method method,
                              CatchImplement implement, boolean lowCoherence) {
    }

    protected static List<Opportunity> availableCatches() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return Collections.emptyList();
        boolean drone = fleet.hasAbility(StatIds.ROD_ABILITY);
        boolean harpoon = fleet.hasAbility(StatIds.HARPOON_ABILITY);
        if (!drone && !harpoon) return Collections.emptyList();
        boolean lamps = fleet.hasAbility(StatIds.LAMPS_ABILITY);
        boolean coupled = drone && lamps && Tackle.BREACH_COUPLER.breachCoupling
                && TackleManager.isOwned(Tackle.BREACH_COUPLER);
        Map<String, Opportunity> catches = new LinkedHashMap<>();
        List<FishSpec> specs = FishSpecLoader.getAllFishSpecs();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!FishPresence.isChartable(system)) continue;
            boolean unstable = !Aberration.isColonySystem(system)
                    && Math.min(Aberration.naturalAt(system.getLocation(), system),
                    Aberration.baseAt(system.getLocation(), system)) >= FishRequirement.LOW_COHERENCE;
            for (FishSpec spec : specs) {
                if (!ordinary(spec)) continue;
                // PondCreator provides a pond on entry even in systems without map data yet.
                if (FishRanges.matches(spec, system, CatchImplement.POND)) {
                    if (drone) add(catches, spec, FishLogEntry.Method.DRONE, CatchImplement.POND, unstable);
                    if (harpoon) add(catches, spec, FishLogEntry.Method.HARPOON, CatchImplement.POND, unstable);
                }
                if (lamps && FishRanges.matches(spec, system, CatchImplement.BREACH_LAMP)) {
                    if (harpoon) add(catches, spec, FishLogEntry.Method.HARPOON, CatchImplement.BREACH_LAMP, unstable);
                    if (coupled) add(catches, spec, FishLogEntry.Method.DRONE, CatchImplement.BREACH_LAMP, unstable);
                }
            }
        }
        return new ArrayList<>(catches.values());
    }

    private static void add(Map<String, Opportunity> catches, FishSpec spec,
                            FishLogEntry.Method method, CatchImplement implement, boolean unstable) {
        String key = spec.id + ":" + method + ":" + implement;
        Opportunity previous = catches.get(key);
        boolean low = !spec.tags.contains("abyssal") && unstable;
        if (previous == null || low && !previous.lowCoherence) {
            catches.put(key, new Opportunity(spec, method, implement, low));
        }
    }

    protected static boolean ordinary(FishSpec spec) {
        return spec != null && spec.id != null && spec.spawnWeight > 0f
                && spec.rarity != null && spec.rarity != FishRarity.LEGENDARY
                && !spec.tags.contains("special");
    }

    protected static FishRequirement generate(Random random, int band, List<Opportunity> catches,
                                              Collection<FishRequirement> saved) {
        band = Math.max(0, Math.min(4, band));
        Map<String, List<Opportunity>> byPattern = new LinkedHashMap<>();
        catches.stream().filter(catchable -> ordinary(catchable.spec))
                .sorted(Comparator.comparing(catchable -> catchable.spec.id))
                .forEach(catchable -> byPattern.computeIfAbsent(catchable.spec.id,
                        ignored -> new ArrayList<>()).add(catchable));
        if (byPattern.isEmpty()) return null;

        int family = random.nextInt(10);
        List<Opportunity> chosen = pickPattern(random, band, byPattern, saved);
        if (chosen == null) return null;
        Opportunity catchable = chosen.get(random.nextInt(chosen.size()));
        FishSpec spec = catchable.spec;
        FishRequirement req = new FishRequirement();
        if (family < 5) req.speciesId = spec.id;
        else if (family < 8) {
            req.tag = FishType.of(spec).name().toLowerCase(Locale.ROOT);
            if (!spec.tags.contains(req.tag)) {
                req.tag = null;
                req.speciesId = spec.id;
                family = 0;
            }
        }
        else req.sameSpecies = true;

        int modifiers = family < 5 ? (band > 0 || random.nextBoolean() ? 1 : 0)
                : family < 8 ? 2 : 1;
        List<Modifier> order = new ArrayList<>(List.of(Modifier.values()));
        Collections.shuffle(order, random);
        for (Modifier modifier : order) {
            if (modifiers == 0) break;
            if (apply(req, modifier, catchable, band)) modifiers--;
        }
        if (modifiers > 0) {
            // Fixed-size third-party patterns may not support two distinct modifiers.
            req = new FishRequirement();
            req.speciesId = spec.id;
        }

        int count = 2 + band / 2;
        if (req.speciesId != null && spec.rarity.rank >= FishRarity.RARE.rank) count--;
        if (req.lowCoherence || req.minGrade == FishGrade.FINE
                || req.minLength > 0f || req.minWeight > 0f) count--;
        req.count = Math.max(req.sameSpecies ? 2 : 1, Math.min(4, count));
        return req;
    }

    private static List<Opportunity> pickPattern(Random random, int band,
                                                 Map<String, List<Opportunity>> patterns,
                                                 Collection<FishRequirement> saved) {
        Map<String, Integer> uses = new LinkedHashMap<>();
        for (FishRequirement req : saved) {
            if (req.speciesId != null) uses.merge(req.speciesId, 1, Integer::sum);
        }
        List<FishRarity> rarities = new ArrayList<>();
        for (FishRarity rarity : FishRarity.values()) {
            if (rarity == FishRarity.LEGENDARY) continue;
            boolean present = patterns.values().stream().anyMatch(options -> options.get(0).spec.rarity == rarity);
            if (present) {
                for (int i = 0; i < RARITY_WEIGHTS[band][rarity.rank]; i++) rarities.add(rarity);
            }
        }
        // A modded sector may have no lower-rarity catches at all. Do not invent an unavailable one.
        if (rarities.isEmpty()) {
            for (List<Opportunity> options : patterns.values()) rarities.add(options.get(0).spec.rarity);
        }
        FishRarity rarity = rarities.get(random.nextInt(rarities.size()));
        List<List<Opportunity>> pool = new ArrayList<>();
        int leastUsed = Integer.MAX_VALUE;
        for (Map.Entry<String, List<Opportunity>> entry : patterns.entrySet()) {
            if (entry.getValue().get(0).spec.rarity != rarity) continue;
            int used = uses.getOrDefault(entry.getKey(), 0);
            if (used < leastUsed) {
                pool.clear();
                leastUsed = used;
            }
            if (used == leastUsed) pool.add(entry.getValue());
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private static boolean apply(FishRequirement req, Modifier modifier, Opportunity catchable, int band) {
        FishSpec spec = catchable.spec;
        boolean sized = req.minGrade != null || req.minLength > 0f || req.minWeight > 0f;
        switch (modifier) {
            case GRADE:
                if (sized) return false;
                FishGrade grade = band >= 3 ? FishGrade.FINE : FishGrade.AVERAGE;
                float size = ((spec.lengthMax > spec.lengthMin ? 0.95f : 0.5f)
                        + (spec.weightMax > spec.weightMin ? 0.95f : 0.5f)) * 0.5f;
                if (FishGrade.of(size).rank < grade.rank) return false;
                req.minGrade = grade;
                return true;
            case LENGTH:
            case WEIGHT:
                if (sized) return false;
                float min = modifier == Modifier.LENGTH ? spec.lengthMin : spec.weightMin;
                float max = modifier == Modifier.LENGTH ? spec.lengthMax : spec.weightMax;
                float threshold = Math.round((min + (max - min) * (0.5f + band * 0.05f)) * 10f) / 10f;
                // The shared description prints tenths. Reject thresholds that round outside the useful range.
                if (threshold <= min || threshold >= max) return false;
                if (modifier == Modifier.LENGTH) req.minLength = threshold;
                else req.minWeight = threshold;
                return true;
            case COHERENCE:
                if (!catchable.lowCoherence || spec.tags.contains("abyssal")) return false;
                req.lowCoherence = true;
                return true;
            case METHOD:
                if (req.implement != null) return false;
                req.method = catchable.method;
                return true;
            case IMPLEMENT:
                if (req.method != null) return false;
                req.implement = catchable.implement;
                return true;
            case RARITY:
                if (req.speciesId != null || spec.rarity == FishRarity.COMMON) return false;
                req.minRarity = spec.rarity;
                return true;
            default:
                return false;
        }
    }
}
