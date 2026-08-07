package catchrelease.campaign.fish.tutorial;

import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.items.FishItemPlugin;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * Standing over somebody else's harpoon.
 * <p>
 * Two paragraphs and one option. The find is the hook, not the reading - what the player is meant
 * to take away is that this is gear, it belongs to a trade, and the water around it is wrong. The
 * transponder supplies the only thing they cannot work out by looking: where the trade is.
 * <p>
 * The line stays where it is. Taking it would make this a salvage, and the point of the scene is
 * that somebody lost something expensive here and did not come back for it.
 */
public class LostHarpoonDialog implements InteractionDialogPlugin {

    protected enum Option {
        TRACE,
        LEAVE
    }

    protected InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        //vanilla's survey plate: somebody's instruments over somebody's rock, which is exactly
        //what standing on this site is
        dialog.getVisualPanel().showImageVisual(
                new InteractionDialogImageVisual("illustrations", "survey", 640, 400));

        dialog.getTextPanel().addPara("Half a kilometre of line, most of it fused into the"
                + " regolith, and at the end of it a head the size of a shuttle - barbed, spooled,"
                + " and built to be fired at something. It is not a weapon. It is too careful to"
                + " be a weapon.");

        //the local instability said in the mod's own words, because this is the first time the
        //player is asked to notice it and they will be asked constantly afterwards
        float drift = FishermanIdentity.getDrift(
                dialog.getInteractionTarget().getContainingLocation());

        dialog.getTextPanel().addPara("The sky above the site is not right. Starlight arrives"
                        + " bent, then arrives again. Instruments call the local fabric %s.",
                Misc.getTextColor(), FishItemPlugin.getAberrationColor(drift),
                FishItemPlugin.getAberrationLabel(drift));

        dialog.getTextPanel().addPara("A transponder in the shaft is still transmitting, on a"
                + " commercial band, to nobody. It has been doing it for a long time.",
                Misc.getGrayColor());

        showOptions();
    }

    protected void showOptions() {
        dialog.getOptionPanel().clearOptions();

        if (!FishingIntro.isAtLeast(FishingIntro.POINTED)) {
            dialog.getOptionPanel().addOption("Trace the transponder's registry", Option.TRACE,
                    Misc.getHighlightColor(), null);
        }

        dialog.getOptionPanel().addOption("Leave it where it lies", Option.LEAVE);
        dialog.getOptionPanel().setShortcut(Option.LEAVE, org.lwjgl.input.Keyboard.KEY_ESCAPE,
                false, false, false, true);
    }

    protected void trace() {
        dialog.getTextPanel().addPara("The registry comes back in pieces. No owner, no home port,"
                + " and a maintenance contract with an outfit that files itself under fisheries -"
                + " which is not a category that has meant anything for two hundred years.");

        dialog.getTextPanel().addPara("Their boats are still listed as active. They work the far"
                + " edge of inhabited systems, out past the last colony, where nothing is in the"
                + " way.");

        FishingIntro.point();

        dialog.getTextPanel().addPara("An intel note tracks the nearest one.", Misc.getGrayColor());

        showOptions();
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData != null) dialog.addOptionSelectedText(optionData);

        if (optionData == Option.TRACE) {
            trace();
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
