package catchrelease.campaign.fish.colony;

import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import java.util.List;

/**
 * Hangs the conservatory's aquarium on the colony's main menu: the tank panel sits in the right
 * sidebar directly below the planet's interaction image. Just the glass - stocking and the
 * display switch live in the aquarium office, off the industry's own click-menu.
 * <p>
 * The crawl is by capability, like every screen we stand on: the encounter dialog comes off
 * {@code CampaignState}, and the planet visual inside it is the child with {@code getPlanet}.
 * The tank mounts when the dialog's market has a working, switched-on conservatory and the
 * docked core UI is not covering the menu; it unmounts the moment any of that stops being true.
 * Every step fails soft - a surprise means no tank, and the menu is exactly as vanilla drew it.
 */
public class AquariumTankScript implements EveryFrameScript {

    public static final String APP_DRIVER = "com.fs.state.AppDriver";
    public static final String CAMPAIGN_STATE = "com.fs.starfarer.campaign.CampaignState";

    public static final float GAP = 8f;
    public static final float TANK_HEIGHT = 170f;
    public static final float PANEL_HEIGHT = TANK_HEIGHT + AquariumTankPanel.WALL_PAD * 2f;

    /** The dialog the tank currently stands in. A new dialog means a fresh mount. */
    protected Object dialog;
    protected CustomPanelAPI panel;

    /** Latched when a mount went wrong; cleared when the dialog closes. */
    protected boolean failed = false;

    /** Registered every load; transient, so a save never carries the watcher. */
    public static void register() {
        Global.getSector().addTransientScript(new AquariumTankScript());
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
        Object current = findDialog();

        if (current != dialog) {
            drop();
            dialog = current;
        }

        if (dialog == null || failed) return;

        try {
            boolean wanted = getConservatory(dialog) != null && !isCoreCovering(dialog);

            if (wanted && panel == null) mount();
            if (!wanted && panel != null) removePanel();
        } catch (Throwable t) {
            Global.getLogger(AquariumTankScript.class)
                    .warn("Aquarium bowing out of the colony menu", t);
            removePanel();
            failed = true;
        }
    }

    /** The open encounter dialog, if there is one. */
    protected Object findDialog() {
        try {
            Object driver = ReflectionUtils.invokeStatic(Class.forName(APP_DRIVER), "getInstance");
            Object state = ReflectionUtils.invokeIfExists(driver, "getCurrentState");
            if (state == null || !CAMPAIGN_STATE.equals(state.getClass().getName())) return null;

            return ReflectionUtils.invokeIfExists(state, "getEncounterDialog");
        } catch (Throwable t) {
            return null;
        }
    }

    /** The working, switched-on conservatory behind the dialog's market, or null. */
    protected BreachConservatory getConservatory(Object dialog) {
        if (!(dialog instanceof InteractionDialogAPI)) return null;

        SectorEntityToken target = ((InteractionDialogAPI) dialog).getInteractionTarget();
        if (target == null) return null;

        MarketAPI market = target.getMarket();
        if (market == null || !market.isPlayerOwned()) return null;

        BreachConservatory conservatory = BreachConservatory.get(market);
        if (conservatory == null || !conservatory.isAquariumEnabled()) return null;

        return conservatory;
    }

    /**
     * Whether the docked core UI (trade, refit, the colony screen) is up over the menu.
     * <p>
     * Not a null check: the dialog keeps the core UI object for its whole life once one has
     * been opened - dismissal only fades it out, nothing nulls the field - so {@code getCoreUI}
     * answering is no proof anything is showing. The fader is what actually knows, and it is
     * why the tank used to vanish for good after any colony-screen visit: the check read the
     * husk as coverage and never mounted again until re-docking rebuilt the dialog.
     */
    protected boolean isCoreCovering(Object dialog) {
        Object core = ReflectionUtils.invokeIfExists(dialog, "getCoreUI");
        if (core == null) return false;

        Object fader = ReflectionUtils.invokeIfExists(core, "getFader");
        if (fader == null) return true;

        Object fadedOut = ReflectionUtils.invokeIfExists(fader, "isFadedOut");

        return !(fadedOut instanceof Boolean) || !((Boolean) fadedOut);
    }

    /** The planet's interaction image: the dialog child that knows what planet it is showing. */
    protected UIComponentAPI findPlanetVisual(Object dialog) {
        Object children = ReflectionUtils.invokeIfExists(dialog, "getChildrenCopy");
        if (!(children instanceof List)) return null;

        for (Object child : (List<?>) children) {
            if (child instanceof UIComponentAPI
                    && ReflectionUtils.hasMethodOfName(child, "getPlanet")) {
                return (UIComponentAPI) child;
            }
        }

        return null;
    }

    /** Builds the tank under the planet image. Just the glass - stocking is the office's job. */
    protected void mount() {
        UIComponentAPI visual = findPlanetVisual(dialog);
        if (visual == null) return;

        BreachConservatory conservatory = getConservatory(dialog);
        if (conservatory == null) return;

        PositionAPI visualPos = visual.getPosition();
        PositionAPI dialogPos = ((UIComponentAPI) dialog).getPosition();

        float width = visualPos.getWidth();
        if (width < 120f) return;

        //no air below the image on this resolution - better no tank than a tank over the text
        if (visualPos.getY() - GAP - PANEL_HEIGHT < dialogPos.getY()) return;

        float x = visualPos.getX() - dialogPos.getX();
        float yFromTop = (dialogPos.getY() + dialogPos.getHeight())
                - (visualPos.getY() - GAP);

        AquariumTankPanel plugin =
                new AquariumTankPanel(conservatory, (InteractionDialogAPI) dialog);

        panel = Global.getSettings().createCustom(width, PANEL_HEIGHT, plugin);

        ((UIPanelAPI) dialog).addComponent(panel)
                .setSize(width, PANEL_HEIGHT)
                .inTL(x, yFromTop);
    }

    protected void removePanel() {
        if (panel != null && dialog instanceof UIPanelAPI) {
            try {
                ((UIPanelAPI) dialog).removeComponent(panel);
            } catch (Throwable ignored) {
                //the dialog is already gone, and the panel with it
            }
        }

        panel = null;
    }

    protected void drop() {
        removePanel();
        dialog = null;
        failed = false;
    }
}
