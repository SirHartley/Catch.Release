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

/**
 * One landed specimen in the hold. Each carries its own length/weight/aberration so specimens
 * never stack; right-click stows it into that species' bundle, hold ctrl to stow all of them.
 * <p>
 * Ctrl, not shift - shift+right-click is intercepted by the cargo screen's own mass-transfer path
 * before the item ever sees it.
 */
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

    /** Handled here rather than by the frame, since what is removed depends on the control key. */
    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @Override
    public void performRightClickAction(RightClickActionHelper helper) {
        FishCatch clicked = getCatch();
        if (clicked == null || helper == null) return;

        List<FishCatch> stowed = new ArrayList<>();

        if (isBulkDown()) {
            //every one of the species, the clicked stack included
            for (CargoStackAPI other : FishItems.getFishStacks(stack.getCargo(), clicked.speciesId)) {
                SpecialItemData data = other.getSpecialDataIfSpecial();
                FishCatch entry = FishCatch.decode(data.getData());
                int count = (int) other.getSize();

                for (int i = 0; i < count; i++) stowed.add(entry);
                helper.removeFromAnyStack(CargoItemType.SPECIAL, data, count);
            }
        } else {
            stowed.add(clicked);
            helper.removeFromClickedStackFirst(1);
        }

        if (stowed.isEmpty()) return;

        //bundle contents ARE its data, so growing it means replacing the stack, not appending.
        //Exactly one crate is taken: identical crates stack, and taking the whole stack to put a
        //single merged crate back would throw away the contents of every crate but one
        CargoStackAPI existing = FishItems.getBundleStack(stack.getCargo(), clicked.speciesId);
        if (existing != null) {
            SpecialItemData data = existing.getSpecialDataIfSpecial();
            stowed.addAll(FishItems.decodeBundle(data.getData()));
            helper.removeFromAnyStack(CargoItemType.SPECIAL, data, 1);
        }

        helper.addItems(CargoItemType.SPECIAL, FishItems.toBundle(stowed), 1);
    }

    @Override
    public void render(float x, float y, float w, float h, float alphaMult, float glowMult,
                       SpecialItemRendererAPI renderer) {

        FishCatch entry = getCatch();
        FishSpec spec = entry == null ? null : entry.getSpec();

        //the spec's own icon is blank, so this is the icon rather than something drawn over one
        FishItemRenderer.renderIcon(x, y, w, h, alphaMult, glowMult, getIconPath(spec));

        if (entry == null) return;

        FishItemRenderer.render(x, y, w, h, alphaMult,
                spec == null ? null : spec.rarity, entry.getGrade());

        //the wanted dot: a marked ware or an open job would take this specimen
        if (ShopMarks.isWanted(entry)) {
            ShopMarks.drawDot(x + w - ShopMarks.DOT_INSET, y + ShopMarks.DOT_INSET,
                    ShopMarks.DOT_RADIUS, alphaMult);
        }
    }

    /** Species icon, or the fallback - used by the codex, which builds this plugin with no stack/specimen to read from. */
    protected String getIconPath(FishSpec spec) {
        if (spec == null || spec.icon == null || spec.icon.isEmpty()) {
            return FishConstants.ITEM_ICON_FALLBACK;
        }

        return spec.icon;
    }

    /** Read from the keyboard directly - the cargo screen's click helper says what may be moved, not what modifier was held. */
    protected boolean isBulkDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    /** Vanilla special-item layout (title, type line, stat rows, description, cost label) so price reads "Sells for"/"Base value" consistently with other items. */
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

        //vanilla resolves F2 to the generic "Fish" item spec; point it at this specimen's species instead
        tooltip.setCodexEntryId(FishCodex.getEntryId(entry.speciesId));

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addTitle(entry.getDisplayName());
        } else {
            tooltip.addSpacer(-opad);
        }

        //where a manufactured thing says its design type, a grown one says its species' standing
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

        //who is asking for this exact specimen: marked gear and open jobs both
        java.util.List<String> requiredBy = ShopMarks.getRequiredBy(entry);
        if (!requiredBy.isEmpty()) {
            tooltip.addPara("Required by: %s", opad, Misc.getGrayColor(),
                    Misc.getHighlightColor(), String.join(", ", requiredBy));
        }

        addCostLabel(tooltip, opad, transferHandler, stackSource);

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addPara("Right-click to stow it with others of its kind; hold %s to stow every"
                    + " one aboard.", opad, Misc.getGrayColor(), Misc.getHighlightColor(), "control");
        }
    }

    /** Said as how well it is holding rather than as a number, which is not a thing a crew would read off. */
    public static String getAberrationLabel(float aberration) {
        if (aberration >= 0.8f) return "barely holding";
        if (aberration >= 0.55f) return "unstable";
        if (aberration >= 0.3f) return "slipping";
        if (aberration >= 0.12f) return "unsettled";

        return "stable";
    }

    public static java.awt.Color getAberrationColor(float aberration) {
        if (aberration >= 0.55f) return Misc.getNegativeHighlightColor();
        if (aberration >= 0.3f) return Misc.getHighlightColor();

        return Misc.getPositiveHighlightColor();
    }
}
