package catchrelease.tools;

import catchrelease.abilities.searchlight.rendering.SearchlightFanRenderer;
import catchrelease.campaign.fish.fisherman.FishermanBehavior;
import catchrelease.campaign.fish.legendary.*;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.math.CircularArc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class LonglinerCleanupChecks {

    private static int checks;

    private static class Haunt extends LegendaryHaunt {

        @Override protected void playFoghorn(float pitch) { }
        boolean abandon() { return fail(); }
        void close() { stop(); }
    }

    private static class Decoy extends LonglinerDecoy {

        StarSystemAPI spawnedIn;
        void refresh() { reconcile(getBoat()); }
        void remove(CampaignFleetAPI boat) { retire(boat); }
        @Override protected void spawnBoat(StarSystemAPI system) { spawnedIn = system; }
    }

    private static class BoatOwner extends FishermanBehavior {

        private static class LitLamp extends Lamp {

            final SearchlightFanRenderer renderer;

            LitLamp() {
                super(new CircularArc(new Vector2f(), 100f, 0f, 90f));
                renderer = new SearchlightFanRenderer(new Vector2f(), new Vector2f(100f, 0f),
                        100f, 45f, Color.WHITE);
                fan = renderer;
            }
        }

        final LitLamp light = new LitLamp();

        BoatOwner(CampaignFleetAPI fleet) {
            super(fleet);
            lamps = new ArrayList<>(List.of(light));
        }
    }

    private static class Boat {

        final CampaignFleetAPI fleet;
        BoatOwner owner;
        boolean expired;
        boolean alive = true;
        int despawns;

        Boat(Fixture f, boolean decoy) {
            MemoryAPI memory = proxy(MemoryAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getBoolean" -> decoy && LonglinerDecoy.DECOY_FLAG.equals(a[0]);
                case "get" -> null;
                default -> throw new AssertionError(m);
            });
            fleet = proxy(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "equals" -> p == a[0];
                case "hashCode" -> System.identityHashCode(p);
                case "getMemoryWithoutUpdate" -> memory;
                case "getContainingLocation" -> f.env.system;
                case "getScripts" -> List.of(owner);
                case "isExpired" -> expired;
                case "isAlive" -> alive;
                case "setExpired" -> { expired = (boolean) a[0]; yield null; }
                case "setAI" -> null;
                case "despawn" -> {
                    check(owner.isDone() && owner.light.renderer.isExpired(), "lights stop before despawn");
                    despawns++;
                    alive = false;
                    yield null;
                }
                default -> throw new AssertionError(m);
            });
            owner = new BoatOwner(fleet);
            f.env.fleets.add(fleet);
        }
    }

    private static class Fixture implements AutoCloseable {

        final LegendaryEscapeChecks.Environment env = new LegendaryEscapeChecks.Environment();
        final LegendaryEscapeChecks.Fish fish = env.real(LonglinerShield.SPECIES);
        final LegendaryChases.Chase state = LegendaryChases.getState(fish.spec.id);
        final Haunt haunt = new Haunt();
        final Decoy decoy = new Decoy();
        final List<StarSystemAPI> systems = new ArrayList<>();

        Fixture(boolean alternate) {
            env.tutorialStage = FishingIntro.DONE;
            env.specs.put(fish.spec.id, fish.spec);
            fish.setSwimTarget(new Vector2f(7000f, 0f));
            state.systemId = env.system.getId();
            state.revealed = state.encountered = state.provoked = true;
            state.longlinerBroken = 1;
            state.longlinerRotation = 45f;
            state.scars = 3;
            state.residency = 4;
            state.seenAt = 1234L;
            env.scripts.clear();
            env.scripts.add(haunt);
            systems.add(env.system);
            if (alternate) addHost();
            SectorAPI sector = Global.getSector();
            EconomyAPI economy = proxy(EconomyAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getMarkets" -> List.of();
                default -> throw new AssertionError(m);
            });
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getStarSystems", "getAllLocations" -> systems;
                case "getEconomy" -> economy;
                default -> m.invoke(sector, a);
            }));
        }

        void addHost() {
            systems.add(proxy(StarSystemAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getId", "getBaseName" -> "other-system";
                case "hasTag" -> false;
                case "getAllEntities", "getCustomEntities" -> List.of();
                case "getTags" -> Set.of();
                case "getLocation", "getStar", "getConstellation" -> null;
                default -> throw new AssertionError(m);
            }));
        }

        void start() {
            haunt.advance(LegendaryHaunt.RAMP_SECONDS);
            check(env.fish.stream().filter(f -> f.phantom && !f.expired).count() == 18, "school starts");
        }

        void relocated() {
            check("other-system".equals(state.systemId) && state.residency == 5 && !state.relocationPending,
                    "failure changes host and residency while the player stays in the old system");
            check(!state.revealed && !state.provoked && !state.roaming && !state.shieldPopped
                            && state.shieldUnits == -1 && state.longlinerBroken == 0
                            && state.longlinerRotation == 0f && state.scars == 0 && state.seenAt == 0L,
                    "new encounter has no saved combat or sighting progress");
            check(state.encountered && !state.caught, "recognition survives without awarding a catch");
            check(!LegendaryChases.isCurrentResidency(fish.spec.id, env.system.getId(), 4), "old rumors expire");
            check(haunt.getModuleCount() == 0 && env.fish.stream().allMatch(f -> f.expired),
                    "real mote and all school copies are gone");
            decoy.refresh();
            check(decoy.spawnedIn == systems.get(1), "replacement disguise belongs only to the new host");
        }

        @Override public void close() { haunt.close(); env.close(); }
    }

    public static void main(String[] args) {
        for (boolean lampsOff : new boolean[]{false, true}) try (Fixture f = new Fixture(true)) {
            f.start();
            Boat stale = new Boat(f, true);
            Boat realFisherman = new Boat(f, false);
            f.env.sectorValues.put(LonglinerDecoy.BOAT_KEY, stale.fleet);
            if (lampsOff) f.env.lampsOn = false;
            else {
                f.fish.at.set(10000f, 0f);
                f.haunt.advance(LegendaryHaunt.LINGER_SECONDS);
            }
            f.fish.setHeld(true);
            f.haunt.advance(LegendaryHaunt.FADE_SECONDS + 1f);
            check(!f.fish.expired && f.state.residency == 4, "held catch postpones failure");
            f.fish.setHeld(false);
            f.haunt.advance(0.1f);
            f.relocated();
            check(stale.expired && stale.owner.isDone() && stale.owner.light.renderer.isExpired()
                    && !f.env.fleets.contains(stale.fleet) && !f.env.sectorValues.containsKey(LonglinerDecoy.BOAT_KEY),
                    "failure removes stale disguise, lights and booking");
            stale.owner.advance(1f);
            check(stale.owner.light.renderer.isExpired(), "removed boat cannot restart its lights");
            check(!realFisherman.expired && !realFisherman.owner.isDone(), "real Fisherman is untouched");
        }
        for (boolean active : new boolean[]{false, true}) try (Fixture f = new Fixture(true)) {
            if (active) f.start();
            f.fish.expired = true;
            if (active) f.haunt.advance(0.01f);
            else f.decoy.refresh();
            f.relocated();
        }
        try (Fixture f = new Fixture(false)) {
            f.start();
            f.haunt.abandon();
            check(f.state.relocationPending && LegendaryChases.getHostSystemId(f.fish.spec) == null,
                    "no alternative leaves the Longliner unspawnable");
            check(!LegendaryChases.matches(f.fish.spec, f.env.system), "old system is no longer habitat");
            f.decoy.refresh();
            check(f.decoy.spawnedIn == null, "no fallback boat in the failed system");
            f.addHost();
            LegendaryChases.getHostSystemId(f.fish.spec);
            f.relocated();
        }
        try (Fixture f = new Fixture(true)) {
            Boat booked = new Boat(f, true);
            Boat orphan = new Boat(f, true);
            f.env.sectorValues.put(LonglinerDecoy.BOAT_KEY, booked.fleet);
            f.decoy.remove(orphan.fleet);
            check(f.env.sectorValues.get(LonglinerDecoy.BOAT_KEY) == booked.fleet,
                    "retiring an unbooked duplicate preserves the current booking");
            booked.expired = true;
            f.state.caught = true;
            f.decoy.refresh();
            check(booked.owner.light.renderer.isExpired() && booked.owner.isDone()
                    && !f.env.fleets.contains(booked.fleet), "already-expired boats still clean up lights");
            check(f.decoy.spawnedIn == null, "caught Longliner never respawns");
        }
        System.out.println("Longliner cleanup checks passed: " + checks);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        checks++;
    }
}
