package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.List;

/**
 * One round fish holder, wherever a screen lines fish up in circles - the intel Planets panel
 * and the system view's sidebar both. A dark disc under the rarity's ring and the face: the art
 * for a landed species, the generic mark for one known only from survey data, and a bare
 * question mark - no colour, no name, no answers - for one the player has never heard of.
 * F2 over a named holder opens its codex page, the same key every fish row answers.
 */
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

        //the ring wears the rarity; a species with no name yet wears no colour either
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

        String iconPath = FishLog.isCaught(spec.id)
                ? FishCodex.getIcon(spec) : FishConstants.ITEM_ICON_FALLBACK;

        SpriteAPI icon = SpriteLoader.loadSprite(iconPath);
        if (icon != null) {
            float iconSize = pos.getWidth() * ICON_SHARE;
            icon.setSize(iconSize, iconSize);
            icon.setColor(Color.WHITE);
            icon.setNormalBlend();
            icon.setAlphaMult(alphaMult);
            icon.renderAtCenter(Math.round(x), Math.round(y));
        }
    }

    /** The codex key, the same one the sidebar's rows answer. */
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
