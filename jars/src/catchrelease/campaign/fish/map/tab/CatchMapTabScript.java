package catchrelease.campaign.fish.map.tab;

import catchrelease.campaign.fish.map.FishMapView;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Hangs the catch map off the intel screen as a fourth tab, next to Intel, Planets and Factions.
 * <p>
 * The screen offers no seam for this, so the tab is placed by hand: an every-frame watch waits for
 * the intel screen to be the open core tab, appends a button after the end of its sub-tab row, and
 * swaps the mod's own content panel in and out as the buttons are worked. The screen is built
 * fresh on every open, so the insertion is redone each time - only the {@link FishMapView} inside
 * survives, which is what keeps the camera and the filters where the player left them.
 * <p>
 * Coexistence with whatever else is on that screen is the design constraint, and it is held four
 * ways. The button goes after the measured end of the row, never at a fixed coordinate, so it
 * lands after anyone who got there first. A stranger landing on top of the button afterwards moves
 * the button, never the stranger. The content swap is polite: activating sweeps the screen's own
 * button-to-panel map - faders out, highlights off, the exact loop vanilla runs - touching nothing
 * that is not in that map, and the moment anything in the map lights up again the mod's panel
 * withdraws on its own. And the row's registry politics were checked against the one mod known to
 * manage core-UI tabs: AshLib's listener registry turns out to govern the Command screen only, so
 * on this screen there is no registry to defer to - the measuring and the withdrawing above are
 * the whole of the diplomacy. Every step fails soft: any surprise means the tab bows out of that
 * screen-open and the ability keeps working.
 * <p>
 * Selection is read through {@code isHighlighted()}, never {@code isChecked()} - the game's
 * buttons flip their checked flag on every click regardless of meaning, and the intel row's real
 * selection state is the highlight fader.
 */
public class CatchMapTabScript implements EveryFrameScript {

    public static final String TAB_LABEL = "Catch";

    /** The vanilla row's own geometry: 130x18 buttons, 1px apart, content 19px down. */
    public static final float BUTTON_WIDTH = 130f;
    public static final float BUTTON_HEIGHT = 18f;
    public static final float BUTTON_GAP = 1f;
    public static final float CONTENT_TOP = 19f;

    public static final float PAD = 12f;

    /** The one map view, kept across screen opens so the camera and filters are never reset. */
    protected final FishMapView view = new FishMapView();

    /** The intel screen instance the tab is currently attached to. A new open means a new one. */
    protected Object intel;

    /** Latched when this screen-open went wrong; cleared when the screen is rebuilt. */
    protected boolean failed = false;

    protected CustomPanelAPI holder;
    protected PositionAPI holderPos;
    protected ButtonAPI button;
    protected CustomPanelAPI content;
    protected boolean attached = false;

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
        Object screen = findIntelScreen();

        if (screen == null) {
            drop();
            return;
        }

        if (screen != intel) {
            clearComponents();
            intel = screen;
            failed = false;

            try {
                insert();
            } catch (Throwable t) {
                fail(t);
            }
        }

        if (failed || holder == null) return;

        try {
            maintain();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** The intel screen if it is on the glass right now, else null. */
    protected Object findIntelScreen() {
        if (Global.getSector() == null) return null;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null || ui.getCurrentCoreTab() != CoreUITabId.INTEL) return null;

        Object core = CoreUiCrawler.getCoreUi();
        if (core == null) return null;

        return CoreUiCrawler.getIntelScreen(core);
    }

