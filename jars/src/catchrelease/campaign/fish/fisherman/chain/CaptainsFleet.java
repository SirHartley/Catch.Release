package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.FishRewardRoller;
import catchrelease.campaign.fish.legendary.EpicHunt;
import catchrelease.campaign.fish.legendary.LegendaryStarAvoidance;
import catchrelease.campaign.fish.legendary.QuestEpicListener;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
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

// T2: the captain's fleet. A captain the Fisherman knows is hunting an epic in an empty system; the player's lamps
// find it for him.
public final class CaptainsFleet {

    public static final String CAPTAIN_FLAG = "$catchrelease_t2Captain";
    public static final String IMPORTANT_REASON = "catchrelease_t2";

    public static final String PORTRAIT = "graphics/portraits/portrait30.png";
    public static final String FLEET_NAME = "Hunting Fleet";

    public static final float FLEET_POINTS = 45f;
    public static final float HOLD_DAYS = 100000f;
    // where the fleet waits, past the star's surface; inside the lamp range, so the player starts there
    public static final float HOLD_DISTANCE = 2500f;

    public static final String PLANTER = "catchrelease_chainT2";
    public static final float LAMP_SECONDS = 15f;
    // measured from the star's surface, not its centre
    public static final float LAMP_RANGE = 6000f;
    // how far ahead of the sweep the epic is placed, so the beam reaches it rather than starting on top of it
    public static final float LEAD_SECONDS = 2f;
    public static final float VANISH_GRACE = 5f;

    public static final int RANGE_DATA = 3;

    private CaptainsFleet() {
    }

    // Entering the system brings back a lost fleet: the player only ever finds it on arrival, so a replacement is
    // never seen appearing. Leaving it before the epic wakes re-arms the lamp count; an awake epic is left to
    // EpicHunt.DepartureCleanup, whose reset ends the hunt.
    public static class Movement implements CurrentLocationChangedListener {

        public static void register() {
            ListenerManagerAPI listeners = Global.getSector().getListenerManager();
            listeners.removeListenerOfClass(Movement.class);
            if (isCaptainWaiting()) listeners.addListener(new Movement(), true);
        }

        @Override
        public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
            StarSystemAPI system = getSystem();
            if (system == null) return;

