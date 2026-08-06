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
 * One column, always: a single centred holder per row, scrolling when the stock runs past the
 * pane, under a heading that spans the pane border to border. One round holder per species
 * catchable in the viewed system, on the map's knowledge rules: landed species wear their art,
 * survey-known ones the generic mark - both ringed in their rarity and answering hover and F2 -
 * and species never heard of stand as bare question marks that answer nothing.
 */
public class FishSystemPane extends BaseCustomUIPanelPlugin {

    public static final float WIDTH = 76f;

    public static final float PAD = 10f;
    public static final float CELL = 38f;
    public static final float CELL_GAP = 6f;

    /** The native section heading's own height. */
    public static final float HEADING_HEIGHT = 22f;

    protected PositionAPI pos;

    /** Builds the header and the column into the pane's own panel. Call once. */
    public void mount(CustomPanelAPI panel, float width, float height, StarSystemAPI system) {
        List<FishSpec> known = FishPresence.getKnownFishIn(system);
        int unknown = FishPresence.getUnknownCountIn(system);
        int total = known.size() + unknown;

        //the heading spans the pane border to border, no side padding at all
        TooltipMakerAPI head = panel.createUIElement(width, HEADING_HEIGHT, false);
        head.setParaSmallInsignia();
        head.addSectionHeading("Fish", Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, 0f);
        panel.addUIElement(head).inTL(0f, 0f);

        float innerWidth = width - PAD * 2f;
        float listHeight = height - HEADING_HEIGHT - PAD * 2f;

        //one column, always - a scroller takes the overflow rather than a second column
        TooltipMakerAPI content = panel.createUIElement(innerWidth, listHeight, true);

        for (int i = 0; i < total; i++) {
            FishSpec spec = i < known.size() ? known.get(i) : null;

            CustomPanelAPI rowPanel = panel.createCustomPanel(innerWidth, CELL,
                    new BaseCustomUIPanelPlugin() {
                    });

            CustomPanelAPI cell = panel.createCustomPanel(CELL, CELL, new FishHolderPlugin(spec));
            rowPanel.addComponent(cell).inTL(Math.max(0f, (innerWidth - CELL) * 0.5f), 0f);

            //the shared species card - the question marks stay questions
            if (spec != null) {
                content.addTooltipTo(FishTooltips.create(spec), cell,
                        TooltipMakerAPI.TooltipLocation.LEFT);
            }

            content.addCustom(rowPanel, i == 0 ? 0f : CELL_GAP);
        }

        panel.updateUIElementSizeAndMakeItProcessInput(content);
        panel.addUIElement(content).inTL(PAD, HEADING_HEIGHT + PAD);
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    /** The same dress as the filter pane: transparent black field, the map panel's own border. */
    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX();
        float y = pos.getY();
        float w = pos.getWidth();
        float h = pos.getHeight();

        ShopUi.drawQuad(x, y, w, h, Color.BLACK, 0.7f * alphaMult);

        //the border the map itself wears - dark player colour read as too dim beside it
        Color border = Misc.getBasePlayerColor();
        ShopUi.drawQuad(x, y, w, 1f, border, alphaMult);
        ShopUi.drawQuad(x, y + h - 1f, w, 1f, border, alphaMult);
        ShopUi.drawQuad(x, y, 1f, h, border, alphaMult);
        ShopUi.drawQuad(x + w - 1f, y, 1f, h, border, alphaMult);
    }
}
