package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.rendering.helper.Disc;
import catchrelease.ui.ListRow;
import catchrelease.ui.ShopUi;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

public abstract class FishListRow extends ListRow {

    public static final float PAD_SIDE = 8f;
    public static final float MARK_RADIUS = 3.5f;
    public static final float MARK_GAP = 7f;

    protected final FishSpec spec;
    protected transient LazyFont.DrawableString name;

    protected FishListRow(FishSpec spec) {
        this.spec = spec;
    }

    @Override
    protected Color getAccentColor() {
        return spec.rarity.color;
    }

    @Override
    protected void renderContent(float x, float y, float width, float height,
                                 boolean selected, boolean hovered, float alphaMult) {
        Color chrome = selected || hovered ? Misc.getBrightPlayerColor() : Misc.getBasePlayerColor();

        // filled = caught, hollow = range-only; a shape rather than a shade, since every shade here already means selection or rarity
        boolean caught = FishLog.isCaught(spec.id);
        float markX = x + ACCENT_WIDTH + PAD_SIDE + MARK_RADIUS;
        float markY = y + height * 0.5f;

        if (caught) {
            Disc.draw(markX, markY, MARK_RADIUS, chrome, 0.9f * alphaMult, 0.9f * alphaMult, false);
        }

        Disc.drawOutline(markX, markY, MARK_RADIUS, chrome, 0.9f * alphaMult, 1.5f);

        LazyFont body = ShopUi.getBodyFont();
        if (body != null) {
            if (name == null) {
                name = ShopUi.createText(body, spec.getDisplayName());
                name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            name.setBaseColor(ShopUi.withAlpha(chrome, alphaMult));
            name.draw(Math.round(x + ACCENT_WIDTH + PAD_SIDE + MARK_RADIUS * 2f + MARK_GAP),
                    Math.round(y + height * 0.5f + name.getHeight() * 0.5f));
        }

        // the wanted dot at the row's right end, centred on the row's own midline - asked of everything that wants a fish, errands included, not the shopping list alone
        boolean marked = ShopMarks.isWanted(spec);

        renderTag(x, y, width, height, marked, alphaMult);

        if (marked) {
            ShopMarks.drawDot(x + width - 8f, y + height * 0.5f,
                    ShopMarks.DOT_RADIUS - 0.5f, alphaMult);
        }
    }

    protected void renderTag(float x, float y, float width, float height, boolean marked,
                             float alphaMult) {
    }

    @Override
    protected boolean handleKey(InputEventAPI event) {
        if (event.getEventValue() != Keyboard.KEY_F2) return false;
        if (!contains(Global.getSettings().getMouseX(), Global.getSettings().getMouseY())) {
            return false;
        }

        event.consume();
        FishCodex.show(spec.id);

        return true;
    }
}
