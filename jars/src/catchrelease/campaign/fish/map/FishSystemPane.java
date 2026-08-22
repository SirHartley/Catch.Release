package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.ui.ShopUi;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

public class FishSystemPane extends BaseCustomUIPanelPlugin {

    public static final float WIDTH = 76f;
    public static final float PAD = 8f;

    public static final float CELL = 38f;
    public static final float CELL_GAP = 6f;
    public static final float HEADING_HEIGHT = 22f;

    protected PositionAPI pos;

    public void mount(CustomPanelAPI panel, float width, float height, StarSystemAPI system) {
        List<FishSpec> known = FishPresence.getKnownFishIn(system);
        int unknown = FishPresence.getUnknownCountIn(system);
        int total = known.size() + unknown;

        TooltipMakerAPI head = panel.createUIElement(width, HEADING_HEIGHT, false);
        head.setParaSmallInsignia();
        head.addSectionHeading("Fish", Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, 0f);
        panel.addUIElement(head).inTL(0f, 0f);

        TooltipMakerAPI tooltips = panel.createUIElement(1f, 1f, false);
        panel.addUIElement(tooltips).inTL(0f, 0f);

        float cellX = (width - CELL) * 0.5f;
        float top = HEADING_HEIGHT + PAD;

        for (int i = 0; i < total; i++) {
            FishSpec spec = i < known.size() ? known.get(i) : null;

            CustomPanelAPI cell = panel.createCustomPanel(CELL, CELL, new FishHolderPlugin(spec));
            panel.addComponent(cell).inTL(cellX, top + i * (CELL + CELL_GAP));

            if (spec != null) {
                tooltips.addTooltipTo(FishTooltips.create(spec), cell,
                        TooltipMakerAPI.TooltipLocation.LEFT);
            }
        }
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        ShopUi.drawPanel(x, y, w, h, 0.7f, alphaMult);
    }
}
