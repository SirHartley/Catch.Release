package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.items.FishItemPlugin;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * Boarding the hulk.
 * <p>
 * Two things to notice and one thing to take. The harpoon went in from outside and the hull gave
 * way from inside, which is a sentence the player can assemble themselves and is far better than
 * being told. Pulling the head out is what starts the errand - it belongs to somebody, and the
 * somebody keeps boats in every inhabited system.
 * <p>
 * There is no salvage here on purpose. A wreck that pays out is a wreck the player files under
 * loot; one that hands them a question is a wreck they remember.
 */
public class TutorialWreckDialog implements InteractionDialogPlugin {

    protected enum Option {
        LOOK,
        TAKE,
        LEAVE
    }

    protected InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.getVisualPanel().showImageVisual(
                new InteractionDialogImageVisual("illustrations", "space_wreckage", 640, 400));

        String hull = TutorialWreck.getHull(dialog.getInteractionTarget());

        dialog.getTextPanel().addPara("A cruiser, cold for a long time. The transponder is dead and"
                + " the registry plate has been ground off, but the hull is a %s and nobody builds"
                + " those by accident.", Misc.getTextColor(), Misc.getHighlightColor(),
                hullName(hull));

        dialog.getTextPanel().addPara("There is a hole in the dorsal plating with half a kilometre"
                + " of line still through it. The head went in from outside. The plating around it"
                + " is bent the other way.");

        //the water it died in, in the mod's own words - the player will be reading this label for
        //the rest of the campaign and this is the first time it is put in front of them
        float drift = FishermanIdentity.getDrift(
                dialog.getInteractionTarget().getContainingLocation());

        dialog.getTextPanel().addPara("The rupture is a few hundred metres off the bow. Instruments"
                        + " call the local fabric %s.", Misc.getTextColor(),
                FishItemPlugin.getAberrationColor(drift),
                FishItemPlugin.getAberrationLabel(drift));

        showOptions();
    }

    /** The variant id as something a person would say. */
    protected String hullName(String hull) {
        if (hull == null) return "cruiser";

        String name = hull.contains("_") ? hull.substring(0, hull.indexOf('_')) : hull;

        return Misc.ucFirst(name) + "-class";
    }

    protected void showOptions() {
        dialog.getOptionPanel().clearOptions();

        dialog.getOptionPanel().addOption("Look for the crew", Option.LOOK);

        if (!FishingIntro.isCarryingHarpoon()) {
            dialog.getOptionPanel().addOption("Cut the head out and take it", Option.TAKE,
                    Misc.getHighlightColor(), null);
        }

        dialog.getOptionPanel().addOption("Leave", Option.LEAVE);
        dialog.getOptionPanel().setShortcut(Option.LEAVE, org.lwjgl.input.Keyboard.KEY_ESCAPE,
                false, false, false, true);
    }

    protected void look() {
        dialog.getTextPanel().addPara("The berths are made up. The galley is stocked. There is a"
                + " half-finished meal on the mess table that has not rotted, because nothing here"
                + " has, and the boarding party stop talking after the second compartment.");

        dialog.getTextPanel().addPara("Nobody is aboard. Nobody has been for a very long time, and"
                + " nobody left in a hurry.", Misc.getGrayColor());

        showOptions();
    }

    protected void take() {
        dialog.getTextPanel().addPara("It comes out of the plating cleanly, which it should not."
                + " Forty tonnes of barbed alloy, and it lifts like it wants to go.");

        FishingIntro.takeHarpoon();

        CampaignFleetAPI boat = FishingIntro.getNearestBoat();

        if (boat != null && boat.getContainingLocation() != null) {
            dialog.getTextPanel().addPara("The shaft carries a maintenance mark - a fisheries"
                            + " outfit, still filing. Their nearest boat works %s.",
                    Misc.getTextColor(), Misc.getHighlightColor(),
                    boat.getContainingLocation().getName());
        } else {
            dialog.getTextPanel().addPara("The shaft carries a maintenance mark: a fisheries"
                    + " outfit, which is not a category that has meant anything for two hundred"
                    + " years, and which is still filing.");
        }

        dialog.getTextPanel().addPara("An intel note tracks the nearest one.", Misc.getGrayColor());

        showOptions();
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData != null) dialog.addOptionSelectedText(optionData);

        if (optionData == Option.LOOK) {
            look();
            return;
        }

        if (optionData == Option.TAKE) {
            take();
            return;
        }

        dialog.dismiss();
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
