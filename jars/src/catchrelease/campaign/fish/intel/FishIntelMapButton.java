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

public final class FishIntelMapButton {

    public static final String BUTTON_ID = "catchrelease_open_fishing_map";
    public static final String PLOT_ROUTE_BUTTON_ID = "catchrelease_plot_fish_route";
    public static final String SET_AUTOPILOT_BUTTON_ID = "catchrelease_set_intel_autopilot";
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

    public static void addSetAutopilot(TooltipMakerAPI info, float width,
                                       SectorEntityToken destination) {
        if (info == null || destination == null
                || Global.getCurrentState() != GameState.CAMPAIGN) return;

        float buttonWidth = Math.min(width, 260f);
        info.addButton("Set autopilot", SET_AUTOPILOT_BUTTON_ID, Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), (int) buttonWidth, HEIGHT, 18f);

        info.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 360f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                                      Object tooltipParam) {
                tooltip.addPara("Lays in a course to this intel's current destination.", 0f);
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
    }

    public static List<FishRequirement> forSpecies(String speciesId) {
        if (speciesId == null) return null;

        FishRequirement ask = new FishRequirement();
        ask.speciesId = speciesId;
        List<FishRequirement> asks = new ArrayList<>();
        asks.add(ask);
        return asks;
    }

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

    public static boolean handleSetAutopilot(Object buttonId, SectorEntityToken destination) {
        if (!SET_AUTOPILOT_BUTTON_ID.equals(buttonId)) return false;

        layInCourse(destination);
        return true;
    }

    public static boolean handlePlotRoute(Object buttonId, SectorEntityToken destination) {
        if (!PLOT_ROUTE_BUTTON_ID.equals(buttonId)) return false;

        layInCourse(destination);
        return true;
    }

    protected static void layInCourse(SectorEntityToken destination) {
        if (destination != null && Global.getSector() != null) {
            Global.getSector().layInCourseFor(destination);
        }
    }
}
