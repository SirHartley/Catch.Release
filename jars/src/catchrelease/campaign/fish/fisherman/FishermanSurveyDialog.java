package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.ui.FishIcons;
import catchrelease.ui.PaneWidgets;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishShopDialog;
import catchrelease.ui.ShopUi;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Fisherman's chart counter: the range data on sale, in the sidebar's own language.
 * <p>
 * Which shelf is being sold off, how it was rolled and when it comes back are all
 * {@link FishermanShelf}'s - this is the counter, not the stock room. All it needs to know is that
 * a boat has offers and that buying one takes it off whichever shelf it stood on.
 * <p>
 * Built from the map panes' parts rather than painted: a titled header with the way out and the
 * explaining on a {@code ?}, the charts as component cards in a grid - each the species'
 * {@link FishIcons} silhouette on the minigame's fading colour disc, wearing a chip's rarity
 * underline - and the undo as the shared text button, lower left, dim until there is a purchase
 * to take back. Closing hands the frame back to the conversation it was opened from.
 */
public class FishermanSurveyDialog implements InteractionDialogPlugin {

    public static final float WIDTH = 700f;
    public static final float HEIGHT = 540f;

    public static final float PAD = 16f;
    public static final float TITLE_HEIGHT = 20f;
    public static final float CLOSE_WIDTH = 20f;
    public static final float HELP_WIDTH = 20f;

    public static final int COLUMNS = 3;
    public static final float CARD_GAP = 12f;
    public static final float CARD_HEIGHT = 200f;

    /** The silhouette's stage: the fading disc under it, and the art's box. */
    public static final float DISC_RADIUS = 56f;
    public static final float ART_SIZE = 78f;

    /** The undo button, lower left, dim until there is a purchase to take back. */
    public static final float UNDO_WIDTH = 200f;
    public static final float UNDO_HEIGHT = 26f;

    /** The way out, bottom right - same size as the outfitter's. */
    public static final float LEAVE_WIDTH = 120f;
    public static final String SOUND_UNDONE = "ui_cancel_construction_or_upgrade_industry";

    //---------------------------------------------------------------- the panel

    protected InteractionDialogAPI dialog;
    protected final FishShopDialog.OnClose onClose;
    protected boolean closed;

    public FishermanSurveyDialog(FishShopDialog.OnClose onClose) {
        this.onClose = onClose;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        closed = false;

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        dialog.setBackgroundDimAmount(0.6f);

        //Custom visuals leave the interaction's option panel alone; clear it through the public
        //API immediately before opening this counter so no dialogue rows show under the cards.
        dialog.getOptionPanel().clearOptions();
        dialog.showCustomVisualDialog(WIDTH, HEIGHT, new Delegate());
    }

    /** Delivers the custom visual's dismissal once, regardless of which close control reached it. */
    protected void close() {
        if (closed || dialog == null) return;

        closed = true;

        if (onClose == null) {
            dialog.dismiss();
            return;
        }

        onClose.onShopClosed(dialog);
    }

    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;

        protected PositionAPI pos;

        /** The grid's components, tracked by what actually removes them, for the rebuild. */
        protected final List<UIComponentAPI> gridParts = new ArrayList<>();

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            buildHeader();
            buildFooter();
            rebuildGrid();
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

        /** The sidebar's dressing - this counter wears the same face as every other panel. */
        @Override
        public void renderBelow(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            ShopUi.drawPanel(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                    0.7f, alphaMult);
        }

        @Override
        public void render(float alphaMult) {
        }

