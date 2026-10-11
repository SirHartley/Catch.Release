package catchrelease.tools;

import catchrelease.campaign.fish.shop.ShopPricing;
import catchrelease.campaign.fish.shop.ShopFishPayments;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.ShopEntry;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.jobs.FishHandoffPicker;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.QuestRewards;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.TransientMemory;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.config.PlayerSettings;
import catchrelease.config.Setting;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class ShopPricingChecks {

    private static int checks;

    private static final class Payments extends ShopFishPayments {

        static List<Opportunity> available() { return availableCatches(); }
        static boolean eligible(FishSpec spec) { return ordinary(spec); }
        static FishRequirement roll(long seed, int band, List<Opportunity> pool,
                                    Collection<FishRequirement> saved) {
            return generate(new Random(seed), band, pool, saved);
        }
    }

    private static final class Fixture implements AutoCloseable {

        final Map<String, Object> data = new LinkedHashMap<>();
        final Map<String, Object> memory = new LinkedHashMap<>();
        final Set<String> abilities = new HashSet<>();
        final List<StarSystemAPI> systems = new ArrayList<>();
        final Map<SpecialItemData, Integer> cargo = new LinkedHashMap<>();
        final TransientMemory transientMemory = new TransientMemory();
        final Map<String, Set<String>> pins = new LinkedHashMap<>();
        final CargoAPI cargoApi = proxy(CargoAPI.class, (self, method, args) -> switch (method.getName()) {
            case "getStacksCopy" -> cargo.entrySet().stream().map(entry -> proxy(CargoStackAPI.class,
                    (stack, call, params) -> switch (call.getName()) {
                        case "getSpecialDataIfSpecial" -> entry.getKey();
                        case "getSize" -> (float) entry.getValue();
                        default -> throw new AssertionError(call);
                    })).toList();
            case "removeItems" -> {
                SpecialItemData item = (SpecialItemData) args[1];
                int left = cargo.get(item) - ((Number) args[2]).intValue();
                if (left <= 0) cargo.remove(item); else cargo.put(item, left);
                yield true;
            }
            case "addSpecial" -> { cargo.merge((SpecialItemData) args[0], ((Number) args[1]).intValue(), Integer::sum); yield null; }
            case "getQuantity" -> (float) cargo.getOrDefault(args[1], 0);
            default -> throw new AssertionError(method);
        });
        final CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
            case "hasAbility" -> abilities.contains(args[0]);
            case "getCargo" -> cargoApi;
            default -> throw new AssertionError(method);
        });

        Fixture() throws Exception {
            if (Global.getSector() != null || Global.getSettings() != null) throw new IllegalStateException("Run outside Starsector");
            JSONArray fish = spreadsheet("data/campaign/fish.csv");
            Global.setSettings(proxy(SettingsAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getMergedSpreadsheetDataForMod" -> fish;
                case "getColor" -> Color.YELLOW;
                case "getFloat" -> 1f;
                case "getInt" -> 1;
                case "getBoolean", "isDevMode" -> false;
                default -> throw new AssertionError(method);
            }));
            GenericPluginManagerAPI plugins = proxy(GenericPluginManagerAPI.class, (self, method, args) -> switch (method.getName()) {
                case "hasPlugin" -> true;
                case "getPluginsOfClass" -> List.of(transientMemory);
                default -> throw new AssertionError(method);
            });
            MemoryAPI mem = proxy(MemoryAPI.class, (self, method, args) -> switch (method.getName()) {
                case "get" -> memory.get(args[0]);
                case "set" -> { require(args.length == 2, "campaign seed has no expiry"); memory.put((String) args[0], args[1]); yield null; }
                default -> throw new AssertionError(method);
            });
            StarSystemAPI system = proxy(StarSystemAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getId", "getBaseName" -> "shop-test-system";
                case "hasTag" -> false;
                default -> throw new AssertionError(method);
            });
            MarketAPI colony = proxy(MarketAPI.class, (self, method, args) -> switch (method.getName()) {
                case "isInEconomy" -> true;
                case "isPlanetConditionMarketOnly" -> false;
                case "getSize" -> 3;
                case "getStarSystem" -> system;
                case "getLocationInHyperspace" -> new Vector2f();
                default -> throw new AssertionError(method);
            });
            EconomyAPI economy = proxy(EconomyAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getMarkets" -> List.of(colony);
                default -> throw new AssertionError(method);
            });
            Global.setSector(proxy(SectorAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getPersistentData" -> data;
                case "getMemoryWithoutUpdate" -> mem;
                case "getGenericPlugins" -> plugins;
                case "getPlayerFleet" -> fleet;
                case "getStarSystems" -> systems;
                case "getEconomy" -> economy;
                default -> throw new AssertionError(method);
            }));
            systems.add(system);
            data.put(FishRanges.PIN_KEY, pins);
            for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) pins.put(spec.id, Set.of(system.getId()));
        }

        public void close() {
            Global.setSector(null);
            Global.setSettings(null);
        }
    }

    public static void main(String[] args) throws Exception {
        creditPrices();
        try (Fixture fixture = new Fixture()) {
            recipes(fixture);
            availabilityAndPersistence(fixture);
            sharedPayment(fixture);
            settings(fixture);
        }
        System.out.println("Shop pricing checks passed: " + checks);
    }

    private static JSONArray spreadsheet(String file) throws Exception {
        List<List<FishCsv.Cell>> rows = FishCsv.parse(Files.readString(Path.of(file)));
        List<FishCsv.Cell> header = rows.get(0);
        JSONArray array = new JSONArray();
        for (List<FishCsv.Cell> row : rows.subList(1, rows.size())) {
            if (row.size() != header.size() || row.get(0).value().startsWith("#")) continue;
            JSONObject entry = new JSONObject();
            for (int i = 0; i < row.size(); i++) if (!row.get(i).value().isEmpty()) entry.put(header.get(i).value(), row.get(i).value());
            array.put(entry);
        }
        return array;
    }

    private static void recipes(Fixture fixture) {
        List<ShopFishPayments.Opportunity> pool = new ArrayList<>();
        Set<String> eligible = new HashSet<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (!Payments.eligible(spec)) continue;
            eligible.add(spec.id);
            for (CatchImplement implement : List.of(CatchImplement.POND, CatchImplement.BREACH_LAMP)) {
                if (!spec.canBeReachedBy(implement)) continue;
                for (FishLogEntry.Method method : List.of(FishLogEntry.Method.DRONE, FishLogEntry.Method.HARPOON)) {
                    pool.add(new ShopFishPayments.Opportunity(spec, method, implement, !spec.tags.contains("abyssal")));
                }
            }
        }
        require(eligible.size() > 90, "full ordinary catalog, not the old rare-only pool");
        Set<String> named = new HashSet<>();
        Set<String> categories = new HashSet<>();
        int[] families = new int[3];
        Set<String> modifiers = new HashSet<>();
        List<FishRequirement> saved = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            int band = i % 5;
            long seed = new Random(i).nextLong();
            FishRequirement req = Payments.roll(seed, band, pool, saved);
            require(req != null, "every normal catalog recipe is available");
            require(req.describe().equals(Payments.roll(seed, band, pool, saved).describe()), "deterministic recipe");
            require(req.origin == null && req.sourceId == null && req.caughtSystemId == null
                    && !req.freshCatch && req.minCaughtAt == 0, "no origin or fresh-catch restriction");
            require(req.count >= 1 && req.count <= 4 && (!req.sameSpecies || req.count >= 2), "quantity bounds");
            require((req.minGrade == null ? 0 : 1) + (req.minLength > 0 ? 1 : 0)
                    + (req.minWeight > 0 ? 1 : 0) <= 1, "no stacked size/grade requirements");
            require(req.method == null || req.implement == null, "no redundant method/source combination");
            require(req.speciesId == null || req.minRarity == null, "no redundant named rarity floor");
            require(pool.stream().anyMatch(option -> req.couldBeSatisfiedBy(option.spec())
                    && req.matches(sample(option))), "combined recipe has an obtainable witness: " + req.describe());
            int mods = 0;
            if (req.minGrade != null) { modifiers.add("grade"); mods++; }
            if (req.minLength > 0) { modifiers.add("length"); mods++; }
            if (req.minWeight > 0) { modifiers.add("weight"); mods++; }
            if (req.lowCoherence) { modifiers.add("coherence"); mods++; }
            if (req.method != null) { modifiers.add("method"); mods++; }
            if (req.implement != null) { modifiers.add("implement"); mods++; }
            if (req.minRarity != null) { modifiers.add("rarity"); mods++; }
            if (req.speciesId != null) { named.add(req.speciesId); families[0]++; require(mods <= 1, "named recipe modifier count"); }
            else if (req.sameSpecies) { families[2]++; require(mods == 1, "matching batch modifier count"); }
            else { categories.add(req.tag); families[1]++; require(mods == 2, "species recipe modifier count"); }
            saved.add(req);
        }
        require(named.equals(eligible), "every ordinary pattern can be named, including Common, Uncommon and Epic");
        require(categories.equals(Set.of("fish", "crab", "mollusc", "other")), "all four species groups");
        require(modifiers.size() == 7, "all supported modifier families used");
        require(Math.abs(families[0] - 2500) < 200 && Math.abs(families[1] - 1500) < 200
                && Math.abs(families[2] - 1000) < 200, "50/30/20 mix");
        require(Payments.roll(3, 4, List.of(), List.of()) == null, "empty world does not invent a target");
        for (int kind = 0; kind < 3; kind++) {
            FishSpec excluded = new FishSpec();
            excluded.id = "excluded";
            if (kind == 0) excluded.spawnWeight = 0f;
            if (kind == 1) excluded.tags.add("special");
            if (kind == 2) excluded.rarity = FishRarity.LEGENDARY;
            require(Payments.roll(1, 4, List.of(new ShopFishPayments.Opportunity(excluded,
                    FishLogEntry.Method.DRONE, CatchImplement.POND, false)), List.of()) == null,
                    "exclude zero-weight, special and legendary patterns");
        }
        System.out.println("Recipe families: " + Arrays.toString(families) + "; named patterns: " + named.size());
    }

    private static FishCatch sample(ShopFishPayments.Opportunity option) {
        FishSpec spec = option.spec();
        FishCatch fish = new FishCatch(spec.id,
                spec.lengthMin + 0.95f * (spec.lengthMax - spec.lengthMin),
                spec.weightMin + 0.95f * (spec.weightMax - spec.weightMin), option.lowCoherence() ? 0.7f : 0f);
        fish.method = option.method();
        fish.implement = option.implement();
        return FishCatch.decode(fish.encode());
    }

    private static void availabilityAndPersistence(Fixture f) {
        require(Payments.available().isEmpty(), "no fishing gear, no payment targets");
        require(ShopPricing.getPrice(Tackle.SONAR_HEAD).fish == null, "unavailable recipe remains absent");
        ShopSchematics.unlock(Tackle.NEEDLE_SENSOR);
        require(!ShopEntry.of(Tackle.NEEDLE_SENSOR, Tackle.Fit.SEARCHLIGHT).canAfford(),
                "no credit-only sale when fish payment is unavailable");
        f.abilities.add(StatIds.ROD_ABILITY);
        List<ShopFishPayments.Opportunity> rod = Payments.available();
        require(!rod.isEmpty() && rod.stream().allMatch(option -> option.method() == FishLogEntry.Method.DRONE
                && option.implement() == CatchImplement.POND), "ROD-only prices need no later gear");
        f.abilities.add(StatIds.LAMPS_ABILITY);
        require(Payments.available().stream().noneMatch(option -> option.implement() == CatchImplement.BREACH_LAMP), "lamps alone do not grant coupling");
        TackleManager.own(Tackle.BREACH_COUPLER);
        require(Payments.available().stream().anyMatch(option -> option.method() == FishLogEntry.Method.DRONE
                && option.implement() == CatchImplement.BREACH_LAMP), "owned coupling allows lamp drones");
        f.data.remove(TackleManager.OWNED_KEY);
        f.abilities.add(StatIds.HARPOON_ABILITY);
        require(Payments.available().stream().anyMatch(option -> option.method() == FishLogEntry.Method.HARPOON
                && option.implement() == CatchImplement.POND), "harpoon pond catches remain valid");
        String absent = rod.get(0).spec().id;
        f.pins.put(absent, Set.of("unreachable-system"));
        require(Payments.available().stream().noneMatch(option -> option.spec().id.equals(absent)), "no target without a current habitat");
        f.data.remove(ShopPricing.PAYMENTS_KEY);
        QuestRewards.valueOf(new FishReward.TackleSchematic(Tackle.SONAR_HEAD));
        require(!f.data.containsKey(ShopPricing.PAYMENTS_KEY), "quest valuation does not generate shop recipes");
        ShopPricing.Price first = ShopPricing.getPrice(Tackle.SONAR_HEAD);
        require(first.credits == 40000 && first.fish != null, "Sonar has top price and fish payment");
        f.systems.clear();
        f.abilities.clear();
        require(ShopPricing.getPrice(Tackle.SONAR_HEAD).fish == first.fish, "saved recipe survives availability changes");
        ShopEntry drone = ShopEntry.of(Tackle.SONAR_HEAD, Tackle.Fit.DRONE);
        ShopEntry harpoon = ShopEntry.of(Tackle.SONAR_HEAD, Tackle.Fit.HARPOON);
        require(drone.getPrice().fish == harpoon.getPrice().fish, "one payment for a module shared by rigs");
        require(drone.isPurchaseLocked(), "Sonar still requires its schematic");
        ShopSchematics.unlock(Tackle.SONAR_HEAD);
        require(!drone.isPurchaseLocked() && drone.getPrice().fish == first.fish, "schematic does not reroll price");
        TackleManager.own(Tackle.SONAR_HEAD);
        require(drone.getPrice() == null && harpoon.getPrice() == null && harpoon.canAfford(), "owned refits stay free");
    }

    private static void sharedPayment(Fixture f) {
        List<FishSpec> specs = FishSpecLoader.getAllFishSpecs().stream()
                .filter(spec -> Payments.eligible(spec) && spec.tags.contains("fish")).limit(2).toList();
        FishCatch first = sample(new ShopFishPayments.Opportunity(specs.get(0), FishLogEntry.Method.HARPOON, CatchImplement.POND, false));
        FishCatch second = sample(new ShopFishPayments.Opportunity(specs.get(1), FishLogEntry.Method.HARPOON, CatchImplement.POND, false));
        FishRequirement req = new FishRequirement();
        req.sameSpecies = true;
        req.minGrade = FishGrade.FINE;
        req.count = 2;
        f.cargo.put(FishItems.toBundle(List.of(first, second)), 1);
        require(FishCurrency.count(req) == 1 && FishHandoffPicker.autoSelect(List.of(req), null) == null,
                "mixed patterns cannot pay a matching batch");
        f.cargo.clear();
        f.cargo.put(FishItems.toBundle(List.of(first, first, second)), 1);
        require(FishCurrency.count(req) == 2, "boxed matching batch counted");
        FishHandoffPicker.Selection selection = FishHandoffPicker.autoSelect(List.of(req), null);
        require(selection != null && selection.getContents().size() == 2 && selection.spend(), "shared picker spends boxed payment");
        require(FishCurrency.count(req) == 1, "unselected boxed fish preserved");
        f.cargo.clear();
        f.cargo.put(FishItems.toItem(first), 2);
        require(FishCurrency.spend(req) && f.cargo.isEmpty(), "automatic currency uses the same matching rule");
    }

    private static void creditPrices() {
        for (int tiers = 2; tiers <= 4; tiers++) {
            UpgradeStat stat = new UpgradeStat();
            stat.maxLevel = tiers;
            for (int level = 1; level <= tiers; level++) {
                require(ShopPricing.getCredits(stat, level) == (4 - tiers + level) * 5000,
                        "end-aligned upgrade ladder");
            }
            require(ShopPricing.getCredits(stat, 0) == 0, "no tier zero purchase");
            require(ShopPricing.getCredits(stat, tiers + 1) == 0, "no purchase beyond maximum");
        }
        for (Tackle tackle : Tackle.values()) {
            if (tackle == Tackle.NONE) continue;
            int credits = ShopPricing.getCredits(tackle);
            require(credits >= 20000 && credits <= 40000, "module price bounds: " + tackle);
            if (tackle.barSizeMult != 1f || tackle.barGravityMult != 1f
                    || tackle.escapeMult != 1f || tackle.progressMult != 1f) {
                require(credits >= 35000, "minigame difficulty premium: " + tackle);
            }
        }
        require(ShopPricing.getCredits(Tackle.SONAR_HEAD) == 40000, "Sonar top bracket");
        require(ShopPricing.getCredits(Tackle.NONE) == 0, "empty slot stays free");
    }

    private static void settings(Fixture fixture) throws Exception {
        UpgradeStat stat = new UpgradeStat();
        stat.id = "settings_price_check";
        stat.maxLevel = 4;
        FishRequirement original = new FishRequirement();
        original.count = 3;
        original.sameSpecies = true;
        original.minGrade = FishGrade.FINE;
        original.implement = CatchImplement.POND;
        fixture.data.put(ShopPricing.PAYMENTS_KEY,
                new LinkedHashMap<>(Map.of("stat:" + stat.id + ":1", original)));
        try {
            var values = PlayerSettings.defaults();
            values.put(Setting.UPGRADE_CREDITS, 50d);
            values.put(Setting.UPGRADE_FISH, 150d);
            values.put(Setting.EQUIPMENT_COST, 25d);
            PlayerSettingsChecks.use(values);
            var price = ShopPricing.getPrice(stat, 1);
            require(price.credits == 2500 && price.fish.count == 5, "configured upgrade prices");
            require(price.fish != original && original.count == 3, "saved base payment is not mutated");
            require(price.fish.sameSpecies && price.fish.minGrade == FishGrade.FINE
                    && price.fish.implement == CatchImplement.POND, "payment modifiers survive scaling");
            require(ShopPricing.getPrice(stat, 1).fish.count == 5, "refresh cannot compound the multiplier");
            require(ShopPricing.getCredits(stat, 1) == 5000, "reward valuation retains base prices");
            require(ShopPricing.getPrice(Tackle.SONAR_HEAD).credits == 10000, "configured module credits");
            values.put(Setting.UPGRADE_FISH, 25d);
            PlayerSettingsChecks.use(values);
            require(ShopPricing.getPrice(stat, 1).fish.count == 1, "fish payment rounds up to at least one");
        } finally {
            PlayerSettingsChecks.use(PlayerSettings.defaults());
        }
        require(ShopPricing.getPrice(stat, 1).fish == original, "restoring defaults restores the saved payment");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
