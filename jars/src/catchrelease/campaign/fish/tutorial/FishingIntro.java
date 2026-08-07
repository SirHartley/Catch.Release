package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * How somebody stops being a person who has never heard of this.
 * <p>
 * Three stages, one piece of state, and no connection to the ordinary loop at all - the trade runs
 * whether or not any of this has happened, and this runs once and is over:
 * <ul>
 * <li><b>{@link #UNSTARTED}</b> - no gear exists. Every way in leads to the same place, so none of
 * them is required and tripping two of them is not two tutorials.
 * <li><b>{@link #POINTED}</b> - the player knows there is a boat and roughly where. A note carries
 * the mark. This is all the wreck and the castaway actually do.
 * <li><b>{@link #TAUGHT}</b> - the Fisherman has explained it and handed the rig over. Done.
 * </ul>
 * The abilities are held behind it: {@code unlockedAtStart} is off for all four, so a new campaign
 * has no fishing gear until somebody puts it in your hands, which is the only version of a tutorial
 * that is not a page of text you can close.
 */
public class FishingIntro {

    public static final int UNSTARTED = 0;
    public static final int POINTED = 1;
    public static final int TAUGHT = 2;

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

    //---------------------------------------------------------------- the hooks

    /**
     * Told there is a boat worth finding. Idempotent, because every way in calls it and a player
     * who trips two should not end up with two notes.
     */
    public static void point() {
        if (isAtLeast(POINTED)) return;

        setStage(POINTED);

        Global.getSector().getIntelManager().addIntel(new IntroIntel());
    }

    /** The rig changes hands. The note comes down; there is nothing left for it to track. */
    public static void teach(TextPanelAPI text) {
        setStage(TAUGHT);

        for (String ability : TutorialConstants.GEAR) grant(ability, text);

        Global.getSector().getMemoryWithoutUpdate().unset(TutorialConstants.CARRYING_KEY);

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager()
                .getIntel(IntroIntel.class)) {

            Global.getSector().getIntelManager().removeIntel(intel);
        }
    }

    /** Whether the player pulled the head out of the hulk and has not handed it over yet. */
    public static boolean isCarryingHarpoon() {
        return Global.getSector().getMemoryWithoutUpdate()
                .getBoolean(TutorialConstants.CARRYING_KEY);
    }

    public static void takeHarpoon() {
        Global.getSector().getMemoryWithoutUpdate().set(TutorialConstants.CARRYING_KEY, true);

        point();
    }

    /**
     * Puts an ability in the player's hands and on the bar.
     * <p>
     * Vanilla's own {@code AddAbility} arithmetic, because a granted ability that lands in the
     * character sheet but on no hotbar slot is one the player has to go and find in a menu they
     * have never opened.
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

    //---------------------------------------------------------------- the note

    /**
     * Go and find a fishing boat - and, if the hulk was opened, take the head back to one.
     * <p>
     * Its map location is worked out fresh every time it is asked for rather than pinned when the
     * note went up, because the boats move and the nearest one on that day is not the nearest one a
     * month later. A note pointing at the wrong side of the sector is worse than no note.
     */
    public static class IntroIntel extends BaseIntelPlugin {

        @Override
        public String getSmallDescriptionTitle() {
            return isCarryingHarpoon() ? "Return the harpoon" : "Fishing: find a boat";
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getSmallDescriptionTitle(), getTitleColor(mode), 0f);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            if (isCarryingHarpoon()) {
                info.addPara("The head came out of the hull cleanly, which it should not have."
                        + " Somebody owns it. Take it to a fishing boat in an inhabited system.",
                        10f);
            } else {
                info.addPara("There is a trade working the far edges of the inhabited systems."
                        + " Find one of their boats and hail it.", 10f);
            }

            info.addPara("They keep out past the last colony, where nothing is in the way.",
                    Misc.getGrayColor(), 10f);

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

        @Override
        public SectorEntityToken getMapLocation(SectorMapAPI map) {
            return getNearestBoat();
        }
    }
}
