package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.treasure.TreasureAward;
import catchrelease.campaign.fish.treasure.TreasureRarity;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * What else came up, on a card of its own: the catch readout's mirror, hung off the other side of
 * the track, up only once the game is over the way the readout is. The treasure was a thing on
 * the track while the catch ran; this is the receipt afterwards, and mixing the two - a tally
 * arriving while there was still a fish to fly - would put reading where playing goes.
 * <p>
 * Same anatomy as the readout on purpose: the square at the top at the same height, the title
 * under it, the list under that, arriving a line at a time to the same sound. Each line is a
 * thing that went into the hold, wearing the cargo icon the hold will show it under, in its
 * tier's colour - so the card reads as cargo, not as prose.
 */
public class LootResultPanel {

    /** One thing that was handed over: its icon, its name in its tier's colour, and how many. */
    protected static class Row {
        final String name;
        final String spriteName;
        final int count;
        final Color color;

        transient LazyFont.DrawableString nameText;
        transient LazyFont.DrawableString countText;
        transient SpriteAPI sprite;
        transient boolean spriteChecked;

        Row(String name, String spriteName, int count, Color color) {
            this.name = name;
            this.spriteName = spriteName;
            this.count = count;
            this.color = color;
        }
    }

    protected final List<TreasureAward> awards;
    protected final List<Row> rows = new ArrayList<>();

    /** Whether this card is the only one up - a lost fish, loot in hand - and so owns the prompt. */
    protected final boolean standalone;

    protected float elapsed = 0f;
    protected int shown = 0;
    protected boolean skipped = false;

    protected final List<CatchResultPanel.Bubble> bubbles = new ArrayList<>();

    transient protected LazyFont font;
    transient protected LazyFont titleFont;
    transient protected LazyFont.DrawableString title;
    transient protected LazyFont.DrawableString prompt;
    transient protected boolean fontsChecked = false;

    transient protected SpriteAPI boxSprite;
    transient protected boolean boxSpriteChecked = false;

