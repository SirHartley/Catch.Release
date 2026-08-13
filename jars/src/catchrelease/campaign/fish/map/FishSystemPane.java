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

/**
 * The system view's catch, as a proper sidebar: when the Fish filter is on and the map shows a
 * single system, the map narrows exactly the way it does for the big filter pane and this takes
 * the freed edge - real components, so the holders wear real tooltips and the header is the
 * game's own.
 * <p>
 * One centred column of holders, placed directly on the panel - no scroller, no element layout,
 * nothing between the arithmetic and the pixels. The stock is assumed to fit; the fish tables
 * are being kept small enough that it does. One round holder per species catchable in the
 * viewed system, on the map's knowledge rules: landed species wear their art, survey-known ones
 * the generic mark - both ringed in their rarity and answering hover and F2 - and species never
 * heard of stand as bare question marks that answer nothing.
 */
public class FishSystemPane extends BaseCustomUIPanelPlugin {

    public static final float WIDTH = 76f;

    public static final float PAD = 8f;
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

        //an empty element carries the tooltips; the cells themselves sit straight on the panel,
        //centred by arithmetic no layout machinery can lean on
        TooltipMakerAPI tooltips = panel.createUIElement(1f, 1f, false);
        panel.addUIElement(tooltips).inTL(0f, 0f);

        float cellX = (width - CELL) * 0.5f;
        float top = HEADING_HEIGHT + PAD;

        for (int i = 0; i < total; i++) {
            FishSpec spec = i < known.size() ? known.get(i) : null;

            CustomPanelAPI cell = panel.createCustomPanel(CELL, CELL, new FishHolderPlugin(spec));
            panel.addComponent(cell).inTL(cellX, top + i * (CELL + CELL_GAP));

            //the shared species card - the question marks stay questions
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

    /** The same dress as the filter pane: transparent black field, the softened border. */
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
