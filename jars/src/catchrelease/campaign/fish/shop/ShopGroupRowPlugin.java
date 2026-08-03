package catchrelease.campaign.fish.shop;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;

/**
 * A shelf label between runs of rows: the group's name and a rule under it, nothing to click.
 */
public class ShopGroupRowPlugin extends BaseCustomUIPanelPlugin {

    protected final ShopGroup group;
    protected final ShopRowPlugin.Host host;

    protected PositionAPI pos;
    protected transient LazyFont.DrawableString label;

    public ShopGroupRowPlugin(ShopGroup group, ShopRowPlugin.Host host) {
        this.group = group;
        this.host = host;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        PositionAPI view = host.getListViewport();
        if (view == null) return;

        float x = pos.getX();
        float y = pos.getY();
        float width = pos.getWidth();
        float height = pos.getHeight();

        if (y + height < view.getY() || y > view.getY() + view.getHeight()) return;

        ShopUi.startClip(view.getX(), view.getY(), view.getWidth(), view.getHeight());

        LazyFont font = ShopUi.getSmallFont();
        if (font != null) {
            if (label == null) {
                label = font.createText(group.title.toUpperCase(), Color.WHITE, 12f);
                label.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            }

            //low in its band, so the spare room reads as space above the group rather than below the label
            label.setBaseColor(ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult));
            label.draw(x + 2f, y + label.getHeight() + 6f);
        }

        //the rule the label sits on
        ShopUi.drawQuad(x, y + 2f, width, 1f, Misc.getBrightPlayerColor(), 0.35f * alphaMult);

        ShopUi.endClip();
    }
}
