package catchrelease.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

public final class UiKit {

    public interface Painter {

        void paint(PositionAPI pos, float alpha);
    }

    public interface Content {

        void create(TooltipMakerAPI tooltip);
    }

    public static final float HEADING_HEIGHT = 18f;
    public static final float LINE_HEIGHT = 20f;
    public static final float BUTTON_HEIGHT = 24f;
    public static final float HELP_SIZE = 20f;
    public static final float TOOLTIP_WIDTH = 360f;
    // a tooltip element places its content 5 units right of its own left edge
    public static final float INSET = 5f;

    public static final float ROW_GAP = 4f;
    public static final float GAP = 8f;
    public static final float SECTION_GAP = 16f;
    public static final float HEADING_GAP = 20f;

    public static final float TAB_ALPHA = 0.6f;
    public static final float RULE_ALPHA = 0.3f;

    public static final Color GREEN = new Color(50, 130, 0);
    public static final Color RED = new Color(160, 30, 20);
    public static final Color AMBER = new Color(200, 160, 10);

    public static final String BODY = Fonts.DEFAULT_SMALL;
    public static final String LABEL = Fonts.ORBITRON_12;

    public static class Canvas extends BaseCustomUIPanelPlugin {

        protected final Painter below;
        protected final Painter above;
        protected PositionAPI pos;

        public Canvas(Painter below, Painter above) {
            this.below = below;
            this.above = above;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
            if (pos != null && below != null) below.paint(pos, alphaMult);
        }

        @Override
        public void render(float alphaMult) {
            if (pos != null && above != null) above.paint(pos, alphaMult);
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (buttonId instanceof Runnable action) action.run();
        }
    }

    public static class Row {

        public final CustomPanelAPI panel;
        public final ButtonAPI area;

        public Row(CustomPanelAPI panel, ButtonAPI area) {
            this.panel = panel;
            this.area = area;
        }
    }

    private UiKit() {
    }

    public static Color base() {
        return Global.getSector().getPlayerFaction().getBaseUIColor();
    }

    public static Color dark() {
        return Global.getSector().getPlayerFaction().getDarkUIColor();
    }

    public static Color bright() {
        return Global.getSector().getPlayerFaction().getBrightUIColor();
    }

    public static Color fill(Color state) {
        return Misc.setAlpha(Misc.scaleColorOnly(state, 0.6f), 255);
    }

    public static CustomPanelAPI plain(CustomPanelAPI parent, float width, float height) {
        return parent.createCustomPanel(width, height, new Canvas(null, null));
    }

    public static CustomPanelAPI painted(CustomPanelAPI parent, float x, float y, float width,
                                         float height, Painter below, Painter above) {
        CustomPanelAPI panel = parent.createCustomPanel(width, height, new Canvas(below, above));
        parent.addComponent(panel).inTL(x, y);

        return panel;
    }

    public static void quad(float x, float y, float width, float height, Color color, float alpha) {
        ShopUi.drawQuad(Math.round(x), Math.round(y), Math.round(width), Math.round(height),
                color, alpha);
    }

    public static void outline(float x, float y, float width, float height, Color color,
                               float alpha) {
        quad(x, y, width, 1f, color, alpha);
        quad(x, y + height - 1f, width, 1f, color, alpha);
        quad(x, y + 1f, 1f, height - 2f, color, alpha);
        quad(x + width - 1f, y + 1f, 1f, height - 2f, color, alpha);
    }

    public static CustomPanelAPI border(CustomPanelAPI parent, float x, float y, float width,
                                        float height) {
        Color edge = dark();

        return painted(parent, x, y, width, height, (pos, alpha) -> outline(pos.getX(),
                pos.getY(), pos.getWidth(), pos.getHeight(), edge, alpha), null);
    }

    public static CustomPanelAPI sprite(CustomPanelAPI parent, float x, float y, float width,
                                        float height, SpriteAPI sprite, Color tint) {
        return painted(parent, x, y, width, height, null, (pos, alpha) -> {
            if (sprite == null || sprite.getWidth() <= 0f) return;

            sprite.setSize(width, height);
            sprite.setColor(tint);
            sprite.setNormalBlend();
            sprite.setAlphaMult(alpha);
            sprite.render(Math.round(pos.getX()), Math.round(pos.getY()));
        });
    }

    public static float width(String text, String font) {
        return Global.getSettings().computeStringWidth(text, font);
    }

