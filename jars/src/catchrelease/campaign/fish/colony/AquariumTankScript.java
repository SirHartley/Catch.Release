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


public class AquariumTankScript implements EveryFrameScript {

    public static final String APP_DRIVER = "com.fs.state.AppDriver";
    public static final String CAMPAIGN_STATE = "com.fs.starfarer.campaign.CampaignState";


    public static final float COVERED = 0.999f;

    public static final float GAP = 8f;
    public static final float TANK_HEIGHT = 170f;
    public static final float PANEL_HEIGHT = TANK_HEIGHT + AquariumTankPanel.WALL_PAD * 2f;


    public static final float PANEL_WIDTH = 400f;


    protected static float mountedWidth = 0f;


    public static float getPanelWidth() {
        return mountedWidth > 0f ? mountedWidth : PANEL_WIDTH;
    }


    protected Object dialog;
    protected CustomPanelAPI panel;


    protected boolean failed = false;


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
            boolean wanted = getConservatory(dialog) != null
                    && coreCoverage(dialog) < COVERED
                    && findPlanetVisual(dialog) != null;

            if (wanted && panel == null) mount();
            if (!wanted && panel != null) removePanel();
        } catch (Throwable t) {
            Global.getLogger(AquariumTankScript.class)
                    .warn("Aquarium bowing out of the colony menu", t);
            removePanel();
            failed = true;
        }
    }


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


    protected float coreCoverage(Object dialog) {
        Object core = ReflectionUtils.invokeIfExists(dialog, "getCoreUI");
        if (core == null) return 0f;

        Object fader = ReflectionUtils.invokeIfExists(core, "getFader");
        if (fader == null) return 1f;

        Object brightness = ReflectionUtils.invokeIfExists(fader, "getBrightness");
        if (brightness instanceof Float) return (Float) brightness;

        Object fadedOut = ReflectionUtils.invokeIfExists(fader, "isFadedOut");

        return fadedOut instanceof Boolean && (Boolean) fadedOut ? 0f : 1f;
    }


    protected UIComponentAPI findPlanetVisual(Object dialog) {
        Object children = ReflectionUtils.invokeIfExists(dialog, "getChildrenCopy");
        if (!(children instanceof List)) return null;

        for (Object child : (List<?>) children) {
            if (child instanceof UIComponentAPI
                    && ReflectionUtils.hasMethodOfName(child, "getPlanet")
                    && isShowing(child)) {
                return (UIComponentAPI) child;
            }
        }

        return null;
    }


    protected boolean isShowing(Object component) {
        Object fader = ReflectionUtils.invokeIfExists(component, "getFader");
        if (fader == null) return false;

        Object out = ReflectionUtils.invokeIfExists(fader, "isFadedOut");
        Object going = ReflectionUtils.invokeIfExists(fader, "isFadingOut");

        if (!(out instanceof Boolean) || !(going instanceof Boolean)) return false;

        return !((Boolean) out) && !((Boolean) going);
    }


    protected void mount() {
        UIComponentAPI visual = findPlanetVisual(dialog);
        if (visual == null) return;

        BreachConservatory conservatory = getConservatory(dialog);
        if (conservatory == null) return;

        PositionAPI visualPos = visual.getPosition();
        PositionAPI dialogPos = ((UIComponentAPI) dialog).getPosition();

        float width = visualPos.getWidth();
        if (width < 120f) return;

        if (visualPos.getY() - GAP - PANEL_HEIGHT < dialogPos.getY()) return;

        float x = visualPos.getX() - dialogPos.getX();
        float yFromTop = (dialogPos.getY() + dialogPos.getHeight())
                - (visualPos.getY() - GAP);

        AquariumTankPanel plugin =
                new AquariumTankPanel(conservatory, (InteractionDialogAPI) dialog);

        // what the menu actually gave us, for the previews that cannot ask
        mountedWidth = width;

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