    /**
     * Builds the button and the content panel and puts the button after the end of the row. The
     * content panel is built now but attached only when the tab is picked - the screen's own
     * panels stay put and get hidden by fader, but a guest does better to not be present at all
     * while it is not wanted.
     */
    protected void insert() {
        UIPanelAPI panel = (UIPanelAPI) intel;
        PositionAPI screenPos = ((UIComponentAPI) intel).getPosition();

        holder = Global.getSettings().createCustom(BUTTON_WIDTH, BUTTON_HEIGHT, new ButtonHost());

        TooltipMakerAPI element = holder.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        button = element.addButton(TAB_LABEL, TAB_LABEL, Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TOP,
                BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        holder.addUIElement(element).inTL(0f, 0f);

        holderPos = panel.addComponent(holder);
        holderPos.setSize(BUTTON_WIDTH, BUTTON_HEIGHT)
                .inTL(findRowEnd(screenPos) - screenPos.getX() + BUTTON_GAP, 0f);

        float width = screenPos.getWidth();
        float height = screenPos.getHeight() - CONTENT_TOP;

        content = Global.getSettings().createCustom(width, height, new ContentHost());
        view.mount(content, PAD, PAD, width - PAD * 2f, height - PAD * 2f);

        button.unhighlight();
    }

    /**
     * The right edge of the rightmost thing living in the row band along the screen's top -
     * vanilla's three buttons, plus whatever other mods have put there. Measured, never assumed,
     * which is the whole difference between appending and colliding.
     */
    protected float findRowEnd(PositionAPI screenPos) {
        float top = screenPos.getY() + screenPos.getHeight();
        float bandFloor = top - BUTTON_HEIGHT - 4f;
        float end = screenPos.getX();

        for (Object child : getChildren()) {
            if (child == holder || !(child instanceof UIComponentAPI)) continue;

            PositionAPI pos = ((UIComponentAPI) child).getPosition();
            if (pos == null) continue;

            //row things are short and live along the top edge; content panels are neither
            if (pos.getHeight() > BUTTON_HEIGHT + 6f) continue;
            if (pos.getCenterY() < bandFloor) continue;

            end = Math.max(end, pos.getX() + pos.getWidth());
        }

        return end;
    }

    /**
     * The steady-state watch: a stranger landing on the button moves the button to the new end of
     * the row - never the stranger - and any button in the screen's own tab map lighting up means
     * the screen has been handed back, so the content withdraws.
     */
    protected void maintain() {
        PositionAPI screenPos = ((UIComponentAPI) intel).getPosition();

        if (holderPos != null && isOverlappedInRow(screenPos)) {
            holderPos.inTL(findRowEnd(screenPos) - screenPos.getX() + BUTTON_GAP, 0f);
        }

        if (attached && isAnyMappedButtonHighlighted()) {
            deactivate();
        }
    }

    protected boolean isOverlappedInRow(PositionAPI screenPos) {
        PositionAPI mine = ((UIComponentAPI) holder).getPosition();
        if (mine == null) return false;

        float top = screenPos.getY() + screenPos.getHeight();
        float bandFloor = top - BUTTON_HEIGHT - 4f;

        for (Object child : getChildren()) {
            if (child == holder || !(child instanceof UIComponentAPI)) continue;

            PositionAPI pos = ((UIComponentAPI) child).getPosition();
            if (pos == null) continue;
            if (pos.getHeight() > BUTTON_HEIGHT + 6f) continue;
            if (pos.getCenterY() < bandFloor) continue;

            boolean apart = pos.getX() + pos.getWidth() <= mine.getX()
                    || pos.getX() >= mine.getX() + mine.getWidth();

            if (!apart) return true;
        }

        return false;
    }

    /**
     * Takes the screen: the exact sweep vanilla runs on a tab pick - every panel in the screen's
     * button-to-panel map faded out, every button in it unhighlighted - and then the mod's own
     * panel where the content goes. Nothing outside that map is touched; a panel some other mod
     * is managing by hand is that mod's business, and it will notice its button the same way this
     * one does.
     */
    protected void activate() {
        try {
            Map<?, ?> tabMap = getVanillaTabMap();
            if (tabMap == null) throw new IllegalStateException("no tab map on the intel screen");

            for (Map.Entry<?, ?> entry : tabMap.entrySet()) {
                Object fader = ReflectionUtils.invokeIfExists(entry.getValue(), "getFader");
                if (fader != null) ReflectionUtils.invokeIfExists(fader, "forceOut");

                if (entry.getKey() instanceof ButtonAPI) ((ButtonAPI) entry.getKey()).unhighlight();
            }

            if (!attached && content != null) {
                PositionAPI screenPos = ((UIComponentAPI) intel).getPosition();

                ((UIPanelAPI) intel).addComponent(content)
                        .setSize(screenPos.getWidth(), screenPos.getHeight() - CONTENT_TOP)
                        .inTL(0f, CONTENT_TOP);

                attached = true;
            }

            if (button != null) {
                button.highlight();

                //the game's buttons toggle this flag on every click whether it means anything or
                //not; parked false so nothing ever mistakes it for state
                button.setChecked(false);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Hands the screen back: the content comes out, the light goes off, vanilla already did the rest. */
    protected void deactivate() {
        if (attached && intel != null && content != null) {
            ((UIPanelAPI) intel).removeComponent(content);
        }

        attached = false;
        if (button != null) button.unhighlight();
    }

    /**
     * The screen's own button-to-panel map, found by shape: the intel screen keeps exactly one
     * {@link Map}-typed field, and this is it. More than one match means the game changed and the
     * answer is to bow out, not to guess.
     */
    protected Map<?, ?> getVanillaTabMap() {
        List<ReflectionUtils.ReflectedField> fields = ReflectionUtils.getFieldsMatching(
                intel.getClass(), null, null, Map.class, null, false);

        if (fields.size() != 1) return null;

        Object value = fields.get(0).get(intel);
        return value instanceof Map ? (Map<?, ?>) value : null;
    }

    protected boolean isAnyMappedButtonHighlighted() {
        Map<?, ?> tabMap = getVanillaTabMap();
        if (tabMap == null) return false;

        for (Object key : tabMap.keySet()) {
            if (key instanceof ButtonAPI && ((ButtonAPI) key).isHighlighted()) return true;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    protected List<Object> getChildren() {
        Object children = ReflectionUtils.invokeIfExists(intel, "getChildrenCopy");
        return children instanceof List ? (List<Object>) children : java.util.Collections.emptyList();
    }

    /** The screen went away, or was never there. Forget everything about it. */
    protected void drop() {
        clearComponents();
        intel = null;
        failed = false;
    }

    protected void clearComponents() {
        holder = null;
        holderPos = null;
        button = null;
        content = null;
        attached = false;
    }

    /**
     * Something on this screen-open did not read the way the recipe expects. Withdraw whatever
     * was placed, log the reason once, and sit the rest of this open out - the ability's dialog
     * still works, so a failed tab costs a convenience, not the feature.
     */
    protected void fail(Throwable t) {
        Global.getLogger(CatchMapTabScript.class)
                .warn("Catch map tab bowing out of this intel screen", t);

        try {
            if (intel != null) {
                if (attached && content != null) ((UIPanelAPI) intel).removeComponent(content);
                if (holder != null) ((UIPanelAPI) intel).removeComponent(holder);
            }
        } catch (Throwable ignored) {
            //the withdrawal itself failing means the screen is already gone
        }

        clearComponents();
        failed = true;
    }

    /** The button's holder panel: a click on the tab is the only thing it ever hears. */
    protected class ButtonHost extends BaseCustomUIPanelPlugin {
        @Override
        public void buttonPressed(Object buttonId) {
            activate();
        }
    }

    /** The content panel's plugin: its only job is keeping the view's polling alive. */
    protected class ContentHost extends BaseCustomUIPanelPlugin {
        @Override
        public void advance(float amount) {
            view.advance(amount);
        }
    }
}
