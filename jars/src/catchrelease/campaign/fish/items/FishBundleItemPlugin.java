package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopMarks;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A crate of one species, holding every specimen that was stowed in it.
 * <p>
 * The contents are the item's data, so a bundle that has grown is a different item to the one it
 * was - which is why stowing replaces the bundle rather than adding to it. Right-clicking one spills
 * it back out into individual specimens, each with the stats it went in with; holding control
 * instead sweeps the whole hold into a single {@link FishPileItemPlugin}.
 * <p>
 * Control rather than shift, the same as on a specimen - shift+right-click is intercepted by the
 * cargo screen's own mass-transfer path before the item ever sees it.
 */
public class FishBundleItemPlugin extends BaseSpecialItemPlugin {

    /** Native box art is 80x80; coordinates are measured from its upper-left corner. */
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

    /** Removal handled in {@link #performRightClickAction}, not here. */
    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        List<FishCatch> contents = getContents();
        if (contents.isEmpty() || helper == null) return;

        //the tidy-up rather than the unpack: everything aboard onto one line
        if (isBulkDown()) {
            FishPileItemPlugin.sweep(helper, stack.getCargo(), stack.getSpecialDataIfSpecial(),
                    (int) stack.getSize());
            return;
        }

        helper.removeFromClickedStackFirst(1);

        //The first unpacked specimen takes the crate's cell; later specimens are appended. This
        //keeps every pre-existing cargo cell in place while the crate expands.
        for (int i = 0; i < contents.size(); i++) {
            helper.addItems(CargoItemType.SPECIAL, FishItems.toItem(contents.get(i)), 1);
        }
    }

    /** Read from the keyboard directly - the click helper says what may be moved, not what was held. */
    protected boolean isBulkDown() {
        return org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LCONTROL)
                || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RCONTROL);
    }

    /** Marked with the species' rarity and the best grade in the crate. */
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

        //the wanted dot: a marked ware or an open job would take something in the crate
        for (FishCatch entry : contents) {
            if (ShopMarks.isWanted(entry)) {
                ShopMarks.drawDot(x + w - ShopMarks.DOT_INSET, y + ShopMarks.DOT_INSET,
                        ShopMarks.DOT_RADIUS, alphaMult);
                break;
            }
        }
    }

    /** Fits the species art to the four measured corners of the box label. */
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

    /** Converts the supplied top-left image grid to Starsector's bottom-left render space. */
    protected float gridY(float y, float h, float imageY) {
        return y + h * (BOX_ICON_GRID - imageY) / BOX_ICON_GRID;
    }

    /** Same tooltip anatomy as a single specimen; contents summarized by grade rather than one line each. */
    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                              CargoTransferHandlerAPI transferHandler, Object stackSource) {
        List<FishCatch> contents = getContents();
        if (contents.isEmpty()) {
            super.createTooltip(tooltip, expanded, transferHandler, stackSource);
            return;
        }

        FishSpec spec = contents.get(0).getSpec();
        float opad = 10f;

        //without this, F2 resolves to the generic bundle item spec rather than the species it holds
        FishCodex.link(tooltip, contents.get(0).speciesId);

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addTitle(getName());
        } else {
            tooltip.addSpacer(-opad);
        }

        if (spec != null) {
            tooltip.addPara("Species type: %s", opad, Misc.getGrayColor(), spec.rarity.color,
                    Misc.ucFirst(spec.rarity.name().toLowerCase()));
        }

        if (Global.CODEX_TOOLTIP_MODE) {
            tooltip.setParaSmallInsignia();
        }

        tooltip.addPara("Contains: %s", opad, Misc.getGrayColor(), Misc.getHighlightColor(),
                contents.size() + " specimens of " + contents.get(0).getDisplayName());

        //who is asking for anything in the crate: marked gear and open jobs both
        java.util.List<String> requiredBy = new java.util.ArrayList<>();
        for (FishCatch entry : contents) {
            for (String name : ShopMarks.getRequiredBy(entry)) {
                if (!requiredBy.contains(name)) requiredBy.add(name);
            }
        }
        if (!requiredBy.isEmpty()) {
            tooltip.addPara("Required by: %s", opad, Misc.getGrayColor(),
                    Misc.getHighlightColor(), String.join(", ", requiredBy));
        }

        Map<FishGrade, Integer> byGrade = new EnumMap<>(FishGrade.class);
        float best = 0f;
        for (FishCatch entry : contents) {
            byGrade.merge(entry.getGrade(), 1, Integer::sum);
            best = Math.max(best, entry.length);
        }

        for (Map.Entry<FishGrade, Integer> line : byGrade.entrySet()) {
            tooltip.addPara(BaseIntelPlugin.BULLET + "%s   %s", 3f, line.getKey().getColor(),
                    line.getKey().name, "x" + line.getValue());
        }

        tooltip.addPara("Longest: %s", opad, Misc.getGrayColor(), Misc.getHighlightColor(),
                String.format("%.2f m", best));

        addCostLabel(tooltip, opad, transferHandler, stackSource);

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addPara("Right-click to unpack; hold %s to sweep every fish aboard into one"
                    + " pile.", opad, Misc.getGrayColor(), Misc.getHighlightColor(), "control");
        }
    }
}
