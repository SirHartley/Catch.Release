package catchrelease.config;

import catchrelease.ModPlugin;
import lunalib.backend.ui.components.LunaUIRadioButton;
import lunalib.backend.ui.components.LunaUITextFieldWithSlider;
import lunalib.backend.ui.components.base.LunaUIButton;
import lunalib.backend.ui.settings.LunaSettingsData;
import lunalib.backend.ui.settings.LunaSettingsUISettingsPanel;

import java.util.Map;

final class LunaSettingsBridge {

    // Luna retains both pending values and mounted controls after Save.
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void sync(Map<Setting, Object> values) {
        var ui = LunaSettingsUISettingsPanel.Companion;
        for (var pending : ui.getChangedSettings()) {
            if (!ModPlugin.MOD_ID.equals(pending.getModID())) continue;
            Object value = find(values, pending.getFieldID());
            if (value != null) pending.setData(value);
        }
        for (var element : ui.getAddedElements()) {
            if (!(element.getKey() instanceof LunaSettingsData data)
                    || !ModPlugin.MOD_ID.equals(data.getModID())) continue;
            Object value = find(values, data.getFieldID());
            if (value == null) continue;
            if (element instanceof LunaUIRadioButton radio && value instanceof String text) {
                radio.setValue(text);
                for (var button : radio.getButtons()) {
                    if (button.getButtonText() != null && text.equals(button.getButtonText().getText())) {
                        button.setSelected();
                    }
                }
            } else if (element instanceof LunaUIButton button && value instanceof Boolean flag) {
                button.setValue(flag);
            } else if (element instanceof LunaUITextFieldWithSlider slider && value instanceof Number) {
                slider.setValue((Number) value);
                if (slider.getTextField() != null) {
                    slider.getTextField().setValue(value);
                    if (slider.getTextField().getParagraph() != null) {
                        slider.getTextField().getParagraph().setText(value.toString());
                    }
                }
                if (slider.getValueSlider() != null) {
                    slider.getValueSlider().setValue((Number) value);
                    slider.getValueSlider().setSliderPositionByValue((Number) value);
                }
            }
        }
    }

    private static Object find(Map<Setting, Object> values, String id) {
        for (Setting setting : Setting.values()) if (setting.id.equals(id)) return values.get(setting);
        return null;
    }

    private LunaSettingsBridge() {
    }
}
