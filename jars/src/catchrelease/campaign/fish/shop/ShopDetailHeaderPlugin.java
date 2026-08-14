package catchrelease.campaign.fish.shop;

import catchrelease.ui.ShopUi;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;

/**
 * Detail pane header: portrait in a cargo-square, name beside it, ladder or slot info underneath.
 * Square backlight colour is the next purchase's price tier, or the done-colour once finished.
 * Portrait art is a placeholder pending real assets.
 */
public class ShopDetailHeaderPlugin extends BaseCustomUIPanelPlugin {

    public static final float BOX_SIZE = 72f;
    public static final float ART_SIZE = 52f;
    public static final float TEXT_GAP = 16f;
    public static final float PIP_SIZE = 10f;
    public static final float PIP_GAP = 4f;

    protected final ShopEntry entry;

    protected PositionAPI pos;

    protected transient LazyFont.DrawableString name;
    protected transient LazyFont.DrawableString sub;

    public ShopDetailHeaderPlugin(ShopEntry entry) {
        this.entry = entry;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX();
        float y = pos.getY();
        float height = pos.getHeight();

        float boxY = y + (height - BOX_SIZE) * 0.5f;

        renderBox(x, boxY, alphaMult);
        renderText(x + BOX_SIZE + TEXT_GAP, y, height, alphaMult);
    }

    /** What the backlight says: the price tier while there is one, the done-colour once there is not. */
    protected Color getAccent() {
        if (entry.isCurio()) {
            return entry.isOn() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor();
        }

        if (entry.isLocked()) return Misc.getGrayColor();

        if (entry.isDone()) return Misc.getPositiveHighlightColor();

        FishRarity rarity = entry.getPriceRarity();

        return rarity == null ? Misc.getBasePlayerColor() : rarity.color;
    }

    protected void renderBox(float x, float y, float alphaMult) {
        ShopUi.drawQuad(x, y, BOX_SIZE, BOX_SIZE, Color.BLACK, 0.75f * alphaMult);

        Disc.draw(x + BOX_SIZE * 0.5f, y + BOX_SIZE * 0.5f, BOX_SIZE * 0.5f, getAccent(),
                0.3f * alphaMult, 0f, true);

        SpriteAPI art = SpriteLoader.getSprite(entry.isUpgrade() ? "placeholder" : "placeholder2");
        if (art != null) {
            float scale = Math.min(ART_SIZE / art.getWidth(), ART_SIZE / art.getHeight());

            art.setSize(art.getWidth() * scale, art.getHeight() * scale);
            art.setColor(Color.WHITE);
            art.setNormalBlend();
            art.setAlphaMult(alphaMult);
            art.renderAtCenter(x + BOX_SIZE * 0.5f, y + BOX_SIZE * 0.5f);
        }

        //the panes' square one-pixel border rather than the minigame's rounded dress
        Color border = Misc.getBasePlayerColor();
        ShopUi.drawQuad(x, y, BOX_SIZE, 1f, border, 0.55f * alphaMult);
        ShopUi.drawQuad(x, y + BOX_SIZE - 1f, BOX_SIZE, 1f, border, 0.55f * alphaMult);
        ShopUi.drawQuad(x, y, 1f, BOX_SIZE, border, 0.55f * alphaMult);
        ShopUi.drawQuad(x + BOX_SIZE - 1f, y, 1f, BOX_SIZE, border, 0.55f * alphaMult);
    }

    protected void renderText(float x, float y, float height, float alphaMult) {
        float top = y + height - 6f;

        LazyFont titleFont = ShopUi.getTitleFont();
        if (titleFont != null) {
            if (name == null) {
                name = ShopUi.createText(titleFont, entry.getName());
                name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            name.setBaseColor(ShopUi.withAlpha(Misc.getBrightPlayerColor(), alphaMult));
            name.draw(Math.round(x), Math.round(top));

            top -= name.getHeight() + 10f;
        }

        if (entry.isUpgrade()) renderLadder(x, top, alphaMult);
        else if (entry.isCurio()) renderSwitch(x, top, alphaMult);
        else renderSlot(x, top, alphaMult);
    }

    /** The ladder, drawn large: lit pips and the count said in words beside them. */
    protected void renderLadder(float x, float top, float alphaMult) {
        int level = entry.getLevel();
        int max = entry.getMaxLevel();

        //bought rungs in the player's own colour - yellow is the shopping list's, not the ladder's
        ShopUi.drawPips(x, top - PIP_SIZE, PIP_SIZE, PIP_GAP, level, max,
                Misc.getBasePlayerColor(), alphaMult);

        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        String text = entry.isMaxed() ? "LEVEL " + level + " / " + max + "  -  MAXED"
                : "LEVEL " + level + " / " + max;

        if (sub == null) {
            sub = ShopUi.createText(font, text);
            sub.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        Color color = entry.isMaxed() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor();

        sub.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        sub.draw(Math.round(x + ShopUi.getPipRowWidth(max, PIP_SIZE, PIP_GAP) + 10f), Math.round(top + 1f));
    }

    /** A curio has no slot and no ladder - only which way its switch is thrown. */
    protected void renderSwitch(float x, float top, float alphaMult) {
        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        String text = entry.group.title.toUpperCase() + "  -  " + (entry.isOn() ? "ON" : "OFF");

        if (sub == null) {
            sub = ShopUi.createText(font, text);
            sub.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        Color color = entry.isOn() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor();

        sub.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        sub.draw(Math.round(x), Math.round(top));
    }

    /** What kind of slot a module goes in, and whether it is in it. */
    protected void renderSlot(float x, float top, float alphaMult) {
        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        String text = entry.group.title.toUpperCase() + "  -  ONE SLOT"
                + (entry.isFitted() ? "  -  FITTED" : "");

        if (sub == null) {
            sub = ShopUi.createText(font, text);
            sub.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        Color color = entry.isFitted() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor();

        sub.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        sub.draw(Math.round(x), Math.round(top));
    }
}
