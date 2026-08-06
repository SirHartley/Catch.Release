package catchrelease.campaign.fish.colony;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * The aquarium office: fish out of the hold and into the tank, fish out of the tank and back
 * into the hold, and the switch on the wall. The moving itself is {@link AquariumTransfers};
 * this dialog is the counter it happens over.
 * <p>
 * The tank's stock lives on the {@link BreachConservatory} itself, so it is saved with the
 * colony and drowns with it.
 */
public class AquariumManageDialog implements InteractionDialogPlugin {

    protected enum Option {
        ADD,
        REMOVE,
        TOGGLE,
        LEAVE
    }

    protected final BreachConservatory conservatory;
    protected InteractionDialogAPI dialog;

    public AquariumManageDialog(BreachConservatory conservatory) {
        this.conservatory = conservatory;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.setPromptText("");
        dialog.getTextPanel().addPara("The conservatory's back office smells of brine and"
                + " filtration. The tank hums through the wall.");

        showMain();
    }

    protected void showMain() {
        dialog.getOptionPanel().clearOptions();

        int held = conservatory.getAquariumFish().size();

        dialog.getTextPanel().addPara(held == 0 ? "The tank is empty."
                        : "The tank holds " + held + (held == 1 ? " specimen." : " specimens."),
                Misc.getGrayColor());

        dialog.getOptionPanel().addOption("Add fish from your hold", Option.ADD);
        dialog.getOptionPanel().addOption("Take fish back aboard", Option.REMOVE);
        dialog.getOptionPanel().addOption(conservatory.isAquariumEnabled()
                ? "Shut the display off" : "Switch the display on", Option.TOGGLE);
        dialog.getOptionPanel().addOption("Leave", Option.LEAVE);

        dialog.getOptionPanel().setShortcut(Option.LEAVE,
                org.lwjgl.input.Keyboard.KEY_ESCAPE, false, false, false, true);

        if (AquariumTransfers.countFishAboard() == 0) {
            dialog.getOptionPanel().setEnabled(Option.ADD, false);
        }
        if (held == 0) {
            dialog.getOptionPanel().setEnabled(Option.REMOVE, false);
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof Option)) return;

        switch ((Option) optionData) {
            case ADD -> AquariumTransfers.openAddPicker(dialog, conservatory,
                    moved -> reportMove(moved, " into the tank."));
            case REMOVE -> AquariumTransfers.openTakePicker(dialog, conservatory,
                    moved -> reportMove(moved, " back aboard."));
            case TOGGLE -> {
                conservatory.setAquariumEnabled(!conservatory.isAquariumEnabled());
                dialog.getTextPanel().addPara(conservatory.isAquariumEnabled()
                        ? "The tank lights hum back up." : "The tank goes dark.");
                showMain();
            }
            case LEAVE -> dialog.dismiss();
        }
    }

    protected void reportMove(int moved, String where) {
        if (moved > 0) {
            dialog.getTextPanel().addPara("Moved " + moved
                            + (moved == 1 ? " specimen" : " specimens") + where,
                    Misc.getPositiveHighlightColor());
        }

        showMain();
    }

    //---------------------------------------------------------------- plumbing

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
