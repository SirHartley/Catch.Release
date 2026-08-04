package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.treasure.TreasureRarity;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;

/**
 * What was prised out of the track, on the other side of the catch from the readout: the chest in a
 * cargo-square, a title, and the tier - the tier being all there is to say, since the item itself is
 * only rolled once the fish is landed.
 * <p>
 * That condition is the card's last line. It starts as a warning and ends as a verdict: treasure
 * goes down with a lost fish, and a chest that vanished without a word would leave the player
 * banking on cargo that never arrived. This card is the one place they are told.
 * <p>
 * Deliberately the readout's twin - same width, same square, same fonts, same dressing, borrowed
 * from {@link CatchResultPanel} directly - so the two read as the two panes of one interface.
 */
public class TreasureFoundPanel {

    protected final TreasureRarity rarity;

    protected float elapsed = 0f;

    /** How the catch ended, once it has. The last line is drawn off these; neither set is pending. */
    protected boolean secured = false;
    protected boolean lost = false;

    transient protected LazyFont font;
    transient protected LazyFont titleFont;
    transient protected LazyFont.DrawableString title;
    transient protected LazyFont.DrawableString typeLabel;
    transient protected LazyFont.DrawableString typeValue;
    transient protected LazyFont.DrawableString pendingText;
    transient protected LazyFont.DrawableString securedText;
    transient protected LazyFont.DrawableString lostText;
    transient protected boolean fontsChecked = false;

    public TreasureFoundPanel(TreasureRarity rarity) {
        this.rarity = rarity;
    }

    public void advance(float amount) {
        elapsed += amount;
    }

    /** The fish landed, and the roll in the hold is where the chest's contents are said. */
    public void setSecured() {
        secured = true;
        lost = false;
    }

    /** The fish got away, and took the chest with it. */
    public void setLost() {
        lost = true;
        secured = false;
    }

    public boolean isLost() {
        return lost;
    }

    public void render(FishingMinigameLayout layout, float alphaMult) {
        //the card arrives rather than switching on - it appears mid-catch, while the eye is on
        //the track, and a hard pop off to the side reads as a glitch
        alphaMult *= MathUtils.clamp(elapsed / FishConstants.TREASURE_CARD_FADE, 0f, 1f);
        if (alphaMult <= 0f) return;

        //before anything is placed, since the fonts are what the column is measured with
        loadFonts();
        layout.fitTreasureContent(getContentWidth());
        layout.centerTreasureContent(getContentHeight());

        renderPanel(layout, alphaMult);
        renderBox(layout, alphaMult);

        float y = layout.treasureBoxY - FishConstants.MINIGAME_RESULT_BOX_GAP;

        y = renderTitle(layout, y, alphaMult);
        y = renderType(layout, y, alphaMult);

        renderFate(layout, y, alphaMult);
    }

    /**
     * The widest thing that has to fit across the column - measured off every string the card can
     * ever show, not the ones showing now, so the end of the catch changes a line and not the
     * card's shape.
     */
    protected float getContentWidth() {
        float widest = 0f;

        if (title != null) widest = Math.max(widest, title.getWidth());

        if (typeLabel != null && typeValue != null) {
            widest = Math.max(widest, typeLabel.getWidth()
                    + FishConstants.MINIGAME_RESULT_COLUMN_GAP + typeValue.getWidth());
        }

        if (pendingText != null) widest = Math.max(widest, pendingText.getWidth());
        if (securedText != null) widest = Math.max(widest, securedText.getWidth());
        if (lostText != null) widest = Math.max(widest, lostText.getWidth());

        return widest;
    }

    /** Box top to last line bottom, as the render methods will space it. Fixed for the card's life. */
    protected float getContentHeight() {
        float height = FishConstants.MINIGAME_RESULT_BOX;

        if (title != null) {
            height += FishConstants.MINIGAME_RESULT_BOX_GAP + title.getHeight()
                    + FishConstants.MINIGAME_RESULT_TITLE_GAP;
        }

        //the tier row and the fate row, at the readout's line rhythm
        if (font != null) {
            height += 2f * FishConstants.MINIGAME_RESULT_LINE_HEIGHT;
        }

        return height;
    }

    /** The readout's field and dressing, without its bubbles - this card is a notice, not a tally. */
    protected void renderPanel(FishingMinigameLayout layout, float alphaMult) {
        CatchResultPanel.drawQuad(layout.treasurePanelX, layout.treasurePanelY,
                layout.treasurePanelWidth, layout.treasurePanelHeight, Color.BLACK, 0.85f * alphaMult);

        CatchResultPanel.drawQuad(layout.treasurePanelX, layout.treasurePanelY,
                layout.treasurePanelWidth, layout.treasurePanelHeight,
                Misc.getDarkPlayerColor(), 0.07f * alphaMult);

        CatchResultPanel.dress(layout.treasurePanelX, layout.treasurePanelY,
                layout.treasurePanelWidth, layout.treasurePanelHeight, alphaMult);
    }

