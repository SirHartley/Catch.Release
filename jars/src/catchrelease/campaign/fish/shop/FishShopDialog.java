package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.campaign.fish.items.FishItems;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The outfitter: upgrades and tackle, paid for in fish.
 * <p>
 * A holding pen, like the map. This wants to hang off a market or a station eventually; for now it
 * is an ability that puts the panel up, which is enough to use it and to find out whether the prices
 * are anywhere near right.
 * <p>
 * Laid out the way every upgrade screen since the dawn of the genre is laid out, because that is
 * the layout players already know how to read: the purse across the top, the wares down the left
 * grouped by the gear they bolt onto, and the one selected thing large on the right with its
 * ladder, its numbers, and one button. The left side is drawn live off the data - a row reads its
 * own level and price every frame - so only the right side is ever rebuilt, and the list never
 * loses its scroll under the mouse.
 */
public class FishShopDialog implements InteractionDialogPlugin {

    public static final float WIDTH = 920f;
    public static final float HEIGHT = 640f;

    public static final float PAD = 12f;
    public static final float HEADER_HEIGHT = 46f;
    public static final float LIST_WIDTH = 320f;
    public static final float ROW_WIDTH = LIST_WIDTH - 18f;
    public static final float ROW_HEIGHT = 26f;
    public static final float DETAIL_GAP = 14f;

    public static final float MAIN_TAB_HEIGHT = 28f;
    public static final float CATEGORY_TAB_HEIGHT = 44f;
    public static final float TAB_GAP = 4f;
    public static final float ACTION_HEIGHT = 28f;