        /** Back to the boat - the shop's own hand-back, reused word for word. */
        @Override
        public void reportDismissed(int option) {
            FishermanSurveyDialog.this.close();
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;

                if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                    event.consume();
                    if (callbacks != null) callbacks.dismissDialog();
                }
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }

        //---------------------------------------------------------------- building

        /** The title row: name, underline, the explaining on the ?, and the X out. */
        protected void buildHeader() {
            float innerWidth = WIDTH - PAD * 2f;
            TooltipMakerAPI header = panel.createUIElement(innerWidth, TITLE_HEIGHT, false);

            CustomPanelAPI titleRow = panel.createCustomPanel(innerWidth, TITLE_HEIGHT,
                    new PaneWidgets.TitleRow("RANGE DATA"));

            CustomPanelAPI help = panel.createCustomPanel(HELP_WIDTH, TITLE_HEIGHT,
                    new PaneWidgets.HelpMark());
            titleRow.addComponent(help).inTR(CLOSE_WIDTH + 4f, 0f);

            CustomPanelAPI close = panel.createCustomPanel(CLOSE_WIDTH, TITLE_HEIGHT,
                    new PaneWidgets.TextButton(() -> "X", () -> true,
                            () -> callbacks.dismissDialog()));
            titleRow.addComponent(close).inTR(0f, 0f);

            header.addCustom(titleRow, 0f);
            header.addTooltipTo(createLegendTooltip(), help, TooltipMakerAPI.TooltipLocation.BELOW);
            header.addTooltipTo(createSimpleTooltip(220f, "Back to the boat."),
                    close, TooltipMakerAPI.TooltipLocation.BELOW);

            panel.addUIElement(header).inTL(PAD, PAD);
        }

        /** The way back out of a slip: the shared text button, dim while there is nothing to undo. */
        protected void buildFooter() {
            TooltipMakerAPI footer = panel.createUIElement(UNDO_WIDTH, UNDO_HEIGHT, false);

            CustomPanelAPI undo = panel.createCustomPanel(UNDO_WIDTH, UNDO_HEIGHT,
                    new PaneWidgets.TextButton(() -> "UNDO LAST PURCHASE",
                            () -> lastPurchase != null, this::undoClicked));
            footer.addCustom(undo, 0f);
            footer.addTooltipToPrevious(createSimpleTooltip(260f,
                    "Takes back the last chart bought this visit - the fish paid for it return"
                            + " exactly as they were."),
                    TooltipMakerAPI.TooltipLocation.ABOVE);

            panel.addUIElement(footer).inTL(PAD, HEIGHT - PAD - UNDO_HEIGHT);

            //the way out, bottom right - the same door every panel in the mod has
            CustomPanelAPI leave = panel.createCustomPanel(LEAVE_WIDTH, UNDO_HEIGHT,
                    new PaneWidgets.TextButton(() -> "LEAVE", () -> true,
                            () -> callbacks.dismissDialog()));
            panel.addComponent(leave).inTL(WIDTH - PAD - LEAVE_WIDTH, HEIGHT - PAD - UNDO_HEIGHT);
        }

        /** The shelf as cards, three to a row - rebuilt whenever the stock changes. */
        protected void rebuildGrid() {
            for (UIComponentAPI part : gridParts) panel.removeComponent(part);
            gridParts.clear();

            float innerWidth = WIDTH - PAD * 2f;
            float gridHeight = HEIGHT - PAD * 2f - TITLE_HEIGHT - 8f - UNDO_HEIGHT - 8f;

            TooltipMakerAPI grid = panel.createUIElement(innerWidth, gridHeight, false);

            List<FishermanShelf.SurveyOffer> offers =
                    FishermanShelf.getOffers(dialog.getInteractionTarget());

            if (offers.isEmpty()) {
                CustomPanelAPI note = panel.createCustomPanel(innerWidth, TITLE_HEIGHT,
                        new PaneWidgets.Note("Nothing left on the shelf this visit."));
                grid.addCustom(note, gridHeight * 0.4f);
            }

            //floored to the pixel: a card on a fractional edge is a card with a soft edge
            float cardWidth = (float) Math.floor(
                    (innerWidth - CARD_GAP * (COLUMNS - 1)) / COLUMNS);

            for (int from = 0; from < offers.size(); from += COLUMNS) {
                CustomPanelAPI rank = panel.createCustomPanel(innerWidth, CARD_HEIGHT,
                        new com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin() {
                        });

                for (int i = from; i < Math.min(from + COLUMNS, offers.size()); i++) {
                    FishermanShelf.SurveyOffer offer = offers.get(i);

                    CustomPanelAPI card = panel.createCustomPanel(cardWidth, CARD_HEIGHT,
                            new CardPlugin(offer));

                    rank.addComponent(card).inTL((i - from) * (cardWidth + CARD_GAP), 0f);
                    grid.addTooltipTo(createCardTooltip(offer), card,
                            TooltipMakerAPI.TooltipLocation.BELOW);
                }

                grid.addCustom(rank, from == 0 ? 0f : CARD_GAP);
            }

            panel.addUIElement(grid).inTL(PAD, PAD + TITLE_HEIGHT + 8f);

            gridParts.add(grid.getExternalScroller() != null
                    ? (UIComponentAPI) grid.getExternalScroller() : grid);
        }

        // --- Tooltips, which is where all the explaining lives. ---

        protected TooltipMakerAPI.TooltipCreator createSimpleTooltip(float tooltipWidth, String text) {
            return new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return tooltipWidth;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara(text, 0f);
                }
            };
        }

        protected TooltipMakerAPI.TooltipCreator createLegendTooltip() {
            return new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return 320f;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara("Charts for charts", Misc.getBasePlayerColor(), 0f);

                    tooltip.addPara("Range data unlocks the habitat of the pattern on your map, allowing you to see its range and plot a course to catch it. " +
                            "It also unlocks the preliminary codex data, some behavioural information, and the presence indicator on the system map.", 8f);

                    tooltip.addPara("Restocks after a while. Encounters outside the core carry a unique inventory.", 8f);
                }
            };
        }

        /**
         * A chart's card, and no more than the counter already says - the species tooltip the
         * rest of the UI shares tells where a fish lives, which is exactly what is for sale here.
         */
        protected TooltipMakerAPI.TooltipCreator createCardTooltip(FishermanShelf.SurveyOffer offer) {
            return new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return 280f;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara(offer.spec.getDisplayName(), offer.spec.rarity.color, 0f);
                    tooltip.addPara(offer.spec.getTypeName(), Misc.getGrayColor(), 2f);

                    tooltip.addPara("Charts this species' habitat on your map.", 8f);

                    int have = FishCurrency.count(offer.costRarity);
                    boolean afford = have >= offer.costCount;

                    tooltip.addPara("Price: %s - %s aboard.", 8f, Misc.getGrayColor(),
                            afford ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(),
                            describeCost(offer), String.valueOf(have));

                    tooltip.addPara(afford ? "Click to buy."
                                    : "Not enough specimens aboard.",
                            afford ? Misc.getTextColor() : Misc.getNegativeHighlightColor(), 8f);
                }
            };
        }

        protected String describeCost(FishermanShelf.SurveyOffer offer) {
            return offer.costCount + " " + offer.costRarity.name().toLowerCase()
                    + (offer.costCount == 1 ? " specimen" : " specimens");
        }

        //---------------------------------------------------------------- buying and undoing

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

        protected void buy(FishermanShelf.SurveyOffer clicked) {
            //re-resolved rather than trusted: the card's offer could be a frame stale
            List<FishermanShelf.SurveyOffer> offers =
                    FishermanShelf.getOffers(dialog.getInteractionTarget());

            FishermanShelf.SurveyOffer offer = null;
            for (FishermanShelf.SurveyOffer candidate : offers) {
                if (candidate.spec.id.equals(clicked.spec.id)) {
                    offer = candidate;
                    break;
                }
            }
            if (offer == null) return;

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
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                    "Puchased range data for " + offer.spec.getDisplayName());

            rebuildGrid();
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

        protected void undoClicked() {
            undo();
            rebuildGrid();
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

        //---------------------------------------------------------------- the drawn controls

        /**
         * One chart for sale, a chip at card scale: dark field that lights under the mouse, the
         * rarity's underline along the bottom, the {@link FishIcons} silhouette on its fading
         * disc, the name, the price.
         */
        protected class CardPlugin extends com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin {

            protected final FishermanShelf.SurveyOffer offer;
            protected PositionAPI cardPos;

            protected transient LazyFont.DrawableString name;
            protected transient LazyFont.DrawableString price;
            protected transient String pricedAs;

            public CardPlugin(FishermanShelf.SurveyOffer offer) {
                this.offer = offer;
            }

            @Override
            public void positionChanged(PositionAPI position) {
                cardPos = position;
            }

            @Override
            public void render(float alphaMult) {
                if (cardPos == null || alphaMult <= 0f) return;

                LazyFont small = ShopUi.getSmallFont();
                if (small == null) return;

                float x = cardPos.getX();
                float y = cardPos.getY();
                float w = cardPos.getWidth();
                float h = cardPos.getHeight();

                int have = FishCurrency.count(offer.costRarity);
                boolean afford = have >= offer.costCount;
                boolean hovered = ShopUi.contains(x, y, w, h,
                        Global.getSettings().getMouseX(), Global.getSettings().getMouseY());

                //the chip's grammar at card scale: dark field, the identity colour underlining
                ShopUi.drawQuad(x, y, w, h, Misc.getDarkPlayerColor(),
                        (hovered && afford ? 0.35f : 0.15f) * alphaMult);
                ShopUi.drawQuad(x, y, w, 2f, offer.spec.rarity.color,
                        (afford ? 0.95f : 0.35f) * alphaMult);

                float cx = x + w * 0.5f;
                float discY = y + h - PAD - DISC_RADIUS;

                //the shared rarity stage and knowledge-aware face - the shape is what is for sale
                FishIcons.drawBacklit(offer.spec, cx, discY, DISC_RADIUS, ART_SIZE, alphaMult);

                if (name == null) {
                    name = small.createText(offer.spec.getDisplayName(), Color.WHITE,
                            small.getBaseHeight(), w - 12f);
                    name.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
                }

                name.setBaseColor(ShopUi.withAlpha(offer.spec.rarity.color, alphaMult));
                name.draw(Math.round(cx - name.getWidth() * 0.5f),
                        Math.round(discY - DISC_RADIUS - 8f));

                //the price alone - what is aboard lives in the tooltip, where it cannot wrap
                //the label onto a second line
                String cost = describeCost(offer);
                if (price == null || !cost.equals(pricedAs)) {
                    pricedAs = cost;
                    price = small.createText(cost, Color.WHITE, small.getBaseHeight(), w - 12f);
                    price.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
                }

                //the cost speaks in the rarity it is asking for; red stays what can't be paid
                price.setBaseColor(ShopUi.withAlpha(afford ? offer.costRarity.color
                        : Misc.getNegativeHighlightColor(), alphaMult));
                price.draw(Math.round(cx - price.getWidth() * 0.5f),
                        Math.round(y + PAD + 14f));
            }

            @Override
            public void processInput(List<InputEventAPI> events) {
                if (cardPos == null) return;

                for (InputEventAPI event : events) {
                    if (event.isConsumed() || !event.isLMBDownEvent()) continue;
                    if (!ShopUi.contains(cardPos.getX(), cardPos.getY(), cardPos.getWidth(),
                            cardPos.getHeight(), event.getX(), event.getY())) {
                        continue;
                    }

                    event.consume();
                    buy(offer);

                    return;
                }
            }
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
