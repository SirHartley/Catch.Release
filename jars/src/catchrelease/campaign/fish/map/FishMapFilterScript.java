package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FishMapFilterScript implements EveryFrameScript, FishMapPane.Host,
        FishRoutePopup.Host, FishRouteSaveDialog.Host {

    public static final String MEMORY_KEY = "$catchrelease_map_fish_filter";
    public static final float BUTTON_WIDTH = 120f;
    public static final float BUTTON_HEIGHT = 25f;
    public static final float BUTTON_PAD = 3f;
    public static final int VANILLA_ROW_BUTTONS = 6;
    public static final int MAX_SHORTCUT_SLOT = 10;

    public static final float PANE_GAP = 8f;
    public static final float BLOB_RADIUS = 3200f;

    public static final long PENDING_SPECIES_MILLIS = 10_000L;
    protected static String pendingSpeciesId;
    protected static List<FishRequirement> pendingRequirements;
    protected static boolean pendingOverview;
    protected static String pendingSystemId;
    protected static long pendingFocusSetAt;
    protected static boolean pendingMapOpen;

    protected Object mapScreen;
    protected boolean failed = false;
    protected ButtonAPI fishButton;

    protected boolean applied = false;
    protected boolean paneStanding = false;
    protected float originalScrollerWidth = 0f;
    protected FishMapPane pane;

    protected CustomPanelAPI panePanel;
    protected CustomPanelAPI overlayPanel;

    protected FishPresenceOverlay overlay;
    protected FishSystemPane systemPane;
    protected CustomPanelAPI systemPanePanel;

    protected boolean systemApplied = false;
    protected Object shownSystem;
    protected FishRoutePopup popup;
    protected CustomPanelAPI popupPanel;
    protected FishRouteSaveDialog saveDialog;
    protected CustomPanelAPI saveDialogPanel;

    protected float paneX, paneY, paneHeight;
    protected Object arrowList;
    protected final List<Object> injectedArrows = new ArrayList<>();
    protected Object lastRouteSeen;
    protected boolean arrowsIn = false;
    protected final Map<String, FishPresenceField.Mesh> meshCache = new HashMap<>();

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
        openPendingMapWhenCodexCloses();

        Object screen = findMapScreen();

        if (screen == null) {
            drop();
            return;
        }

        if (screen != mapScreen) {
            clearComponents();
            mapScreen = screen;
            failed = false;

            try {
                insertButton();

                mountOverlay();

                if (fishButton != null && isRemembered()) fishButton.setChecked(true);
            } catch (Throwable t) {
                fail(t);
            }
        }

        if (failed || fishButton == null) return;

        try {
            if (hasFreshPendingFocus()) {
                if (!fishButton.isChecked()) fishButton.setChecked(true);

                if (!isHyperViewShown()) {
                    ReflectionUtils.invoke(mapScreen, "notifyMapLocationChanged",
                            Global.getSector().getHyperspace());
                }
            }

            boolean checked = fishButton.isChecked();
            if (checked != isRemembered()) remember(checked);

            boolean wantPane = checked && isHyperViewShown();

            StarSystemAPI viewed = getViewedSystem();
            boolean wantSystem = checked && viewed != null && hasAnyFish(viewed);

            if (applied && !wantPane) deactivate();
            if (systemApplied && (!wantSystem || viewed != shownSystem)) deactivateSystemPane();

            if (!applied && wantPane) activate();
            if (!systemApplied && wantSystem) activateSystemPane(viewed);

            if (applied && wantPane && popupPanel == null) ensurePaneStanding();

            // the route can be closed from under the dialog by the label next to it
            if (saveDialogPanel != null && FishRoute.get() == null) closeSaveDialog();

            if (applied && hasPendingFocus()) applyPendingFocus();

            syncRouteArrows();
        } catch (Throwable t) {
            fail(t);
        }
    }

    protected void syncRouteArrows() {
        FishRoute.Saved route = FishRoute.get();
        List<catchrelease.campaign.fish.intel.FishRouteIntel> saved =
                catchrelease.campaign.fish.intel.FishRouteIntel.getAll();
        boolean hyper = isHyperViewShown();
        boolean liveShown = route != null && !route.stops.isEmpty();
        boolean want = hyper && (liveShown || !saved.isEmpty());

        List<Object> sig = new ArrayList<>();
        sig.add(route);
        sig.addAll(saved);
        if (sig.equals(lastRouteSeen) && want == arrowsIn) return;

        if (arrowList instanceof List) ((List<?>) arrowList).removeAll(injectedArrows);
        injectedArrows.clear();
        lastRouteSeen = sig;
        arrowsIn = want;

        if (!want || mapScreen == null) return;
        if (Global.getSector().getPlayerFleet() == null) return;

        try {
            Object params = ReflectionUtils.invoke(mapScreen, "getParams");
            if (params == null) return;

            Object target = null;
            for (ReflectionUtils.ReflectedField field : ReflectionUtils.getFieldsMatching(
                    params.getClass(), null, null, List.class, null, false)) {
                Object value = field.get(params);
                if (value == null) continue;

                if (target != null) return;
                target = value;
            }
            if (target == null) return;

            @SuppressWarnings("unchecked")
            List<Object> arrows = (List<Object>) target;

            if (liveShown) addArrowChain(arrows, route.stops, 0.5f);

            for (catchrelease.campaign.fish.intel.FishRouteIntel intel : saved) {
                // the live plot of an already-saved route would double its chain
                if (liveShown && intel.matches(route)) continue;

                addArrowChain(arrows, intel.getStops(), 0.35f);
            }

            arrowList = arrows;
        } catch (Throwable t) {
            Global.getLogger(FishMapFilterScript.class)
                    .warn("Could not put the fishing route's arrows on the map", t);
        }
    }

    protected void addArrowChain(List<Object> arrows, List<FishRoute.Stop> stops, float alpha) {
        com.fs.starfarer.api.campaign.SectorEntityToken from =
                Global.getSector().getPlayerFleet();

        for (FishRoute.Stop stop : stops) {
            com.fs.starfarer.api.campaign.StarSystemAPI system = FishRoute.getSystem(stop);
            if (system == null || system.getHyperspaceAnchor() == null) continue;

            com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ArrowData arrow =
                    new com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ArrowData(
                            from, system.getHyperspaceAnchor());
            arrow.color = Global.getSector().getPlayerFaction().getBaseUIColor();
            arrow.alphaMult = alpha;

            arrows.add(arrow);
            injectedArrows.add(arrow);

            from = system.getHyperspaceAnchor();
        }
    }

    protected StarSystemAPI getViewedSystem() {
        if (mapScreen == null) return null;

        try {
            Object mapWidget = ReflectionUtils.invoke(mapScreen, "getMap");
            Object location = ReflectionUtils.invokeIfExists(mapWidget, "getLocation");

            return location instanceof StarSystemAPI ? (StarSystemAPI) location : null;
        } catch (Throwable t) {
            return null;
        }
    }

    protected boolean hasAnyFish(StarSystemAPI system) {
        return !FishPresence.getKnownFishIn(system).isEmpty()
                || FishPresence.getUnknownCountIn(system) > 0;
    }

    protected void activateSystemPane(StarSystemAPI system) {
        try {
            Object scroller = ReflectionUtils.invoke(mapScreen, "getScroller");
            Object mapWidget = ReflectionUtils.invoke(mapScreen, "getMap");
            PositionAPI scrollerPos = ((UIComponentAPI) scroller).getPosition();

            originalScrollerWidth = scrollerPos.getWidth();

            Vector2f keep = (Vector2f) ReflectionUtils.invoke(mapWidget, "getWorldLocation",
                    scrollerPos.getCenterX(), scrollerPos.getCenterY());

            float narrowWidth = originalScrollerWidth - FishSystemPane.WIDTH - PANE_GAP;
            scrollerPos.setSize(narrowWidth, scrollerPos.getHeight());
            ReflectionUtils.invoke(mapScreen, "centerOn", keep);

            PositionAPI screenPos = ((UIComponentAPI) mapScreen).getPosition();
            float x = scrollerPos.getX() + narrowWidth + PANE_GAP - screenPos.getX();
            float y = screenPos.getY() + screenPos.getHeight()
                    - (scrollerPos.getY() + scrollerPos.getHeight());
            float height = scrollerPos.getHeight();

            systemPane = new FishSystemPane();
            systemPanePanel = Global.getSettings().createCustom(
                    FishSystemPane.WIDTH, height, systemPane);
            systemPane.mount(systemPanePanel, FishSystemPane.WIDTH, height, system);

            ((UIPanelAPI) mapScreen).addComponent(systemPanePanel)
                    .setSize(FishSystemPane.WIDTH, height)
                    .inTL(x, y);

            if (overlayPanel != null) {
                overlayPanel.getPosition().setSize(narrowWidth, height);
            }

            shownSystem = system;
            systemApplied = true;
        } catch (Throwable t) {
            fail(t);
        }
    }

    protected void deactivateSystemPane() {
        try {
            Object scroller = ReflectionUtils.invoke(mapScreen, "getScroller");
            Object mapWidget = ReflectionUtils.invoke(mapScreen, "getMap");
            PositionAPI scrollerPos = ((UIComponentAPI) scroller).getPosition();

            Vector2f keep = (Vector2f) ReflectionUtils.invoke(mapWidget, "getWorldLocation",
                    scrollerPos.getCenterX(), scrollerPos.getCenterY());

            if (systemPanePanel != null) {
                ((UIPanelAPI) mapScreen).removeComponent(systemPanePanel);
            }

            if (originalScrollerWidth > 0f) {
                scrollerPos.setSize(originalScrollerWidth, scrollerPos.getHeight());
                ReflectionUtils.invoke(mapScreen, "centerOn", keep);
            }

            if (overlayPanel != null) {
                overlayPanel.getPosition().setSize(originalScrollerWidth,
                        scrollerPos.getHeight());
            }

            systemPane = null;
            systemPanePanel = null;
            shownSystem = null;
            systemApplied = false;
        } catch (Throwable t) {
            fail(t);
        }
    }

    protected boolean isHyperViewShown() {
        if (mapScreen == null) return false;

        try {
            Object mapWidget = ReflectionUtils.invoke(mapScreen, "getMap");
            Object location = ReflectionUtils.invokeIfExists(mapWidget, "getLocation");

            return location instanceof com.fs.starfarer.api.campaign.LocationAPI
                    && ((com.fs.starfarer.api.campaign.LocationAPI) location).isHyperspace();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onCoherenceToggled(boolean shown) {
        if (overlay != null) overlay.setCoherenceShown(shown);
    }

    @Override
    public void onPlannerRequested() {
        if (popupPanel != null || mapScreen == null || panePanel == null) return;

        try {
            ((UIPanelAPI) mapScreen).removeComponent(panePanel);
            paneStanding = false;

            popup = new FishRoutePopup(this);
            popupPanel = Global.getSettings().createCustom(FishMapPane.WIDTH, paneHeight, popup);
            popup.mount(popupPanel, FishMapPane.WIDTH, paneHeight);

            ((UIPanelAPI) mapScreen).addComponent(popupPanel)
                    .setSize(FishMapPane.WIDTH, paneHeight)
                    .inTL(paneX, paneY);
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    public void onRoutePlotted(FishRoute.Saved route) {
        closePlanner();

        // arrows/badges land this frame via syncRouteArrows; point the map at the first stop
        try {
            if (route != null && !route.stops.isEmpty()) {
                com.fs.starfarer.api.campaign.StarSystemAPI first =
                        FishRoute.getSystem(route.stops.get(0));

                if (first != null && mapScreen != null) {
                    ReflectionUtils.invoke(mapScreen, "centerOn", first.getLocation());
                }
            }
        } catch (Throwable t) {
        }
    }

    @Override
    public void onPlannerClosed() {
        closePlanner();
    }

    protected void openSaveDialog() {
        if (saveDialogPanel != null || mapScreen == null || FishRoute.get() == null) return;

        try {
            Object scroller = ReflectionUtils.invoke(mapScreen, "getScroller");
            PositionAPI scrollerPos = ((UIComponentAPI) scroller).getPosition();
            PositionAPI screenPos = ((UIComponentAPI) mapScreen).getPosition();

            float x = scrollerPos.getX() - screenPos.getX()
                    + (scrollerPos.getWidth() - FishRouteSaveDialog.WIDTH) * 0.5f;
            float y = screenPos.getY() + screenPos.getHeight()
                    - (scrollerPos.getY() + scrollerPos.getHeight())
                    + (scrollerPos.getHeight() - FishRouteSaveDialog.HEIGHT) * 0.5f;

            saveDialog = new FishRouteSaveDialog(this);
            saveDialogPanel = Global.getSettings().createCustom(
                    FishRouteSaveDialog.WIDTH, FishRouteSaveDialog.HEIGHT, saveDialog);
            saveDialog.mount(saveDialogPanel);

            ((UIPanelAPI) mapScreen).addComponent(saveDialogPanel)
                    .setSize(FishRouteSaveDialog.WIDTH, FishRouteSaveDialog.HEIGHT)
                    .inTL(x, y);
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    public void onRouteSaveConfirmed(String name, String purpose) {
        closeSaveDialog();

        FishRoute.Saved route = FishRoute.get();
        if (route == null) return;

        // straight into the manager - the entry has to exist the moment the player checks
        catchrelease.campaign.fish.intel.FishRouteIntel intel =
                new catchrelease.campaign.fish.intel.FishRouteIntel(name, purpose, route);
        Global.getSector().getIntelManager().addIntel(intel);

        if (overlay != null) overlay.noteRouteSaved();
    }

    @Override
    public void onRouteSaveClosed() {
        closeSaveDialog();
    }

    protected void closeSaveDialog() {
        if (saveDialogPanel != null && mapScreen != null) {
            try {
                ((UIPanelAPI) mapScreen).removeComponent(saveDialogPanel);
            } catch (Throwable t) {
                // the screen going away mid-close already took the panel with it
            }
        }

        saveDialog = null;
        saveDialogPanel = null;
    }

    protected void closePlanner() {
        if (popupPanel != null && mapScreen != null) {
            try {
                ((UIPanelAPI) mapScreen).removeComponent(popupPanel);

                if (applied && panePanel != null) {
                    ((UIPanelAPI) mapScreen).addComponent(panePanel)
                            .setSize(FishMapPane.WIDTH, paneHeight)
                            .inTL(paneX, paneY);

                    paneStanding = true;
                }
            } catch (Throwable t) {
                // usually the screen going away mid-close; if it is in fact still up, the advance gate re-seats the pane next frame
                Global.getLogger(FishMapFilterScript.class)
                        .warn("Planner could not hand the sidebar's slot back", t);
            }
        }

        popup = null;
        popupPanel = null;
    }

    protected void ensurePaneStanding() {
        if (panePanel == null || mapScreen == null) return;

        if (paneStanding) return;

        // remove first: harmless when it is genuinely detached, and the one thing that stops a wrong answer here from stacking a second sidebar on top of the first
        ((UIPanelAPI) mapScreen).removeComponent(panePanel);
        ((UIPanelAPI) mapScreen).addComponent(panePanel)
                .setSize(FishMapPane.WIDTH, paneHeight)
                .inTL(paneX, paneY);

        paneStanding = true;
    }

    public static void requestSpeciesFocus(String speciesId) {
        pendingSpeciesId = speciesId;
        pendingRequirements = null;
        pendingOverview = false;
        pendingSystemId = null;
        pendingFocusSetAt = System.currentTimeMillis();

        if (Global.getSector() != null) {
            Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, true);
        }
    }

    public static void requestRequirementsFocus(List<FishRequirement> asks) {
        pendingMapOpen = false;
        pendingSpeciesId = null;
        pendingRequirements = asks == null ? new ArrayList<>() : new ArrayList<>(asks);
        pendingOverview = false;
        pendingSystemId = null;
        pendingFocusSetAt = System.currentTimeMillis();

        if (Global.getSector() != null) {
            Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, true);
        }
    }

    public static void requestOverviewFocus(String systemId) {
        pendingMapOpen = false;
        pendingSpeciesId = null;
        pendingRequirements = null;
        pendingOverview = true;
        pendingSystemId = systemId;
        pendingFocusSetAt = System.currentTimeMillis();

        if (Global.getSector() != null) {
            Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, true);
        }
    }

    public static void requestSpeciesFocusFromCodex(String speciesId) {
        requestSpeciesFocus(speciesId);
        pendingMapOpen = true;
    }

    protected void openPendingMapWhenCodexCloses() {
        if (!pendingMapOpen) return;

        if (!hasFreshPendingFocus()) {
            pendingMapOpen = false;
            return;
        }

        if (Global.getSector() == null) return;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null) return;

        Object showing = ReflectionUtils.invokeIfExists(ui, "isShowingCodex");
        if (Boolean.TRUE.equals(showing)) return;

        pendingMapOpen = false;

        if (ui.getCurrentCoreTab() != CoreUITabId.MAP) {
            ui.showCoreUITab(CoreUITabId.MAP);
        }
    }

    protected static boolean hasPendingFocus() {
        return pendingSpeciesId != null || pendingRequirements != null || pendingOverview;
    }

    protected static boolean hasFreshPendingFocus() {
        return hasPendingFocus()
                && System.currentTimeMillis() - pendingFocusSetAt <= PENDING_SPECIES_MILLIS;
    }

    protected void applyPendingFocus() {
        boolean fresh = hasFreshPendingFocus();
        String id = pendingSpeciesId;
        List<FishRequirement> asks = pendingRequirements;
        boolean overview = pendingOverview;
        String systemId = pendingSystemId;
        pendingSpeciesId = null;
        pendingRequirements = null;
        pendingOverview = false;
        pendingSystemId = null;

        if (!fresh) return;
        if (pane == null) return;

        try {
            ReflectionUtils.invoke(mapScreen, "notifyMapLocationChanged",
                    Global.getSector().getHyperspace());
        } catch (Throwable t) {
        }

        FishSpec spec = FishPresence.getSpec(id);
        if (id != null && spec != null) {
            pane.showSpecies(id);
        } else if (asks != null) {
            pane.showRequirements(asks);
        } else if (overview) {
            pane.showOverview();
        }

        rebuildBlobs();
        if (spec != null) {
            onSpeciesFocused(spec);
        } else if (asks != null) {
            for (String selected : pane.getSelectedIds()) {
                FishSpec first = FishPresence.getSpec(selected);
                if (first != null) onSpeciesFocused(first);
                break;
            }
        }

        if (systemId != null) centerOnSystem(systemId);
    }

    protected void centerOnSystem(String systemId) {
        if (mapScreen == null || systemId == null || Global.getSector() == null) return;

        try {
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (!systemId.equals(system.getId()) || system.getLocation() == null) continue;
                ReflectionUtils.invoke(mapScreen, "centerOn", system.getLocation());
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    protected Object findMapScreen() {
        if (Global.getSector() == null) return null;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null || ui.getCurrentCoreTab() != CoreUITabId.MAP) return null;

        Object core = CoreUiCrawler.getCoreUi();
        if (core == null) return null;

        Object tab = ReflectionUtils.invokeIfExists(core, "getCurrentTab");
        if (tab == null) return null;

        return ReflectionUtils.hasMethodOfName(tab, "updateMapAndScrollerSize") ? tab : null;
    }

    protected void insertButton() {
        Object filterRow = ReflectionUtils.invoke(mapScreen, "getFilter");
        if (filterRow == null) throw new IllegalStateException("no filter row on the map screen");

        ButtonAPI template = (ButtonAPI) ReflectionUtils.get(filterRow, "constellations");
        Object renderer = ReflectionUtils.invoke(template, "getRenderer");

        ReflectionUtils.ReflectedConstructor checkboxCtor = null;
        Class<?> checkboxClass = null;

        List<Object> candidates = new ArrayList<>();
        candidates.add(renderer);

        for (ReflectionUtils.ReflectedField field : ReflectionUtils.getFieldsMatching(
                renderer.getClass(), null, null, null, null, false)) {
            Object value = field.get(renderer);
            if (value != null) candidates.add(value);
        }

        for (Object candidate : candidates) {
            List<ReflectionUtils.ReflectedConstructor> ctors = ReflectionUtils.getConstructorsMatching(
                    candidate.getClass(), 5,
                    new Class<?>[]{String.class, String.class, Color.class, Color.class, Color.class});

            if (!ctors.isEmpty()) {
                checkboxCtor = ctors.get(0);
                checkboxClass = candidate.getClass();
                break;
            }
        }

        if (checkboxCtor == null) throw new IllegalStateException("no checkbox renderer to clone");

        // the number key continues the row's own sequence, so a button another mod put
        // there first shifts ours to the next digit instead of double-binding its key
        int slot = countRowButtons(filterRow) + 1;
        String digit = slot <= MAX_SHORTCUT_SLOT ? String.valueOf(slot % 10) : null;

        // bracketed key written into the label by hand - vanilla's auto-append only covers its own rebindable keys
        FactionAPI player = Global.getSector().getPlayerFaction();
        Object checkbox = checkboxCtor.newInstance(digit == null ? "Fish" : "Fish [" + digit + "]",
                Global.getSettings().getString("defaultFont"),
                player.getColor(), player.getDarkUIColor(), player.getBrightUIColor());

        // the key digit in the highlight colour, the way vanilla's own row wears its numbers
        Object title = ReflectionUtils.invokeIfExists(checkbox, "getTitle");
        if (digit != null && title instanceof LabelAPI) {
            ((LabelAPI) title).setHighlightColor(Misc.getHighlightColor());
            ((LabelAPI) title).setHighlight(digit);
        }

        fishButton = null;

        if (!renderer.getClass().equals(checkboxClass)) {
            List<ReflectionUtils.ReflectedConstructor> wrapCtors = ReflectionUtils.getConstructorsMatching(
                    renderer.getClass(), 1, new Class<?>[]{checkboxClass});

            if (!wrapCtors.isEmpty()) {
                Object wrapper = wrapCtors.get(0).newInstance(checkbox);

                List<ReflectionUtils.ReflectedConstructor> wrapped = ReflectionUtils.getConstructorsMatching(
                        template.getClass(), 2, new Class<?>[]{wrapper.getClass(), null});

                if (!wrapped.isEmpty()) {
                    fishButton = (ButtonAPI) wrapped.get(0).newInstance(wrapper, null);
                }
            }
        }

        if (fishButton == null) {
            List<ReflectionUtils.ReflectedConstructor> direct = ReflectionUtils.getConstructorsMatching(
                    template.getClass(), 2, new Class<?>[]{checkboxClass, null});

            if (!direct.isEmpty()) {
                fishButton = (ButtonAPI) direct.get(0).newInstance(checkbox, null);
            }
        }

        if (fishButton == null) throw new IllegalStateException("no button constructor fits the renderer");

        fishButton.setChecked(false);

        // the same finishing touches vanilla's factory applies, both plain API
        fishButton.setHighlightBrightness(0.8f);
        fishButton.setQuickMode(true);

        // LWJGL's digit keycodes run contiguously KEY_1..KEY_9 then KEY_0
        if (digit != null) fishButton.setShortcut(Keyboard.KEY_1 + (slot - 1), true);

        ((UIPanelAPI) filterRow).addComponent((UIComponentAPI) fishButton)
                .setSize(BUTTON_WIDTH, BUTTON_HEIGHT)
                .rightOfMid(findRowAnchor(filterRow, (UIComponentAPI) template), BUTTON_PAD);
    }

    // another mod may have put its own button after vanilla's last one, so the anchor
    // is the rightmost thing actually in the row, not the constellations checkbox
    protected UIComponentAPI findRowAnchor(Object filterRow, UIComponentAPI fallback) {
        UIComponentAPI anchor = fallback;
        float best = anchor.getPosition().getX() + anchor.getPosition().getWidth();

        Object children = ReflectionUtils.invokeIfExists(filterRow, "getChildrenCopy");
        if (!(children instanceof List)) return anchor;

        PositionAPI row = ((UIComponentAPI) filterRow).getPosition();
        for (Object child : (List<?>) children) {
            if (!isInRowBand(child, row)) continue;

            PositionAPI pos = ((UIComponentAPI) child).getPosition();
            float right = pos.getX() + pos.getWidth();
            if (right > best) {
                best = right;
                anchor = (UIComponentAPI) child;
            }
        }

        return anchor;
    }

    /** Buttons the row actually shows; falls back to vanilla's six when the children
     *  cannot be enumerated, which restores the fixed [7] behaviour. */
    protected int countRowButtons(Object filterRow) {
        Object children = ReflectionUtils.invokeIfExists(filterRow, "getChildrenCopy");
        if (!(children instanceof List)) return VANILLA_ROW_BUTTONS;

        PositionAPI row = ((UIComponentAPI) filterRow).getPosition();
        int count = 0;
        for (Object child : (List<?>) children) {
            if (isInRowBand(child, row)) count++;
        }

        return count == 0 ? VANILLA_ROW_BUTTONS : count;
    }

    // vanilla parks two never-laid-out checkboxes at the origin as children of the same
    // row; the band test keeps them, and anything similar from other mods, out of both
    // the count and the anchor pick
    protected boolean isInRowBand(Object child, PositionAPI row) {
        if (!(child instanceof UIComponentAPI) || child == fishButton) return false;

        PositionAPI pos = ((UIComponentAPI) child).getPosition();
        if (pos == null || pos.getWidth() <= 0f) return false;

        float centerY = pos.getCenterY();

        return centerY >= row.getY() && centerY <= row.getY() + row.getHeight()
                && pos.getX() + pos.getWidth() > row.getX();
    }

    protected void mountOverlay() {
        Object scroller = ReflectionUtils.invoke(mapScreen, "getScroller");
        Object mapWidget = ReflectionUtils.invoke(mapScreen, "getMap");
        PositionAPI scrollerPos = ((UIComponentAPI) scroller).getPosition();

        overlay = new FishPresenceOverlay();
        overlay.setMapWidget(mapWidget);
        overlay.setSaveRouteListener(this::openSaveDialog);

        // the heat map belongs to the filter, not the bare map - activate() applies the sticky choice when the pane comes up, and a map without the filter shows no heat

        overlayPanel = Global.getSettings().createCustom(
                scrollerPos.getWidth(), scrollerPos.getHeight(), overlay);
        overlay.mountTooltips(overlayPanel);
        ReflectionUtils.invoke(scroller, "addToOverlay", overlayPanel);
    }

    protected void activate() {
        try {
            Object scroller = ReflectionUtils.invoke(mapScreen, "getScroller");
            Object mapWidget = ReflectionUtils.invoke(mapScreen, "getMap");
            PositionAPI scrollerPos = ((UIComponentAPI) scroller).getPosition();

            originalScrollerWidth = scrollerPos.getWidth();

            Vector2f keep = (Vector2f) ReflectionUtils.invoke(mapWidget, "getWorldLocation",
                    scrollerPos.getCenterX(), scrollerPos.getCenterY());

            float narrowWidth = originalScrollerWidth - FishMapPane.WIDTH - PANE_GAP;
            scrollerPos.setSize(narrowWidth, scrollerPos.getHeight());
            ReflectionUtils.invoke(mapScreen, "centerOn", keep);

            PositionAPI screenPos = ((UIComponentAPI) mapScreen).getPosition();
            paneX = scrollerPos.getX() + narrowWidth + PANE_GAP - screenPos.getX();
            paneY = screenPos.getY() + screenPos.getHeight()
                    - (scrollerPos.getY() + scrollerPos.getHeight());
            paneHeight = scrollerPos.getHeight();

            pane = new FishMapPane(this);
            panePanel = Global.getSettings().createCustom(FishMapPane.WIDTH, paneHeight, pane);
            pane.mount(panePanel, FishMapPane.WIDTH, paneHeight);

            ((UIPanelAPI) mapScreen).addComponent(panePanel)
                    .setSize(FishMapPane.WIDTH, paneHeight)
                    .inTL(paneX, paneY);

            paneStanding = true;

            if (overlayPanel != null) {
                overlayPanel.getPosition().setSize(narrowWidth, paneHeight);
            }

            if (overlay != null) overlay.setCoherenceShown(FishMapPane.isCoherenceShown());

            rebuildBlobs();
            applied = true;
        } catch (Throwable t) {
            fail(t);
        }
    }

    protected void deactivate() {
        try {
            // a planner holding the pane's slot goes down with it, without handing the slot back
            if (popupPanel != null) {
                ((UIPanelAPI) mapScreen).removeComponent(popupPanel);
                popup = null;
                popupPanel = null;
            }

            Object scroller = ReflectionUtils.invoke(mapScreen, "getScroller");
            Object mapWidget = ReflectionUtils.invoke(mapScreen, "getMap");
            PositionAPI scrollerPos = ((UIComponentAPI) scroller).getPosition();

            Vector2f keep = (Vector2f) ReflectionUtils.invoke(mapWidget, "getWorldLocation",
                    scrollerPos.getCenterX(), scrollerPos.getCenterY());

            if (panePanel != null) ((UIPanelAPI) mapScreen).removeComponent(panePanel);
            paneStanding = false;

            if (originalScrollerWidth > 0f) {
                scrollerPos.setSize(originalScrollerWidth, scrollerPos.getHeight());
                ReflectionUtils.invoke(mapScreen, "centerOn", keep);
            }

            if (overlay != null) {
                overlay.setBlobs(null);
                overlay.setNoDataShown(false);
            }

            // the heat map is the filter's reading of the water, not the map's - it goes down with the pane; the sticky choice itself stays for the next activation
            if (overlay != null) overlay.setCoherenceShown(false);

            if (overlayPanel != null) {
                overlayPanel.getPosition().setSize(originalScrollerWidth, scrollerPos.getHeight());
            }

            pane = null;
            panePanel = null;
            paneStanding = false;

            applied = false;
        } catch (Throwable t) {
            fail(t);
        }
    }

    protected void rebuildBlobs() {
        if (pane == null || overlay == null) return;

        List<FishPresenceOverlay.Blob> blobs = new ArrayList<>();

        if (pane.isCategoryView()) {
            int index = 0;

            for (FishType type : FishType.values()) {
                if (!pane.getFilter().types.contains(type)) continue;

                boolean constrained = pane.getFilter().speciesRestricted;
                FishPresenceField.Mesh mesh = constrained
                        ? null : meshCache.get("type:" + type.name());

                if (mesh == null) {
                    List<Vector2f> hosts = FishPresence.getTypeHostLocations(type,
                            pane.getFilter());
                    if (hosts.isEmpty()) continue;

                    mesh = FishPresenceField.build(hosts, BLOB_RADIUS);
                    if (!constrained) meshCache.put("type:" + type.name(), mesh);
                }

                if (mesh.isEmpty()) continue;

                blobs.add(new FishPresenceOverlay.Blob(mesh, type.color,
                        getStyle(index++), true, true));
            }
        } else {
            // colour groups first, because a border is per colour rather than per pick
            Map<Integer, List<FishSpec>> byColor = new java.util.LinkedHashMap<>();
            List<FishSpec> picked = new ArrayList<>();

            for (String id : pane.getSelectedIds()) {
                FishSpec spec = FishPresence.getSpec(id);
                if (spec == null || !FishPresence.isKnown(spec) || !FishPresence.showsRegions(spec)) {
                    continue;
                }

                picked.add(spec);
                byColor.computeIfAbsent(spec.rarity.color.getRGB(), k -> new ArrayList<>()).add(spec);
            }

            // each pick's fill, in its own weave, with its own border only when its colour is its own
            for (int i = 0; i < picked.size(); i++) {
                FishSpec spec = picked.get(i);
                FishPresenceField.Mesh mesh = getSpeciesMesh(spec);
                if (mesh == null) continue;

                boolean colorShared = byColor.get(spec.rarity.color.getRGB()).size() > 1;

                blobs.add(new FishPresenceOverlay.Blob(mesh, spec.rarity.color,
                        getStyle(i), true, !colorShared));
            }

            for (List<FishSpec> group : byColor.values()) {
                if (group.size() < 2) continue;

                FishPresenceField.Mesh union = getUnionMesh(group);
                if (union == null) continue;

                blobs.add(new FishPresenceOverlay.Blob(union, group.get(0).rarity.color,
                        FishPresenceOverlay.STYLE_SOLID, false, true));
            }
        }

        overlay.setBlobs(blobs);
        overlay.setNoDataShown(pane.hasSelectionWithoutRangeData());
    }

    protected int getStyle(int index) {
        switch (index % 3) {
            case 1: return FishPresenceOverlay.STYLE_STRIPE_RIGHT;
            case 2: return FishPresenceOverlay.STYLE_STRIPE_LEFT;
            default: return FishPresenceOverlay.STYLE_SOLID;
        }
    }

    protected FishPresenceField.Mesh getSpeciesMesh(FishSpec spec) {
        FishPresenceField.Mesh mesh = meshCache.get("spec:" + spec.id);

        if (mesh == null) {
            List<Vector2f> hosts = FishPresence.getHostLocations(spec);
            if (hosts.isEmpty()) return null;

            mesh = FishPresenceField.build(hosts, BLOB_RADIUS);
            meshCache.put("spec:" + spec.id, mesh);
        }

        return mesh.isEmpty() ? null : mesh;
    }

    protected FishPresenceField.Mesh getUnionMesh(List<FishSpec> group) {
        List<String> ids = new ArrayList<>();
        for (FishSpec spec : group) ids.add(spec.id);
        java.util.Collections.sort(ids);

        String key = "union:" + String.join(",", ids);
        FishPresenceField.Mesh mesh = meshCache.get(key);

        if (mesh == null) {
            java.util.Set<Vector2f> hosts = new java.util.LinkedHashSet<>();
            for (FishSpec spec : group) hosts.addAll(FishPresence.getHostLocations(spec));

            if (hosts.isEmpty()) return null;

            mesh = FishPresenceField.build(new ArrayList<>(hosts), BLOB_RADIUS);
            meshCache.put(key, mesh);
        }

        return mesh.isEmpty() ? null : mesh;
    }

    @Override
    public void onPresenceChanged() {
        if (applied) rebuildBlobs();
    }

    @Override
    public void onSpeciesFocused(FishSpec spec) {
        try {
            Vector2f focus = FishPresence.getFocusPoint(spec);
            if (focus != null && mapScreen != null) {
                ReflectionUtils.invoke(mapScreen, "centerOn", focus);
            }
        } catch (Throwable t) {
            // pointing the map is a nicety; a species with nowhere to point is not an error
        }
    }

    protected boolean isRemembered() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(MEMORY_KEY);
    }

    protected void remember(boolean on) {
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, on);
    }

    protected void drop() {
        clearComponents();
        mapScreen = null;
        failed = false;

        meshCache.clear();
    }

    protected void clearComponents() {
        fishButton = null;
        pane = null;
        panePanel = null;
        paneStanding = false;
        overlayPanel = null;
        overlay = null;
        popup = null;
        popupPanel = null;
        saveDialog = null;
        saveDialogPanel = null;
        systemPane = null;
        systemPanePanel = null;
        systemApplied = false;
        shownSystem = null;
        applied = false;
        originalScrollerWidth = 0f;

        // arrow list belonged to the old screen - drop it and force re-injection on the next one
        arrowList = null;
        injectedArrows.clear();
        lastRouteSeen = new Object();
    }

    protected void fail(Throwable t) {
        Global.getLogger(FishMapFilterScript.class)
                .warn("Fish map filter bowing out of this map screen", t);

        try {
            if (mapScreen != null) {
                if (panePanel != null) ((UIPanelAPI) mapScreen).removeComponent(panePanel);
                paneStanding = false;

                if (saveDialogPanel != null) {
                    ((UIPanelAPI) mapScreen).removeComponent(saveDialogPanel);
                }

                if (systemPanePanel != null) {
                    ((UIPanelAPI) mapScreen).removeComponent(systemPanePanel);
                }

                Object scroller = ReflectionUtils.invokeIfExists(mapScreen, "getScroller");
                if (scroller != null && overlayPanel != null) {
                    ReflectionUtils.invokeIfExists(scroller, "removeFromOverlay", overlayPanel);
                }

                if (scroller != null && (applied || systemApplied) && originalScrollerWidth > 0f) {
                    ((UIComponentAPI) scroller).getPosition()
                            .setSize(originalScrollerWidth,
                                    ((UIComponentAPI) scroller).getPosition().getHeight());
                }
            }
        } catch (Throwable ignored) {
            // the withdrawal itself failing means the screen is already gone
        }

        clearComponents();
        failed = true;
    }
}
