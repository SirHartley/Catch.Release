package catchrelease.ui;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FishPurse {

    public static final String LABEL = "Fish";
    public static final float ICON_SIZE = 20f;
    public static final float ICON_GAP = 2f;
    public static final float ITEM_GAP = 10f;
    public static final float LABEL_GAP = 6f;

    public static final float TOOLTIP_WIDTH = 320f;
    // addCustom insets a tooltip's custom panel by this much on each side
    public static final float TOOLTIP_MARGIN = 5f;
    public static final float SPECIES_ICON_SIZE = 32f;
    public static final float SPECIES_ICON_GAP = 6f;
    public static final int SPECIES_ICON_ROWS = 2;

    private FishPurse() {
    }

    // ends at right; each rarity's icon and count carries the species aboard as its tooltip. Returns the left edge
    public static float add(CustomPanelAPI parent, float right, float y, float height,
                            Map<FishRarity, Integer> wallet) {
        SpriteAPI icon = SpriteLoader.getSprite("pane_fish");
        FishRarity[] rarities = FishRarity.values();
        float x = right;

        for (int i = rarities.length - 1; i >= 0; i--) {
            FishRarity rarity = rarities[i];
            Integer held = wallet == null ? null : wallet.get(rarity);
            int count = held == null ? 0 : held;

            float left = UiKit.iconValue(parent, x, y, height, icon, rarity.color, ICON_SIZE,
                    ICON_GAP, String.valueOf(count),
                    count > 0 ? Misc.getHighlightColor() : Misc.getGrayColor());

            CustomPanelAPI spot = UiKit.plain(parent, x - left, height);
            parent.addComponent(spot).inTL(left, y);
            UiKit.tooltip(parent, spot, TOOLTIP_WIDTH, TooltipMakerAPI.TooltipLocation.BELOW, false,
                    tooltip -> describe(tooltip, rarity));

            x = left - ITEM_GAP;
        }

        return UiKit.labelLeftOf(parent, x + ITEM_GAP - LABEL_GAP, y, height, LABEL,
                Misc.getGrayColor());
    }

    // built when shown; the cargo cannot change while the pointer rests on a count
    public static void describe(TooltipMakerAPI tooltip, FishRarity rarity) {
        String name = Misc.ucFirst(rarity.name().toLowerCase());
        List<Map.Entry<FishSpec, Integer>> held = FishCurrency.countSpecies(rarity);

        tooltip.addPara("%s fish aboard", 0f, rarity.color, name);
        if (held.isEmpty()) {
            tooltip.addPara("None.", Misc.getGrayColor(), 6f);
            return;
        }

        float width = TOOLTIP_WIDTH - 2f * TOOLTIP_MARGIN;
        int perRow = Math.max(1, (int) ((width + SPECIES_ICON_GAP)
                / (SPECIES_ICON_SIZE + SPECIES_ICON_GAP)));
        int shown = Math.min(held.size(), perRow * SPECIES_ICON_ROWS);
        tooltip.addCustom(createSpeciesIcons(held.subList(0, shown), width, perRow), 6f);

        if (held.size() > shown) {
            tooltip.addPara("... and %s other species.", 6f, Misc.getGrayColor(),
                    Misc.getHighlightColor(), String.valueOf(held.size() - shown));
        }
    }

    protected static CustomPanelAPI createSpeciesIcons(List<Map.Entry<FishSpec, Integer>> held,
                                                       float width, int perRow) {
        int rows = (held.size() + perRow - 1) / perRow;
        float cell = SPECIES_ICON_SIZE + SPECIES_ICON_GAP;
        float height = rows * cell - SPECIES_ICON_GAP;

        return Global.getSettings().createCustom(width, height, new BaseCustomUIPanelPlugin() {
            private PositionAPI pos;
            private final List<LazyFont.DrawableString> counts = new ArrayList<>();

            @Override
            public void positionChanged(PositionAPI position) {
                pos = position;
            }

            @Override
            public void render(float alphaMult) {
                if (pos == null) return;

                LazyFont font = ShopUi.getBodyFont();
                if (font != null && counts.isEmpty()) {
                    for (Map.Entry<FishSpec, Integer> entry : held) {
                        LazyFont.DrawableString count = ShopUi.createText(font, "x" + entry.getValue());
                        count.setAnchor(LazyFont.TextAnchor.BOTTOM_RIGHT);
                        counts.add(count);
                    }
                }

                for (int i = 0; i < held.size(); i++) {
                    float left = pos.getX() + (i % perRow) * cell;
                    float top = pos.getY() + pos.getHeight() - (i / perRow) * cell;
                    FishIcons.draw(held.get(i).getKey(), left + SPECIES_ICON_SIZE * 0.5f,
                            top - SPECIES_ICON_SIZE * 0.5f, SPECIES_ICON_SIZE, alphaMult);

                    if (i < counts.size() && held.get(i).getValue() > 1) {
                        LazyFont.DrawableString count = counts.get(i);
                        count.setBaseColor(ShopUi.withAlpha(Misc.getHighlightColor(), alphaMult));
                        count.draw(Math.round(left + SPECIES_ICON_SIZE),
                                Math.round(top - SPECIES_ICON_SIZE));
                    }
                }
            }
        });
    }
}