            if (prev == system && prev != curr && FishermanChain.isAt(FishermanChain.Stage.T2_HUNT)
                    && !hasAwakeEpic(system)) {
                rearm();
            }
            if (curr == system) ensureFleet();
        }
    }

    // The captain's epic hunt ends at the first report for it; reports for an epic already given up are ignored.
    // LAMPS is accepted too: the vanish check can re-arm while a minigame on the epic is about to report.
    public static class Hunt implements QuestEpicListener {

        public static void register() {
            ListenerManagerAPI listeners = Global.getSector().getListenerManager();
            listeners.removeListenerOfClass(Hunt.class);
            if (isSearching()) listeners.addListener(new Hunt(), true);
        }

        @Override
        public void reportQuestEpicReset(SectorEntityToken mote, String planter) {
            if (PLANTER.equals(planter)) resolve(false);
        }

        @Override
        public void reportQuestEpicEscaped(SectorEntityToken mote, String planter) {
            if (PLANTER.equals(planter)) resolve(false);
        }

        @Override
        public void reportQuestEpicBlasted(SectorEntityToken mote, String planter) {
            if (PLANTER.equals(planter)) resolve(false);
        }

        @Override
        public void reportQuestEpicCaught(SectorEntityToken mote, String planter, FishCatch caught) {
            if (PLANTER.equals(planter)) resolve(true);
        }
    }

    // Continuous lamp time near the star while the captain waits for his epic. Advanced from the player's
    // SearchlightAbilityPlugin.advance: no callback reports lamp time, and the ability already runs every unpaused
    // frame. Transient, so a load or any break in the conditions restarts the count.
    public static class LampTimer {

        protected float seconds;
        protected float missing;

        public void advance(SearchlightAbilityPlugin lamps, float amount) {
            if (FishermanChain.isAt(FishermanChain.Stage.T2_HUNT)) {
                seconds = 0f;
                checkVanished(amount);
                return;
            }
            missing = 0f;

            if (!FishermanChain.isAt(FishermanChain.Stage.T2_LAMPS) || !isCounting(lamps)) {
                seconds = 0f;
                return;
            }

            seconds += amount;
            if (seconds < LAMP_SECONDS) return;

            seconds = 0f;
            Searchlight beam = lamps.getFirstLitBeam();
            if (beam != null) placeEpic(beam.getPointAhead(LEAD_SECONDS));
        }

        // A buried epic a drone surfaced without waking it fades at the end of its swim with no report. After
        // VANISH_GRACE seconds with no mote in the system the count starts again; the grace covers the frames
        // between a hook and the minigame that will report the result.
        protected void checkVanished(float amount) {
            StarSystemAPI system = getSystem();
            if (system == null || Global.getSector().getCurrentLocation() != system || hasEpic(system)) {
                missing = 0f;
                return;
            }

            missing += amount;
            if (missing < VANISH_GRACE) return;

            missing = 0f;
            rearm();
        }

        protected boolean isCounting(SearchlightAbilityPlugin lamps) {
            CampaignFleetAPI fleet = lamps.getFleet();
            StarSystemAPI system = getSystem();
            if (fleet == null || system == null || fleet.getContainingLocation() != system) return false;

            PlanetAPI star = system.getStar();
            if (star == null || Misc.getDistance(fleet, star) - star.getRadius() > LAMP_RANGE) return false;

            return lamps.hasLitBeam();
        }
    }

    // One intel from accepting the job to reporting back to the Fisherman; title, lines and map target follow the stage.
    public static class CaptainIntel extends BaseIntelPlugin {

        @Override
        public boolean shouldRemoveIntel() {
            if (isEnded()) return true;
            if (!isCaptainWaiting() && !FishermanChain.isAt(FishermanChain.Stage.T2_DEBRIEF)) endImmediately();

            return isEnded();
        }

        @Override
        public String getName() {
            return switch (FishermanChain.getStage()) {
                case T2_DEBRIEF -> "The captain's epic: report to the Fisherman";
                default -> "The captain's epic";
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
            switch (FishermanChain.getStage()) {
                case T2_MEET -> info.addPara("Meet the captain's fleet in %s", pad, text, h, getSystemName());
                case T2_LAMPS -> {
                    info.addPara("Run the Breach Lights within %s of the star in %s", pad, text, h,
                            Misc.getWithDGS(LAMP_RANGE) + " units", getSystemName());
                    info.addPara("Keep them lit for %s without a break", 0f, text, h,
                            (int) LAMP_SECONDS + " seconds");
                }
                case T2_HUNT -> info.addPara("Harpoon the %s", pad, text, h, getSpeciesName());
                case T2_REPORT -> info.addPara("Report to the captain in %s", pad, text, h, getSystemName());
                case T2_DEBRIEF -> info.addPara("Return to the nearest fishing boat", text, pad);
                default -> info.addPara("Speak to the captain in %s", pad, text, h, getSystemName());
            }
            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            float opad = 10f;
            Color h = Misc.getHighlightColor();
            FactionAPI faction = Global.getSector().getFaction(Factions.INDEPENDENT);

            info.addImages(width, 128, opad, opad, PORTRAIT, faction.getCrest());

            if (FishermanChain.isAt(FishermanChain.Stage.T2_DEBRIEF)) {
                info.addPara("The captain's business is done. The Fisherman will want to hear how it went.", opad);
                addBulletPoints(info, ListInfoMode.IN_DESC);
                FishIntelMapButton.addSetAutopilot(info, width, FishingIntro.getNearestBoat());
                return;
            }

            info.addPara("A captain who once crewed for the Fisherman is hunting a %s in %s. His Breach Lights have "
                    + "failed, and he wants yours.", opad, h, getSpeciesName(), getSystemName());
            info.addPara("Reward: one of his ships.", opad, h, "one of his ships");
            addBulletPoints(info, ListInfoMode.IN_DESC);
            FishIntelMapButton.addPlotRoute(info, width, getMapLocation(null));
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            if (FishermanChain.isAt(FishermanChain.Stage.T2_DEBRIEF)
                    && FishIntelMapButton.handleSetAutopilot(buttonId, FishingIntro.getNearestBoat())) {
                return;
            }
            if (FishIntelMapButton.handlePlotRoute(buttonId, getMapLocation(null))) return;

            super.buttonPressConfirmed(buttonId, ui);
        }

        protected String getSystemName() {
            FishermanChain.Saved saved = FishermanChain.get(false);

            return saved == null || saved.t2SystemName == null ? "the system" : saved.t2SystemName;
        }

        @Override
        public String getIcon() {
            return PORTRAIT;
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
            if (!isCaptainWaiting()) return null;

            StarSystemAPI system = getSystem();

            return system == null ? null : system.getHyperspaceAnchor();
        }
    }

    public static void register() {
        Movement.register();
        Hunt.register();
        if (!FishermanChain.isAt(FishermanChain.Stage.T2_HUNT)) clearEpic();
        if (isCaptainWaiting() && Global.getSector().getCurrentLocation() != getSystem()) ensureFleet();
    }

    public static boolean isSearching() {
        FishermanChain.Stage stage = FishermanChain.getStage();

        return stage == FishermanChain.Stage.T2_LAMPS || stage == FishermanChain.Stage.T2_HUNT;
    }

    public static boolean isCaptainWaiting() {
        FishermanChain.Stage stage = FishermanChain.getStage();

        return stage == FishermanChain.Stage.T2_MEET || stage == FishermanChain.Stage.T2_LAMPS
                || stage == FishermanChain.Stage.T2_HUNT || stage == FishermanChain.Stage.T2_REPORT;
    }

    // Keeps the saved system and species while they still qualify, so a declined offer names the same place again.
    public static boolean prepareOffer() {
        FishermanChain.Saved saved = FishermanChain.get(true);
        if (saved == null) return false;

        StarSystemAPI kept = FishRumors.findSystem(saved.t2SystemId);
        if (isCandidate(kept) && isTarget(FishSpecLoader.getFishSpec(saved.t2SpeciesId), kept)) return true;

        StarSystemAPI system = pickSystem();
        FishSpec species = system == null ? null : pickSpecies(system);
        if (species == null) return false;

        saved.t2SystemId = system.getId();
        saved.t2SystemName = system.getNameWithNoType();
        saved.t2SpeciesId = species.id;

        return true;
    }

    public static boolean start(TextPanelAPI text) {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null || !FishermanChain.isAt(FishermanChain.Stage.T1_DONE)) return false;

        StarSystemAPI system = getSystem();
        if (!isCandidate(system) || !isTarget(FishSpecLoader.getFishSpec(saved.t2SpeciesId), system)) return false;

        CampaignFleetAPI fleet = spawnFleet(system);
        if (fleet == null) return false;

        saved.t2Fleet = fleet;
        FishermanChain.setStage(FishermanChain.Stage.T2_MEET);
        register();
        FishIntelNotifications.queue(new CaptainIntel(), text);

        return true;
    }

    // "Find her for me": the lamp count starts.
    public static void beginSearch() {
        if (!FishermanChain.isAt(FishermanChain.Stage.T2_MEET)) return;

        FishermanChain.setStage(FishermanChain.Stage.T2_LAMPS);
        Hunt.register();
        updateIntel();
    }

    // Both outcomes pay the harpoon ship; a catch also pays a schematic and range data. The fleet then heads home.
    public static boolean complete(TextPanelAPI text) {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null || !FishermanChain.isAt(FishermanChain.Stage.T2_REPORT)) return false;

        Random random = new Random();
        List<FishReward.Receipt> receipts = new ArrayList<>();
        receipts.add(FishReward.ship(FishingShips.Ship.HARPOON.hullId).grantWithReceipt());
        if (saved.t2Caught) {
            FishReward schematic = pickSchematic(random);
            if (schematic != null) receipts.add(schematic.grantWithReceipt());

            for (FishReward data : FishRewardRoller.rollLocationData(random, RANGE_DATA,
                    FishRewardRoller.VALUE_PER_FISH)) {
                receipts.add(data.grantWithReceipt());
            }
        }

        releaseFleet();
        FishermanChain.setStage(FishermanChain.Stage.T2_DEBRIEF);
        register();
        FishReward.showReceipts(text, receipts);
        updateIntel();

        return true;
    }

    // A harpoon module the player has no schematic for; failing that, any module. Ship modules are never stocked,
    // and the Needle Sensor is left for the Fisherman to give.
    protected static FishReward pickSchematic(Random random) {
        Set<String> reserved = FishRewardRoller.getReservedSchematicKeys();
        List<Tackle> harpoon = new ArrayList<>();
        List<Tackle> any = new ArrayList<>();

        for (Tackle tackle : Tackle.values()) {
            if (tackle == Tackle.NONE || tackle == Tackle.NEEDLE_SENSOR || !tackle.stocked) continue;
            if (!TackleManager.isUnlocked(tackle) || ShopSchematics.has(tackle)) continue;
            if (reserved.contains(ShopSchematics.getKey(tackle))) continue;

            any.add(tackle);
            if (tackle.fits(Tackle.Fit.HARPOON)) harpoon.add(tackle);
        }

        List<Tackle> pool = harpoon.isEmpty() ? any : harpoon;

        return pool.isEmpty() ? null : FishReward.tackleSchematic(pool.get(random.nextInt(pool.size())));
    }

    // The epic was lost before anyone woke it: it goes, and the lamp count starts again.
    protected static void rearm() {
        if (!FishermanChain.isAt(FishermanChain.Stage.T2_HUNT)) return;

        clearEpic();
        FishermanChain.setStage(FishermanChain.Stage.T2_LAMPS);
        updateIntel();
    }

    // The hunt is over either way; the captain hears about it next.
    protected static void resolve(boolean caught) {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null || !isSearching()) return;

        saved.t2Caught = caught;
        clearEpic();
        FishermanChain.setStage(FishermanChain.Stage.T2_REPORT);
        Hunt.register();
        updateIntel();
    }

    // QuestPond.sweep only reaches surfaced motes, so the buried one is removed here too.
    protected static void clearEpic() {
        StarSystemAPI system = getSystem();
        if (system == null) return;

        for (SectorEntityToken mote : getEpics(system)) Misc.fadeAndExpire(mote, 1f);
    }

    protected static boolean hasEpic(StarSystemAPI system) {
        return !getEpics(system).isEmpty();
    }

    protected static boolean hasAwakeEpic(StarSystemAPI system) {
        for (SectorEntityToken mote : getEpics(system)) {
            if (mote.getCustomPlugin() instanceof FishEntityPlugin fish && EpicHunt.isActive(fish)) return true;
        }
        return false;
    }

    protected static List<SectorEntityToken> getEpics(StarSystemAPI system) {
        List<SectorEntityToken> epics = new ArrayList<>();
        for (String tag : new String[]{FishEntityPlugin.MOTE_TAG, BuriedMoteEntityPlugin.BURIED_TAG}) {
            for (SectorEntityToken mote : system.getEntitiesWithTag(tag)) {
                if (!mote.isExpired() && PLANTER.equals(QuestPond.getPlanter(mote))) epics.add(mote);
            }
        }
        return epics;
    }

    // The captain's epic, buried and quest-marked so spawners, culls and Lantern Jack leave it alone.
    protected static void placeEpic(Vector2f at) {
        FishermanChain.Saved saved = FishermanChain.get(false);
        StarSystemAPI system = getSystem();
        if (saved == null || system == null || at == null) return;

        Vector2f clear = LegendaryStarAvoidance.place(system, at, 0f);
        SectorEntityToken mote = system.addCustomEntity(Misc.genUID(), null, FishConstants.BURIED_ENTITY_ID, null,
                new BuriedMoteEntityPlugin.Params(saved.t2SpeciesId));
        mote.setLocation(clear.x, clear.y);
        QuestPond.markPlanted(mote, PLANTER);

        FishermanChain.setStage(FishermanChain.Stage.T2_HUNT);
        updateIntel();
    }

    public static void updateIntel() {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(CaptainIntel.class)) {
            FishIntelNotifications.update((CaptainIntel) intel, null);
        }
    }

    public static CampaignFleetAPI getFleet() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null ? null : saved.t2Fleet;
    }

    protected static void ensureFleet() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        StarSystemAPI system = getSystem();
        if (saved == null || system == null || !isCaptainWaiting()) return;

        CampaignFleetAPI fleet = saved.t2Fleet;
        if (fleet != null && fleet.isAlive() && !fleet.isExpired() && fleet.getContainingLocation() == system) return;

        saved.t2Fleet = spawnFleet(system);
    }

    protected static CampaignFleetAPI spawnFleet(StarSystemAPI system) {
        PlanetAPI star = system.getStar();
        if (star == null) return null;

        FleetParamsV3 params = new FleetParamsV3(null, system.getLocation(), Factions.INDEPENDENT, null,
                FleetTypes.SCAVENGER_SMALL, FLEET_POINTS, 0f, FLEET_POINTS * 0.1f, 0f, 0f, 0f, 0f);
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setNoFactionInName(true);
        fleet.setName(FLEET_NAME);
        fleet.setTransponderOn(true);

        system.addEntity(fleet);
        Vector2f at = MathUtils.getPointOnCircumference(star.getLocation(), star.getRadius() + HOLD_DISTANCE,
                MathUtils.getRandomNumberInRange(0f, 360f));
        fleet.setLocation(at.x, at.y);

        PersonAPI captain = Global.getSector().getFaction(Factions.INDEPENDENT)
                .createRandomPerson(FullName.Gender.MALE);
        captain.setPortraitSprite(PORTRAIT);
        captain.setRankId(Ranks.SPACE_CAPTAIN);
        captain.setPostId(Ranks.POST_FLEET_COMMANDER);
        fleet.setCommander(captain);
        FleetMemberAPI flagship = fleet.getFlagship();
        if (flagship != null) flagship.setCaptain(captain);

        // never hostile and never drawn away: the player must always be able to find him and talk
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.set(CAPTAIN_FLAG, true);
        mem.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        mem.set(MemFlags.NON_HOSTILE_OVERRIDES_MAKE_HOSTILE, true);
        mem.set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        mem.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        mem.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        mem.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        mem.set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
        Misc.makeImportant(fleet, IMPORTANT_REASON);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, star, HOLD_DAYS, "Hunting");

        return fleet;
    }

    // The captain heads home: the fleet makes for the nearest jump point and despawns there.
    protected static void releaseFleet() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        CampaignFleetAPI fleet = saved == null ? null : saved.t2Fleet;
        if (saved != null) saved.t2Fleet = null;
        if (fleet == null || fleet.isExpired()) return;

        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.unset(MemFlags.MEMORY_KEY_NO_JUMP);
        mem.unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);
        mem.unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        mem.unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
        Misc.makeUnimportant(fleet, IMPORTANT_REASON);

        SectorEntityToken exit = Misc.findNearestJumpPointTo(fleet);
        fleet.clearAssignments();
        if (exit == null) {
            fleet.despawn();
        } else {
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, exit, 1000f, "Heading home");
        }
    }

    public static StarSystemAPI getSystem() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null ? null : FishRumors.findSystem(saved.t2SystemId);
    }

    public static String getSpeciesName() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        FishSpec spec = saved == null ? null : FishSpecLoader.getFishSpec(saved.t2SpeciesId);

        return spec == null ? "" : spec.getDisplayName();
    }

    // The nearest qualifying system, at any distance.
    protected static StarSystemAPI pickSystem() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Vector2f from = player == null ? new Vector2f() : player.getLocationInHyperspace();

        StarSystemAPI nearest = null;
        float best = Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isCandidate(system) || pickSpecies(system) == null) continue;

            float distance = Misc.getDistanceLY(from, system.getLocation());
            if (distance < best) {
                best = distance;
                nearest = system;
            }
        }

        return nearest;
    }

    // No markets or colonies, and a primary star the lamps can circle: never a nebula, pulsar or black hole.
    protected static boolean isCandidate(StarSystemAPI system) {
        if (system == null || system.getId() == null || system.isNebula()) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER) || system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;
        if (system.getHyperspaceAnchor() == null || OuterReaches.isPopulated(system)) return false;
        if (FishRumors.isOccupied(system.getId())) return false;

        PlanetAPI star = system.getStar();
        if (star == null || star.getSpec() == null) return false;

        return !star.getSpec().isPulsar() && !star.getSpec().isBlackHole();
    }

    protected static FishSpec pickSpecies(StarSystemAPI system) {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (isTarget(spec, system)) picker.add(spec, spec.spawnWeight);
        }

        return picker.pick();
    }

    // An ordinary epic the lamps can find there; Lantern Jack and the other legendaries never qualify.
    protected static boolean isTarget(FishSpec spec, StarSystemAPI system) {
        if (spec == null || system == null || spec.rarity != FishRarity.EPIC) return false;
        if (spec.spawnWeight <= 0f || !spec.hasHabitat()) return false;

        return FishRanges.matches(spec, system, CatchImplement.BREACH_LAMP);
    }
}
