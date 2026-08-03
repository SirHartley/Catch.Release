package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
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

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                              CargoTransferHandlerAPI transferHandler, Object stackSource) {
        List<FishCatch> contents = getContents();
        if (contents.isEmpty()) {
            super.createTooltip(tooltip, expanded, transferHandler, stackSource);
            return;
        }

        float pad = 10f;
        tooltip.addTitle(getName());

        tooltip.addPara("%s specimens of %s, stowed together.", pad, Misc.getHighlightColor(),
                "" + contents.size(), contents.get(0).getDisplayName());

        //what is in it, by grade, rather than a line each - a full crate would run off the screen
        Map<FishGrade, Integer> byGrade = new EnumMap<>(FishGrade.class);
        float best = 0f;
        for (FishCatch entry : contents) {
            byGrade.merge(entry.getGrade(), 1, Integer::sum);
            best = Math.max(best, entry.length);
        }

        for (Map.Entry<FishGrade, Integer> line : byGrade.entrySet()) {
            tooltip.addPara("%s   %s", 3f, line.getKey().getColor(),
                    line.getKey().name, "x" + line.getValue());
        }

        tooltip.addPara("Longest: %s", pad, Misc.getHighlightColor(), String.format("%.2f m", best));

        //the same two marks the crate's icon carries, said once so they are not a mystery
        tooltip.addPara("Marked across the top of the icon: the species' rarity as the bar, then the best"
                + " grade in the crate as the pips after it.", Misc.getGrayColor(), pad);
        tooltip.addPara("Right-click to unpack.", Misc.getGrayColor(), 3f);
    }
}
