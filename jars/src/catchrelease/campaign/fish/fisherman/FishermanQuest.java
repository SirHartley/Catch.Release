package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.map.FishPresence;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * Work the trade puts out: one named specimen, from one named place, and it will be there.
 * <p>
 * Not a bar job. A bar job is somebody who wants a fish; this is the people who <i>chart</i> the
 * water asking for a reading from a spot they cannot get to, and the difference shows in every part
 * of it - the target is a specific animal at a specific rupture rather than a species and a count,
 * it does not expire, and what it pays is the thing nothing else sells.
 * <p>
 * <b>The fish is put there and kept there.</b> A quest that says "go to Kumari and catch a Marlin"
 * and then leaves it to the spawn tables is a quest that can be arrived at correctly and fail for
 * an hour, so {@link Keeper} replaces the specimen whenever the player is in the system and it is
 * missing. It only ever comes up <i>barely holding</i> - see {@link #isQuestFish} - because that is
 * what is being asked about: not the animal, the water it is in.
 * <p>
 * <b>The pay is a wider shelf.</b> Credits too, but the shelf is the point: two charts on offer is
 * the floor, and the only thing that ever raises it is having done this. Each round is further out,
 * rarer, and worth more than the last.
 */
public class FishermanQuest {

    public static final String STATE_KEY = "$catchrelease_fisherQuest";
    public static final String ROUND_KEY = "$catchrelease_fisherQuestRound";

    /** Set on the specimen this quest planted, so the catch pipeline knows to force the water. */
    public static final String QUEST_FISH_FLAG = "$catchrelease_questFish";

    /** Rarity the ladder reaches for at each round, clamped to what exists. */
    public static final int[] RUNG_BY_ROUND = {1, 2, 2, 3, 3, 4};

    /** How far off the target may be, in light-years, and what it pays. */
    public static final float[] MIN_LY_BY_ROUND = {0f, 4f, 8f, 12f, 16f, 20f};
    public static final int CREDITS_BASE = 40000;
    public static final int CREDITS_PER_ROUND = 25000;

    /** Always exactly one more chart on the shelf. The ladder is in the money, not in this. */
    public static final int SLOTS_PER_JOB = 1;

    /** How often the keeper looks, and how far from the mark the specimen is put. */
    public static final float KEEP_CHECK_SECONDS = 2f;
    public static final float SPOT_SPREAD = 400f;

    /** The quest as the save knows it. */
    public static class Saved implements Serializable {
        public String speciesId;
        public String systemId;
        public String systemName;

        /** Where in the system, and whether the mark is a rupture or open water for the lamps. */
        public float x;
        public float y;
        public boolean atPond;

        public int round;
        public int credits;

        /**
         * Whether the hold has the specimen, as opposed to the request being finished.
         * <p>
         * The request finishes at the boat. Landing the thing is where the player did the part
         * they were asked to do, and nothing marked that moment: the rupture kept its mission
         * marker, the note kept saying it was still in there, and the only confirmation was flying
         * back and finding a new option waiting.
         */
        public boolean landed;
    }

    public static Saved getActive() {
        Object stored = Global.getSector().getPersistentData().get(STATE_KEY);

        return stored instanceof Saved ? (Saved) stored : null;
    }

    /** The wanted species as a name, for the rows that talk about it. */
    public static String describe(Saved quest) {
        if (quest == null) return "";

        FishSpec spec = FishSpecLoader.getFishSpec(quest.speciesId);

        return spec == null ? "the specimen" : spec.getDisplayName();
    }

    public static int getRound() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(ROUND_KEY);
    }

    //---------------------------------------------------------------- the offer

    /**
     * Rolls one without committing to it, so the conversation can describe it before it is taken.
     * <p>
     * Species first, then somewhere it could plausibly live - the fish is planted either way, but a
     * chart-reading job that sends somebody to the one system its target could never be in reads as
     * the trade not knowing its own business. Falls back to any legal system rather than to nothing.
     */
    public static Saved roll() {
        int round = getRound();

        FishSpec spec = pickSpecies(round);
        if (spec == null) return null;

        StarSystemAPI system = pickSystem(spec, round);
        if (system == null) return null;

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
            //no rupture to send them to, so it is lamp work: a marked patch of open water out
            //where the boats themselves would be
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

    /** Something the player has not landed, at the rung this round reaches for. */
    protected static FishSpec pickSpecies(int round) {
        int want = RUNG_BY_ROUND[Math.min(round, RUNG_BY_ROUND.length - 1)];

        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;

            //the rung is a target rather than a floor: one below it still counts, so a round late
            //in the ladder is not unfillable once the legendaries are all caught
            int rung = spec.rarity.ordinal();
            if (rung > want || rung < want - 1) continue;

            picker.add(spec, rung == want ? 3f : 1f);
        }

        return picker.pick();
    }

    /** Somewhere far enough out for the round, and ideally somewhere the thing could live. */
    protected static StarSystemAPI pickSystem(FishSpec spec, int round) {
        float minLY = MIN_LY_BY_ROUND[Math.min(round, MIN_LY_BY_ROUND.length - 1)];

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Vector2f from = player == null ? new Vector2f() : player.getLocationInHyperspace();

        WeightedRandomPicker<StarSystemAPI> plausible = new WeightedRandomPicker<>();
        WeightedRandomPicker<StarSystemAPI> anywhere = new WeightedRandomPicker<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) continue;
            if (system.getCenter() == null) continue;

            if (Misc.getDistanceLY(from, system.getLocation()) < minLY) continue;

            anywhere.add(system, 1f);

            if (FishPresence.livesIn(spec, system)) plausible.add(system, 1f);
        }

        StarSystemAPI pick = plausible.pick();

        return pick != null ? pick : anywhere.pick();
    }

    /** Taken. The mark goes up and the keeper starts putting the specimen back. */
    public static void accept(Saved quest) {
        if (quest == null) return;

        Global.getSector().getPersistentData().put(STATE_KEY, quest);
        Global.getSector().getIntelManager().addIntel(new QuestIntel(quest));
    }

    //---------------------------------------------------------------- the hand-in

    /** Whether the hold has the named species aboard, loose or crated. */
    public static boolean isSatisfied() {
        Saved quest = getActive();
        if (quest == null) return false;

        return findAboard(quest.speciesId) != null;
    }

    /** The specimen itself, so the hand-in can take exactly the one that was asked for. */
    protected static FishCatch findAboard(String speciesId) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return null;

        for (CargoStackAPI stack : player.getCargo().getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            for (FishCatch entry : FishItems.read(data)) {
                if (speciesId.equals(entry.speciesId)) return entry;
            }
        }

        return null;
    }

    /**
     * The specimen changes hands: money, a wider shelf, and the next round is now harder.
     * <p>
     * The fish is spent out of whatever it was in - loose stack or crate - the same careful way
     * every other spend in the mod works, so a part-emptied crate goes back as a crate.
     */
    public static boolean turnIn(TextPanelAPI text) {
        Saved quest = getActive();
        if (quest == null || !spend(quest.speciesId)) return false;

        Global.getSector().getPlayerFleet().getCargo().getCredits().add(quest.credits);

        FishermanShelf.widen(SLOTS_PER_JOB);

        Global.getSector().getMemoryWithoutUpdate().set(ROUND_KEY, quest.round + 1);

        letGo(quest);
        Global.getSector().getPersistentData().remove(STATE_KEY);

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager()
                .getIntel(QuestIntel.class)) {

            Global.getSector().getIntelManager().removeIntel(intel);
        }

        if (text != null) {
            text.addPara("Paid %s, and the shelf is one chart wider from now on.",
                    Misc.getTextColor(), Misc.getHighlightColor(),
                    Misc.getDGSCredits(quest.credits));
        }

        return true;
    }

    /** Takes one of the named species out of the hold, repacking whatever it came out of. */
    protected static boolean spend(String speciesId) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        CargoAPI cargo = player.getCargo();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            List<FishCatch> contents = FishItems.read(data);

            int found = -1;
            for (int i = 0; i < contents.size(); i++) {
                if (speciesId.equals(contents.get(i).speciesId)) {
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

            //a container's contents are its identity, so a part-spent one is a different item
            if (!contents.isEmpty()) {
                cargo.addSpecial(FishItems.repack(data.getId(), contents), 1);
            }

            return true;
        }

        return false;
    }

    /**
     * Takes this request's claim off whatever rupture it was using.
     * <p>
     * {@link #plant} claims one and nothing ever let it go, so vanilla's own mission marker - the
     * gold ring and the exclamation - stayed burned onto that rupture for the rest of the campaign,
     * pointing at a request that was over. Asked of every rupture in the system rather than the one
     * remembered, since {@link QuestPond#release} only lets go of ponds held under this key.
     */
    protected static void letGo(Saved quest) {
        if (quest == null || quest.systemId == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!system.getId().equals(quest.systemId)) continue;

            for (SectorEntityToken pond : QuestPond.getPonds(system)) {
                QuestPond.release(pond, STATE_KEY);
            }

            return;
        }
    }

    /**
     * The moment the hold answers the request: the water is done with, and the note says so.
     * <p>
     * Unbooked again if the specimen leaves the hold, so the mark and the note follow what is
     * aboard rather than what was aboard once.
     */
    protected static void setLanded(Saved quest, boolean landed) {
        if (quest == null || quest.landed == landed) return;

        quest.landed = landed;

        if (landed) letGo(quest);

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager()
                .getIntel(QuestIntel.class)) {

            ((QuestIntel) intel).sendUpdateIfPlayerHasIntel(null, false);
        }
    }

    //---------------------------------------------------------------- the fish itself

    /** Whether this mote is the one a quest planted, which is what forces the water it reads as. */
    public static boolean isQuestFish(SectorEntityToken mote) {
        return mote != null && mote.getMemoryWithoutUpdate().getBoolean(QUEST_FISH_FLAG);
    }

    /**
     * Puts the specimen back wherever it is meant to be.
     * <p>
     * At a rupture it goes in through {@link QuestPond}, which is the machinery the bar jobs already
     * use. In open water it is a bare mote at the marked spot, which is what the lamps are for -
     * nothing else out there will show it.
     */
    protected static void plant(Saved quest, StarSystemAPI system) {
        Vector2f mark = new Vector2f(quest.x, quest.y);

        SectorEntityToken mote = null;

        if (quest.atPond) {
            for (SectorEntityToken pond : QuestPond.getPonds(system)) {
                if (Misc.getDistance(pond.getLocation(), mark) > SPOT_SPREAD) continue;

                QuestPond.claim(pond, STATE_KEY);
                mote = QuestPond.placeMote(pond, quest.speciesId);
                break;
            }
        }

        if (mote == null) {
            //born on one side of the marked patch and swimming to the other, the same way the
            //boats stage their own catch - a mote swims to its target and expires there, so one
            //spawned on top of its own destination would blink out immediately
            float across = MathUtils.getRandomNumberInRange(0f, 360f);

            Vector2f at = MathUtils.getPointOnCircumference(mark, SPOT_SPREAD, across);
            Vector2f to = MathUtils.getPointOnCircumference(mark, SPOT_SPREAD, across + 180f);

            mote = system.addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote", null,
                    new FishEntityPlugin.Params(to, quest.speciesId));

            mote.setLocation(at.x, at.y);
        }

        if (mote == null) return;

        mote.getMemoryWithoutUpdate().set(QuestPond.QUEST_MOTE_FLAG, true);
        mote.getMemoryWithoutUpdate().set(QUEST_FISH_FLAG, true);

        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) fish.refreshColor();
    }

    /** Whether the planted specimen is still out there somewhere in the system. */
    protected static boolean isPlanted(StarSystemAPI system) {
        for (SectorEntityToken mote : system.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (isQuestFish(mote) && !mote.isExpired()) return true;
        }

        return false;
    }

    /**
     * Keeps the asked-for specimen in the water while the player is there to look for it.
     * <p>
     * Only while they are in the system: a mote is an entity with a plugin on it, and keeping one
     * alive in a system nobody is standing in for the rest of the campaign is upkeep bought for
     * nothing. Arriving is what puts it back, which is indistinguishable from its having been there.
     */
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

            //asked before anything about where the player is: the answer is about the hold, and
            //this is the only thing watching for it - the catch minigame has no idea what any
            //request wants
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

    //---------------------------------------------------------------- the note

    /** Where to go and what to bring back. */
    public static class QuestIntel extends BaseIntelPlugin {

        protected final Saved quest;

        public QuestIntel(Saved quest) {
            this.quest = quest;
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
            return getName();
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);

            addBulletPoints(info, mode);
        }

        /**
         * The facts under the title, the same on the list row and the open panel: the quarry,
         * the water, what the mark is, and the pay. No day count - the quest does not expire.
         */
        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color h = Misc.getHighlightColor();
            Color tc = getBulletColorForMode(mode);

            float initPad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);

            FishSpec spec = getSpec();

            info.addPara("Wanted: %s", initPad, tc, h,
                    spec == null ? "the named species" : spec.getDisplayName());
            info.addPara("In %s", 0f, tc, h, quest.systemName);
            info.addPara(quest.atPond ? "The mark is a rupture"
                    : "The mark is open space - lamp work", tc, 0f);
            info.addPara("%s and one more chart on the shelf", 0f, tc, h,
                    Misc.getDGSCredits(quest.credits));

            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            FishSpec spec = getSpec();
            String name = spec == null ? "the named species" : spec.getDisplayName();

            //the specimen itself at the top, where vanilla's missions put the poster's crest -
            //still what the note is about once it is aboard, so it stays on both branches
            info.addImage(spec == null || spec.icon == null || spec.icon.isEmpty()
                    ? FishConstants.ITEM_ICON_FALLBACK : spec.icon, width, 80f, 10f);

            if (quest.landed) {
                info.addPara("%s is in the hold. Take it to a fishing boat.", 10f,
                        Misc.getHighlightColor(), Misc.ucFirst(name));
            } else {
                info.addPara("One specimen of %s, out of %s. It is in there, and it will keep being"
                                + " in there until somebody lands it.", 10f,
                        Misc.getHighlightColor(), name, quest.systemName);

                info.addPara(quest.atPond
                                ? "The mark is a rupture. Drop a rod down it."
                                : "The mark is open space. Nothing will show it but the lamps.",
                        Misc.getGrayColor(), 10f);

                info.addPara("Whatever comes up will be barely holding. That is what they are"
                        + " asking about.", Misc.getGrayColor(), 10f);
            }

            info.addPara("Pays %s and one more chart on the shelf, permanently.", 10f,
                    Misc.getHighlightColor(), Misc.getDGSCredits(quest.credits));

            addBulletPoints(info, ListInfoMode.IN_DESC);
        }

        @Override
        public String getIcon() {
            return FishConstants.CODEX_CATEGORY_ICON;
        }

        @Override
        public String getSortString() {
            return getSortStringNewestFirst();
        }

        /** The Fisherman's colours - the request is his, whichever boat happens to carry it. */
        @Override
        public FactionAPI getFactionForUIColors() {
            return Global.getSector().getFaction(FishermanConstants.FACTION);
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_EXPLORATION);
            tags.add(Tags.INTEL_MISSIONS);
            tags.add(FishermanConstants.FACTION);

            return tags;
        }

        /** The marked rupture where there is one - it wears the mission marker, so the note should
         *  point at it rather than at the system around it. */
        @Override
        public SectorEntityToken getMapLocation(SectorMapAPI map) {
            //once it is aboard the water is not where the player is being sent
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
}
