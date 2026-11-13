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

    public static final float PIP_SIZE = 8f;
    public static final float PIP_PITCH = 11f;
    public static final float PAD_SIDE = 10f;
    public static final float FRESH_GAP = 6f;
    public static final float PRICE_GAP = 12f;
    public static final float OPEN_PIP_ALPHA = 0.6f;
    public static final float PRICE_SHORT_ALPHA = 0.35f;
    public static final float BLOCKED_TEXT_ALPHA = 0.6f;

    // the gap between the accent bar and the name; the star sits in it and takes its clicks
    public static final float NAME_LEFT = 30f;
    public static final float MARK_SLOT = NAME_LEFT - ACCENT_WIDTH;
    public static final float MARK_LEFT = 8f;
    public static final float MARK_SIZE = 16f;

    protected final ShopEntry entry;
    protected final Host host;
    protected transient LazyFont.DrawableString name;

    protected transient LazyFont.DrawableString mark;
    protected transient String markText;
    protected transient LazyFont.DrawableString fresh;

    public interface Host {

        boolean isSelected(ShopEntry entry);

        void onRowClicked(ShopEntry entry);

        PositionAPI getListViewport();

        // the slot Fit puts a module into: 0, or 1 while the rig has its second slot
        int getTargetSlot();
    }

    public ShopRowPlugin(ShopEntry entry, Host host) {
        this.entry = entry;
        this.host = host;
    }

    public static float getPipRowWidth(int max) {
        return max <= 0 ? 0f : (max - 1) * PIP_PITCH + PIP_SIZE;
    }

    // bought tiers in highlight, open ones light; a tier still waiting for its schematic takes the hole colour
    public static void drawPips(float x, float y, ShopEntry entry, Color hole, float holeAlpha,
                                float alphaMult) {
        int level = entry.getLevel();

        for (int rung = 1; rung <= entry.getMaxLevel(); rung++) {
            float px = x + (rung - 1) * PIP_PITCH;

            if (rung <= level) {
                ShopUi.drawQuad(px, y, PIP_SIZE, PIP_SIZE, Misc.getHighlightColor(), alphaMult);
            } else if (ShopSchematics.requires(entry.stat, rung)
                    && !ShopSchematics.has(entry.stat, rung)) {
                ShopUi.drawQuad(px, y, PIP_SIZE, PIP_SIZE, hole, holeAlpha * alphaMult);
            } else {
                ShopUi.drawQuad(px, y, PIP_SIZE, PIP_SIZE, Misc.getBasePlayerColor(),
                        OPEN_PIP_ALPHA * alphaMult);
            }
        }
    }

    @Override
    protected PositionAPI getViewport() {
        return host.getListViewport();
    }

    @Override
    protected boolean isSelected() {
        return host.isSelected(entry);
    }

    protected int slot() {
        return host.getTargetSlot();
    }

    // fitted elsewhere on the rig, or clashing with what is: greyed out for this slot only
    protected boolean isBlocked() {
        return entry.kind == ShopEntry.Kind.TACKLE && !entry.isFittedIn(slot()) && !entry.canFitIn(slot());
    }

    @Override
    protected Color getAccentColor() {
        if (isBlocked()) return Misc.getGrayColor();

        FishRarity tier = entry.getPriceRarity();
        return tier == null ? Misc.getBasePlayerColor() : tier.color;
    }

    @Override
    protected void renderBackground(float x, float y, float width, float height,
                                    boolean selected, boolean hovered, float alphaMult) {
        float fill = selected ? ShopUi.FILL_SELECTED : isBlocked() ? ShopUi.FILL_DIM
                : hovered ? ShopUi.FILL_HOVER : ShopUi.FILL_IDLE;

        ShopUi.drawQuad(x, y, width, height, ShopUi.fill(), fill * alphaMult);
        ShopUi.drawQuad(x, y, ACCENT_WIDTH, height, getAccentColor(), alphaMult);
    }

    @Override
    protected void renderContent(float x, float y, float width, float height,
                                 boolean selected, boolean hovered, float alphaMult) {
        renderMark(x, y, height, alphaMult);
        renderName(x, y, height, selected, alphaMult);
        renderState(x, y, width, height, alphaMult);
    }

    protected void renderMark(float x, float y, float height, float alphaMult) {
        // a row that cannot be marked must not draw a saving-for-this star, marked or not: a
        // BOTH-fit module bought on one shelf used to keep a lit mark on the other
        if (!ShopMarks.isMarkable(entry)) return;

        Color rim = isMouseOverMark() ? Misc.getBasePlayerColor() : Misc.getGrayColor();

        ShopMarks.drawStar(Math.round(x + MARK_LEFT + MARK_SIZE * 0.5f),
                Math.round(y + height * 0.5f), MARK_SIZE, ShopMarks.isMarked(entry), rim,
                alphaMult);
    }

    protected void renderName(float x, float y, float height, boolean selected, float alphaMult) {
        LazyFont font = ShopUi.getBodyFont();
        if (font == null) return;

        if (name == null) {
            name = ShopUi.createText(font, entry.getListName());
            name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        // fitted gear reads as good news, not as spent; a maxed ladder and a locked rung stay quiet
        Color color = entry.isFittedIn(slot()) ? Misc.getPositiveHighlightColor()
                : entry.isDone(slot()) || entry.isLocked() || isBlocked() || (entry.isCurio() && !entry.isOn())
                ? Misc.getGrayColor()
                : selected ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

        name.setBaseColor(ShopUi.withAlpha(color, (isBlocked() ? BLOCKED_TEXT_ALPHA : 1f) * alphaMult));

        // indented past the star's slot whether or not this row draws one, so the names stay in a column
        name.draw(Math.round(x + NAME_LEFT), top(y, height, name.getHeight()));
    }

    // the engine puts the odd unit of centred text below, so round the gap above down
    protected static float top(float y, float height, float textHeight) {
        return y + height - (float) Math.floor((height - textHeight) / 2f);
    }

    protected void renderState(float x, float y, float width, float height, float alphaMult) {
        float right = x + width - PAD_SIDE;

        if (entry.isUpgrade() && !entry.isMaxed()) {
            float pipsLeft = right - getPipRowWidth(entry.getMaxLevel());

            drawPips(Math.round(pipsLeft), Math.round(y + (height - PIP_SIZE) * 0.5f), entry,
                    Color.BLACK, 1f, alphaMult);
            drawFresh(pipsLeft - FRESH_GAP, y, height, alphaMult);
            return;
        }

        // a switch says which way it is thrown, both ways - Off has to be as readable as On, since an unmarked row would look like the shelf had simply not loaded
        if (entry.isCurio()) {
            drawMark(entry.isOn() ? "On" : "Off",
                    entry.isOn() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor(),
                    right, y, height, alphaMult);
            return;
        }

        if (entry.isDone(slot())) {
            drawMark(entry.isUpgrade() ? "Max" : "Fitted", Misc.getPositiveHighlightColor(),
                    right, y, height, alphaMult);
            return;
        }

        if (isBlocked()) {
            Tackle blocker = entry.getBlocker(slot());
            drawMark(blocker == entry.tackle ? "In slot " + (2 - slot()) : "Conflict", Misc.getGrayColor(),
                    right, y, height, alphaMult);
            return;
        }

        if (entry.isLocked()) {
            drawMark("Locked", Misc.getGrayColor(), right, y, height, alphaMult);
            return;
        }

        if (entry.isOwned() && entry.tackle != Tackle.NONE) {
            drawMark("Owned", Misc.getGrayColor(), right, y, height, alphaMult);
            return;
        }

        if (entry.isShipModule()) {
            drawMark("Ship", Misc.getGrayColor(), right, y, height, alphaMult);
            return;
        }

        FishRarity rarity = entry.getPriceRarity();
        if (rarity == null) return;

        ShopUi.drawQuad(Math.round(right - PIP_SIZE), Math.round(y + (height - PIP_SIZE) * 0.5f),
                PIP_SIZE, PIP_SIZE, rarity.color,
                (entry.canAfford() ? 1f : PRICE_SHORT_ALPHA) * alphaMult);
        drawFresh(right - PIP_SIZE - PRICE_GAP, y, height, alphaMult);
    }

    protected void drawFresh(float right, float y, float height, float alphaMult) {
        if (!ShopSchematics.isFresh(entry)) return;

        LazyFont body = ShopUi.getBodyFont();
        if (body == null) return;

        if (fresh == null) {
            fresh = ShopUi.createText(body, "New!");
            fresh.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }

        fresh.setBaseColor(ShopUi.withAlpha(Misc.getHighlightColor(), alphaMult));
        fresh.draw(Math.round(right), top(y, height, fresh.getHeight()));
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
        mark.draw(Math.round(right), top(y, height, mark.getHeight()));
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
                && pointX < pos.getX() + NAME_LEFT;
    }

    protected boolean isMouseOverMark() {
        return isMouseOver() && isInMarkSlot(Global.getSettings().getMouseX());
    }
}
