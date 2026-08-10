package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishSpec;
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

/**
 * Puts a Fish toggle on the sector map's filter row (beside Starscape, Fuel range) and runs
 * everything that follows from pressing it: the map narrows, a filter pane takes the freed edge,
 * and the shown species' waters are drawn as merged shapes over the map.
 * <p>
 * The button is built via reflection to match vanilla's own checkbox renderer/class exactly, and
 * clicks are read by polling {@code isChecked} rather than through a listener. Narrowing resizes
 * the scroller - the map's real viewport, which content size and zoom clamps re-derive from -
 * and re-centres on the world point that was in the middle; the waters render in the scroller's
 * overlay layer.
 * <p>
 * The map screen rebuilds on every open, so the button is re-inserted each time; whether the
 * filter was on persists in sector memory. Every step fails soft - a broken assumption just
 * means no button, and the sector map is exactly as vanilla made it.
 */
public class FishMapFilterScript implements EveryFrameScript, FishMapPane.Host,
        FishRoutePopup.Host {

    public static final String MEMORY_KEY = "$catchrelease_map_fish_filter";

    /** The vanilla row's own button geometry: 120x25, 3px apart. */
    public static final float BUTTON_WIDTH = 120f;
    public static final float BUTTON_HEIGHT = 25f;
    public static final float BUTTON_PAD = 3f;

    public static final float PANE_GAP = 8f;

    /** How far a lone system's water reaches, in hyperspace units. */
    public static final float BLOB_RADIUS = 3200f;

    /** How long a parked species request stays valid - generous next to a tab switch, short
     *  next to a session, so a stale request can't reshape a map opened much later. */
    public static final long PENDING_SPECIES_MILLIS = 10_000L;

    /** Species someone outside asked the map to focus (the codex's "show on the sector map").
     *  Static, since the asker's dialog is gone before the map exists. */
    protected static String pendingSpeciesId;
    protected static long pendingSpeciesSetAt;

    /** Whether the codex asked to open the map for the parked species. The codex overlay fades
     *  out asynchronously, so the tab switch must wait until its dismissal callback has run. */
    protected static boolean pendingMapOpen;

    /** The map screen instance the button currently lives on. A new open means a new one. */
    protected Object mapScreen;

    /** Latched when this screen-open went wrong; cleared when the screen is rebuilt. */
    protected boolean failed = false;

    protected ButtonAPI fishButton;
    protected boolean applied = false;

    /**
     * Whether the sidebar is actually attached to the map screen right now, as opposed to
     * {@link #applied}, which only records that {@link #activate()} ran. The two come apart when
     * the planner hands the slot back and the re-add fails, and nothing else reconciles them -
     * see {@link #ensurePaneStanding()}. Maintained by every add and remove of {@link #panePanel}
     * rather than asked of the component, because the API has no attachment query and the
     * internals' answer cannot be identity-checked against the screen without running the game.
     */
    protected boolean paneStanding = false;
    protected float originalScrollerWidth = 0f;

    protected FishMapPane pane;
    protected CustomPanelAPI panePanel;
    protected CustomPanelAPI overlayPanel;
    protected FishPresenceOverlay overlay;

    /** The system view's own sidebar - same mechanism as the big pane, smaller tenant. */
    protected FishSystemPane systemPane;
    protected CustomPanelAPI systemPanePanel;
    protected boolean systemApplied = false;
    protected Object shownSystem;

    protected FishRoutePopup popup;
    protected CustomPanelAPI popupPanel;

    /** The pane's slot on the screen, kept so the planner can borrow it and hand it back. */
    protected float paneX, paneY, paneHeight;

    /** The map's own arrow list and what this script put in it, for taking it back out. */
    protected Object arrowList;
    protected final List<Object> injectedArrows = new ArrayList<>();
    protected Object lastRouteSeen;
    protected boolean arrowsIn = false;

    /** World-space meshes by species - the hosts never move during a session, so cut once. */
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

                //the overlay rides every map open, filter or no filter - the route badges and
                //the system view's fish row are map furniture, not filter furniture. The waters
                //only ever appear in it once the filter fills the blob list
                mountOverlay();

                //the map reopens the way it was left
                if (fishButton != null && isRemembered()) fishButton.setChecked(true);
            } catch (Throwable t) {
                fail(t);
            }
        }

        if (failed || fishButton == null) return;

        try {
            //a parked request self-activates the filter - the memory flag only fires on a fresh
            //attach, and the codex may have opened over a map already up with the filter off.
            //It also flips to the hyper view up front: the pane only stands there now, and a
            //request that waited for the pane while the pane waited for the view would wait forever
            if (hasFreshPendingSpecies()) {
                if (!fishButton.isChecked()) fishButton.setChecked(true);

                if (!isHyperViewShown()) {
                    ReflectionUtils.invoke(mapScreen, "notifyMapLocationChanged",
                            Global.getSector().getHyperspace());
                }
            }

            boolean checked = fishButton.isChecked();
            if (checked != isRemembered()) remember(checked);

            //the pane and the narrowed map are hyperspace furniture: the filter stays checked
            //across a flip to the system view, where the system's own smaller pane stands in.
            //Deactivations run before activations, so a view flip hands the map's edge over
            //instead of narrowing an already-narrowed map
            boolean wantPane = checked && isHyperViewShown();

            StarSystemAPI viewed = getViewedSystem();
            boolean wantSystem = checked && viewed != null && hasAnyFish(viewed);

            if (applied && !wantPane) deactivate();
            if (systemApplied && (!wantSystem || viewed != shownSystem)) deactivateSystemPane();

            if (!applied && wantPane) activate();
            if (!systemApplied && wantSystem) activateSystemPane(viewed);

            //the flag says the pane is up and the planner is down - make sure the glass
            //agrees. applied only means activate() ran; a hand-back that broke half-way
            //leaves the pane gone with neither branch above ever firing again
            if (applied && wantPane && popupPanel == null) ensurePaneStanding();

            if (applied && pendingSpeciesId != null) applyPendingSpecies();

            syncRouteArrows();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * Keeps the map's own arrow list carrying the plotted route, so legs wear vanilla's own
     * arrow style. The list is found on the map's params object as the one non-null {@code List}
     * field (arrows vs markers, told apart by which is populated) - if that stops holding, the
     * arrows are silently skipped. Re-run when the route's identity changes or the map flips
     * between views - the legs are hyperspace geometry, and vanilla draws its params arrows on
     * the system view too, where they point at nothing. Anything this script previously added is
     * pulled back out first.
     */
    protected void syncRouteArrows() {
        FishRoute.Saved route = FishRoute.get();
        boolean hyper = isHyperViewShown();
        boolean want = route != null && !route.stops.isEmpty() && hyper;

        if (route == lastRouteSeen && want == arrowsIn) return;

        if (arrowList instanceof List) ((List<?>) arrowList).removeAll(injectedArrows);
        injectedArrows.clear();
        lastRouteSeen = route;
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

                if (target != null) return; //two live lists - the shape no longer answers
                target = value;
            }
            if (target == null) return;

            @SuppressWarnings("unchecked")
            List<Object> arrows = (List<Object>) target;

            com.fs.starfarer.api.campaign.SectorEntityToken from =
                    Global.getSector().getPlayerFleet();

            for (FishRoute.Stop stop : route.stops) {
                com.fs.starfarer.api.campaign.StarSystemAPI system = FishRoute.getSystem(stop);
                if (system == null || system.getHyperspaceAnchor() == null) continue;

                com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ArrowData arrow =
                        new com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ArrowData(
                                from, system.getHyperspaceAnchor());
                arrow.color = Global.getSector().getPlayerFaction().getBaseUIColor();
                arrow.alphaMult = 0.5f;

                arrows.add(arrow);
                injectedArrows.add(arrow);

                from = system.getHyperspaceAnchor();
            }

            arrowList = arrows;
        } catch (Throwable t) {
            Global.getLogger(FishMapFilterScript.class)
                    .warn("Could not put the fishing route's arrows on the map", t);
        }
    }

    /** The single system the map is showing, or null on the hyperspace view. */
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

    /** Whether the system has anything to put on a pane - an empty pane is just a narrower map. */
    protected boolean hasAnyFish(StarSystemAPI system) {
        return !FishPresence.getKnownFishIn(system).isEmpty()
                || FishPresence.getUnknownCountIn(system) > 0;
    }

    /**
     * The system view's pane: the same hand-over as {@link #activate()} - the map gives up its
     * right edge, the pane takes it - sized for a column of holders rather than the full
     * filter. Rebuilt whenever the viewed system changes, since the stock is the system's.
     */
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

    /** The system pane steps off and the map takes its edge back. */
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

    /** Whether the map is showing hyperspace right now, rather than a single system. */
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

    /** The heat map rides the overlay; the pane holds the choice, the overlay does the painting. */
    @Override
    public void onCoherenceToggled(boolean shown) {
        if (overlay != null) overlay.setCoherenceShown(shown);
    }

    /**
     * The planner takes the sidebar's own slot: the pane steps aside, the planner stands exactly
     * where it stood, and closing hands the slot back. A separate floating card was tried and
     * looked like a guest; the sidebar is already the pane the player is working in.
     */
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

        //arrows/badges land this frame via syncRouteArrows; point the map at the first stop
        try {
            if (route != null && !route.stops.isEmpty()) {
                com.fs.starfarer.api.campaign.StarSystemAPI first =
                        FishRoute.getSystem(route.stops.get(0));

                if (first != null && mapScreen != null) {
                    ReflectionUtils.invoke(mapScreen, "centerOn", first.getLocation());
                }
            }
        } catch (Throwable t) {
            //pointing the map is a nicety
        }
    }

    @Override
    public void onPlannerClosed() {
        closePlanner();
    }

    /** Takes the planner down and puts the sidebar back in the slot it lent out. */
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
                //usually the screen going away mid-close; if it is in fact still up, the
                //advance gate re-seats the pane next frame
                Global.getLogger(FishMapFilterScript.class)
                        .warn("Planner could not hand the sidebar's slot back", t);
            }
        }

        popup = null;
        popupPanel = null;
    }

    /**
     * Re-seats the sidebar if it is wanted but not actually standing on the screen.
     * {@code applied} only records that {@link #activate()} ran - a broken hand-back from the
     * planner leaves the flag and the glass disagreeing, and no other path reconciles them.
     * Reads {@link #paneStanding} rather than asking the component, since the API has no
     * attachment query and the internals' {@code getParent} cannot be identity-checked against
     * the screen from source alone - and a wrong answer there would re-seat the sidebar every
     * frame, which is worse than the fault being healed.
     */
    protected void ensurePaneStanding() {
        if (panePanel == null || mapScreen == null) return;

        if (paneStanding) return;

        //remove first: harmless when it is genuinely detached, and the one thing that stops a
        //wrong answer here from stacking a second sidebar on top of the first
        ((UIPanelAPI) mapScreen).removeComponent(panePanel);
        ((UIPanelAPI) mapScreen).addComponent(panePanel)
                .setSize(FishMapPane.WIDTH, paneHeight)
                .inTL(paneX, paneY);

        paneStanding = true;
    }

    /**
     * Parks a species and flags the filter to come on next frame (same frame, if the map's
     * already open). Applied in {@link #applyPendingSpecies()} once a pane exists.
     */
    public static void requestSpeciesFocus(String speciesId) {
        pendingSpeciesId = speciesId;
        pendingSpeciesSetAt = System.currentTimeMillis();

        if (Global.getSector() != null) {
            Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, true);
        }
    }

    /**
     * Parks a species and asks the campaign UI to open its map after the codex has actually
     * finished dismissing. Calling {@code showCoreUITab()} directly from the codex button races
     * the overlay's fade-out and can leave the core UI on its previous tab.
     */
    public static void requestSpeciesFocusFromCodex(String speciesId) {
        requestSpeciesFocus(speciesId);
        pendingMapOpen = true;
    }

    /** Opens the requested map exactly once, after CodexDialog's dismissal delegate clears the
     *  campaign state's codex flag. This runs while paused, as codex transitions do. */
    protected void openPendingMapWhenCodexCloses() {
        if (!pendingMapOpen) return;

        if (!hasFreshPendingSpecies()) {
            pendingMapOpen = false;
            return;
        }

        if (Global.getSector() == null) return;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null) return;

        Object showing = ReflectionUtils.invokeIfExists(ui, "isShowingCodex");
        if (Boolean.TRUE.equals(showing)) return;

        pendingMapOpen = false;
        ui.showCoreUITab(CoreUITabId.MAP);
    }

    protected static boolean hasFreshPendingSpecies() {
        return pendingSpeciesId != null
                && System.currentTimeMillis() - pendingSpeciesSetAt <= PENDING_SPECIES_MILLIS;
    }

    /**
     * Honours the parked request now the pane exists: flips to SPECIES with the species picked,
     * re-cuts the waters, and points the map like a row click would. Consumed up front, so a
     * failed request doesn't retry on the next open.
     */
    protected void applyPendingSpecies() {
        boolean fresh = hasFreshPendingSpecies();
        String id = pendingSpeciesId;
        pendingSpeciesId = null;

        if (!fresh) return;

        FishSpec spec = FishPresence.getSpec(id);
        if (spec == null || pane == null) return;

        //waters are in hyperspace coordinates; flip to the hyper view first (same method the
        //game calls on a location change), so the focus point means something once there
        try {
            ReflectionUtils.invoke(mapScreen, "notifyMapLocationChanged",
                    Global.getSector().getHyperspace());
        } catch (Throwable t) {
            //the flip is a nicety - on the system view the pane still opens on the species
        }

        pane.showSpecies(id);
        rebuildBlobs();
        onSpeciesFocused(spec);
    }

    /** The sector map screen if it is on the glass right now, else null. */
    protected Object findMapScreen() {
        if (Global.getSector() == null) return null;

        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null || ui.getCurrentCoreTab() != CoreUITabId.MAP) return null;

        Object core = CoreUiCrawler.getCoreUi();
        if (core == null) return null;

        Object tab = ReflectionUtils.invokeIfExists(core, "getCurrentTab");
        if (tab == null) return null;

        //identified by capability - the one panel that resizes a map and scroller together
        return ReflectionUtils.hasMethodOfName(tab, "updateMapAndScrollerSize") ? tab : null;
    }

    /**
     * Builds the Fish button from the row's own parts and hangs it after the last vanilla button.
     * The checkbox renderer is found by shape - a constructor taking (label, font, three colours)
     * - so no obfuscated name is ever written down.
     */
    protected void insertButton() {
        Object filterRow = ReflectionUtils.invoke(mapScreen, "getFilter");
        if (filterRow == null) throw new IllegalStateException("no filter row on the map screen");

        ButtonAPI template = (ButtonAPI) ReflectionUtils.get(filterRow, "constellations");
        Object renderer = ReflectionUtils.invoke(template, "getRenderer");

        //checkbox class is either the renderer itself or the one thing it wraps - both tried by shape
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

        //bracketed key written into the label by hand - vanilla's auto-append only covers its
        //own rebindable keys
        FactionAPI player = Global.getSector().getPlayerFaction();
        Object checkbox = checkboxCtor.newInstance("Fish [7]",
                Global.getSettings().getString("defaultFont"),
                player.getColor(), player.getDarkUIColor(), player.getBrightUIColor());

        //the key digit in the highlight colour, the way vanilla's own row wears its numbers
        Object title = ReflectionUtils.invokeIfExists(checkbox, "getTitle");
        if (title instanceof LabelAPI) {
            ((LabelAPI) title).setHighlightColor(Misc.getHighlightColor());
            ((LabelAPI) title).setHighlight("7");
        }

        //vanilla's factory wraps the checkbox in an adapter (new n(new m(checkbox), listener))
        //before the button ctor accepts it - try the adapter route first, direct ctor as fallback
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

        //the same finishing touches vanilla's factory applies, both plain API
        fishButton.setHighlightBrightness(0.8f);
        fishButton.setQuickMode(true);

        fishButton.setShortcut(Keyboard.KEY_7, true);

        ((UIPanelAPI) filterRow).addComponent((UIComponentAPI) fishButton)
                .setSize(BUTTON_WIDTH, BUTTON_HEIGHT)
                .rightOfMid((UIComponentAPI) template, BUTTON_PAD);
    }

    /**
     * Filter on: the map hands its right edge to the pane and the waters appear. Resizing the
     * scroller (the map's real viewport, which content size and zoom clamps re-derive from) is
     * the whole resize, plus a re-centre on the world point that was in the middle.
     */
    /**
     * The always-on overlay: route badges and the system view's fish row belong to the map
     * itself, so this goes up the moment the screen is found, before the filter has said a word.
     * The waters arrive in it later, if and when the filter fills the blob list.
     */
    protected void mountOverlay() {
        Object scroller = ReflectionUtils.invoke(mapScreen, "getScroller");
        Object mapWidget = ReflectionUtils.invoke(mapScreen, "getMap");
        PositionAPI scrollerPos = ((UIComponentAPI) scroller).getPosition();

        overlay = new FishPresenceOverlay();
        overlay.setMapWidget(mapWidget);

        //the toggle is sticky across map opens; a fresh overlay is told what was chosen
        overlay.setCoherenceShown(FishMapPane.isCoherenceShown());

        overlayPanel = Global.getSettings().createCustom(
                scrollerPos.getWidth(), scrollerPos.getHeight(), overlay);
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

            //the pane, on the edge the map gave up - its slot remembered so the planner can
            //borrow it
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

            //the standing overlay follows the narrowed viewport
            if (overlayPanel != null) {
                overlayPanel.getPosition().setSize(narrowWidth, paneHeight);
            }

            rebuildBlobs();
            applied = true;
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** The filter goes off: the pane and the waters leave; the overlay stays for the route and
     *  the system view, with its blob list emptied. */
    protected void deactivate() {
        try {
            //a planner holding the pane's slot goes down with it, without handing the slot back
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

            if (overlay != null) overlay.setBlobs(null);

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

    /**
     * Cuts and caches the current mode's waters. Category view shades each enabled type's whole
     * territory; species view shades up to three picks, each in its own weave (solid /
     * stripe-right / stripe-left) so overlaps cross instead of stacking, with picks sharing a
     * colour sharing one merged border instead of doubled lines.
     */
    protected void rebuildBlobs() {
        if (pane == null || overlay == null) return;

        List<FishPresenceOverlay.Blob> blobs = new ArrayList<>();

        if (pane.isCategoryView()) {
            int index = 0;

            for (FishType type : FishType.values()) {
                if (!pane.getFilter().types.contains(type)) continue;

                FishPresenceField.Mesh mesh = meshCache.get("type:" + type.name());

                if (mesh == null) {
                    List<Vector2f> hosts = FishPresence.getTypeHostLocations(type);
                    if (hosts.isEmpty()) continue;

                    mesh = FishPresenceField.build(hosts, BLOB_RADIUS);
                    meshCache.put("type:" + type.name(), mesh);
                }

                if (mesh.isEmpty()) continue;

                blobs.add(new FishPresenceOverlay.Blob(mesh, type.color,
                        getStyle(index++), true, true));
            }
        } else {
            //colour groups first, because a border is per colour rather than per pick
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

            //each pick's fill, in its own weave, with its own border only when its colour is its own
            for (int i = 0; i < picked.size(); i++) {
                FishSpec spec = picked.get(i);
                FishPresenceField.Mesh mesh = getSpeciesMesh(spec);
                if (mesh == null) continue;

                boolean colorShared = byColor.get(spec.rarity.color.getRGB()).size() > 1;

                blobs.add(new FishPresenceOverlay.Blob(mesh, spec.rarity.color,
                        getStyle(i), true, !colorShared));
            }

            //and one merged border per shared colour, around everything that colour covers
            for (List<FishSpec> group : byColor.values()) {
                if (group.size() < 2) continue;

                FishPresenceField.Mesh union = getUnionMesh(group);
                if (union == null) continue;

                blobs.add(new FishPresenceOverlay.Blob(union, group.get(0).rarity.color,
                        FishPresenceOverlay.STYLE_SOLID, false, true));
            }
        }

        overlay.setBlobs(blobs);
    }

    /** The three weaves, dealt in pick order; a fourth of anything starts the deal over. */
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

    /** The merged shape around everything a colour group covers, cached by its membership. */
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
            //pointing the map is a nicety; a species with nowhere to point is not an error
        }
    }

    protected boolean isRemembered() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(MEMORY_KEY);
    }

    protected void remember(boolean on) {
        Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, on);
    }

    /** The screen went away, or was never there. Forget everything about it. */
    protected void drop() {
        clearComponents();
        mapScreen = null;
        failed = false;

        //cut fresh next open: a catch or a bought chart between opens changes what is drawn
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
        systemPane = null;
        systemPanePanel = null;
        systemApplied = false;
        shownSystem = null;
        applied = false;
        originalScrollerWidth = 0f;

        //arrow list belonged to the old screen - drop it and force re-injection on the next one
        arrowList = null;
        injectedArrows.clear();
        lastRouteSeen = new Object();
    }

    /**
     * Something on this screen-open broke the recipe: un-narrow the map if narrowed, log once,
     * and sit the rest of this open out.
     */
    protected void fail(Throwable t) {
        Global.getLogger(FishMapFilterScript.class)
                .warn("Fish map filter bowing out of this map screen", t);

        try {
            if (mapScreen != null) {
                if (panePanel != null) ((UIPanelAPI) mapScreen).removeComponent(panePanel);
                paneStanding = false;

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
            //the withdrawal itself failing means the screen is already gone
        }

        clearComponents();
        failed = true;
    }
}
