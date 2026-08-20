package catchrelease.campaign.fish.shop;

import catchrelease.ui.ShopUi;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.util.EnumMap;
import java.util.Map;

/**
 * The band across the top: shop name on the left, purse on the right. The purse is one chip per
 * rarity - specimen mark plus count - since every price below is quoted in exactly those terms.
 * Counts are read off the dialog's cache, so a purchase shows up the same frame it is paid.
 */
public class ShopHeaderPlugin extends BaseCustomUIPanelPlugin {

    /** Where the counts come from - the dialog owns the cache, this only draws it. */
    public interface Purse {
        Map<FishRarity, Integer> getWallet();

        int getCredits();
    }

    public static final String TITLE = "THE OUTFITTER";

    public static final float CHIP_WIDTH = 64f;
    public static final float CREDITS_CHIP_WIDTH = 110f;
    public static final float CHIP_HEIGHT = 30f;
    public static final float CHIP_GAP = 8f;
    public static final float ICON_SIZE = 20f;

    protected final Purse purse;

    protected PositionAPI pos;

    protected transient LazyFont.DrawableString title;
    protected final Map<FishRarity, LazyFont.DrawableString> counts = new EnumMap<>(FishRarity.class);
    protected final Map<FishRarity, Integer> drawnCounts = new EnumMap<>(FishRarity.class);

    protected transient LazyFont.DrawableString creditsText;
    protected int drawnCredits = -1;

    public ShopHeaderPlugin(Purse purse) {
        this.purse = purse;
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
        float width = pos.getWidth();
        float height = pos.getHeight();

        renderTitle(x, y, height, alphaMult);
        renderPurse(x, y, width, height, alphaMult);

        //the rule the whole band sits on, in the pane headers' quiet hand
        ShopUi.drawQuad(x, y, width, 1f, Misc.getDarkPlayerColor(), 0.8f * alphaMult);
    }

    /** The name, back in the large hand - a small-caps title read as a section label, not a shop sign. */
    protected void renderTitle(float x, float y, float height, float alphaMult) {
        LazyFont font = ShopUi.getTitleFont();
        if (font == null) return;

        if (title == null) {
            title = ShopUi.createText(font, TITLE);
            title.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        }

        title.setBaseColor(ShopUi.withAlpha(Misc.getBrightPlayerColor(), alphaMult));
        title.draw(Math.round(x + 2f), Math.round(y + height * 0.5f + title.getHeight() * 0.5f));
    }

    protected void renderPurse(float x, float y, float width, float height, float alphaMult) {
        Map<FishRarity, Integer> wallet = purse.getWallet();
        if (wallet == null) return;

        FishRarity[] ladder = FishRarity.values();
        float chipX = x + width - ladder.length * CHIP_WIDTH - ladder.length * CHIP_GAP
                - CREDITS_CHIP_WIDTH;
        float chipY = y + (height - CHIP_HEIGHT) * 0.5f;

        renderCredits(chipX, chipY, alphaMult);
        chipX += CREDITS_CHIP_WIDTH + CHIP_GAP;

        for (FishRarity rarity : ladder) {
            renderChip(rarity, wallet.get(rarity) == null ? 0 : wallet.get(rarity),
                    chipX, chipY, alphaMult);

            chipX += CHIP_WIDTH + CHIP_GAP;
        }
    }

    /** The other half of every price, so the purse tells the whole of it. */
    protected void renderCredits(float x, float y, float alphaMult) {
        //the chip's grammar: dark field, the identity colour underlining
        ShopUi.drawQuad(x, y, CREDITS_CHIP_WIDTH, CHIP_HEIGHT, Misc.getDarkPlayerColor(),
                0.18f * alphaMult);
        ShopUi.drawQuad(x, y, CREDITS_CHIP_WIDTH, 2f, Misc.getHighlightColor(),
                0.6f * alphaMult);

        LazyFont font = ShopUi.getBodyFont();
        if (font == null) return;

        int credits = purse.getCredits();
        if (creditsText == null || credits != drawnCredits) {
            creditsText = ShopUi.createText(font, Misc.getDGSCredits(credits));
            creditsText.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
            drawnCredits = credits;
        }

        creditsText.setBaseColor(ShopUi.withAlpha(Misc.getHighlightColor(), alphaMult));
        creditsText.draw(Math.round(x + CREDITS_CHIP_WIDTH - 7f),
                Math.round(y + CHIP_HEIGHT * 0.5f + creditsText.getHeight() * 0.5f));
    }

    protected void renderChip(FishRarity rarity, int count, float x, float y, float alphaMult) {
        //empty pockets go quiet rather than away, so the ladder stays readable
        float presence = count > 0 ? 1f : 0.4f;

        //the chip's grammar: dark field, the identity colour underlining
        ShopUi.drawQuad(x, y, CHIP_WIDTH, CHIP_HEIGHT, Misc.getDarkPlayerColor(),
                0.18f * alphaMult);
        ShopUi.drawQuad(x, y, CHIP_WIDTH, 2f, rarity.color, 0.6f * presence * alphaMult);

        SpriteAPI icon = SpriteLoader.getSprite("pane_fish");
        if (icon != null) {
            icon.setSize(ICON_SIZE, ICON_SIZE);
            icon.setColor(rarity.color);
            icon.setNormalBlend();
            icon.setAlphaMult(presence * alphaMult);
            icon.renderAtCenter(x + 6f + ICON_SIZE * 0.5f, y + CHIP_HEIGHT * 0.5f);
        }

        LazyFont.DrawableString text = getCount(rarity, count);
        if (text == null) return;

        text.setBaseColor(ShopUi.withAlpha(rarity.color, presence * alphaMult));
        text.draw(Math.round(x + CHIP_WIDTH - 7f), Math.round(y + CHIP_HEIGHT * 0.5f + text.getHeight() * 0.5f));
    }

    /** Rebuilt only when the number it shows stops being true. */
    protected LazyFont.DrawableString getCount(FishRarity rarity, int count) {
        LazyFont font = ShopUi.getBodyFont();
        if (font == null) return null;

        Integer drawn = drawnCounts.get(rarity);
        if (drawn == null || drawn != count) {
            LazyFont.DrawableString text = ShopUi.createText(font, String.valueOf(count));
            text.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);

            counts.put(rarity, text);
            drawnCounts.put(rarity, count);
        }

        return counts.get(rarity);
    }
}
