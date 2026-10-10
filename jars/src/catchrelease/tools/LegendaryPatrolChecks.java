package catchrelease.tools;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.*;
import catchrelease.tools.LegendaryEscapeChecks.Buried;
import catchrelease.tools.LegendaryEscapeChecks.Environment;
import catchrelease.tools.LegendaryEscapeChecks.Fish;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.io.*;

public final class LegendaryPatrolChecks {

    private static int checks;
    private static final List<String> PATROLLERS = List.of("lantern_jack", "slipstream_moray",
            "quorum", "abyssal_ghost_manta");

    private static class Haunt extends HauntWindDownChecks.Haunt {

        void begin(Fish fish, Environment env) { start(fish.spec, env.system); }
        boolean abandon() { return fail(); }
    }

    private static class Decoy extends LonglinerDecoy {

        int boats;
        void reconcile() { reconcile(null); }
        @Override protected void spawnBoat(com.fs.starfarer.api.campaign.StarSystemAPI system) { boats++; }
    }

    public static void main(String[] args) throws Exception {
        passiveMovement();
        activeMovement();
        returnProgress();
        quorumReturn();
        attachedFish();
        exceptions();
        resetTimers();
        completedCatch();
        System.out.println("Legendary patrol checks passed: " + checks);
    }

    private static void passiveMovement() {
        for (String id : PATROLLERS) for (int fps : new int[]{30, 60, 144}) {
            for (boolean buried : new boolean[]{false, true}) try (Environment env = new Environment()) {
                Vector2f center = new Vector2f(10000f, -5000f);
                env.star(center.x, center.y, 800f, 2400f);
                env.star(center.x + 4500f, center.y + 2000f, 500f, 1000f);
                Fish fish = env.real(id);
                fish.at.set(center.x + 5850f, center.y);
                fish.keepSurfaced(100f);
                Buried hidden = new Buried(fish);
                double traveled = 0;
                for (int i = 0; i < fps * 30; i++) {
                    Vector2f before = new Vector2f(fish.at);
                    fish.setSwimTarget(new Vector2f(fish.at.x + 10000f, fish.at.y));
                    if (buried) hidden.advance(1f / fps);
                    else fish.advance(1f / fps);
                    float radius = Vector2f.sub(fish.at, center, null).length();
                    check(radius <= 6000.01f && radius >= 2550f - 0.01f,
                            id + " stays in its patrol area and outside the star at " + fps + " Hz");
                    Vector2f companion = new Vector2f(center.x + 4500f, center.y + 2000f);
                    check(Vector2f.sub(fish.at, companion, null).length() >= 1150f - 0.01f,
                            "patrol also clears companion stars");
                    traveled += Vector2f.sub(fish.at, before, null).length();
                }
                check(traveled > 500f, "outward patrol keeps moving instead of sticking to the boundary");
                check(!fish.expired, "patrol has no swim expiry");
            }
        }
        try (Environment env = new Environment()) {
            Fish fish = env.real("slipstream_moray");
            fish.at.set(14000f, 0f);
            fish.setSwimTarget(new Vector2f(16000f, 0f));
            fish.advance(0.1f);
            check(fish.at.length() <= 6000.01f, "an out-of-bounds inactive mote rejoins the origin patrol");
            fish.at.set(14000f, 0f);
            fish.setHeld(true);
            fish.advance(0.1f);
            check(fish.at.x == 14000f, "held fish bypass patrol confinement");
        }
    }

    private static void activeMovement() {
        for (String id : PATROLLERS) try (Environment env = new Environment()) {
            Fish fish = env.real(id);
            env.specs.put(id, fish.spec);
            var state = LegendaryChases.getState(id);
            state.systemId = env.system.getId();
            state.provoked = true;
            fish.at.set(5950f, 0f);
            fish.swim(new Vector2f(6150f, 0f));
            check(fish.at.length() <= 6000.01f, "provocation alone does not release the patrol limit");
            Haunt haunt = new Haunt();
            haunt.begin(fish, env);
            fish.at.set(5950f, 0f);
            fish.swim(new Vector2f(6150f, 0f));
            check(fish.at.x == 6150f && state.roaming, "haunt start releases the outer patrol limit");
            fish.at.set(14000f, 0f);
            fish.swim(new Vector2f(14100f, 0f));
            check(fish.at.x == 14100f, "active fish is not returned by ordinary movement");
            haunt.abandon();
            check(!state.roaming && fish.at.length() <= 6000.01f, "failed hunt restores patrol placement");
            fish.at.set(5950f, 0f);
            fish.swim(new Vector2f(6150f, 0f));
            check(fish.at.length() <= 6000.01f, "returned fish cannot immediately leave its patrol");
        }
    }

