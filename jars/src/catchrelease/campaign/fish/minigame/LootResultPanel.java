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
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class LootResultPanel {
    protected static final int COIN_SEGMENTS = 16;

    protected final List<TreasureAward> awards;
    protected final List<Row> rows = new ArrayList<>();
    protected float elapsed = 0f;
    protected float backdrop = 0f;
    protected int shown = 0;
    protected boolean skipped = false;
    protected boolean chestSoundPlayed = false;
    protected final List<Coin> coins = new ArrayList<>();
    transient protected LazyFont font;
    transient protected LazyFont titleFont;
    transient protected LazyFont.DrawableString title;
    transient protected boolean fontsChecked = false;

    protected static class Row {
        final String name;
        final String spriteName;
        final int count;
        final Color color;
        transient LazyFont.DrawableString nameText;
        transient LazyFont.DrawableString countText;

        Row(String name, String spriteName, int count, Color color) {
            this.name = name;
            this.spriteName = spriteName;
            this.count = count;
            this.color = color;
        }
    }

    protected static class Coin {
        float fx;
        float startY; // how far into the fall it began, in pixels, so they never start in a row
        float speed;
        float size;
        float flipRate; // radians per second of the main flip
        float phase;
        float wobbleRate; // radians per second of the second, slower flip across the other axis
        float wobblePhase;
        float spinRate; // radians per second the whole ellipse turns in the plane, signed
        float spinPhase;
    }

    public LootResultPanel(List<TreasureAward> awards) {
        this.awards = awards == null ? new ArrayList<>() : awards;

        for (TreasureAward award : this.awards) {
            for (TreasureAward.Item item : award.items) {
                rows.add(new Row(item.name, item.sprite, item.count, award.rarity.color));
            }
        }
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public void advanceBackdrop(float amount) {
        backdrop += amount;
    }

    public void advance(float amount) {
        elapsed += amount;

        while (shown < rows.size()
                && elapsed >= (shown + 1) * FishConstants.MINIGAME_RESULT_LINE_DELAY) {
            shown++;
            if (shown == 1) playChestOpenSound();
            CatchCelebration.playHook(FishConstants.SOUND_RESULT_LINE);
        }
    }

    public void revealAll() {
        if (shown == 0 && !rows.isEmpty()) playChestOpenSound();
        shown = rows.size();
        skipped = true;
    }

    protected void playChestOpenSound() {
        if (chestSoundPlayed) return;

        chestSoundPlayed = true;
        CatchCelebration.playHook(FishConstants.SOUND_TREASURE_OPEN);
    }

    public boolean isComplete() {
        return shown >= rows.size();
    }

    public void render(FishingMinigameLayout layout, float alphaMult) {
        if (rows.isEmpty() || alphaMult <= 0f) return;

        loadFonts();
        layout.fitLootContent(getContentWidth());
        layout.alignLootContent(getContentHeight());

        renderPanel(layout, alphaMult);
        renderBox(layout, alphaMult);

        float y = layout.lootBoxY - FishConstants.MINIGAME_RESULT_BOX_GAP;

        y = renderTitle(layout, y, alphaMult);
        renderRows(layout, y, alphaMult);
    }

    protected float getContentWidth() {
        float widest = 0f;

        if (title != null) widest = Math.max(widest, title.getWidth());

        if (font == null) return widest;

        for (Row row : rows) {
            build(row);

            float width = getRowWidth(row);

            if (row.countText != null) {
                width += FishConstants.MINIGAME_LOOT_COUNT_GAP + row.countText.getWidth();
            }

            widest = Math.max(widest, width);
        }

        return widest;
    }

    protected float getContentHeight() {
        float height = FishConstants.MINIGAME_RESULT_BOX;

        if (title != null) {
            height += FishConstants.MINIGAME_RESULT_BOX_GAP + title.getHeight()
                    + FishConstants.MINIGAME_RESULT_TITLE_GAP;
        }

        for (Row row : rows) {
            build(row);
            height += getRowHeight(row);
        }

        return height;
    }

    protected void renderPanel(FishingMinigameLayout layout, float alphaMult) {
        CatchResultPanel.drawQuad(layout.lootPanelX, layout.lootPanelY, layout.lootPanelWidth,
                layout.lootPanelHeight, Color.BLACK, 0.85f * alphaMult);

        CatchResultPanel.drawQuad(layout.lootPanelX, layout.lootPanelY, layout.lootPanelWidth,
                layout.lootPanelHeight, Misc.getDarkPlayerColor(), 0.07f * alphaMult);

        renderCoins(layout, alphaMult);

        CatchResultPanel.dress(layout.lootPanelX, layout.lootPanelY, layout.lootPanelWidth,
                layout.lootPanelHeight, alphaMult);
    }

    protected void renderCoins(FishingMinigameLayout layout, float alphaMult) {
        if (coins.isEmpty()) spawnCoins();

        Color gold = FishConstants.MINIGAME_LOOT_COIN_COLOR;
        float r = gold.getRed() / 255f;
        float g = gold.getGreen() / 255f;
        float b = gold.getBlue() / 255f;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (Coin c : coins) {
            float fallen = (c.startY + c.speed * backdrop) % layout.lootPanelHeight;

            float x = layout.lootPanelX + c.fx * layout.lootPanelWidth;
            float y = layout.lootPanelY + layout.lootPanelHeight - fallen;

            // 1 face-on, 0 edge-on; the floor keeps a sliver of rim rather than a blink
            float face = Math.abs((float) Math.cos(backdrop * c.flipRate + c.phase));
            float width = c.size * Math.max(face, FishConstants.MINIGAME_LOOT_COIN_EDGE);

            float wobble = Math.abs((float) Math.cos(backdrop * c.wobbleRate + c.wobblePhase));
            float height = c.size * (1f - FishConstants.MINIGAME_LOOT_COIN_WOBBLE_DEPTH
                    * (1f - wobble));

            // the tilt of the whole ellipse this frame
            float tilt = c.spinPhase + backdrop * c.spinRate;
            float cosT = (float) Math.cos(tilt);
            float sinT = (float) Math.sin(tilt);

            float alpha = FishConstants.MINIGAME_LOOT_COIN_ALPHA * alphaMult
                    * (1f + FishConstants.MINIGAME_LOOT_COIN_EDGE_SHINE * (1f - face) * (1f - face));

            GL11.glColor4f(r, g, b, alpha);

            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(x, y);
            for (int i = 0; i <= COIN_SEGMENTS; i++) {
                double angle = Math.toRadians(i * 360.0 / COIN_SEGMENTS);

                float ex = (float) Math.cos(angle) * width;
                float ey = (float) Math.sin(angle) * height;

                GL11.glVertex2f(x + ex * cosT - ey * sinT, y + ex * sinT + ey * cosT);
            }
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }

    protected void spawnCoins() {
        for (int i = 0; i < FishConstants.MINIGAME_LOOT_COINS; i++) {
            Coin c = new Coin();

            c.fx = MathUtils.getRandomNumberInRange(0.1f, 0.9f);
            c.startY = MathUtils.getRandomNumberInRange(0f, FishConstants.MINIGAME_PANEL_HEIGHT);
            c.speed = MathUtils.getRandomNumberInRange(FishConstants.MINIGAME_LOOT_COIN_SPEED_MIN,
                    FishConstants.MINIGAME_LOOT_COIN_SPEED_MAX);
            c.size = MathUtils.getRandomNumberInRange(FishConstants.MINIGAME_LOOT_COIN_SIZE_MIN,
                    FishConstants.MINIGAME_LOOT_COIN_SIZE_MAX);
            c.flipRate = MathUtils.getRandomNumberInRange(FishConstants.MINIGAME_LOOT_COIN_FLIP_RATE_MIN,
                    FishConstants.MINIGAME_LOOT_COIN_FLIP_RATE_MAX);
            c.phase = MathUtils.getRandomNumberInRange(0f, (float) (Math.PI * 2.0));

            c.wobbleRate = MathUtils.getRandomNumberInRange(
                    FishConstants.MINIGAME_LOOT_COIN_WOBBLE_RATE_MIN,
                    FishConstants.MINIGAME_LOOT_COIN_WOBBLE_RATE_MAX);
            c.wobblePhase = MathUtils.getRandomNumberInRange(0f, (float) (Math.PI * 2.0));

            c.spinRate = MathUtils.getRandomNumberInRange(
                    FishConstants.MINIGAME_LOOT_COIN_SPIN_RATE_MIN,
                    FishConstants.MINIGAME_LOOT_COIN_SPIN_RATE_MAX)
                    * (MathUtils.getRandomNumberInRange(0f, 1f) < 0.5f ? -1f : 1f);
            c.spinPhase = MathUtils.getRandomNumberInRange(0f, (float) (Math.PI * 2.0));

            coins.add(c);
        }
    }

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
            if (award.rarity.rank > best.rank) best = award.rarity;
        }

        return best;
    }

    protected float renderTitle(FishingMinigameLayout layout, float y, float alphaMult) {
        if (title == null) return y;

        title.setBaseColor(CatchResultPanel.withAlpha(Misc.getBrightPlayerColor(), alphaMult));
        title.draw(layout.lootX + layout.lootWidth * 0.5f, y);

        return y - title.getHeight() - FishConstants.MINIGAME_RESULT_TITLE_GAP;
    }

    protected float renderRows(FishingMinigameLayout layout, float y, float alphaMult) {
        if (font == null) return y;

        float iconSize = FishConstants.MINIGAME_LOOT_ICON;
        float right = layout.lootX + layout.lootWidth;

        for (int i = 0; i < rows.size(); i++) {
            if (i >= shown) break;

            Row row = rows.get(i);
            build(row);
            float rowHeight = getRowHeight(row);

            float age = elapsed - (i + 1) * FishConstants.MINIGAME_RESULT_LINE_DELAY;
            float alpha = skipped
                    ? alphaMult
                    : alphaMult * MathUtils.clamp(age / FishConstants.MINIGAME_RESULT_FADE, 0f, 1f);

            float centerY = y - rowHeight * 0.5f;

            float left = row.countText != null ? layout.lootX
                    : layout.lootX + (layout.lootWidth - getRowWidth(row)) * 0.5f;

            SpriteAPI sprite = getRowSprite(row);
            if (sprite != null) {
                float scale = Math.min(iconSize / sprite.getWidth(), iconSize / sprite.getHeight());

                sprite.setSize(sprite.getWidth() * scale, sprite.getHeight() * scale);
                sprite.setNormalBlend();
                sprite.setAlphaMult(alpha);
                sprite.renderAtCenter(Math.round(left + iconSize * 0.5f), Math.round(centerY));
            }

            row.nameText.setBaseColor(CatchResultPanel.withAlpha(row.color, alpha));
            row.nameText.draw(Math.round(left + iconSize + FishConstants.MINIGAME_LOOT_ICON_GAP),
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

    protected float getRowWidth(Row row) {
        return FishConstants.MINIGAME_LOOT_ICON + FishConstants.MINIGAME_LOOT_ICON_GAP
                + row.nameText.getWidth();
    }

    protected float getRowHeight(Row row) {
        return Math.max(FishConstants.MINIGAME_LOOT_LINE_HEIGHT,
                row.nameText.getHeight() + FishConstants.MINIGAME_LOOT_ROW_PAD);
    }

    protected float getNameMaxWidth(Row row) {
        float width = FishConstants.MINIGAME_RESULT_WIDTH
                - FishConstants.MINIGAME_RESULT_PAD * 2f
                - FishConstants.MINIGAME_LOOT_ICON
                - FishConstants.MINIGAME_LOOT_ICON_GAP;

        if (row.countText != null) {
            width -= FishConstants.MINIGAME_LOOT_COUNT_GAP + row.countText.getWidth();
        }

        return width;
    }

    protected void build(Row row) {
        if (row.nameText != null) return;

        if (row.count > 1) {
            row.countText = font.createText("x" + row.count, Color.WHITE,
                    FishConstants.MINIGAME_RESULT_TEXT_SIZE);
            row.countText.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
        }

        row.nameText = font.createText(row.name, Color.WHITE, FishConstants.MINIGAME_RESULT_TEXT_SIZE);
        row.nameText.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        row.nameText.setMaxWidth(getNameMaxWidth(row));
    }

    protected SpriteAPI getRowSprite(Row row) {
        SpriteAPI sprite = SpriteLoader.loadSprite(row.spriteName);

        return sprite == null ? getBoxSprite() : sprite;
    }

    protected SpriteAPI getBoxSprite() {
        String icon = shown > 0 ? FishConstants.TREASURE_RESULT_OPEN_ICON
                : FishConstants.TREASURE_RESULT_CLOSED_ICON;
        return SpriteLoader.loadSprite(icon);
    }

    protected void loadFonts() {
        if (fontsChecked) return;
        fontsChecked = true;

        try {
            font = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_FONT);
            titleFont = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_TITLE_FONT);

            title = titleFont.createText(FishConstants.MINIGAME_LOOT_TITLE, Color.WHITE,
                    FishConstants.MINIGAME_RESULT_TITLE_SIZE);
            title.setAnchor(LazyFont.TextAnchor.TOP_CENTER);
        } catch (Exception e) {
            Global.getLogger(LootResultPanel.class).warn("No font for the loot card", e);
        }
    }
}
