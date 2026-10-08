package catchrelease.tools;

import catchrelease.abilities.searchlight.rendering.SearchlightImpressionRenderer;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.reflection.ReflectionUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public final class LanternFeedingChecks {

    private static int checks;

    public static void main(String[] args) {
        pursuit();
        buried();
        exclusions();
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
}
