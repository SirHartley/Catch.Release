package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
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
 * Puts a Fish toggle on the sector map's own filter row, beside Starscape and Fuel range, and
 * runs everything that follows from pressing it: the map narrows, the filter pane takes the
 * freed edge, and the shown species' waters appear over the map as merged shapes.
 * <p>
 * The button is built the way vanilla builds its own - the same checkbox renderer class, found
 * through the row's last button rather than by name, with the player faction's colours and the
 * game's default font - so it is pixel-for-pixel a member of the row rather than a guest at it.
 * Clicks are read by polling {@code isChecked} every frame: the game's buttons flip that flag
 * before consulting any listener, so no listener - and no obfuscated listener interface - is
 * needed at all.
 * <p>
 * The narrowing leans on how the map actually lays itself out: the visible viewport is the
 * scroller's rectangle, the map re-derives content size and zoom clamps from that rectangle
 * every frame, and the frame is drawn around it - so resizing the scroller and re-centring on
 * the world point the player was looking at is the whole of the resize, and everything else
 * corrects itself a frame later. The waters ride in the scroller's overlay layer, which the
 * game scissors to the map rectangle unconditionally.
 * <p>
 * The map screen is rebuilt on every open, so the insertion is redone each time; whether the
 * filter was on is kept in sector memory, so the map reopens the way it was left. Every step
 * fails soft - a surprise means the button simply is not there, and the sector map is exactly
 * as vanilla made it.
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

    /**
     * How long a parked species request stays good. Generous next to the frame or two the tab
     * switch actually takes, and short next to a play session - a request the player never
     * followed through on must not lie in wait and reshape a map they open much later.
     */
    public static final long PENDING_SPECIES_MILLIS = 10_000L;

    /**
     * A species somebody outside asked the map to open on - the codex's "show on the sector map".
     * Parked statically because the asker lives in a dialog that is gone before the map exists,
     * and nothing outside can reach the live pane anyway.
     */
    protected static String pendingSpeciesId;
    protected static long pendingSpeciesSetAt;

    /** The map screen instance the button currently lives on. A new open means a new one. */
    protected Object mapScreen;

    /** Latched when this screen-open went wrong; cleared when the screen is rebuilt. */
    protected boolean failed = false;

    protected ButtonAPI fishButton;
    protected boolean applied = false;
    protected float originalScrollerWidth = 0f;

    protected FishMapPane pane;
    protected CustomPanelAPI panePanel;
    protected CustomPanelAPI overlayPanel;
    protected FishPresenceOverlay overlay;

    protected FishRoutePopup popup;
    protected CustomPanelAPI popupPanel;

    /** The map's own arrow list and what this script put in it, for taking it back out. */
    protected Object arrowList;
    protected final List<Object> injectedArrows = new ArrayList<>();
    protected Object lastRouteSeen;

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

                //the map reopens the way it was left
                if (fishButton != null && isRemembered()) fishButton.setChecked(true);
            } catch (Throwable t) {
                fail(t);
            }
        }

        if (failed || fishButton == null) return;

        try {
            //a parked request turns the filter on itself: the memory flag only speaks when the
            //screen is freshly attached, and the codex may have been opened over a map that was
            //already up with the filter off
            if (hasFreshPendingSpecies() && !fishButton.isChecked()) fishButton.setChecked(true);

            boolean wanted = fishButton.isChecked();

            if (wanted != applied) {
                if (wanted) activate();
                else deactivate();
            }

            if (applied && pendingSpeciesId != null) applyPendingSpecies();

            syncRouteArrows();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * Keeps the map's own arrow list carrying the plotted route - the same list intel arrows
     * ride, so the legs wear exactly the game's arrow style and show on the plain map too.
     * <p>
     * The list is found on the map's params object by shape: the one non-null {@code List}
     * field. Two lists live there - arrows and markers - and only the arrows are built non-null,
     * which is the whole of how they are told apart; if that ever stops being true, the arrows
     * are quietly skipped and the route still shows as its badges. Re-run whenever the route
     * object changes identity, and everything this script added is pulled back out first, so a
     * closed route takes its arrows with it.
     */
    protected void syncRouteArrows() {
        FishRoute.Saved route = FishRoute.get();
        if (route == lastRouteSeen) return;

        if (arrowList instanceof List) ((List<?>) arrowList).removeAll(injectedArrows);
        injectedArrows.clear();
        lastRouteSeen = route;

        if (route == null || route.stops.isEmpty() || mapScreen == null) return;
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

    /** The planner floats over the middle of the map, sized to itself. */
    @Override
    public void onPlannerRequested() {
        if (popupPanel != null || mapScreen == null) return;

        try {
            Object scroller = ReflectionUtils.invoke(mapScreen, "getScroller");
            PositionAPI scrollerPos = ((UIComponentAPI) scroller).getPosition();
            PositionAPI screenPos = ((UIComponentAPI) mapScreen).getPosition();

            float x = scrollerPos.getX() + (scrollerPos.getWidth() - FishRoutePopup.WIDTH) * 0.5f
                    - screenPos.getX();
            float y = (screenPos.getY() + screenPos.getHeight())
                    - (scrollerPos.getY() + scrollerPos.getHeight())
                    + (scrollerPos.getHeight() - FishRoutePopup.HEIGHT) * 0.5f;

            popup = new FishRoutePopup(this);
            popupPanel = Global.getSettings().createCustom(
                    FishRoutePopup.WIDTH, FishRoutePopup.HEIGHT, popup);

            ((UIPanelAPI) mapScreen).addComponent(popupPanel)
                    .setSize(FishRoutePopup.WIDTH, FishRoutePopup.HEIGHT)
                    .inTL(x, y);
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    public void onRoutePlotted(FishRoute.Saved route) {
        closePlanner();

        //the arrows and badges land this same frame through syncRouteArrows; the map is pointed
        //at the first stop so the route reads from its beginning
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

    protected void closePlanner() {
        if (popupPanel != null && mapScreen != null) {
            try {
                ((UIPanelAPI) mapScreen).removeComponent(popupPanel);
            } catch (Throwable ignored) {
                //the screen is already gone, and the panel with it
            }
        }

        popup = null;
        popupPanel = null;
    }

    /**
     * Parks a species for the next time the filter is up, and turns the remembered flag on so the
     * filter comes up by itself - which, when the map is already open with the filter on, is this
     * same frame. The rest happens in {@link #applyPendingSpecies()} once there is a pane to
     * apply it to.
     */
    public static void requestSpeciesFocus(String speciesId) {
        pendingSpeciesId = speciesId;
        pendingSpeciesSetAt = System.currentTimeMillis();

        if (Global.getSector() != null) {
            Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, true);
        }
    }

    protected static boolean hasFreshPendingSpecies() {
        return pendingSpeciesId != null
                && System.currentTimeMillis() - pendingSpeciesSetAt <= PENDING_SPECIES_MILLIS;
    }

    /**
     * The parked request, honoured now that the pane exists: the pane flips to SPECIES with the
     * species picked, the waters are re-cut, and the map points where a row click would have
     * pointed it. Consumed before anything else is done, so a request that goes wrong is a
     * request that is over rather than one that fires again on the next open.
     */
    protected void applyPendingSpecies() {
        boolean fresh = hasFreshPendingSpecies();
        String id = pendingSpeciesId;
        pendingSpeciesId = null;

        if (!fresh) return;

        FishSpec spec = FishPresence.getSpec(id);
        if (spec == null || pane == null) return;

        //the waters live in hyperspace coordinates, and a map opened from inside a system comes
        //up showing that system - flipped to the hyper view first, through the same stable
        //method the game calls when the player's location changes, so the jump lands on a map
        //the focus point means something on
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

        //the map screen by capability: the one panel in the game that resizes a map and scroller
        return ReflectionUtils.hasMethodOfName(tab, "updateMapAndScrollerSize") ? tab : null;
    }

    /**
     * Builds the Fish button out of the row's own parts and hangs it after the last vanilla
     * button. The checkbox renderer is found by shape - the field on the template's renderer
     * whose class takes (label, font, three colours) - so no obfuscated name is ever written
     * down, and the row's look is inherited rather than imitated.
     */
    protected void insertButton() {
        Object filterRow = ReflectionUtils.invoke(mapScreen, "getFilter");
        if (filterRow == null) throw new IllegalStateException("no filter row on the map screen");

        ButtonAPI template = (ButtonAPI) ReflectionUtils.get(filterRow, "constellations");
        Object renderer = ReflectionUtils.invoke(template, "getRenderer");

        //the checkbox class is either the renderer itself or the one thing the renderer wraps -
        //both are asked, by shape, for the (label, font, three colours) constructor
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

        //the bracketed key is written into the label by hand: vanilla's auto-append only runs
        //for its own rebindable keys, and the raw-code path carries no display name
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

        //the game's buttons do not take the checkbox raw: vanilla's factory reads
        //  new n(new m(checkbox), listener)
        //where m is the adapter the template's getRenderer() returned - the checkbox speaks one
        //renderer dialect and the button another, and m is the translation. So the adapter route
        //goes first, through the template renderer's own one-argument constructor; the direct
        //route stays as the fallback for the day the adapter stops existing.
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
     * The filter goes on: the map hands its right edge to the pane and the waters appear.
     * The scroller is the map's real viewport - content size, zoom clamps and the drawn frame
     * all re-derive from its rectangle - so the resize is the scroller's, plus a re-centre on
     * the world point the player had in the middle.
     */
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

            //the pane, on the edge the map gave up
            PositionAPI screenPos = ((UIComponentAPI) mapScreen).getPosition();
            float paneX = scrollerPos.getX() + narrowWidth + PANE_GAP - screenPos.getX();
            float paneY = screenPos.getY() + screenPos.getHeight()
                    - (scrollerPos.getY() + scrollerPos.getHeight());
            float paneHeight = scrollerPos.getHeight();

            pane = new FishMapPane(this);
            panePanel = Global.getSettings().createCustom(FishMapPane.WIDTH, paneHeight, pane);
            pane.mount(panePanel, FishMapPane.WIDTH, paneHeight);

            ((UIPanelAPI) mapScreen).addComponent(panePanel)
                    .setSize(FishMapPane.WIDTH, paneHeight)
                    .inTL(paneX, paneY);

            //the waters, in the scroller's overlay layer - over the map, cut at its edge
            overlay = new FishPresenceOverlay();
            overlay.setMapWidget(mapWidget);

            overlayPanel = Global.getSettings().createCustom(narrowWidth, paneHeight, overlay);
            ReflectionUtils.invoke(scroller, "addToOverlay", overlayPanel);

            rebuildBlobs();
            remember(true);
            applied = true;
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** The filter goes off: the pane and the waters leave, the map takes its edge back. */
    protected void deactivate() {
        try {
            Object scroller = ReflectionUtils.invoke(mapScreen, "getScroller");
            Object mapWidget = ReflectionUtils.invoke(mapScreen, "getMap");
            PositionAPI scrollerPos = ((UIComponentAPI) scroller).getPosition();

            Vector2f keep = (Vector2f) ReflectionUtils.invoke(mapWidget, "getWorldLocation",
                    scrollerPos.getCenterX(), scrollerPos.getCenterY());

            if (panePanel != null) ((UIPanelAPI) mapScreen).removeComponent(panePanel);
            if (overlayPanel != null) ReflectionUtils.invoke(scroller, "removeFromOverlay", overlayPanel);

            if (originalScrollerWidth > 0f) {
                scrollerPos.setSize(originalScrollerWidth, scrollerPos.getHeight());
                ReflectionUtils.invoke(mapScreen, "centerOn", keep);
            }

            pane = null;
            panePanel = null;
            overlayPanel = null;
            overlay = null;

            remember(false);
            applied = false;
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * The waters the current mode calls for, cut once and cached. The survey shades each enabled
     * type's whole territory; the species view shades exactly what the player has picked, up to
     * three, each fill in its own weave - solid, striped right, striped left - so overlaps cross
     * instead of piling. Picks that share a colour also share one merged border, cut from the
     * union of their systems: the same colour twice over would only stack lines.
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
        overlayPanel = null;
        overlay = null;
        popup = null;
        popupPanel = null;
        applied = false;
        originalScrollerWidth = 0f;

        //the arrow list belonged to the screen that just went - forget it, and force the next
        //screen to re-inject by making the last-seen route compare unequal to any real one
        arrowList = null;
        injectedArrows.clear();
        lastRouteSeen = new Object();
    }

    /**
     * Something on this screen-open did not read the way the recipe expects. Put the map back
     * if it was narrowed, log the reason once, and sit the rest of this open out - a failed
     * filter costs a convenience and leaves the sector map exactly as vanilla made it.
     */
    protected void fail(Throwable t) {
        Global.getLogger(FishMapFilterScript.class)
                .warn("Fish map filter bowing out of this map screen", t);

        try {
            if (mapScreen != null) {
                if (panePanel != null) ((UIPanelAPI) mapScreen).removeComponent(panePanel);

                Object scroller = ReflectionUtils.invokeIfExists(mapScreen, "getScroller");
                if (scroller != null && overlayPanel != null) {
                    ReflectionUtils.invokeIfExists(scroller, "removeFromOverlay", overlayPanel);
                }

                if (scroller != null && applied && originalScrollerWidth > 0f) {
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
