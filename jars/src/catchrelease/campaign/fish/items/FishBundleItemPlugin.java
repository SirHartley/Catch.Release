package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishSpec;
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
 * it back out into individual specimens, each with the stats it went in with.
 */
public class FishBundleItemPlugin extends BaseSpecialItemPlugin {

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

    /** The bundle is removed here, along with the specimens that come back out of it. */
    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        List<FishCatch> contents = getContents();
        if (contents.isEmpty() || helper == null) return;

        helper.removeFromClickedStackFirst(1);

        for (FishCatch entry : contents) {
            helper.addItems(CargoItemType.SPECIAL, FishItems.toItem(entry), 1);
        }
    }

    /** Marked with the species' rarity and the best grade in the crate. */
    @Override
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult,
                       SpecialItemRendererAPI renderer) {

        super.render(x, y, w, h, alphaMult, glowMult, renderer);

        List<FishCatch> contents = getContents();
        if (contents.isEmpty()) return;

        FishGrade best = FishGrade.TERRIBLE;
        for (FishCatch entry : contents) {
            if (entry.getGrade().ordinal() > best.ordinal()) best = entry.getGrade();
        }

        FishSpec spec = contents.get(0).getSpec();

        FishItemRenderer.render(x, y, w, h, alphaMult, spec == null ? null : spec.rarity, best);
    }

    /**
     * The same anatomy the specimen's tooltip has, which is the anatomy vanilla gives an item:
     * title, typed line, labelled rows, and the base class's cost label so the price speaks with
     * the market's voice. The contents are said by grade rather than a line each - a full crate
     * would run off the screen.
     */
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

        //F2 over the crate would otherwise open the codex on the generic bundle item, which is what
        //vanilla resolves from the item spec; point it at the species the crate holds instead
        tooltip.setCodexEntryId(FishCodex.getEntryId(contents.get(0).speciesId));

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
            tooltip.addPara("Right-click to unpack.", Misc.getGrayColor(), opad);
        }
    }
}
