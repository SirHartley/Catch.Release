package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItemPlugin;
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


public class CatchResultPanel {

    protected static class Line {
        final String label;
        final String value;
        final Color color;


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

    protected final List<Line> lines = new ArrayList<>();

    protected float elapsed = 0f;
    protected int shown = 0;


    protected boolean skipped = false;


    protected boolean record = false;


    protected boolean newSpecies = false;


    protected static class Bubble {
        float fx;
        float startY;
        float speed;
        float radius;
        float phase;
    }

    protected final List<Bubble> bubbles = new ArrayList<>();

    transient protected LazyFont font;
    transient protected LazyFont titleFont;
    transient protected LazyFont.DrawableString title;
    transient protected LazyFont.DrawableString prompt;
    transient protected LazyFont.DrawableString recordText;
    transient protected boolean fontsChecked = false;

    public CatchResultPanel(FishCatch entry, SectorEntityToken where, FishLogEntry.Method method) {
        this.entry = entry;
        this.where = where;
        this.method = method;

        buildLines();
    }


    protected void buildLines() {
        if (entry == null) return;

        FishSpec spec = entry.getSpec();
        FishGrade grade = entry.getGrade();

        // filed before anything is drawn, since the comparison is against what was there beforehand; this is also where a species stops being unknown to the codex
        newSpecies = !FishLog.isCaught(entry.speciesId);
        record = FishLog.record(entry, where, method);

        if (spec != null) {
            lines.add(new Line("Species", Misc.ucFirst(spec.rarity.name().toLowerCase()), spec.rarity.color));
        }

        lines.add(new Line("Grade", grade.name, grade.getColor()));
        Line length = new Line("Length", String.format("%.2f m", entry.length), Misc.getHighlightColor());
        length.record = record;
        lines.add(length);
        lines.add(new Line("Weight", String.format("%.1f kg", entry.weight), Misc.getHighlightColor()));
        lines.add(new Line("Coherence", FishItemPlugin.getAberrationLabel(entry.aberration),
                FishItemPlugin.getAberrationColor(entry.aberration)));
        lines.add(new Line("Value", Misc.getDGSCredits(entry.getValue()), Misc.getHighlightColor()));

        // other loot is not a row here - see LootResultPanel; a record isn't either - see renderRecord
    }

    public void advance(float amount) {
        elapsed += amount;

        while (shown < lines.size()
                && elapsed >= (shown + 1) * FishConstants.MINIGAME_RESULT_LINE_DELAY) {

            shown++;
            CatchCelebration.playHook(FishConstants.SOUND_RESULT_LINE);
        }
    }

    public void revealAll() {
        shown = lines.size();
        skipped = true;
    }


    public boolean isComplete() {
        return shown >= lines.size();
    }

    public void render(FishingMinigameLayout layout, SpriteAPI fishSprite, float alphaMult) {
        if (entry == null || alphaMult <= 0f) return;

        // before anything is placed, since the fonts are what the column is measured with
        loadFonts();
        layout.fitResultContent(getContentWidth());
        layout.centerResultContent(getContentHeight(), getRecordHeadroom());

        renderPanel(layout, alphaMult);
        renderBox(layout, fishSprite, alphaMult);
        renderRecord(layout, alphaMult);

        float y = layout.boxY - FishConstants.MINIGAME_RESULT_BOX_GAP;

        y = renderTitle(layout, y, alphaMult);
        y = renderLines(layout, y, alphaMult);

        renderPrompt(layout, y, alphaMult);
    }


    protected float getRecordHeadroom() {
        if (!record || recordText == null) return 0f;

        return FishConstants.MINIGAME_RESULT_RECORD_GAP + recordText.getHeight()
                + FishConstants.MINIGAME_RESULT_RECORD_BOUNCE;
    }


    protected float getContentWidth() {
        float widest = 0f;

        if (title != null) widest = Math.max(widest, title.getWidth());
        if (prompt != null) widest = Math.max(widest, prompt.getWidth());
        if (recordText != null) widest = Math.max(widest, recordText.getWidth());

        if (font == null) return widest;

        for (Line line : lines) {
            build(line);

            widest = Math.max(widest, line.labelText.getWidth()
                    + FishConstants.MINIGAME_RESULT_COLUMN_GAP + line.valueText.getWidth());
        }

        return widest;
    }


