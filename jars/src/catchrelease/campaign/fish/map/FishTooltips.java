package catchrelease.campaign.fish.map;

import catchrelease.ui.FishIcons;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishLocationSummary;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.function.Supplier;

public final class FishTooltips {
    private FishTooltips() {
    }

    public static TooltipMakerAPI.TooltipCreator create(FishSpec spec) {
        return create(spec, null);
    }

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

                // the silhouette for anything nobody's seen - survey tells where it lives, not what it looks like. addImage can't tint, so the uncaught case paints itself
                if (caught) {
                    String icon = FishCodex.getIcon(spec);
                    if (icon != null && !icon.isEmpty()) {
                        try {
                            Global.getSettings().loadTexture(icon);
                            tooltip.addImage(icon, 48f, 48f, 0f);
                        } catch (Exception e) {
                            // a tooltip without a portrait is still a tooltip
                        }
                    }
                } else {
                    tooltip.addCustom(Global.getSettings().createCustom(48f, 48f,
                            new BaseCustomUIPanelPlugin() {
                                private PositionAPI pos;

                                @Override
                                public void positionChanged(PositionAPI position) {
                                    pos = position;
                                }

                                @Override
                                public void render(float alphaMult) {
                                    if (pos == null) return;
                                    FishIcons.draw(spec, pos.getCenterX(), pos.getCenterY(),
                                            48f, alphaMult);
                                }
                            }), 0f);
                }

                tooltip.addPara(spec.getDisplayName(), spec.rarity.color, 8f);

                // type persists whether or not caught - it's what the lists sort/filter by
                tooltip.addPara(spec.getTypeName(), Misc.getGrayColor(), 2f);

                if (caught && logged != null) {
                    tooltip.addPara("Caught " + logged.caught + (logged.caught == 1
                            ? " time." : " times."), 8f);
                } else {
                    tooltip.addPara("Known only from range data.", Misc.getGrayColor(), 8f);
                }

                tooltip.addPara("%s", 8f, Misc.getGrayColor(), Misc.getHighlightColor(),
                        FishLocationSummary.describe(spec));

                if (Global.getSettings().isDevMode() && !spec.hasHabitat()) {
                    tooltip.addPara("No region data in the table.", Misc.getNegativeHighlightColor(), 8f);
                }

                java.util.List<String> requiredBy =
                        catchrelease.campaign.fish.shop.ShopMarks.getRequiredBy(spec);
                if (!requiredBy.isEmpty()) {
                    tooltip.addPara("Required by: %s", 8f, Misc.getGrayColor(),
                            Misc.getHighlightColor(), String.join(", ", requiredBy));
                }

                String action = actionLine == null ? null : actionLine.get();
                tooltip.addPara(action != null ? action : "F2 opens the codex.",
                        Misc.getGrayColor(), 8f);
            }
        };
    }
}
