package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.legendary.EpicHunt;
import catchrelease.campaign.fish.legendary.LegendaryStarAvoidance;
import catchrelease.campaign.fish.legendary.QuestEpicListener;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

// T3: the wounded epic. It circles a planet while buried and hunts at half speed once woken; any hunt that does not
// land it sends it back to the planet, so the quest cannot be lost.
public final class WoundedEpic {

    public static final String PLANTER = "catchrelease_chainT3";

    public static final float CIRCLE_RADIUS = 1000f;
    public static final float AWAKE_SPEED_MULT = 0.5f;
    // paid in place of the range data when every epic is already caught or charted
    public static final int RANGE_DATA_CREDITS = 10000;
    // past the star's surface, clear of every vanilla corona (the widest reaches about 2,250) with the circle
    public static final float STAR_CLEARANCE = 2500f;

    private WoundedEpic() {
    }

    // The planet is never left without its epic while the hunt is on: a lost one is replaced on arrival or load, and
    // wild ones of its species are cleared.
    public static class Arrival implements CurrentLocationChangedListener {

        public static void register() {
            ListenerManagerAPI listeners = Global.getSector().getListenerManager();
            listeners.removeListenerOfClass(Arrival.class);
            if (isHunting()) listeners.addListener(new Arrival(), true);
        }

        @Override
        public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
            if (curr != null && curr == getSystem()) {
                clearWild();
                ensureEpic();
            }
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

    // Putting the Breach Lights out calls a woken epic off: it goes back to the planet. Vanilla reports the player's
    // ability deactivation (BaseAbilityPlugin.deactivate), so the lamp state is never polled. A location change turns
    // the lights off too, after the player has left the epic's system, where EpicHunt's own reset covers it.
    public static class LampsOff extends BaseCampaignEventListener {

        public LampsOff() {
            super(false);
        }

        public static void register() {
            for (CampaignEventListener listener : Global.getSector().getAllListeners()) {
                if (listener instanceof LampsOff) Global.getSector().removeListener(listener);
            }
            if (isHunting()) Global.getSector().addTransientListener(new LampsOff());
        }

        @Override
        public void reportPlayerDeactivatedAbility(AbilityPlugin ability, Object param) {
            if (ability != null && SearchlightAbilityPlugin.ABILITY_ID.equals(ability.getId()) && isAwakeHere()) {
                returnToPlanet();
            }
        }
    }

    // One intel for all of T3; its title, lines and map target follow the stage.
    public static class WoundedIntel extends BaseIntelPlugin {

        protected static boolean isReturning() {
            return FishermanChain.isAt(FishermanChain.Stage.T3_CAUGHT)
                    || FishermanChain.isAt(FishermanChain.Stage.T3_BUILT);
        }

        protected static boolean isBuilding() {
            return FishermanChain.isAt(FishermanChain.Stage.T3_BUILD);
        }

        @Override
        public boolean shouldRemoveIntel() {
            if (isEnded()) return true;
            if (!isHunting() && !isReturning() && !isBuilding()) endImmediately();

            return isEnded();
        }

        @Override
        public String getName() {
            return switch (FishermanChain.getStage()) {
                case T3_CAUGHT -> "An Epic tale: report to the Fisherman";
                case T3_BUILD -> "Build the " + getLampShipName();
                case T3_BUILT -> getLampShipName() + ": report to the Fisherman";
                default -> "An Epic tale: Chapter 2";
            };
        }

        @Override
        public String getSmallDescriptionTitle() {
            return getName();
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);

            addBulletPoints(info, mode);
        }

        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color text = getBulletColorForMode(mode);
            Color h = Misc.getHighlightColor();
            float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);
            if (isReturning()) {
                info.addPara("Return to the nearest fishing boat", text, pad);
            } else if (isBuilding()) {
                info.addPara("Learn the blueprint and have a %s built", pad, text, h, getLampShipName());
            } else {
                info.addPara("Find the %s circling %s in %s", pad, text, h, getSpeciesName(), getPlanetName(),
                        getSystemName()).setHighlightColors(FishRarity.EPIC.color, h, h);
                info.addPara("Use the %s to see it while the Breach Lights are off", 0f, text, h,
                        Tackle.NEEDLE_SENSOR.name);
                info.addPara("Harpoon and land it.", 0f);
            }
            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            float opad = 10f;
            Color h = Misc.getHighlightColor();
            FactionAPI faction = Global.getSector().getFaction(FishermanConstants.FACTION);

