package catchrelease.campaign.fish.map;

import catchrelease.ui.PaneWidgets;
import catchrelease.ui.ShopUi;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class FishRouteSaveDialog extends BaseCustomUIPanelPlugin {

    public static final float WIDTH = 380f;
    public static final float HEIGHT = 208f;

    public static final float PAD = 14f;
    public static final float TITLE_HEIGHT = 20f;
    public static final float CLOSE_WIDTH = 20f;
    public static final float FIELD_HEIGHT = 22f;
    public static final float BUTTON_HEIGHT = 26f;

    public static final String NAME_GHOST = "Route name...";
    public static final String PURPOSE_GHOST = "Optional note...";
    public static final int NAME_MAX_CHARS = 40;
    public static final int PURPOSE_MAX_CHARS = 120;

    protected final Host host;
    protected CustomPanelAPI panel;
    protected PositionAPI pos;
    protected TextFieldAPI nameField;
    protected TextFieldAPI purposeField;

    public interface Host {

        void onRouteSaveConfirmed(String name, String purpose);

        void onRouteSaveClosed();
    }

    public FishRouteSaveDialog(Host host) {
        this.host = host;
    }

    public void mount(CustomPanelAPI panel) {
        this.panel = panel;

        float innerWidth = WIDTH - PAD * 2f;
        TooltipMakerAPI content = panel.createUIElement(innerWidth, HEIGHT - PAD * 2f, false);

        CustomPanelAPI titleRow = panel.createCustomPanel(innerWidth, TITLE_HEIGHT,
                new PaneWidgets.TitleRow("TRACK ROUTE"));
        CustomPanelAPI close = panel.createCustomPanel(CLOSE_WIDTH, TITLE_HEIGHT,
                new PaneWidgets.TextButton(() -> "X", () -> true, host::onRouteSaveClosed));
        titleRow.addComponent(close).inTR(0f, 0f);
        content.addCustom(titleRow, 0f);

        content.addPara("Name", Misc.getGrayColor(), 10f);
        nameField = content.addTextField(innerWidth, FIELD_HEIGHT, ShopUi.FONT_SMALL, 4f);
        nameField.setText(NAME_GHOST);
        nameField.setMaxChars(NAME_MAX_CHARS);

        content.addPara("Note", Misc.getGrayColor(), 8f);
        purposeField = content.addTextField(innerWidth, FIELD_HEIGHT, ShopUi.FONT_SMALL, 4f);
        purposeField.setText(PURPOSE_GHOST);
        purposeField.setMaxChars(PURPOSE_MAX_CHARS);

        CustomPanelAPI save = panel.createCustomPanel(innerWidth, BUTTON_HEIGHT,
                new PaneWidgets.TextButton(() -> "START TRACKING", () -> true, this::confirm));
        content.addCustom(save, 12f);

        panel.addUIElement(content).inTL(PAD, PAD);
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        ShopUi.drawPanel(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                0.85f, alphaMult);
    }

    @Override
    public void advance(float amount) {
        if (nameField != null) PaneWidgets.tendGhost(nameField, NAME_GHOST);
        if (purposeField != null) PaneWidgets.tendGhost(purposeField, PURPOSE_GHOST);
    }

    protected void confirm() {
        String name = nameField == null ? "" : PaneWidgets.tendGhost(nameField, NAME_GHOST);
        String purpose = purposeField == null
                ? "" : PaneWidgets.tendGhost(purposeField, PURPOSE_GHOST);

        host.onRouteSaveConfirmed(name, purpose);
    }
}