    private static void returnProgress() {
        for (String id : PATROLLERS) try (Environment env = new Environment()) {
            Fish fish = env.real(id);
            env.specs.put(id, fish.spec);
            var state = LegendaryChases.getState(id);
            state.systemId = env.system.getId();
            state.provoked = true;
            state.shieldUnits = 0;
            state.shieldPopped = true;
            state.shieldStampAt = 42L;
            state.residency = 9;
            fish.tryBaseShieldDeflect();
            Haunt haunt = new Haunt();
            haunt.begin(fish, env);
            fish.at.set(20000f, -15000f);
            fish.startTravelDash(new Vector2f(900f, 0f), 100f);
            check(haunt.abandon(), "free real fish returns immediately");
            check(!state.shieldPopped && fish.isBaseShieldUp()
                    && LegendaryShields.isShielded(fish), "return restores persistent and timed shields");
            check(state.residency == 9 && env.system.getId().equals(state.systemId),
                    "encounter reset does not change the host ledger");
            if (id.equals("lantern_jack")) check(LegendaryShields.getStackedRings(fish) == 3,
                    "Jack gets its fresh-encounter shell count");
            check(!fish.isDashing() && !state.provoked && !state.roaming, "return ends escape behavior");
        }
    }

    private static void quorumReturn() {
        try (Environment env = new Environment()) {
            Fish fish = env.real("quorum");
            env.specs.put(fish.spec.id, fish.spec);
            var state = LegendaryChases.getState(fish.spec.id);
            state.shieldUnits = 0;
            state.provoked = true;
            Haunt haunt = new Haunt();
            haunt.begin(fish, env);
            QuorumShellGame.advance(fish, 0.1f);
            QuorumShellGame.onFailedCatch(fish);
            check(env.fish.size() == 15, "bare Quorum has shell bodies and escape illusions");
            fish.at.set(14000f, 0f);
            haunt.abandon();
            check(env.fish.stream().filter(f -> !f.expired).count() == 4 && state.shieldUnits == 3,
                    "return removes shell bodies and restores all three escorts");
            check(!QuorumShellGame.advance(fish, 1f), "restored Quorum patrol has no shell game");
            state.provoked = true;
            check(!QuorumShellGame.advance(fish, 1f), "shell game waits for actual haunt start");
            haunt.begin(fish, env);
            check(!QuorumShellGame.advance(fish, 0.1f), "new hunt must defeat the restored escorts first");
            state.shieldUnits = 0;
            for (Fish escort : env.fish) if (escort.orbit == fish.getMote()) escort.expired = true;
            check(QuorumShellGame.advance(fish, 0.1f)
                    && env.fish.stream().filter(f -> !f.expired).count() == 3,
                    "new haunt reconstructs only the two ordinary shell bodies");
            haunt.abandon();
        }
    }

    private static void exceptions() {
        try (Environment env = new Environment()) {
            Fish longliner = env.real("longliner");
            env.specs.put(longliner.spec.id, longliner.spec);
            longliner.at.set(14000f, 0f);
            longliner.swim(new Vector2f(14100f, 0f));
            check(longliner.at.x == 14100f, "Longliner has no patrol radius limit");
            var state = LegendaryChases.getState(longliner.spec.id);
            state.provoked = true;
            state.revealed = state.encountered = state.shieldPopped = true;
            state.systemId = env.system.getId();
            Haunt haunt = new Haunt();
            haunt.begin(longliner, env);
            longliner.setHeld(true);
            check(!haunt.abandon() && !longliner.expired && state.shieldPopped && state.revealed,
                    "held Longliner cannot be replaced by its disguise");
            longliner.setHeld(false);
            haunt.abandon();
            check(longliner.expired && longliner.removed && !state.provoked && !state.roaming
                    && !state.revealed && !state.shieldPopped && state.encountered,
                    "Longliner abandons its mote and resets hull and disguise, not recognition");
            env.tutorialStage = catchrelease.campaign.fish.tutorial.FishingIntro.DONE;
            Decoy decoy = new Decoy();
            decoy.reconcile();
            check(decoy.boats == 1, "existing Longliner reconciliation offers a fresh boat after reset");
            Fish ordinary = env.real("ordinary");
            ordinary.spec.rarity = FishRarity.COMMON;
            ordinary.swim(new Vector2f(20000f, 0f));
            check(ordinary.at.x == 20000f, "ordinary fish do not acquire a legendary patrol limit");
        }
        try (Environment env = new Environment()) {
            env.star(20000f, -10000f, 8000f, 12000f);
            Fish dawn = env.real("false_dawn");
            env.specs.put(dawn.spec.id, dawn.spec);
            var corona = FalseDawnOrbit.findCorona(env.system);
            Vector2f at = LegendarySpawns.position(env.system, dawn.spec);
            check(at != null, "large False Dawn coronas are not rejected by the 6000-unit rule");
            dawn.at.set(50000f, 40000f);
            LegendaryChases.getState(dawn.spec.id).shieldUnits = 0;
            Haunt haunt = new Haunt();
            haunt.begin(dawn, env);
            check(haunt.abandon(), "False Dawn can return to a large corona");
            check(LegendaryShields.isShielded(dawn) && LegendaryShields.getDawnCharges() == 1,
                    "False Dawn regains its single starting charge");
            LegendaryChases.getState(dawn.spec.id).shieldUnits = 2;
            haunt.begin(dawn, env);
            haunt.abandon();
            check(LegendaryShields.getDawnCharges() == 1, "reset also discards False Dawn's extra mine-fed charge");
            float distance = Vector2f.sub(dawn.at, corona.getParams().relatedEntity.getLocation(), null).length();
            check(distance >= FalseDawnOrbit.innerRadius(corona) - 0.01f
                    && distance <= FalseDawnOrbit.outerRadius(corona) + 0.01f && distance > 6000f,
                    "False Dawn return uses the corona instead of the ordinary patrol disk");
        }
    }