            info.addImages(width, 128, opad, opad, FishermanIdentity.getPortrait(0f), faction.getCrest());

            if (isBuilding()) {
                info.addPara("The Fisherman gave you the blueprint for the %s. It brings a second Lens Array slot "
                        + "and the %s while it is in your fleet.", opad, h,
                        getLampShipName(), Tackle.FANNED_ARRAY.name);
                addBulletPoints(info, ListInfoMode.IN_DESC);
                return;
            }

            if (FishermanChain.isAt(FishermanChain.Stage.T3_BUILT)) {
                info.addPara("Your %s is in the fleet. The Fisherman will want to see it.", opad, h,
                        getLampShipName());
                addBulletPoints(info, ListInfoMode.IN_DESC);
                FishIntelMapButton.addSetAutopilot(info, width, FishingIntro.getNearestBoat());
                return;
            }

            if (isReturning()) {
                info.addPara("You landed the wounded %s. The Fisherman will want to hear about it.", opad,
                        FishRarity.EPIC.color, getSpeciesName());
                addBulletPoints(info, ListInfoMode.IN_DESC);
                FishIntelMapButton.addSetAutopilot(info, width, FishingIntro.getNearestBoat());
                return;
            }

            info.addPara("A wounded %s is circling %s in %s, about %s out. It swims slower than it should once "
                    + "woken.", opad, new Color[]{FishRarity.EPIC.color, h, h, h}, getSpeciesName(),
                    getPlanetName(), getSystemName(), Misc.getWithDGS(CIRCLE_RADIUS) + " units");
            addBulletPoints(info, ListInfoMode.IN_DESC);
            FishIntelMapButton.addPlotRoute(info, width, getMapLocation(null));
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            if (isReturning() && FishIntelMapButton.handleSetAutopilot(buttonId, FishingIntro.getNearestBoat())) {
                return;
            }
            if (FishIntelMapButton.handlePlotRoute(buttonId, getMapLocation(null))) return;