    public static String fit(String text, String font, float width) {
        // the engine clips at the exact width, so keep two units of slack
        float max = width - 2f;
        if (width(text, font) <= max) return text;

        int low = 0;
        int high = text.length();

        while (low < high) {
            int mid = (low + high + 1) / 2;

            if (width(text.substring(0, mid).stripTrailing() + "...", font) <= max) low = mid;
            else high = mid - 1;
        }

        return low == 0 ? "..." : text.substring(0, low).stripTrailing() + "...";
    }

    public static LabelAPI text(CustomPanelAPI parent, float x, float y, float width, String text,
                                String font, Color color, Alignment align) {
        String shown = fit(text, font, width);

        LabelAPI label = Global.getSettings().createLabel(shown, font);
        label.setColor(color);
        label.setAlignment(align);

        parent.addComponent((UIComponentAPI) label)
                .setSize(width, label.computeTextHeight("Ag")).inTL(x, y);

        if (!shown.equals(text)) {
            tooltip(parent, (UIComponentAPI) label, TOOLTIP_WIDTH, TooltipLocation.BELOW, false,
                    tooltip -> tooltip.addPara(text.replace("%", "%%"), 0f));
        }

        return label;
    }

    public static LabelAPI textIn(CustomPanelAPI parent, float x, float y, float width,
                                  float height, String text, String font, Color color,
                                  Alignment align) {
        LabelAPI label = text(parent, x, y, width, text, font, color, align);

        // the engine puts the odd unit below centred text, so round down
        label.getPosition().inTL(x,
                y + (float) Math.floor((height - label.getPosition().getHeight()) / 2f));

        return label;
    }

    public static LabelAPI wrap(CustomPanelAPI parent, float x, float y, float width, String text,
                                Color color) {
        LabelAPI label = Global.getSettings().createLabel(text, BODY);
        label.setColor(color);

        PositionAPI pos = parent.addComponent((UIComponentAPI) label);
        label.autoSizeToWidth(width);
        pos.inTL(x, y);

        return label;
    }

