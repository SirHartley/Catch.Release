package catchrelease.campaign.fish.colony;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.items.FishItems;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

/**
 * Moving fish between the hold and the tank, both directions through the vanilla cargo picker.
 * Shared by the aquarium office dialog and the tank's own buttons on the colony menu, so the
 * two doors into the same water cannot drift apart.
 * <p>
 * Only loose specimens move - a crated bundle is a crate, and nobody tips a crate into a
 * display tank.
 */
public final class AquariumTransfers {

    /** Callback with how many specimens moved, after a picker resolves. */
    public interface OnMoved {
        void moved(int count);
    }

    private AquariumTransfers() {
    }

    /**
     * Specimens aboard, crates and the pile included.
     * <p>
     * It used to be loose ones only, which stopped being a useful answer the moment a landed fish
     * went straight into a crate - the tank would have read as empty for a hold full of fish.
     */
    public static int countFishAboard() {
        int count = 0;

        for (CargoStackAPI stack : Global.getSector().getPlayerFleet()
                .getCargo().getStacksCopy()) {

            count += FishItems.countSpecimens(stack);
        }

        return count;
    }

    public static void openAddPicker(InteractionDialogAPI dialog,
                                     BreachConservatory conservatory, OnMoved after) {

        CargoAPI offer = Global.getFactory().createCargo(true);

        for (CargoStackAPI stack : Global.getSector().getPlayerFleet()
                .getCargo().getStacksCopy()) {

            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (FishItems.isCatch(data)) offer.addSpecial(data, stack.getSize());
        }

        dialog.showCargoPickerDialog("Select specimens for the tank", "Add", "Never mind",
                false, 330f, offer, new PickerListener(after) {

                    @Override
                    public void pickedCargo(CargoAPI picked) {
                        after.moved(addToTank(picked, conservatory));
                    }

                    @Override
                    public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo,
                                                  CargoStackAPI pickedUp,
                                                  boolean pickedUpFromSource, CargoAPI combined) {
                        panel.addPara("Specimens go into the display tank and stay in the"
                                + " colony's care until taken back.", Misc.getGrayColor(), 0f);
                    }
                });
    }

    public static void openTakePicker(InteractionDialogAPI dialog,
                                      BreachConservatory conservatory, OnMoved after) {

        CargoAPI offer = Global.getFactory().createCargo(true);

        for (String encoded : conservatory.getAquariumFish()) {
            offer.addSpecial(new SpecialItemData(FishItems.FISH, encoded), 1);
        }

        dialog.showCargoPickerDialog("Select specimens to take back", "Take", "Never mind",
                false, 330f, offer, new PickerListener(after) {

                    @Override
                    public void pickedCargo(CargoAPI picked) {
                        after.moved(takeFromTank(picked, conservatory));
                    }

                    @Override
                    public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo,
                                                  CargoStackAPI pickedUp,
                                                  boolean pickedUpFromSource, CargoAPI combined) {
                        panel.addPara("Netted back out of the tank and into your hold.",
                                Misc.getGrayColor(), 0f);
                    }
                });
    }

    /** Hold to tank. @return how many specimens moved */
    public static int addToTank(CargoAPI picked, BreachConservatory conservatory) {
        if (picked == null) return 0;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int moved = 0;

        for (CargoStackAPI stack : picked.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            //a crate goes in whole: what the tank keeps is specimens, so the container is opened on
            //the way and the fish inside it are what is added
            java.util.List<FishCatch> going = FishItems.read(data);
            if (going.isEmpty()) continue;

            int count = (int) stack.getSize();
            cargo.removeItems(CargoItemType.SPECIAL, data, count);

            for (int i = 0; i < count; i++) {
                for (FishCatch entry : going) conservatory.getAquariumFish().add(entry.encode());
            }
            moved += count * going.size();
        }

        return moved;
    }

    /** Tank to hold. @return how many specimens moved */
    public static int takeFromTank(CargoAPI picked, BreachConservatory conservatory) {
        if (picked == null) return 0;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        List<String> tank = conservatory.getAquariumFish();
        int moved = 0;

        for (CargoStackAPI stack : picked.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null || !FishItems.FISH.equals(data.getId())) continue;

            for (int i = 0; i < (int) stack.getSize(); i++) {
                if (!tank.remove(data.getData())) break;

                cargo.addSpecial(data, 1);
                moved++;
            }
        }

        return moved;
    }

    /** Base with the cancel callback nothing here needs. */
    protected abstract static class PickerListener implements CargoPickerListener {

        protected final OnMoved after;

        public PickerListener(OnMoved after) {
            this.after = after;
        }

        @Override
        public void cancelledCargoSelection() {
            after.moved(0);
        }
    }
}
