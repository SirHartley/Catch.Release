package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.shop.ShopUi;
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

/**
 * Puts a fish panel on the intel screen's Planets view, in the empty air to the right of the
 * planet detail card: which species can be caught in the viewed planet's system, as round
 * icon holders in a boxed panel styled after the card it stands beside - the title bar runs
 * border to border and matches the card's own header weight, and the box never grows taller
 * than the card; a stock that will not fit scrolls inside instead.
 * <p>
 * The knowledge rules are the map's: a caught species wears its art, one known only from survey
 * data wears the generic mark - both ringed in their rarity's colour, with the same species
 * tooltip the sidebar uses and the same F2-to-codex key - and a species the player has never
 * encountered shows as a bare question mark that answers nothing.
 * <p>
 * The crawl is by capability, like the sector map's: the planets view is the thing on the intel
 * tab with {@code getPlanetList2}, and the detail card inside it is the thing with
 * {@code getLayInCourse}. The card is rebuilt on every planet selection, so a new card instance
 * is the rebuild signal. Every step fails soft - a surprise means no panel, and the intel screen
 * is exactly as vanilla made it.
 */
public class FishIntelPlanetPanel implements EveryFrameScript {

    public static final float GAP = 10f;
    public static final float CELL = 38f;
    public static final float CELL_GAP = 6f;
    public static final float ICON_SHARE = 0.66f;

    /** The card's own header weight - half again the old heading, border to border. */
    public static final float TITLE_HEIGHT = 30f;

    public static final float INNER_PAD = 10f;

    /** The detail card this panel is currently standing beside. A new card means a rebuild. */
    protected Object detailCard;

    protected Object planetsPanel;
    protected CustomPanelAPI fishPanel;

    /** Latched when this tab-open went wrong; cleared when the intel tab is left. */
    protected boolean failed = false;

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

    /** The planets view, if the intel tab is up and showing it - the thing with getPlanetList2. */
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

    /** The planet detail card inside the planets view - the thing with getLayInCourse. */
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

    /** Builds the panel for the card's planet and hangs it in the air to the card's right. */
    protected void attachPanel(Object card) {
        Object entity = ReflectionUtils.invokeIfExists(card, "getEntity");
        if (!(entity instanceof SectorEntityToken)) return;

        if (!(((SectorEntityToken) entity).getContainingLocation() instanceof StarSystemAPI)) {
            return;
        }
        StarSystemAPI system =
                (StarSystemAPI) ((SectorEntityToken) entity).getContainingLocation();

        List<FishSpec> known = getKnownFish(system);
        int unknown = getUnknownCount(system);
        if (known.isEmpty() && unknown == 0) return;

        //the card's slot, in the planets panel's own inTL coordinates
        PositionAPI cardPos = ((UIComponentAPI) card).getPosition();
        PositionAPI panelPos = ((UIComponentAPI) planetsPanel).getPosition();

        float x = cardPos.getX() + cardPos.getWidth() + GAP - panelPos.getX();
        float y = (panelPos.getY() + panelPos.getHeight())
                - (cardPos.getY() + cardPos.getHeight());

        float width = panelPos.getWidth() - x - GAP;
        if (width < CELL + 30f) return; //no air to stand in on this resolution

        //the card it stands beside is the ceiling: never taller, scroll instead
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

    /** The cell grid, in its own element below the title bar - scrolling when the stock is deep. */
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

                CustomPanelAPI cell = panel.createCustomPanel(CELL, CELL, new HolderPlugin(spec));
                rowPanel.addComponent(cell).inTL(i * (CELL + CELL_GAP), 0f);

                //the sidebar's own species card - the question marks stay questions
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

    /** Known species catchable in the system, caught first so the art leads the row. */
    protected List<FishSpec> getKnownFish(StarSystemAPI system) {
        List<FishSpec> caught = new ArrayList<>();
        List<FishSpec> surveyed = new ArrayList<>();

        SectorRegion at = SectorRegion.of(system);
        if (at == null) return caught;

        for (FishSpec spec : catchrelease.helper.loading.FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (!spec.regions.contains(at)) continue;
            if (!FishPresence.isKnown(spec)) continue;

            if (FishLog.isCaught(spec.id)) caught.add(spec);
            else surveyed.add(spec);
        }

        caught.addAll(surveyed);

        return caught;
    }

    /** How many species live here that the player has never heard of - counted, never named. */
    protected int getUnknownCount(StarSystemAPI system) {
        SectorRegion at = SectorRegion.of(system);
        if (at == null) return 0;

        int count = 0;

        for (FishSpec spec : catchrelease.helper.loading.FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (!spec.regions.contains(at)) continue;
            if (FishPresence.isKnown(spec)) continue;

            count++;
        }

        return count;
    }

    protected void removePanel() {
        if (fishPanel != null && planetsPanel != null) {
            try {
                ((UIPanelAPI) planetsPanel).removeComponent(fishPanel);
            } catch (Throwable ignored) {
                //the screen is already gone, and the panel with it
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

    /** The box itself: dark field, one-pixel border, and the border-to-border title bar. */
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

            ShopUi.drawQuad(x, y, w, h, Color.BLACK, 0.8f * alphaMult);
            ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(), 0.07f * alphaMult);

            //the title bar: flush with the borders on every side, the card's own header weight
            ShopUi.drawQuad(x, y + h - TITLE_HEIGHT, w, TITLE_HEIGHT,
                    Misc.getDarkPlayerColor(), 0.65f * alphaMult);

            LazyFont body = ShopUi.getBodyFont();
            if (body != null) {
                LazyFont.DrawableString title = body.createText("LOCAL CATCH",
                        ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult),
                        body.getBaseHeight());
                title.draw(Math.round(x + (w - title.getWidth()) * 0.5f),
                        Math.round(y + h - (TITLE_HEIGHT - title.getHeight()) * 0.5f));
            }

            Color border = Misc.getDarkPlayerColor();
            ShopUi.drawQuad(x, y, w, 1f, border, alphaMult);
            ShopUi.drawQuad(x, y + h - 1f, w, 1f, border, alphaMult);
            ShopUi.drawQuad(x, y, 1f, h, border, alphaMult);
            ShopUi.drawQuad(x + w - 1f, y, 1f, h, border, alphaMult);
        }
    }