    public static void tooltip(CustomPanelAPI parent, UIComponentAPI target, float width,
                               TooltipLocation location, boolean live, Content content) {
        if (target instanceof ButtonAPI button) button.setShowTooltipWhileInactive(true);

        // the element is only a factory for the tooltip and is never added
        parent.createUIElement(1f, 1f, false).addTooltipTo(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return width;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                                      Object tooltipParam) {
                // the default 0.85 lets the text of filled rows show through
                tooltip.setBgAlpha(1f);
                content.create(tooltip);
            }
        }, target, location, live);
    }

    public static void title(CustomPanelAPI parent, float width, String title, String sub,
                             Content help) {
        float barWidth = help == null ? width : width - HELP_SIZE - 6f;
        String shown = sub == null || sub.isEmpty() ? title : title + "   " + sub;

        TooltipMakerAPI element = parent.createUIElement(barWidth, HEADING_HEIGHT, false);
        // addSectionHeading draws ten units wider than the width it is given
        LabelAPI label = element.addSectionHeading(fit(shown, LABEL, barWidth - 16f), base(),
                dark(), Alignment.MID, barWidth - 10f, 0f);
        parent.addUIElement(element).inTL(0f, 0f);

        if (sub != null && !sub.isEmpty()) {
            label.highlightLast(sub);
            label.setHighlightColor(Misc.getGrayColor());
        }

        if (help == null) return;

        SpriteAPI icon = Global.getSettings().getSprite("fleetScreen", "icon_more_info");
        CustomPanelAPI spot = sprite(parent, width - HELP_SIZE, -1f, HELP_SIZE, HELP_SIZE, icon,
                base());

        tooltip(parent, spot, TOOLTIP_WIDTH, TooltipLocation.BELOW, false, help);
    }

    public static LabelAPI heading(CustomPanelAPI parent, float x, float y, float width,
                                   String title, String note) {
        Color fill = dark();

        // the vanilla heading has no inset for left aligned text, so the bar is painted here
        painted(parent, x, y, width, HEADING_HEIGHT, (pos, alpha) -> quad(pos.getX(), pos.getY(),
                pos.getWidth(), pos.getHeight(), fill, alpha), null);

        float noteWidth = 0f;

        if (note != null && !note.isEmpty()) {
            noteWidth = (float) Math.ceil(width(note, LABEL)) + 4f;

            textIn(parent, x + width - 8f - noteWidth, y, noteWidth, HEADING_HEIGHT, note, LABEL,
                    Misc.getGrayColor(), Alignment.RMID);
        }

        return textIn(parent, x + 8f, y, width - 24f - noteWidth, HEADING_HEIGHT, title, LABEL,
                base(), Alignment.LMID);
    }

    public static ButtonAPI button(CustomPanelAPI parent, float x, float y, float width,
                                   float height, String label, Color text, Color fill,
                                   CutStyle cut, Runnable action) {
        TooltipMakerAPI element = parent.createUIElement(width + INSET, height, false);
        ButtonAPI button = element.addButton(fit(label, LABEL, width - height / 2f), action, text,
                fill, Alignment.MID, cut, width, height, 0f);
        parent.addUIElement(element).inTL(x - INSET, y);

        if (action == null) button.setEnabled(false);

        return button;
    }

    public static ButtonAPI button(CustomPanelAPI parent, float x, float y, float width,
                                   String label, CutStyle cut, Runnable action) {
        return button(parent, x, y, width, BUTTON_HEIGHT, label, base(), dark(), cut, action);
    }

    public static ButtonAPI tab(CustomPanelAPI parent, float x, float y, float width, String label,
                                boolean selected, Runnable action) {
        return button(parent, x, y, width, BUTTON_HEIGHT, label, selected ? bright() : base(),
                selected ? Misc.setAlpha(dark(), 255) : Misc.scaleAlpha(dark(), TAB_ALPHA),
                CutStyle.TOP, action);
    }

    public static void rule(CustomPanelAPI parent, float x, float y, float width) {
        Color line = base();

        painted(parent, x, y, width, 1f, (pos, alpha) -> quad(pos.getX(), pos.getY(),
                pos.getWidth(), 1f, line, RULE_ALPHA * alpha), null);
    }

    public static void vrule(CustomPanelAPI parent, float x, float y, float height) {
        Color line = base();

        painted(parent, x, y, 1f, height, (pos, alpha) -> quad(pos.getX(), pos.getY(), 1f,
                pos.getHeight(), line, RULE_ALPHA * alpha), null);
    }

    // a value that ends at right with its icon gap units before it, centred in a line of the given height; returns the icon's left edge
    public static float iconValue(CustomPanelAPI parent, float right, float y, float height,
                                  SpriteAPI icon, Color tint, float iconSize, float gap,
                                  String value, Color color) {
        float valueWidth = (float) Math.ceil(width(value, BODY)) + 2f;
        float iconX = right - valueWidth - gap - iconSize;

        textIn(parent, right - valueWidth, y, valueWidth, height, value, BODY, color,
                Alignment.RMID);
        sprite(parent, iconX, y + (float) Math.floor((height - iconSize) / 2f), iconSize,
                iconSize, icon, tint);

        return iconX;
    }

    // a one line label that ends at right; returns its left edge
    public static float labelLeftOf(CustomPanelAPI parent, float right, float y, float height,
                                    String label, Color color) {
        float labelWidth = (float) Math.ceil(width(label, BODY)) + 2f;

        textIn(parent, right - labelWidth, y, labelWidth, height, label, BODY, color,
                Alignment.RMID);

        return right - labelWidth;
    }

    // a wrapped paragraph with %s values in the highlight colour; returns its height
    public static float para(CustomPanelAPI parent, float x, float y, float width, String format,
                             Color color, Color highlight, String... values) {
        TooltipMakerAPI element = parent.createUIElement(width + INSET, 0f, false);
        element.addPara(format, 0f, color, highlight, values);
        parent.addUIElement(element).inTL(x - INSET, y);

        return element.getHeightSoFar();
    }

    public static ButtonAPI checkbox(CustomPanelAPI parent, float x, float y, float width,
                                     String label, boolean on, Runnable action) {
        TooltipMakerAPI element = parent.createUIElement(width + INSET, LINE_HEIGHT, false);
        ButtonAPI box = element.addCheckbox(width, LINE_HEIGHT, label, action,
                ButtonAPI.UICheckboxSize.SMALL, 0f);
        parent.addUIElement(element).inTL(x - INSET, y);
        box.setChecked(on);

        return box;
    }

    public static Row row(CustomPanelAPI parent, float x, float y, float width, float height,
                          boolean selected, Runnable action) {
        CustomPanelAPI panel = painted(parent, x, y, width, height, null, null);

        // labels and painted panels on top never take the click, the area under them does
        TooltipMakerAPI element = panel.createUIElement(width + INSET, height, false);
        ButtonAPI area = element.addAreaCheckbox("", action, base(), dark(), bright(), width,
                height, 0f);
        panel.addUIElement(element).inTL(-INSET, 0f);
        area.setChecked(selected);

        return new Row(panel, area);
    }
}
