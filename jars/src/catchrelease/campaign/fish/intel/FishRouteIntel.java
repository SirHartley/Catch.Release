package catchrelease.campaign.fish.intel;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.map.FishPresence;
import catchrelease.campaign.fish.map.FishRoute;
import catchrelease.campaign.fish.map.FishRoutePlanner;
import catchrelease.ui.FishIcons;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FishRouteIntel extends BaseIntelPlugin {

    public static final String TAG = "Fishing routes";
    public static final String BUTTON_SHOW = "catchrelease_route_show";
    public static final String BUTTON_FORGET = "catchrelease_route_forget";

    public static final float STRIP_HEIGHT = 64f;
    public static final float STRIP_ICON = 40f;
    public static final float STRIP_GAP = 10f;

    protected final String title;
    protected final String purpose;
    protected final ArrayList<FishRoute.Stop> stops = new ArrayList<>();
    protected final LinkedHashMap<String, Integer> landed = new LinkedHashMap<>();
    protected final long savedAt;

    public FishRouteIntel(String title, String purpose, FishRoute.Saved route) {
        this.title = title == null || title.isBlank() ? "Fishing route" : title.trim();
        this.purpose = purpose == null || purpose.isBlank() ? null : purpose.trim();
        this.savedAt = Global.getSector().getClock().getTimestamp();

        // the live route object keeps being replotted and cleared; the entry keeps its own copy
        for (FishRoute.Stop stop : route.stops) {
            FishRoute.Stop copy = new FishRoute.Stop();
            copy.systemId = stop.systemId;
            copy.fishIds = new ArrayList<>(stop.fishIds);
            stops.add(copy);
        }
    }

    public static void onCatch(FishCatch specimen) {
        if (specimen == null || specimen.speciesId == null || Global.getSector() == null) return;

        for (IntelInfoPlugin intel
                : Global.getSector().getIntelManager().getIntel(FishRouteIntel.class)) {
            FishRouteIntel entry = (FishRouteIntel) intel;
            if (!entry.tracks(specimen.speciesId)) continue;

            entry.landed.merge(specimen.speciesId, 1, Integer::sum);
            FishIntelNotifications.update(entry, specimen);
        }
    }

    public static boolean isSaved(FishRoute.Saved route) {
        if (route == null || Global.getSector() == null) return false;

        for (FishRouteIntel intel : getAll()) {
            if (intel.matches(route)) return true;
        }

        return false;
    }

    public static List<FishRouteIntel> getAll() {
        List<FishRouteIntel> out = new ArrayList<>();
        if (Global.getSector() == null) return out;

        for (IntelInfoPlugin intel
                : Global.getSector().getIntelManager().getIntel(FishRouteIntel.class)) {
            out.add((FishRouteIntel) intel);
        }

        return out;
    }

    public List<FishRoute.Stop> getStops() {
        return stops;
    }

    public boolean matches(FishRoute.Saved route) {
        if (route.stops.size() != stops.size()) return false;

        for (int i = 0; i < stops.size(); i++) {
            FishRoute.Stop mine = stops.get(i);
            FishRoute.Stop theirs = route.stops.get(i);

            if (!Objects.equals(mine.systemId, theirs.systemId)) return false;
            if (!new LinkedHashSet<>(mine.fishIds).equals(new LinkedHashSet<>(theirs.fishIds))) {
                return false;
            }
        }

        return true;
    }

    protected boolean tracks(String speciesId) {
        for (FishRoute.Stop stop : stops) {
            if (stop.fishIds.contains(speciesId)) return true;
        }

        return false;
    }

    protected List<String> getSpeciesIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (FishRoute.Stop stop : stops) ids.addAll(stop.fishIds);

        return new ArrayList<>(ids);
    }

    protected int getLandedTotal() {
        int total = 0;
        for (Integer count : landed.values()) total += count;

        return total;
    }

    protected Map<String, String> getOpenAsks() {
        Map<String, String> byId = new LinkedHashMap<>();

        for (FishRoutePlanner.Suggestion suggestion : FishRoutePlanner.getSuggestions()) {
            if (tracks(suggestion.speciesId)) byId.put(suggestion.speciesId, suggestion.reason);
        }

        return byId;
    }

    @Override
    public String getName() {
        return title;
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);

        addBulletPoints(info, mode);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color h = Misc.getHighlightColor();
        Color tc = getBulletColorForMode(mode);
        float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

        bullet(info);

        if (getListInfoParam() instanceof FishCatch specimen) {
            FishSpec spec = specimen.getSpec();
            LabelAPI line = info.addPara("%s caught - %s so far on this route", pad, tc, h,
                    specimen.getDisplayName(),
                    String.valueOf(landed.getOrDefault(specimen.speciesId, 0)));
            if (spec != null) line.setHighlightColors(spec.rarity.color, h);
        } else {
            info.addPara("%s stops - %s species", pad, tc, h,
                    String.valueOf(stops.size()), String.valueOf(getSpeciesIds().size()));

            int total = getLandedTotal();
            if (total > 0) {
                info.addPara("%s caught since saving", 0f, tc, h, String.valueOf(total));
            }

            int open = getOpenAsks().size();
            if (open > 0) {
                info.addPara("%s still asked for", 0f, tc, h, String.valueOf(open));
            }
        }

        unindent(info);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();
        Color text = Misc.getTextColor();

        List<String> speciesIds = getSpeciesIds();

        // the route's species on the shared portrait stage, the way single catches are shown
        info.addCustom(Global.getSettings().createCustom(width, STRIP_HEIGHT,
                new BaseCustomUIPanelPlugin() {
                    private PositionAPI position;

                    @Override
                    public void positionChanged(PositionAPI newPosition) {
                        position = newPosition;
                    }

                    @Override
                    public void render(float alphaMult) {
                        if (position == null) return;

                        float step = STRIP_ICON + STRIP_GAP;
                        float x = position.getCenterX() - (speciesIds.size() - 1) * step * 0.5f;

                        for (String id : speciesIds) {
                            FishSpec spec = FishPresence.getSpec(id);
                            if (spec != null) {
                                FishIcons.draw(spec, x, position.getCenterY(), STRIP_ICON,
                                        alphaMult);
                            }
                            x += step;
                        }
                    }
                }), opad);

        if (purpose != null) {
            info.addPara("\"" + purpose + "\"", gray, opad);
        }

        info.addPara("Route saved on %s.", opad, h, getSavedDate());

        info.addPara("The plotted run:", opad);
        for (FishRoute.Stop stop : stops) {
            info.addPara("%s", 6f, text, h, getSystemName(stop));

            bullet(info);
            for (String id : stop.fishIds) {
                FishSpec spec = FishPresence.getSpec(id);
                LabelAPI line = info.addPara("%s - %s caught - %s", 2f, text, h,
                        getSpeciesName(id), String.valueOf(landed.getOrDefault(id, 0)),
                        describeReach(spec));
                line.setHighlightColors(
                        spec == null ? text : spec.rarity.color, h, gray);
            }
            unindent(info);
        }

        Map<String, String> open = getOpenAsks();
        if (!open.isEmpty()) {
            info.addPara("Still on somebody's list: %s.", opad, h, describeOpenAsks(open));
        }

        int total = getLandedTotal();
        if (total > 0) {
            info.addPara("%s of the route's fish caught since it was saved.", opad, h,
                    String.valueOf(total));
        }

        float buttonWidth = Math.min(width, 260f);
        info.addButton("Show route on map", BUTTON_SHOW, Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), (int) buttonWidth, FishIntelMapButton.HEIGHT, opad * 2f);
        info.addButton("Stop tracking", BUTTON_FORGET, Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), (int) buttonWidth, FishIntelMapButton.HEIGHT, 6f);
    }

    protected String getSavedDate() {
        return Global.getSector().getClock().createClock(savedAt).getDateString();
    }

    protected String getSystemName(FishRoute.Stop stop) {
        StarSystemAPI system = FishRoute.getSystem(stop);

        return system == null ? "Uncharted" : system.getBaseName();
    }

    protected String describeReach(FishSpec spec) {
        if (spec == null || spec.reachedBy.isEmpty()) return "rupture or lamp";
        if (spec.reachedBy.contains(CatchImplement.POND)
                && !spec.reachedBy.contains(CatchImplement.BREACH_LAMP)) return "rupture only";
        if (spec.reachedBy.contains(CatchImplement.BREACH_LAMP)
                && !spec.reachedBy.contains(CatchImplement.POND)) return "lamp only";

        return "rupture or lamp";
    }

    protected String getSpeciesName(String id) {
        FishSpec spec = FishPresence.getSpec(id);

        return spec == null ? id : spec.getDisplayName();
    }

    protected String describeOpenAsks(Map<String, String> open) {
        StringBuilder out = new StringBuilder();

        for (Map.Entry<String, String> ask : open.entrySet()) {
            if (out.length() > 0) out.append(", ");
            out.append(getSpeciesName(ask.getKey())).append(" (").append(ask.getValue()).append(")");
        }

        return out.toString();
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (BUTTON_SHOW.equals(buttonId)) {
            FishRoute.Saved route = new FishRoute.Saved();
            for (FishRoute.Stop stop : stops) {
                FishRoute.Stop copy = new FishRoute.Stop();
                copy.systemId = stop.systemId;
                copy.fishIds = new ArrayList<>(stop.fishIds);
                route.stops.add(copy);
            }

            FishRoute.set(route);
            Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.MAP);
            return;
        }

        if (BUTTON_FORGET.equals(buttonId)) {
            Global.getSector().getIntelManager().removeIntel(this);
            if (ui != null) ui.recreateIntelUI();
            return;
        }

        super.buttonPressConfirmed(buttonId, ui);
    }

    @Override
    public String getIcon() {
        return FishIntelIcon.get(CatchImplement.UNKNOWN);
    }

    @Override
    public String getSortString() {
        return getSortStringNewestFirst();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (stops.isEmpty()) return null;

        StarSystemAPI system = FishRoute.getSystem(stops.get(0));

        return system == null ? null : system.getHyperspaceAnchor();
    }

    @Override
    public List<ArrowData> getArrowData(SectorMapAPI map) {
        // the same chain the plotted route draws: player, then stop to stop in order
        List<ArrowData> arrows = new ArrayList<>();
        SectorEntityToken from = Global.getSector().getPlayerFleet();
        if (from == null) return null;

        for (FishRoute.Stop stop : stops) {
            StarSystemAPI system = FishRoute.getSystem(stop);
            if (system == null || system.getHyperspaceAnchor() == null) continue;

            ArrowData arrow = new ArrowData(from, system.getHyperspaceAnchor());
            arrow.color = Global.getSector().getPlayerFaction().getBaseUIColor();
            arrow.alphaMult = 0.5f;
            arrows.add(arrow);

            from = system.getHyperspaceAnchor();
        }

        return arrows;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(TAG);

        return tags;
    }
}