    /** Opens the outfitter, if the UI will have it. */
    public static boolean open() {
        return Global.getSector().getCampaignUI()
                .showInteractionDialog(new FishShopDialog(), Global.getSector().getPlayerFleet());
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

    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin,
            ShopRowPlugin.Host, ShopTabPlugin.Host, ShopHeaderPlugin.Purse {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;

        protected final List<ShopEntry> entries = new ArrayList<>();
        protected String selectedKey;

        /** Which shelf is out: the big split first, the gear within it second. */
        protected ShopEntry.Kind mainTab = ShopEntry.Kind.UPGRADE;
        protected ShopGroup category = ShopGroup.SEARCHLIGHTS;

        protected TooltipMakerAPI list;

        /** Counted once per change rather than once per frame - the purse walks the whole hold. */
        protected Map<FishRarity, Integer> wallet = new HashMap<>();
        protected int credits = 0;

        /**
         * Everything this build put on the panel, by the reference that can actually take it off
         * again. A scrollable element is wrapped in a scroller on the way in and the wrapper is
         * what the panel holds - removing the element itself removes nothing, which is where the
         * stacked-up panels came from. So what goes in this list is the element's external
         * scroller when it has one, and the element itself when it does not.
         */
        protected final List<UIComponentAPI> added = new ArrayList<>();

        /** The right-hand pane, torn down and rebuilt whenever what it shows stops being true. */
        protected TooltipMakerAPI detail;
        protected PositionAPI listViewport;
        protected Object buyId;

        protected final Object storeId = new Object();
        protected final Object retrieveId = new Object();
        protected final Object sellId = new Object();

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            buildEntries();
            selectFirstVisible();
            refreshWallet();

            build();
        }

        /**
         * Everything on sale, shelf by shelf: every stat the sheet knows grouped by its gear, then
         * each rig's modules with the empty slot listed last as a way out of all of them.
         */
        protected void buildEntries() {
            List<UpgradeStat> stats = new ArrayList<>(UpgradeManager.getInstance().getAll().values());

            //the sheet documents its own format with a row, which is not a thing for sale - and the
            //catch's own tuning stats are not equipment, so the shop does not stock them at all
            stats.removeIf(stat -> stat.id == null || stat.id.equalsIgnoreCase("example"));
            stats.removeIf(stat -> ShopGroup.forStat(stat) == ShopGroup.THE_CATCH);
            stats.sort(Comparator.comparing(stat -> stat.id));

            for (ShopGroup group : ShopGroup.values()) {
                for (UpgradeStat stat : stats) {
                    if (ShopGroup.forStat(stat) == group) entries.add(ShopEntry.of(stat));
                }
            }

            //the empty slot first: the way out of every module is the first thing on the shelf
            for (Tackle.Fit rig : new Tackle.Fit[]{Tackle.Fit.DRONE, Tackle.Fit.HARPOON}) {
                entries.add(ShopEntry.of(Tackle.NONE, rig));

                for (Tackle tackle : TackleManager.getOptions(rig)) {
                    if (tackle != Tackle.NONE) entries.add(ShopEntry.of(tackle, rig));
                }
            }
        }

        protected void refreshWallet() {
            wallet = FishCurrency.count();

            credits = Global.getSector().getPlayerFleet() == null ? 0
                    : (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        }

        @Override
        public int getCredits() {
            return credits;
        }

        protected void build() {
            buildHeader();
            buildTabs();
            buildList();
            buildDetail();
            buildActions();
        }

        /**
         * Everything torn down by the reference it was tracked under and put back, since reaching
         * back into a live panel is how stale copies get left behind it.
         *
         * @param keepScroll whether the list should come back at the scroll it was at - true for a
         *                   click inside the shelf, false when the shelf itself changed
         */
        protected void rebuild(boolean keepScroll) {
            if (panel == null) return;

            float scroll = keepScroll && list != null && list.getExternalScroller() != null
                    ? list.getExternalScroller().getYOffset() : 0f;

            for (UIComponentAPI component : added) panel.removeComponent(component);
            added.clear();

            build();

            if (keepScroll && list != null && list.getExternalScroller() != null) {
                list.getExternalScroller().setYOffset(scroll);
            }
        }

        /**
         * Adds an element and remembers the thing the panel actually holds for it.
         *
         * @return the position of that thing - for a scrollable element the scroller's, which is
         *         the window on screen rather than the content behind it
         */
        protected PositionAPI place(TooltipMakerAPI element, float x, float y) {
            PositionAPI pos = panel.addUIElement(element);
            pos.inTL(x, y);

            added.add(element.getExternalScroller() != null
                    ? (UIComponentAPI) element.getExternalScroller() : element);

            return pos;
        }

        protected void buildHeader() {
            CustomPanelAPI header = panel.createCustomPanel(WIDTH - PAD * 2f, HEADER_HEIGHT,
                    new ShopHeaderPlugin(this));

            panel.addComponent(header).inTL(PAD, PAD);
            added.add(header);
        }

        /** The gear a main tab sells, in shelf order. */
        protected ShopGroup[] getCategories(ShopEntry.Kind tab) {
            return tab == ShopEntry.Kind.UPGRADE
                    ? new ShopGroup[]{ShopGroup.SEARCHLIGHTS, ShopGroup.DRONES, ShopGroup.HARPOON,
                            ShopGroup.DEPTH_BOMBS}
                    : new ShopGroup[]{ShopGroup.DRONE_TACKLE, ShopGroup.HARPOON_TIPS};
        }

        /** What the current shelf holds - the list and the default selection are both cut from this. */
        protected List<ShopEntry> getVisible() {
            List<ShopEntry> visible = new ArrayList<>();

            for (ShopEntry entry : entries) {
                if (entry.group == category) visible.add(entry);
            }

            return visible;
        }

        protected void selectFirstVisible() {
            List<ShopEntry> visible = getVisible();

            selectedKey = visible.isEmpty() ? null : visible.get(0).getKey();
        }

        /**
         * Two rows of tabs over the shelf list, drawn in the rows' own style: the big split
         * between what is bought once and levelled (upgrades) and what is fitted to a slot
         * (modifiers), each wearing its icon, and under it the gear within the chosen half.
         */
        protected void buildTabs() {
            float top = PAD + HEADER_HEIGHT + 10f;
            float mainWidth = (LIST_WIDTH - TAB_GAP) / 2f;

            ShopEntry.Kind[] kinds = ShopEntry.Kind.values();
            for (int i = 0; i < kinds.length; i++) {
                addTab(kinds[i], kinds[i] == ShopEntry.Kind.UPGRADE ? "Upgrades" : "Modifiers",
                        kinds[i] == ShopEntry.Kind.UPGRADE ? "placeholder" : "placeholder2",
                        false, PAD + i * (mainWidth + TAB_GAP), top, mainWidth, MAIN_TAB_HEIGHT);
            }

            float categoryTop = top + MAIN_TAB_HEIGHT + TAB_GAP;
            ShopGroup[] groups = getCategories(mainTab);
            float categoryWidth = (LIST_WIDTH - (groups.length - 1) * TAB_GAP) / groups.length;

            for (int i = 0; i < groups.length; i++) {
                addTab(groups[i], groups[i].tabTitle, "placeholder",
                        true, PAD + i * (categoryWidth + TAB_GAP), categoryTop,
                        categoryWidth, CATEGORY_TAB_HEIGHT);
            }
        }

        protected void addTab(Object data, String label, String iconId,
                              boolean vertical, float x, float y, float width, float height) {

            CustomPanelAPI tab = panel.createCustomPanel(width, height,
                    new ShopTabPlugin(data, label, iconId, vertical, this));

            panel.addComponent(tab).inTL(x, y);
            added.add(tab);
        }

        @Override
        public boolean isActiveTab(Object id) {
            return id == mainTab || id == category;
        }

        @Override
        public void onTabClicked(Object id) {
            if (id instanceof ShopEntry.Kind) {
                mainTab = (ShopEntry.Kind) id;
                category = getCategories(mainTab)[0];
            } else if (id instanceof ShopGroup) {
                category = (ShopGroup) id;
            } else {
                return;
            }

            selectFirstVisible();
            rebuild(false);
        }

        protected void buildList() {
            float top = PAD + HEADER_HEIGHT + 10f + MAIN_TAB_HEIGHT + TAB_GAP
                    + CATEGORY_TAB_HEIGHT + 8f;
            float height = HEIGHT - top - PAD;

            list = panel.createUIElement(LIST_WIDTH, height, true);

            for (ShopEntry entry : getVisible()) {
                list.addCustom(panel.createCustomPanel(ROW_WIDTH, ROW_HEIGHT,
                        new ShopRowPlugin(entry, this)), 3f);
            }

            listViewport = place(list, PAD, top);
        }

        protected void buildDetail() {
            float top = PAD + HEADER_HEIGHT + 10f;
            float height = HEIGHT - top - PAD - ACTION_HEIGHT - 10f;
            float width = WIDTH - PAD * 2f - LIST_WIDTH - DETAIL_GAP;

            detail = panel.createUIElement(width, height, false);

            ShopEntry entry = getSelected();
            if (entry != null) buildDetailContent(detail, width, entry);

            place(detail, PAD + LIST_WIDTH + DETAIL_GAP, top);
        }

        protected void buildDetailContent(TooltipMakerAPI info, float width, ShopEntry entry) {
            CustomPanelAPI head = panel.createCustomPanel(width - 10f, 84f,
                    new ShopDetailHeaderPlugin(entry));
            info.addCustom(head, 0f);

            info.addPara(entry.getDescription(), 12f);

            if (entry.isUpgrade() && !entry.isMaxed()) {
                info.addPara("Now %s - next level %s", 12f, Misc.getHighlightColor(),
                        entry.getValueAt(entry.getLevel()), entry.getValueAt(entry.getLevel() + 1));
            }

            if (entry.kind == ShopEntry.Kind.TACKLE && !entry.isFitted()) {
                Tackle fitted = TackleManager.get(entry.rig);

                if (fitted != entry.tackle && fitted != Tackle.NONE) {
                    info.addPara("In the slot now: %s. One slot - fitting this puts that back on"
                            + " the shelf.", 12f, Misc.getGrayColor(),
                            Misc.getHighlightColor(), fitted.name);
                }
            }

            buildPrice(info, entry);
            buildBuyButton(info, entry);
        }

        /** The tag: credits, the catch beside them, and whether the hold can cover it. */
        protected void buildPrice(TooltipMakerAPI info, ShopEntry entry) {
            if (entry.isMaxed()) {
                info.addPara("Fully upgraded.", Misc.getPositiveHighlightColor(), 16f);
                return;
            }

            if (entry.isFitted()) {
                info.addPara("Fitted and ready.", Misc.getPositiveHighlightColor(), 16f);
                return;
            }

            ShopPricing.Price price = entry.getPrice();
            if (price == null) {
                info.addPara("No charge for emptying a slot.", Misc.getGrayColor(), 16f);
                return;
            }

            boolean creditsOk = credits >= price.credits;

            info.addPara("Price: %s", 16f, Misc.getGrayColor(),
                    creditsOk ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(),
                    Misc.getDGSCredits(price.credits));

            if (price.fish != null) {
                FishRarity rarity = price.fish.getDisplayRarity();
                int have = FishCurrency.count(price.fish);
                boolean fishOk = have >= price.fish.count;

                info.addPara("And the catch: %s", 4f, Misc.getGrayColor(),
                        rarity == null ? Misc.getHighlightColor() : rarity.color,
                        price.fish.describe());

                info.addPara("Matching aboard: %s", 4f, Misc.getGrayColor(),
                        fishOk ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                        String.valueOf(have));
            }
        }

        protected void buildBuyButton(TooltipMakerAPI info, ShopEntry entry) {
            if (entry.isDone()) return;

            FishRarity rarity = entry.getPriceRarity();
            boolean afford = entry.canAfford();

            String label = entry.isUpgrade() ? "UPGRADE" : "FIT";
            Color base = afford
                    ? (rarity == null ? Misc.getBasePlayerColor() : rarity.color)
                    : Misc.getGrayColor();

            buyId = new Object();

            info.setButtonFontOrbitron20Bold();
            ButtonAPI button = info.addButton(label, buyId, base, Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.TL_BR, 240f, 34f, 20f);
            info.setButtonFontDefault();

            button.setEnabled(afford);
        }

        /**
         * The counter itself, under the detail pane: stow the catch with the shop, take it back,
         * or sell it outright - each through the game's own cargo picker, over only the fish.
         */
        protected void buildActions() {
            float width = WIDTH - PAD * 2f - LIST_WIDTH - DETAIL_GAP;
            float buttonWidth = (width - TAB_GAP * 2f) / 3f;
            float x = PAD + LIST_WIDTH + DETAIL_GAP;
            float y = HEIGHT - PAD - ACTION_HEIGHT;

            addAction("Store fish", storeId, x, y, buttonWidth);
            addAction("Retrieve fish", retrieveId, x + buttonWidth + TAB_GAP, y, buttonWidth);
            addAction("Sell fish", sellId, x + (buttonWidth + TAB_GAP) * 2f, y, buttonWidth);
        }

        protected void addAction(String label, Object id, float x, float y, float width) {
            TooltipMakerAPI element = panel.createUIElement(width, ACTION_HEIGHT, false);

            element.addButton(label, id, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.TL_BR, width, ACTION_HEIGHT - 2f, 0f);

            place(element, x, y);
        }

        /** One picker for all three counters; what changes is where the picked stacks go. */
        protected void openPicker(String title, String okText, CargoAPI shown,
                                  java.util.function.Consumer<CargoAPI> onPicked) {

            if (dialog == null || shown.getStacksCopy().isEmpty()) return;

            dialog.showCargoPickerDialog(title, okText, "Cancel", false, 330f, shown,
                    new CargoPickerListener() {
                        @Override
                        public void pickedCargo(CargoAPI cargo) {
                            onPicked.accept(cargo);

                            refreshWallet();
                            rebuild(true);
                        }

                        @Override
                        public void cancelledCargoSelection() {
                        }

                        @Override
                        public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo,
                                                      CargoStackAPI pickedUp, boolean pickedUpFromSource,
                                                      CargoAPI combined) {
                            int count = 0;
                            float value = 0f;

                            if (combined != null) {
                                for (CargoStackAPI stack : combined.getStacksCopy()) {
                                    count += FishItems.countSpecimens(stack);
                                    value += FishItems.getStackValue(stack);
                                }
                            }

                            panel.setParaFontOrbitron();
                            panel.addPara(title, Misc.getBasePlayerColor(), 10f);
                            panel.setParaFontDefault();

                            panel.addPara("Selected: %s specimens", 10f, Misc.getHighlightColor(),
                                    String.valueOf(count));
                            panel.addPara("Base value: %s", 3f, Misc.getHighlightColor(),
                                    Misc.getDGSCredits(value));
                        }
                    });
        }

        /** Every picked stack out of one hold and into the other. A null destination is a sale. */
        protected float moveStacks(CargoAPI picked, CargoAPI from, CargoAPI to) {
            float value = 0f;

            for (CargoStackAPI stack : picked.getStacksCopy()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                if (data == null) continue;

                value += FishItems.getStackValue(stack);

                from.removeItems(CargoAPI.CargoItemType.SPECIAL, data, stack.getSize());
                if (to != null) to.addItems(CargoAPI.CargoItemType.SPECIAL, data, stack.getSize());
            }

            return value;
        }

        protected ShopEntry getSelected() {
            List<ShopEntry> visible = getVisible();

            for (ShopEntry entry : visible) {
                if (entry.getKey().equals(selectedKey)) return entry;
            }

            return visible.isEmpty() ? null : visible.get(0);
        }

        @Override
        public boolean isSelected(ShopEntry entry) {
            return entry.getKey().equals(selectedKey);
        }

        @Override
        public void onRowClicked(ShopEntry entry) {
            if (isSelected(entry)) return;

            selectedKey = entry.getKey();
            rebuild(true);
        }

        @Override
        public PositionAPI getListViewport() {
            return listViewport;
        }

        @Override
        public Map<FishRarity, Integer> getWallet() {
            return wallet;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            CargoAPI player = Global.getSector().getPlayerFleet().getCargo();

            if (buttonId == storeId) {
                openPicker("Store the catch with the shop", "Store", FishItems.copyFishStacks(player),
                        picked -> moveStacks(picked, player, ShopStorage.get()));
                return;
            }

            if (buttonId == retrieveId) {
                openPicker("Take the catch back aboard", "Retrieve",
                        FishItems.copyFishStacks(ShopStorage.get()),
                        picked -> moveStacks(picked, ShopStorage.get(), player));
                return;
            }

            if (buttonId == sellId) {
                openPicker("Sell the catch", "Sell", FishItems.copyFishStacks(player),
                        picked -> {
                            float value = moveStacks(picked, player, null);
                            player.getCredits().add(value);
                        });
                return;
            }

            if (buttonId != buyId) return;

            ShopEntry entry = getSelected();
            if (entry == null || !entry.buy()) return;

            Global.getSoundPlayer().playUISound("ui_char_increase_aptitude", 1f, 1f);

            refreshWallet();
            rebuild(true);
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
        public void advance(float amount) {
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
        public void positionChanged(PositionAPI position) {
        }

        @Override
        public void renderBelow(float alphaMult) {
        }

        @Override
        public void render(float alphaMult) {
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