    public LootResultPanel(List<TreasureAward> awards, boolean standalone) {
        this.awards = awards == null ? new ArrayList<>() : awards;
        this.standalone = standalone;

        for (TreasureAward award : this.awards) {
            for (TreasureAward.Item item : award.items) {
                rows.add(new Row(item.name, item.sprite, item.count, award.rarity.color));
            }
        }
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public void advance(float amount) {
        elapsed += amount;

        while (shown < rows.size()
                && elapsed >= (shown + 1) * FishConstants.MINIGAME_RESULT_LINE_DELAY) {

            shown++;
            CatchCelebration.playHook(FishConstants.SOUND_RESULT_LINE);
        }
    }

    /** Everything at once, for a player who would rather not be read to. */
    public void revealAll() {
        shown = rows.size();
        skipped = true;
    }

    /** True once there is nothing left to arrive. */
    public boolean isComplete() {
        return shown >= rows.size();
    }

    public void render(FishingMinigameLayout layout, float alphaMult) {
        if (rows.isEmpty() || alphaMult <= 0f) return;

        loadFonts();
        layout.fitLootContent(getContentWidth());
        layout.centerLootContent(getContentHeight());

        renderPanel(layout, alphaMult);
        renderBox(layout, alphaMult);

        float y = layout.lootBoxY - FishConstants.MINIGAME_RESULT_BOX_GAP;

        y = renderTitle(layout, y, alphaMult);
        y = renderRows(layout, y, alphaMult);

        renderPrompt(layout, y, alphaMult);
    }

    /** The widest row, measured up front so the card never grows while it is being read out. */
    protected float getContentWidth() {
        float widest = 0f;

        if (title != null) widest = Math.max(widest, title.getWidth());
        if (prompt != null) widest = Math.max(widest, prompt.getWidth());

        if (font == null) return widest;

        for (Row row : rows) {
            build(row);

            float width = FishConstants.MINIGAME_LOOT_ICON + FishConstants.MINIGAME_LOOT_ICON_GAP
                    + row.nameText.getWidth();

            if (row.countText != null) {
                width += FishConstants.MINIGAME_LOOT_COUNT_GAP + row.countText.getWidth();
            }

            widest = Math.max(widest, width);
        }

        return widest;
    }

    /** Box top to prompt bottom, counted off the full list - the mirror of the readout's measure. */
    protected float getContentHeight() {
        float height = FishConstants.MINIGAME_RESULT_BOX;

        if (title != null) {
            height += FishConstants.MINIGAME_RESULT_BOX_GAP + title.getHeight()
                    + FishConstants.MINIGAME_RESULT_TITLE_GAP;
        }

        height += rows.size() * FishConstants.MINIGAME_LOOT_LINE_HEIGHT;

        if (standalone && prompt != null) {
            height += FishConstants.MINIGAME_RESULT_TITLE_GAP + prompt.getHeight();
        }

        return height;
    }

    /** The same field, bubbles and dressing the readout's panel wears, on its own rectangle. */
    protected void renderPanel(FishingMinigameLayout layout, float alphaMult) {
        CatchResultPanel.drawQuad(layout.lootPanelX, layout.lootPanelY, layout.lootPanelWidth,
                layout.lootPanelHeight, Color.BLACK, 0.85f * alphaMult);

        CatchResultPanel.drawQuad(layout.lootPanelX, layout.lootPanelY, layout.lootPanelWidth,
                layout.lootPanelHeight, Misc.getDarkPlayerColor(), 0.07f * alphaMult);

        renderBubbles(layout, alphaMult);

        CatchResultPanel.dress(layout.lootPanelX, layout.lootPanelY, layout.lootPanelWidth,
                layout.lootPanelHeight, alphaMult);
    }

    protected void renderBubbles(FishingMinigameLayout layout, float alphaMult) {
        if (bubbles.isEmpty()) spawnBubbles();

        for (CatchResultPanel.Bubble b : bubbles) {
            float risen = (b.startY + b.speed * elapsed) % layout.lootPanelHeight;

            float x = layout.lootPanelX + b.fx * layout.lootPanelWidth
                    + (float) Math.sin(elapsed * FishConstants.MINIGAME_RESULT_BUBBLE_DRIFT_RATE + b.phase)
                            * FishConstants.MINIGAME_RESULT_BUBBLE_DRIFT;

            Disc.drawOutline(x, layout.lootPanelY + risen, b.radius, Misc.getBrightPlayerColor(),
                    FishConstants.MINIGAME_RESULT_BUBBLE_ALPHA * alphaMult, 1f);
        }
    }

    protected void spawnBubbles() {
        for (int i = 0; i < FishConstants.MINIGAME_RESULT_BUBBLES; i++) {
            CatchResultPanel.Bubble b = new CatchResultPanel.Bubble();

            b.fx = MathUtils.getRandomNumberInRange(0.1f, 0.9f);
            b.startY = MathUtils.getRandomNumberInRange(0f, FishConstants.MINIGAME_PANEL_HEIGHT);
            b.speed = MathUtils.getRandomNumberInRange(FishConstants.MINIGAME_RESULT_BUBBLE_SPEED_MIN,
                    FishConstants.MINIGAME_RESULT_BUBBLE_SPEED_MAX);
            b.radius = MathUtils.getRandomNumberInRange(FishConstants.MINIGAME_RESULT_BUBBLE_SIZE_MIN,
                    FishConstants.MINIGAME_RESULT_BUBBLE_SIZE_MAX);
            b.phase = MathUtils.getRandomNumberInRange(0f, (float) (Math.PI * 2.0));

            bubbles.add(b);
        }
    }

    /**
     * The square at the top, at the readout's height and size: the salvage marker washed in the
     * best tier's colour - the square is what says "cargo", and the wash is what says how good.
     */
    protected void renderBox(FishingMinigameLayout layout, float alphaMult) {
        Color accent = getBestRarity().color;

        float x = layout.lootBoxX;
        float y = layout.lootBoxY;
        float size = layout.boxSize;

        CatchResultPanel.drawQuad(x, y, size, size, Color.BLACK, 0.75f * alphaMult);

        Disc.draw(x + size * 0.5f, y + size * 0.5f, size * 0.5f, accent, 0.3f * alphaMult, 0f, true);

        SpriteAPI sprite = getBoxSprite();
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

    protected TreasureRarity getBestRarity() {
        TreasureRarity best = TreasureRarity.COMMON;

        for (TreasureAward award : awards) {
            if (award.rarity.ordinal() > best.ordinal()) best = award.rarity;
        }

        return best;
    }

    /** @return the y the next thing down should start at */
    protected float renderTitle(FishingMinigameLayout layout, float y, float alphaMult) {
        if (title == null) return y;

        title.setBaseColor(CatchResultPanel.withAlpha(Misc.getBrightPlayerColor(), alphaMult));
        title.draw(layout.lootX + layout.lootWidth * 0.5f, y);

        return y - title.getHeight() - FishConstants.MINIGAME_RESULT_TITLE_GAP;
    }

    /**
     * The things themselves, each fading in as it lands: the cargo icon, the name in the tier's
     * colour, and the count out in the right column where the readout keeps its numbers.
     */
    protected float renderRows(FishingMinigameLayout layout, float y, float alphaMult) {
        if (font == null) return y;

        float rowHeight = FishConstants.MINIGAME_LOOT_LINE_HEIGHT;
        float iconSize = FishConstants.MINIGAME_LOOT_ICON;
        float right = layout.lootX + layout.lootWidth;

        for (int i = 0; i < rows.size(); i++) {
            if (i >= shown) break;

            Row row = rows.get(i);
            build(row);

            float age = elapsed - (i + 1) * FishConstants.MINIGAME_RESULT_LINE_DELAY;
            float alpha = skipped
                    ? alphaMult
                    : alphaMult * MathUtils.clamp(age / FishConstants.MINIGAME_RESULT_FADE, 0f, 1f);

            float centerY = y - rowHeight * 0.5f;

            SpriteAPI sprite = getRowSprite(row);
            if (sprite != null) {
                float scale = Math.min(iconSize / sprite.getWidth(), iconSize / sprite.getHeight());

                sprite.setSize(sprite.getWidth() * scale, sprite.getHeight() * scale);
                sprite.setNormalBlend();
                sprite.setAlphaMult(alpha);
                sprite.renderAtCenter(Math.round(layout.lootX + iconSize * 0.5f), Math.round(centerY));
            }

            row.nameText.setBaseColor(CatchResultPanel.withAlpha(row.color, alpha));
            row.nameText.draw(Math.round(layout.lootX + iconSize + FishConstants.MINIGAME_LOOT_ICON_GAP),
                    Math.round(centerY + row.nameText.getHeight() * 0.5f));

            if (row.countText != null) {
                row.countText.setBaseColor(CatchResultPanel.withAlpha(Misc.getGrayColor(), alpha));
                row.countText.draw(Math.round(right),
                        Math.round(centerY + row.countText.getHeight() * 0.5f));
            }

            y -= rowHeight;
        }

        return y;
    }

    /** Only when this card is alone on the glass - beside the readout, the readout does the asking. */
    protected void renderPrompt(FishingMinigameLayout layout, float y, float alphaMult) {
        if (!standalone || !isComplete() || prompt == null) return;

        float lit = 0.5f - 0.5f * (float) Math.cos(
                elapsed * (Math.PI * 2.0) / FishConstants.MINIGAME_RESULT_PROMPT_PERIOD);

        prompt.setBaseColor(CatchResultPanel.withAlpha(
                CatchResultPanel.blend(FishConstants.MINIGAME_RESULT_PROMPT_DIM,
                        FishConstants.MINIGAME_RESULT_PROMPT_LIT, lit),
                alphaMult * FishConstants.MINIGAME_RESULT_PROMPT_ALPHA));
        prompt.draw(layout.lootX + layout.lootWidth * 0.5f, y - FishConstants.MINIGAME_RESULT_TITLE_GAP);
    }

    /** Built on first sight rather than up front, so a row that is never shown is never made. */
    protected void build(Row row) {
        if (row.nameText != null) return;

        row.nameText = font.createText(row.name, Color.WHITE, FishConstants.MINIGAME_RESULT_TEXT_SIZE);
        row.nameText.setAnchor(LazyFont.TextAnchor.TOP_LEFT);

        if (row.count > 1) {
            row.countText = font.createText("x" + row.count, Color.WHITE,
                    FishConstants.MINIGAME_RESULT_TEXT_SIZE);
            row.countText.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }
    }

    /** The row's cargo icon, loaded on first sight; the salvage marker when there is none to load. */
    protected SpriteAPI getRowSprite(Row row) {
        if (row.spriteChecked) return row.sprite;
        row.spriteChecked = true;

        row.sprite = loadSprite(row.spriteName);
        if (row.sprite == null) row.sprite = getBoxSprite();

        return row.sprite;
    }

    protected SpriteAPI getBoxSprite() {
        if (boxSpriteChecked) return boxSprite;
        boxSpriteChecked = true;

        boxSprite = SpriteLoader.loadSprite(FishConstants.TREASURE_ICON);

        return boxSprite;
    }

    /**
     * A sprite off a raw path. Vanilla has most of these loaded already; loadTexture makes the
     * ones it does not - modded weapon art, mostly - real before they are asked for.
     */
    protected SpriteAPI loadSprite(String path) {
        if (path == null || path.isEmpty()) return null;

        try {
            Global.getSettings().loadTexture(path);
            return Global.getSettings().getSprite(path);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Loaded once and kept. A missing font costs the text and nothing else. */
    protected void loadFonts() {
        if (fontsChecked) return;
        fontsChecked = true;

        try {
            font = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_FONT);
            titleFont = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_TITLE_FONT);

            title = titleFont.createText(FishConstants.MINIGAME_LOOT_TITLE, Color.WHITE,
                    FishConstants.MINIGAME_RESULT_TITLE_SIZE);
            title.setAnchor(LazyFont.TextAnchor.TOP_CENTER);

            prompt = font.createText("Press any key", Color.WHITE, FishConstants.MINIGAME_RESULT_TEXT_SIZE);
            prompt.setAnchor(LazyFont.TextAnchor.TOP_CENTER);
        } catch (Exception e) {
            Global.getLogger(LootResultPanel.class).warn("No font for the loot card", e);
        }
    }
}
