package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.ui.ShopUi;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.reflection.ReflectionUtils;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class FishIntelPlanetPanel implements EveryFrameScript {
    public static final float GAP = 10f;
    public static final float CELL = 38f;
    public static final float CELL_GAP = 6f;
    public static final float ICON_SHARE = 0.66f;
    public static final float TITLE_HEIGHT = 30f;
    public static final float INNER_PAD = 10f;

    protected Object detailCard;
    protected Object planetsPanel;
    protected CustomPanelAPI fishPanel;
    protected boolean failed = false;

    protected static class BoxPlugin extends BaseCustomUIPanelPlugin {
        protected PositionAPI pos;

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

            // the field is transparent black, the way the screen's own panels sit on it
            ShopUi.drawQuad(x, y, w, h, Color.BLACK, 0.7f * alphaMult);

            float titleAlpha = 0.65f;
            ShopUi.drawQuad(x, y + h - TITLE_HEIGHT, w, TITLE_HEIGHT,
                    Misc.getDarkPlayerColor(), titleAlpha * alphaMult);

            LazyFont body = ShopUi.getBodyFont();
            if (body != null) {
                LazyFont.DrawableString title = body.createText("Patterns",
                        ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult),
                        body.getBaseHeight());

                int titleX = Math.round(x + (w - title.getWidth()) * 0.5f);
                int titleY = Math.round(y + h - (TITLE_HEIGHT - title.getHeight()) * 0.5f);

                LazyFont.DrawableString rim = body.createText("Patterns",
                        ShopUi.withAlpha(Color.BLACK, alphaMult), body.getBaseHeight());
                rim.draw(titleX - 1, titleY);
                rim.draw(titleX + 1, titleY);
                rim.draw(titleX, titleY - 1);
                rim.draw(titleX, titleY + 1);

                title.draw(titleX, titleY);
            }

            // the border wears exactly the title bar's colour, so the bar reads as part of the frame
            Color border = Misc.getDarkPlayerColor();
            ShopUi.drawQuad(x, y, w, 1f, border, titleAlpha * alphaMult);
            ShopUi.drawQuad(x, y + h - 1f, w, 1f, border, titleAlpha * alphaMult);
            ShopUi.drawQuad(x, y, 1f, h, border, titleAlpha * alphaMult);
            ShopUi.drawQuad(x + w - 1f, y, 1f, h, border, titleAlpha * alphaMult);
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        Object planets = findPlanetsPanel();

        if (planets == null) {
            drop();
            return;
        }

        if (planets != planetsPanel) {
            drop();
            planetsPanel = planets;
        }

        if (failed) return;

        try {
            Object card = findDetailCard(planets);

            if (card != detailCard) {
                removePanel();
                detailCard = card;

                if (card != null) attachPanel(card);
            }
        } catch (Throwable t) {
            Global.getLogger(FishIntelPlanetPanel.class)
                    .warn("Fish panel bowing out of the planets view", t);
            removePanel();
            failed = true;
        }
    }

    protected Object findPlanetsPanel() {
        if (Global.getSector() == null) return null;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null || ui.getCurrentCoreTab() != CoreUITabId.INTEL) return null;

        Object core = CoreUiCrawler.getCoreUi();
        if (core == null) return null;

        Object tab = ReflectionUtils.invokeIfExists(core, "getCurrentTab");
        if (tab == null) return null;

        for (ReflectionUtils.ReflectedField field : ReflectionUtils.getFieldsMatching(
                tab.getClass(), null, null, null, null, false)) {
            Object value = field.get(tab);
            if (value != null && ReflectionUtils.hasMethodOfName(value, "getPlanetList2")) {
                return value;
            }
        }

        return null;
    }

    protected Object findDetailCard(Object planets) {
        for (ReflectionUtils.ReflectedField field : ReflectionUtils.getFieldsMatching(
                planets.getClass(), null, null, null, null, false)) {
            Object value = field.get(planets);
            if (value != null && ReflectionUtils.hasMethodOfName(value, "getLayInCourse")) {
                return value;
            }
        }

        return null;
    }

    protected void attachPanel(Object card) {
        Object entity = ReflectionUtils.invokeIfExists(card, "getEntity");
        if (!(entity instanceof SectorEntityToken)) return;

        if (!(((SectorEntityToken) entity).getContainingLocation() instanceof StarSystemAPI)) {
            return;
        }
        StarSystemAPI system =
                (StarSystemAPI) ((SectorEntityToken) entity).getContainingLocation();

        List<FishSpec> known = FishPresence.getKnownFishIn(system);
        int unknown = FishPresence.getUnknownCountIn(system);
        if (known.isEmpty() && unknown == 0) return;

        PositionAPI cardPos = ((UIComponentAPI) card).getPosition();
        PositionAPI panelPos = ((UIComponentAPI) planetsPanel).getPosition();

        float x = cardPos.getX() + cardPos.getWidth() + GAP - panelPos.getX();
        float y = (panelPos.getY() + panelPos.getHeight())
                - (cardPos.getY() + cardPos.getHeight());

        float width = panelPos.getWidth() - x - GAP;
        if (width < CELL + 30f) return;

        // the card it stands beside is the ceiling: never taller, scroll instead
        float maxHeight = cardPos.getHeight();

        float innerWidth = width - INNER_PAD * 2f;
        int total = known.size() + unknown;
        int perRow = Math.max(1, (int) ((innerWidth + CELL_GAP) / (CELL + CELL_GAP)));
        int rows = (total + perRow - 1) / perRow;

        float rowsNeeded = rows * CELL + (rows - 1) * CELL_GAP;
        float contentBudget = maxHeight - TITLE_HEIGHT - INNER_PAD * 2f;

        boolean scrolls = rowsNeeded > contentBudget;
        float contentHeight = Math.min(rowsNeeded, contentBudget);

        float height = TITLE_HEIGHT + INNER_PAD * 2f + contentHeight;

        fishPanel = Global.getSettings().createCustom(width, height, new BoxPlugin());

        buildContent(fishPanel, innerWidth, contentHeight, scrolls, perRow, known, unknown);

        ((UIPanelAPI) planetsPanel).addComponent(fishPanel)
                .setSize(width, height)
                .inTL(x, y);
    }

    protected void buildContent(CustomPanelAPI panel, float innerWidth, float contentHeight,
                                boolean scrolls, int perRow, List<FishSpec> known, int unknown) {
        TooltipMakerAPI content = panel.createUIElement(innerWidth, contentHeight, scrolls);

        int total = known.size() + unknown;
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

                if (spec != null) {
                    content.addTooltipTo(FishTooltips.create(spec), cell,
                            TooltipMakerAPI.TooltipLocation.BELOW);
                }
            }

            content.addCustom(rowPanel, row == 0 ? 0f : CELL_GAP);
            placed += inThisRow;
        }

        panel.updateUIElementSizeAndMakeItProcessInput(content);
        panel.addUIElement(content).inTL(INNER_PAD, TITLE_HEIGHT + INNER_PAD);
    }

    protected void removePanel() {
        if (fishPanel != null && planetsPanel != null) {
            try {
                ((UIPanelAPI) planetsPanel).removeComponent(fishPanel);
            } catch (Throwable ignored) {
                // the screen is already gone, and the panel with it
            }
        }

        fishPanel = null;
        detailCard = null;
    }

    protected void drop() {
        removePanel();
        planetsPanel = null;
        failed = false;
    }
}
