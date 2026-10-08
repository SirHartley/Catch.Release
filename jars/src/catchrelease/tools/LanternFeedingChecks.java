package catchrelease.tools;

import catchrelease.abilities.searchlight.rendering.SearchlightImpressionRenderer;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.reflection.ReflectionUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LanternFeedingChecks {

    private static int checks;

    public static void main(String[] args) {
        pursuit();
        buried();
        exclusions();
        call();
        calledMovement();
        System.out.println("Lantern feeding checks passed: " + checks);
    }

    private static void pursuit() {
        for (int fps : new int[]{30, 60, 144}) try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            jack.at.set(0f, 0f);
            jack.setSwimTarget(new Vector2f(0f, 2000f));
            var meal = env.real("meal");
            meal.spec.rarity = FishRarity.COMMON;
            meal.at.set(1000f, 0f);
            for (int i = 0; i < fps * 10 && !meal.expired; i++) jack.advance(1f / fps);
            check(meal.expired, "Jack reaches prey at " + fps + " Hz");
            check(LegendaryChases.getState("lantern_jack").shieldUnits == 1, "one meal adds one shell");
            for (int i = 0; i < fps; i++) jack.advance(1f / fps);
            check(LegendaryChases.getState("lantern_jack").shieldUnits == 1, "meal cannot be counted twice");
        }
    }

    private static void buried() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            jack.setSwimTarget(new Vector2f(2000f, 2000f));
            var meal = env.real("meal");
            meal.spec.rarity = FishRarity.COMMON;
            env.specs.put(meal.spec.id, meal.spec);
            meal.plugin = new LegendaryEscapeChecks.Buried(meal);
            meal.tags.clear();
            meal.tags.add(BuriedMoteEntityPlugin.BURIED_TAG);
            float[] reveal = {0f};
            var impressions = new SearchlightImpressionRenderer(List.of(), env.lamps, env.system) {
                @Override public float getRevealStrength(com.fs.starfarer.api.campaign.SectorEntityToken mote) {
                    return reveal[0];
                }
            };
            ReflectionUtils.set(env.lamps, "impressionRenderer", impressions, true);
            LegendaryShields.advanceEater(jack);
            check(!meal.expired, "passive hunt leaves unexposed buried prey alone");
            reveal[0] = 1f;
            LegendaryShields.advanceEater(jack);
            check(meal.expired && env.fish.size() == 3, "exposed buried prey is surfaced");
            check(env.fish.get(2).expired && LegendaryChases.getState("lantern_jack").shieldUnits == 1,
                    "surfaced prey within reach is swallowed once");
        }
    }

    private static void exclusions() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            var meal = env.real("meal");
            meal.spec.rarity = FishRarity.COMMON;
            meal.quest = true;
            LegendaryShields.advanceEater(jack);
            check(!meal.expired, "quest fish protected");
            meal.quest = false;
            meal.pond = true;
            LegendaryShields.advanceEater(jack);
            check(!meal.expired, "pond stock protected");
            meal.pond = false;
            meal.setHeld(true);
            LegendaryShields.advanceEater(jack);
            check(!meal.expired, "held catch protected");
            meal.setHeld(false);
            LegendaryChases.getState("lantern_jack").shieldUnits = LegendaryShields.JACK_STACK_MAX;
            LegendaryShields.advanceEater(jack);
            check(!meal.expired, "full shell stack stops feeding");
            LegendaryChases.getState("lantern_jack").shieldUnits = 0;
            jack.startEvasive();
            LegendaryShields.advanceEater(jack);
            check(!meal.expired, "evasion takes priority over feeding");
        }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void call() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            var buried = env.real("meal");
            buried.spec.rarity = FishRarity.COMMON;
            buried.at.set(900f, 500f);
            env.specs.put("meal", buried.spec);
            env.persistent.put(FishRanges.PIN_KEY, Map.of("meal", Set.of(env.system.getId())));
            buried.plugin = new LegendaryEscapeChecks.Buried(buried);
            buried.tags.clear();
            buried.tags.add(BuriedMoteEntityPlugin.BURIED_TAG);
            var surfaced = env.real("meal");
            var quest = env.real("meal");
            quest.quest = true;
            var pond = env.real("meal");
            pond.pond = true;
            var held = env.real("meal");
            held.setHeld(true);
            var remote = env.real("meal");
            remote.at.set(10000f, 0f);
            int before = env.fish.size();
            jack.tryLureFlare();
            check(buried.expired, "call surfaces buried prey even outside a current beam");
            check(env.fish.size() == before + 1 + LegendaryShields.LURE_SPAWN_COUNT,
                    "call surfaces one existing fish and adds three new local fish");
            check(surfaced.getLureSpeedMult() > 1f, "existing surfaced prey responds");
            for (var fish : List.of(quest, pond, held, remote)) {
                check(fish.getLureSpeedMult() == 1f && !fish.expired, "protected or distant prey is unchanged");
            }
            for (int i = before + 1; i < env.fish.size(); i++) {
                var fish = env.fish.get(i);
                float distance = Vector2f.sub(fish.at, jack.at, null).length();
                check(distance >= 799.99f && distance <= 1400.01f && fish.getLureSpeedMult() > 1f,
                        "new local prey starts in the call ring and responds immediately");
                check(fish.spec.rarity != FishRarity.LEGENDARY, "call cannot duplicate a legendary");
            }
            before = env.fish.size();
            jack.tryLureFlare();
            check(env.fish.size() == before, "call cooldown prevents repeated fish spawns");
        }
    }

    private static void calledMovement() {
        for (FishMotion mode : FishMotion.values()) for (int fps : new int[]{30, 60, 144}) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                var jack = env.real("lantern_jack");
                jack.at.set(0f, 0f);
                var meal = env.real("meal");
                meal.spec.rarity = FishRarity.RARE;
                meal.spec.motion = mode;
                meal.at.set(1300f, 0f);
                meal.startLure(jack.getMote(), LegendaryShields.LURE_SECONDS);
                float previous = meal.at.length();
                for (int i = 0; i < fps * 15; i++) {
                    meal.advance(1f / fps);
                    check(meal.at.length() <= previous + 0.001f, "call overrides wandering and dives: " + mode);
                    previous = meal.at.length();
                }
                check(previous < LegendaryShields.EAT_RANGE && !meal.expired && meal.entityScripts.isEmpty(),
                        "called prey waits within bite range rather than expiring");
            }
        }
    }
}
