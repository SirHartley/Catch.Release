package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Learning to fish, in six lessons and one shortcut.
 * <p>
 * Entirely detached from the trade: the boats run whether or not any of this has happened. What it
 * gates is <b>equipment</b>, and through that everything downstream - the shop only shelves gear you
 * own, and no job of any kind is offered until the first errand is done. See {@link #isOpenForWork}.
 * <p>
 * <b>The ladder.</b> Each rung hands something over and asks for one thing back, and the asking is
 * always the same shape: a specimen, from a place, brought to a boat.
 * <ol start="0">
 * <li>{@link #UNSTARTED} - nothing. A rating, a hulk, or a boat that heads you off starts it.
 * <li>{@link #POINTED} - told where a boat is.
 * <li>{@link #RODDED} - given the rod. Fetch one fish out of a rupture nearby.
 * <li>{@link #FISH_ONE} - drones and ghosts explained. Fetch one from thinner water further out.
 * <li>{@link #FISH_TWO} - lamps, harpoon and ledger handed over, shop opens. Fetch one <i>through a
 * lamp, on a line</i>.
 * <li>{@link #FISH_THREE} - the rest of the shop, two free charts, and fetch both of those.
 * <li>{@link #DONE} - the chart counter opens, with four more charts in it.
 * </ol>
 * <b>The shortcut.</b> A campaign is not the first time somebody has done this - the file at
 * {@link TutorialConstants#SEEN_FILE} outlives every save, and once it exists the conversation
 * offers a way to say so. It is not questioned. See {@link #skip}.
 * <p>
 * Not a word of what any of this <i>says</i> is in here; the sheet owns that. This owns the state,
 * the targets and the grants - see {@code catchrelease.dialogue.rules.CatchReleaseCMD}.
 */
public class FishingIntro {

    public static final int UNSTARTED = 0;
    public static final int POINTED = 1;
    public static final int RODDED = 2;
    public static final int FISH_ONE = 3;
    public static final int FISH_TWO = 4;
    public static final int FISH_THREE = 5;
    public static final int DONE = 6;

    public static int getStage() {
        return Global.getSector().getMemoryWithoutUpdate().getInt(TutorialConstants.STAGE_KEY);
    }

    public static boolean isAtLeast(int stage) {
        return getStage() >= stage;
    }

    protected static void setStage(int stage) {
        if (getStage() >= stage) return;

        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.STAGE_KEY, stage);
    }

    /**
     * Whether the world is allowed to offer the player work yet.
     * <p>
     * Nothing - no bar job, no fleet job - turns up until the first errand is behind them. A player
     * who has been handed a rod ten minutes ago and is being asked for three legendaries out of the
     * abyss has been handed the wrong game.
     */
    public static boolean isOpenForWork() {
        return isAtLeast(FISH_ONE);
    }

    /**
     * Whether the introduction has opened a given shelf of the shop yet.
     * <p>
     * The rod's shelves come with the shop itself. The lamp and harpoon shelves are held back one
     * further rung: the gear and the errand to use it in arrive together, and the upgrades for it
     * are what finishing that errand buys - see {@code ShopGroup.isUnlocked}. Anything past the
     * ladder is open, which is every campaign that skipped and every one that finished.
     */
    public static boolean isShelfOpen(catchrelease.campaign.fish.shop.ShopGroup shelf) {
        if (shelf == null) return true;

        boolean deep = shelf == catchrelease.campaign.fish.shop.ShopGroup.SEARCHLIGHTS
                || shelf == catchrelease.campaign.fish.shop.ShopGroup.SEARCHLIGHT_RIG
                || shelf == catchrelease.campaign.fish.shop.ShopGroup.HARPOON
                || shelf == catchrelease.campaign.fish.shop.ShopGroup.HARPOON_TIPS;

        return !deep || isAtLeast(FISH_THREE);
    }

    /** Whether a rig is in the player's hands, for anything that gates on owning it. */
    public static boolean hasGear(String abilityId) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        return player != null && player.hasAbility(abilityId);
    }

    //---------------------------------------------------------------- the shortcut

    /** Written the first time anybody finishes or skips, and read by every campaign after. */
    public static void rememberSeen() {
        try {
            Global.getSettings().writeTextFileToCommon(TutorialConstants.SEEN_FILE, "1");
        } catch (Exception e) {
            //a shortcut nobody can offer is a slower first hour, not a broken campaign
            Global.getLogger(FishingIntro.class).warn("Could not record the tutorial as seen", e);
        }
    }

    public static boolean hasSeenBefore() {
        try {
            String seen = Global.getSettings().readTextFileFromCommon(TutorialConstants.SEEN_FILE);

            return seen != null && !seen.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Straight to the end: every rig, the whole shop, six charts, and no money.
     * <p>
     * Not questioned, in the sheet or here. Somebody who says the breach is calling them is either
     * telling the truth or has done this before, and from where the Fisherman is standing those are
     * the same answer.
     */
    public static void skip(TextPanelAPI text) {
        grant(TutorialConstants.ROD, text);
        for (String ability : TutorialConstants.DEEP_GEAR) grant(ability, text);
        grant(TutorialConstants.OUTFITTER, text);

        //Exactly what the long route pays: its two teaching charts, then 2/1/1 by rarity.
        giveCharts(TutorialConstants.FREE_COMMONS, null);
        for (int rung = 0; rung < TutorialConstants.GRADUATION_CHARTS.length; rung++) {
            giveChartsOfRarity(FishRarity.values()[rung],
                    TutorialConstants.GRADUATION_CHARTS[rung]);
        }

        clearTarget();
        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.STAGE_KEY, DONE);
        Global.getSector().getMemoryWithoutUpdate().unset(TutorialConstants.DEEP_HANDOFF_KEY);

        dropNote();
        rememberSeen();
    }

    //---------------------------------------------------------------- the rungs

    /** Told where a boat is. Idempotent - every source calls it and one note is enough. */
    public static void point() {
        if (isAtLeast(POINTED)) return;

        setStage(POINTED);

        Global.getSector().getIntelManager().addIntel(new IntroIntel());
    }

    /** Lesson one: the rod, and one fish out of the nearest rupture. */
    public static void giveRod(TextPanelAPI text) {
        point();
        setStage(RODDED);

        grant(TutorialConstants.ROD, text);

        setTarget(rollTarget(RODDED));
    }

    /** Lesson two: what the drones are, what the fish are, and thinner water to try it in. */
    public static void sendOut(TextPanelAPI text) {
        setStage(FISH_ONE);

        setTarget(rollTarget(FISH_ONE));
    }

    /**
     * Opens the basic outfitter after the second catch, before the deep rigs change hands.
     * <p>
     * Kept separate from {@link #giveDeepGear(TextPanelAPI)} because the dialogue deliberately
     * introduces the ledger while it contains only the ROD/LINE shelves. The pending flag makes an
     * interrupted conversation resume at the handoff instead of falling back to an empty stage-three
     * reminder after the catch has already been taken.
     */
    public static void giveOutfitter(TextPanelAPI text) {
        grant(TutorialConstants.OUTFITTER, text);
        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.DEEP_HANDOFF_KEY, true);
    }

    /** Whether the second-catch conversation still owes the player the deep rigs. */
    public static boolean isDeepHandoffPending() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.DEEP_HANDOFF_KEY);
    }

    /** Lesson three: the deep gear, and something to point it at. */
    public static void giveDeepGear(TextPanelAPI text) {
        setStage(FISH_TWO);

        for (String ability : TutorialConstants.DEEP_GEAR) grant(ability, text);
        grant(TutorialConstants.OUTFITTER, text);
        Global.getSector().getMemoryWithoutUpdate().unset(TutorialConstants.DEEP_HANDOFF_KEY);

        setTarget(rollTarget(FISH_TWO));
    }

    /** Lesson four: the rest of the shop, two charts to read, and both of their fish wanted. */
    public static void giveCharts(TextPanelAPI text) {
        setStage(FISH_THREE);

        List<String> given = new ArrayList<>();
        giveCharts(TutorialConstants.FREE_COMMONS, given);

        Target target = new Target();
        target.stage = FISH_THREE;
        target.speciesIds = given;

        setTarget(target);
    }

    /** The last rung: the counter opens and the graduation package goes in the log. */
    public static void finish(TextPanelAPI text) {
        setStage(DONE);

        clearTarget();
        dropNote();

        for (int rung = 0; rung < TutorialConstants.GRADUATION_CHARTS.length; rung++) {
            giveChartsOfRarity(FishRarity.values()[rung], TutorialConstants.GRADUATION_CHARTS[rung]);
        }

        rememberSeen();
    }

    //---------------------------------------------------------------- the targets

    /** What the current errand wants, and where it is. */
    public static class Target implements Serializable {
        public int stage;
        public List<String> speciesIds = new ArrayList<>();

        public String systemId;
        public String systemName;

        public float x;
        public float y;
        public boolean atPond;

        /** Set on the last rung that cares: it has to come up through a lamp, on a line. */
        public boolean needsDeepGear;

        /**
         * Set on the first rung: any fish answers it, whatever species came up.
         * <p>
         * A specimen is still planted at the mark so there is guaranteed to be something down
         * there, but somebody thirty seconds into owning a rod should not be able to fail their
         * first cast by landing the wrong animal.
         */
        public boolean anySpecies;

        /**
         * Whether the hold has answered this, which is a different question from the errand being
         * over.
         * <p>
         * The errand ends at the boat - that is the shape of the whole ladder - but landing the
         * thing is the moment the player did the part they were asked to do, and until this
         * existed nothing at all happened at that moment. The mark stayed on the rupture, the note
         * still read as an errand outstanding, and the only way to find out it had worked was to
         * fly back and see a new option. Recorded on the target rather than in sector memory
         * because it is a fact about this errand and should die with it.
         */
        public boolean landed;
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

        //the outgoing errand lets go of its water before the incoming one is stored, or the mark
        //on the old rupture outlives every reference to what put it there
        letGo(getTarget());

        Global.getSector().getPersistentData().put(TutorialConstants.TARGET_KEY, target);
    }

    protected static void clearTarget() {
        letGo(getTarget());

        Global.getSector().getPersistentData().remove(TutorialConstants.TARGET_KEY);
    }

    /**
     * Takes this errand's claim off whatever rupture it was using.
     * <p>
     * Every other user of {@link QuestPond} pairs its claim with a release; the introduction only
     * ever claimed. What that leaves is vanilla's own mission marker - the gold ring and the
     * exclamation - burned onto a rupture for the rest of the campaign, on every rupture the
     * ladder ever used, pointing at nothing.
     * <p>
     * Asked of every rupture in the sector rather than of the one system the errand remembers. The
     * claim is named, so asking widely costs nothing and cannot let go of anybody else's water -
     * and the errand's remembered place is not reliably where its claim ended up. A rung with no
     * system at all returned here before doing anything; an errand replaced while the player stood
     * somewhere else pointed the sweep at the wrong system. Both left a marker behind.
     */
    protected static void letGo(Target target) {
        if (target == null) return;

        QuestPond.releaseAll(TutorialConstants.TARGET_KEY);
        QuestPond.clearMotes(TutorialConstants.TARGET_KEY);
    }

    /**
     * The moment the hold answers the errand: the water is done with, and the note says so.
     * <p>
     * Booked once, and unbooked if the specimen leaves the hold again - sold, or spent on
     * something else - so the mark and the note follow what is actually aboard rather than what
     * was aboard once.
     */
    protected static void setLanded(Target target, boolean landed) {
        if (target == null || target.landed == landed) return;

        target.landed = landed;

        if (landed) letGo(target);

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager()
                .getIntel(IntroIntel.class)) {

            ((IntroIntel) intel).sendUpdateIfPlayerHasIntel(null, false);
        }
    }

    /** Whether the current errand has been answered by something in the hold. */
    public static boolean isLanded() {
        Target target = getTarget();

        return target != null && target.landed;
    }

    /**
     * Picks the errand for a rung.
     * <p>
     * The first is deliberately underfoot - a rupture in the system the boat is standing in, because
     * somebody who has owned a rod for thirty seconds should not be reading a star map. The second
     * moves out a little and asks for thinner water, which is the first time coherence is something
     * the player has to look at rather than be told about. The third needs the lamps, so it is open
     * water by definition.
     */
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

        FishSpec spec = pickSpecies(stage);
        if (spec == null) return null;
        target.speciesIds.add(spec.id);

        //the lamp lesson is open water by definition; the others prefer a rupture and fall back
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

    /** Near the core, and thinner than where they started - which is the whole lesson. */
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

    /** Commons all the way up: the ladder is in the gear and the water, not in the quarry. */
    protected static FishSpec pickSpecies(int stage) {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (spec.rarity.ordinal() > FishRarity.UNCOMMON.ordinal()) continue;

            picker.add(spec, spec.rarity == FishRarity.COMMON ? 4f : 1f);
        }

        return picker.pick();
    }

    //---------------------------------------------------------------- the hand-in

    /**
     * Whether the hold has what the current rung asked for.
     * <p>
     * The lamp lesson checks <i>how</i> as well as what: a specimen out of a pond does not answer a
     * question about the lamps, and the fields to check it with are on every catch already.
     */
    public static boolean isTargetMet() {
        Target target = getTarget();
        if (target == null) return false;

        if (target.anySpecies) return findAny(target.needsDeepGear) != null;

        for (String speciesId : target.speciesIds) {
            if (find(speciesId, target.needsDeepGear) == null) return false;
        }

        return true;
    }

    /** The first thing aboard that answers, whatever it is - see {@link Target#anySpecies}. */
    protected static FishCatch findAny(boolean deepGear) {
        return find(null, deepGear);
    }

    /** A named species, or any at all when {@code speciesId} is null. */
    protected static FishCatch find(String speciesId, boolean deepGear) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return null;

        for (CargoStackAPI stack : player.getCargo().getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            for (FishCatch entry : FishItems.read(data)) {
                if (speciesId != null && !speciesId.equals(entry.speciesId)) continue;

                if (deepGear && (entry.implement != CatchImplement.BREACH_LAMP
                        || entry.method != FishLogEntry.Method.HARPOON)) {

                    continue;
                }

                return entry;
            }
        }

        return null;
    }

    /** Takes everything the rung asked for out of the hold, repacking whatever it came out of. */
    public static boolean takeTarget() {
        Target target = getTarget();
        if (target == null || !isTargetMet()) return false;

        if (target.anySpecies) {
            FishCatch any = findAny(target.needsDeepGear);
            if (any != null) spend(any.speciesId, target.needsDeepGear);
        } else {
            for (String speciesId : target.speciesIds) spend(speciesId, target.needsDeepGear);
        }

        clearTarget();

        return true;
    }

    protected static boolean spend(String speciesId, boolean deepGear) {
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

            //a container's contents are its identity, so a part-spent one is a different item
            if (!contents.isEmpty()) {
                cargo.addSpecial(FishItems.repack(data.getId(), contents), 1);
            }

            return true;
        }

        return false;
    }

    //---------------------------------------------------------------- grants

    /** Charts on the house, at the bottom rung, recording what was given if anybody is listening. */
    public static void giveCharts(int count, List<String> givenOut) {
        giveChartsOfRarity(FishRarity.COMMON, count, givenOut);
    }

    public static void giveChartsOfRarity(FishRarity rarity, int count) {
        giveChartsOfRarity(rarity, count, null);
    }

    protected static void giveChartsOfRarity(FishRarity rarity, int count, List<String> givenOut) {
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
        }
    }

    /**
     * Puts an ability in the player's hands and on the bar.
     * <p>
     * Vanilla's own {@code AddAbility} arithmetic: an ability that lands in the character sheet but
     * on no hotbar slot is one the player has to go and find in a menu they have never opened.
     */
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

    //---------------------------------------------------------------- odds and ends

    public static boolean isCarryingFisherProperty() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI memory =
                Global.getSector().getMemoryWithoutUpdate();

        if (memory.getBoolean(TutorialConstants.FISHER_PROPERTY_KEY)) return true;

        //A pre-overhaul save carrying the old breadcrumb is carrying the new fiction's assembly.
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

    /** The nearest standing boat to the player right now, by hyperspace distance. */
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
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager()
                .getIntel(IntroIntel.class)) {

            Global.getSector().getIntelManager().removeIntel(intel);
        }
    }

    /** What the current errand wants, as a sentence. Read by the sheet and by the note. */
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

    //---------------------------------------------------------------- keeping the target there

    /**
     * Keeps the errand's specimen in the water while the player is there to look for it.
     * <p>
     * The same argument as the chart requests': a lesson that can be arrived at correctly and fail
     * for an hour because the spawn tables did not oblige is not a lesson, it is a wait. Only while
     * the player is in the system - a mote in a system nobody is standing in is upkeep bought for
     * nothing, and arriving is what puts it back, which from the outside is indistinguishable from
     * its having been there all along.
     */
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

            //asked before anything about where the player is, since the answer is about the hold
            //and travels with it. This is also the only thing that notices a catch at all - the
            //minigame has no idea what the introduction wants
            setLanded(target, isTargetMet());
            if (target.landed) return;

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

    /** Whether this errand's specimen is still out there somewhere in the system. */
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

    /**
     * Puts one back where the errand says it should be.
     * <p>
     * At a rupture it goes in through {@link QuestPond}, the machinery the bar jobs already use.
     * In open water it is spawned on one side of the marked patch aiming at the other - a mote
     * swims to its target and expires there, so one spawned on its own destination blinks out
     * immediately.
     */
    protected static void plant(Target target, StarSystemAPI system, String speciesId) {
        Vector2f mark = new Vector2f(target.x, target.y);

        SectorEntityToken mote = null;

        if (target.atPond) {
            for (SectorEntityToken pond : QuestPond.getPonds(system)) {
                if (Misc.getDistance(pond.getLocation(), mark) > TutorialConstants.SPOT_SPREAD) {
                    continue;
                }

                QuestPond.claim(pond, TutorialConstants.TARGET_KEY);

                //holds, unlike every other errand's fish. Somebody being taught what a rupture is
                //should find the thing they were sent for still in it - "elusive" is a difficulty,
                //and the first catch is not the place to be teaching difficulty
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

    //---------------------------------------------------------------- the note

    /** One entry for the whole ladder, retitled at each rung. */
    public static class IntroIntel extends BaseIntelPlugin
            implements catchrelease.campaign.fish.shop.FishAsker {

        /**
         * The rung's quarry, so a specimen the ladder sent the player after wears the same mark a
         * job's would - see {@link catchrelease.campaign.fish.shop.FishAsker}.
         * <p>
         * The gear clause rides along for the lamp rung, which is the one rung where the wrong
         * specimen of the right species does not answer. It costs nothing on the species screens -
         * {@link FishRequirement#couldBeSatisfiedBy} only tests what a species decides - and it is
         * what keeps the hold from marking a rod-caught one as spent for.
         * <p>
         * Nothing at all on the first rung. Anything the player can land answers it, and a
         * requirement that matches everything would put a mark on every row of the codex.
         */
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

        /** The ladder, not the rung - the title changes at every step and this must not. */
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
            return getName();
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);

            addBulletPoints(info, mode);
        }

        /**
         * The facts under the title, the same on the list row and the open panel: what is wanted,
         * where, and what gear the water will answer to. No day count - the ladder has no clock.
         */
        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color h = Misc.getHighlightColor();
            Color tc = getBulletColorForMode(mode);

            float initPad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);

            Target target = getTarget();

            if (target == null) {
                SectorEntityToken at = getMapLocation(null);
                if (at != null && at.getContainingLocation() != null) {
                    info.addPara("Nearest boat: %s", initPad, tc, h,
                            at.getContainingLocation().getName());
                }
            } else {
                info.addPara("Wanted: %s", initPad, tc, h, describeTarget());
                info.addPara("In %s", 0f, tc, h, target.systemName);

                if (target.needsDeepGear) {
                    info.addPara("Breach lamp and harpoon line only", tc, 0f);
                } else if (target.atPond) {
                    info.addPara("The mark is a rupture", tc, 0f);
                }
            }

            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            Target target = getTarget();

            if (target == null) {
                if (isCarryingFisherProperty()) {
                    info.addPara("The LYNE service assembly still carries its last accepted"
                            + " handshake. Take it to a fishing boat.", 10f);
                } else {
                    info.addPara("There is a trade working the far edges of the inhabited systems."
                            + " Find one of their boats and hail it.", 10f);
                }
            } else if (target.landed) {
                info.addPara("%s is in the hold. Take it to a fishing boat.", 10f,
                        Misc.getHighlightColor(), Misc.ucFirst(describeTarget()));
            } else if (target.systemName == null) {
                //the chart rung, which is the one errand with no place in it - the whole lesson is
                //that the charts say where. A line naming a system it does not have would read
                //"out of" and then nothing, which is what it did
                info.addPara("Bring back %s. The charts say where; the planner on the map will"
                        + " plot it.", 10f, Misc.getHighlightColor(), describeTarget());
            } else {
                info.addPara("Bring back %s, out of %s.", 10f, Misc.getHighlightColor(),
                        describeTarget(), target.systemName);

                if (target.needsDeepGear) {
                    info.addPara("It has to come up through a breach lamp, on a harpoon line."
                            + " Nothing out of a pond will answer the question.",
                            Misc.getGrayColor(), 10f);
                } else if (target.atPond) {
                    info.addPara("The mark is a rupture. Drop a rod down it.",
                            Misc.getGrayColor(), 10f);
                }
            }

            addBulletPoints(info, ListInfoMode.IN_DESC);
        }

        /** Vanilla's own tutorial-mission icon, which is exactly what this is. */
        @Override
        public String getIcon() {
            return Global.getSettings().getSpriteName("campaignMissions", "tutorial");
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

            return tags;
        }

        /**
         * The rupture itself while the errand is at one, so the note points at the thing wearing
         * the marker rather than at the system containing it; the system otherwise, and whichever
         * boat is nearest when there is no errand at all.
         */
        @Override
        public SectorEntityToken getMapLocation(SectorMapAPI map) {
            Target target = getTarget();

            //once it is aboard the water is not where the player is being sent
            if (target != null && !target.landed) {
                for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                    if (!system.getId().equals(target.systemId)) continue;

                    if (target.atPond) {
                        SectorEntityToken pond = QuestPond.findPondAt(system, target.x, target.y,
                                TutorialConstants.SPOT_SPREAD);

                        if (pond != null) return pond;
                    }

                    return system.getHyperspaceAnchor();
                }
            }

            return getNearestBoat();
        }
    }
}
