package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.ui.ListRow;
import catchrelease.ui.ShopUi;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;


public class ShopRowPlugin extends ListRow {


    public interface Host {
        boolean isSelected(ShopEntry entry);

        void onRowClicked(ShopEntry entry);


        PositionAPI getListViewport();
    }

    public static final float PIP_SIZE = 8f;
    public static final float PIP_GAP = 3f;
    public static final float PAD_SIDE = 10f;


    public static final float MARK_SLOT = 18f;
    public static final float MARK_RADIUS = 5f;

    protected final ShopEntry entry;
    protected final Host host;

    protected transient LazyFont.DrawableString name;
    protected transient LazyFont.DrawableString mark;
    protected transient String markText;
    protected transient LazyFont.DrawableString fresh;

    public ShopRowPlugin(ShopEntry entry, Host host) {
        this.entry = entry;
        this.host = host;
    }

    @Override
    protected PositionAPI getViewport() {
        return host.getListViewport();
    }

    @Override
    protected boolean isSelected() {
        return host.isSelected(entry);
    }


    @Override
    protected float getSelectedFieldAlpha() {
        return 0.5f;
    }


    @Override
    protected Color getAccentColor() {
        FishRarity tier = entry.getPriceRarity();
        return tier == null ? Misc.getBasePlayerColor() : tier.color;
    }

    @Override
    protected void renderContent(float x, float y, float width, float height,
                                 boolean selected, boolean hovered, float alphaMult) {
        renderMarkRing(x, y, height, alphaMult);
        renderName(x, y, height, selected, alphaMult);
        renderState(x, y, width, height, alphaMult);
    }


    protected void renderMarkRing(float x, float y, float height, float alphaMult) {
        boolean marked = ShopMarks.isMarked(entry);
        if (!marked && !ShopMarks.isMarkable(entry)) return;

        float cx = x + ACCENT_WIDTH + MARK_SLOT * 0.5f;
        float cy = y + height * 0.5f;

        boolean hovered = isMouseOverMark();

        if (marked) {
            catchrelease.rendering.helper.Disc.draw(cx, cy, MARK_RADIUS,
                    Misc.getHighlightColor(), 0.95f * alphaMult, 0.95f * alphaMult, false);
        }

        Color rim = marked ? Misc.getHighlightColor()
                : hovered ? Misc.getBrightPlayerColor() : Misc.getGrayColor();

        catchrelease.rendering.helper.Disc.drawOutline(cx, cy, MARK_RADIUS, rim,
                (marked ? 0.95f : hovered ? 0.9f : 0.6f) * alphaMult, 1.2f);
    }

    protected void renderName(float x, float y, float height, boolean selected, float alphaMult) {
        LazyFont font = ShopUi.getBodyFont();
        if (font == null) return;

        if (name == null) {
            name = ShopUi.createText(font, entry.getListName());
            name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        // fitted gear reads as good news, not as spent; a maxed ladder and a locked rung stay quiet
        Color color = entry.isFitted() ? Misc.getPositiveHighlightColor()
                : entry.isDone() || entry.isLocked() || (entry.isCurio() && !entry.isOn())
                ? Misc.getGrayColor()
                : selected ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

        name.setBaseColor(ShopUi.withAlpha(color, alphaMult));

        // rounded to the pixel - bitmap fonts blur off-pixel. Indented past the ring's slot whether or not this row draws one, so the names stay in a column
        name.draw(Math.round(x + ACCENT_WIDTH + MARK_SLOT + 4f),
                Math.round(y + height * 0.5f + name.getHeight() * 0.5f));
    }


    protected void renderState(float x, float y, float width, float height, float alphaMult) {
        float right = x + width - PAD_SIDE;

        if (entry.isUpgrade() && !entry.isMaxed()) {
            float pipsWidth = ShopUi.getPipRowWidth(entry.getMaxLevel(), PIP_SIZE, PIP_GAP);
            float pipsLeft = right - pipsWidth;

            // bought rungs always in the player's own colour; an unbought rung is grey only while its schematic is missing, and an ordinary dark square once it can be bought
            ShopUi.drawPips(Math.round(pipsLeft), Math.round(y + (height - PIP_SIZE) * 0.5f),
                    PIP_SIZE, PIP_GAP, entry.getLevel(), entry.getMaxLevel(),
                    Misc.getBasePlayerColor(), alphaMult,
                    rung -> ShopSchematics.requires(entry.stat, rung)
                            && !ShopSchematics.has(entry.stat, rung));

            drawFresh(pipsLeft - 6f, y, height, alphaMult);
            return;
        }

        right = drawFresh(right, y, height, alphaMult);

        // a switch says which way it is thrown, both ways - OFF has to be as readable as ON, since an unmarked row would look like the shelf had simply not loaded
        if (entry.isCurio()) {
            drawMark(entry.isOn() ? "ON" : "OFF",
                    entry.isOn() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor(),
                    right, y, height, alphaMult);
            return;
        }

        if (entry.isDone()) {
            drawMark(entry.isUpgrade() ? "MAX" : "FITTED", Misc.getPositiveHighlightColor(),
                    right, y, height, alphaMult);
            return;
        }

        if (entry.isLocked()) {
            drawMark("LOCKED", Misc.getGrayColor(), right, y, height, alphaMult);
            return;
        }

        if (entry.isOwned() && entry.tackle != Tackle.NONE) {
            drawMark("OWNED", Misc.getGrayColor(), right, y, height, alphaMult);
            return;
        }

        FishRarity rarity = entry.getPriceRarity();
        if (rarity == null) return;

        ShopUi.drawQuad(right - PIP_SIZE, y + (height - PIP_SIZE) * 0.5f, PIP_SIZE, PIP_SIZE,
                rarity.color, (entry.canAfford() ? 0.9f : 0.35f) * alphaMult);
    }


    protected float drawFresh(float right, float y, float height, float alphaMult) {
        if (!ShopSchematics.isFresh(entry)) return right;

        LazyFont small = ShopUi.getSmallFont();
        if (small == null) return right;

        if (fresh == null) {
            fresh = ShopUi.createText(small, "New!");
            fresh.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }

        fresh.setBaseColor(ShopUi.withAlpha(Misc.getHighlightColor(), alphaMult));
        fresh.draw(Math.round(right), Math.round(y + height * 0.5f + fresh.getHeight() * 0.5f));

        return right - fresh.getWidth() - 6f;
    }

    protected void drawMark(String text, Color color, float right, float y, float height,
                            float alphaMult) {

        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        if (mark == null || !text.equals(markText)) {
            markText = text;
            mark = ShopUi.createText(font, text);
            mark.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }

        mark.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        mark.draw(Math.round(right), Math.round(y + height * 0.5f + mark.getHeight() * 0.5f));
    }


    @Override
    protected void onRowClick(float pointX, float pointY) {
        if (isInMarkSlot(pointX) &&
                (ShopMarks.isMarked(entry) || ShopMarks.isMarkable(entry))) {
            ShopMarks.toggle(entry);
            return;
        }

        host.onRowClicked(entry);
    }

    protected boolean isInMarkSlot(float pointX) {
        return pointX >= pos.getX() + ACCENT_WIDTH
                && pointX <= pos.getX() + ACCENT_WIDTH + MARK_SLOT;
    }

    protected boolean isMouseOverMark() {
        return isMouseOver() && isInMarkSlot(Global.getSettings().getMouseX());
    }
}
