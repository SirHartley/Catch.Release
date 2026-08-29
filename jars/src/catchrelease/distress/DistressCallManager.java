package catchrelease.distress;

import catchrelease.distress.vanilla.NearbyEventsBridge;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.events.nearby.DistressCallNormalAssignmentAI;
import com.fs.starfarer.api.impl.campaign.events.nearby.NearbyEventsEvent;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.DistressCallIntel;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class DistressCallManager implements EveryFrameScript, RouteFleetSpawner {

    public static class SpawnData {

        public String instanceId;
        public String specId;
        public StarSystemAPI system;
        public SectorEntityToken jumpPoint;
    }

    private static class Candidate {

        final DistressCallSpec spec;
        final StarSystemAPI system;

        Candidate(DistressCallSpec spec, StarSystemAPI system) {
            this.spec = spec;
            this.system = system;
        }
    }

    protected List<DistressCallInstance> active = new ArrayList<>();
    protected Map<String, Float> cooldowns = new LinkedHashMap<>();
    protected Random random = new Random();

    protected transient NearbyEventsBridge vanilla;
    protected transient boolean bridgeFailureLogged;
    protected transient boolean sawElapsed;
    protected transient List<StarSystemAPI> cachedCandidates = new ArrayList<>();
    protected transient Set<IntelInfoPlugin> seenDistressIntel = identitySet();

    public static DistressCallManager getInstanceOrRegister() {
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof DistressCallManager) return (DistressCallManager) script;
        }

        DistressCallManager manager = new DistressCallManager();
        Global.getSector().addScript(manager);

        return manager;
    }

    Object readResolve() {
        if (active == null) active = new ArrayList<>();
        if (cooldowns == null) cooldowns = new LinkedHashMap<>();
        if (random == null) random = new Random();

        vanilla = null;
        bridgeFailureLogged = false;
        sawElapsed = false;
        cachedCandidates = new ArrayList<>();
        seenDistressIntel = identitySet();

        return this;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().isInFastAdvance()) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        advanceCooldowns(days);
        maintainInstances(days);

        if (!bindVanilla()) return;

        List<IntelInfoPlugin> currentIntel = getActiveDistressIntel();
        boolean frameworkActive = !active.isEmpty();

        if (frameworkActive) {
            reserveEligibleSystems(DistressCallSettings.ACTIVE_RESERVATION_DAYS);
            rememberIntel(currentIntel);
            sawElapsed = vanilla.isCheckElapsed();
            return;
        }

        if (hasVanillaIntel(currentIntel)) {
            rememberIntel(currentIntel);
            sawElapsed = vanilla.isCheckElapsed();
            return;
        }

        boolean elapsed = vanilla.isCheckElapsed();
        // Vanilla reserves every candidate before its probability roll, so retain the pre-check pool.
        if (!elapsed) cachedCandidates = getEligibleSystems(true);

        if (elapsed && !sawElapsed && !hasNewVanillaIntel(currentIntel)) {
            trySpawnFrom(cachedCandidates);
        }

        sawElapsed = elapsed;
        rememberIntel(currentIntel);
    }

    private boolean bindVanilla() {
        if (vanilla != null) return true;

        vanilla = NearbyEventsBridge.bind();
        if (vanilla == null && !bridgeFailureLogged) {
            DistressCallFramework.logError(
                    "Distress call framework disabled: vanilla NearbyEventsEvent was unavailable",
                    new IllegalStateException("No compatible NearbyEventsEvent"));
            bridgeFailureLogged = true;
        }

        return vanilla != null;
    }

    private void advanceCooldowns(float days) {
        Iterator<Map.Entry<String, Float>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Float> entry = iterator.next();
            float remaining = entry.getValue() - days;
            if (remaining <= 0f) iterator.remove();
            else entry.setValue(remaining);
        }
    }

    private void maintainInstances(float days) {
        Iterator<DistressCallInstance> iterator = active.iterator();
        while (iterator.hasNext()) {
            DistressCallInstance instance = iterator.next();
            CampaignFleetAPI fleet = instance.fleet;
            if (!instance.spawned) {
                instance.pendingDays += days;
                if (instance.pendingDays <= 5f) continue;
            }

            boolean expired = fleet == null || fleet.isExpired() || !fleet.isAlive()
                    || !fleet.getMemoryWithoutUpdate().contains("$distress");

            if (!instance.resolved && expired) {
                DistressCallProvider provider = provider(instance);
                if (provider != null) provider.onExpired(instance, fleet);
                instance.resolved = true;
                clearFrameworkState(fleet);
            }

            if (instance.resolved) iterator.remove();
        }
    }

    public void resolve(CampaignFleetAPI fleet) {
        if (fleet == null) return;

        for (DistressCallInstance instance : active) {
            if (instance.fleet != fleet || instance.resolved) continue;

            instance.resolved = true;
            DistressCallNormalAssignmentAI.undistress(fleet);
            clearFrameworkState(fleet);

            DistressCallProvider provider = provider(instance);
            if (provider != null) provider.onResolved(instance, fleet);

            return;
        }
    }

    private DistressCallProvider provider(DistressCallInstance instance) {
        DistressCallSpec spec = instance == null ? null : instance.getSpec();
        return spec == null ? null : DistressCallFramework.getProvider(spec.providerId);
    }

    private void trySpawnFrom(List<StarSystemAPI> systems) {
        if (systems == null || systems.isEmpty()) return;
        if (active.size() >= DistressCallSettings.GLOBAL_MAX_ACTIVE) return;

        WeightedRandomPicker<Candidate> picker = new WeightedRandomPicker<>(random);
        for (DistressCallSpec spec : DistressCallRegistry.all()) {
            DistressCallProvider provider = DistressCallFramework.getProvider(spec.providerId);
            if (provider == null || cooldowns.containsKey(spec.id)) continue;
            if (countActive(spec.id) >= spec.maxActive) continue;

            for (StarSystemAPI system : systems) {
                if (!isSystemEligible(system, false) || !provider.isEligible(spec, system)) continue;
                picker.add(new Candidate(spec, system), spec.weight);
            }
        }

        Candidate candidate = picker.pick();
        if (candidate == null || random.nextFloat() > candidate.spec.probability) return;

        startRoute(candidate.spec, candidate.system);
    }

    private int countActive(String specId) {
        int count = 0;
        for (DistressCallInstance instance : active) {
            if (!instance.resolved && specId.equals(instance.specId)) count++;
        }
        return count;
    }

    public StarSystemAPI spawnForTesting(String specId) {
        if (!bindVanilla() || hasActiveCall()) return null;

        DistressCallSpec spec = DistressCallRegistry.get(specId);
        DistressCallProvider provider = spec == null ? null
                : DistressCallFramework.getProvider(spec.providerId);
        if (spec == null || provider == null) return null;

        List<StarSystemAPI> eligible = new ArrayList<>();
        for (StarSystemAPI system : getEligibleSystems(true)) {
            if (provider.isEligible(spec, system)) eligible.add(system);
        }
        if (eligible.isEmpty()) return null;

        StarSystemAPI system = eligible.get(random.nextInt(eligible.size()));
        return startRoute(spec, system) == null ? null : system;
    }

    public StarSystemAPI claimVanillaSystemForTesting() {
        if (!bindVanilla() || hasActiveCall()) return null;

        List<StarSystemAPI> eligible = getEligibleSystems(true);
        if (eligible.isEmpty()) return null;

        StarSystemAPI system = eligible.get(random.nextInt(eligible.size()));
        vanilla.reserve(system.getId(), DistressCallSettings.REPEAT_RESERVATION_DAYS);
        return system;
    }

    private boolean hasActiveCall() {
        return !active.isEmpty() || !getActiveDistressIntel().isEmpty();
    }

    private DistressCallInstance startRoute(DistressCallSpec spec, StarSystemAPI system) {
        SectorEntityToken jumpPoint = Misc.getDistressJumpPoint(system);
        if (jumpPoint == null) return null;

        DistressCallInstance instance = new DistressCallInstance(spec.id, system);
        active.add(instance);
        cooldowns.put(spec.id, spec.cooldownDays);
        vanilla.reserve(system.getId(), DistressCallSettings.REPEAT_RESERVATION_DAYS);

        SpawnData data = new SpawnData();
        data.instanceId = instance.id;
        data.specId = spec.id;
        data.system = system;
        data.jumpPoint = jumpPoint;

        OptionalFleetData optional = new OptionalFleetData();
        optional.factionId = spec.factionId;

        RouteData route = RouteManager.getInstance().addRoute(
                DistressCallSettings.ROUTE_SOURCE_ID, null, Misc.genRandomSeed(), optional, this, data);
        route.addSegment(new RouteSegment(30f + random.nextFloat() * 10f, jumpPoint));

        return instance;
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        if (!(route.getCustom() instanceof SpawnData)) return null;

        SpawnData data = (SpawnData) route.getCustom();
        DistressCallSpec spec = DistressCallRegistry.get(data.specId);
        DistressCallInstance instance = find(data.instanceId);
        DistressCallProvider provider = spec == null ? null
                : DistressCallFramework.getProvider(spec.providerId);
        if (spec == null || instance == null || provider == null || data.system == null
                || data.jumpPoint == null) {
            if (instance != null) {
                instance.spawned = true;
                instance.resolved = true;
            }
            return null;
        }

        instance.spawned = true;

        float points = spec.minFP + random.nextFloat() * (spec.maxFP - spec.minFP);
        FleetParamsV3 params = new FleetParamsV3(data.system.getLocation(), spec.factionId, null,
                spec.fleetType, points * 0.6f, points * 0.25f, points * 0.15f,
                0f, 0f, 0f, 0f);
        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            instance.resolved = true;
            return null;
        }

        data.system.addEntity(fleet);
        fleet.setLocation(data.jumpPoint.getLocation().x, data.jumpPoint.getLocation().y);
        fleet.removeAbility(Abilities.EMERGENCY_BURN);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_TYPE, spec.fleetType);
        Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.ENTITY_MISSION_IMPORTANT,
                "distress", true, 1000f);
        fleet.getMemoryWithoutUpdate().set("$ne_eventRef", vanilla.getEvent());
        fleet.getMemoryWithoutUpdate().set("$distress", true);
        fleet.getMemoryWithoutUpdate().set(DistressCallSettings.ENTITY_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(DistressCallSettings.INSTANCE_REF, instance);
        fleet.getMemoryWithoutUpdate().set(DistressCallSettings.EVENT_ID, spec.id);

        instance.fleet = fleet;
        if (!provider.onFleetSpawned(instance, fleet)) {
            instance.resolved = true;
            DistressCallNormalAssignmentAI.undistress(fleet);
            clearFrameworkState(fleet);
            Misc.fadeAndExpire(fleet);
            return null;
        }

        SectorEntityToken anchor = provider.getFleetAnchor(instance, fleet, data.jumpPoint);
        if (anchor == null || anchor.isExpired() || anchor.getContainingLocation() != data.system) {
            anchor = data.jumpPoint;
        }

        float offset = anchor == data.jumpPoint ? 400f + random.nextFloat() * 200f
                : anchor.getRadius() + 400f;
        fleet.setLocation(anchor.getLocation().x + offset, anchor.getLocation().y);
        fleet.addScript(new DistressCallNormalAssignmentAI(fleet, data.system, anchor));

        DistressCallIntel intel = new DistressCallIntel(data.system);
        String intelText = provider.getIntelText(instance, fleet);
        if (intelText != null && !intelText.isEmpty()) intel.setText(intelText);
        instance.intel = intel;
        Global.getSector().getIntelManager().addIntel(intel);

        DistressCallFramework.log("Spawned " + spec.id + " in " + data.system.getName());

        return fleet;
    }

    private DistressCallInstance find(String id) {
        for (DistressCallInstance instance : active) {
            if (instance.id.equals(id)) return instance;
        }
        return null;
    }

    private void clearFrameworkState(CampaignFleetAPI fleet) {
        if (fleet == null) return;

        fleet.getMemoryWithoutUpdate().unset(DistressCallSettings.ENTITY_FLAG);
        fleet.getMemoryWithoutUpdate().unset(DistressCallSettings.INSTANCE_REF);
        fleet.getMemoryWithoutUpdate().unset(DistressCallSettings.EVENT_ID);
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteData route) {
        route.expire();
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteData route) {
        return false;
    }

    @Override
    public boolean shouldRepeat(RouteData route) {
        return false;
    }

    private List<StarSystemAPI> getEligibleSystems(boolean respectReservations) {
        List<StarSystemAPI> result = new ArrayList<>();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !player.isInHyperspace() || player.isInHyperspaceTransition()) return result;

        for (StarSystemAPI system : Misc.getNearbyStarSystems(player,
                Global.getSettings().getFloat("distressCallEventRangeLY"))) {
            if (isSystemEligible(system, respectReservations)) result.add(system);
        }

        return result;
    }

    private boolean isSystemEligible(StarSystemAPI system, boolean respectReservations) {
        if (system == null) return false;
        if (respectReservations && vanilla.isReserved(system.getId())) return false;
        if (system.hasPulsar() || system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                || system.hasTag(Tags.THEME_HIDDEN)) return false;
        if (system.getDaysSinceLastPlayerVisit() < NearbyEventsEvent.DISTRESS_MIN_SINCE_PLAYER_IN_SYSTEM) {
            return false;
        }

        boolean validTheme = false;
        for (String tag : system.getTags()) {
            if (NearbyEventsEvent.distressCallAllowedThemes.contains(tag)) {
                validTheme = true;
                break;
            }
        }
        if (!validTheme || !Misc.getMarketsInLocation(system).isEmpty()) return false;

        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (fleet.getFaction().isHostileTo("independent")) return false;
        }

        return Misc.getDistressJumpPoint(system) != null;
    }

    private void reserveEligibleSystems(float days) {
        for (StarSystemAPI system : getEligibleSystems(false)) {
            vanilla.reserve(system.getId(), days);
        }
    }

    private List<IntelInfoPlugin> getActiveDistressIntel() {
        List<IntelInfoPlugin> result = new ArrayList<>();
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(DistressCallIntel.class)) {
            if (!intel.isEnding() && !intel.isEnded()) result.add(intel);
        }
        return result;
    }

    private boolean hasVanillaIntel(List<IntelInfoPlugin> current) {
        for (IntelInfoPlugin intel : current) {
            if (!owns(intel)) return true;
        }
        return false;
    }

    private boolean hasNewVanillaIntel(List<IntelInfoPlugin> current) {
        for (IntelInfoPlugin intel : current) {
            if (!seenDistressIntel.contains(intel) && !owns(intel)) return true;
        }
        return false;
    }

    private boolean owns(IntelInfoPlugin intel) {
        for (DistressCallInstance instance : active) {
            if (instance.intel == intel) return true;
        }
        return false;
    }

    private void rememberIntel(List<IntelInfoPlugin> current) {
        seenDistressIntel.clear();
        seenDistressIntel.addAll(current);
    }

    private static Set<IntelInfoPlugin> identitySet() {
        return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
