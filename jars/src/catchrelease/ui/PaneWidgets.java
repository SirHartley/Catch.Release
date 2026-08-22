package catchrelease.ui;

import catchrelease.helper.loading.SpriteLoader;
import catchrelease.ui.ShopUi;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

public final class PaneWidgets {

    public static final String CHIP_ICON_FONT = "graphics/fonts/victor10.fnt";
    public static final String CLICK_SOUND = "ui_button_pressed";

    protected static transient LazyFont tinyFont;
    protected static transient boolean tinyChecked = false;

    public static class Chip extends BaseCustomUIPanelPlugin {

        public static final float ICON_SIZE = 16f;

        protected final String label;
        protected final Color color;
        protected final String iconId;

        protected final Supplier<Boolean> on;
        protected final Runnable onToggle;
        protected PositionAPI chipPos;
        protected transient LazyFont.DrawableString text;
        protected transient SpriteAPI icon;
        protected transient boolean iconChecked;

        public Chip(String label, Color color, String iconId,
                    Supplier<Boolean> on, Runnable onToggle) {
            this.label = label;
            this.color = color;
            this.iconId = iconId;
            this.on = on;
            this.onToggle = onToggle;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            chipPos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (chipPos == null || alphaMult <= 0f) return;

            float x = chipPos.getX();
            float y = chipPos.getY();
            float w = chipPos.getWidth();
            float h = chipPos.getHeight();

            boolean on = this.on.get();
            boolean hovered = ShopUi.contains(x, y, w, h,
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            if (on) {
                ShopUi.drawQuad(x, y, w, h, color, (hovered ? 0.5f : 0.35f) * alphaMult);
                ShopUi.drawQuad(x, y, w, 2f, color, 0.95f * alphaMult);
            } else {
                // off is absence, not another colour - dark field with just the underline remembering
                ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(),
                        (hovered ? 0.35f : 0.18f) * alphaMult);
                ShopUi.drawQuad(x, y, w, 2f, color, 0.35f * alphaMult);
            }

            Color color = on ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

            SpriteAPI face = getIcon();
            if (face != null) {
                float scale = Math.min(ICON_SIZE / face.getWidth(), ICON_SIZE / face.getHeight());

                face.setSize(face.getWidth() * scale, face.getHeight() * scale);
                face.setNormalBlend();
                face.setAlphaMult((on ? 1f : 0.55f) * alphaMult);
                face.setColor(color);
                face.renderAtCenter(Math.round(x + w * 0.5f),
                        Math.round(y + h - 3f - ICON_SIZE * 0.5f));
            }

            // the smallest native size there is: a chip is a label, not a heading
            LazyFont tiny = getTinyFont();
            if (tiny == null) return;

            if (text == null) {
                text = ShopUi.createText(tiny, label);
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            text.setBaseColor(ShopUi.withAlpha(color, alphaMult));

            text.draw(Math.round(x + (w - text.getWidth()) * 0.5f),
                    Math.round(y + 4f + text.getHeight()));
        }

        protected SpriteAPI getIcon() {
            if (iconChecked) return icon;
            iconChecked = true;

            if (iconId == null || iconId.isEmpty()) return null;

            try {
                icon = SpriteLoader.getSprite(iconId);
            } catch (Exception e) {
                icon = null;
            }

            return icon;
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (chipPos == null) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed() || !event.isLMBDownEvent()) continue;
                if (!ShopUi.contains(chipPos.getX(), chipPos.getY(), chipPos.getWidth(),
                        chipPos.getHeight(), event.getX(), event.getY())) {
                    continue;
                }

                event.consume();
                Global.getSoundPlayer().playUISound(CLICK_SOUND, 1f, 1f);
                onToggle.run();

                return;
            }
        }
    }

    public static class Note extends BaseCustomUIPanelPlugin {

        protected final String text;
        protected PositionAPI notePos;

        public Note(String text) {
            this.text = text;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            notePos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (notePos == null || alphaMult <= 0f) return;

            drawNote(text, notePos.getX(), notePos.getY(),
                    notePos.getWidth(), notePos.getHeight(), alphaMult);
        }
    }

    public static class TitleRow extends BaseCustomUIPanelPlugin {

        protected final String label;
        protected PositionAPI titlePos;
        protected transient LazyFont.DrawableString text;

        public TitleRow(String label) {
            this.label = label;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            titlePos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (titlePos == null || alphaMult <= 0f) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            float x = titlePos.getX();
            float y = titlePos.getY();
            float h = titlePos.getHeight();

            if (text == null) {
                text = ShopUi.createText(small, label);
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            text.setBaseColor(ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult));
            text.draw(Math.round(x), Math.round(y + h * 0.5f + text.getHeight() * 0.5f));

            ShopUi.drawQuad(x, y, titlePos.getWidth(), 1f, Misc.getDarkPlayerColor(),
                    0.8f * alphaMult);
        }
    }

    public static class ListHeader extends BaseCustomUIPanelPlugin {

        protected final Supplier<String> label;
        protected PositionAPI headerPos;

        protected transient LazyFont.DrawableString text;
        protected transient String written;
        protected transient LazyFont.DrawableString help;

        public ListHeader(Supplier<String> label) {
            this.label = label;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            headerPos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (headerPos == null || alphaMult <= 0f) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            float x = headerPos.getX();
            float y = headerPos.getY();
            float w = headerPos.getWidth();
            float h = headerPos.getHeight();

            String wanted = label.get();

            if (text == null || !wanted.equals(written)) {
                written = wanted;
                text = ShopUi.createText(small, wanted);
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            text.setBaseColor(ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult));
            text.draw(Math.round(x), Math.round(y + h * 0.5f + text.getHeight() * 0.5f));

            if (help == null) {
                help = ShopUi.createText(small, "?");
                help.setAnchor(LazyFont.TextAnchor.TOP_RIGHT);
            }

            boolean hovered = ShopUi.contains(x + w - 2f - help.getWidth(), y,
                    help.getWidth() + 2f, h,
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            help.setBaseColor(ShopUi.withAlpha(
                    hovered ? Misc.getBrightPlayerColor() : Misc.getGrayColor(), alphaMult));
            help.draw(Math.round(x + w - 2f), Math.round(y + h * 0.5f + help.getHeight() * 0.5f));

            ShopUi.drawQuad(x, y, w, 1f, Misc.getDarkPlayerColor(), 0.8f * alphaMult);
        }
    }

    public static class HelpMark extends BaseCustomUIPanelPlugin {

        protected PositionAPI markPos;
        protected transient LazyFont.DrawableString mark;

        @Override
        public void positionChanged(PositionAPI position) {
            markPos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (markPos == null || alphaMult <= 0f) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            if (mark == null) {
                mark = ShopUi.createText(small, "?");
                mark.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            boolean hovered = ShopUi.contains(markPos.getX(), markPos.getY(),
                    markPos.getWidth(), markPos.getHeight(),
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            mark.setBaseColor(ShopUi.withAlpha(
                    hovered ? Misc.getBrightPlayerColor() : Misc.getGrayColor(), alphaMult));
            mark.draw(Math.round(markPos.getX() + (markPos.getWidth() - mark.getWidth()) * 0.5f),
                    Math.round(markPos.getY() + markPos.getHeight() * 0.5f
                            + mark.getHeight() * 0.5f));
        }
    }

    public static class TextButton extends BaseCustomUIPanelPlugin {

        protected final Supplier<String> label;
        protected final Supplier<Boolean> live;
        protected final Runnable onClick;
        protected PositionAPI buttonPos;

        protected transient LazyFont.DrawableString text;
        protected transient String written;

        public TextButton(Supplier<String> label, Supplier<Boolean> live, Runnable onClick) {
            this.label = label;
            this.live = live;
            this.onClick = onClick;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            buttonPos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (buttonPos == null || alphaMult <= 0f) return;

            LazyFont small = ShopUi.getSmallFont();
            if (small == null) return;

            float x = buttonPos.getX();
            float y = buttonPos.getY();
            float w = buttonPos.getWidth();
            float h = buttonPos.getHeight();

            boolean on = live.get();
            boolean hovered = on && ShopUi.contains(x, y, w, h,
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(),
                    (on ? (hovered ? 0.45f : 0.32f) : 0.12f) * alphaMult);

            String wanted = label.get();
            if (text == null || !wanted.equals(written)) {
                written = wanted;
                text = ShopUi.createText(small, wanted);
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            Color color = on
                    ? (hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor())
                    : Misc.getGrayColor();

            text.setBaseColor(ShopUi.withAlpha(color, (on ? 1f : 0.6f) * alphaMult));
            text.draw(Math.round(x + (w - text.getWidth()) * 0.5f),
                    Math.round(y + h * 0.5f + text.getHeight() * 0.5f));
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (buttonPos == null || !live.get()) return;

            for (InputEventAPI event : events) {
                if (event.isConsumed() || !event.isLMBDownEvent()) continue;
                if (!ShopUi.contains(buttonPos.getX(), buttonPos.getY(), buttonPos.getWidth(),
                        buttonPos.getHeight(), event.getX(), event.getY())) {
                    continue;
                }

                event.consume();
                Global.getSoundPlayer().playUISound(CLICK_SOUND, 1f, 1f);
                onClick.run();

                return;
            }
        }
    }

    private PaneWidgets() {
    }

    public static LazyFont getTinyFont() {
        if (tinyChecked) return tinyFont;
        tinyChecked = true;

        try {
            tinyFont = LazyFont.loadFont(CHIP_ICON_FONT);
        } catch (Exception e) {
            tinyFont = null;
        }

        return tinyFont;
    }

    public static String tendGhost(TextFieldAPI field, String ghost) {
        String text = field.getText();
        boolean focused = field.hasFocus();

        if (focused && ghost.equals(text)) {
            field.deleteAll(false);
            text = "";
        } else if (!focused && (text == null || text.isEmpty())) {
            field.setText(ghost);
            text = ghost;
        }

        return text == null || ghost.equals(text) ? "" : text;
    }

    public static void drawNote(String text, float x, float y, float width, float height,
                                float alphaMult) {
        LazyFont small = ShopUi.getSmallFont();
        if (small == null) return;

        LazyFont.DrawableString line = small.createText(text,
                ShopUi.withAlpha(Misc.getGrayColor(), alphaMult), small.getBaseHeight());
        line.draw(Math.round(x + (width - line.getWidth()) * 0.5f),
                Math.round(y + (height + line.getHeight()) * 0.5f));
    }
}
