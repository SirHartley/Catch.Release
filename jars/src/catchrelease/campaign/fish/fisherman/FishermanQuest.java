package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.FishHandoffPicker;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.jobs.camp.CampedSpot;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.map.FishPresence;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
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

        @Override
        public List<catchrelease.campaign.fish.shop.FishRequirement> getAsks() {
            List<catchrelease.campaign.fish.shop.FishRequirement> out = new java.util.ArrayList<>();
            if (quest == null || quest.speciesId == null) return out;

            catchrelease.campaign.fish.shop.FishRequirement ask =
                    new catchrelease.campaign.fish.shop.FishRequirement();

            ask.speciesId = quest.speciesId;
            out.add(ask);

            return out;
        }

        @Override
        public String getAskerName() {
            return "Chart request";
        }

        protected FishSpec getSpec() {
            return FishSpecLoader.getFishSpec(quest.speciesId);
        }

        @Override
        public String getName() {
            FishSpec spec = getSpec();

            if (quest.landed) return "Chart request: take it back";

            return "Chart request: " + (spec == null ? "a specimen" : spec.getDisplayName());
        }

        @Override
        public String getSmallDescriptionTitle() {
            return quest.landed ? "Chart request: take it back" : "Chart request";
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            LabelAPI title = info.addPara(getName(), getTitleColor(mode), 0f);
            FishRequirement.highlight(title, getAsks(), getName());

            addBulletPoints(info, mode);
        }

        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color h = Misc.getHighlightColor();
            Color tc = getBulletColorForMode(mode);

            float initPad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);

            FishSpec spec = getSpec();

            String wanted = spec == null ? "the named species" : spec.getDisplayName();
            LabelAPI wantedLine = info.addPara("Wanted: %s", initPad, tc, h, wanted);
            FishRequirement.highlight(wantedLine, getAsks(), wanted);
            info.addPara("In %s", 0f, tc, h, quest.systemName);
            info.addPara(quest.atPond ? "The mark is a rupture"
                    : "The mark is open space - lamp work", tc, 0f);

            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            FishSpec spec = getSpec();
            String name = spec == null ? "the named species" : spec.getDisplayName();
            float opad = 10f;

            FactionAPI faction = getFactionForUIColors();
            info.addImages(width, 128, opad, opad,
                    FishermanIdentity.getPortrait(0f), faction.getCrest());

            info.addPara("Chart request given by the Fisherman, affiliated with "
                            + faction.getDisplayNameWithArticle() + ".", opad,
                    faction.getBaseUIColor(),
                    faction.getDisplayNameWithArticleWithoutArticle());

            if (quest.landed) {
                LabelAPI landed = info.addPara("%s is in the hold. Take it to a fishing boat.", 10f,
                        Misc.getHighlightColor(), Misc.ucFirst(name));
                FishRequirement.highlight(landed, getAsks(), Misc.ucFirst(name));
            } else {
                LabelAPI request = info.addPara("One specimen of %s, out of %s. It is in there, and it will keep being"
                                + " in there until somebody lands it.", 10f,
                        Misc.getHighlightColor(), name, quest.systemName);
                FishRequirement.highlight(request, getAsks(), name, quest.systemName);

                info.addPara(quest.atPond
                                ? "The mark is a rupture. Drop a rod down it."
                                : "The mark is open space. Nothing will show it but the lamps.",
                        Misc.getGrayColor(), 10f);

                info.addPara("Whatever comes up will be barely holding. That is what they are"
                        + " asking about.", Misc.getGrayColor(), 10f);
            }

            if (quest.landed) {
                FishIntelMapButton.addSetAutopilot(info, width, FishingIntro.getNearestBoat());
            } else {
                FishIntelMapButton.addPlotRoute(info, width, getMapLocation(null));
            }

            addBulletPoints(info, ListInfoMode.IN_DESC);
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            if (quest.landed
                    && FishIntelMapButton.handleSetAutopilot(buttonId,
                    FishingIntro.getNearestBoat())) return;
            if (!quest.landed
                    && FishIntelMapButton.handlePlotRoute(buttonId, getMapLocation(null))) return;
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
            // once it is aboard the water is not where the player is being sent
            if (quest.landed) return null;

            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (!system.getId().equals(quest.systemId)) continue;

                if (quest.atPond) {
                    SectorEntityToken pond = QuestPond.findPondAt(system, quest.x, quest.y,
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

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.speciesId = quest.speciesId;

        boolean opened = FishHandoffPicker.show(dialog, "Select the requested specimen",
                java.util.Collections.singletonList(ask), new FishHandoffPicker.Eligibility() {
                    @Override
                    public boolean accepts(FishCatch fish) {
                        return isEligible(quest, fish);
                    }
                }, new FishHandoffPicker.Listener() {
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
        Saved quest = getActive();
        if (quest == null) return false;

        ensureIdentity(quest);

        return findAboard(quest) != null;
    }

    protected static FishCatch findAboard(Saved quest) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return null;

        for (CargoStackAPI stack : player.getCargo().getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            for (FishCatch entry : FishItems.read(data)) {
                if (isEligible(quest, entry)) return entry;
            }
        }

        return null;
    }

    public static boolean turnIn(TextPanelAPI text) {
        Saved quest = getActive();
        if (quest == null || !spend(quest)) return false;

        return finishTurnIn(quest, text);
    }

    protected static boolean turnIn(TextPanelAPI text, FishHandoffPicker.Selection selection) {
        Saved quest = getActive();
        FishCatch selected = selection == null ? null : selection.getBestForFirstAsk();
        if (quest == null || !isEligible(quest, selected) || !selection.spend()) return false;

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
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        CargoAPI cargo = player.getCargo();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            List<FishCatch> contents = FishItems.read(data);

            int found = -1;
            for (int i = 0; i < contents.size(); i++) {
                if (isEligible(quest, contents.get(i))) {
                    found = i;
                    break;
                }
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
        if (quest != null && isEligible(quest, fish)) setLanded(quest, true);
    }

    protected static boolean isEligible(Saved quest, FishCatch fish) {
        if (quest == null || fish == null) return false;
        ensureIdentity(quest);

        return quest.speciesId != null && quest.speciesId.equals(fish.speciesId)
                && quest.targetFishId.equals(fish.questTargetId)
                && fish.caughtAt >= quest.acceptedAt
                && quest.systemId != null && quest.systemId.equals(fish.caughtSystemId);
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
