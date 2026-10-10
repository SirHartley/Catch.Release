package catchrelease.tools;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.campaign.fish.legendary.QuorumShellGame;
import catchrelease.tools.LegendaryEscapeChecks.Buried;
import catchrelease.tools.LegendaryEscapeChecks.Environment;
import catchrelease.tools.LegendaryEscapeChecks.Fish;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public final class LegendaryLifetimeChecks {

    private static int checks;
    private static final List<String> SPECIES = List.of("lantern_jack", "slipstream_moray",
            "quorum", "false_dawn", "longliner", "abyssal_ghost_manta");

    private static class Shell extends QuorumShellGame {

        static int size() { return states.size(); }
    }

    public static void main(String[] args) {
        swimming();
        expiryExclusions();
        departure();
        shellDeparture();
        mantaDeparture();
        System.out.println("Legendary lifetime checks passed: " + checks);
    }

    private static void swimming() {
        for (int fps : new int[]{30, 60, 144}) try (Environment env = new Environment()) {
            Fish quorum = env.real("quorum");
            quorum.at.set(10000f, 10000f);
            quorum.setSwimTarget(new Vector2f(quorum.at));
            quorum.keepSurfaced(600f);
            Vector2f before = new Vector2f(quorum.at);
            for (int i = 0; i < fps * 300; i++) quorum.advance(1f / fps);
            check(!quorum.expired && quorum.entityScripts.isEmpty(),
                    "shielded Quorum survives repeated arrivals beyond sight range at " + fps + " Hz");
            check(Vector2f.sub(quorum.at, before, null).length() > 3000f,
                    "Quorum keeps swimming past its original destination");
            check(env.fish.size() == 4, "escort is maintained without duplicate splinters");
            check(!env.haunt.isSightedNow(quorum.spec, env.system), "distance sighting check remains effective");
            quorum.at.set(100f, 100f);
            check(env.haunt.isSightedNow(quorum.spec, env.system), "nearby surfaced fish is sighted");
            env.haunt.close();
            check(!quorum.expired, "ending haunt effects does not remove the real fish");
        }
        for (String id : List.of("slipstream_moray", "longliner", "abyssal_ghost_manta")) {
            try (Environment env = new Environment()) {
                Fish fish = env.real(id);
                fish.at.set(10000f, 10000f);
                fish.setSwimTarget(new Vector2f(fish.at));
                fish.advance(0.1f);
                fish.advance(0.1f);
                check(fish.entityScripts.isEmpty() && !fish.expired, id + " survives swim arrival");
                fish.startTravelDash(new Vector2f(100f, 0f), 0.1f);
                fish.advance(0.2f);
                check(fish.entityScripts.isEmpty() && !fish.expired, id + " survives dash completion");
            }
        }
    }

    private static void expiryExclusions() {
        try (Environment env = new Environment()) {
            Fish ordinary = env.real("ordinary");
            ordinary.spec.rarity = FishRarity.COMMON;
            Fish phantom = new Fish(env, "quorum", true, null);
            Fish shard = env.real("quorum_shard");
            for (Fish fish : List.of(ordinary, phantom, shard)) {
                fish.setSwimTarget(new Vector2f(fish.at));
                fish.advance(0.1f);
                check(!fish.isRealLegendary() && fish.entityScripts.size() == 1,
                        "ordinary fish, phantoms and shards retain arrival expiry");
            }
        }
    }

    private static void departure() {
        try (Environment env = new Environment()) {
            Fish ordinary = env.real("ordinary");
            ordinary.spec.rarity = FishRarity.COMMON;
            for (String id : SPECIES) {
                Fish fish = env.real(id);
                check(fish.isRealLegendary(), id + " has system lifetime");
                LegendaryChases.Chase state = LegendaryChases.getState(id);
                state.systemId = "original-host";
                state.residency = 7;
                state.seenAt = 12345L;
                state.revealed = true;
                state.provoked = true;
                state.shieldUnits = 2;
            }
            Fish buried = env.real("quorum");
            buried.plugin = new Buried(buried);
            buried.tags.clear();
            buried.tags.add(BuriedMoteEntityPlugin.BURIED_TAG);
            env.haunt.reportCurrentLocationChanged(null, env.system);
            env.haunt.reportCurrentLocationChanged(env.system, env.system);
            check(env.fish.stream().noneMatch(f -> f.expired), "entry and same-location callbacks preserve fish");
            env.haunt.reportCurrentLocationChanged(env.system, null);
            check(!ordinary.expired, "departure does not clear ordinary fish");
            check(env.fish.stream().filter(f -> f != ordinary).allMatch(f -> f.expired),
                    "departure clears every surfaced and buried legendary, even without an active haunt");
            for (String id : SPECIES) {
                LegendaryChases.Chase state = LegendaryChases.getState(id);
                check("original-host".equals(state.systemId) && state.residency == 7
                                && state.seenAt == 12345L && state.revealed && state.provoked
                                && state.shieldUnits == 2 && !state.caught,
                        id + " departure preserves host, relocation clock, reveal, provocation and shield state");
            }
        }
    }

    private static void shellDeparture() {
        try (Environment env = new Environment()) {
            Fish fish = env.real("quorum");
            LegendaryShields.maintainSatellites(fish);
            LegendaryChases.getState("quorum").shieldUnits = 0;
            QuorumShellGame.onFailedCatch(fish);
            check(Shell.size() == 1 && env.fish.size() == 18,
                    "fixture includes real Quorum, escorts, shell decoys and escape illusions");
            env.haunt.reportCurrentLocationChanged(env.system, null);
            check(env.fish.stream().allMatch(f -> f.expired), "departure clears the Quorum and all attached bodies");
            check(Shell.size() == 0, "departure releases the shell-game controller");
        }
    }

    private static void mantaDeparture() {
        try (Environment env = new Environment()) {
            Fish fish = env.real("abyssal_ghost_manta");
            LegendaryShields.onFailedCatch(fish.getMote());
            check(env.fish.size() == 3 && env.haunt.getModuleCount() > 0, "fixture has active manta formation");
            env.haunt.reportCurrentLocationChanged(env.system, null);
            check(env.fish.stream().allMatch(f -> f.expired) && env.haunt.getModuleCount() == 0,
                    "departure removes manta, formation copies and haunt modules synchronously");
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
