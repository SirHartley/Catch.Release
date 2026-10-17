package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.rendering.helper.Disc;
import catchrelease.ui.ShopUi;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class FishSizeChart extends BaseCustomUIPanelPlugin {

    public static final float HEIGHT = 104f;
    public static final float AXIS_LABEL_HEIGHT = 16f;
    public static final float MARKER_ROW_HEIGHT = 13f;
    public static final float CURVE_HEIGHT_SHARE = 0.9f;
    public static final int SEGMENTS = 48;
    public static final float LINE_WIDTH = 1.5f;
    public static final float DOT_RADIUS = 3f;
    // FishCatch.roll's triangular length has this standard deviation as a fraction of the range
    public static final float SIGMA = (float) (1.0 / Math.sqrt(24.0));

    protected final FishSpec spec;
    protected final float best;
    protected final float last;
    protected PositionAPI pos;

    protected transient LazyFont.DrawableString minText;
    protected transient LazyFont.DrawableString maxText;
    protected transient LazyFont.DrawableString bestText;
    protected transient LazyFont.DrawableString lastText;

    public FishSizeChart(FishSpec spec, FishLogEntry logged) {
        this.spec = spec;
        this.best = logged.recordLength;
        this.last = logged.lastLength;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f || spec.lengthMax <= spec.lengthMin) return;

        float x = pos.getX();
        float width = pos.getWidth();
        float baseline = pos.getY() + AXIS_LABEL_HEIGHT;
        float plotHeight = pos.getHeight() - AXIS_LABEL_HEIGHT - 2f * MARKER_ROW_HEIGHT;
        Color color = spec.rarity.color;

        renderCurve(x, baseline, width, plotHeight, color, alphaMult);
        ShopUi.drawQuad(x, baseline, width, 1f, Misc.getDarkPlayerColor(), alphaMult);

        boolean same = last <= 0f || Math.abs(last - best) < 0.005f;
        if (!same) renderMarker(x, baseline, width, plotHeight, last, Misc.getBrightPlayerColor(), alphaMult);
        renderMarker(x, baseline, width, plotHeight, best, Misc.getHighlightColor(), alphaMult);

        renderLabels(x, baseline, width, plotHeight, same, alphaMult);
    }

    // A normal curve with the roll's mean and spread, lowered so both range ends touch the axis.
    protected static float curve(float t) {
        double floor = Math.exp(-0.5 * Math.pow(0.5 / SIGMA, 2));
        double value = Math.exp(-0.5 * Math.pow((t - 0.5) / SIGMA, 2));

        return (float) Math.max(0.0, (value - floor) / (1.0 - floor));
    }

    protected float fraction(float length) {
        return Math.max(0f, Math.min(1f, (length - spec.lengthMin) / (spec.lengthMax - spec.lengthMin)));
    }

    protected void renderCurve(float x, float baseline, float width, float plotHeight, Color color,
                               float alphaMult) {
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float peak = plotHeight * CURVE_HEIGHT_SHARE;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        GL11.glColor4f(r, g, b, 0.25f * alphaMult);
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = i / (float) SEGMENTS;
            GL11.glVertex2f(x + t * width, baseline);
            GL11.glVertex2f(x + t * width, baseline + curve(t) * peak);
        }
        GL11.glEnd();

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(LINE_WIDTH);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glColor4f(r, g, b, 0.9f * alphaMult);
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = i / (float) SEGMENTS;
            GL11.glVertex2f(x + t * width, baseline + curve(t) * peak);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    protected void renderMarker(float x, float baseline, float width, float plotHeight, float length,
                                Color color, float alphaMult) {
        float t = fraction(length);
        float markerX = Math.round(x + t * width);
        float top = baseline + curve(t) * plotHeight * CURVE_HEIGHT_SHARE;

        ShopUi.drawQuad(markerX, baseline, 1f, Math.max(1f, top - baseline), color, 0.8f * alphaMult);
        Disc.draw(markerX, top, DOT_RADIUS, color, alphaMult, alphaMult, false);
    }

    protected void renderLabels(float x, float baseline, float width, float plotHeight, boolean same,
                                float alphaMult) {
        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        if (minText == null) {
            minText = createText(font, String.format("%.2f m", spec.lengthMin), LazyFont.TextAnchor.TOP_LEFT);
            maxText = createText(font, String.format("%.2f m", spec.lengthMax), LazyFont.TextAnchor.TOP_RIGHT);
            bestText = createText(font, String.format(same ? "Best and last %.2f m" : "Best %.2f m", best),
                    LazyFont.TextAnchor.TOP_LEFT);
            if (!same) lastText = createText(font, String.format("Last %.2f m", last), LazyFont.TextAnchor.TOP_LEFT);
        }

        Color gray = ShopUi.withAlpha(Misc.getGrayColor(), alphaMult);
        minText.setBaseColor(gray);
        minText.draw(Math.round(x), Math.round(baseline - 3f));
        maxText.setBaseColor(gray);
        maxText.draw(Math.round(x + width), Math.round(baseline - 3f));

        // Best keeps the top row and Last the one below, so the labels never overlap.
        float top = baseline + plotHeight + 2f * MARKER_ROW_HEIGHT;
        drawMarkerLabel(bestText, x, width, best, top, Misc.getHighlightColor(), alphaMult);
        if (lastText != null) {
            drawMarkerLabel(lastText, x, width, last, top - MARKER_ROW_HEIGHT, Misc.getBrightPlayerColor(), alphaMult);
        }
    }

    protected void drawMarkerLabel(LazyFont.DrawableString text, float x, float width, float length, float top,
                                   Color color, float alphaMult) {
        float left = x + fraction(length) * width - text.getWidth() * 0.5f;
        left = Math.max(x, Math.min(x + width - text.getWidth(), left));

        text.setBaseColor(ShopUi.withAlpha(color, alphaMult));
        text.draw(Math.round(left), Math.round(top));
    }

    protected static LazyFont.DrawableString createText(LazyFont font, String value, LazyFont.TextAnchor anchor) {
        LazyFont.DrawableString text = ShopUi.createText(font, value);
        text.setAnchor(anchor);
        return text;
    }
}
