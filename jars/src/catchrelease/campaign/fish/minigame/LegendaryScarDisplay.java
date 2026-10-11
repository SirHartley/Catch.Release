package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class LegendaryScarDisplay {

    public static final float DIAMETER = 24f;
    public static final float SPACING = 32f;
    public static final float STAMP_SCALE = 2.4f;
    public static final float STAMP_TIME = 0.6f;
    public static final float NOTICE_TIME = 5f;
    public static final String SOUND = "ui_cargo_supplies_drop";
    public static final String EXPLANATION = "The pattern is now scarred. The hunt will be easier, at the cost of quality.";

    private int count;
    private boolean failed;
    private boolean added;
    private boolean stamped;
    private boolean hoversAdded;
    private boolean promptAdded;
    private boolean disposed;
    private float elapsed;
    private CustomPanelAPI frame;
    private UIPanelAPI notice;
    private final List<UIPanelAPI> components = new ArrayList<>();
    private SpriteAPI icon;

    public LegendaryScarDisplay(int count) {
        this.count = Math.max(0, Math.min(LegendaryChases.MAX_SCARS, count));
    }

    public void attach(CustomPanelAPI frame) {
        this.frame = frame;
    }

    public void failed(int scars) {
        if (failed || disposed) return;
        failed = true;
        int next = Math.max(count, Math.min(LegendaryChases.MAX_SCARS, scars));
        added = next > count;
        count = next;
        elapsed = 0f;
    }

    public void advance(float amount, boolean running) {
        if (disposed || running) return;
        elapsed += amount;
        if (added && !stamped && elapsed >= STAMP_TIME) {
            stamped = true;
            CatchCelebration.playHook(SOUND);
            if (frame != null) showNotice();
        }
        if (notice != null && elapsed >= STAMP_TIME + NOTICE_TIME) {
            frame.removeComponent(notice);
            notice = null;
        }
        if (frame != null && !hoversAdded && (!added || elapsed >= STAMP_TIME + NOTICE_TIME)) {
            addHovers();
        }
        if (frame != null && !promptAdded && canDismiss()) {
            promptAdded = true;
            TooltipMakerAPI prompt = frame.createUIElement(frame.getPosition().getWidth(), 20f, false);
            prompt.setParaFont(FishConstants.MINIGAME_RESULT_FONT);
            prompt.addPara(FishConstants.MINIGAME_RESULT_PROMPT, Misc.getGrayColor(), 0f).setAlignment(Alignment.MID);
            frame.addUIElement(prompt).inTL(0f, frame.getPosition().getHeight() - 30f);
            components.add(prompt);
        }
    }

    public boolean canDismiss() {
        return !disposed && failed && (!added || stamped);
    }

    public void skipAnimation() {
        if (disposed || !failed || !added || stamped) return;
        // Finish on advance so input dispatch does not rebuild the panel tree.
        elapsed = STAMP_TIME;
    }

    private void showNotice() {
        TooltipMakerAPI text = frame.createUIElement(240f, 0f, false);
        explain(text);
        frame.updateUIElementSizeAndMakeItProcessInput(text);
        notice = frame.wrapTooltipWithBox(text, Misc.getBasePlayerColor());
        FishingMinigameLayout layout = new FishingMinigameLayout(frame.getPosition());
        frame.addComponent(notice).inTL(x(layout) - frame.getPosition().getX() + DIAMETER,
                frame.getPosition().getY() + frame.getPosition().getHeight() - y(layout, count - 1)
                        - notice.getPosition().getHeight() * 0.5f);
    }

    private void addHovers() {
        hoversAdded = true;
        if (count == 0) return;
        FishingMinigameLayout layout = new FishingMinigameLayout(frame.getPosition());
        TooltipMakerAPI tips = frame.createUIElement(1f, 1f, false);
        frame.addUIElement(tips).inTL(0f, 0f);
        components.add(tips);
        for (int i = 0; i < count; i++) {
            CustomPanelAPI target = frame.createCustomPanel(DIAMETER, DIAMETER,
                    new BaseCustomUIPanelPlugin());
            frame.addComponent(target).inTL(x(layout) - frame.getPosition().getX() - DIAMETER * 0.5f,
                    frame.getPosition().getY() + frame.getPosition().getHeight()
                            - y(layout, i) - DIAMETER * 0.5f);
            components.add(target);
            tips.addTooltipTo(new BaseTooltipCreator() {
                @Override public float getTooltipWidth(Object param) { return 240f; }
                @Override public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object param) {
                    explain(tooltip);
                }
            }, target, TooltipMakerAPI.TooltipLocation.RIGHT, false);
        }
    }

    private static void explain(TooltipMakerAPI tooltip) {
        tooltip.addPara(EXPLANATION, 0f, Misc.getHighlightColor(), "scarred", "easier", "quality");
    }

    public static float x(FishingMinigameLayout layout) {
        return layout.panelX + DIAMETER * 0.5f * STAMP_SCALE;
    }

    public static float y(FishingMinigameLayout layout, int index) {
        return layout.meterY + layout.meterHeight - DIAMETER * 0.5f - index * SPACING;
    }

    public void render(FishingMinigameLayout layout, float alphaMult) {
        if (disposed || count == 0) return;
        if (icon == null) icon = SpriteLoader.getSprite("shop_harpoon");
        Color fill = failed ? Misc.getDarkPlayerColor() : Color.DARK_GRAY;
        Color rim = failed ? Misc.getBasePlayerColor() : Color.GRAY;
        Color ink = failed ? Misc.getTextColor() : Color.LIGHT_GRAY;
        float visibility = failed ? 1f : 0.35f;
        for (int i = 0; i < count; i++) {
            float phase = added && i == count - 1 ? Math.min(1f, elapsed / STAMP_TIME) : 1f;
            float scale = 1f + (STAMP_SCALE - 1f) * (float) Math.pow(1f - phase, 3);
            float alpha = alphaMult * visibility * Math.min(1f, phase * 4f);
            float x = x(layout);
            float y = y(layout, i);
            float radius = DIAMETER * 0.5f * scale;
            Disc.draw(x, y, radius, fill, 0.7f * alpha, 0.7f * alpha, false);
            Disc.drawOutline(x, y, radius, rim, 0.65f * alpha, 1f);
            float width = icon.getWidth();
            float height = icon.getHeight();
            Color color = icon.getColor();
            float oldAlpha = icon.getAlphaMult();
            float fit = DIAMETER * 0.62f * scale / Math.max(width, height);
            icon.setSize(width * fit, height * fit);
            icon.setNormalBlend();
            icon.setColor(ink);
            icon.setAlphaMult(0.65f * alpha);
            icon.renderAtCenter(x, y);
            icon.setSize(width, height);
            icon.setColor(color);
            icon.setAlphaMult(oldAlpha);
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (frame != null) {
            if (notice != null) frame.removeComponent(notice);
            for (UIPanelAPI panel : components) frame.removeComponent(panel);
        }
        notice = null;
        components.clear();
        frame = null;
    }
}