    protected float getContentHeight() {
        float height = FishConstants.MINIGAME_RESULT_BOX;

        if (title != null) {
            height += FishConstants.MINIGAME_RESULT_BOX_GAP + title.getHeight()
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


    protected void renderPanel(FishingMinigameLayout layout, float alphaMult) {
        drawQuad(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight,
                Color.BLACK, 0.85f * alphaMult);

        drawQuad(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight,
                Misc.getDarkPlayerColor(), 0.07f * alphaMult);

        // discovery gets light as well as water; ordinary records keep the quieter bubble field
        if (newSpecies) renderGodRays(layout, alphaMult);

        // between the field and the content, so they read as texture in the card rather than on it
        renderBubbles(layout, alphaMult);

        dress(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight, alphaMult);
    }


    protected void renderGodRays(FishingMinigameLayout layout, float alphaMult) {
        Color color = FishConstants.MINIGAME_RESULT_GOD_RAY_COLOR;
        float topY = layout.panelY + layout.panelHeight;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        for (int i = 0; i < FishConstants.MINIGAME_RESULT_GOD_RAYS; i++) {
            float anchor = layout.panelX + layout.panelWidth
                    * (FishConstants.MINIGAME_RESULT_GOD_RAY_ANCHOR_START
                    + FishConstants.MINIGAME_RESULT_GOD_RAY_ANCHOR_STEP * i)
                    + (float) Math.sin(elapsed * FishConstants.MINIGAME_RESULT_GOD_RAY_SWAY_RATE
                    + i * FishConstants.MINIGAME_RESULT_GOD_RAY_SWAY_PHASE_STEP)
                    * layout.panelWidth * FishConstants.MINIGAME_RESULT_GOD_RAY_SWAY;
            float topHalf = layout.panelWidth * FishConstants.MINIGAME_RESULT_GOD_RAY_TOP_HALF_WIDTH;
            float bottomHalf = topHalf * FishConstants.MINIGAME_RESULT_GOD_RAY_BOTTOM_WIDTH_MULT;
            float lean = layout.panelWidth * FishConstants.MINIGAME_RESULT_GOD_RAY_LEAN;
            float strength = FishConstants.MINIGAME_RESULT_GOD_RAY_ALPHA
                    + FishConstants.MINIGAME_RESULT_GOD_RAY_ALPHA_SWING
                    * (float) Math.sin(elapsed * FishConstants.MINIGAME_RESULT_GOD_RAY_PULSE_RATE
                    + i * FishConstants.MINIGAME_RESULT_GOD_RAY_PULSE_PHASE_STEP);

            drawGodRay(anchor, anchor + lean, topY, layout.panelY,
                    topHalf, bottomHalf, color, strength * alphaMult);
        }

        GL11.glPopAttrib();
    }

    protected static void drawGodRay(float topX, float bottomX, float topY, float bottomY,
                                     float topHalf, float bottomHalf, Color color, float alpha) {

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r, g, b, alpha);
        GL11.glVertex2f(topX - topHalf, topY);
        GL11.glVertex2f(topX + topHalf, topY);
        GL11.glColor4f(r, g, b, 0f);
        GL11.glVertex2f(bottomX + bottomHalf, bottomY);
        GL11.glVertex2f(bottomX - bottomHalf, bottomY);
        GL11.glEnd();
    }


    protected void renderBubbles(FishingMinigameLayout layout, float alphaMult) {
        if (bubbles.isEmpty()) spawnBubbles();

        for (Bubble b : bubbles) {
            float risen = (b.startY + b.speed * elapsed) % layout.panelHeight;

            float x = layout.panelX + b.fx * layout.panelWidth
                    + (float) Math.sin(elapsed * FishConstants.MINIGAME_RESULT_BUBBLE_DRIFT_RATE + b.phase)
                            * FishConstants.MINIGAME_RESULT_BUBBLE_DRIFT;

            Disc.drawOutline(x, layout.panelY + risen, b.radius, Misc.getBrightPlayerColor(),
                    FishConstants.MINIGAME_RESULT_BUBBLE_ALPHA * alphaMult, 1f);
        }
    }


    protected void spawnBubbles() {
        for (int i = 0; i < FishConstants.MINIGAME_RESULT_BUBBLES; i++) {
            Bubble b = new Bubble();

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


    protected void renderBox(FishingMinigameLayout layout, SpriteAPI sprite, float alphaMult) {
        FishSpec spec = entry.getSpec();
        Color accent = spec == null ? Color.WHITE : spec.rarity.color;

        float x = layout.boxX;
        float y = layout.boxY;
        float size = layout.boxSize;

        drawQuad(x, y, size, size, Color.BLACK, 0.75f * alphaMult);

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

        // the same marks it will carry in the hold, in the same corner
        catchrelease.campaign.fish.items.FishItemRenderer.render(x, y, size, size, alphaMult,
                spec == null ? null : spec.rarity, entry.getGrade());

        dress(x, y, size, size, alphaMult);
    }


    protected void renderRecord(FishingMinigameLayout layout, float alphaMult) {
        if (!record || recordText == null || !isComplete()) return;

        float bounce = (float) Math.sin(elapsed * FishConstants.MINIGAME_RESULT_RECORD_BOUNCE_RATE)
                * FishConstants.MINIGAME_RESULT_RECORD_BOUNCE;

        Color bannerColor = newSpecies ? Misc.getHighlightColor() : Misc.getPositiveHighlightColor();
        recordText.setBaseColor(withAlpha(bannerColor, alphaMult));
        recordText.draw(layout.getBoxCenterX(),
                layout.boxY + layout.boxSize + FishConstants.MINIGAME_RESULT_RECORD_GAP
                        + recordText.getHeight() + bounce);
    }


    protected float renderTitle(FishingMinigameLayout layout, float y, float alphaMult) {
        loadFonts();
        if (title == null) return y;

        title.setBaseColor(withAlpha(Misc.getBrightPlayerColor(), alphaMult));
        title.draw(layout.resultX + layout.resultWidth * 0.5f, y);

        return y - title.getHeight() - FishConstants.MINIGAME_RESULT_TITLE_GAP;
    }


    protected float renderLines(FishingMinigameLayout layout, float y, float alphaMult) {
        if (font == null) return y;

        float right = layout.resultX + layout.resultWidth;

        for (int i = 0; i < lines.size(); i++) {
            if (i >= shown) break;

            Line line = lines.get(i);
            build(line);

            float age = elapsed - (i + 1) * FishConstants.MINIGAME_RESULT_LINE_DELAY;
            float alpha = skipped
                    ? alphaMult
                    : alphaMult * MathUtils.clamp(age / FishConstants.MINIGAME_RESULT_FADE, 0f, 1f);

            line.labelText.setBaseColor(withAlpha(Misc.getGrayColor(), alpha));
            line.labelText.draw(layout.resultX, y);

            line.valueText.setBaseColor(withAlpha(line.color, alpha));
            line.valueText.draw(right, y);

            if (line.record && line.markText != null) {
                line.markText.setBaseColor(withAlpha(Misc.getHighlightColor(), alpha));
                line.markText.draw(right + FishConstants.MINIGAME_RESULT_MARK_GAP, y);
            }

            y -= FishConstants.MINIGAME_RESULT_LINE_HEIGHT;
        }

        return y;
    }


    protected void renderPrompt(FishingMinigameLayout layout, float y, float alphaMult) {
        if (!isComplete() || prompt == null) return;

        float lit = 0.5f - 0.5f * (float) Math.cos(
                elapsed * (Math.PI * 2.0) / FishConstants.MINIGAME_RESULT_PROMPT_PERIOD);

        prompt.setBaseColor(withAlpha(
                blend(FishConstants.MINIGAME_RESULT_PROMPT_DIM, FishConstants.MINIGAME_RESULT_PROMPT_LIT, lit),
                alphaMult * FishConstants.MINIGAME_RESULT_PROMPT_ALPHA));
        prompt.draw(layout.resultX + layout.resultWidth * 0.5f, y - FishConstants.MINIGAME_RESULT_TITLE_GAP);
    }


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


    protected void loadFonts() {
        if (fontsChecked) return;
        fontsChecked = true;

        try {
            font = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_FONT);
            titleFont = LazyFont.loadFont(FishConstants.MINIGAME_RESULT_TITLE_FONT);

            // wrapped at the widest the card can grow, not its floor - the wrap doubles as the width measurement, so wrapping at the floor would cap it there
            title = titleFont.createText(entry.getDisplayName(), Color.WHITE,
                    FishConstants.MINIGAME_RESULT_TITLE_SIZE,
                    FishConstants.MINIGAME_RESULT_MAX_WIDTH - FishConstants.MINIGAME_RESULT_PAD * 2f);
            title.setAnchor(LazyFont.TextAnchor.TOP_CENTER);
            title.setAlignment(LazyFont.TextAlignment.CENTER);

            prompt = font.createText("Press any key", Color.WHITE, FishConstants.MINIGAME_RESULT_TEXT_SIZE);
            prompt.setAnchor(LazyFont.TextAnchor.TOP_CENTER);

            if (record) {
                String heading = newSpecies ? FishConstants.MINIGAME_RESULT_NEW_SPECIES
                        : FishConstants.MINIGAME_RESULT_RECORD;

                recordText = font.createText(heading, Color.WHITE,
                        FishConstants.MINIGAME_RESULT_TEXT_SIZE);
                recordText.setAnchor(LazyFont.TextAnchor.TOP_CENTER);
            }
        } catch (Exception e) {
            Global.getLogger(CatchResultPanel.class).warn("No font for the catch readout", e);
        }
    }

    protected static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int) MathUtils.clamp(alpha * 255f, 0f, 255f));
    }

    protected static Color blend(Color from, Color to, float share) {
        share = MathUtils.clamp(share, 0f, 1f);

        return new Color(
                (int) (from.getRed() + (to.getRed() - from.getRed()) * share),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * share),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * share));
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
