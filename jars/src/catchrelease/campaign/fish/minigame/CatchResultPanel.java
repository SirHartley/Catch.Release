package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItemPlugin;
import catchrelease.rendering.helper.Disc;
import catchrelease.rendering.helper.RoundedBorder;
import com.fs.starfarer.api.Global;
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

        transient LazyFont.DrawableString labelText;
        transient LazyFont.DrawableString valueText;

        Line(String label, String value, Color color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    protected final FishCatch entry;
    protected final List<Line> lines = new ArrayList<>();

    protected float elapsed = 0f;
    protected int shown = 0;

    transient protected LazyFont font;
    transient protected LazyFont titleFont;
    transient protected LazyFont.DrawableString title;
    transient protected LazyFont.DrawableString prompt;
    transient protected boolean fontsChecked = false;

    public CatchResultPanel(FishCatch entry) {
        this.entry = entry;

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

        if (spec != null) {
            lines.add(new Line("Species", Misc.ucFirst(spec.rarity.name().toLowerCase()), spec.rarity.color));
        }

        lines.add(new Line("Specimen", grade.name, grade.getColor()));
        lines.add(new Line("Length", String.format("%.2f m", entry.length), Misc.getHighlightColor()));
        lines.add(new Line("Weight", String.format("%.1f kg", entry.weight), Misc.getHighlightColor()));
        lines.add(new Line("Coherence", FishItemPlugin.getAberrationLabel(entry.aberration),
                FishItemPlugin.getAberrationColor(entry.aberration)));
        lines.add(new Line("Value", Misc.getDGSCredits(entry.getValue()), Misc.getHighlightColor()));
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
    }

    /** True once there is nothing left to arrive, which is when a keypress means "close". */
    public boolean isComplete() {
        return shown >= lines.size();
    }

    public void render(FishingMinigameLayout layout, SpriteAPI fishSprite, float alphaMult) {
        if (entry == null || alphaMult <= 0f) return;

        renderBox(layout, fishSprite, alphaMult);

        float y = layout.boxY - FishConstants.MINIGAME_RESULT_TITLE_GAP;

        y = renderTitle(layout, y, alphaMult);
        y = renderLines(layout, y, alphaMult);

        renderPrompt(layout, y, alphaMult);
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

        //dressing: the panel's bright line just off the square, and a dimmer one outside it
        RoundedBorder.draw(x - FishConstants.MINIGAME_BORDER_INSET - FishConstants.MINIGAME_BORDER_SPACING,
                y - FishConstants.MINIGAME_BORDER_INSET - FishConstants.MINIGAME_BORDER_SPACING,
                size + (FishConstants.MINIGAME_BORDER_INSET + FishConstants.MINIGAME_BORDER_SPACING) * 2f,
                size + (FishConstants.MINIGAME_BORDER_INSET + FishConstants.MINIGAME_BORDER_SPACING) * 2f,
                FishConstants.MINIGAME_BORDER_RADIUS, Misc.getDarkPlayerColor(),
                FishConstants.MINIGAME_BORDER_OUTER_ALPHA * alphaMult, FishConstants.MINIGAME_BORDER_WIDTH);

        RoundedBorder.draw(x - FishConstants.MINIGAME_BORDER_INSET, y - FishConstants.MINIGAME_BORDER_INSET,
                size + FishConstants.MINIGAME_BORDER_INSET * 2f, size + FishConstants.MINIGAME_BORDER_INSET * 2f,
                FishConstants.MINIGAME_BORDER_RADIUS, Misc.getBrightPlayerColor(),
                FishConstants.MINIGAME_BORDER_ALPHA * alphaMult, FishConstants.MINIGAME_BORDER_WIDTH);
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

            //the newest line arrives rather than switching on
            float age = elapsed - (i + 1) * FishConstants.MINIGAME_RESULT_LINE_DELAY;
            float alpha = alphaMult * MathUtils.clamp(age / FishConstants.MINIGAME_RESULT_FADE, 0f, 1f);

            line.labelText.setBaseColor(withAlpha(Misc.getGrayColor(), alpha));
            line.labelText.draw(layout.resultX, y);

            line.valueText.setBaseColor(withAlpha(line.color, alpha));
            line.valueText.draw(right, y);

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
