package catchrelease.ui;

import com.fs.starfarer.api.campaign.CargoPickerListener;

public interface CargoPickerButtonListener extends CargoPickerListener {

    String getPickerButtonText();

    // runs from the picker's advance once its selection is back in the offer; the grids refresh afterwards
    void pickerButtonPressed();
}
