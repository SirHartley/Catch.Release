package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItemPlugin;
import catchrelease.campaign.fish.treasure.TreasureRarity;
import catchrelease.rendering.helper.Disc;
import catchrelease.rendering.helper.RoundedBorder;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * What was actually caught, beside the track: the specimen in a cargo-square, its name under that,
 * and its numbers under that a line at a time.
 * <p>
 * The square is deliberately the shape and size of a cargo cell, because the next place this fish
 * will be seen is the hold, and it should be recognisable when it gets there - down to the grade and
 * rarity marks along the bottom of it.
 * <p>
 * The lines arrive one at a time rather than all at once. A number that lands has been read; five
 * numbers that appear together have been skipped. Each one is a sound as well as a line, which is
 * what turns a readout into a tally being counted out.
 */
public class CatchResultPanel {

    /** One row of the readout: what it is on the left, what it says on the right. */
    protected static class Line {
        final String label;
        final String value;
        final Color color;

        /** Set on the row a record was set on, which gets a mark after its value. */
        boolean record;

        transient LazyFont.DrawableString labelText;
        transient LazyFont.DrawableString valueText;
        transient LazyFont.DrawableString markText;

        Line(String label, String value, Color color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    protected final FishCatch entry;
    protected final SectorEntityToken where;
    protected final FishLogEntry.Method method;

    /** What else came up, if anything. Read out after the fish, since the fish is what was played. */
    protected final String treasure;
    protected final TreasureRarity treasureRarity;
    protected final List<Line> lines = new ArrayList<>();

    protected float elapsed = 0f;
    protected int shown = 0;

    /** Set when the tally was skipped. Lines shown this way arrive at once, without fading in. */
    protected boolean skipped = false;

    transient protected LazyFont font;
    transient protected LazyFont titleFont;
    transient protected LazyFont.DrawableString title;
    transient protected LazyFont.DrawableString prompt;
    transient protected boolean fontsChecked = false;

    public CatchResultPanel(FishCatch entry, SectorEntityToken where, FishLogEntry.Method method,
                            String treasure, TreasureRarity treasureRarity) {
        this.entry = entry;
        this.where = where;
        this.method = method;
        this.treasure = treasure;
        this.treasureRarity = treasureRarity;

        buildLines();
    }

    /**
     * What is worth saying about one specimen, in the order it is worth saying it: what kind of
     * thing this was, then how good an example of it, then the measurements that decide that, then
     * what it is worth.
     */
    protected void buildLines() {
        if (entry == null) return;

        FishSpec spec = entry.getSpec();
        FishGrade grade = entry.getGrade();

        //filed before anything is drawn, since the comparison is against what was there beforehand.
        //This is also where a species stops being unknown to the codex
        boolean record = FishLog.record(entry, where, method);

        if (spec != null) {
            lines.add(new Line("Species", Misc.ucFirst(spec.rarity.name().toLowerCase()), spec.rarity.color));
        }

        lines.add(new Line("Specimen", grade.name, grade.getColor()));
        Line length = new Line("Length", String.format("%.2f m", entry.length), Misc.getHighlightColor());
        length.record = record;
        lines.add(length);
        lines.add(new Line("Weight", String.format("%.1f kg", entry.weight), Misc.getHighlightColor()));
        lines.add(new Line("Coherence", FishItemPlugin.getAberrationLabel(entry.aberration),
                FishItemPlugin.getAberrationColor(entry.aberration)));
        lines.add(new Line("Value", Misc.getDGSCredits(entry.getValue()), Misc.getHighlightColor()));

        //after the specimen, because the specimen is what was being played for
        if (treasure != null && treasureRarity != null) {
            lines.add(new Line(treasureRarity.name, treasure, treasureRarity.color));
        }

        //last, so it lands after the number it is about rather than interrupting the tally
        if (record) {
            lines.add(new Line("", FishConstants.MINIGAME_RESULT_RECORD, Misc.getPositiveHighlightColor()));
        }
    }

    public void advance(float amount) {
        elapsed += amount;

        while (shown < lines.size()
                && elapsed >= (shown + 1) * FishConstants.MINIGAME_RESULT_LINE_DELAY) {

            shown++;
            CatchCelebration.playHook(FishConstants.SOUND_RESULT_LINE);
        }
    }

    /** Everything at once, for a player who would rather not be read to. */
    public void revealAll() {
        shown = lines.size();
        skipped = true;
    }

    /** True once there is nothing left to arrive, which is when a keypress means "close". */
    public boolean isComplete() {
        return shown >= lines.size();
    }

    public void render(FishingMinigameLayout layout, SpriteAPI fishSprite, float alphaMult) {
        if (entry == null || alphaMult <= 0f) return;

        //before anything is placed, since the fonts are what the column is measured with
        loadFonts();
        layout.fitResultContent(getContentWidth());
        layout.centerResultContent(getContentHeight());

        renderPanel(layout, alphaMult);
        renderBox(layout, fishSprite, alphaMult);

        float y = layout.boxY - FishConstants.MINIGAME_RESULT_TITLE_GAP;

        y = renderTitle(layout, y, alphaMult);
        y = renderLines(layout, y, alphaMult);

        renderPrompt(layout, y, alphaMult);
    }

    /**
     * The widest thing that has to fit across the column.
     * <p>
     * The species name is the usual offender - it is read off a table, and the long ones ran off the
     * card - but a row is a label drawn to the left edge and a value drawn to the right one, so a
     * long enough pair collides in the middle the same way. A treasure is the other candidate.
     * <p>
     * Measured off every line up front rather than off the ones that have arrived, for the same
     * reason the height is: the tally reads out one row at a time, and a card that grew as it filled
     * in would be worse than one that was too narrow to begin with.
     */
    protected float getContentWidth() {
        float widest = 0f;

        if (title != null) widest = Math.max(widest, title.getWidth());
        if (prompt != null) widest = Math.max(widest, prompt.getWidth());

        if (font == null) return widest;

        for (Line line : lines) {
            build(line);

            widest = Math.max(widest, line.labelText.getWidth()
                    + FishConstants.MINIGAME_RESULT_COLUMN_GAP + line.valueText.getWidth());
        }

        return widest;
    }

    /**
     * Box top to prompt bottom, as the render methods will space it. Counted off the full line list
     * and with the prompt in, not off what has arrived so far - so the column is measured once and
     * nothing shifts while the tally is still being read out.
     */
    protected float getContentHeight() {
        float height = FishConstants.MINIGAME_RESULT_BOX;

        if (title != null) {
            height += FishConstants.MINIGAME_RESULT_TITLE_GAP + title.getHeight()
                    + FishConstants.MINIGAME_RESULT_TITLE_GAP;
        }

        if (font != null) {
            height += lines.size() * FishConstants.MINIGAME_RESULT_LINE_HEIGHT;
        }

        if (prompt != null) {
            height += FishConstants.MINIGAME_RESULT_TITLE_GAP + prompt.getHeight();
        }

        return height;
    }

    /**
     * The readout's own frame: a dark field with the same bright-line-and-dimmer-line dressing the
     * catch's panel carries, so the two read as two panels of one interface rather than as one panel
     * with something bolted to the side of it.
     */
    protected void renderPanel(FishingMinigameLayout layout, float alphaMult) {
        drawQuad(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight,
                Color.BLACK, 0.85f * alphaMult);

        drawQuad(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight,
                Misc.getDarkPlayerColor(), 0.07f * alphaMult);

        dress(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight, alphaMult);
    }

    /** The bright outline just off a box and the dimmer one outside it, as the catch's panel has. */
    protected static void dress(float x, float y, float width, float height, float alphaMult) {
        float inset = FishConstants.MINIGAME_BORDER_INSET;
        float spacing = FishConstants.MINIGAME_BORDER_SPACING;

        outline(x, y, width, height, inset + spacing, Misc.getDarkPlayerColor(),
                FishConstants.MINIGAME_BORDER_OUTER_ALPHA * alphaMult);

        outline(x, y, width, height, inset, Misc.getBrightPlayerColor(),
                FishConstants.MINIGAME_BORDER_ALPHA * alphaMult);
    }

    protected static void outline(float x, float y, float width, float height, float offset,
                                  Color color, float alpha) {

        RoundedBorder.draw(x - offset, y - offset, width + offset * 2f, height + offset * 2f,
                FishConstants.MINIGAME_BORDER_RADIUS + offset, color, alpha,
                FishConstants.MINIGAME_BORDER_WIDTH);
    }

    /**
     * The cargo-square: dark, backlit in the fish's own colour, ringed the way the rest of the panel
     * is ringed, with the specimen in it and its marks along the bottom.
     */
    protected void renderBox(FishingMinigameLayout layout, SpriteAPI sprite, float alphaMult) {
        FishSpec spec = entry.getSpec();
        Color accent = spec == null ? Color.WHITE : spec.rarity.color;

        float x = layout.boxX;
        float y = layout.boxY;
        float size = layout.boxSize;

        drawQuad(x, y, size, size, Color.BLACK, 0.75f * alphaMult);

        //a wash of the rarity colour behind the art, so the silhouette has something to sit against
        Disc.draw(x + size * 0.5f, y + size * 0.5f, size * 0.5f, accent,
                0.3f * alphaMult, 0f, true);

        if (sprite != null) {
            float available = size - FishConstants.MINIGAME_RESULT_BOX_PAD * 2f;
            float scale = Math.min(available / sprite.getWidth(), available / sprite.getHeight());

            sprite.setSize(sprite.getWidth() * scale, sprite.getHeight() * scale);
            sprite.setNormalBlend();
            sprite.setAlphaMult(alphaMult);
            sprite.renderAtCenter(x + size * 0.5f, y + size * 0.5f);
        }

        //the same two marks it will carry in the hold, in the same corner of the same square
        catchrelease.campaign.fish.items.FishItemRenderer.render(x, y, size, size, alphaMult,
                spec == null ? null : spec.rarity, entry.getGrade());

        dress(x, y, size, size, alphaMult);
    }

    /** @return the y the next thing down should start at */
    protected float renderTitle(FishingMinigameLayout layout, float y, float alphaMult) {
        loadFonts();
        if (title == null) return y;

        title.setBaseColor(withAlpha(Misc.getBrightPlayerColor(), alphaMult));
        title.draw(layout.resultX, y);

        return y - title.getHeight() - FishConstants.MINIGAME_RESULT_TITLE_GAP;
    }

    /**
     * The numbers, each fading in as it lands rather than appearing. @return the y under the last of
     * them.
     */
    protected float renderLines(FishingMinigameLayout layout, float y, float alphaMult) {
        if (font == null) return y;

        float right = layout.resultX + layout.resultWidth;

        for (int i = 0; i < lines.size(); i++) {
            if (i >= shown) break;

            Line line = lines.get(i);
            build(line);

            //the newest line arrives rather than switching on - unless it was skipped to, in which
            //case it is already meant to be here and fading it in would be a second wait
            float age = elapsed - (i + 1) * FishConstants.MINIGAME_RESULT_LINE_DELAY;
            float alpha = skipped
                    ? alphaMult
                    : alphaMult * MathUtils.clamp(age / FishConstants.MINIGAME_RESULT_FADE, 0f, 1f);

            line.labelText.setBaseColor(withAlpha(Misc.getGrayColor(), alpha));
            line.labelText.draw(layout.resultX, y);

            line.valueText.setBaseColor(withAlpha(line.color, alpha));
            line.valueText.draw(right, y);

            //a record is marked on the row that set it as well as being said in words below, so the
            //eye lands on the number rather than on the announcement. Hung in the gutter past the
            //value, in the value's own colour - the number column keeps its edge, and the mark
            //reads as part of the number rather than as a second thing on the row
            if (line.record && line.markText != null) {
                line.markText.setBaseColor(withAlpha(Misc.getHighlightColor(), alpha));
                line.markText.draw(right + FishConstants.MINIGAME_RESULT_MARK_GAP, y);
            }

            y -= FishConstants.MINIGAME_RESULT_LINE_HEIGHT;
        }

        return y;
    }

    /** Said once there is nothing left to wait for, since that is the only point at which it is true. */
    protected void renderPrompt(FishingMinigameLayout layout, float y, float alphaMult) {
        if (!isComplete() || prompt == null) return;

        prompt.setBaseColor(withAlpha(Misc.getGrayColor(),
                alphaMult * FishConstants.MINIGAME_RESULT_PROMPT_ALPHA));
        prompt.draw(layout.resultX, y - FishConstants.MINIGAME_RESULT_TITLE_GAP);
    }

    /** Built on first sight rather than up front, so a line that is never shown is never made. */
    protected void build(Line line) {
        if (line.labelText != null) return;

        line.labelText = font.createText(line.label, Color.WHITE, FishConstants.MINIGAME_RESULT_TEXT_SIZE);
        line.labelText.setAnchor(LazyFont.TextAnchor.TOP_LEFT);

        line.valueText = font.createText(line.value, Color.WHITE, FishConstants.MINIGAME_RESULT_TEXT_SIZE);
        line.valueText.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);

        if (!line.record) return;

        line.markText = font.createText(FishConstants.MINIGAME_RESULT_RECORD_MARK, Color.WHITE,
                FishConstants.MINIGAME_RESULT_TEXT_SIZE);
        line.markText.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
    }

    /** Loaded once and kept. A missing font costs the text and nothing else. */
    protected void loadFonts() {
        if (fontsChecked) return;
        fontsChecked = true;

        try {
            font = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_FONT);
            titleFont = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_TITLE_FONT);

            title = titleFont.createText(entry.getDisplayName(), Color.WHITE,
                    FishConstants.MINIGAME_RESULT_TITLE_SIZE, FishConstants.MINIGAME_RESULT_WIDTH);
            title.setAnchor(LazyFont.TextAnchor.TOP_LEFT);

            prompt = font.createText("Press any key", Color.WHITE, FishConstants.MINIGAME_RESULT_TEXT_SIZE);
            prompt.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
        } catch (Exception e) {
            Global.getLogger(CatchResultPanel.class).warn("No font for the catch readout", e);
        }
    }

    protected static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int) MathUtils.clamp(alpha * 255f, 0f, 255f));
    }

    protected static void drawQuad(float x, float y, float width, float height, Color color, float alpha) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();

        GL11.glPopAttrib();
    }
}
