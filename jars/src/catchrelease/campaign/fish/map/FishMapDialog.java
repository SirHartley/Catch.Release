package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.shop.ShopUi;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The catch map in a dialog off an ability press - the same holding pen the outfitter lives in,
 * and the fallback door to the map while the campaign-UI tab is the new way in.
 * <p>
 * Everything the map is lives in {@link FishMapView}; this class is only the frame - the dialog
 * plumbing, the title band, and escape to leave. The view here is this dialog's own rather than
 * shared with the tab's, so the two doors keep separate cameras, which is the less surprising of
 * the two behaviours for a fallback.
 */
public class FishMapDialog implements InteractionDialogPlugin {

    public static final float WIDTH = 980f;
    public static final float HEIGHT = 640f;

    public static final float PAD = 12f;
    public static final float HEADER_HEIGHT = 38f;

    /** Opens the map, if the UI will have it. */
    public static boolean open() {
        return Global.getSector().getCampaignUI()
                .showInteractionDialog(new FishMapDialog(), Global.getSector().getPlayerFleet());
    }

    protected InteractionDialogAPI dialog;
    protected Delegate delegate;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        dialog.setBackgroundDimAmount(0.6f);

        delegate = new Delegate();

        dialog.showCustomVisualDialog(WIDTH, HEIGHT, delegate);
    }

    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;

        protected final FishMapView view = new FishMapView();

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            CustomPanelAPI header = panel.createCustomPanel(WIDTH - PAD * 2f, HEADER_HEIGHT,
                    new HeaderPlugin());
            panel.addComponent(header).inTL(PAD, PAD);

            view.mount(panel, PAD, PAD + HEADER_HEIGHT + 8f, WIDTH - PAD * 2f,
                    HEIGHT - PAD * 2f - HEADER_HEIGHT - 8f);
        }

        @Override
        public void advance(float amount) {
            view.advance(amount);
        }

        @Override
        public CustomUIPanelPlugin getCustomPanelPlugin() {
            return this;
        }

        @Override
        public float getNoiseAlpha() {
            return 0.05f;
        }

        @Override
        public void reportDismissed(int option) {
            if (dialog != null) dialog.dismiss();
        }

        /** Escape closes it. There is nothing to lose by leaving, so nothing to confirm. */
        @Override
        public void processInput(List<InputEventAPI> events) {
            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;
                if (!event.isKeyDownEvent()) continue;
                if (event.getEventValue() != Keyboard.KEY_ESCAPE) continue;

                event.consume();
                if (callbacks != null) callbacks.dismissDialog();
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }

        @Override
        public void positionChanged(PositionAPI position) {
        }

        @Override
        public void renderBelow(float alphaMult) {
        }

        @Override
        public void render(float alphaMult) {
        }
    }

    /** The band across the top: the title in the shop's hand, on the shop's rule. */
    protected static class HeaderPlugin extends BaseCustomUIPanelPlugin {

        protected PositionAPI pos;
        protected transient LazyFont.DrawableString title;

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            LazyFont font = ShopUi.getTitleFont();
            if (font != null) {
                if (title == null) {
                    title = ShopUi.createText(font, "CATCH LOCATIONS");
                    title.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
                }

                title.setBaseColor(ShopUi.withAlpha(Misc.getBrightPlayerColor(), alphaMult));
                title.draw(Math.round(pos.getX() + 2f),
                        Math.round(pos.getY() + pos.getHeight() * 0.5f + title.getHeight() * 0.5f));
            }

            ShopUi.drawQuad(pos.getX(), pos.getY(), pos.getWidth(), 1f,
                    Misc.getBrightPlayerColor(), 0.35f * alphaMult);
        }
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return new HashMap<>();
    }
}
