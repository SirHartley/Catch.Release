package catchrelease.tools;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.rendering.SearchlightImpressionRenderer;
import catchrelease.abilities.searchlight.scripts.NeedleSensor;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.FishRewardRoller;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.shop.ShopEntry;
import catchrelease.campaign.fish.shop.ShopPricing;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.reflection.ReflectionUtils;
import catchrelease.rendering.renderers.RippleRingRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class NeedleSensorChecks {

    private static int checks;

    private static class Lamps extends SearchlightAbilityPlugin {

        void bind(CampaignFleetAPI player) { entity = player; }
        void state(boolean on, float progress) { turnedOn = on; level = progress; }
        void reload() { readResolve(); }
    }

    private static class Sensor extends NeedleSensor {

        final List<RippleRingRenderer> emitted = new ArrayList<>();

        Sensor(Lamps lamps) { super(lamps); }
        @Override protected float nextDelay() { return INTERVAL_MIN; }
        @Override protected void register(RippleRingRenderer ring) { emitted.add(ring); }
        float randomDelay() { return super.nextDelay(); }
    }

    private static class Rewards extends FishRewardRoller {

        static FishReward module() { return rollTackle(new Random(1), Set.of()); }
    }

    private static class Fixture implements AutoCloseable {

        final LegendaryEscapeChecks.Environment env = new LegendaryEscapeChecks.Environment();
        final Lamps lamps = new Lamps();
        final Sensor sensor = new Sensor(lamps);
        LocationAPI location = env.system;
        SearchlightAbilityPlugin installed = lamps;
        boolean paused;
        boolean hyperspace;
        float reveal;

        Fixture() {
            SectorAPI original = Global.getSector();
            CampaignFleetAPI player = proxy(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getContainingLocation" -> location;
                case "getAbility" -> installed;
                case "hasAbility" -> SearchlightAbilityPlugin.ABILITY_ID.equals(a[0]);
                case "isInHyperspace" -> hyperspace;
                case "isInCurrentLocation", "isPlayerFleet" -> true;
                case "isVisibleToPlayerFleet" -> false;
                default -> throw new AssertionError(m);
            });
            MemoryAPI memory = proxy(MemoryAPI.class, (p, m, a) -> switch (m.getName()) {
                case "get" -> ShopPricing.SEED_KEY.equals(a[0]) ? 123L : null;
                default -> throw new AssertionError(m);
            });
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getPlayerFleet" -> player;
                case "getCurrentLocation" -> location;
                case "isPaused" -> paused;
                case "getMemoryWithoutUpdate" -> memory;
                default -> m.invoke(original, a);
            }));
            lamps.bind(player);
            ReflectionUtils.set(lamps, "needleSensor", sensor, true);
            ReflectionUtils.set(lamps, "impressionRenderer",
                    new SearchlightImpressionRenderer(List.of(), lamps, env.system) {
                        @Override public float getRevealStrength(SectorEntityToken mote) { return reveal; }
                    }, true);
        }

        LegendaryEscapeChecks.Fish fish(String id, FishRarity rarity, boolean buried) {
            var fish = env.real(id);
            fish.spec.rarity = rarity;
            if (buried) {
                fish.plugin = new LegendaryEscapeChecks.Buried(fish);
                fish.tags.clear();
                fish.tags.add(BuriedMoteEntityPlugin.BURIED_TAG);
            }
            return fish;
        }

        void fit() { ShopEntry.of(Tackle.NEEDLE_SENSOR).grant(); }

        void run(float seconds) {
            for (int i = 0; i < Math.round(seconds * 10); i++) {
                lamps.advance(0.1f);
                for (var ring : sensor.emitted) ring.advance(0.1f);
            }
        }

        @Override public void close() { env.close(); }
    }

    public static void main(String[] args) {
        equipment();
        eligibility();
        lifecycle();
        reload();
        System.out.println("Needle sensor checks passed: " + checks);
    }

    private static void equipment() {
        try (Fixture f = new Fixture()) {
            Tackle sensor = Tackle.NEEDLE_SENSOR;
            check(sensor.fits(Tackle.Fit.SEARCHLIGHT) && !sensor.fits(Tackle.Fit.DRONE)
                    && !sensor.fits(Tackle.Fit.HARPOON), "lamp module only");
            check(TackleManager.getOptions(Tackle.Fit.SEARCHLIGHT).contains(sensor), "stocked in outfitter");
            ShopEntry entry = ShopEntry.of(sensor);
            check(entry.isPurchaseLocked(), "schematic required");
            for (Tackle tackle : Tackle.values()) if (tackle != sensor) ShopSchematics.unlock(tackle);
            FishReward reward = Rewards.module();
            check(reward instanceof FishReward.TackleSchematic schematic && schematic.tackle == sensor,
                    "new module enters the ordinary schematic reward pool");
            ShopSchematics.unlock(sensor);
            check(!entry.isPurchaseLocked(), "schematic exposes purchase");
            check(ShopPricing.getPrice(sensor).credits == 12000, "same credit tier as existing lens arrays");
            f.fit();
            check(entry.isOwned() && entry.isFitted(), "grant owns and fits");
            TackleManager.fit(Tackle.Fit.SEARCHLIGHT, Tackle.FANNED_ARRAY);
            check(!entry.isFitted() && entry.isOwned() && !f.sensor.isEnabled(), "ownership alone is insufficient");
            f.fit();
            check(f.sensor.isEnabled(), "refitting enables passive sensor");
            for (int i = 0; i < 100; i++) {
                float delay = f.sensor.randomDelay();
                check(delay >= NeedleSensor.INTERVAL_MIN && delay <= NeedleSensor.INTERVAL_MAX,
                        "random pulse interval within tuning bounds");
            }
        }
    }

    private static void eligibility() {
        try (Fixture f = new Fixture()) {
            f.fit();
            var rare = f.fish("rare", FishRarity.RARE, true);
            var epic = f.fish("epic", FishRarity.EPIC, false);
            var jack = f.fish("lantern_jack", FishRarity.LEGENDARY, true);
            f.fish("common", FishRarity.COMMON, true);
            f.fish("uncommon", FishRarity.UNCOMMON, true);
            var imposter = f.fish("longliner", FishRarity.LEGENDARY, true);
            var awake = f.fish("quorum", FishRarity.LEGENDARY, false);
            LegendaryChases.getState("quorum").provoked = true;
            var held = f.fish("held", FishRarity.RARE, false);
            held.setHeld(true);
            var pond = f.fish("pond", FishRarity.EPIC, false);
            pond.pond = true;
            var escort = f.fish("escort", FishRarity.RARE, false);
            escort.orbit = jack.getMote();
            new LegendaryEscapeChecks.Fish(f.env, "phantom", true, null);
            f.run(7f);
            check(f.sensor.emitted.size() == 3, "only rare, epic and dormant legendary produce rings");
            check(!f.sensor.isEligible(imposter.getMote()) && !f.sensor.isEligible(awake.getMote()),
                    "Longliner and provoked legendaries excluded");
            check(LegendaryChases.getState("lantern_jack").seenAt == 0L
                    && !LegendaryChases.isProvoked("lantern_jack"), "hint does not sight or wake a legendary");
            check(rare.plugin instanceof BuriedMoteEntityPlugin && !epic.isHeld(), "hint does not surface or catch fish");
            var ring = f.sensor.emitted.get(0);
            Vector2f origin = new Vector2f(ring.location);
            rare.at.set(2000f, 2000f);
            epic.at.set(2000f, 2000f);
            jack.at.set(2000f, 2000f);
            check(ring.location.equals(origin), "ripple stays at emission point instead of tracking fish");
            check(ring.color.getAlpha() == 55 && ring.size == 100f, "small faint ring");
            f.reveal = 1f;
            check(ring.isExpired(), "remaining lamp reveal suppresses a ripple");
            f.sensor.advance(0.5f);
            check(!f.sensor.isEligible(rare.getMote()), "lit or retained contact excluded");
        }
    }

    private static void lifecycle() {
        try (Fixture f = new Fixture()) {
            var fish = f.fish("rare", FishRarity.RARE, true);
            f.run(15f);
            check(f.sensor.emitted.isEmpty(), "no sensor, no ripples");
            f.fit();
            f.run(5f);
            check(f.sensor.emitted.isEmpty(), "no immediate pulse on fitting");
            f.paused = true;
            f.run(20f);
            check(f.sensor.emitted.isEmpty(), "pause does not advance cadence");
            f.paused = false;
            f.run(2f);
            check(f.sensor.emitted.size() == 1, "inactive ability advances passive sensor");
            var ring = f.sensor.emitted.get(0);
            f.paused = true;
            ring.advance(20f);
            check(!ring.isExpired(), "pause preserves ring lifetime");
            f.lamps.state(true, 0f);
            check(ring.isExpired() && !f.sensor.isEnabled(), "activation suppresses rings before spool-up");
            f.sensor.advance(0f);
            f.lamps.state(false, 0.5f);
            check(!f.sensor.isEnabled(), "spool-down still suppresses passive sensor");
            f.lamps.state(false, 0f);
            f.paused = false;
            f.run(7f);
            check(f.sensor.emitted.size() == 2, "fresh delay after lights turn off");
            ring = f.sensor.emitted.get(1);
            TackleManager.fit(Tackle.Fit.SEARCHLIGHT, Tackle.NONE);
            check(ring.isExpired(), "unfit kills existing rings without waiting for scan");
            f.sensor.advance(0f);
            f.fit();
            f.run(7f);
            ring = f.sensor.emitted.get(2);
            f.location = proxy(LocationAPI.class, (p, m, a) -> { throw new AssertionError(m); });
            check(ring.isExpired(), "location change kills old ring");
            f.sensor.advance(0f);
            f.location = f.env.system;
            f.run(7f);
            ring = f.sensor.emitted.get(3);
            f.hyperspace = true;
            check(ring.isExpired() && !f.sensor.isEnabled(), "disabled in hyperspace");
            f.hyperspace = false;
            f.sensor.clear();
            f.run(7f);
            ring = f.sensor.emitted.get(4);
            f.installed = null;
            check(ring.isExpired(), "removed/replaced ability retires its effects");
            f.installed = f.lamps;
            f.sensor.clear();
            f.run(7f);
            ring = f.sensor.emitted.get(5);
            fish.expired = true;
            check(ring.isExpired(), "removed fish retires its effects");
        }
    }

    private static void reload() {
        try (Fixture f = new Fixture()) {
            f.fit();
            SettingsAPI original = Global.getSettings();
            Global.setSettings(proxy(SettingsAPI.class, (p, m, a) ->
                    "getAbilitySpec".equals(m.getName()) ? null : m.invoke(original, a)));
            f.lamps.reload();
            check(ReflectionUtils.get(f.lamps, "needleSensor", null, true) == null, "load discards transient controller");
            f.lamps.advance(0.1f);
            Object restored = ReflectionUtils.get(f.lamps, "needleSensor", null, true);
            check(restored instanceof NeedleSensor sensor && sensor != f.sensor && sensor.isEnabled(),
                    "existing inactive-ability callback rebuilds passive sensor after load");
            check(TackleManager.get(Tackle.Fit.SEARCHLIGHT) == Tackle.NEEDLE_SENSOR
                    && TackleManager.isOwned(Tackle.NEEDLE_SENSOR), "load retains fitted and owned module");
        }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
