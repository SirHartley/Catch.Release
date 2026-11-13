package catchrelease.campaign.fish.colony;

import catchrelease.helper.loading.SpriteLoader;
import catchrelease.ui.UiKit;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AquariumManagePanel extends BaseCustomUIPanelPlugin {

    public interface Host {

        void reportMove(int moved, boolean intoTank);

        void reportHung(Backdrop backdrop);

        void reportDisplay(boolean on);
    }

    public static final float WIDTH = 590f;
    public static final float LIST_WIDTH = WIDTH - 10f;
    public static final float TALL_SCREEN = 850f;

    public static final float TANK_Y = 30f;
    public static final float SIDE_X = AquariumTankScript.PANEL_WIDTH + 12f;
    public static final float SIDE_WIDTH = WIDTH - SIDE_X;
    public static final float TRANSFER_WIDTH = 86f;
    public static final float TRANSFER_GAP = 6f;

    public static final float BACKDROP_Y = TANK_Y + AquariumTankScript.PANEL_HEIGHT
            + UiKit.HEADING_GAP;
    public static final float GRID_Y = BACKDROP_Y + UiKit.HEADING_HEIGHT + UiKit.ROW_GAP;
    public static final int COLUMNS = 4;
    public static final float TILE_GAP = 6f;
    // a tile at the left edge of a scroll area loses its left border to the clip
    public static final float GRID_MARGIN = 2f;
    public static final float TILE_WIDTH = 140f;
    public static final float TILE_HEIGHT = 88f;
    public static final float THUMB_WIDTH = 136f;
    public static final float THUMB_HEIGHT = 59f;

    protected final BreachConservatory conservatory;
    protected final InteractionDialogAPI dialog;
    protected final Host host;
    protected final AquariumTankPanel tank;
    protected final float height;
    protected final int visibleRows;

    protected CustomPanelAPI root;
    protected CustomPanelAPI tankPanel;
    protected boolean tankShown;
    protected CustomPanelAPI body;
    protected TooltipMakerAPI grid;
    protected PositionAPI gridViewport;
    protected final Map<Backdrop, PositionAPI> tiles = new LinkedHashMap<>();
    protected float gridOffset;

    protected final List<Runnable> pending = new ArrayList<>();
    protected boolean dirty;

    public AquariumManagePanel(BreachConservatory conservatory, InteractionDialogAPI dialog,
                               Host host) {
        this.conservatory = conservatory;
        this.dialog = dialog;
        this.host = host;

        tank = new AquariumTankPanel(conservatory, dialog);
        visibleRows = Global.getSettings().getScreenHeight() >= TALL_SCREEN ? 3 : 2;
        height = GRID_Y + visibleRows * (TILE_HEIGHT + TILE_GAP) - TILE_GAP;
    }

    public void show() {
        root = dialog.getVisualPanel().showCustomPanel(WIDTH, height, this);
        tankPanel = root.createCustomPanel(AquariumTankScript.PANEL_WIDTH,
                AquariumTankScript.PANEL_HEIGHT, tank);

        rebuild();
    }

    @Override
    public void advance(float amount) {
        if (root == null) return;

        // actions change the panel tree, so they wait until input dispatch is over
        if (!pending.isEmpty()) {
            List<Runnable> actions = new ArrayList<>(pending);
            pending.clear();

            for (Runnable action : actions) action.run();
        }

        if (dirty) {
            dirty = false;
            rebuild();
        }

        tank.setPreview(findHovered());
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (buttonId instanceof Runnable action) action.run();
    }

    protected Runnable later(Runnable action) {
        return () -> pending.add(action);
    }

    protected void rebuild() {
        ScrollPanelAPI scroller = grid == null ? null : grid.getExternalScroller();
        if (scroller != null) gridOffset = scroller.getYOffset();

        if (body != null) root.removeComponent(body);

        tiles.clear();
        body = UiKit.plain(root, WIDTH, height);
        root.addComponent(body).inTL(0f, 0f);

        UiKit.title(body, WIDTH, "Aquarium", getColonyName(), tooltip -> tooltip.addPara(
                "The backdrop can be changed at any time. More can be found through quests"
                        + " or purchased from a certain crab merchant.", 0f));

        buildTank();
        buildSpecimens();
        buildBackdrops();
    }

    protected String getColonyName() {
        return conservatory.getMarket() == null ? null : conservatory.getMarket().getName();
    }

    protected void buildTank() {
        boolean on = conservatory.isAquariumEnabled();

        if (on && !tankShown) {
            root.addComponent(tankPanel).inTL(0f, TANK_Y);
        } else if (!on && tankShown) {
            root.removeComponent(tankPanel);
        }
        tankShown = on;

        if (!on) {
            UiKit.border(body, 0f, TANK_Y, AquariumTankScript.PANEL_WIDTH,
                    AquariumTankScript.PANEL_HEIGHT);
        }
    }

    protected void buildSpecimens() {
        int held = conservatory.getAquariumFish().size();
        int aboard = AquariumTransfers.countFishAboard();
        float y = TANK_Y;

        UiKit.heading(body, SIDE_X, y, SIDE_WIDTH, "Specimens", null);
        y += UiKit.HEADING_HEIGHT + UiKit.ROW_GAP;

        addCount(y, "In the tank", held);
        y += 18f;
        addCount(y, "Aboard", aboard);
        y += 18f + UiKit.GAP;

        UiKit.button(body, SIDE_X, y, TRANSFER_WIDTH, "Add...", CutStyle.TL_BR,
                aboard == 0 ? null : later(this::openAddPicker));
        UiKit.button(body, SIDE_X + TRANSFER_WIDTH + TRANSFER_GAP, y, TRANSFER_WIDTH, "Take...",
                CutStyle.TL_BR, held == 0 ? null : later(this::openTakePicker));
        y += UiKit.BUTTON_HEIGHT + UiKit.ROW_GAP;

        String reason = aboard == 0 ? "No specimens aboard."
                : held == 0 ? "The tank is empty." : null;

        if (reason != null) {
            UiKit.text(body, SIDE_X, y, SIDE_WIDTH, reason, UiKit.BODY,
                    Misc.getNegativeHighlightColor(), Alignment.LMID);
        }

        UiKit.checkbox(body, SIDE_X, TANK_Y + AquariumTankScript.PANEL_HEIGHT - UiKit.LINE_HEIGHT,
                SIDE_WIDTH, "Display on", conservatory.isAquariumEnabled(),
                later(this::toggleDisplay));
    }

    protected void addCount(float y, String label, int count) {
        UiKit.text(body, SIDE_X, y, SIDE_WIDTH - 50f, label, UiKit.BODY, Misc.getTextColor(),
                Alignment.LMID);
        UiKit.text(body, SIDE_X + SIDE_WIDTH - 50f, y, 50f, Misc.getWithDGS(count), UiKit.BODY,
                Misc.getHighlightColor(), Alignment.RMID);
    }

    protected void buildBackdrops() {
        List<Backdrop> owned = Backdrops.getOwned();
        Backdrop hanging = Backdrops.getHanging(conservatory);

        UiKit.heading(body, 0f, BACKDROP_Y, LIST_WIDTH, "Backdrop", owned.size() + " owned");

        grid = body.createUIElement(WIDTH + UiKit.INSET, height - GRID_Y, true);

        for (int first = 0; first < owned.size(); first += COLUMNS) {
            CustomPanelAPI line = UiKit.plain(body, LIST_WIDTH, TILE_HEIGHT);

            for (int column = 0; column < COLUMNS && first + column < owned.size(); column++) {
                Backdrop backdrop = owned.get(first + column);

                addTile(line, GRID_MARGIN + column * (TILE_WIDTH + TILE_GAP), backdrop,
                        hanging != null && backdrop.id.equals(hanging.id));
            }

            grid.addCustom(line, first == 0 ? 0f : TILE_GAP);
        }

        gridViewport = body.addUIElement(grid).inTL(-UiKit.INSET, GRID_Y);

        ScrollPanelAPI scroller = grid.getExternalScroller();

        if (scroller != null) {
            float max = Math.max(0f, grid.getHeightSoFar() - (height - GRID_Y));
            scroller.setYOffset(Math.max(0f, Math.min(max, gridOffset)));
        }
    }

    protected void addTile(CustomPanelAPI line, float x, Backdrop backdrop, boolean hanging) {
        UiKit.Row tile = UiKit.row(line, x, 0f, TILE_WIDTH, TILE_HEIGHT, hanging,
                later(() -> hang(backdrop)));
        tiles.put(backdrop, tile.panel.getPosition());

        float thumbX = (TILE_WIDTH - THUMB_WIDTH) / 2f;
        SpriteAPI art = Backdrops.isBare(backdrop) ? null : SpriteLoader.loadSprite(backdrop.sprite);

        if (art == null) {
            UiKit.border(tile.panel, thumbX, 2f, THUMB_WIDTH, THUMB_HEIGHT);
        } else {
            UiKit.sprite(tile.panel, thumbX, 2f, THUMB_WIDTH, THUMB_HEIGHT, art, Color.WHITE);
        }

        float nameY = 2f + THUMB_HEIGHT;

        UiKit.textIn(tile.panel, 4f, nameY, TILE_WIDTH - 8f, TILE_HEIGHT - nameY,
                backdrop.getDisplayName(), UiKit.BODY, backdrop.rarity.color, Alignment.MID);
    }

    protected Backdrop findHovered() {
        if (gridViewport == null) return null;

        float x = Global.getSettings().getMouseX();
        float y = Global.getSettings().getMouseY();

        if (!contains(gridViewport, x, y)) return null;

        for (Map.Entry<Backdrop, PositionAPI> tile : tiles.entrySet()) {
            if (contains(tile.getValue(), x, y)) return tile.getKey();
        }

        return null;
    }

    protected static boolean contains(PositionAPI pos, float x, float y) {
        return x >= pos.getX() && x <= pos.getX() + pos.getWidth()
                && y >= pos.getY() && y <= pos.getY() + pos.getHeight();
    }

    protected void hang(Backdrop backdrop) {
        Backdrop hanging = Backdrops.getHanging(conservatory);
        boolean changed = hanging == null || !backdrop.id.equals(hanging.id);

        if (changed && Backdrops.hang(conservatory, backdrop)) host.reportHung(backdrop);

        // the area button has already flipped its own check mark
        dirty = true;
    }

    protected void toggleDisplay() {
        conservatory.setAquariumEnabled(!conservatory.isAquariumEnabled());
        host.reportDisplay(conservatory.isAquariumEnabled());
        dirty = true;
    }

    protected void openAddPicker() {
        AquariumTransfers.openAddPicker(dialog, conservatory, moved -> {
            host.reportMove(moved, true);
            dirty = true;
        });
    }

    protected void openTakePicker() {
        AquariumTransfers.openTakePicker(dialog, conservatory, moved -> {
            host.reportMove(moved, false);
            dirty = true;
        });
    }
}
