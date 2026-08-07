package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishShopDialog;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Fisherman's chart counter: the survey data on sale, as a proper panel in the outfitter's own
 * dress.
 * <p>
 * Which shelf is being sold off, how it was rolled and when it comes back are all
 * {@link FishermanShelf}'s - this is the counter, not the stock room. All it needs to know is that
 * a boat has offers and that buying one takes it off whichever shelf it stood on.
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

    /** The undo button, lower left, standing only while there is a purchase to take back. */
    public static final float UNDO_WIDTH = 170f;
    public static final float UNDO_HEIGHT = 26f;
    public static final String SOUND_UNDONE = "ui_cancel_construction_or_upgrade_industry";

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

                if (lastPurchase != null && ShopUi.contains(pos.getX() + PAD, pos.getY() + PAD,
                        UNDO_WIDTH, UNDO_HEIGHT, event.getX(), event.getY())) {
                    event.consume();
                    undo();
                    continue;
                }

                int index = cardIndexAt(event.getX(), event.getY());
                if (index < 0) continue;

                event.consume();
                buy(index);
            }
        }

        /**
         * The last purchase, held so it can be taken back. The spend walks worst-first through
         * stacks and crates, so the receipt is the hold's whole fish inventory as it stood -
         * putting the price back means putting that picture back, not re-adding a count.
         */
        protected static class Receipt {
            String specId;
            int stockIndex;
            List<Object[]> fishAboard;
        }

        protected Receipt lastPurchase;

        protected void buy(int index) {
            List<FishermanShelf.SurveyOffer> offers =
                    FishermanShelf.getOffers(dialog.getInteractionTarget());
            if (index >= offers.size()) return;

            FishermanShelf.SurveyOffer offer = offers.get(index);
            if (FishCurrency.count(offer.costRarity) < offer.costCount) return;

            Receipt receipt = new Receipt();
            receipt.specId = offer.spec.id;
            receipt.stockIndex = Math.max(0,
                    FishermanShelf.getStock(dialog.getInteractionTarget()).indexOf(offer.spec.id));
            receipt.fishAboard = snapshotFish();

            if (!FishCurrency.spend(offer.costRarity, offer.costCount)) return;

            FishLog.unlockLocationData(offer.spec.id);
            FishermanShelf.take(dialog.getInteractionTarget(), offer.spec.id);

            lastPurchase = receipt;
            Global.getSoundPlayer().playUISound(FishShopDialog.SOUND_BOUGHT, 1f, 1f);
        }

        /** Every fish stack aboard - loose specimens and crates - as data plus count. */
        protected List<Object[]> snapshotFish() {
            List<Object[]> stacks = new ArrayList<>();

            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                if (data == null) continue;
                if (!FishItems.FISH.equals(data.getId()) && !FishItems.isContainer(data)) continue;

                stacks.add(new Object[]{data, (int) stack.getSize()});
            }

            return stacks;
        }

        /** The purchase taken back: fish restored as they were, chart relocked and reshelved. */
        protected void undo() {
            if (lastPurchase == null) return;

            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                if (data == null) continue;
                if (!FishItems.FISH.equals(data.getId()) && !FishItems.isContainer(data)) continue;

                cargo.removeItems(CargoItemType.SPECIAL, data, stack.getSize());
            }
            for (Object[] entry : lastPurchase.fishAboard) {
                cargo.addSpecial((SpecialItemData) entry[0], (Integer) entry[1]);
            }

            FishLog.relockLocationData(lastPurchase.specId);

            FishermanShelf.putBack(dialog.getInteractionTarget(), lastPurchase.specId,
                    lastPurchase.stockIndex);

            lastPurchase = null;
            Global.getSoundPlayer().playUISound(SOUND_UNDONE, 1f, 1f);
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

            //the way back out of a slip, standing only while there is one to take back -
            //drawn even over a bare shelf, since buying the last chart is how it empties
            if (lastPurchase != null) drawUndoButton(x + PAD, y + PAD, small, alphaMult);

            List<FishermanShelf.SurveyOffer> offers =
                    FishermanShelf.getOffers(dialog.getInteractionTarget());

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

            List<FishermanShelf.SurveyOffer> offers =
                    FishermanShelf.getOffers(dialog.getInteractionTarget());

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
        protected void drawCard(FishermanShelf.SurveyOffer offer, float x, float y, float w, float h,
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

        /** The undo button in the cards' own dress: dark seat, 1px frame, brighter under the mouse. */
        protected void drawUndoButton(float x, float y, LazyFont small, float alphaMult) {
            boolean hovered = ShopUi.contains(x, y, UNDO_WIDTH, UNDO_HEIGHT, mouseX, mouseY);

            ShopUi.drawQuad(x, y, UNDO_WIDTH, UNDO_HEIGHT, Misc.getDarkPlayerColor(),
                    (hovered ? 0.35f : 0.15f) * alphaMult);
            ShopUi.drawQuad(x, y + UNDO_HEIGHT - 1f, UNDO_WIDTH, 1f, Misc.getDarkPlayerColor(), alphaMult);
            ShopUi.drawQuad(x, y, UNDO_WIDTH, 1f, Misc.getDarkPlayerColor(), alphaMult);
            ShopUi.drawQuad(x, y, 1f, UNDO_HEIGHT, Misc.getDarkPlayerColor(), alphaMult);
            ShopUi.drawQuad(x + UNDO_WIDTH - 1f, y, 1f, UNDO_HEIGHT, Misc.getDarkPlayerColor(), alphaMult);

            LazyFont.DrawableString label = small.createText("Undo last purchase",
                    ShopUi.withAlpha(hovered ? Misc.getBasePlayerColor() : Misc.getTextColor(),
                            alphaMult), small.getBaseHeight());
            label.draw(Math.round(x + (UNDO_WIDTH - label.getWidth()) * 0.5f),
                    Math.round(y + (UNDO_HEIGHT + label.getHeight()) * 0.5f));
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