    /**
     * The cargo-square, with the chest in it and the tier's colour behind it - the same square the
     * readout puts its specimen in, because the hold is where this is going too.
     */
    protected void renderBox(FishingMinigameLayout layout, float alphaMult) {
        float x = layout.treasureBoxX;
        float y = layout.treasureBoxY;
        float size = layout.treasureBoxSize;

        CatchResultPanel.drawQuad(x, y, size, size, Color.BLACK, 0.75f * alphaMult);

        Disc.draw(x + size * 0.5f, y + size * 0.5f, size * 0.5f, rarity.color,
                0.3f * alphaMult, 0f, true);

        SpriteAPI sprite = SpriteLoader.loadSprite(FishConstants.TREASURE_ICON);

        if (sprite != null) {
            float available = size - FishConstants.MINIGAME_RESULT_BOX_PAD * 2f;
            float scale = Math.min(available / sprite.getWidth(), available / sprite.getHeight());

            sprite.setSize(sprite.getWidth() * scale, sprite.getHeight() * scale);
            sprite.setNormalBlend();
            sprite.setAlphaMult(alphaMult);
            sprite.renderAtCenter(x + size * 0.5f, y + size * 0.5f);
        }

        CatchResultPanel.dress(x, y, size, size, alphaMult);
    }

    /** @return the y the next thing down should start at */
    protected float renderTitle(FishingMinigameLayout layout, float y, float alphaMult) {
        if (title == null) return y;

        title.setBaseColor(CatchResultPanel.withAlpha(Misc.getBrightPlayerColor(), alphaMult));
        title.draw(layout.treasureContentX + layout.treasureContentWidth * 0.5f, y);

        return y - title.getHeight() - FishConstants.MINIGAME_RESULT_TITLE_GAP;
    }

    /** The tier, as the readout writes a row: label to the left edge, value to the right. */
    protected float renderType(FishingMinigameLayout layout, float y, float alphaMult) {
        if (typeLabel == null || typeValue == null) return y;

        typeLabel.setBaseColor(CatchResultPanel.withAlpha(Misc.getGrayColor(), alphaMult));
        typeLabel.draw(layout.treasureContentX, y);

        typeValue.setBaseColor(CatchResultPanel.withAlpha(rarity.color, alphaMult));
        typeValue.draw(layout.treasureContentX + layout.treasureContentWidth, y);

        return y - FishConstants.MINIGAME_RESULT_LINE_HEIGHT;
    }

    /**
     * The condition the chest is held on, centred like a notice rather than written as a row - it
     * is a sentence about the whole card, not one of its numbers. Grey while the fish is still on,
     * and the outcome's colour once there is one.
     */
    protected void renderFate(FishingMinigameLayout layout, float y, float alphaMult) {
        LazyFont.DrawableString fate = lost ? lostText : secured ? securedText : pendingText;
        if (fate == null) return;

        Color color = lost ? Misc.getNegativeHighlightColor()
                : secured ? Misc.getPositiveHighlightColor()
                : Misc.getGrayColor();

        fate.setBaseColor(CatchResultPanel.withAlpha(color, alphaMult));
        fate.draw(layout.treasureContentX + layout.treasureContentWidth * 0.5f, y);
    }

    /** Loaded once and kept. A missing font costs the text and nothing else. */
    protected void loadFonts() {
        if (fontsChecked) return;
        fontsChecked = true;

        try {
            font = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_FONT);
            titleFont = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_TITLE_FONT);

            title = titleFont.createText(FishConstants.TREASURE_CARD_TITLE, Color.WHITE,
                    FishConstants.MINIGAME_RESULT_TITLE_SIZE);
            title.setAnchor(LazyFont.TextAnchor.TOP_CENTER);
            title.setAlignment(LazyFont.TextAlignment.CENTER);

            typeLabel = font.createText(FishConstants.TREASURE_CARD_TYPE_LABEL, Color.WHITE,
                    FishConstants.MINIGAME_RESULT_TEXT_SIZE);
            typeLabel.setAnchor(LazyFont.TextAnchor.TOP_LEFT);

            typeValue = font.createText(rarity.name, Color.WHITE,
                    FishConstants.MINIGAME_RESULT_TEXT_SIZE);
            typeValue.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);

            pendingText = createFate(FishConstants.TREASURE_CARD_PENDING);
            securedText = createFate(FishConstants.TREASURE_CARD_SECURED);
            lostText = createFate(FishConstants.TREASURE_CARD_LOST);
        } catch (Exception e) {
            Global.getLogger(TreasureFoundPanel.class).warn("No font for the treasure card", e);
        }
    }

    /** All three fates are built up front so the card can be measured against whichever is widest. */
    protected LazyFont.DrawableString createFate(String text) {
        LazyFont.DrawableString fate = font.createText(text, Color.WHITE,
                FishConstants.MINIGAME_RESULT_TEXT_SIZE);
        fate.setAnchor(LazyFont.TextAnchor.TOP_CENTER);

        return fate;
    }
}
