package catchrelease.campaign.fish.map;

import catchrelease.ui.FishIcons;
import catchrelease.campaign.fish.data.FishLocationSummary;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.function.Supplier;

public final class FishTooltips {

    public static final float WIDTH = 320f;
    public static final float ICON_SIZE = 48f;
    public static final float ICON_GAP = 10f;
    // addCustom shifts a first element 5 units right; the header leaves the same room on the right edge
    public static final float MARGIN = 5f;
    public static final float OPAD = 10f;

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
                return WIDTH;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                Color gray = Misc.getGrayColor();
                Color h = Misc.getHighlightColor();
                FishLogEntry logged = FishLog.isCaught(spec.id) ? FishLog.get(spec.id) : null;

                tooltip.addCustom(createHeader(spec, logged), 0f);

                if (logged != null && logged.recordLength > 0f) {
                    String length = String.format("%.2f m", logged.recordLength);
                    String weight = String.format("%.1f kg", logged.recordWeight);

                    if (logged.recordSystemName == null) {
                        tooltip.addPara("Record: %s, %s.", OPAD, gray, h, length, weight);
                    } else {
                        tooltip.addPara("Record: %s, %s, in %s.", OPAD, gray, h, length, weight,
                                logged.recordSystemName);
                    }
                }

                tooltip.addPara("Range: %s", OPAD, gray, Misc.getTextColor(), FishLocationSummary.describe(spec));

                if (Global.getSettings().isDevMode() && !spec.hasHabitat()) {
                    tooltip.addPara("No region data in the table.", Misc.getNegativeHighlightColor(), OPAD);
                }

                java.util.List<String> wantedBy = catchrelease.campaign.fish.shop.ShopMarks.getRequiredBy(spec);
                if (!wantedBy.isEmpty()) {
                    tooltip.addPara("Wanted for: %s", OPAD, gray, h, String.join(", ", wantedBy));
                }

                String action = actionLine == null ? null : actionLine.get();
                tooltip.addPara(action != null ? action : "F2 opens the codex.", gray, OPAD);
            }
        };
    }

    // Icon beside the name, as vanilla's image-with-text block does. FishIcons draws the silhouette for anything not
    // yet caught, which a sprite-based beginImageWithText cannot.
    protected static CustomPanelAPI createHeader(FishSpec spec, FishLogEntry logged) {
        float width = WIDTH - 2f * MARGIN;
        CustomPanelAPI header = Global.getSettings().createCustom(width, ICON_SIZE, new BaseCustomUIPanelPlugin() {
            private PositionAPI pos;

            @Override
            public void positionChanged(PositionAPI position) {
                pos = position;
            }

            @Override
            public void render(float alphaMult) {
                if (pos == null) return;
                FishIcons.draw(spec, pos.getX() + ICON_SIZE / 2f, pos.getY() + pos.getHeight() - ICON_SIZE / 2f,
                        ICON_SIZE, alphaMult);
            }
        });

        TooltipMakerAPI text = header.createUIElement(width - ICON_SIZE - ICON_GAP, ICON_SIZE, false);
        text.addPara(spec.getDisplayName(), spec.rarity.color, 0f);
        // type persists whether or not caught - it's what the lists sort/filter by
        text.addPara("%s " + spec.getTypeName().toLowerCase(), 2f, Misc.getGrayColor(), spec.rarity.color,
                Misc.ucFirst(spec.rarity.name().toLowerCase()));

        if (logged != null) {
            text.addPara("Landed %s.", 2f, Misc.getGrayColor(), Misc.getHighlightColor(),
                    logged.caught == 1 ? "once" : logged.caught + " times");
        } else {
            text.addPara("Known only from range data.", Misc.getGrayColor(), 2f);
        }

        header.addUIElement(text).inTL(ICON_SIZE + ICON_GAP, 0f);
        // three lines can run taller than the icon; the panel's height is what pushes the next paragraph down
        header.getPosition().setSize(width, Math.max(ICON_SIZE, text.getHeightSoFar()));

        return header;
    }
}
