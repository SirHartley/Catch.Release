package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopMarks;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FishBundleItemPlugin extends BaseSpecialItemPlugin {

    public static final float BOX_ICON_GRID = 80f;
    public static final float BOX_ICON_UL_X = 49f;
    public static final float BOX_ICON_UL_Y = 35f;
    public static final float BOX_ICON_LL_X = 49f;
    public static final float BOX_ICON_LL_Y = 57f;
    public static final float BOX_ICON_UR_X = 69f;
    public static final float BOX_ICON_UR_Y = 29f;
    public static final float BOX_ICON_LR_X = 69f;
    public static final float BOX_ICON_LR_Y = 51f;

    public List<FishCatch> getContents() {
        SpecialItemData data = stack == null ? null : stack.getSpecialDataIfSpecial();

        return FishItems.decodeBundle(data == null ? null : data.getData());
    }

    @Override
    public String getName() {
        List<FishCatch> contents = getContents();
        if (contents.isEmpty()) return super.getName();

        return contents.get(0).getDisplayName() + " (" + contents.size() + ")";
    }

    @Override
    public int getPrice(MarketAPI market, SubmarketAPI submarket) {
        float total = 0f;

        for (FishCatch entry : getContents()) total += entry.getValue();

        return (int) total;
    }

    @Override
    public boolean hasRightClickAction() {
        return !getContents().isEmpty();
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        List<FishCatch> contents = getContents();
        if (contents.isEmpty() || helper == null) return;

        // the tidy-up rather than the unpack: everything aboard onto one line
        if (isBulkDown()) {
            FishPileItemPlugin.sweep(helper, stack.getCargo(), stack.getSpecialDataIfSpecial(),
                    (int) stack.getSize());
            return;
        }

        helper.removeFromClickedStackFirst(1);

        // The first unpacked specimen takes the crate's cell; later specimens are appended. This keeps every pre-existing cargo cell in place while the crate expands.
        for (int i = 0; i < contents.size(); i++) {
            helper.addItems(CargoItemType.SPECIAL, FishItems.toItem(contents.get(i)), 1);
        }
    }

    protected boolean isBulkDown() {
        return org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LCONTROL)
                || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RCONTROL);
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult,
                       SpecialItemRendererAPI renderer) {
        super.render(x, y, w, h, alphaMult, glowMult, renderer);

        List<FishCatch> contents = getContents();
        if (contents.isEmpty()) return;

        FishSpec spec = contents.get(0).getSpec();
        renderBoxIcon(x, y, w, h, alphaMult, glowMult, spec);

        FishGrade best = FishGrade.TERRIBLE;
        for (FishCatch entry : contents) {
            if (entry.getGrade().rank > best.rank) best = entry.getGrade();
        }

        FishItemRenderer.render(x, y, w, h, alphaMult, spec == null ? null : spec.rarity, best);

        for (FishCatch entry : contents) {
            if (ShopMarks.isWanted(entry)) {
                ShopMarks.drawDot(x + w - ShopMarks.DOT_INSET, y + ShopMarks.DOT_INSET,
                        ShopMarks.DOT_RADIUS, alphaMult);
                break;
            }
        }
    }

    protected void renderBoxIcon(float x, float y, float w, float h, float alphaMult,
                                 float glowMult, FishSpec spec) {
        String path = spec == null || spec.icon == null || spec.icon.isEmpty()
                ? FishConstants.ITEM_ICON_FALLBACK : spec.icon;

        FishItemRenderer.renderIconWithCorners(path,
                gridX(x, w, BOX_ICON_LL_X), gridY(y, h, BOX_ICON_LL_Y),
                gridX(x, w, BOX_ICON_UL_X), gridY(y, h, BOX_ICON_UL_Y),
                gridX(x, w, BOX_ICON_UR_X), gridY(y, h, BOX_ICON_UR_Y),
                gridX(x, w, BOX_ICON_LR_X), gridY(y, h, BOX_ICON_LR_Y),
                alphaMult, glowMult);
    }

    protected float gridX(float x, float w, float imageX) {
        return x + w * imageX / BOX_ICON_GRID;
    }

    protected float gridY(float y, float h, float imageY) {
        return y + h * (BOX_ICON_GRID - imageY) / BOX_ICON_GRID;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                              CargoTransferHandlerAPI transferHandler, Object stackSource) {
        List<FishCatch> contents = getContents();
        if (contents.isEmpty()) {
            super.createTooltip(tooltip, expanded, transferHandler, stackSource);
            return;
        }

        FishSpec spec = contents.get(0).getSpec();
        float pad = FishItemTooltips.PAD;
        float opad = FishItemTooltips.OPAD;
        Color h = Misc.getHighlightColor();

        // without this, F2 resolves to the generic bundle item spec rather than the species it holds
        FishCodex.link(tooltip, contents.get(0).speciesId);

        FishItemTooltips.addTitle(tooltip, getName(), spec);
        FishItemTooltips.addClassification(tooltip, spec);
        FishItemTooltips.addDescription(tooltip, spec);

        Map<FishGrade, Integer> byGrade = new EnumMap<>(FishGrade.class);
        FishGrade bestGrade = FishGrade.TERRIBLE;
        float longest = 0f;
        float leastAberration = 1f;
        float mostAberration = 0f;
        for (FishCatch entry : contents) {
            FishGrade grade = entry.getGrade();
            byGrade.merge(grade, 1, Integer::sum);
            if (grade.rank > bestGrade.rank) bestGrade = grade;
            longest = Math.max(longest, entry.length);
            leastAberration = Math.min(leastAberration, entry.aberration);
            mostAberration = Math.max(mostAberration, entry.aberration);
        }

        tooltip.addPara("%s, best %s, longest %s.", opad, Misc.getGrayColor(), h,
                FishItemTooltips.plural(contents.size(), "specimen", "specimens"), bestGrade.name,
                String.format("%.2f m", longest)).setHighlightColors(h, bestGrade.getColor(), h);

        FishGrade[] grades = FishGrade.values();
        for (int i = grades.length - 1; i >= 0; i--) {
            Integer count = byGrade.get(grades[i]);
            if (count == null) continue;

            tooltip.addPara(BaseIntelPlugin.BULLET + "%s   %s", pad, new Color[]{grades[i].getColor(), h},
                    grades[i].name, "x" + count);
        }

        addCoherenceRange(tooltip, leastAberration, mostAberration);

        FishItemTooltips.addWantedFor(tooltip, contents);

        addCostLabel(tooltip, opad, transferHandler, stackSource);

        FishItemTooltips.addActions(tooltip, "Right-click to unpack into loose specimens.",
                "Control-right-click sweeps every fish aboard into one pile.");
    }

    // Coherence asks accept a band or worse, so the crate shows the spread rather than an average.
    protected void addCoherenceRange(TooltipMakerAPI tooltip, float least, float most) {
        String low = Misc.ucFirst(FishItemPlugin.getAberrationLabel(least));
        String high = Misc.ucFirst(FishItemPlugin.getAberrationLabel(most));
        Color lowColor = FishItemPlugin.getAberrationColor(least);
        Color highColor = FishItemPlugin.getAberrationColor(most);

        if (low.equals(high)) {
            tooltip.addPara("Coherence: %s", FishItemTooltips.OPAD, Misc.getGrayColor(), lowColor, low);
        } else {
            tooltip.addPara("Coherence: %s to %s", FishItemTooltips.OPAD, Misc.getGrayColor(), lowColor,
                    low, high).setHighlightColors(lowColor, highColor);
        }
    }
}
