package catchrelease.tools;

import catchrelease.abilities.rod.ability.PondInteractionAbilityPlugin;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class PondProximityChecks {

    private static int checks;

    private static class Lamps extends SearchlightAbilityPlugin {

        void bind(CampaignFleetAPI fleet) { init(ABILITY_ID, fleet); }
        void on() { turnedOn = true; level = 1f; recoverRuntimeLocation(getFleet().getContainingLocation()); }
        void reload() { readResolve(); }
        @Override public boolean owns(Searchlight light) { return true; }
        @Override protected void ensureImpressionRenderer() { }
        @Override protected void applyBeamSlow(CampaignFleetAPI fleet) { }
        @Override protected String getDeactivationText() { return null; }
    }

    private static class Rod extends PondInteractionAbilityPlugin {

        void bind(CampaignFleetAPI fleet) { entity = fleet; }
        SectorEntityToken pond() { return getPond(); }
    }

    private static class Pond extends MaskedFishingPondTerrainPlugin {

        final Vector2f at = new Vector2f();
        final CampaignTerrainAPI token;

        Pond(Fixture f, boolean visual) {
            visualOnly = visual;
            token = proxy(CampaignTerrainAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getPlugin" -> this;
                case "hasTag" -> !visualOnly && TERRAIN_ID.equals(a[0]);
                case "getRadius" -> 100f;
                case "getLocation" -> at;
                case "getContainingLocation" -> f.location;
                case "addScript" -> null;
                default -> throw new AssertionError(m);
            });
            entity = token;
            f.terrain.add(token);
            readResolve();
        }
        @Override protected void throwOpeningDistortion() { }
    }

    private static class Fixture implements AutoCloseable {

        final List<CampaignTerrainAPI> terrain = new ArrayList<>();
        final Vector2f at = new Vector2f();
        final Lamps lamps = new Lamps();
        final Rod rod = new Rod();
        final Searchlight beam = new Searchlight();
        final CampaignFleetAPI fleet;
        final LocationAPI system;
        LocationAPI location;
        int scans;
        boolean installed = true;
        boolean paused;

        Fixture() {
            if (Global.getSector() != null) throw new IllegalStateException("Run outside Starsector.");
            system = location(false);
            location = system;
            StatBonus detection = new StatBonus();
            MutableFleetStatsAPI stats = proxy(MutableFleetStatsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getDetectedRangeMod" -> detection;
                default -> throw new AssertionError(m);
            });
            fleet = proxy(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getContainingLocation" -> location;
                case "getLocation" -> at;
                case "getAbility" -> installed ? lamps : null;
                case "getStats" -> stats;
                case "isPlayerFleet", "isInCurrentLocation" -> true;
                case "isVisibleToPlayerFleet", "isAIMode" -> false;
                default -> throw new AssertionError(m);
            });
            var data = new HashMap<String, Object>();
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getPlayerFleet" -> fleet;
                case "getCurrentLocation" -> location;
                case "getPersistentData" -> data;
                case "isPaused" -> paused;
                case "addScript", "reportPlayerDeactivatedAbility" -> null;
                default -> throw new AssertionError(m);
            }));
            AbilitySpecAPI spec = proxy(AbilitySpecAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getUILoop", "getWorldLoop" -> null;
                case "getDeactivationDays", "getDeactivationCooldown" -> 0f;
                default -> throw new AssertionError(m);
            });
            Global.setSettings(proxy(SettingsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getAbilitySpec" -> spec;
                case "getFloat" -> 1f;
                case "getInt" -> 1;
                case "getBoolean" -> false;
                case "getColor" -> java.awt.Color.WHITE;
                default -> throw new AssertionError(m);
            }));
            lamps.bind(fleet);
            rod.bind(fleet);
            beam.bindOwner(lamps, fleet, system);
        }

        LocationAPI location(boolean hyperspace) {
            return proxy(LocationAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isHyperspace" -> hyperspace;
                case "getTerrainCopy" -> { scans++; yield new ArrayList<>(terrain); }
                default -> throw new AssertionError(m);
            });
        }

        boolean allowed() { return SearchlightAbilityPlugin.canRunHere(fleet); }

        @Override public void close() {
            Global.setSector(null);
            Global.setSettings(null);
        }
    }

    public static void main(String[] args) {
        terrainAndRange();
        repeatedQueries();
        pondChanges();
        locationAndLoad();
        System.out.println("Pond proximity checks passed: " + checks);
    }

    private static void terrainAndRange() {
        try (Fixture f = new Fixture()) {
            check(!SearchlightAbilityPlugin.isNearActivePond(null), "null fleet");
            f.terrain.add(proxy(CampaignTerrainAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasTag" -> true;
                case "getPlugin" -> new BaseTerrain();
                default -> throw new AssertionError(m);
            }));
            Pond visual = new Pond(f, true);
            visual.activate();
            check(!SearchlightAbilityPlugin.isNearActivePond(f.fleet), "ignore visual-only and unrelated terrain");
            check(f.rod.pond() == null, "ROD ignores visual-only and unrelated terrain");
            Pond pond = new Pond(f, false);
            check(!SearchlightAbilityPlugin.isNearActivePond(f.fleet), "closed pond does not block lamps");
            check(f.rod.pond() == pond.token, "ROD finds closed terrain pond");
            pond.activate();
            check(SearchlightAbilityPlugin.isNearActivePond(f.fleet), "open pond blocks lamps");
            float range = 100f * PondConstants.POND_INTERACT_RANGE_MULT;
            for (float offset : new float[]{-0.1f, 0f, 0.1f}) {
                f.at.set(range + offset, 0f);
                check(SearchlightAbilityPlugin.isNearActivePond(f.fleet) == (offset < 0f), "strict distance boundary");
                check((f.rod.pond() != null) == (offset < 0f), "ROD distance unchanged");
            }
            f.location = null;
            check(!f.allowed() && f.rod.pond() == null, "fleet without a location");
        }
    }

    private static void repeatedQueries() {
        try (Fixture f = new Fixture()) {
            Pond pond = new Pond(f, false);
            f.lamps.on();
            f.lamps.advance(0.1f);
            check(f.scans == 1, "one terrain snapshot per ability update");
            for (int i = 0; i < 10000; i++) {
                check(f.allowed() && f.lamps.isUsable() && f.lamps.isRuntimeCurrent()
                        && f.beam.isRuntimeCurrent(), "repeated availability and beam checks");
                SearchlightAbilityPlugin.getBeamVisibilityAt(f.at);
            }
            check(f.scans == 1, "render and UI queries do not rescan terrain");
            f.lamps.advance(0.1f);
            check(f.scans == 2, "next update refreshes once");
            pond.activate();
            f.at.set(10000f, 0f);
            f.lamps.advance(0.1f);
            check(f.allowed(), "distant open pond does not block lamps");
            f.at.set(0f, 0f);
            f.lamps.advance(0.1f);
            check(!f.allowed() && !f.lamps.isActive(), "movement into range disables lamps on update");
        }
    }

    private static void pondChanges() {
        try (Fixture f = new Fixture()) {
            Pond pond = new Pond(f, false);
            f.paused = true;
            f.lamps.on();
            check(f.allowed(), "first paused query initializes cache");
            int before = f.scans;
            pond.activate();
            check(!f.lamps.isRuntimeCurrent() && !f.beam.isRuntimeCurrent() && !f.lamps.isUsable(),
                    "opening while paused immediately blocks rendering and activation");
            check(f.scans == before + 1, "opening invalidates once");
            pond.deactivate();
            check(f.allowed() && f.beam.isRuntimeCurrent(), "closing while paused refreshes availability");
            check(f.scans == before + 2, "closing invalidates once");
            pond.activate();
            check(!f.allowed(), "second opening invalidates cache");
            f.terrain.clear();
            f.lamps.advance(0f);
            check(f.allowed(), "removed terrain disappears on next update");
            Pond added = new Pond(f, false);
            added.activate();
            check(!f.allowed(), "newly created open pond invalidates cache");
        }
    }

    private static void locationAndLoad() {
        try (Fixture f = new Fixture()) {
            check(f.allowed(), "initial location");
            int before = f.scans;
            f.location = f.location(false);
            check(f.allowed() && f.scans == before + 1, "location change refreshes before advance");
            f.lamps.on();
            f.location = f.location(false);
            check(!f.lamps.isRuntimeCurrent() && !f.beam.isRuntimeCurrent(), "old beams cannot follow a jump");
            f.location = f.location(true);
            before = f.scans;
            check(!f.allowed(), "hyperspace rejects lamps without terrain query");
            f.lamps.advance(0f);
            check(f.scans == before, "hyperspace update skips terrain query");
            f.location = f.system;
            check(f.allowed(), "return from hyperspace refreshes");
            before = f.scans;
            f.lamps.reload();
            check(f.allowed() && f.scans == before + 1, "load discards cached proximity");
            f.lamps.on();
            f.installed = false;
            check(!f.lamps.isRuntimeCurrent() && !f.beam.isRuntimeCurrent(), "removed ability cannot render");
            Pond pond = new Pond(f, false);
            pond.activate();
            check(!f.allowed(), "fleet without lamp ability uses direct terrain query");
        }
    }

    private static void check(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }
}
