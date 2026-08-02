package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishSpec;
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
 * One landed specimen in the hold.
 * <p>
 * Every specimen carries its own length, weight and aberration, so no two stack - which is correct
 * but fills a hold quickly. Right-clicking one stows it into that species' bundle, and holding
 * control stows every one of that species at once.
 * <p>
 * Control rather than shift, which is what a bulk action would normally be: the cargo screen routes
 * shift and right-click together to its own mass-transfer path and never asks the item about it, so
 * a shift-right-click on a fish is a transfer and cannot be anything else.
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

    /** Handled here rather than by the frame, since what is removed depends on the shift key. */
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

        //fold into the species' existing bundle if it has one, which means replacing it - a bundle's
        //contents are its data, so a bundle that has grown is a different item to the one before it
        CargoStackAPI existing = FishItems.getBundleStack(stack.getCargo(), clicked.speciesId);
        if (existing != null) {
            SpecialItemData data = existing.getSpecialDataIfSpecial();
            stowed.addAll(FishItems.decodeBundle(data.getData()));
            helper.removeFromAnyStack(CargoItemType.SPECIAL, data, (int) existing.getSize());
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
    }

    /**
     * The species' icon where there is a species. The stand-in covers the codex, which builds a
     * plugin with no stack behind it and so has no specimen to read one off.
     */
    protected String getIconPath(FishSpec spec) {
        if (spec == null || spec.icon == null || spec.icon.isEmpty()) {
            return FishConstants.ITEM_ICON_FALLBACK;
        }

        return spec.icon;
    }

    /**
     * Whether this click is the bulk one.
     * <p>
     * Read off the keyboard because the click never arrives with the modifier attached - the helper
     * the cargo screen hands us says what may be moved, not what was pressed to ask for it.
     */
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
        float pad = 10f;

        tooltip.addTitle(entry.getDisplayName());

        //rarity first: it is the mark down the left edge of the icon, and nothing else said so
        if (spec != null) {
            tooltip.addPara("%s species, %s specimen", pad,
                    new java.awt.Color[]{spec.rarity.color, grade.getColor()},
                    Misc.ucFirst(spec.rarity.name().toLowerCase()), grade.name);
        } else {
            tooltip.addPara("%s specimen", pad, grade.getColor(), grade.name);
        }
        tooltip.addPara("Length: %s   Weight: %s", 3f, Misc.getHighlightColor(),
                String.format("%.2f m", entry.length), String.format("%.1f kg", entry.weight));
        tooltip.addPara("Coherence: %s", 3f, getAberrationColor(entry.aberration),
                getAberrationLabel(entry.aberration));

        if (spec != null && spec.desc != null && !spec.desc.isEmpty()) {
            tooltip.addPara(spec.desc, Misc.getGrayColor(), pad);
        }

        tooltip.addPara("Worth around %s.", pad, Misc.getHighlightColor(),
                Misc.getDGSCredits(entry.getValue()));

        //said here because the item's own description is not shown once there is a specimen to describe
        tooltip.addPara("Marked along the bottom of the icon: grade as pips, then rarity as the bar"
                + " at the end of them.", Misc.getGrayColor(), pad);
        tooltip.addPara("Right-click to stow it with others of its kind; hold %s to stow every one"
                + " aboard.", 3f, Misc.getGrayColor(), Misc.getHighlightColor(), "control");
    }

    /** Said as how well it is holding rather than as a number, which is not a thing a crew would read off. */
    public static String getAberrationLabel(float aberration) {
        if (aberration >= 0.8f) return "barely holding";
        if (aberration >= 0.55f) return "unstable";
        if (aberration >= 0.3f) return "slipping";
        if (aberration >= 0.12f) return "settled";

        return "stable";
    }

    public static java.awt.Color getAberrationColor(float aberration) {
        if (aberration >= 0.55f) return Misc.getNegativeHighlightColor();
        if (aberration >= 0.3f) return Misc.getHighlightColor();

        return Misc.getPositiveHighlightColor();
    }
}
