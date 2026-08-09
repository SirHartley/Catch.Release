package catchrelease.campaign.fish.colony;

import catchrelease.campaign.fish.data.FishRarity;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * The aquarium office: fish out of the hold and into the tank, fish out of the tank and back
 * into the hold, the scene behind the water, and the switch on the wall. The moving itself is
 * {@link AquariumTransfers}; this dialog is the counter it happens over.
 * <p>
 * The tank's stock and its scene both live on the {@link BreachConservatory} itself, so they are
 * saved with the colony and drown with it. Which scenes the player <i>has</i> is the other scope -
 * see {@link Backdrops}.
 * <p>
 * The scenery rack previews on hover. What it puts in the visual slot is an actual
 * {@link AquariumTankPanel} showing that scene, not a picture of the art: the question the player
 * is asking is what the tank will look like, and the only answer that cannot be wrong is the tank.
 */
public class AquariumManageDialog implements InteractionDialogPlugin {

    protected enum Option {
        ADD,
        REMOVE,
        BACKDROP,
        PREVIOUS_BACKDROPS,
        NEXT_BACKDROPS,
        TOGGLE,
        BACK,
        LEAVE
    }

    protected final BreachConservatory conservatory;
    protected InteractionDialogAPI dialog;
    protected int backdropPage;

    /** Six leaves room for Previous, Next and Back under the option panel's nine-row ceiling. */
    protected static final int BACKDROPS_PER_PAGE = 6;

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
        dialog.getOptionPanel().addOption("Change the backdrop", Option.BACKDROP);
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

    //---------------------------------------------------------------- the scenery rack

    /**
     * Every scene the player has, with the one currently up marked, previewing on hover.
     * <p>
     * Only what is owned. A rack that listed everything and greyed most of it out would be a shop
     * pretending to be a cupboard, and the shop is Crablobab.
     */
    protected void showBackdrops() {
        dialog.getOptionPanel().clearOptions();

        Backdrop hanging = Backdrops.getHanging(conservatory);
        List<Backdrop> owned = Backdrops.getOwned();
        int pages = Math.max(1, (owned.size() + BACKDROPS_PER_PAGE - 1) / BACKDROPS_PER_PAGE);
        backdropPage = Math.max(0, Math.min(backdropPage, pages - 1));
        int first = backdropPage * BACKDROPS_PER_PAGE;
        int last = Math.min(first + BACKDROPS_PER_PAGE, owned.size());

        dialog.getTextPanel().addPara("Rolled scenery in a rack along the back wall, most of it"
                + " painted by somebody who has never been near the water it is of.",
                Misc.getGrayColor());

        for (Backdrop backdrop : owned.subList(first, last)) {
            String label = backdrop.getDisplayName();
            if (hanging != null && backdrop.id.equals(hanging.id)) label += " - up now";

            if (backdrop.rarity == FishRarity.COMMON) {
                dialog.getOptionPanel().addOption(label, backdrop, "The backdrop can be changed at any time. More can be found through quests or purchased from a certain crab merchant.");
            } else {
                dialog.getOptionPanel().addOption(label, backdrop, backdrop.rarity.color, "The backdrop can be changed at any time. More can be found through quests or purchased from a certain crab merchant.");
            }
        }

        if (backdropPage > 0) {
            dialog.getOptionPanel().addOption("Previous page", Option.PREVIOUS_BACKDROPS);
        }
        if (backdropPage + 1 < pages) {
            dialog.getOptionPanel().addOption("Next page", Option.NEXT_BACKDROPS);
        }
        dialog.getOptionPanel().addOption("Back", Option.BACK);
        dialog.getOptionPanel().setShortcut(Option.BACK,
                org.lwjgl.input.Keyboard.KEY_ESCAPE, false, false, false, true);

        preview(hanging);
    }

    /** Puts the tank itself in the visual slot, showing the scene being considered. */
    protected void preview(Backdrop backdrop) {
        AquariumTankPanel tank = new AquariumTankPanel(conservatory, dialog);
        tank.setPreview(backdrop);

        dialog.getVisualPanel().showCustomPanel(AquariumTankScript.getPanelWidth(),
                AquariumTankScript.PANEL_HEIGHT, tank);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData instanceof Backdrop backdrop) {
            if (Backdrops.hang(conservatory, backdrop)) {
                dialog.getTextPanel().addPara(Backdrops.isBare(backdrop)
                                ? "The rack goes back on the wall. Bare glass from here."
                                : "It goes up behind the glass.",
                        Misc.getPositiveHighlightColor());
            }

            showBackdrops();
            return;
        }

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
            case BACKDROP -> {
                backdropPage = 0;
                showBackdrops();
            }
            case PREVIOUS_BACKDROPS -> {
                backdropPage--;
                showBackdrops();
            }
            case NEXT_BACKDROPS -> {
                backdropPage++;
                showBackdrops();
            }
            case BACK -> {
                dialog.getVisualPanel().fadeVisualOut();
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

    /** The rack's whole point: the scene under the cursor is the scene in the visual slot. */
    @Override
    public void optionMousedOver(String optionText, Object optionData) {
        if (optionData instanceof Backdrop backdrop) preview(backdrop);
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
