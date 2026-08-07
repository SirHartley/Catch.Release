package catchrelease.campaign.fish.map;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.shop.ShopUi;
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
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The map panes' shared widgets, lifted out of the hyperspace sidebar so the planner could wear
 * the same face instead of painting an imitation: the type chip, the text button, and the
 * hand-worked ghost text a bare {@link TextFieldAPI} does not provide.
 */
public final class PaneWidgets {

    /** The chips' own face: category art does not exist yet, and a stand-in says so honestly. */
    public static final String CHIP_ICON_FONT = "graphics/fonts/victor10.fnt";

    public static final String CLICK_SOUND = "ui_button_pressed";

    protected static transient LazyFont tinyFont;
    protected static transient boolean tinyChecked = false;

    private PaneWidgets() {
    }

    /** The smallest hand the game writes in, for labels that were shouting at chip size. */
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

    /**
     * Works a text field's placeholder by hand - there is no change callback or ghost text in
     * the API. Call every frame; returns what the player actually typed, with the ghost never
     * leaking through as a search term.
     */
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

    /** One type as a chip: a placeholder mark (category art doesn't exist yet) over the name,
     *  lit in the type's colour while shown. Reads the filter, never writes it - the toggle is
     *  the owner's to make. */
    public static class Chip extends BaseCustomUIPanelPlugin {

        public static final float ICON_SIZE = 16f;

        protected final FishType type;
        protected final FishPresence.Filter filter;
        protected final Consumer<FishType> onToggle;

        protected PositionAPI chipPos;

        protected transient LazyFont.DrawableString text;
        protected transient SpriteAPI icon;
        protected transient boolean iconChecked;

        public Chip(FishType type, FishPresence.Filter filter, Consumer<FishType> onToggle) {
            this.type = type;
            this.filter = filter;
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

            boolean on = filter.types.contains(type);
            boolean hovered = ShopUi.contains(x, y, w, h,
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

            if (on) {
                ShopUi.drawQuad(x, y, w, h, type.color, (hovered ? 0.5f : 0.35f) * alphaMult);
                ShopUi.drawQuad(x, y, w, 2f, type.color, 0.95f * alphaMult);
            } else {
                //off is absence, not another colour - dark field with just the underline remembering
                ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(),
                        (hovered ? 0.35f : 0.18f) * alphaMult);
                ShopUi.drawQuad(x, y, w, 2f, type.color, 0.35f * alphaMult);
            }

            SpriteAPI face = getIcon();
            if (face != null) {
                float scale = Math.min(ICON_SIZE / face.getWidth(), ICON_SIZE / face.getHeight());

                face.setSize(face.getWidth() * scale, face.getHeight() * scale);
                face.setNormalBlend();
                face.setAlphaMult((on ? 1f : 0.55f) * alphaMult);
                face.renderAtCenter(Math.round(x + w * 0.5f),
                        Math.round(y + h - 3f - ICON_SIZE * 0.5f));
            }

            //the smallest native size there is: a chip is a label, not a heading
            LazyFont tiny = getTinyFont();
            if (tiny == null) return;

            if (text == null) {
                text = ShopUi.createText(tiny, type.label);
                text.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            Color color = on ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();
            text.setBaseColor(ShopUi.withAlpha(color, alphaMult));

            //held two pixels off the underline, which the label used to stand right on
            text.draw(Math.round(x + (w - text.getWidth()) * 0.5f),
                    Math.round(y + 4f + text.getHeight()));
        }

        protected SpriteAPI getIcon() {
            if (iconChecked) return icon;
            iconChecked = true;

            try {
                icon = Global.getSettings().getSprite(ModPlugin.MOD_ID, "placeholder");
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
                onToggle.accept(type);

                return;
            }
        }
    }

    /**
     * A text button in the pane's manner: dark field, centred small-font label, brighter under
     * the mouse. Label and liveness are read every frame, so a button whose words carry a count
     * stays truthful and one whose job is spent goes quiet on its own.
     */
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
}