    /** One round holder: dark disc, the rarity's ring, and the face - art, mark, or question. */
    protected static class HolderPlugin extends BaseCustomUIPanelPlugin {

        protected final FishSpec spec;
        protected PositionAPI pos;

        public HolderPlugin(FishSpec spec) {
            this.spec = spec;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            float x = pos.getCenterX();
            float y = pos.getCenterY();
            float radius = pos.getWidth() * 0.5f;

            //the ring wears the rarity; a species with no name yet wears no colour either
            Color ring = spec == null ? Misc.getDarkPlayerColor() : spec.rarity.color;

            Disc.draw(x, y, radius, Color.BLACK, 0.8f * alphaMult, 0.8f * alphaMult, false);
            Disc.drawOutline(x, y, radius, ring, 0.9f * alphaMult, 1.2f);

            if (spec == null) {
                LazyFont small = ShopUi.getSmallFont();
                if (small != null) {
                    LazyFont.DrawableString mark = small.createText("?",
                            Misc.getGrayColor(), small.getBaseHeight());
                    mark.draw(Math.round(x - mark.getWidth() * 0.5f),
                            Math.round(y + mark.getHeight() * 0.5f));
                }
                return;
            }

            String iconPath = FishLog.isCaught(spec.id)
                    ? FishCodex.getIcon(spec) : FishConstants.ITEM_ICON_FALLBACK;

            SpriteAPI icon = SpriteLoader.loadSprite(iconPath);
            if (icon != null) {
                float iconSize = pos.getWidth() * ICON_SHARE;
                icon.setSize(iconSize, iconSize);
                icon.setColor(Color.WHITE);
                icon.setNormalBlend();
                icon.setAlphaMult(alphaMult);
                icon.renderAtCenter(Math.round(x), Math.round(y));
            }
        }

        /** The codex key, the same one the sidebar's rows answer. */
        @Override
        public void processInput(List<InputEventAPI> events) {
            if (pos == null || spec == null) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;
                if (!event.isKeyDownEvent() || event.getEventValue() != Keyboard.KEY_F2) continue;

                if (!ShopUi.contains(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                        Global.getSettings().getMouseX(), Global.getSettings().getMouseY())) {
                    continue;
                }

                event.consume();
                FishCodex.show(spec.id);
                return;
            }
        }
    }
}