            super.buttonPressConfirmed(buttonId, ui);
        }

        @Override
        public String getIcon() {
            return FishermanIdentity.getPortrait(0f);
        }

        @Override
        public String getSortString() {
            return getSortStringNewestFirst();
        }

        @Override
        public FactionAPI getFactionForUIColors() {
            return Global.getSector().getPlayerFaction();
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_MISSIONS);
            tags.add(Tags.INTEL_ACCEPTED);
            tags.add(FishermanConstants.FACTION);

            return tags;
        }

        @Override
        public SectorEntityToken getMapLocation(SectorMapAPI map) {
            if (!isHunting()) return null;

            PlanetAPI planet = getPlanet();
            if (planet != null) return planet;

            StarSystemAPI system = getSystem();
            return system == null ? null : system.getHyperspaceAnchor();
        }

        protected static String getSystemName() {
            FishermanChain.Saved saved = FishermanChain.get(false);

            return saved == null || saved.t3SystemName == null ? "the system" : "the " + saved.t3SystemName;
        }

        protected static String getPlanetName() {
            FishermanChain.Saved saved = FishermanChain.get(false);

            return saved == null || saved.t3PlanetName == null ? "the planet" : saved.t3PlanetName;
        }
    }

    public static void register() {
        Arrival.register();
        Hunt.register();
        LampsOff.register();
        if (isHunting()) {
            clearWild();
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
                && isTarget(FishSpecLoader.getFishSpec(saved.t3SpeciesId), kept)) {
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
        FishSpec species = pickSpecies(system);
        if (planet == null || species == null) return false;

        saved.t3SystemId = system.getId();
        saved.t3SystemName = system.getNameWithLowercaseType();
        saved.t3PlanetId = planet.getId();
        saved.t3PlanetName = planet.getName();
        saved.t3SpeciesId = species.id;

        return true;
    }

    public static boolean start(TextPanelAPI text) {
        if (!FishermanChain.isAt(FishermanChain.Stage.T2_SENSOR_BOUGHT)) return false;

        FishermanChain.Saved saved = FishermanChain.get(false);
        StarSystemAPI system = getSystem();
        if (saved == null || !isCandidate(system) || !isHost(system, getPlanet())
                || !isTarget(FishSpecLoader.getFishSpec(saved.t3SpeciesId), system)) {
            return false;
        }

        FishermanChain.setStage(FishermanChain.Stage.T3_HUNT);
        register();
        FishIntelNotifications.queue(new WoundedIntel(), text);

        return true;
    }

    // The lamp ship's blueprint and range data on an epic the player has neither caught nor charted; with none left,
    // RANGE_DATA_CREDITS instead.
    public static boolean complete(TextPanelAPI text) {
        if (!FishermanChain.isAt(FishermanChain.Stage.T3_CAUGHT)) return false;

        List<FishReward.Receipt> receipts = new ArrayList<>();
        receipts.add(FishReward.shipBlueprint(FishingShips.Ship.LAMP.hullId).grantWithReceipt());

        FishSpec epic = pickRangeData(new Random());
        receipts.add(epic == null ? FishReward.credits(RANGE_DATA_CREDITS).grantWithReceipt()
                : FishReward.locationData(epic.id, RANGE_DATA_CREDITS).grantWithReceipt());

        FishermanChain.setStage(FishermanChain.Stage.T3_BUILD);
        register();
        FishReward.showReceipts(text, receipts);
        updateIntel();
        checkBuilt();

        return true;
    }

    // The follow-up ends once a lamp ship joins the fleet; FishingShips.reconcile runs this after every presence
    // check. Custom production delivers to storage, so a built ship counts when it is taken out of storage.
    public static void checkBuilt() {
        if (!FishermanChain.isAt(FishermanChain.Stage.T3_BUILD)
                || !FishingShips.hasSecondSlot(FishingShips.Ship.LAMP.rig)) return;

        FishermanChain.setStage(FishermanChain.Stage.T3_BUILT);
        updateIntel();
    }

    // The closing conversation ends T3 and starts T4, which it introduces.
    public static void finish(TextPanelAPI text) {
        if (FishermanChain.isAt(FishermanChain.Stage.T3_BUILT)) LegendaryHunt.start(text);
    }

    public static String getLampShipName() {
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(FishingShips.Ship.LAMP.hullId);

        return spec == null ? "Breach Light Trawler" : spec.getHullName();
    }

    public static String getLampShipFullName() {
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(FishingShips.Ship.LAMP.hullId);

        return spec == null ? "Breach Light Trawler" : spec.getNameWithDesignationWithDashClass();
    }

    protected static FishSpec pickRangeData(Random random) {
        List<FishSpec> unknown = new ArrayList<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.rarity != FishRarity.EPIC || spec.spawnWeight <= 0f || !spec.hasHabitat()) {
                continue;
            }
            if (!FishLog.isCaught(spec.id) && !FishLog.isLocationDataUnlocked(spec.id)) unknown.add(spec);
        }

        return unknown.isEmpty() ? null : unknown.get(random.nextInt(unknown.size()));
    }

    public static void updateIntel() {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(WoundedIntel.class)) {
            FishIntelNotifications.update((WoundedIntel) intel, null);
        }
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

    // A woken epic in the player's location, not on the line or in a minigame.
    protected static boolean isAwakeHere() {
        StarSystemAPI system = getSystem();
        if (!isHunting() || system == null || Global.getSector().getCurrentLocation() != system) return false;

        for (SectorEntityToken mote : QuestPond.getPlanted(system, PLANTER)) {
            if (mote.getCustomPlugin() instanceof FishEntityPlugin fish && EpicHunt.isActive(fish) && !fish.isHeld()) {
                return true;
            }
        }
        return false;
    }

    protected static void landed() {
        if (!isHunting()) return;

        clearEpic();
        FishermanChain.setStage(FishermanChain.Stage.T3_CAUGHT);
        register();
        updateIntel();
    }

    // While the hunt is on, the wounded epic is the only one of its species in its system. A wild one would wake at
    // full speed and read as the quest's, its sensor ripples identical. BuriedMoteSpawner asks before each spawn.
    public static boolean isReserved(LocationAPI location, String fishId) {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return isHunting() && saved != null && fishId != null && fishId.equals(saved.t3SpeciesId)
                && location != null && location == getSystem();
    }

    protected static void clearWild() {
        StarSystemAPI system = getSystem();
        if (system == null) return;

        for (SectorEntityToken mote : new ArrayList<>(system.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG))) {
            if (mote.isExpired() || QuestPond.isQuestMote(mote)
                    || !(mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin buried)
                    || buried.getFishSpec() == null || !isReserved(system, buried.getFishSpec().id)) continue;

            // removeEntity does not expire it, and an epic's trail renderer retires on expiry
            mote.setExpired(true);
            system.removeEntity(mote);
        }
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
            if (!isCandidate(system) || pickPlanet(system) == null || pickSpecies(system) == null) {
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

    // No markets or colonies, where Breach Lights would bring patrols down on the player, nothing unsafe and no pulsar.
    protected static boolean isCandidate(StarSystemAPI system) {
        if (system == null || system.getId() == null) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER) || system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;
        if (system.hasTag(Tags.THEME_UNSAFE)) return false;
        if (system.getHyperspaceAnchor() == null || OuterReaches.isPopulated(system)) return false;
        if (FishRumors.isOccupied(system.getId())) return false;
        // never the captain's system: T3 is a new hunt
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved != null && system.getId().equals(saved.t2SystemId)) return false;

        // a pulsar's beams reach far past any clearance the circle keeps
        for (PlanetAPI star : system.getPlanets()) {
            if (star.isStar() && star.getSpec() != null && star.getSpec().isPulsar()) return false;
        }
        return true;
    }

    protected static FishSpec pickSpecies(StarSystemAPI system) {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (isTarget(spec, system)) picker.add(spec, spec.spawnWeight);
        }

        return picker.pick();
    }

    // An epic CaptainsFleet would hunt there, but never the captain's own species.
    protected static boolean isTarget(FishSpec spec, StarSystemAPI system) {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (spec == null || saved != null && spec.id.equals(saved.t2SpeciesId)) return false;

        return CaptainsFleet.isTarget(spec, system);
    }

    protected static PlanetAPI pickPlanet(StarSystemAPI system) {
        for (PlanetAPI planet : system.getPlanets()) {
            if (isHost(system, planet)) return planet;
        }
        return null;
    }

    // A planet whose circle stays clear of every star and its corona as both orbit. Measured from the planet's orbit
    // focus, the closest a star that also circles it can come is the difference of their orbit radii; a star at the
    // focus is the planet's own orbit radius away. Moons are refused: their path round a moving planet has no such
    // bound. The epic does not steer round stars, so the circle itself has to stay clear.
    protected static boolean isHost(StarSystemAPI system, PlanetAPI planet) {
        if (system == null || planet == null || planet.isStar() || planet.isExpired()) return false;

        SectorEntityToken focus = planet.getOrbitFocus() != null ? planet.getOrbitFocus() : system.getCenter();
        if (focus == null || focus instanceof PlanetAPI parent && !parent.isStar()) return false;

        float orbit = Misc.getDistance(focus.getLocation(), planet.getLocation());
        for (PlanetAPI star : system.getPlanets()) {
            if (!star.isStar()) continue;

            float closest = Math.abs(orbit - Misc.getDistance(focus.getLocation(), star.getLocation()));
            if (closest - star.getRadius() - CIRCLE_RADIUS < STAR_CLEARANCE) return false;
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
