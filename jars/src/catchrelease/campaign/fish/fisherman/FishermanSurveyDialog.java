package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishShopDialog;
import catchrelease.config.FishingSounds;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.ui.FishIcons;
import catchrelease.ui.FishPurse;
import catchrelease.ui.UiKit;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FishermanSurveyDialog implements InteractionDialogPlugin {

    // showCustomDialog adds 20 across and 55 down for its frame and the Return button
    public static final float WIDTH = 712f;
    public static final float HEIGHT = 437f;
    public static final String TITLE = "Range data";
    public static final String SOUND_UNDONE = "ui_cancel_construction_or_upgrade_industry";

    public static final float PURSE_Y = 28f;
    public static final float TOP = 64f;
    public static final int COLUMNS = 3;
    public static final int VISIBLE_ROWS = 2;
    public static final float TILE_WIDTH = 116f;
    public static final float TILE_HEIGHT = 168f;
    public static final float TILE_GAP = 5f;
    public static final float TILE_PITCH_X = TILE_WIDTH + TILE_GAP;
    public static final float TILE_PITCH_Y = TILE_HEIGHT + TILE_GAP;
    public static final float LIST_WIDTH = COLUMNS * TILE_WIDTH + (COLUMNS - 1) * TILE_GAP;
    public static final float GRID_Y = TOP + UiKit.HEADING_HEIGHT + UiKit.ROW_GAP;
    public static final float GRID_HEIGHT = VISIBLE_ROWS * TILE_PITCH_Y - TILE_GAP;
    // a tile at the left edge of a scroll area loses its left border to the clip
    public static final float GRID_MARGIN = 2f;
    public static final float DIVIDER_X = LIST_WIDTH + 11f;

    public static final float SILHOUETTE_X = 18f;
    public static final float SILHOUETTE_Y = 5f;
    public static final float SILHOUETTE_SIZE = 80f;
    public static final float SILHOUETTE_ART = 76f;
    public static final float NAME_Y = 90f;
    public static final float NAME_LINE = 19f;
    public static final float NAME_SIDE = 4f;
    public static final float TILE_RULE_Y = 134f;
    public static final float COST_ICON_X = 40f;
    public static final float COST_ICON_Y = 141f;
    public static final float COST_ICON = 20f;
    public static final float COST_TEXT_X = 63f;

    public static final float CARD_X = DIVIDER_X + 11f;
    public static final float CARD_WIDTH = WIDTH - CARD_X;
    public static final float NAME_BAR_HEIGHT = 26f;
    public static final float TAGLINE_Y = TOP + 34f;
    public static final float PICTURE_Y = TAGLINE_Y + 19f;
    public static final float PICTURE_SIZE = 72f;
    public static final float PICTURE_ART = 60f;
    public static final float PICTURE_INSET = 4f;
    public static final float FACTS_X = CARD_X + PICTURE_SIZE + 12f;
    public static final float FACTS_Y = PICTURE_Y + 9f;
    public static final float FACT_ROW = 18f;
    public static final float STRIP_Y = PICTURE_Y + PICTURE_SIZE + 16f;
    public static final float STRIP_HEIGHT = 40f;
    public static final float STRIP_ICON_X = 10f;
    public static final float STRIP_TEXT_X = 36f;
    public static final float BUY_WIDTH = 96f;
    public static final float STRIP_INSET = 8f;
    public static final float REASON_Y = STRIP_Y + STRIP_HEIGHT + 4f;
    public static final float UNDO_Y = HEIGHT - UiKit.BUTTON_HEIGHT;
    public static final float UNDO_WIDTH = 168f;
    public static final float UNDO_TOOLTIP_WIDTH = 260f;
    public static final float LEGEND_WIDTH = 320f;

    protected InteractionDialogAPI dialog;
    protected final FishShopDialog.OnClose onClose;
    protected boolean closed;

    protected class Delegate extends BaseCustomUIPanelPlugin implements CustomDialogDelegate {

        protected CustomPanelAPI panel;
        protected CustomPanelAPI body;
        protected TooltipMakerAPI grid;
        protected float gridOffset;

        protected final List<Runnable> pending = new ArrayList<>();
        protected boolean dirty;

        protected String selectedId;
        protected int selectedIndex;
        // newest last; undo takes them back in reverse order, each restoring the hold as it was before
        protected final List<Receipt> purchases = new ArrayList<>();

        protected static class Receipt {

            String specId;
            int stockIndex;
            List<Object[]> fishAboard;
        }

        @Override
        public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
            this.panel = panel;

            rebuild();
        }

        @Override
        public boolean hasCancelButton() {
            return false;
        }

        @Override
        public String getConfirmText() {
            return "Return";
        }

        @Override
        public String getCancelText() {
            return null;
        }

        // escape cancels; both ways out go back to the Fisherman
        @Override
        public void customDialogConfirm() {
            FishermanSurveyDialog.this.close();
        }

        @Override
        public void customDialogCancel() {
            FishermanSurveyDialog.this.close();
        }

        @Override
        public CustomUIPanelPlugin getCustomPanelPlugin() {
            return this;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (buttonId instanceof Runnable action) action.run();
        }

        @Override
        public void advance(float amount) {
            if (panel == null) return;

            // actions change the panel tree, so they wait until input dispatch is over
            if (!pending.isEmpty()) {
                List<Runnable> actions = new ArrayList<>(pending);
                pending.clear();

                for (Runnable action : actions) action.run();
            }

            if (dirty) {
                dirty = false;
                rebuild();
            }
        }

        protected Runnable later(Runnable action) {
            return () -> pending.add(action);
        }

        protected List<FishermanShelf.SurveyOffer> getOffers() {
            return FishermanShelf.getOffers(dialog.getInteractionTarget());
        }

        protected void rebuild() {
            ScrollPanelAPI scroller = grid == null ? null : grid.getExternalScroller();
            if (scroller != null) gridOffset = scroller.getYOffset();

            if (body != null) panel.removeComponent(body);

            grid = null;
            body = UiKit.plain(panel, WIDTH, HEIGHT);
            panel.addComponent(body).inTL(0f, 0f);

            List<FishermanShelf.SurveyOffer> offers = getOffers();
            FishermanShelf.SurveyOffer selected = resolveSelected(offers);

            UiKit.title(body, WIDTH, TITLE, null, this::describeRangeData);
            FishPurse.add(body, WIDTH, PURSE_Y, UiKit.BUTTON_HEIGHT, FishCurrency.count());

            buildCharts(offers);
            UiKit.vrule(body, DIVIDER_X, TOP, HEIGHT - TOP);
            if (selected != null) buildCard(selected);
            buildUndo();
        }

        protected FishermanShelf.SurveyOffer resolveSelected(List<FishermanShelf.SurveyOffer> offers) {
            if (offers.isEmpty()) {
                selectedId = null;
                return null;
            }

            for (int i = 0; i < offers.size(); i++) {
                if (offers.get(i).spec.id.equals(selectedId)) {
                    selectedIndex = i;
                    return offers.get(i);
                }
            }

            // the chart just bought has left the shelf: its neighbour takes its place
            selectedIndex = Math.max(0, Math.min(selectedIndex, offers.size() - 1));
            selectedId = offers.get(selectedIndex).spec.id;

            return offers.get(selectedIndex);
        }

        protected void describeRangeData(TooltipMakerAPI tooltip) {
            tooltip.addPara("Range data", Misc.getBasePlayerColor(), 0f);

            tooltip.addPara("Charts a species' range so you can plan a route to it. Also"
                    + " adds habitat and movement details to the codex, and a species"
                    + " indicator to the system map.", 8f);

            tooltip.addPara("Stock refreshes periodically. Fishing boats outside the core"
                    + " have their own stock.", 8f);
        }

        protected void buildCharts(List<FishermanShelf.SurveyOffer> offers) {
            UiKit.heading(body, 0f, TOP, LIST_WIDTH, "Charts", String.valueOf(offers.size()));

            if (offers.isEmpty()) {
                UiKit.text(body, 0f, GRID_Y, LIST_WIDTH, "Nothing left on the shelf this visit.", UiKit.BODY,
                        Misc.getGrayColor(), Alignment.LMID);
                return;
            }

            SpriteAPI fish = SpriteLoader.getSprite("pane_fish");

            // the scroll element reserves its last five units for the bar, which then sits clear of the divider
            grid = body.createUIElement(DIVIDER_X - 1f + UiKit.INSET, GRID_HEIGHT, true);

            for (int first = 0; first < offers.size(); first += COLUMNS) {
                CustomPanelAPI line = UiKit.plain(body, LIST_WIDTH + GRID_MARGIN, TILE_HEIGHT);

                for (int column = 0; column < COLUMNS && first + column < offers.size(); column++) {
                    addTile(line, GRID_MARGIN + column * TILE_PITCH_X, offers.get(first + column), fish);
                }

                grid.addCustom(line, first == 0 ? 0f : TILE_GAP);
            }

            body.addUIElement(grid).inTL(-UiKit.INSET, GRID_Y);

            ScrollPanelAPI scroller = grid.getExternalScroller();
            if (scroller != null) {
                float max = Math.max(0f, grid.getHeightSoFar() - GRID_HEIGHT);
                scroller.setYOffset(Math.max(0f, Math.min(max, gridOffset)));
            }
        }

        protected void addTile(CustomPanelAPI line, float x, FishermanShelf.SurveyOffer offer, SpriteAPI fish) {
            FishSpec spec = offer.spec;
            boolean afford = FishCurrency.count(offer.costRarity) >= offer.costCount;

            UiKit.Row tile = UiKit.row(line, x, 0f, TILE_WIDTH, TILE_HEIGHT, spec.id.equals(selectedId),
                    later(() -> select(spec.id)));
            CustomPanelAPI parent = tile.panel;

            UiKit.painted(parent, SILHOUETTE_X, SILHOUETTE_Y, SILHOUETTE_SIZE, SILHOUETTE_SIZE, null,
                    (pos, alpha) -> FishIcons.drawBacklit(spec, pos.getX() + SILHOUETTE_SIZE * 0.5f,
                            pos.getY() + SILHOUETTE_SIZE * 0.5f, SILHOUETTE_SIZE * 0.5f, SILHOUETTE_ART, alpha));

            float nameWidth = TILE_WIDTH - 2f * NAME_SIDE;
            List<String> lines = splitName(spec.getDisplayName(), nameWidth);
            for (int i = 0; i < lines.size(); i++) {
                UiKit.text(parent, NAME_SIDE, NAME_Y + i * NAME_LINE, nameWidth, lines.get(i), UiKit.BODY,
                        spec.rarity.color, Alignment.MID);
            }

            Color rule = UiKit.base();
            UiKit.painted(parent, SILHOUETTE_X, TILE_RULE_Y, SILHOUETTE_SIZE, 1f, null,
                    (pos, alpha) -> UiKit.quad(pos.getX(), pos.getY(), pos.getWidth(), 1f, rule,
                            UiKit.RULE_ALPHA * alpha));

            UiKit.sprite(parent, COST_ICON_X, COST_ICON_Y, COST_ICON, COST_ICON, fish, offer.costRarity.color);
            UiKit.textIn(parent, COST_TEXT_X, COST_ICON_Y, TILE_WIDTH - COST_TEXT_X - NAME_SIDE, COST_ICON,
                    String.valueOf(offer.costCount), UiKit.BODY,
                    afford ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(), Alignment.LMID);
        }

        // two lines at most, broken where the longer line is shortest; whatever still does not fit is shortened
        protected List<String> splitName(String name, float width) {
            List<String> out = new ArrayList<>();
            String[] words = name.split(" ");

            if (UiKit.width(name, UiKit.BODY) <= width - 2f || words.length < 2) {
                out.add(name);
                return out;
            }

            int best = 1;
            float bestWidth = Float.MAX_VALUE;
            for (int split = 1; split < words.length; split++) {
                float longer = Math.max(UiKit.width(join(words, 0, split), UiKit.BODY),
                        UiKit.width(join(words, split, words.length), UiKit.BODY));
                if (longer < bestWidth) {
                    bestWidth = longer;
                    best = split;
                }
            }

            out.add(join(words, 0, best));
            out.add(join(words, best, words.length));

            return out;
        }

        protected String join(String[] words, int from, int to) {
            return String.join(" ", Arrays.copyOfRange(words, from, to));
        }

        protected void select(String specId) {
            if (specId.equals(selectedId)) return;

            selectedId = specId;
            dirty = true;
        }

        protected void buildCard(FishermanShelf.SurveyOffer offer) {
            FishSpec spec = offer.spec;
            int have = FishCurrency.count(offer.costRarity);
            boolean afford = have >= offer.costCount;
            Color dark = UiKit.dark();

            UiKit.painted(body, CARD_X, TOP, CARD_WIDTH, NAME_BAR_HEIGHT, (pos, alpha) -> UiKit.quad(pos.getX(),
                    pos.getY(), pos.getWidth(), pos.getHeight(), dark, alpha), null);
            UiKit.textIn(body, CARD_X, TOP, CARD_WIDTH, NAME_BAR_HEIGHT, spec.getDisplayName(),
                    Fonts.ORBITRON_20AABOLD, UiKit.bright(), Alignment.MID);

            UiKit.text(body, CARD_X, TAGLINE_Y, CARD_WIDTH, "Charts this species' habitat on your map.",
                    UiKit.BODY, Misc.getGrayColor(), Alignment.MID);

            UiKit.border(body, CARD_X, PICTURE_Y, PICTURE_SIZE, PICTURE_SIZE);
            UiKit.painted(body, CARD_X, PICTURE_Y, PICTURE_SIZE, PICTURE_SIZE, null,
                    (pos, alpha) -> FishIcons.drawBacklit(spec, pos.getX() + PICTURE_SIZE * 0.5f,
                            pos.getY() + PICTURE_SIZE * 0.5f, PICTURE_SIZE * 0.5f - PICTURE_INSET, PICTURE_ART, alpha));

            float factsWidth = WIDTH - FACTS_X;
            addFact(0, factsWidth, "Type", spec.getTypeName(), Misc.getTextColor());
            addFact(1, factsWidth, "Rarity", Misc.ucFirst(spec.rarity.name().toLowerCase()), spec.rarity.color);
            addFact(2, factsWidth, "Aboard", String.valueOf(have),
                    afford ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());

            UiKit.border(body, CARD_X, STRIP_Y, CARD_WIDTH, STRIP_HEIGHT);
            UiKit.sprite(body, CARD_X + STRIP_ICON_X, STRIP_Y + (STRIP_HEIGHT - COST_ICON) / 2f, COST_ICON,
                    COST_ICON, SpriteLoader.getSprite("pane_fish"), offer.costRarity.color);

            float buyX = CARD_X + CARD_WIDTH - STRIP_INSET - BUY_WIDTH;
            UiKit.textIn(body, CARD_X + STRIP_TEXT_X, STRIP_Y, buyX - CARD_X - STRIP_TEXT_X - STRIP_INSET,
                    STRIP_HEIGHT, describeCost(offer), UiKit.BODY,
                    afford ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(), Alignment.LMID);

            String id = spec.id;
            UiKit.button(body, buyX, STRIP_Y + (STRIP_HEIGHT - UiKit.BUTTON_HEIGHT) / 2f, BUY_WIDTH,
                    UiKit.BUTTON_HEIGHT, "Buy", afford ? Misc.getTextColor() : UiKit.base(),
                    afford ? UiKit.fill(UiKit.GREEN) : dark, CutStyle.ALL, afford ? later(() -> buy(id)) : null);

            if (!afford) {
                UiKit.text(body, CARD_X, REASON_Y, CARD_WIDTH, "Not enough specimens aboard.", UiKit.BODY,
                        Misc.getNegativeHighlightColor(), Alignment.LMID);
            }
        }

        protected void addFact(int row, float width, String label, String value, Color color) {
            float y = FACTS_Y + row * FACT_ROW;

            UiKit.text(body, FACTS_X, y, width * 0.6f, label, UiKit.BODY, Misc.getTextColor(), Alignment.LMID);
            UiKit.text(body, FACTS_X + width * 0.4f, y, width * 0.6f, value, UiKit.BODY, color, Alignment.RMID);
        }

        // the bottom row of the card side, with the chart undo would hand back
        protected void buildUndo() {
            UiKit.rule(body, CARD_X, UNDO_Y - 12f, CARD_WIDTH);

            ButtonAPI undo = UiKit.button(body, CARD_X, UNDO_Y, UNDO_WIDTH, "Undo last purchase", CutStyle.ALL,
                    purchases.isEmpty() ? null : later(this::undo));
            UiKit.tooltip(body, undo, UNDO_TOOLTIP_WIDTH, TooltipLocation.ABOVE, false,
                    tooltip -> tooltip.addPara("Returns the last chart bought this visit and refunds the exact"
                            + " fish used to pay for it.", 0f));

            if (purchases.isEmpty()) return;

            FishSpec last = FishSpecLoader.getFishSpec(purchases.get(purchases.size() - 1).specId);
            if (last == null) return;

            float x = CARD_X + UNDO_WIDTH + 10f;
            UiKit.textIn(body, x, UNDO_Y, WIDTH - x, UiKit.BUTTON_HEIGHT, last.getDisplayName(), UiKit.BODY,
                    Misc.getGrayColor(), Alignment.LMID);
        }

        protected String describeCost(FishermanShelf.SurveyOffer offer) {
            return offer.costCount + " " + offer.costRarity.name().toLowerCase()
                    + (offer.costCount == 1 ? " specimen" : " specimens");
        }

        protected void buy(String specId) {
            // re-resolved rather than trusted: the offer could be a frame stale
            FishermanShelf.SurveyOffer offer = null;
            for (FishermanShelf.SurveyOffer candidate : getOffers()) {
                if (candidate.spec.id.equals(specId)) {
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

            purchases.add(receipt);
            FishingSounds.playUISound(FishShopDialog.SOUND_BOUGHT, 1f, 1f);
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                    "Purchased range data for " + offer.spec.getDisplayName());

            dirty = true;
        }

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

        protected void undo() {
            if (purchases.isEmpty()) return;
            Receipt receipt = purchases.remove(purchases.size() - 1);

            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                if (data == null) continue;
                if (!FishItems.FISH.equals(data.getId()) && !FishItems.isContainer(data)) continue;

                cargo.removeItems(CargoItemType.SPECIAL, data, stack.getSize());
            }
            for (Object[] entry : receipt.fishAboard) {
                cargo.addSpecial((SpecialItemData) entry[0], (Integer) entry[1]);
            }

            FishLog.relockLocationData(receipt.specId);

            FishermanShelf.putBack(dialog.getInteractionTarget(), receipt.specId,
                    receipt.stockIndex);

            FishSpec spec = FishSpecLoader.getFishSpec(receipt.specId);
            String name = spec == null ? "the last chart" : spec.getDisplayName() + " range data";
            Global.getSector().getCampaignUI().getMessageDisplay()
                    .addMessage("Refunded " + name);

            FishingSounds.playUISound(SOUND_UNDONE, 1f, 1f);

            selectedId = receipt.specId;
            dirty = true;
        }
    }

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

        dialog.getOptionPanel().clearOptions();
        dialog.showCustomDialog(WIDTH, HEIGHT, new Delegate());
    }

    protected void close() {
        if (closed || dialog == null) return;

        closed = true;

        if (onClose == null) {
            dialog.dismiss();
            return;
        }

        onClose.onShopClosed(dialog);
    }

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
