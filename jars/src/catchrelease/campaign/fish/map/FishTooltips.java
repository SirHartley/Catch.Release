package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLocationSummary;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.function.Supplier;

/**
 * The one species tooltip, used everywhere a fish icon answers a hover - the sidebar's rows,
 * the intel Planets panel, anywhere else that grows one. Portrait (or the generic mark for a
 * species nobody has landed), name in the rarity's colour, type, the catch record, where it
 * lives, and the codex key - so every corner of the UI tells the same story about the same
 * fish.
 */
public final class FishTooltips {

    private FishTooltips() {
    }

    public static TooltipMakerAPI.TooltipCreator create(FishSpec spec) {
        return create(spec, null);
    }

    /**
     * @param actionLine read at hover time, so a context whose hint depends on live state
     *                   (the sidebar's selection cap) stays truthful; null gets the codex key
     */
    public static TooltipMakerAPI.TooltipCreator create(FishSpec spec,
                                                        Supplier<String> actionLine) {
        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 320f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                boolean caught = FishLog.isCaught(spec.id);
                FishLogEntry logged = FishLog.get(spec.id);

                //generic mark for anything nobody's seen - survey tells where it lives, not
                //what it looks like
                String icon = caught ? FishCodex.getIcon(spec) : FishConstants.ITEM_ICON_FALLBACK;
                if (icon != null && !icon.isEmpty()) {
                    try {
                        Global.getSettings().loadTexture(icon);
                        tooltip.addImage(icon, 48f, 48f, 0f);
                    } catch (Exception e) {
                        //a tooltip without a portrait is still a tooltip
                    }
                }

                tooltip.addPara(spec.getDisplayName(), spec.rarity.color, 8f);

                //type persists whether or not caught - it's what the lists sort/filter by
                tooltip.addPara(spec.getTypeName(), Misc.getGrayColor(), 2f);

                if (caught && logged != null) {
                    tooltip.addPara("Caught " + logged.caught + (logged.caught == 1
                            ? " time." : " times."), 8f);
                } else {
                    tooltip.addPara("Known only from survey data.", Misc.getGrayColor(), 8f);
                }

                tooltip.addPara("%s", 8f, Misc.getGrayColor(), Misc.getHighlightColor(),
                        FishLocationSummary.describe(spec));

                if (Global.getSettings().isDevMode() && !spec.hasHabitat()) {
                    tooltip.addPara("No region data in the table.", Misc.getHighlightColor(), 8f);
                }

                String action = actionLine == null ? null : actionLine.get();
                tooltip.addPara(action != null ? action : "F2 opens the codex.",
                        Misc.getGrayColor(), 8f);
            }
        };
    }
}
