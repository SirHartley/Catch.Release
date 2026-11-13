package catchrelease.campaign.fish.colony;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.Map;

public class AquariumManageDialog implements InteractionDialogPlugin, AquariumManagePanel.Host {

    protected enum Option {

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
        dialog.getTextPanel().addPara("The conservatory's back office smells of brine."
                + " The tank hums through the wall.");

        dialog.getTextPanel().addPara("Rolled scenery fills a rack along the back wall, most of it"
                        + " painted by somebody who has never been near the water.",
                Misc.getGrayColor());

        int held = conservatory.getAquariumFish().size();

        dialog.getTextPanel().addPara(held == 0 ? "The tank is empty."
                        : "The tank holds " + held + (held == 1 ? " specimen." : " specimens."),
                Misc.getGrayColor());

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Leave", Option.LEAVE);
        dialog.getOptionPanel().setShortcut(Option.LEAVE, Keyboard.KEY_ESCAPE,
                false, false, false, true);

        new AquariumManagePanel(conservatory, dialog, this).show();
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == Option.LEAVE) dialog.dismiss();
    }

    @Override
    public void reportMove(int moved, boolean intoTank) {
        if (moved <= 0) return;

        dialog.getTextPanel().addPara("Moved " + moved + (moved == 1 ? " specimen" : " specimens")
                + (intoTank ? " into the tank." : " back aboard."), Misc.getPositiveHighlightColor());
    }

    @Override
    public void reportHung(Backdrop backdrop) {
        dialog.getTextPanel().addPara(Backdrops.isBare(backdrop)
                ? "The scenery goes back on the rack." : "It goes up behind the glass.",
                Misc.getPositiveHighlightColor());
    }

    @Override
    public void reportDisplay(boolean on) {
        dialog.getTextPanel().addPara(on ? "The tank lights hum back up." : "The tank goes dark.");
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
