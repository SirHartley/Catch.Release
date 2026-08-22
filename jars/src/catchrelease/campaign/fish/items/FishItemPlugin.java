package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
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
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class FishItemPlugin extends BaseSpecialItemPlugin {

    public FishCatch getCatch() {
        SpecialItemData data = stack == null ? null : stack.getSpecialDataIfSpecial();

        return data == null ? null : FishCatch.decode(data.getData());
    }

    @Override
    public String getName() {
        FishCatch entry = getCatch();
        if (entry == null) return super.getName();

        return entry.getDisplayName();
    }

    @Override
    public int getPrice(MarketAPI market, SubmarketAPI submarket) {
        FishCatch entry = getCatch();

        return entry == null ? 0 : (int) entry.getValue();
    }

    @Override
    public boolean hasRightClickAction() {
        return getCatch() != null;
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        FishCatch clicked = getCatch();
        if (clicked == null || helper == null) return;

        List<FishCatch> stowed = new ArrayList<>();
        List<SpecialItemData> looseData = new ArrayList<>();
        List<Integer> looseCounts = new ArrayList<>();
        SpecialItemData clickedData = stack.getSpecialDataIfSpecial();

        if (isBulkDown()) {
            // Snapshot before changing anything: the replacement has to be written into the clicked cell before the other source cells are emptied.
            for (CargoStackAPI other : FishItems.getFishStacks(stack.getCargo(), clicked.speciesId)) {
                SpecialItemData data = other.getSpecialDataIfSpecial();
                FishCatch entry = FishCatch.decode(data.getData());
                int count = (int) other.getSize();

                for (int i = 0; i < count; i++) stowed.add(entry);
                looseData.add(data);
                looseCounts.add(count);
            }
        } else {
            stowed.add(clicked);
        }

        if (stowed.isEmpty()) return;

        CargoStackAPI existing = FishItems.getBundleStack(stack.getCargo(), clicked.speciesId);
        SpecialItemData existingData = null;
        if (existing != null) {
            existingData = existing.getSpecialDataIfSpecial();
            stowed.addAll(FishItems.decodeBundle(existingData.getData()));
        }

        int clickedCount = isBulkDown() ? (int) stack.getSize() : 1;
        helper.removeFromClickedStackFirst(clickedCount);
        helper.addItems(CargoItemType.SPECIAL, FishItems.toBundle(stowed), 1);

        if (isBulkDown()) {
            for (int i = 0; i < looseData.size(); i++) {
                if (looseData.get(i).equals(clickedData)) continue;
                helper.removeFromAnyStack(CargoItemType.SPECIAL, looseData.get(i), looseCounts.get(i));
            }
        }

        if (existingData != null) {
            helper.removeFromAnyStack(CargoItemType.SPECIAL, existingData, 1);
        }
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult,
                       SpecialItemRendererAPI renderer) {
        FishCatch entry = getCatch();
        FishSpec spec = entry == null ? null : entry.getSpec();

        // the spec's own icon is blank, so this is the icon rather than something drawn over one
        FishItemRenderer.renderIcon(x, y, w, h, alphaMult, glowMult, getIconPath(spec));

        if (entry == null) return;

        FishItemRenderer.render(x, y, w, h, alphaMult,
                spec == null ? null : spec.rarity, entry.getGrade());

        if (ShopMarks.isWanted(entry)) {
            ShopMarks.drawDot(x + w - ShopMarks.DOT_INSET, y + ShopMarks.DOT_INSET,
                    ShopMarks.DOT_RADIUS, alphaMult);
        }
    }

    protected String getIconPath(FishSpec spec) {
        if (spec == null || spec.icon == null || spec.icon.isEmpty()) {
            return FishConstants.ITEM_ICON_FALLBACK;
        }

        return spec.icon;
    }

    protected boolean isBulkDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                              CargoTransferHandlerAPI transferHandler, Object stackSource) {
        FishCatch entry = getCatch();
        if (entry == null) {
            super.createTooltip(tooltip, expanded, transferHandler, stackSource);
            return;
        }

        FishSpec spec = entry.getSpec();
        FishGrade grade = entry.getGrade();
        float opad = 10f;

        // vanilla resolves F2 to the generic "Fish" item spec; point it at this specimen's species instead
        FishCodex.link(tooltip, entry.speciesId);

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addTitle(entry.getDisplayName());
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

        tooltip.addPara("Specimen grade: %s", opad, Misc.getGrayColor(), grade.getColor(), grade.name);
        tooltip.addPara("Length: %s   Weight: %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(),
                String.format("%.2f m", entry.length), String.format("%.1f kg", entry.weight));
        tooltip.addPara("Coherence: %s", 3f, Misc.getGrayColor(), getAberrationColor(entry.aberration),
                getAberrationLabel(entry.aberration));

        if (spec != null && spec.desc != null && !spec.desc.isEmpty()) {
            tooltip.addPara(spec.desc, Misc.getTextColor(), opad);
        }

        java.util.List<String> requiredBy = ShopMarks.getRequiredBy(entry);
        if (!requiredBy.isEmpty()) {
            tooltip.addPara("Yellow dot: needed for %s", opad, Misc.getGrayColor(),
                    Misc.getHighlightColor(), String.join(", ", requiredBy));
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource);

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addPara("Right-click to stow it with others of its kind; hold %s to stow every"
                    + " one aboard.", opad, Misc.getGrayColor(), Misc.getHighlightColor(), "control");
        }
    }

    public static int getAberrationBand(float aberration) {
        if (aberration >= 0.8f) return 4;
        if (aberration >= 0.55f) return 3;
        if (aberration >= 0.3f) return 2;
        if (aberration >= 0.12f) return 1;

        return 0;
    }

    public static String getAberrationLabel(float aberration) {
        return switch (getAberrationBand(aberration)) {
            case 4 -> "barely holding";
            case 3 -> "unstable";
            case 2 -> "slipping";
            case 1 -> "unsettled";
            default -> "stable";
        };
    }

    public static java.awt.Color getAberrationColor(float aberration) {
        if (aberration >= 0.55f) return Misc.getNegativeHighlightColor();
        if (aberration >= 0.3f) return Misc.getHighlightColor();

        return Misc.getPositiveHighlightColor();
    }
}
