package catchrelease.tools;

import catchrelease.abilities.searchlight.rendering.SearchlightImpressionRenderer;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LanternFeedingChecks {

    private static final float TICK = 1f / 60f;

    private static int checks;

    private static class MovingFish extends LegendaryEscapeChecks.Fish {

        MovingFish(LegendaryEscapeChecks.Environment env, String id) {
            super(env, id, false, null);
            setSwimTarget(new Vector2f(10000f, at.y));
            keepSurfaced(60f);
        }
        void field(String name, Object value) { ReflectionUtils.set(this, name, value, true); }
        float value(String name) { return (float) ReflectionUtils.get(this, name, null, true); }
        float wander() { return getWander(); }
        float unweightedWander() {
            float variance = value("sineVariance");
            float time = value("time");
            float wander = (float) Math.sin(time * 1.5f) * variance;
            float extra = (float) Math.sin(time * 2.63f + variance) * variance * 0.6f;
            return (wander + extra * (getWanderMult() - 1f)) * getWanderMult() + getModeWander();
        }
        void prowl(float amount) { advanceProwl(amount); }
        float heading() {
            return Misc.getAngleInDegrees(at, (Vector2f) ReflectionUtils.get(this, "target", null, true));
        }
    }

    public static void main(String[] args) {
        startingShields();
        shieldMovement();
        shieldTurns();
        emptyShields();
        pursuit();
        buried();
        lunge();
        loadedLunge();
        exclusions();
        call();
        calledMovement();
        releaseEdges();
        System.out.println("Lantern feeding checks passed: " + checks);
    }

    private static void startingShields() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            callPool(env);
            var jack = env.real("lantern_jack");
            var color = LegendaryShields.getShieldColor(jack);
            check(color.equals(LegendaryShields.SHIELD_ORANGE),
                    "Jack uses the shared charge-shield color");
            check(LegendaryShields.isShielded(jack) && LegendaryShields.getStackedRings(jack) == 3,
                    "Jack has three hunt shields before the wake-up hit");
            for (int remaining : new int[]{2, 1, 0}) {
                check(LegendaryShields.onHarpoonContact(jack.getMote(), false)
                        == LegendaryShields.HitResult.DEFLECTED, "each hunt shell deflects once");
                check(LegendaryShields.getStackedRings(jack) == remaining,
                        "each hit spends a shell, including the wake-up hit");
                check(LegendaryShields.isShielded(jack) == (remaining > 0),
                        "collision and display lose the shield with the last shell");
                check(LegendaryChases.isProvoked("lantern_jack"), "shield hits keep the chase provoked");
            }
            check(jack.notices.stream().anyMatch(n -> n.text.startsWith("The lantern flares")),
                    "spending the last shell still calls prey");
            check(LegendaryShields.onHarpoonContact(jack.getMote(), false) == LegendaryShields.HitResult.NONE,
                    "no base shield beneath the hunt shells");
            check(LegendaryShields.getStackedRings(jack) == 0, "empty saved stack is not initialized again");
            LegendaryChases.getState("lantern_jack").shieldUnits = 3;
            var prey = meal(env, 500f, 500f);
            int population = env.fish.size();
            int notices = jack.notices.size();
            float cooldown = (float) ReflectionUtils.get(jack, "flareCooldown", null, true);
            jack.tryLureFlare();
            LegendaryShields.lureFlare(jack);
            check(env.fish.size() == population && prey.getLureSpeedMult() == 1f && jack.notices.size() == notices,
                    "full shields ignore prey and suppress both call entry points");
            check((float) ReflectionUtils.get(jack, "flareCooldown", null, true) == cooldown,
                    "suppressed call does not spend cooldown");
        }
    }

    private static void shieldMovement() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = new MovingFish(env, "lantern_jack");
            var state = LegendaryChases.getState("lantern_jack");
            jack.field("time", 1.7f);
            jack.field("sineVariance", 53f);
            for (int shells : new int[]{0, 1, 2, 3, 2, 1, 0, 3}) {
                state.shieldUnits = shells;
                float pace = new float[]{1f, .85f, .7f, .55f}[shells];
                float agility = new float[]{1f, .8f, .6f, .4f}[shells];
                for (int mode = 0; mode < 3; mode++) {
                    jack.setHunting(mode == 1);
                    jack.field("evasiveLeft", mode == 2 ? 5f : 0f);
                    check(Math.abs(LegendaryShields.getSpeedMult(jack)
                                    - new float[]{1.6f, 1.9f, 2.6f}[mode] * pace) < .00001f,
                            "current shells scale prowl, pursuit and evasion immediately");
                }
                for (FishMotion mode : FishMotion.values()) {
                    jack.spec.motion = mode;
                    jack.field("activeMode", mode);
                    jack.field("dashing", true);
                    check(Math.abs(jack.wander() - jack.unweightedWander() * agility) < .0001f,
                            "shells damp all wandering, with the original value at zero: " + mode);
                }
            }
            for (String id : new String[]{"ordinary", "false_dawn", "slipstream_moray"}) {
                var fish = new MovingFish(env, id);
                if (id.equals("ordinary")) fish.spec.rarity = FishRarity.COMMON;
                fish.field("time", 1.7f);
                fish.field("sineVariance", 53f);
                check(fish.wander() == fish.unweightedWander(), "other species keep their wandering");
            }
        }
        for (int fps : new int[]{30, 60, 144}) for (int shells = 0; shells <= 3; shells++) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                var jack = new MovingFish(env, "lantern_jack");
                LegendaryChases.getState("lantern_jack").shieldUnits = shells;
                jack.field("prowlLeft", 100f);
                Vector2f start = new Vector2f(jack.at);
                for (int i = 0; i < fps; i++) jack.advance(1f / fps);
                float expected = 90f * jack.spec.getCampaignSpeedMult() * 1.6f
                        * new float[]{1f, .85f, .7f, .55f}[shells];
                check(Math.abs(Vector2f.sub(jack.at, start, null).length() - expected) < .02f,
                        "actual travel scales with shields at " + fps + " Hz");
            }
        }
    }

    private static void shieldTurns() {
        for (boolean evading : new boolean[]{false, true}) for (int shells = 0; shells <= 3; shells++) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                var jack = new MovingFish(env, "lantern_jack");
                LegendaryChases.getState("lantern_jack").shieldUnits = shells;
                float agility = new float[]{1f, .8f, .6f, .4f}[shells];
                MathUtils.getRandom().setSeed(413L);
                float clock = MathUtils.getRandomNumberInRange(evading ? .35f : 2.5f, evading ? .7f : 4.5f);
                float sign = evading ? -1f : MathUtils.getRandomNumberInRange(0f, 1f) < .5f ? -1f : 1f;
                float turn = sign * MathUtils.getRandomNumberInRange(evading ? 50f : 25f, evading ? 110f : 60f);
                float base = evading ? Misc.getAngleInDegrees(env.playerAt, jack.at) : jack.heading();
                if (evading) jack.startEvasive();
                MathUtils.getRandom().setSeed(413L);
                jack.prowl(.1f);
                check(Math.abs(Misc.getAngleDiff(jack.heading(), base + turn * agility)) < .001f,
                        "patrol/evasive turns shrink with shells; zero retains the seeded turn");
                String timer = evading ? "jinkLeft" : "prowlLeft";
                check(jack.value(timer) == clock, "original randomized retarget interval is retained");
                jack.prowl(.1f);
                check(Math.abs(jack.value(timer) - (clock - .1f * agility)) < .00001f,
                        "shields slow the existing retarget clock");
                if (evading) check(Math.abs(jack.value("evasiveLeft") - 4.8f) < .00001f,
                        "shield count does not extend the five-second evasion");
            }
        }
    }

    private static void emptyShields() {
        for (boolean explosive : new boolean[]{false, true}) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                callPool(env);
                var jack = env.real("lantern_jack");
                var state = LegendaryChases.getState("lantern_jack");
                state.shieldUnits = 0;
                check(LegendaryShields.onHarpoonContact(jack.getMote(), explosive)
                        == LegendaryShields.HitResult.NONE, "empty hunt shields give no free wake-up deflection");
                check(state.provoked, "an unshielded contact still provokes Jack");
                for (int i = 0; i < 30 * 60; i++) jack.advance(1f / 60f);
                check(!LegendaryShields.isShielded(jack) && state.shieldUnits == 0,
                        "waiting beyond the base shield cooldown does not regrow hunt shells");
                check(LegendaryShields.onHarpoonContact(jack.getMote(), explosive)
                        == LegendaryShields.HitResult.NONE, "Jack stays catchable without prey");
                for (int i = 0; i < 10 * 60; i++) jack.advance(1f / 60f);
                var prey = meal(env, jack.at.x, jack.at.y);
                for (int i = 0; i < 60 && !prey.expired; i++) jack.advance(TICK);
                check(prey.expired && state.shieldUnits == 1 && LegendaryShields.isShielded(jack),
                        "eating restores a hunt shield after the stack empties");
                check(LegendaryShields.onHarpoonContact(jack.getMote(), explosive)
                        == LegendaryShields.HitResult.DEFLECTED && state.shieldUnits == 0,
                        "a replenished hunt shield blocks either harpoon head once");
            }
        }
    }

    private static void pursuit() {
        for (int fps : new int[]{30, 60, 144}) try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            LegendaryChases.getState("lantern_jack").shieldUnits = 2;
            jack.at.set(0f, 0f);
            jack.setSwimTarget(new Vector2f(0f, 2000f));
            var meal = env.real("meal");
            meal.spec.rarity = FishRarity.COMMON;
            meal.at.set(1000f, 0f);
            for (int i = 0; i < fps * 10 && !meal.expired; i++) jack.advance(1f / fps);
            check(meal.expired, "Jack reaches prey at " + fps + " Hz");
            check(LegendaryChases.getState("lantern_jack").shieldUnits == 3, "one meal fills the third extra shell");
            for (int i = 0; i < fps; i++) jack.advance(1f / fps);
            check(LegendaryChases.getState("lantern_jack").shieldUnits == 3, "meal cannot be counted twice");
            var extra = meal(env, jack.at.x, jack.at.y);
            Vector2f oldTarget = new Vector2f((Vector2f) ReflectionUtils.get(jack, "target", null, true));
            LegendaryShields.advanceEater(jack);
            check(!extra.expired && !(boolean) ReflectionUtils.get(jack, "hunting", null, true)
                    && oldTarget.equals(ReflectionUtils.get(jack, "target", null, true)),
                    "full shields ignore nearby prey without retargeting");
        }
    }

    private static void buried() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            LegendaryChases.getState("lantern_jack").shieldUnits = 2;
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
            var surfaced = env.fish.get(2);
            check(!surfaced.expired, "the surfaced mote is not swallowed before the lunge");
            for (int i = 0; i < 60 && !surfaced.expired; i++) jack.advance(TICK);
            check(surfaced.expired && LegendaryChases.getState("lantern_jack").shieldUnits == 3,
                    "surfaced prey within reach is swallowed once");
        }
    }

    /** Closing, the coil, the dash through, and the pace each stage runs at. */
    private static void lunge() {
        for (int fps : new int[]{30, 60, 144}) try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            var state = LegendaryChases.getState("lantern_jack");
            state.shieldUnits = 0;
            jack.at.set(0f, 0f);
            jack.setSwimTarget(new Vector2f(0f, 3000f));
            var meal = meal(env, FishEntityPlugin.LUNGE_RANGE + 200f, 0f);

            float dt = 1f / fps;
            jack.advance(dt);
            check(jack.getLungePhase() == FishEntityPlugin.LungePhase.NONE,
                    "prey beyond lunge range is only approached at " + fps + " Hz");

            for (int i = 0; i < fps * 10
                    && jack.getLungePhase() == FishEntityPlugin.LungePhase.NONE; i++) {
                jack.advance(dt);
            }
            check(jack.getLungePhase() == FishEntityPlugin.LungePhase.COIL
                            && Misc.getDistance(jack.at, meal.at) <= FishEntityPlugin.LUNGE_RANGE,
                    "closing inside lunge range starts the coil at " + fps + " Hz");
            check(LegendaryShields.getSpeedMult(jack) < 1f,
                    "the coil is slower than any ordinary pace at " + fps + " Hz");

            Vector2f coiledAt = new Vector2f(jack.at);
            for (int i = 0; i < fps * 10
                    && jack.getLungePhase() == FishEntityPlugin.LungePhase.COIL; i++) {
                jack.advance(dt);
            }
            check(!meal.expired, "the coil does not swallow at " + fps + " Hz");
            check(Misc.getDistance(coiledAt, jack.at) < FishEntityPlugin.LUNGE_RANGE,
                    "the coil crawls rather than closing the gap at " + fps + " Hz");
            check(jack.getLungePhase() == FishEntityPlugin.LungePhase.DASH
                            && LegendaryShields.getSpeedMult(jack) > 2.6f,
                    "the dash follows the coil and outruns evasion pace at " + fps + " Hz");

            Vector2f mealAt = new Vector2f(meal.at);
            for (int i = 0; i < fps * 10 && !meal.expired; i++) jack.advance(dt);
            check(meal.expired && state.shieldUnits == 1,
                    "the dash swallows the mote at " + fps + " Hz");
            check(jack.getLungePhase() == FishEntityPlugin.LungePhase.DASH
                            && jack.at.x >= mealAt.x - 1f,
                    "the mote is swallowed as the Jack runs through it, not before, at " + fps + " Hz");
            for (int i = 0; i < fps * 10
                    && jack.getLungePhase() == FishEntityPlugin.LungePhase.DASH; i++) {
                jack.advance(dt);
            }
            check(jack.getLungePhase() == FishEntityPlugin.LungePhase.NONE
                            && jack.at.x >= mealAt.x + FishEntityPlugin.LUNGE_OVERRUN - 1f,
                    "the dash carries on past the mote at " + fps + " Hz");
        }

        // a mote that leaves during the coil is not chased by a dash with nothing on its line
        try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            LegendaryChases.getState("lantern_jack").shieldUnits = 0;
            jack.at.set(0f, 0f);
            var meal = meal(env, 100f, 0f);
            jack.advance(TICK);
            check(jack.getLungePhase() == FishEntityPlugin.LungePhase.COIL, "prey underfoot coils");
            meal.expired = true;
            jack.advance(TICK);
            check(jack.getLungePhase() == FishEntityPlugin.LungePhase.NONE,
                    "losing the mote during the coil drops the lunge");
        }
    }

    // a save restores transient fields to null, not to their initializers
    private static void loadedLunge() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            LegendaryChases.getState("lantern_jack").shieldUnits = 0;
            jack.at.set(0f, 0f);
            jack.setSwimTarget(new Vector2f(0f, 3000f));
            var meal = meal(env, FishEntityPlugin.LUNGE_RANGE + 200f, 0f);
            ReflectionUtils.set(jack, "lungePhase", null, true);

            check(jack.getLungePhase() == FishEntityPlugin.LungePhase.NONE
                    && LegendaryShields.getSpeedMult(jack) > 0f, "a loaded Jack reads no lunge");
            for (int i = 0; i < 1200 && !meal.expired; i++) jack.advance(TICK);
            check(meal.expired && LegendaryChases.getState("lantern_jack").shieldUnits == 1,
                    "a loaded Jack still closes, coils and swallows");
        }
    }

    private static void exclusions() {
        try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            LegendaryChases.getState("lantern_jack").shieldUnits = 2;
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
        Set<Integer> counts = new HashSet<>();
        for (int available : new int[]{0, 2, 8}) for (int seed = 0; seed < 40; seed++)
                try (var env = new LegendaryEscapeChecks.Environment()) {
            var jack = env.real("lantern_jack");
            LegendaryChases.getState("lantern_jack").shieldUnits = 2;
            callPool(env);
            List<LegendaryEscapeChecks.Fish> existing = new ArrayList<>();
            for (int i = 0; i < available; i++) {
                var fish = meal(env, 900f + i * 50f, 500f);
                if (i % 2 == 0) {
                    fish.plugin = new LegendaryEscapeChecks.Buried(fish);
                    fish.tags.clear();
                    fish.tags.add(BuriedMoteEntityPlugin.BURIED_TAG);
                }
                existing.add(fish);
            }
            var quest = meal(env, 500f, 500f);
            quest.quest = true;
            var pond = meal(env, 500f, 500f);
            pond.pond = true;
            var held = meal(env, 500f, 500f);
            held.setHeld(true);
            var remote = meal(env, 10000f, 0f);
            var legendary = env.real("false_dawn");
            int before = env.fish.size();
            MathUtils.getRandom().setSeed(seed);
            jack.tryLureFlare();
            int called = (int) env.fish.stream().filter(f -> !f.expired && f.getLureSpeedMult() > 1f).count();
            counts.add(called);
            check(called >= 1 && called <= 3, "entire call draws only 1-3 fish");
            int surfaced = (int) existing.stream().filter(f -> f.expired).count();
            int spawned = env.fish.size() - before - surfaced;
            check(spawned == Math.max(0, called - available), "spawn fallback only fills missing call slots");
            for (var fish : List.of(quest, pond, held, remote, legendary)) {
                check(fish.getLureSpeedMult() == 1f && !fish.expired, "protected or distant prey is unchanged");
            }
            for (int i = before + surfaced; i < env.fish.size(); i++) {
                var fish = env.fish.get(i);
                float distance = Vector2f.sub(fish.at, jack.at, null).length();
                check(distance >= 799.99f && distance <= 1400.01f && fish.getLureSpeedMult() > 1f,
                        "new local prey starts in the call ring and responds immediately");
                check(fish.spec.rarity != FishRarity.LEGENDARY, "call cannot duplicate a legendary");
                fish.at.set(jack.at.x + 49f, jack.at.y);
                fish.advance(0.1f);
                check(fish.getLureSpeedMult() == 1f && !fish.expired && fish.entityScripts.isEmpty(),
                        "new call-spawned fish also leaves without lingering or fading at Jack");
            }
            before = env.fish.size();
            jack.tryLureFlare();
            check(env.fish.size() == before, "call cooldown prevents repeated fish spawns");
        }
        check(counts.equals(Set.of(1, 2, 3)), "all three call sizes occur");
    }

    private static void calledMovement() {
        for (FishMotion mode : FishMotion.values()) for (int fps : new int[]{30, 60, 144}) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                var jack = env.real("lantern_jack");
                jack.at.set(0f, 0f);
                var meal = meal(env, 1300f, 0f);
                meal.spec.rarity = FishRarity.RARE;
                meal.spec.motion = mode;
                meal.startLure(jack.getMote(), LegendaryShields.LURE_SECONDS);
                float previous = meal.at.length();
                for (int i = 0; i < fps * 15 && meal.getLureSpeedMult() > 1f; i++) {
                    meal.advance(1f / fps);
                    check(meal.at.length() <= previous + 0.001f, "call overrides wandering and dives: " + mode);
                    previous = meal.at.length();
                }
                check(Math.abs(previous - 50f) < 0.01f && meal.getLureSpeedMult() == 1f
                        && !meal.expired && meal.entityScripts.isEmpty(), "prey releases at 50 units in the arrival frame");
                check(ReflectionUtils.get(meal, "lureTarget", null, true) == null
                        && !(boolean) ReflectionUtils.get(meal, "diveScheduled", null, true),
                        "arrival clears tracking and the forced surface schedule");
                Vector2f arrived = new Vector2f(meal.at);
                jack.at.set(10000f, 10000f);
                for (int i = 0; i < fps; i++) meal.advance(1f / fps);
                check(!meal.at.equals(arrived) && meal.getLureSpeedMult() == 1f && !meal.expired,
                        "released prey swims normally without following Jack");
                check(new Vector2f(-3000f, 1500f).equals(ReflectionUtils.get(meal, "target", null, true)),
                        "original destination survives the call");
            }
        }
    }

    private static LegendaryEscapeChecks.Fish meal(LegendaryEscapeChecks.Environment env, float x, float y) {
        var fish = env.real("meal");
        fish.spec.rarity = FishRarity.COMMON;
        fish.at.set(x, y);
        fish.setSwimTarget(new Vector2f(-3000f, 1500f));
        return fish;
    }

    private static void callPool(LegendaryEscapeChecks.Environment env) {
        FishSpec spec = new FishSpec();
        spec.id = "meal";
        spec.rarity = FishRarity.COMMON;
        env.specs.put("meal", spec);
        env.persistent.put(FishRanges.PIN_KEY, Map.of("meal", Set.of(env.system.getId())));
    }

    private static void releaseEdges() {
        for (float distance : new float[]{0f, 49f, 50f, 51f, 1300f}) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                var jack = env.real("lantern_jack");
                jack.at.set(0f, 0f);
                var fish = meal(env, distance, 0f);
                fish.startLure(jack.getMote(), LegendaryShields.LURE_SECONDS);
                fish.advance(0f);
                check(fish.getLureSpeedMult() > 1f && fish.at.x == distance, "pause does not advance the lure");
                fish.advance(10f);
                check(fish.getLureSpeedMult() == 1f && !fish.expired, "long frame releases rather than overshooting");
            }
        }
        for (boolean expired : new boolean[]{false, true}) {
            try (var env = new LegendaryEscapeChecks.Environment()) {
                var jack = env.real("lantern_jack");
                var fish = meal(env, 1300f, 0f);
                fish.startLure(jack.getMote(), expired ? 20f : 0.1f);
                jack.expired = expired;
                fish.advance(0.2f);
                check(fish.getLureSpeedMult() == 1f && ReflectionUtils.get(fish, "lureTarget", null, true) == null,
                        "timeout or missing Jack releases the lure");
                check(new Vector2f(-3000f, 1500f).equals(ReflectionUtils.get(fish, "target", null, true)),
                        "aborted lure also preserves ordinary destination");
            }
        }
    }
}
