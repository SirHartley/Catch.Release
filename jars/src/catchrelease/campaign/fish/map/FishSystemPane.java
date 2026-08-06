package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopUi;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

/**
 * The system view's catch, as a proper sidebar: when the Fish filter is on and the map shows a
 * single system, the map narrows exactly the way it does for the big filter pane and this takes
 * the freed edge - real components, so the holders wear real tooltips and the header is the
 * game's own.
 * <p>
 * One round holder per species catchable in the viewed system, on the map's knowledge rules:
 * landed species wear their art, survey-known ones the generic mark - both ringed in their
 * rarity and answering hover and F2 - and species never heard of stand as bare question marks
 * that answer nothing.
 */
public class FishSystemPane extends BaseCustomUIPanelPlugin {

    public static final float WIDTH = 140f;

    public static final float PAD = 10f;
    public static final float CELL = 38f;
    public static final float CELL_GAP = 6f;

    protected PositionAPI pos;

    /** Builds the header and the grid into the pane's own panel. Call once. */
    public void mount(CustomPanelAPI panel, float width, float height, StarSystemAPI system) {
        List<FishSpec> known = FishPresence.getKnownFishIn(system);
        int unknown = FishPresence.getUnknownCountIn(system);
        int total = known.size() + unknown;

        float innerWidth = width - PAD * 2f;
        TooltipMakerAPI content = panel.createUIElement(innerWidth, height - PAD * 2f, true);

        content.setParaSmallInsignia();
        content.addSectionHeading("Local catch", Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, 0f);

        int perRow = Math.max(1, (int) ((innerWidth + CELL_GAP) / (CELL + CELL_GAP)));
        int rows = (total + perRow - 1) / perRow;

        int placed = 0;
        for (int row = 0; row < rows; row++) {
            int inThisRow = Math.min(perRow, total - placed);

            CustomPanelAPI rowPanel = panel.createCustomPanel(innerWidth, CELL,
                    new BaseCustomUIPanelPlugin() {
                    });

            for (int i = 0; i < inThisRow; i++) {
                int index = placed + i;
                FishSpec spec = index < known.size() ? known.get(index) : null;

                CustomPanelAPI cell = panel.createCustomPanel(CELL, CELL,
                        new FishHolderPlugin(spec));
                rowPanel.addComponent(cell).inTL(i * (CELL + CELL_GAP), 0f);

                //the shared species card - the question marks stay questions
                if (spec != null) {
                    content.addTooltipTo(FishTooltips.create(spec), cell,
                            TooltipMakerAPI.TooltipLocation.LEFT);
                }
            }

            content.addCustom(rowPanel, row == 0 ? 8f : CELL_GAP);
            placed += inThisRow;
        }

        panel.updateUIElementSizeAndMakeItProcessInput(content);
        panel.addUIElement(content).inTL(PAD, PAD);
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    /** The same dress as the filter pane: transparent black field, one-pixel player border. */
    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        ShopUi.drawQuad(x, y, w, h, Color.BLACK, 0.7f * alphaMult);

        Color border = Misc.getDarkPlayerColor();
        ShopUi.drawQuad(x, y, w, 1f, border, alphaMult);
        ShopUi.drawQuad(x, y + h - 1f, w, 1f, border, alphaMult);
        ShopUi.drawQuad(x, y, 1f, h, border, alphaMult);
        ShopUi.drawQuad(x + w - 1f, y, 1f, h, border, alphaMult);
    }
}
