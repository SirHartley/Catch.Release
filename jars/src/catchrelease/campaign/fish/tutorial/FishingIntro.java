package catchrelease.campaign.fish.tutorial;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.intel.FishIntelIcon;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import lunalib.backend.ui.settings.LunaSettingsLoader;
import lunalib.lunaSettings.LunaSettings;
import org.lazywizard.lazylib.JSONUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class FishingIntro {

    public static final int UNSTARTED = 0;
    public static final int POINTED = 1;
    public static final int RODDED = 2;

    public static final int FISH_ONE = 3;
    public static final int FISH_TWO = 4;
    public static final int FISH_THREE = 5;
    public static final int DONE = 6;

    public static class TutorialBoatKeeper implements EveryFrameScript, Serializable {

        private final CampaignFleetAPI boat;
        private final String systemId;
        private final boolean temporary;
        private boolean done;

        public TutorialBoatKeeper(CampaignFleetAPI boat, String systemId, boolean temporary) {
            this.boat = boat;
            this.systemId = systemId;
            this.temporary = temporary;
        }

        @Override
        public void advance(float amount) {
            if (boat == null || boat.isExpired() || !boat.isAlive()) {
                done = true;
                return;
            }

            Target target = getTarget();
            if (getStage() == FISH_ONE && target != null
                    && systemId.equals(target.systemId)) return;

            if (catchrelease.helper.CampaignHelper.isPlayerHere(boat)) return;

            boat.getMemoryWithoutUpdate().unset(FishermanConstants.TUTORIAL_TARGET_KEY);
            if (temporary) {
                boat.getMemoryWithoutUpdate().unset(FishermanConstants.TUTORIAL_TEMPORARY_KEY);
                boat.despawn();
            }
            done = true;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }
    }

    public static class Target implements Serializable {

        public int stage;
        public List<String> speciesIds = new ArrayList<>();

        public String systemId;
        public String systemName;

        public float x;
        public float y;
        public boolean atPond;
        public boolean needsDeepGear;
        public boolean anySpecies;
        public boolean landed;
        public int qualifyingCatches;
    }

    public static class Keeper implements com.fs.starfarer.api.EveryFrameScript {

        protected final com.fs.starfarer.api.util.IntervalUtil interval =
                new com.fs.starfarer.api.util.IntervalUtil(
                        TutorialConstants.KEEP_CHECK_SECONDS, TutorialConstants.KEEP_CHECK_SECONDS);

        public static void register() {
            Global.getSector().addTransientScript(new Keeper());
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
            interval.advance(amount);
            if (!interval.intervalElapsed()) return;

            Target target = getTarget();
            if (target == null) return;

            if (target.stage == FISH_ONE) ensureTargetBoat(target);

            setLanded(target, isTargetMet());
            if (target.landed) return;

            ensureTargetPondClaim(target);

            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null) return;

            StarSystemAPI system = asSystem(player.getContainingLocation());
            if (system == null || !system.getId().equals(target.systemId)) return;

            for (String speciesId : target.speciesIds) {
                if (isPlanted(system, speciesId)) continue;

                plant(target, system, speciesId);
            }
        }
    }

    public static class IntroIntel extends BaseIntelPlugin
            implements catchrelease.campaign.fish.shop.FishAsker {

        @Override
        public List<catchrelease.campaign.fish.shop.FishRequirement> getAsks() {
            List<catchrelease.campaign.fish.shop.FishRequirement> out = new ArrayList<>();

            Target target = getTarget();
            if (target == null || target.anySpecies) return out;

            for (String speciesId : target.speciesIds) {
                catchrelease.campaign.fish.shop.FishRequirement ask =
                        new catchrelease.campaign.fish.shop.FishRequirement();

                ask.speciesId = speciesId;

                if (target.needsDeepGear) {
                    ask.implement = CatchImplement.BREACH_LAMP;
                    ask.method = FishLogEntry.Method.HARPOON;
                }

                out.add(ask);
            }

            return out;
        }

        @Override
        public String getAskerName() {
            return "Fishing lessons";
        }

        @Override
        public String getName() {
            if (isCarryingFisherProperty() && !isAtLeast(RODDED)) {
                return "Return the service assembly";
            }
            if (!isAtLeast(RODDED)) return "Fishing: find a boat";

            if (isLanded()) return "Fishing: take it back";

            return "Fishing: " + describeTarget();
        }

        @Override
        public String getSmallDescriptionTitle() {
            Target target = getTarget();
            if (target == null) return getName();
            if (target.landed) return "Fishing: take it back";
            return "Fishing lesson";
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);

            addBulletPoints(info, mode);
        }

        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color text = getBulletColorForMode(mode);
            float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);

            Target target = getTarget();
            if (target == null) {
                SectorEntityToken at = getMapLocation(null);
                if (at != null && at.getContainingLocation() != null) {
                    info.addPara("Nearest boat: %s", pad, text, Misc.getHighlightColor(),
                            at.getContainingLocation().getName());
                }
            } else {
                pad = addProgressLines(info, target, text, pad);

                if (target.landed) {
                    info.addPara("Return to the nearest fishing boat", text, pad);
                } else {
                    pad = addDestinationLine(info, target, text, pad);

                    if (target.needsDeepGear) {
                        info.addPara("Breach Lights and Harpoon only", text, pad);
                    } else if (target.atPond) {
                        info.addPara("ROD/LINE at the marked rupture", text, pad);
                    }
                }
            }

            unindent(info);
        }

        protected float addProgressLines(TooltipMakerAPI info, Target target, Color text,
                                         float pad) {
            if (target.anySpecies) {
                int aboard = target.landed ? 1 : 0;
                info.addPara("%s anything you can land", pad, text, Misc.getHighlightColor(),
                        aboard + "/1");
                return 0f;
            }

            for (catchrelease.campaign.fish.shop.FishRequirement ask : getAsks()) {
                int aboard = find(ask.speciesId, target.needsDeepGear) == null ? 0 : 1;
                String progress = ask.describeProgress(aboard);
                LabelAPI line = info.addPara(progress, text, pad);
                FishRequirement.highlight(line,
                        java.util.Collections.singletonList(ask), progress,
                        aboard + "/" + ask.count);
                pad = 0f;
            }

            return pad;
        }

        protected float addDestinationLine(TooltipMakerAPI info, Target target, Color text,
                                           float pad) {
            if (target.systemName == null) {
                info.addPara("Known ranges on the fishing map", text, pad);
            } else if (target.atPond) {
                info.addPara("Marked rupture in %s", pad, text, Misc.getHighlightColor(),
                        target.systemName);
            } else {
                info.addPara("Open space in %s", pad, text, Misc.getHighlightColor(),
                        target.systemName);
            }

            return 0f;
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            Target target = getTarget();
            float opad = 10f;
            Color highlight = Misc.getHighlightColor();
            Color text = getBulletColorForMode(ListInfoMode.IN_DESC);

            FactionAPI faction = getFishermanFaction();
            if (target != null) {
                info.addImages(width, 128, opad, opad,
                        FishermanIdentity.getPortrait(0f), faction.getCrest());
            }

            if (target != null) {
                info.addPara("Fishing lessons given by the Fisherman.", opad,
                        faction.getBaseUIColor(),
                        faction.getDisplayNameWithArticleWithoutArticle());
            }

            if (target == null) {
                if (isCarryingFisherProperty()) {
                    info.addPara("The LYNE service assembly still carries its last accepted"
                            + " handshake. Take it to a fishing boat.", opad);
                } else {
                    info.addPara("There is a trade working the far edges of the inhabited systems."
                            + " Find one of their boats and hail it.", opad);
                }

                info.addPara("Next step:", opad);
                bullet(info);
                SectorEntityToken at = getMapLocation(null);
                if (at != null && at.getContainingLocation() != null) {
                    info.addPara("Find the nearest fishing boat in %s", 0f, text, highlight,
                            at.getContainingLocation().getName());
                } else {
                    info.addPara("Find and hail a fishing boat", text, 0f);
                }
                unindent(info);
            } else {
                String catchWord = target.speciesIds.size() > 1 ? "catches" : "catch";
                info.addPara("The Fisherman is waiting for the lesson's " + catchWord + ".", opad);

                info.addPara("What is wanted:", opad);
                bullet(info);
                addProgressLines(info, target, text, 0f);
                unindent(info);

                info.addPara(target.landed ? "Return to:" : "Where and how:", opad);
                bullet(info);
                if (target.landed) {
                    info.addPara("The nearest fishing boat", text, 0f);
                } else {
                    addDestinationLine(info, target, text, 0f);

                    if (target.needsDeepGear) {
                        info.addPara("Use the Breach Lights, then land it with the Harpoon",
                                text, 0f);
                    } else if (target.atPond) {
                        info.addPara("Use the ROD/LYNE at the marked rupture", text, 0f);
                    }
                }
                unindent(info);
            }

            if (target != null && target.systemId != null && !target.landed) {
                FishIntelMapButton.addPlotRoute(info, width, getMapLocation(null));
            } else if (target == null || target.landed) {
                FishIntelMapButton.addSetAutopilot(info, width, getMapLocation(null));
            } else {
                FishIntelMapButton.add(info, width, getAsks());
            }

            //addBulletPoints(info, ListInfoMode.IN_DESC);
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            Target target = getTarget();
            if ((target == null || target.landed)
                    && FishIntelMapButton.handleSetAutopilot(buttonId, getMapLocation(null))) return;

            if (target != null && target.systemId != null
                    && FishIntelMapButton.handlePlotRoute(buttonId, getMapLocation(null))) return;

            List<catchrelease.campaign.fish.shop.FishRequirement> mapAsks = target == null
                    ? null : getAsks();
            SectorEntityToken center = target == null ? getMapLocation(null) : null;

            if (FishIntelMapButton.handle(buttonId, ui, mapAsks, center, null)) return;
            super.buttonPressConfirmed(buttonId, ui);
        }

        @Override
        public String getIcon() {
            int stage = getStage();

            if (stage <= POINTED) {
                return Global.getSettings().getSpriteName(ModPlugin.MOD_ID, "intel_tutorial");
            }
            if (stage == RODDED || stage == FISH_ONE) {
                return FishIntelIcon.get(CatchImplement.POND);
            }
            if (stage == FISH_TWO) {
                return FishIntelIcon.get(CatchImplement.BREACH_LAMP);
            }

            return FishIntelIcon.get(getAsks());
        }

        @Override
        public FactionAPI getFactionForUIColors() {
            return Global.getSector().getPlayerFaction();
        }

        public FactionAPI getFishermanFaction() {
            return Global.getSector().getFaction(FishermanConstants.FACTION);
        }

        @Override
        public String getSortString() {
            return getSortStringNewestFirst();
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_EXPLORATION);
            tags.add(Tags.INTEL_MISSIONS);
            tags.add(Tags.INTEL_ACCEPTED);

            return tags;
        }

        @Override
        public SectorEntityToken getMapLocation(SectorMapAPI map) {
            Target target = getTarget();

            // once it is aboard the water is not where the player is being sent
            if (target != null && !target.landed) {
                if (target.systemId == null) return null;

                for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                    if (!system.getId().equals(target.systemId)) continue;

                    if (target.atPond) {
                        SectorEntityToken pond = QuestPond.findPondAt(system, target.x, target.y,
                                TutorialConstants.SPOT_SPREAD);

                        if (pond != null) return pond;
                    }

                    return system.getHyperspaceAnchor();
                }

                // A target that names a system which no longer resolves is still a target. It is not an instruction to substitute the nearest boat and point somewhere unrelated.
                return null;
            }

            return getNearestBoat();
        }
    }

    public static int getStage() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(TutorialConstants.STAGE_KEY);
    }

    public static boolean isAtLeast(int stage) {
        return getStage() >= stage;
    }

    public static boolean isComplete() {
        return Global.getSector() != null && isAtLeast(DONE);
    }

    protected static void setStage(int stage) {
        if (getStage() >= stage) return;

        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.STAGE_KEY, stage);
    }

    public static boolean isOpenForWork() {
        return isAtLeast(FISH_ONE);
    }

    public static boolean isShelfOpen(catchrelease.campaign.fish.shop.ShopGroup shelf) {
        if (shelf == null) return true;

        boolean deep = shelf == catchrelease.campaign.fish.shop.ShopGroup.SEARCHLIGHTS
                || shelf == catchrelease.campaign.fish.shop.ShopGroup.SEARCHLIGHT_RIG
                || shelf == catchrelease.campaign.fish.shop.ShopGroup.HARPOON
                || shelf == catchrelease.campaign.fish.shop.ShopGroup.HARPOON_TIPS;

        return !deep || isAtLeast(FISH_THREE);
    }

    public static boolean hasGear(String abilityId) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        return player != null && player.hasAbility(abilityId);
    }

    public static boolean canSkip() {
        return Boolean.TRUE.equals(LunaSettings.getBoolean(
                ModPlugin.MOD_ID, TutorialConstants.SKIP_SETTING))
                && !isAtLeast(RODDED);
    }

    public static void enableFutureSkip() {
        try {
            JSONUtils.CommonDataJSONObject settings = JSONUtils.loadCommonJSON(
                    "LunaSettings/" + ModPlugin.MOD_ID + ".json",
                    "data/config/LunaSettingsDefault.default");
            if (settings == null) throw new IllegalStateException("Luna settings unavailable");

            settings.put(TutorialConstants.SKIP_SETTING, true);
            settings.save();

            // LunaLib has no public setter; mirror the saved object without firing its global settings lifecycle.
            LunaSettingsLoader.getSettings().put(ModPlugin.MOD_ID, settings);
        } catch (Exception e) {
            // A shortcut nobody can offer is a slower first hour, not a broken campaign.
            Global.getLogger(FishingIntro.class).warn(
                    "Could not enable the tutorial skip setting", e);
        }
    }

    public static void skip(TextPanelAPI text) {
        grant(TutorialConstants.ROD, text);
        for (String ability : TutorialConstants.DEEP_GEAR) grant(ability, text);

        giveCharts(TutorialConstants.FREE_COMMONS, null, text);
        for (int rung = 0; rung < TutorialConstants.GRADUATION_CHARTS.length; rung++) {
            giveChartsOfRarity(FishRarity.ofRank(rung),
                    TutorialConstants.GRADUATION_CHARTS[rung], null, text);
        }

        clearTarget();
        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.STAGE_KEY, DONE);
        Global.getSector().getMemoryWithoutUpdate().unset(TutorialConstants.DEEP_HANDOFF_KEY);

        dropNote();
        FishRumors.ensureTutorialLead();
    }

    public static void point() {
        if (isAtLeast(POINTED)) return;

        setStage(POINTED);

        IntroIntel intel = new IntroIntel();
        FishIntelNotifications.queue(intel);
    }

    public static void giveRod(TextPanelAPI text) {
        point();
        setStage(RODDED);

        grant(TutorialConstants.ROD, text);

        setTarget(rollTarget(RODDED));
    }

    public static void sendOut(TextPanelAPI text) {
        setStage(FISH_ONE);

        Target target = rollTarget(FISH_ONE);
        setTarget(target);
        ensureTargetBoat(target);
    }

    protected static void ensureTargetBoat(Target target) {
        if (target == null || target.stage != FISH_ONE || target.systemId == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!target.systemId.equals(system.getId())) continue;

            CampaignFleetAPI boat = CoreFisherSpawner.ensureBoat(system);

            if (boat != null) {
                Object reserved = boat.getMemoryWithoutUpdate()
                        .get(FishermanConstants.TUTORIAL_TARGET_KEY);
                if (target.systemId.equals(reserved)) return;

                // Only an uninhabited-system standing boat is the disposable directed posting. A visitor or an ordinary core trawler returns to its normal lifecycle afterwards.
                boolean temporary = !FishermanSpawner.isVisiting(boat)
                        && !OuterReaches.isPopulated(system);
                boat.getMemoryWithoutUpdate().set(FishermanConstants.TUTORIAL_TARGET_KEY,
                        target.systemId);
                if (temporary) {
                    boat.getMemoryWithoutUpdate().set(FishermanConstants.TUTORIAL_TEMPORARY_KEY,
                            true);
                }

                boat.addScript(new TutorialBoatKeeper(boat, target.systemId, temporary));
            }

            return;
        }
    }

    public static void giveOutfitter(TextPanelAPI text) {
        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.DEEP_HANDOFF_KEY, true);
    }

    public static boolean isDeepHandoffPending() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.DEEP_HANDOFF_KEY);
    }

    public static void giveDeepGear(TextPanelAPI text) {
        setStage(FISH_TWO);

        for (String ability : TutorialConstants.DEEP_GEAR) grant(ability, text);
        Global.getSector().getMemoryWithoutUpdate().unset(TutorialConstants.DEEP_HANDOFF_KEY);

        setTarget(rollTarget(FISH_TWO));
    }

    public static void giveCharts(TextPanelAPI text) {
        setStage(FISH_THREE);

        List<String> given = new ArrayList<>();
        giveNearbyCommonCharts(TutorialConstants.FREE_COMMONS, given, text);

        Target target = new Target();
        target.stage = FISH_THREE;
        target.speciesIds = given;

        setTarget(target);
    }

    public static void finish(TextPanelAPI text) {
        setStage(DONE);

        clearTarget();
        dropNote();

        for (int rung = 0; rung < TutorialConstants.GRADUATION_CHARTS.length; rung++) {
            giveChartsOfRarity(FishRarity.ofRank(rung),
                    TutorialConstants.GRADUATION_CHARTS[rung], null, text);
        }

        enableFutureSkip();
        FishRumors.ensureTutorialLead();
    }

    public static boolean showCurrentIntel(TextPanelAPI text) {
        if (text == null) return false;

        IntelManagerAPI manager = Global.getSector().getIntelManager();
        List<IntelInfoPlugin> entries = manager.getCommQueue(IntroIntel.class);
        if (entries.isEmpty()) entries = manager.getIntel(IntroIntel.class);
        if (entries.isEmpty()) return false;

        FishIntelNotifications.showAdded((IntroIntel) entries.get(0), text);
        return true;
    }

    public static Target getTarget() {
        Object stored = Global.getSector().getPersistentData().get(TutorialConstants.TARGET_KEY);

        return stored instanceof Target ? (Target) stored : null;
    }

    protected static void setTarget(Target target) {
        if (target == null) {
            clearTarget();
            return;
        }

        // the outgoing errand lets go of its water before the incoming one is stored, or the mark on the old rupture outlives every reference to what put it there
        letGo(getTarget());

        Global.getSector().getPersistentData().put(TutorialConstants.TARGET_KEY, target);
        ensureTargetPondClaim(target);
        updateIntel();
    }

    protected static void clearTarget() {
        letGo(getTarget());

        Global.getSector().getPersistentData().remove(TutorialConstants.TARGET_KEY);
    }

    protected static void updateIntel() {
        IntelManagerAPI manager = Global.getSector().getIntelManager();
        if (!manager.getCommQueue(IntroIntel.class).isEmpty()) return;

        List<IntelInfoPlugin> active = manager.getIntel(IntroIntel.class);
        if (active.isEmpty()) return;

        sendIntelUpdate(active);
    }

    protected static void sendIntelUpdate(List<IntelInfoPlugin> entries) {
        for (IntelInfoPlugin intel : entries) {
            FishIntelNotifications.update((IntroIntel) intel, null);
        }
    }

    protected static void letGo(Target target) {
        if (target == null) return;

        QuestPond.releaseAll(TutorialConstants.TARGET_KEY);
        QuestPond.clearMotes(TutorialConstants.TARGET_KEY);
    }

    protected static void ensureTargetPondClaim(Target target) {
        if (target == null || !target.atPond || target.systemId == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!target.systemId.equals(system.getId())) continue;

            SectorEntityToken pond = QuestPond.findPondAt(system, target.x, target.y,
                    TutorialConstants.SPOT_SPREAD);

            if (pond != null && !QuestPond.isClaimedBy(
                    pond, TutorialConstants.TARGET_KEY)) {
                QuestPond.claim(pond, TutorialConstants.TARGET_KEY);
            }
            return;
        }
    }

    protected static void setLanded(Target target, boolean landed) {
        if (target == null || target.landed == landed) return;

        target.landed = landed;

        if (landed) letGo(target);

        updateIntel();
    }

    public static void onCatchStored(FishCatch caught) {
        Target target = getTarget();
        if (target == null || caught == null) return;
        if (target.anySpecies) {
            if (TutorialConstants.TARGET_KEY.equals(caught.questTargetId)) {
                setLanded(target, true);
            }
            return;
        }

        if (target.speciesIds == null || !target.speciesIds.contains(caught.speciesId)) return;
        if (target.needsDeepGear
                && (caught.method != FishLogEntry.Method.HARPOON
                || caught.implement != CatchImplement.BREACH_LAMP)) return;

        if (isTargetMet()) {
            setLanded(target, true);
        } else if (target.speciesIds.size() > 1) {
            updateIntel();
        }
    }

    public static FishSpec applyCatchTargetProtection(FishSpec rolled,
                                                       FishLogEntry.Method method,
                                                       CatchImplement implement,
                                                       LocationAPI where,
                                                       String planterId) {
        Target target = getTarget();
        FishSpec wanted = getProtectedTarget(target);
        if (rolled == null || wanted == null || rolled.rarity != wanted.rarity) return rolled;
        if (!isTargetAvailable(target, wanted, method, implement, where)) return rolled;
        if (planterId != null && !TutorialConstants.TARGET_KEY.equals(planterId)) return rolled;
        if (target.qualifyingCatches < TutorialConstants.TARGET_PROTECTION_CATCHES - 1) {
            return rolled;
        }

        return wanted;
    }

    public static void recordCatchForTargetProtection(FishCatch caught, LocationAPI where) {
        Target target = getTarget();
        if (caught == null) return;

        FishSpec wanted = getProtectedTarget(target);
        FishSpec landed = caught.getSpec();
        if (wanted == null || landed == null || landed.rarity != wanted.rarity) return;
        if (wanted.id.equals(landed.id)) return;
        if (!isTargetAvailable(target, wanted, caught.method, caught.implement, where)) return;

        target.qualifyingCatches = Math.min(
                TutorialConstants.TARGET_PROTECTION_CATCHES - 1,
                target.qualifyingCatches + 1);
    }

    protected static FishSpec getProtectedTarget(Target target) {
        if (target == null || target.landed || target.anySpecies) return null;
        if (target.stage != FISH_ONE && target.stage != FISH_TWO) return null;
        if (target.speciesIds == null || target.speciesIds.size() != 1) return null;

        return FishSpecLoader.getFishSpec(target.speciesIds.get(0));
    }

    protected static boolean isTargetAvailable(Target target, FishSpec wanted,
                                               FishLogEntry.Method method,
                                               CatchImplement implement,
                                               LocationAPI where) {
        return matchesTargetMethod(target, method, implement)
                && FishRanges.matches(wanted, where, implement);
    }

    protected static boolean matchesTargetMethod(Target target,
                                                 FishLogEntry.Method method,
                                                 CatchImplement implement) {
        return target != null && (!target.needsDeepGear
                || (method == FishLogEntry.Method.HARPOON
                && implement == CatchImplement.BREACH_LAMP));
    }

    public static boolean isLanded() {
        Target target = getTarget();

        return target != null && target.landed;
    }

    protected static Target rollTarget(int stage) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return null;

        StarSystemAPI system = stage == RODDED
                ? asSystem(player.getContainingLocation())
                : pickSystem(stage);

        if (system == null) system = asSystem(player.getContainingLocation());
        if (system == null) return null;

        Target target = new Target();
        target.stage = stage;
        target.systemId = system.getId();
        target.systemName = system.getName();
        target.needsDeepGear = stage == FISH_TWO;
        target.anySpecies = stage == RODDED;

        CatchImplement implement = target.needsDeepGear
                ? CatchImplement.BREACH_LAMP : CatchImplement.POND;
        FishSpec spec = pickSpecies(stage, system, implement);
        if (spec == null) {
            StarSystemAPI fallbackSystem = pickFallbackSystem(stage, implement);
            FishSpec fallbackSpec = pickFallbackSpecies(stage, fallbackSystem, implement);

            if (fallbackSystem == null || fallbackSpec == null) {
                Global.getLogger(FishingIntro.class).warn("No valid "
                        + getMaxTargetRarity(stage).name().toLowerCase()
                        + "-or-below tutorial target for stage " + stage
                        + "; refusing to create an impossible target.");
                return null;
            }

            Global.getLogger(FishingIntro.class).warn("No valid "
                    + getMaxTargetRarity(stage).name().toLowerCase()
                    + "-or-below tutorial target in " + system.getName()
                    + " for stage " + stage + "; using deterministic fallback "
                    + fallbackSpec.id + " in " + fallbackSystem.getName() + ".");
            system = fallbackSystem;
            target.systemId = system.getId();
            target.systemName = system.getName();
            spec = fallbackSpec;
        }
        target.speciesIds.add(spec.id);

        SectorEntityToken pond = target.needsDeepGear ? null : QuestPond.findFreePond(system);

        if (pond != null) {
            target.atPond = true;
            target.x = pond.getLocation().x;
            target.y = pond.getLocation().y;
        } else {
            Vector2f at = catchrelease.campaign.fish.fisherman.OuterReaches.center(system);

            target.atPond = false;
            target.x = at.x + Misc.getUnitVectorAtDegreeAngle(
                    (float) Math.random() * 360f).x * 6000f;
            target.y = at.y + Misc.getUnitVectorAtDegreeAngle(
                    (float) Math.random() * 360f).y * 6000f;
        }

        return target;
    }

    protected static StarSystemAPI asSystem(Object location) {
        return location instanceof StarSystemAPI ? (StarSystemAPI) location : null;
    }

    protected static StarSystemAPI pickSystem(int stage) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Vector2f from = player == null ? new Vector2f() : player.getLocationInHyperspace();

        WeightedRandomPicker<StarSystemAPI> thin = new WeightedRandomPicker<>();
        WeightedRandomPicker<StarSystemAPI> any = new WeightedRandomPicker<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) continue;
            if (system.getCenter() == null) continue;

            float distance = Misc.getDistanceLY(from, system.getLocation());
            if (distance < TutorialConstants.SECOND_MIN_LY) continue;
            if (distance > TutorialConstants.SECOND_MAX_LY) continue;

            any.add(system, 1f);

            if (Aberration.baseAt(system.getLocation(), system)
                    >= TutorialConstants.SECOND_MIN_DRIFT) {
                thin.add(system, 1f);
            }
        }

        StarSystemAPI pick = thin.pick();

        return pick != null ? pick : any.pick();
    }

    protected static FishSpec pickSpecies(int stage, StarSystemAPI system,
                                          CatchImplement implement) {
        List<FishSpec> candidates = getSpeciesCandidates(stage, system, implement);
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : candidates) {
            picker.add(spec, spec.spawnWeight
                    * (spec.rarity == FishRarity.COMMON ? 4f : 1f));
        }

        return picker.pick();
    }

    protected static FishRarity getMaxTargetRarity(int stage) {
        if (stage == RODDED || stage == FISH_ONE) return FishRarity.COMMON;
        if (stage == FISH_TWO || stage == FISH_THREE) return FishRarity.UNCOMMON;
        return FishRarity.COMMON;
    }

    protected static List<FishSpec> getSpeciesCandidates(int stage, StarSystemAPI system,
                                                          CatchImplement implement) {
        List<FishSpec> candidates = new ArrayList<>();
        if (system == null) return candidates;

        FishRarity maximum = getMaxTargetRarity(stage);

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || spec.rarity == null || !spec.hasHabitat()) continue;
            if (spec.spawnWeight <= 0f || !FishRanges.matches(spec, system, implement)) continue;
            if (!isHarpoonLessonCandidate(stage, spec)) continue;
            if (spec.rarity.rank > maximum.rank) continue;

            candidates.add(spec);
        }

        return candidates;
    }

    protected static boolean isHarpoonLessonCandidate(int stage, FishSpec spec) {
        if (stage != FISH_TWO) return true;

        return spec.reachedBy.size() == 1 && spec.reachedBy.contains(CatchImplement.BREACH_LAMP);
    }

    protected static FishSpec pickFallbackSpecies(int stage, StarSystemAPI system,
                                                  CatchImplement implement) {
        List<FishSpec> candidates = getSpeciesCandidates(stage, system, implement);
        candidates.sort(Comparator.comparing(spec -> spec.id));

        return candidates.isEmpty() ? null : candidates.get(0);
    }

    protected static StarSystemAPI pickFallbackSystem(int stage, CatchImplement implement) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        StarSystemAPI current = player == null ? null : asSystem(player.getContainingLocation());

        if (stage == RODDED) {
            return getSpeciesCandidates(stage, current, implement).isEmpty() ? null : current;
        }

        Vector2f from = player == null ? new Vector2f() : player.getLocationInHyperspace();
        List<StarSystemAPI> thin = new ArrayList<>();
        List<StarSystemAPI> any = new ArrayList<>();

        for (StarSystemAPI candidate : Global.getSector().getStarSystems()) {
            if (candidate == null || candidate.getId() == null) continue;
            if (candidate.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (candidate.hasTag(Tags.THEME_SPECIAL) || candidate.hasTag(Tags.THEME_HIDDEN)) continue;
            if (candidate.getCenter() == null) continue;

            float distance = Misc.getDistanceLY(from, candidate.getLocation());
            if (distance < TutorialConstants.SECOND_MIN_LY) continue;
            if (distance > TutorialConstants.SECOND_MAX_LY) continue;
            if (getSpeciesCandidates(stage, candidate, implement).isEmpty()) continue;

            any.add(candidate);
            if (Aberration.baseAt(candidate.getLocation(), candidate)
                    >= TutorialConstants.SECOND_MIN_DRIFT) {
                thin.add(candidate);
            }
        }

        thin.sort(Comparator.comparing(StarSystemAPI::getId));
        any.sort(Comparator.comparing(StarSystemAPI::getId));
        return !thin.isEmpty() ? thin.get(0) : any.isEmpty() ? null : any.get(0);
    }

    public static boolean isTargetMet() {
        Target target = getTarget();
        if (target == null) return false;

        if (target.anySpecies) {
            return find(null, target.needsDeepGear, TutorialConstants.TARGET_KEY) != null;
        }

        for (String speciesId : target.speciesIds) {
            if (find(speciesId, target.needsDeepGear) == null) return false;
        }

        return true;
    }

    protected static FishCatch find(String speciesId, boolean deepGear) {
        return find(speciesId, deepGear, null);
    }

    protected static FishCatch find(String speciesId, boolean deepGear, String questTargetId) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return null;

        for (CargoStackAPI stack : player.getCargo().getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            for (FishCatch entry : FishItems.read(data)) {
                if (speciesId != null && !speciesId.equals(entry.speciesId)) continue;
                if (questTargetId != null && !questTargetId.equals(entry.questTargetId)) continue;

                if (deepGear && (entry.implement != CatchImplement.BREACH_LAMP
                        || entry.method != FishLogEntry.Method.HARPOON)) {
                    continue;
                }

                return entry;
            }
        }

        return null;
    }

    public static boolean takeTarget() {
        Target target = getTarget();
        if (target == null || !isTargetMet()) return false;

        if (target.anySpecies) {
            FishCatch any = find(null, target.needsDeepGear, TutorialConstants.TARGET_KEY);
            if (any != null) {
                spend(any.speciesId, target.needsDeepGear, TutorialConstants.TARGET_KEY);
            }
        } else {
            for (String speciesId : target.speciesIds) spend(speciesId, target.needsDeepGear);
        }

        clearTarget();

        return true;
    }

    protected static boolean spend(String speciesId, boolean deepGear) {
        return spend(speciesId, deepGear, null);
    }

    protected static boolean spend(String speciesId, boolean deepGear, String questTargetId) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        CargoAPI cargo = player.getCargo();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            List<FishCatch> contents = FishItems.read(data);

            int found = -1;
            for (int i = 0; i < contents.size(); i++) {
                FishCatch entry = contents.get(i);
                if (speciesId != null && !speciesId.equals(entry.speciesId)) continue;
                if (questTargetId != null && !questTargetId.equals(entry.questTargetId)) continue;

                if (deepGear && (entry.implement != CatchImplement.BREACH_LAMP
                        || entry.method != FishLogEntry.Method.HARPOON)) {
                    continue;
                }

                found = i;
                break;
            }
            if (found < 0) continue;

            if (!FishItems.isContainer(data)) {
                cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1);
                return true;
            }

            contents.remove(found);
            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1);

            // a container's contents are its identity, so a part-spent one is a different item
            if (!contents.isEmpty()) {
                cargo.addSpecial(FishItems.repack(data.getId(), contents), 1);
            }

            return true;
        }

        return false;
    }

    public static void giveCharts(int count, List<String> givenOut) {
        giveCharts(count, givenOut, null);
    }

    protected static void giveCharts(int count, List<String> givenOut, TextPanelAPI text) {
        giveChartsOfRarity(FishRarity.COMMON, count, givenOut, text);
    }

    protected static void giveNearbyCommonCharts(int count, List<String> givenOut,
                                                 TextPanelAPI text) {
        List<StarSystemAPI> nearby = getNearbyChartSystems();
        List<FishSpec> candidates = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || spec.rarity != FishRarity.COMMON) continue;
            if (!spec.hasHabitat() || spec.spawnWeight <= 0f) continue;
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;
            if (getNearbySpawnScore(spec, nearby) <= 0f) continue;

            candidates.add(spec);
        }

        candidates.sort((left, right) -> {
            int byNearbyScore = Float.compare(
                    getNearbySpawnScore(right, nearby), getNearbySpawnScore(left, nearby));
            if (byNearbyScore != 0) return byNearbyScore;

            int byWeight = Float.compare(right.spawnWeight, left.spawnWeight);
            if (byWeight != 0) return byWeight;

            return left.id.compareTo(right.id);
        });

        for (int i = 0; i < count && i < candidates.size(); i++) {
            FishSpec spec = candidates.get(i);

            FishLog.unlockLocationData(spec.id);
            if (givenOut != null) givenOut.add(spec.id);
            if (text != null) addRangeDataGainText(spec, text);
        }
    }

    protected static List<StarSystemAPI> getNearbyChartSystems() {
        List<StarSystemAPI> nearby = new ArrayList<>();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return nearby;

        Vector2f from = player.getLocationInHyperspace();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) continue;
            if (system.getCenter() == null) continue;
            if (Misc.getDistanceLY(from, system.getLocation())
                    > TutorialConstants.CHART_TARGET_RANGE_LY) continue;

            nearby.add(system);
        }

        return nearby;
    }

    protected static float getNearbySpawnScore(FishSpec spec, List<StarSystemAPI> systems) {
        float score = 0f;

        for (StarSystemAPI system : systems) {
            if (FishRanges.matches(spec, system, CatchImplement.POND)) score += spec.spawnWeight;
            if (FishRanges.matches(spec, system, CatchImplement.BREACH_LAMP)) score += spec.spawnWeight;
        }

        return score;
    }

    public static void giveChartsOfRarity(FishRarity rarity, int count) {
        giveChartsOfRarity(rarity, count, null, null);
    }

    protected static void giveChartsOfRarity(FishRarity rarity, int count,
                                             List<String> givenOut, TextPanelAPI text) {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (spec.rarity != rarity) continue;
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;

            picker.add(spec, 1f);
        }

        for (int i = 0; i < count && !picker.isEmpty(); i++) {
            FishSpec spec = picker.pickAndRemove();

            FishLog.unlockLocationData(spec.id);
            if (givenOut != null) givenOut.add(spec.id);
            if (text != null) addRangeDataGainText(spec, text);
        }
    }

    protected static void addRangeDataGainText(FishSpec spec, TextPanelAPI text) {
        String pattern = spec.getDisplayName();

        text.setFontSmallInsignia();
        text.addParagraph("Gained: Range data for " + pattern, Misc.getPositiveHighlightColor());
        text.highlightInLastPara(spec.rarity.color, pattern);
        text.setFontInsignia();
    }

    public static void grant(String abilityId, TextPanelAPI text) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || abilityId == null) return;

        boolean had = player.hasAbility(abilityId);

        Global.getSector().getCharacterData().addAbility(abilityId);
        Global.getSector().getCharacterData().getMemoryWithoutUpdate()
                .set("$ability:" + abilityId, true, 0);

        if (had) return;

        assignSlot(abilityId);

        if (text != null) AddRemoveCommodity.addAbilityGainText(abilityId, text);
    }

    protected static void assignSlot(String abilityId) {
        AbilitySlotsAPI slots = Global.getSector().getUIData().getAbilitySlotsAPI();
        if (slots == null) return;

        int was = slots.getCurrBarIndex();

        for (int bar = 0; bar < 5; bar++) {
            slots.setCurrBarIndex(bar);

            for (AbilitySlotAPI slot : slots.getCurrSlotsCopy()) {
                if (slot.getAbilityId() != null) continue;

                slot.setAbilityId(abilityId);
                slots.setCurrBarIndex(was);

                return;
            }
        }

        slots.setCurrBarIndex(was);
    }

    public static boolean isCarryingFisherProperty() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI memory =
                Global.getSector().getMemoryWithoutUpdate();

        if (memory.getBoolean(TutorialConstants.FISHER_PROPERTY_KEY)) return true;

        // A pre-overhaul save carrying the old breadcrumb is carrying the new fiction's assembly.
        if (memory.getBoolean(TutorialConstants.LEGACY_CARRYING_HARPOON_KEY)) {
            memory.set(TutorialConstants.FISHER_PROPERTY_KEY, true);
            memory.unset(TutorialConstants.LEGACY_CARRYING_HARPOON_KEY);
            return true;
        }

        return false;
    }

    public static void takeFisherProperty() {
        Global.getSector().getMemoryWithoutUpdate()
                .set(TutorialConstants.FISHER_PROPERTY_KEY, true);

        point();
    }

    public static void dropFisherProperty() {
        Global.getSector().getMemoryWithoutUpdate().unset(TutorialConstants.FISHER_PROPERTY_KEY);
        Global.getSector().getMemoryWithoutUpdate()
                .unset(TutorialConstants.LEGACY_CARRYING_HARPOON_KEY);
    }

    public static CampaignFleetAPI getNearestBoat() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return null;

        CampaignFleetAPI best = null;
        float bestDistance = Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            CampaignFleetAPI boat = CoreFisherSpawner.getBoat(system);
            if (boat == null) continue;

            float distance = Misc.getDistanceLY(player.getLocationInHyperspace(),
                    system.getLocation());

            if (distance < bestDistance) {
                bestDistance = distance;
                best = boat;
            }
        }

        return best;
    }

    protected static void dropNote() {
        IntelManagerAPI manager = Global.getSector().getIntelManager();
        List<IntelInfoPlugin> notes = new ArrayList<>(manager.getIntel(IntroIntel.class));
        notes.addAll(manager.getCommQueue(IntroIntel.class));

        for (IntelInfoPlugin intel : notes) {
            manager.removeIntel(intel);
        }
    }

    public static String describeTarget() {
        Target target = getTarget();
        if (target == null) return "";

        if (target.anySpecies) return "anything you can land";

        List<String> names = new ArrayList<>();
        for (String id : target.speciesIds) {
            FishSpec spec = FishSpecLoader.getFishSpec(id);
            names.add(spec == null ? "a specimen" : spec.getDisplayName());
        }

        return String.join(" and ", names);
    }

    protected static boolean isPlanted(StarSystemAPI system, String speciesId) {
        for (SectorEntityToken mote : system.getEntitiesWithTag(
                catchrelease.campaign.fish.entities.FishEntityPlugin.MOTE_TAG)) {
            if (mote.isExpired()) continue;
            if (!mote.getMemoryWithoutUpdate().getBoolean(QuestPond.QUEST_MOTE_FLAG)) continue;

            if (mote.getCustomPlugin()
                    instanceof catchrelease.campaign.fish.entities.FishEntityPlugin fish
                    && speciesId.equals(fish.getFishId())) {
                return true;
            }
        }

        return false;
    }

    protected static void plant(Target target, StarSystemAPI system, String speciesId) {
        Vector2f mark = new Vector2f(target.x, target.y);

        SectorEntityToken mote = null;

        if (target.atPond) {
            for (SectorEntityToken pond : QuestPond.getPonds(system)) {
                if (Misc.getDistance(pond.getLocation(), mark) > TutorialConstants.SPOT_SPREAD) {
                    continue;
                }

                QuestPond.claim(pond, TutorialConstants.TARGET_KEY);

                mote = QuestPond.placeMote(pond, speciesId, true,
                        TutorialConstants.TARGET_KEY);
                break;
            }
        }

        if (mote == null) {
            float across = (float) Math.random() * 360f;

            Vector2f at = new Vector2f(mark);
            Vector2f offset = Misc.getUnitVectorAtDegreeAngle(across);
            at.x += offset.x * TutorialConstants.SPOT_SPREAD;
            at.y += offset.y * TutorialConstants.SPOT_SPREAD;

            Vector2f to = new Vector2f(mark);
            to.x -= offset.x * TutorialConstants.SPOT_SPREAD;
            to.y -= offset.y * TutorialConstants.SPOT_SPREAD;

            mote = system.addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote", null,
                    new catchrelease.campaign.fish.entities.FishEntityPlugin.Params(to, speciesId));

            mote.setLocation(at.x, at.y);
        }

        if (mote == null) return;

        QuestPond.markPlanted(mote, TutorialConstants.TARGET_KEY);
    }
}
