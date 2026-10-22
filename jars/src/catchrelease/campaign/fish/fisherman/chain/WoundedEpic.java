package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.legendary.EpicHunt;
import catchrelease.campaign.fish.legendary.LegendaryStarAvoidance;
import catchrelease.campaign.fish.legendary.QuestEpicListener;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

// T3: the wounded epic. It circles a planet while buried and hunts at half speed once woken; any hunt that does not
// land it sends it back to the planet, so the quest cannot be lost.
public final class WoundedEpic {

    public static final String PLANTER = "catchrelease_chainT3";

    public static final float CIRCLE_RADIUS = 1000f;
    public static final float AWAKE_SPEED_MULT = 0.5f;
    // past the star's surface, clear of every vanilla corona (the widest reaches about 2,250) with the circle
    public static final float STAR_CLEARANCE = 2500f;

    private WoundedEpic() {
    }

    // The planet is never left without its epic while the hunt is on: a lost one is replaced on arrival or load.
    public static class Arrival implements CurrentLocationChangedListener {

        public static void register() {
            ListenerManagerAPI listeners = Global.getSector().getListenerManager();
            listeners.removeListenerOfClass(Arrival.class);
            if (isHunting()) listeners.addListener(new Arrival(), true);
        }

        @Override
        public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
            if (curr != null && curr == getSystem()) ensureEpic();
        }
    }

    // A catch ends the hunt; a reset, lost minigame or blast sends the epic back to the planet.
    public static class Hunt implements QuestEpicListener {

        public static void register() {
            ListenerManagerAPI listeners = Global.getSector().getListenerManager();
            listeners.removeListenerOfClass(Hunt.class);
            if (isHunting()) listeners.addListener(new Hunt(), true);
        }

        @Override
        public void reportQuestEpicReset(SectorEntityToken mote, String planter) {
            if (PLANTER.equals(planter)) returnToPlanet();
        }

        @Override
        public void reportQuestEpicEscaped(SectorEntityToken mote, String planter) {
            if (PLANTER.equals(planter)) returnToPlanet();
        }

        @Override
        public void reportQuestEpicBlasted(SectorEntityToken mote, String planter) {
            if (PLANTER.equals(planter)) returnToPlanet();
        }

        @Override
        public void reportQuestEpicCaught(SectorEntityToken mote, String planter, FishCatch caught) {
            if (PLANTER.equals(planter)) landed();
        }
    }

    public static void register() {
        Arrival.register();
        Hunt.register();
        if (isHunting()) {
            ensureEpic();
        } else {
            clearEpic();
        }
    }

    public static boolean isHunting() {
        return FishermanChain.isAt(FishermanChain.Stage.T3_HUNT);
    }

    // Keeps the saved system, planet and species while they still qualify, so a declined offer names them again.
    public static boolean prepareOffer() {
        FishermanChain.Saved saved = FishermanChain.get(true);
        if (saved == null) return false;

        StarSystemAPI kept = FishRumors.findSystem(saved.t3SystemId);
        if (isCandidate(kept) && isHost(kept, getPlanet(kept, saved.t3PlanetId))
                && CaptainsFleet.isTarget(FishSpecLoader.getFishSpec(saved.t3SpeciesId), kept)) {
            return true;
        }

        saved.t3SystemId = null;
        saved.t3SystemName = null;
        saved.t3PlanetId = null;
        saved.t3PlanetName = null;
        saved.t3SpeciesId = null;

        StarSystemAPI system = pickSystem();
        if (system == null) return false;

        PlanetAPI planet = pickPlanet(system);
        FishSpec species = CaptainsFleet.pickSpecies(system);
        if (planet == null || species == null) return false;

        saved.t3SystemId = system.getId();
        saved.t3SystemName = system.getNameWithNoType();
        saved.t3PlanetId = planet.getId();
        saved.t3PlanetName = planet.getName();
        saved.t3SpeciesId = species.id;

        return true;
    }

    public static StarSystemAPI getSystem() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null ? null : FishRumors.findSystem(saved.t3SystemId);
    }

    public static PlanetAPI getPlanet() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null ? null : getPlanet(getSystem(), saved.t3PlanetId);
    }

    public static String getSpeciesName() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        FishSpec spec = saved == null ? null : FishSpecLoader.getFishSpec(saved.t3SpeciesId);

        return spec == null ? "" : spec.getDisplayName();
    }

    // The epic goes back to circling its planet, wherever the player is.
    protected static void returnToPlanet() {
        if (!isHunting()) return;

        clearEpic();
        SectorEntityToken mote = spawnEpic();
        PlanetAPI planet = getPlanet();
        if (mote == null || planet == null) return;

        Global.getSector().getCampaignUI().addMessage("The pattern returns to " + planet.getName() + " to recover.",
                Misc.getTextColor(), planet.getName(), null, Misc.getHighlightColor(), null);
    }

    protected static void landed() {
        if (!isHunting()) return;

        clearEpic();
        FishermanChain.setStage(FishermanChain.Stage.T3_CAUGHT);
        register();
    }

    protected static void ensureEpic() {
        StarSystemAPI system = getSystem();
        if (!isHunting() || system == null || !QuestPond.getPlanted(system, PLANTER).isEmpty()) return;

        spawnEpic();
    }

    // Buried, circling the planet, quest-marked so spawners, culls and Lantern Jack leave it alone, and at half the
    // awake speed once woken.
    protected static SectorEntityToken spawnEpic() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        StarSystemAPI system = getSystem();
        PlanetAPI planet = getPlanet();
        if (saved == null || system == null || planet == null || saved.t3SpeciesId == null) return null;

        Vector2f at = LegendaryStarAvoidance.place(system, MathUtils.getPointOnCircumference(planet.getLocation(),
                CIRCLE_RADIUS, MathUtils.getRandomNumberInRange(0f, 360f)), 0f);
        SectorEntityToken mote = system.addCustomEntity(Misc.genUID(), null, FishConstants.BURIED_ENTITY_ID, null,
                new BuriedMoteEntityPlugin.Params(saved.t3SpeciesId));
        mote.setLocation(at.x, at.y);

        BuriedMoteEntityPlugin.setCircle(mote, planet, CIRCLE_RADIUS);
        mote.getMemoryWithoutUpdate().set(EpicHunt.AWAKE_SPEED_KEY, AWAKE_SPEED_MULT);
        QuestPond.markPlanted(mote, PLANTER);

        return mote;
    }

    protected static void clearEpic() {
        StarSystemAPI system = getSystem();
        if (system == null) return;

        for (SectorEntityToken mote : QuestPond.getPlanted(system, PLANTER)) Misc.fadeAndExpire(mote, 1f);
    }

    // The nearest qualifying system, at any distance.
    protected static StarSystemAPI pickSystem() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Vector2f from = player == null ? new Vector2f() : player.getLocationInHyperspace();

        StarSystemAPI nearest = null;
        float best = Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isCandidate(system) || pickPlanet(system) == null || CaptainsFleet.pickSpecies(system) == null) {
                continue;
            }

            float distance = Misc.getDistanceLY(from, system.getLocation());
            if (distance < best) {
                best = distance;
                nearest = system;
            }
        }

        return nearest;
    }

    // No markets or colonies, where Breach Lights would bring patrols down on the player, and nothing unsafe.
    protected static boolean isCandidate(StarSystemAPI system) {
        if (system == null || system.getId() == null) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER) || system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;
        if (system.hasTag(Tags.THEME_UNSAFE)) return false;
        if (system.getHyperspaceAnchor() == null || OuterReaches.isPopulated(system)) return false;

        return !FishRumors.isOccupied(system.getId());
    }

    protected static PlanetAPI pickPlanet(StarSystemAPI system) {
        for (PlanetAPI planet : system.getPlanets()) {
            if (isHost(system, planet)) return planet;
        }
        return null;
    }

    // A planet whose circle stays clear of every star and its corona.
    protected static boolean isHost(StarSystemAPI system, PlanetAPI planet) {
        if (system == null || planet == null || planet.isStar() || planet.isExpired()) return false;

        for (PlanetAPI other : system.getPlanets()) {
            if (!other.isStar()) continue;

            float gap = Misc.getDistance(planet, other) - other.getRadius() - CIRCLE_RADIUS;
            if (gap < STAR_CLEARANCE) return false;
        }
        return true;
    }

    protected static PlanetAPI getPlanet(StarSystemAPI system, String planetId) {
        if (system == null || planetId == null) return null;

        for (PlanetAPI planet : system.getPlanets()) {
            if (planetId.equals(planet.getId())) return planet;
        }
        return null;
    }
}