    private static void attachedFish() {
        try (Environment env = new Environment()) {
            Fish fish = env.real("quorum");
            env.specs.put(fish.spec.id, fish.spec);
            var state = LegendaryChases.getState(fish.spec.id);
            state.shieldUnits = 2;
            LegendaryShields.maintainSatellites(fish);
            Haunt haunt = new Haunt();
            haunt.begin(fish, env);
            fish.at.set(14000f, 0f);
            haunt.abandon();
            check(env.fish.size() == 4 && state.shieldUnits == 3,
                    "return fills missing escorts without duplicating surviving ones");
            for (Fish escort : env.fish) {
                check(!escort.expired && Vector2f.sub(escort.at, fish.at, null).length() < 1f,
                        "surviving escorts return with the Quorum");
            }
        }
        try (Environment env = new Environment()) {
            env.tutorialStage = catchrelease.campaign.fish.tutorial.FishingIntro.DONE;
            Fish fish = env.real(MantaFormationModule.SPECIES);
            env.specs.put(fish.spec.id, fish.spec);
            var state = LegendaryChases.getState(fish.spec.id);
            state.systemId = env.system.getId();
            LegendaryShields.onFailedCatch(fish.getMote());
            env.haunt.advance(4f);
            check(env.fish.size() == 3, "active manta has two formation copies");
            fish.at.set(14000f, 0f);
            env.lampsOn = false;
            env.haunt.advance(12f);
            check(env.fish.stream().filter(f -> !f.expired).count() == 1 && fish.at.length() <= 6000.01f,
                    "manta cleanup removes copies before returning the real fish");
            fish.advance(0.1f);
            check(fish.at.length() <= 6000.01f && !state.roaming,
                    "old manta formation cannot move the returned fish back out");
        }
    }

    private static void resetTimers() throws Exception {
        try (Environment env = new Environment()) {
            Fish jack = env.real("lantern_jack");
            env.specs.put(jack.spec.id, jack.spec);
            var state = LegendaryChases.getState(jack.spec.id);
            state.systemId = env.system.getId();
            state.shieldUnits = 0;
            jack.tryBaseShieldDeflect();
            jack.startEvasive();
            jack.setHunting(true);
            jack.applyBlast(100f, 0.9f, 100f);
            for (String name : List.of("flareCooldown", "flareRing", "phaseLeft", "rerollLeft")) {
                var field = FishEntityPlugin.class.getDeclaredField(name);
                field.setAccessible(true);
                field.setFloat(jack, 20f);
            }
            Fish called = env.real("prey");
            called.spec.rarity = FishRarity.COMMON;
            called.startLure(jack.getMote(), 20f);
            Fish unrelated = env.real("other-prey");
            unrelated.spec.rarity = FishRarity.COMMON;
            unrelated.startLure(called.getMote(), 20f);
            Haunt haunt = new Haunt();
            haunt.begin(jack, env);
            haunt.abandon();
            check(called.getLureSpeedMult() == 1f && unrelated.getLureSpeedMult() > 1f,
                    "reset releases only this Jack's called prey");
            check(!jack.isEvading() && jack.isBaseShieldUp(), "combat evasion and timed shield reset");
            for (String name : List.of("flareCooldown", "flareRing", "phaseLeft", "rerollLeft")) {
                var field = FishEntityPlugin.class.getDeclaredField(name);
                field.setAccessible(true);
                check(field.getFloat(jack) == 0f, "fresh encounter clears " + name);
            }
            Vector2f before = new Vector2f(jack.at);
            jack.advance(0.1f);
            check(Vector2f.sub(jack.at, before, null).length() > 1f,
                    "old stun and slowdown do not hold the reset patrol");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(state); }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                var saved = (LegendaryChases.Chase) in.readObject();
                check(saved.shieldUnits == 3 && !saved.shieldPopped && !saved.provoked && !saved.roaming,
                        "fresh defenses and inactive hunt state survive save/load");
            }
        }
    }

    private static void completedCatch() {
        try (Environment env = new Environment()) {
            Fish fish = env.real("longliner");
            env.specs.put(fish.spec.id, fish.spec);
            var state = LegendaryChases.getState(fish.spec.id);
            state.revealed = state.shieldPopped = true;
            Haunt haunt = new Haunt();
            haunt.begin(fish, env);
            LegendaryChases.noteCaught(fish.spec.id);
            haunt.abandon();
            state.resetEncounter();
            check(state.caught && state.encountered && state.systemId == null
                    && state.shieldPopped && state.revealed,
                    "cleanup cannot reset a completed catch or restore its disguise");
            Decoy decoy = new Decoy();
            decoy.reconcile();
            check(decoy.boats == 0, "caught Longliner cannot respawn as a boat");
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
