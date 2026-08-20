package catchrelease.campaign.fish.intel;

import catchrelease.campaign.fish.map.FishMapFilterScript;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/** One button and one handoff shared by every live Catch.Release intel entry. */
public final class FishIntelMapButton {

    public static final String BUTTON_ID = "catchrelease_open_fishing_map";
    public static final String PLOT_ROUTE_BUTTON_ID = "catchrelease_plot_fish_route";
    public static final float HEIGHT = 22f;

    private FishIntelMapButton() {
    }

    public static void add(TooltipMakerAPI info, float width, List<FishRequirement> asks) {
        if (info == null || Global.getCurrentState() != GameState.CAMPAIGN) return;

        float buttonWidth = Math.min(width, 260f);
        info.addButton("Open fishing map", BUTTON_ID, Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), (int) buttonWidth, HEIGHT, 18f);

        final boolean targeted = asks != null;
        info.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 360f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                                      Object tooltipParam) {
                if (targeted) {
                    tooltip.addPara("Opens the fishing map with known species ranges narrowed to"
                            + " this request. Size, grade, origin and catch-method terms still"
                            + " apply to the individual specimen.", 0f);
                } else {
                    tooltip.addPara("Opens the fishing overlay and centers the reported system.",
                            0f);
                }
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
    }

    /** A known destination supersedes habitat search: send the campaign autopilot there. */
    public static void addPlotRoute(TooltipMakerAPI info, float width,
                                    SectorEntityToken destination) {
        if (info == null || destination == null
                || Global.getCurrentState() != GameState.CAMPAIGN) return;

        float buttonWidth = Math.min(width, 260f);
        info.addButton("Plot route", PLOT_ROUTE_BUTTON_ID, Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), (int) buttonWidth, HEIGHT, 18f);

        info.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 360f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                                      Object tooltipParam) {
                tooltip.addPara("Lays in a course to the system named by this request.", 0f);
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
    }

    /** Convenience for one named target without exposing mutable singleton lists. */
    public static List<FishRequirement> forSpecies(String speciesId) {
        if (speciesId == null) return null;

        FishRequirement ask = new FishRequirement();
        ask.speciesId = speciesId;
        List<FishRequirement> asks = new ArrayList<>();
        asks.add(ask);
        return asks;
    }

    /**
     * Handles the shared button, parks its filter before changing tabs, and lets IntelUI center a
     * place-based entry using the public API when one is available.
     */
    public static boolean handle(Object buttonId, IntelUIAPI ui, List<FishRequirement> asks,
                                 SectorEntityToken center, String systemId) {
        if (!BUTTON_ID.equals(buttonId)) return false;

        if (asks != null) {
            FishMapFilterScript.requestRequirementsFocus(asks);
        } else {
            FishMapFilterScript.requestOverviewFocus(systemId);
        }

        if (ui != null && center != null) {
            ui.showOnMap(center);
        } else if (Global.getSector() != null && Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.MAP);
        }

        return true;
    }

    /** Uses vanilla's public route planner; the destination may be a system anchor or entity. */
    public static boolean handlePlotRoute(Object buttonId, SectorEntityToken destination) {
        if (!PLOT_ROUTE_BUTTON_ID.equals(buttonId)) return false;

        if (destination != null && Global.getSector() != null) {
            Global.getSector().layInCourseFor(destination);
        }

        return true;
    }
}
