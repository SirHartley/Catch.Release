package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.intel.FishIntelIcon;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.FishHandoffPicker;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.jobs.camp.CampedSpot;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.map.FishPresence;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FishermanQuest {

    public static final String STATE_KEY = "$catchrelease_fisherQuest";
    public static final String OFFER_KEY = "$catchrelease_fisherQuestOffer";
    public static final String ROUND_KEY = "$catchrelease_fisherQuestRound";
    public static final String LAST_COMPLETED_KEY = "$catchrelease_fisherQuestLastCompleted";
    public static final float COOLDOWN_DAYS = 90f;

    public static final String QUEST_FISH_FLAG = "$catchrelease_questFish";
    public static final String QUEST_TARGET_ID_KEY = "$catchrelease_questTargetId";

    public static final int[] RUNG_BY_ROUND = {1, 2, 2, 3, 3, 4};
    public static final float[] MIN_LY_BY_ROUND = {0f, 4f, 8f, 12f, 16f, 20f};

    public static final int CREDITS_BASE = 40000;
    public static final int CREDITS_PER_ROUND = 25000;

    public static final int SLOTS_PER_JOB = 1;
    public static final float KEEP_CHECK_SECONDS = 2f;
    public static final float SPOT_SPREAD = 400f;

    public static class Saved implements Serializable {

        public String speciesId;
        public String systemId;
        public String systemName;

        public float x;
        public float y;
        public boolean atPond;
        public int round;
        public int credits;
        public String targetFishId;
        public long acceptedAt;
        public boolean landed;
    }

    protected static class Target {

        public final FishSpec spec;
        public final StarSystemAPI system;

        public Target(FishSpec spec, StarSystemAPI system) {
            this.spec = spec;
            this.system = system;
        }
    }

    public static class Keeper implements EveryFrameScript {

        protected final IntervalUtil interval =
                new IntervalUtil(KEEP_CHECK_SECONDS, KEEP_CHECK_SECONDS);

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

            Saved quest = getActive();
            if (quest == null) return;

            setLanded(quest, isSatisfied());
            if (quest.landed) return;

            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null) return;

            if (!(player.getContainingLocation() instanceof StarSystemAPI)) return;
            StarSystemAPI system = (StarSystemAPI) player.getContainingLocation();

            if (!system.getId().equals(quest.systemId)) return;
            if (isPlanted(system)) return;

            plant(quest, system);
        }
    }

    public static class QuestIntel extends BaseIntelPlugin
            implements catchrelease.campaign.fish.shop.FishAsker {

        protected final Saved quest;

        public QuestIntel(Saved quest) {
            this.quest = quest;
        }

        protected Saved getQuest() {
            Saved active = FishermanQuest.getActive();
            return active == null ? quest : active;
        }

        protected boolean isLanded(Saved current) {
            return FishermanQuest.isSatisfied(current);
        }

        @Override
        public List<catchrelease.campaign.fish.shop.FishRequirement> getAsks() {
            return FishermanQuest.getAsks(getQuest());
        }

        @Override
        public String getAskerName() {
            return "Chart request";
        }

        protected FishSpec getSpec() {
            Saved current = getQuest();
            return current == null ? null : FishSpecLoader.getFishSpec(current.speciesId);
        }

        @Override
        public String getName() {
            Saved current = getQuest();
            FishSpec spec = getSpec();

            if (isLanded(current)) return "Chart request: take it back";

            return "Chart request: " + (spec == null ? "a specimen" : spec.getDisplayName());
        }

        @Override
        public String getSmallDescriptionTitle() {
            return isLanded(getQuest()) ? "Chart request: take it back" : "Chart request";
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            LabelAPI title = info.addPara(getName(), getTitleColor(mode), 0f);
            FishRequirement.highlight(title, getAsks(), getName());

            addBulletPoints(info, mode);
        }

        protected float addProgressLine(TooltipMakerAPI info, Color text, float pad) {
            List<FishRequirement> asks = getAsks();
            if (asks.isEmpty()) {
                info.addPara("0/1 aboard - The named species", text, pad);
                return 0f;
            }

            FishRequirement ask = asks.get(0);
            int aboard = Math.min(ask.count, FishCurrency.count(ask));
            String progress = ask.describeProgress(aboard);
            LabelAPI line = info.addPara(progress, text, pad);
            FishRequirement.highlight(line, java.util.Collections.singletonList(ask), progress,
                    aboard + "/" + ask.count);

            return 0f;
        }

        protected float addDestinationLine(TooltipMakerAPI info, Saved current, Color text,
                                           float pad) {
            if (current.atPond) {
                info.addPara("Marked rupture in %s", pad, text, Misc.getHighlightColor(),
                        current.systemName);
            } else {
                info.addPara("Open space in %s", pad, text, Misc.getHighlightColor(),
                        current.systemName);
            }

            return 0f;
        }

        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Saved current = getQuest();
            if (current == null) return;

            Color text = getBulletColorForMode(mode);
            float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);
            pad = addProgressLine(info, text, pad);

            if (isLanded(current)) {
                info.addPara("Return to the nearest fishing boat", text, pad);
            } else {
                pad = addDestinationLine(info, current, text, pad);
                if (current.atPond) {
                    info.addPara("ROD/LYNE at the marked rupture", text, pad);
                } else {
                    info.addPara("Breach Lights and Harpoon only", text, pad);
                }
            }

            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            Saved current = getQuest();
            if (current == null) return;

            float opad = 10f;
            Color text = getBulletColorForMode(ListInfoMode.IN_DESC);

            FactionAPI faction = getFactionForUIColors();
            info.addImages(width, 128, opad, opad,
                    FishermanIdentity.getPortrait(0f), faction.getCrest());

            info.addPara("Chart request given by the Fisherman, affiliated with "
                            + faction.getDisplayNameWithArticle() + ".", opad,
                    faction.getBaseUIColor(),
                    faction.getDisplayNameWithArticleWithoutArticle());

            info.addPara("The Fisherman is waiting for the requested specimen.", opad);

            info.addPara("What is wanted:", opad);
            bullet(info);
            addProgressLine(info, text, 0f);
            unindent(info);

            info.addPara(isLanded(current) ? "Return to:" : "Where and how:", opad);
            bullet(info);
            if (isLanded(current)) {
                info.addPara("The nearest fishing boat", text, 0f);
            } else {
                addDestinationLine(info, current, text, 0f);
                if (current.atPond) {
                    info.addPara("Use the ROD/LYNE at the marked rupture", text, 0f);
                } else {
                    info.addPara("Use the Breach Lights, then land it with the Harpoon",
                            text, 0f);
                }
            }
            unindent(info);

            if (isLanded(current)) {
                FishIntelMapButton.addSetAutopilot(info, width, FishingIntro.getNearestBoat());
            } else {
                FishIntelMapButton.addPlotRoute(info, width, getMapLocation(null));
            }

            //addBulletPoints(info, ListInfoMode.IN_DESC);
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            Saved current = getQuest();
            if (isLanded(current)
                    && FishIntelMapButton.handleSetAutopilot(buttonId,
                    FishingIntro.getNearestBoat())) return;
            if (!isLanded(current)
                    && FishIntelMapButton.handlePlotRoute(buttonId, getMapLocation(null))) return;
            super.buttonPressConfirmed(buttonId, ui);
        }

        @Override
        public String getIcon() {
            Saved current = getQuest();
            return FishIntelIcon.get(current != null && current.atPond
                    ? CatchImplement.POND : CatchImplement.BREACH_LAMP);
        }

        @Override
        public String getSortString() {
            return getSortStringNewestFirst();
        }

        @Override
        public FactionAPI getFactionForUIColors() {
            return Global.getSector().getFaction(FishermanConstants.FACTION);
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_EXPLORATION);
            tags.add(Tags.INTEL_MISSIONS);
            tags.add(Tags.INTEL_ACCEPTED);
            tags.add(FishermanConstants.FACTION);

            return tags;
        }

        @Override
        public SectorEntityToken getMapLocation(SectorMapAPI map) {
            Saved current = getQuest();
            if (current == null) return null;

            // once it is aboard the water is not where the player is being sent
            if (isLanded(current)) return null;

            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (!system.getId().equals(current.systemId)) continue;

                if (current.atPond) {
                    SectorEntityToken pond = QuestPond.findPondAt(system, current.x, current.y,
                            SPOT_SPREAD);

                    if (pond != null) return pond;
                }

                return system.getHyperspaceAnchor();
            }

            return null;
        }
    }

    public static Saved getActive() {
        Object stored = Global.getSector().getPersistentData().get(STATE_KEY);

        return stored instanceof Saved ? (Saved) stored : null;
    }

    public static Saved getOffer() {
        Object stored = Global.getSector().getPersistentData().get(OFFER_KEY);

        return stored instanceof Saved ? (Saved) stored : null;
    }

    public static String describe(Saved quest) {
        if (quest == null) return "";

        FishSpec spec = FishSpecLoader.getFishSpec(quest.speciesId);

        return spec == null ? "the specimen" : spec.getDisplayName();
    }

    public static int getRound() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(ROUND_KEY);
    }

    public static boolean isAvailable() {
        if (getActive() != null) return false;

        Object last = Global.getSector().getPersistentData().get(LAST_COMPLETED_KEY);
        return !(last instanceof Long) || Global.getSector().getClock()
                .getElapsedDaysSince((Long) last) >= COOLDOWN_DAYS;
    }

    public static Saved roll() {
        if (!isAvailable()) return null;

        int round = getRound();

        Target target = pickTarget(round);
        if (target == null) return null;

        FishSpec spec = target.spec;
        StarSystemAPI system = target.system;

        Saved quest = new Saved();
        quest.speciesId = spec.id;
        quest.systemId = system.getId();
        quest.systemName = system.getName();
        quest.round = round;
        quest.credits = CREDITS_BASE + CREDITS_PER_ROUND * round;

        SectorEntityToken pond = QuestPond.findFreePond(system);

        if (pond != null) {
            quest.atPond = true;
            quest.x = pond.getLocation().x;
            quest.y = pond.getLocation().y;
        } else {
            Vector2f at = MathUtils.getPointOnCircumference(OuterReaches.center(system),
                    MathUtils.getRandomNumberInRange(OuterReaches.getInnerLimit(system),
                            OuterReaches.getOuterLimit(system)),
                    MathUtils.getRandomNumberInRange(0f, 360f));

            quest.atPond = false;
            quest.x = at.x;
            quest.y = at.y;
        }

        return quest;
    }

    public static Saved getOrRollOffer() {
        Saved offer = getOffer();
        if (offer != null) return offer;

        offer = roll();
        if (offer != null) Global.getSector().getPersistentData().put(OFFER_KEY, offer);

        return offer;
    }

    protected static Target pickTarget(int round) {
        int want = RUNG_BY_ROUND[Math.min(round, RUNG_BY_ROUND.length - 1)];

        float minLY = MIN_LY_BY_ROUND[Math.min(round, MIN_LY_BY_ROUND.length - 1)];
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Vector2f from = player == null ? new Vector2f() : player.getLocationInHyperspace();

        WeightedRandomPicker<Target> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;

            int rung = spec.rarity.rank;
            float rarityWeight = rung == want ? 3f : rung == want - 1 ? 1f : 0.25f;

            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (!isEligibleSystem(system, from, minLY)) continue;
                if (!FishPresence.livesIn(spec, system)) continue;
                if (!isChartRequest(spec, system)) continue;

                picker.add(new Target(spec, system), rarityWeight);
            }
        }

        return picker.pick();
    }

    protected static boolean isEligibleSystem(StarSystemAPI system, Vector2f from, float minLY) {
        if (system == null) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;
        if (system.getCenter() == null) return false;

        return Misc.getDistanceLY(from, system.getLocation()) >= minLY;
    }

    protected static boolean isChartRequest(FishSpec spec, StarSystemAPI system) {
        if (spec.difficulty >= 65f) return true;
        if (spec.rarity == FishRarity.RARE || spec.rarity == FishRarity.EPIC) return true;
        if (Misc.getPulsarInSystem(system) != null) return true;

        for (SectorEntityToken pond : QuestPond.getPonds(system)) {
            if (spec.id.equals(CampedSpot.getCampedSpecies(pond))) return true;
        }

        return false;
    }

    public static void accept(Saved quest) {
        if (quest == null) return;

        ensureIdentity(quest);

        Global.getSector().getPersistentData().put(STATE_KEY, quest);
        Global.getSector().getPersistentData().remove(OFFER_KEY);
        FishIntelNotifications.queue(new QuestIntel(quest));
    }

    public static boolean showTurnInPicker(final InteractionDialogAPI dialog,
                                           final Map<String, MemoryAPI> memoryMap) {
        Saved quest = getActive();
        if (quest == null) return false;

        FishRequirement ask = getAsk(quest);
        if (ask == null) return false;

        boolean opened = FishHandoffPicker.show(dialog, "Select the requested specimen",
                java.util.Collections.singletonList(ask), new FishHandoffPicker.Listener() {
                    @Override
                    public void picked(FishHandoffPicker.Selection selection) {
                        if (turnIn(dialog == null ? null : dialog.getTextPanel(), selection)) {
                            MemoryAPI local = memoryMap == null
                                    ? null : memoryMap.get(MemKeys.LOCAL);
                            if (local != null) {
                                local.set("$option", "catchrelease_workTurnIn", 0f);
                                local.set("$catchreleaseWorkHandoffPaid", true, 0f);
                            }

                            FireBest.fire(null, dialog, memoryMap, "DialogOptionSelected");
                        } else {
                            FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
                        }
                    }

                    @Override
                    public void cancelled() {
                        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
                    }
                });

        if (!opened) FireAll.fire(null, dialog, memoryMap, "PopulateOptions");

        return opened;
    }

    public static boolean isSatisfied() {
        return isSatisfied(getActive());
    }

    protected static boolean isSatisfied(Saved quest) {
        FishRequirement ask = getAsk(quest);

        return ask != null && FishCurrency.count(ask) >= ask.count;
    }

    protected static List<FishRequirement> getAsks(Saved quest) {
        FishRequirement ask = getAsk(quest);

        if (ask == null) return java.util.Collections.emptyList();
        return java.util.Collections.singletonList(ask);
    }

    protected static FishRequirement getAsk(Saved quest) {
        if (quest == null || quest.speciesId == null) return null;
        ensureIdentity(quest);

        FishRequirement ask = new FishRequirement();
        ask.speciesId = quest.speciesId;
        ask.lowCoherence = true;
        ask.minCaughtAt = quest.acceptedAt;
        ask.caughtSystemId = quest.systemId;
        ask.questTargetId = quest.targetFishId;

        return ask;
    }

    public static boolean turnIn(TextPanelAPI text) {
        Saved quest = getActive();
        if (quest == null || !spend(quest)) return false;

        return finishTurnIn(quest, text);
    }

    protected static boolean turnIn(TextPanelAPI text, FishHandoffPicker.Selection selection) {
        Saved quest = getActive();
        FishRequirement ask = getAsk(quest);
        FishCatch selected = selection == null ? null : selection.getBestForFirstAsk();
        if (quest == null || ask == null || !ask.matches(selected) || !selection.spend()) {
            return false;
        }

        return finishTurnIn(quest, text);
    }

    protected static boolean finishTurnIn(Saved quest, TextPanelAPI text) {
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(quest.credits);

        FishermanShelf.widen(SLOTS_PER_JOB);

        Global.getSector().getMemoryWithoutUpdate().set(ROUND_KEY, quest.round + 1);
        Global.getSector().getPersistentData().put(LAST_COMPLETED_KEY,
                Global.getSector().getClock().getTimestamp());

        letGo(quest);
        Global.getSector().getPersistentData().remove(STATE_KEY);

        for (IntelInfoPlugin intel : new ArrayList<>(Global.getSector().getIntelManager()
                .getIntel(QuestIntel.class))) {
            Global.getSector().getIntelManager().removeIntel(intel);
        }

        if (text != null) {
            text.setFontSmallInsignia();
            String credits = Misc.getDGSCredits(quest.credits);
            text.addPara("Gained " + credits, Misc.getPositiveHighlightColor());
            text.highlightLastInLastPara(credits, Misc.getHighlightColor());
            text.addPara("The Fisherman now stocks an additional range data entry.",
                    Misc.getPositiveHighlightColor());
            text.setFontInsignia();
        }

        return true;
    }

    protected static boolean spend(Saved quest) {
        FishRequirement ask = getAsk(quest);
        return ask != null && FishCurrency.spend(ask);
    }

    protected static void letGo(Saved quest) {
        if (quest == null) return;

        QuestPond.releaseAll(STATE_KEY);
        QuestPond.clearMotes(STATE_KEY);
    }

    protected static void setLanded(Saved quest, boolean landed) {
        if (quest == null || quest.landed == landed) return;

        quest.landed = landed;

        if (landed) letGo(quest);

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager()
                .getIntel(QuestIntel.class)) {
            FishIntelNotifications.update((QuestIntel) intel, null);
        }
    }

    public static boolean isQuestFish(SectorEntityToken mote) {
        return mote != null && mote.getMemoryWithoutUpdate().getBoolean(QUEST_FISH_FLAG);
    }

    public static void markCatch(FishCatch fish, SectorEntityToken mote) {
        if (fish == null || !isQuestFish(mote)) return;

        Saved quest = getActive();
        if (quest == null) return;
        ensureIdentity(quest);

        String targetId = mote.getMemoryWithoutUpdate().getString(QUEST_TARGET_ID_KEY);
        if (!quest.targetFishId.equals(targetId)) return;

        fish.questTargetId = targetId;
        fish.caughtAt = Global.getSector().getClock().getTimestamp();
        fish.caughtSystemId = mote.getContainingLocation() == null
                ? null : mote.getContainingLocation().getId();
    }

    public static void onCatchStored(FishCatch fish) {
        Saved quest = getActive();
        FishRequirement ask = getAsk(quest);
        if (ask != null && ask.matches(fish)) setLanded(quest, true);
    }

    protected static void ensureIdentity(Saved quest) {
        if (quest.targetFishId == null || quest.targetFishId.isEmpty()) {
            quest.targetFishId = Misc.genUID();
        }
        if (quest.acceptedAt <= 0L) {
            quest.acceptedAt = Global.getSector().getClock().getTimestamp();
        }
    }

    protected static void plant(Saved quest, StarSystemAPI system) {
        Vector2f mark = new Vector2f(quest.x, quest.y);

        SectorEntityToken mote = null;

        if (quest.atPond) {
            for (SectorEntityToken pond : QuestPond.getPonds(system)) {
                if (Misc.getDistance(pond.getLocation(), mark) > SPOT_SPREAD) continue;

                QuestPond.claim(pond, STATE_KEY);
                mote = QuestPond.placeMote(pond, quest.speciesId, STATE_KEY);
                break;
            }
        }

        if (mote == null) {
            float across = MathUtils.getRandomNumberInRange(0f, 360f);

            Vector2f at = MathUtils.getPointOnCircumference(mark, SPOT_SPREAD, across);
            Vector2f to = MathUtils.getPointOnCircumference(mark, SPOT_SPREAD, across + 180f);

            mote = system.addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote", null,
                    new FishEntityPlugin.Params(to, quest.speciesId));

            mote.setLocation(at.x, at.y);
        }

        if (mote == null) return;

        ensureIdentity(quest);
        mote.getMemoryWithoutUpdate().set(QUEST_FISH_FLAG, true);
        mote.getMemoryWithoutUpdate().set(QUEST_TARGET_ID_KEY, quest.targetFishId);

        QuestPond.markPlanted(mote, STATE_KEY);
    }

    protected static boolean isPlanted(StarSystemAPI system) {
        for (SectorEntityToken mote : system.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (isQuestFish(mote) && !mote.isExpired()) return true;
        }

        return false;
    }
}
