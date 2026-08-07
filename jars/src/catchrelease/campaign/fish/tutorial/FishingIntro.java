package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * How somebody comes to be fishing at all.
 * <p>
 * Four stages and one piece of state. Nothing here spawns anything or ticks on a clock - the hooks
 * push it forward and it answers questions about where it is, which is what keeps three separate
 * ways in from becoming three separate tutorials:
 * <ul>
 * <li><b>{@link #UNSTARTED}</b> - the gear does not exist yet. Every way in leads to the same
 * place, so the wreck, the hand on the shoulder in a bar, and simply hailing a fishing boat all
 * work and none of them is required.
 * <li><b>{@link #POINTED}</b> - the player has been told there is a boat and where. An intel note
 * carries the mark; it is the only thing the first two hooks actually do.
 * <li><b>{@link #TAUGHT}</b> - Baha has explained it and handed over the rig. One task outstanding:
 * bring something back.
 * <li><b>{@link #DONE}</b> - it was brought back, the outfitter is fitted, the note comes down.
 * </ul>
 * The abilities are held behind this. {@code unlockedAtStart} is off for all four in
 * {@code abilities.csv}, so a new campaign has no fishing gear until somebody puts it in your hands
 * - which is the only version of a tutorial that is not a page of text you can skip.
 * <p>
 * A campaign that predates the gating is already carrying the abilities, and
 * {@link #hasGearAlready()} is how Baha notices and talks about the boat instead of the basics.
 */
public class FishingIntro {

    public static final int UNSTARTED = 0;
    public static final int POINTED = 1;
    public static final int TAUGHT = 2;
    public static final int DONE = 3;

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

    //---------------------------------------------------------------- Baha

    /**
     * The scientist aboard, made once and kept - the same reason the Fisherman is. Nobody has said
     * what Baha's pronouns are, so they are they/them and the gender is left unset.
     */
    public static PersonAPI getBaha() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(TutorialConstants.BAHA_KEY);
        if (stored instanceof PersonAPI) return (PersonAPI) stored;

        PersonAPI person = Global.getFactory().createPerson();

        person.setFaction(FishermanConstants.FACTION);
        person.setGender(FullName.Gender.ANY);
        person.setName(new FullName(TutorialConstants.BAHA_FIRST, TutorialConstants.BAHA_LAST,
                FullName.Gender.ANY));
        person.setPortraitSprite(TutorialConstants.BAHA_PORTRAIT);
        person.setRankId(null);
        person.setPostId(null);

        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.BAHA_KEY, person);

        return person;
    }

    /**
     * A campaign that was fishing before any of this existed has already done the introduction.
     * <p>
     * All four abilities present is the tell, and it is one a fresh campaign cannot produce: the
     * outfitter is only ever handed over at the end. Without this, somebody two hundred cycles into
     * a save would be offered the beginner's talk by a scientist they have been trading with for a
     * year.
     */
    public static void healOldSave() {
        if (isAtLeast(DONE)) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        if (!hasGearAlready()) return;
        if (!player.hasAbility(TutorialConstants.OUTFITTER)) return;

        setStage(DONE);
    }

    //---------------------------------------------------------------- the hooks

    /**
     * Told there is a boat worth finding. Idempotent, because all three ways in call it and a
     * player who trips two of them should not end up with two notes.
     */
    public static void point() {
        if (isAtLeast(POINTED)) return;

        setStage(POINTED);

        Global.getSector().getIntelManager().addIntel(new IntroIntel());
    }

    /** Baha has explained it and handed over the rig. */
    public static void teach(TextPanelAPI text) {
        setStage(TAUGHT);

        for (String ability : TutorialConstants.STARTING_GEAR) grant(ability, text);
    }

    /** Something was brought back. The outfitter goes on, and the note comes down. */
    public static void finish(TextPanelAPI text) {
        setStage(DONE);

        grant(TutorialConstants.OUTFITTER, text);

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager()
                .getIntel(IntroIntel.class)) {

            Global.getSector().getIntelManager().removeIntel(intel);
        }
    }

    /** Whether the player already has the rig - a campaign that predates the gating does. */
    public static boolean hasGearAlready() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        for (String ability : TutorialConstants.STARTING_GEAR) {
            if (!player.hasAbility(ability)) return false;
        }

        return true;
    }

    /**
     * Puts an ability in the player's hands and on the bar.
     * <p>
     * Vanilla's own {@code AddAbility} arithmetic, because a granted ability that lands in the
     * character sheet but on no hotbar slot is one the player has to go and find in a menu they
     * have never opened. Quiet if they had it already, which is what makes this safe to call on a
     * campaign that was fishing before the gate existed.
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

    /** The first empty slot on any bar, left where it was found. */
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

    //---------------------------------------------------------------- the note

    /**
     * The one intel entry the introduction has: go and find a fishing boat.
     * <p>
     * Its map location is worked out fresh every time it is asked for rather than pinned at
     * creation, because the boats move and the nearest one to the player on the day the note went
     * up is not the nearest one a month later. A note that points at the wrong side of the sector
     * is worse than no note.
     */
    public static class IntroIntel extends BaseIntelPlugin {

        @Override
        public String getSmallDescriptionTitle() {
            return isAtLeast(TAUGHT) ? "Fishing: a first catch" : "Fishing: find a trawler";
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getSmallDescriptionTitle(), getTitleColor(mode), 0f);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            if (isAtLeast(TAUGHT)) {
                info.addPara("Baha wants to see you land one yourself. Anything common will do -"
                        + " bring it back to any fishing boat.", 10f);
            } else {
                info.addPara("There is a trade working the far edges of the inhabited systems."
                        + " Find one of their boats and hail it.", 10f);
                info.addPara("They keep out past the last colony, where nothing is in the way.",
                        Misc.getGrayColor(), 10f);
            }

            SectorEntityToken at = getMapLocation(null);
            if (at != null && at.getContainingLocation() != null) {
                info.addPara("Nearest known: %s", 10f, Misc.getHighlightColor(),
                        at.getContainingLocation().getName());
            }
        }

        @Override
        public String getIcon() {
            return FishConstants.CODEX_CATEGORY_ICON;
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = new LinkedHashSet<>();
            tags.add(Tags.INTEL_EXPLORATION);

            return tags;
        }

        /** The nearest boat to the player right now, by hyperspace distance. */
        @Override
        public SectorEntityToken getMapLocation(SectorMapAPI map) {
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
    }
}
