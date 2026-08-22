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

public final class AquariumTransfers {

    public interface OnMoved {

        void moved(int count);
    }

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

    private AquariumTransfers() {
    }

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
        FishItems.unbox(Global.getSector().getPlayerFleet().getCargo());

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

    public static int addToTank(CargoAPI picked, BreachConservatory conservatory) {
        if (picked == null) return 0;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        int moved = 0;

        for (CargoStackAPI stack : picked.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            // the picker supplies loose fish; container support keeps direct callers safe by moving the encoded contents rather than the box itself
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
}
