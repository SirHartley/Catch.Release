package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishShopDialog;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Fisherman's chart counter: the survey data on sale this visit, as a proper panel in the
 * outfitter's own dress.
 * <p>
 * The shelf is rolled once per visit - up to {@link FishermanConstants#SURVEY_STOCK} species the
 * player has no data on, weighted so commons are likely and legendaries a long shot. As the
 * player's knowledge grows the common end of the pool empties, so a seasoned fisher is offered
 * rarer charts by the same roll. Sold data does not restock; the shelf refills when the next
 * boat does.
 * <p>
 * Each card is built around the species' art coloured down to a silhouette over the minigame's
 * fading colour disc - the shape of the thing is on offer, the look of it still has to be
 * caught. Everything is drawn by hand in {@link ShopUi}'s style, and closing hands the frame
 * back to the conversation it was opened from.
 */
public class FishermanSurveyDialog implements InteractionDialogPlugin {

    public static final float WIDTH = 700f;
    public static final float HEIGHT = 540f;

    public static final float PAD = 16f;
    public static final float HEADER_HEIGHT = 52f;

    public static final int COLUMNS = 3;
    public static final float CARD_GAP = 12f;
    public static final float CARD_HEIGHT = 200f;

    /** The silhouette's stage: the fading disc under it, and the art's box. */
    public static final float DISC_RADIUS = 56f;
    public static final float ART_SIZE = 78f;

    /** One species on offer, with the fish that pay for it. */
    public static class SurveyOffer {
        public FishSpec spec;
        public FishRarity costRarity;
        public int costCount;
    }

    //---------------------------------------------------------------- the shelf

    /**
     * This visit's stock, rolled on first ask and lived on the boat itself - the shelf dies
     * with the fleet, and the next boat rolls its own.
     */
    @SuppressWarnings("unchecked")
    public static List<String> getStock(SectorEntityToken fleet) {
        if (fleet == null) return new ArrayList<>();

        Object stored = fleet.getMemoryWithoutUpdate().get(FishermanConstants.SURVEY_STOCK_KEY);
        if (stored instanceof List) return (List<String>) stored;

        List<String> stock = rollStock();
        fleet.getMemoryWithoutUpdate().set(FishermanConstants.SURVEY_STOCK_KEY, stock);

        return stock;
    }

    /** The roll: unknown species only, weighted by rarity, drawn without replacement. */
    protected static List<String> rollStock() {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id)) continue;

            int rung = Math.min(spec.rarity.ordinal(),
                    FishermanConstants.SURVEY_RARITY_WEIGHTS.length - 1);
            picker.add(spec, FishermanConstants.SURVEY_RARITY_WEIGHTS[rung]);
        }

        List<String> stock = new ArrayList<>();
        while (!picker.isEmpty() && stock.size() < FishermanConstants.SURVEY_STOCK) {
            stock.add(picker.pickAndRemove().id);
        }

        return stock;
    }

    /** The shelf as offers, pruned of anything learned since it was rolled. */
    public static List<SurveyOffer> getOffers(SectorEntityToken fleet) {
        List<SurveyOffer> offers = new ArrayList<>();
        List<String> stock = getStock(fleet);

        stock.removeIf(id -> FishLog.isCaught(id) || FishLog.isLocationDataUnlocked(id));

        for (String id : stock) {
            FishSpec spec = FishSpecLoader.getFishSpec(id);
            if (spec == null) continue;

            SurveyOffer offer = new SurveyOffer();
            offer.spec = spec;

            int rung = spec.rarity.ordinal();
            offer.costRarity = rung == 0 ? FishRarity.COMMON : FishRarity.values()[rung - 1];
            offer.costCount = rung == 0 ? 1 : FishermanConstants.SURVEY_COST;

            offers.add(offer);
        }

        return offers;
    }

    //---------------------------------------------------------------- the panel

    protected InteractionDialogAPI dialog;
    protected final FishShopDialog.OnClose onClose;

    public FishermanSurveyDialog(FishShopDialog.OnClose onClose) {
        this.onClose = onClose;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        dialog.setBackgroundDimAmount(0.6f);

        dialog.showCustomVisualDialog(WIDTH, HEIGHT, new Delegate());
    }

    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;

        protected PositionAPI pos;

        protected float mouseX = -1f;
        protected float mouseY = -1f;

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;
        }

        @Override
        public CustomUIPanelPlugin getCustomPanelPlugin() {
            return this;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public float getNoiseAlpha() {
            return 0f;
        }

        @Override
        public void advance(float amount) {
        }

        /** Back to the boat - the shop's own hand-back, reused word for word. */
        @Override
        public void reportDismissed(int option) {
            if (dialog == null) return;

            if (onClose == null) {
                dialog.dismiss();
                return;
            }

            onClose.onShopClosed(dialog);
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;

                if (event.isMouseMoveEvent() || event.isMouseEvent()) {
                    mouseX = event.getX();
                    mouseY = event.getY();
                }

                if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                    event.consume();
                    if (callbacks != null) callbacks.dismissDialog();
                    continue;
                }

                if (!event.isLMBDownEvent()) continue;

                int index = cardIndexAt(event.getX(), event.getY());
                if (index < 0) continue;

                event.consume();
                buy(index);
            }
        }

        protected void buy(int index) {
            List<SurveyOffer> offers = getOffers(dialog.getInteractionTarget());
            if (index >= offers.size()) return;

            SurveyOffer offer = offers.get(index);
            if (FishCurrency.count(offer.costRarity) < offer.costCount) return;
            if (!FishCurrency.spend(offer.costRarity, offer.costCount)) return;

            FishLog.unlockLocationData(offer.spec.id);
            getStock(dialog.getInteractionTarget()).remove(offer.spec.id);

            Global.getSoundPlayer().playUISound(FishShopDialog.SOUND_BOUGHT, 1f, 1f);
        }

        //---------------------------------------------------------------- drawing

        @Override
        public void render(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            LazyFont title = ShopUi.getTitleFont();
            LazyFont small = ShopUi.getSmallFont();
            if (title == null || small == null) return;

            float x = pos.getX();
            float y = pos.getY();
            float w = pos.getWidth();
            float h = pos.getHeight();

            //the outfitter's own dressing
            ShopUi.dress(x, y, w, h, alphaMult);

            LazyFont.DrawableString heading = title.createText("SURVEY DATA",
                    ShopUi.withAlpha(Misc.getBasePlayerColor(), alphaMult),
                    title.getBaseHeight());
            heading.draw(Math.round(x + PAD), Math.round(y + h - PAD));

            LazyFont.DrawableString sub = small.createText(
                    "Charts for charts - what is on the shelf is on it until the boat leaves."
                            + " Escape closes.",
                    ShopUi.withAlpha(Misc.getGrayColor(), alphaMult), small.getBaseHeight());
            sub.draw(Math.round(x + PAD),
                    Math.round(y + h - PAD - heading.getHeight() - 4f));

            List<SurveyOffer> offers = getOffers(dialog.getInteractionTarget());

            if (offers.isEmpty()) {
                LazyFont.DrawableString empty = small.createText(
                        "Nothing left on the shelf this visit.",
                        ShopUi.withAlpha(Misc.getGrayColor(), alphaMult), small.getBaseHeight());
                empty.draw(Math.round(x + (w - empty.getWidth()) * 0.5f),
                        Math.round(y + h * 0.5f));
                return;
            }

            for (int i = 0; i < offers.size(); i++) {
                float[] card = cardRect(i);
                drawCard(offers.get(i), card[0], card[1], card[2], card[3], small, alphaMult);
            }
        }

        /** Card rectangle by index, {x, y, width, height} - grid under the header. */
        protected float[] cardRect(int index) {
            float innerWidth = pos.getWidth() - PAD * 2f;
            float cardWidth = (innerWidth - CARD_GAP * (COLUMNS - 1)) / COLUMNS;

            int column = index % COLUMNS;
            int row = index / COLUMNS;

            float x = pos.getX() + PAD + column * (cardWidth + CARD_GAP);
            float top = pos.getY() + pos.getHeight() - HEADER_HEIGHT - PAD
                    - row * (CARD_HEIGHT + CARD_GAP);

            return new float[]{x, top - CARD_HEIGHT, cardWidth, CARD_HEIGHT};
        }

        protected int cardIndexAt(float x, float y) {
            if (pos == null) return -1;

            List<SurveyOffer> offers = getOffers(dialog.getInteractionTarget());

            for (int i = 0; i < offers.size(); i++) {
                float[] card = cardRect(i);

                if (x >= card[0] && x <= card[0] + card[2]
                        && y >= card[1] && y <= card[1] + card[3]) {
                    return i;
                }
            }

            return -1;
        }

        /** One chart for sale: the silhouette on its fading disc, the name, the price. */
        protected void drawCard(SurveyOffer offer, float x, float y, float w, float h,
                                LazyFont small, float alphaMult) {

            boolean afford = FishCurrency.count(offer.costRarity) >= offer.costCount;
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

            ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(),
                    (hovered && afford ? 0.35f : 0.15f) * alphaMult);
            ShopUi.drawQuad(x, y + h - 1f, w, 1f, Misc.getDarkPlayerColor(), alphaMult);
            ShopUi.drawQuad(x, y, w, 1f, Misc.getDarkPlayerColor(), alphaMult);
            ShopUi.drawQuad(x, y, 1f, h, Misc.getDarkPlayerColor(), alphaMult);
            ShopUi.drawQuad(x + w - 1f, y, 1f, h, Misc.getDarkPlayerColor(), alphaMult);

            float cx = x + w * 0.5f;
            float discY = y + h - PAD - DISC_RADIUS;

            //the minigame result box's stage: the rarity's colour fading out from the centre
            Disc.draw(cx, discY, DISC_RADIUS, offer.spec.rarity.color,
                    0.35f * alphaMult, 0f, true);

            SpriteAPI art = SpriteLoader.loadSprite(offer.spec.icon);
            if (art != null && art.getWidth() > 0f) {
                float scale = Math.min(ART_SIZE / art.getWidth(), ART_SIZE / art.getHeight());

                art.setSize(art.getWidth() * scale, art.getHeight() * scale);

                //the shape is what is for sale; the look of it still has to be caught
                art.setColor(Color.BLACK);
                art.setNormalBlend();
                art.setAlphaMult(alphaMult);
                art.renderAtCenter(Math.round(cx), Math.round(discY));
            }

            LazyFont.DrawableString name = small.createText(offer.spec.getDisplayName(),
                    ShopUi.withAlpha(offer.spec.rarity.color, alphaMult),
                    small.getBaseHeight(), w - 12f);
            name.draw(Math.round(cx - name.getWidth() * 0.5f),
                    Math.round(discY - DISC_RADIUS - 8f));

            int have = FishCurrency.count(offer.costRarity);
            String cost = offer.costCount + " "
                    + offer.costRarity.name().toLowerCase()
                    + (offer.costCount == 1 ? " specimen" : " specimens")
                    + " (have " + have + ")";

            LazyFont.DrawableString price = small.createText(cost,
                    ShopUi.withAlpha(afford ? Misc.getTextColor()
                            : Misc.getNegativeHighlightColor(), alphaMult),
                    small.getBaseHeight(), w - 12f);
            price.draw(Math.round(cx - price.getWidth() * 0.5f), Math.round(y + PAD + 14f));
        }

        @Override
        public void renderBelow(float alphaMult) {
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }
    }

    //---------------------------------------------------------------- plumbing

    @Override
    public void optionSelected(String optionText, Object optionData) {
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
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
        return null;
    }
}
