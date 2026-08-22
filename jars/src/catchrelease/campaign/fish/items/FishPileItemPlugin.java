package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopMarks;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FishPileItemPlugin extends BaseSpecialItemPlugin {

    public List<FishCatch> getContents() {
        SpecialItemData data = stack == null ? null : stack.getSpecialDataIfSpecial();

        return data == null ? new ArrayList<>() : FishItems.decodeBundle(data.getData());
    }

    @Override
    public String getName() {
        List<FishCatch> contents = getContents();
        if (contents.isEmpty()) return super.getName();

        return "Catch Pile (" + contents.size() + ")";
    }

    @Override
    public int getPrice(MarketAPI market, SubmarketAPI submarket) {
        float total = 0f;
        for (FishCatch entry : getContents()) total += entry.getValue();

        return (int) total;
    }

    public static boolean sweep(RightClickActionHelper helper,
                                com.fs.starfarer.api.campaign.CargoAPI cargo,
                                SpecialItemData clickedData, int clickedCount) {
        if (helper == null || cargo == null) return false;

        List<FishCatch> gathered = new ArrayList<>();
        List<SpecialItemData> sourceData = new ArrayList<>();
        List<Integer> sourceCounts = new ArrayList<>();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!FishItems.isCatch(data)) continue;

            int count = (int) stack.getSize();

            // a loose stack can be several of the same specimen; a container is one item however many swim in it, and its whole list comes across each time
            for (int i = 0; i < count; i++) gathered.addAll(FishItems.read(data));
            sourceData.add(data);
            sourceCounts.add(count);
        }

        if (gathered.isEmpty()) return false;

        helper.removeFromClickedStackFirst(clickedCount);
        helper.addItems(CargoItemType.SPECIAL, FishItems.toPile(gathered), 1);

        for (int i = 0; i < sourceData.size(); i++) {
            if (sourceData.get(i).equals(clickedData)) continue;
            helper.removeFromAnyStack(CargoItemType.SPECIAL, sourceData.get(i), sourceCounts.get(i));
        }

        return true;
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

        Map<String, List<FishCatch>> bySpecies = new LinkedHashMap<>();
        for (FishCatch entry : contents) {
            bySpecies.computeIfAbsent(entry.speciesId, id -> new ArrayList<>()).add(entry);
        }

        helper.removeFromClickedStackFirst(1);

        for (List<FishCatch> species : bySpecies.values()) {
            SpecialItemData unpacked = species.size() == 1
                    ? FishItems.toItem(species.get(0)) : FishItems.toBundle(species);

            helper.addItems(CargoItemType.SPECIAL, unpacked, 1);
        }
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult,
                       SpecialItemRendererAPI renderer) {
        super.render(x, y, w, h, alphaMult, glowMult, renderer);

        List<FishCatch> contents = getContents();
        if (contents.isEmpty()) return;

        FishRarity best = null;
        for (FishCatch entry : contents) {
            FishSpec spec = entry.getSpec();
            if (spec == null) continue;

            if (best == null || spec.rarity.rank > best.rank) best = spec.rarity;
        }

        // no grade pip: a pile is not a specimen and has no single quality to report
        FishItemRenderer.render(x, y, w, h, alphaMult, best, null);

        for (FishCatch entry : contents) {
            if (ShopMarks.isWanted(entry)) {
                ShopMarks.drawDot(x + w - ShopMarks.DOT_INSET, y + ShopMarks.DOT_INSET,
                        ShopMarks.DOT_RADIUS, alphaMult);
                break;
            }
        }
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                              CargoTransferHandlerAPI transferHandler, Object stackSource) {
        List<FishCatch> contents = getContents();
        if (contents.isEmpty()) {
            super.createTooltip(tooltip, expanded, transferHandler, stackSource);
            return;
        }

        float opad = 10f;

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addTitle(getName());
        } else {
            tooltip.addSpacer(-opad);
        }

        Map<String, Integer> bySpecies = new LinkedHashMap<>();
        for (FishCatch entry : contents) {
            bySpecies.merge(entry.getDisplayName(), 1, Integer::sum);
        }

        tooltip.addPara("Holds %s of %s.", opad, Misc.getGrayColor(), Misc.getHighlightColor(),
                contents.size() + (contents.size() == 1 ? " specimen" : " specimens"),
                bySpecies.size() + (bySpecies.size() == 1 ? " species" : " species"));

        for (Map.Entry<String, Integer> line : bySpecies.entrySet()) {
            tooltip.addPara(BaseIntelPlugin.BULLET + "%s   %s", 3f, Misc.getHighlightColor(),
                    line.getKey(), "x" + line.getValue());
        }

        List<String> requiredBy = new ArrayList<>();
        for (FishCatch entry : contents) {
            for (String name : ShopMarks.getRequiredBy(entry)) {
                if (!requiredBy.contains(name)) requiredBy.add(name);
            }
        }

        if (!requiredBy.isEmpty()) {
            tooltip.addPara("Yellow dot: needed for %s", opad, Misc.getGrayColor(),
                    Misc.getHighlightColor(), String.join(", ", requiredBy));
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource);

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addPara("Right-click to unpack loose singles and crate repeated species.",
                    Misc.getGrayColor(), opad);
        }
    }
}
