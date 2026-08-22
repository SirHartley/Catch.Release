package catchrelease.campaign.fish.map;

import catchrelease.ui.FishIcons;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.ui.ShopUi;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.List;

public class FishHolderPlugin extends BaseCustomUIPanelPlugin {

    public static final float ICON_SHARE = 0.66f;

    protected final FishSpec spec;
    protected PositionAPI pos;

    public FishHolderPlugin(FishSpec spec) {
        this.spec = spec;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getCenterX();
        float y = pos.getCenterY();
        float radius = pos.getWidth() * 0.5f;

        Color ring = spec == null ? Misc.getDarkPlayerColor() : spec.rarity.color;

        Disc.draw(x, y, radius, Color.BLACK, 0.8f * alphaMult, 0.8f * alphaMult, false);
        Disc.drawOutline(x, y, radius, ring, 0.9f * alphaMult, 1.2f);

        if (spec == null) {
            LazyFont small = ShopUi.getSmallFont();
            if (small != null) {
                LazyFont.DrawableString mark = small.createText("?",
                        Misc.getGrayColor(), small.getBaseHeight());
                mark.draw(Math.round(x - mark.getWidth() * 0.5f),
                        Math.round(y + mark.getHeight() * 0.5f));
            }
            return;
        }

        // the art as painted once landed, its rimmed silhouette while only surveyed
        FishIcons.draw(spec, x, y, pos.getWidth() * ICON_SHARE, alphaMult);

        if (ShopMarks.isWanted(spec)) {
            float off = radius * 0.707f;
            ShopMarks.drawDot(x + off, y - off, ShopMarks.DOT_RADIUS, alphaMult);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (pos == null || spec == null) return;

        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            if (!event.isKeyDownEvent() || event.getEventValue() != Keyboard.KEY_F2) continue;

            if (!ShopUi.contains(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                    Global.getSettings().getMouseX(), Global.getSettings().getMouseY())) {
                continue;
            }

            event.consume();
            FishCodex.show(spec.id);
            return;
        }
    }
}
