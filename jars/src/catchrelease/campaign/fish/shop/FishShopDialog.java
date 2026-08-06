package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
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
 * The outfitter: upgrades and tackle, paid for in fish. Not hung off a market yet - opened directly
 * via an ability for now.
 * <p>
 * Standard upgrade-screen layout: purse on top, wares down the left grouped by gear, selected item
 * detail on the right. The list is drawn live off the data each frame, so only the right pane is
 * ever rebuilt, keeping the list's scroll position stable.
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

    /** Purchase chime; named once here since the sound id isn't checked until first played. */
    public static final String SOUND_BOUGHT = "ui_upgrade_industry";

    public static final float MAIN_TAB_HEIGHT = 28f;
    public static final float CATEGORY_TAB_HEIGHT = 44f;
    public static final float TAB_GAP = 4f;

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

        //returns anything a save is still holding in shop storage - that button no longer exists
        ShopStorage.reclaim();

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

        /** mainTab is the upgrade/tackle split, category the shelf within it. */
        protected ShopEntry.Kind mainTab = ShopEntry.Kind.UPGRADE;
        protected ShopGroup category = ShopGroup.SEARCHLIGHTS;

        protected TooltipMakerAPI list;

        /** Recomputed on change, not per frame - counting the wallet walks the whole cargo hold. */
        protected Map<FishRarity, Integer> wallet = new HashMap<>();
        protected int credits = 0;

        /**
         * Components added to the panel, tracked by the reference actually needed to remove them -
         * a scrollable element's external scroller wraps it, and only removing the wrapper works.
         */
        protected final List<UIComponentAPI> added = new ArrayList<>();

        /** Right-hand pane, torn down and rebuilt whenever its content changes. */
        protected TooltipMakerAPI detail;
        protected PositionAPI listViewport;
        protected Object buyId;

        /** Dev-mode free-grant button id, next to the buy button. Null outside dev mode. */
        protected Object devBuyId;

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            buildEntries();
            selectFirstVisible();
            refreshWallet();

            build();
        }

        /** All stock: every upgrade stat grouped by gear, then each rig's tackle options. */
        protected void buildEntries() {
            List<UpgradeStat> stats = new ArrayList<>(UpgradeManager.getInstance().getAll().values());

            //"example" is a format-documentation row, not for sale; catch tuning stats aren't equipment
            stats.removeIf(stat -> stat.id == null || stat.id.equalsIgnoreCase("example"));
            stats.removeIf(stat -> ShopGroup.forStat(stat) == ShopGroup.THE_CATCH);
            stats.sort(Comparator.comparing(stat -> stat.id));

            for (ShopGroup group : ShopGroup.values()) {
                for (UpgradeStat stat : stats) {
                    if (ShopGroup.forStat(stat) == group) entries.add(ShopEntry.of(stat));
                }
            }

            //iterate every Fit rather than a fixed list, so a new rig automatically gets a shelf
            for (Tackle.Fit rig : Tackle.Fit.values()) {
                if (!rig.isRig()) continue;

                List<Tackle> options = TackleManager.getOptions(rig);
                options.remove(Tackle.NONE);

                if (options.isEmpty()) continue;

                //empty-slot entry listed first
                entries.add(ShopEntry.of(Tackle.NONE, rig));

                for (Tackle tackle : options) entries.add(ShopEntry.of(tackle, rig));
            }

            //nothing here is for sale - these were bought in a bar, and the shop is only where the
            //switch on one lives. An unbought curio has no row, so the whole shelf and the tab over
            //it appear the moment the first one is
            for (CrabWares ware : CrabWares.getSwitchable()) entries.add(ShopEntry.of(ware));
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
        }

        /**
         * Tears down and rebuilds everything tracked in {@link #added}.
         *
         * @param keepScroll whether to restore the list's prior scroll offset
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
         * Adds an element and records it in {@link #added}.
         *
         * @return the on-screen position (the scroller's, not the content's, if scrollable)
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

        /**
         * Shelves that actually have stock under the given tab, derived from {@link #entries} rather
         * than a hand-kept list - a hard-coded list previously went stale when tackle was added for
         * a rig with no shelf reachable to hold it.
         */
        protected ShopGroup[] getCategories(ShopEntry.Kind tab) {
            List<ShopGroup> out = new ArrayList<>();

            for (ShopGroup group : ShopGroup.values()) {
                for (ShopEntry entry : entries) {
                    if (entry.kind != tab || entry.group != group) continue;

                    out.add(group);
                    break;
                }
            }

            return out.toArray(new ShopGroup[0]);
        }

        /**
         * Main tabs with anything under them, derived the same way the shelves are - the extras tab
         * has nothing in it until something has been bought out of a coat, and a tab that opens onto
         * an empty list is a promise the shop cannot keep.
         */
        protected List<ShopEntry.Kind> getStockedKinds() {
            List<ShopEntry.Kind> out = new ArrayList<>();

            for (ShopEntry.Kind kind : ShopEntry.Kind.values()) {
                for (ShopEntry entry : entries) {
                    if (entry.kind != kind) continue;

                    out.add(kind);
                    break;
                }
            }

            return out;
        }

        /**
         * Entries under the current shelf ({@link #category}), and under the current tab. Both,
         * even though every shelf today belongs to exactly one tab - a shelf shared by two kinds
         * would otherwise put a module on the extras list, and the shelving is derived rather than
         * declared, so that is a thing a later change could do without meaning to.
         */
        protected List<ShopEntry> getVisible() {
            List<ShopEntry> visible = new ArrayList<>();

            for (ShopEntry entry : entries) {
                if (entry.kind == mainTab && entry.group == category) visible.add(entry);
            }

            return visible;
        }

        protected void selectFirstVisible() {
            List<ShopEntry> visible = getVisible();

            selectedKey = visible.isEmpty() ? null : visible.get(0).getKey();
        }

        /** Two tab rows: main split (upgrades/modifiers/extras) on top, shelves within it below. */
        protected void buildTabs() {
            float top = PAD + HEADER_HEIGHT + 10f;

            List<ShopEntry.Kind> kinds = getStockedKinds();
            if (kinds.isEmpty()) return;

            float mainWidth = (LIST_WIDTH - (kinds.size() - 1) * TAB_GAP) / kinds.size();

            for (int i = 0; i < kinds.size(); i++) {
                ShopEntry.Kind kind = kinds.get(i);

                addTab(kind, kind.tabTitle,
                        kind == ShopEntry.Kind.UPGRADE ? "placeholder" : "placeholder2",
                        false, PAD + i * (mainWidth + TAB_GAP), top, mainWidth, MAIN_TAB_HEIGHT);
            }

            float categoryTop = top + MAIN_TAB_HEIGHT + TAB_GAP;
            ShopGroup[] groups = getCategories(mainTab);

            if (groups.length == 0) return;

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

                ShopGroup[] groups = getCategories(mainTab);
                if (groups.length > 0) category = groups[0];
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
            float height = HEIGHT - top - PAD;
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

        /** Price display: credits, fish cost, and whether the player can afford both. */
        protected void buildPrice(TooltipMakerAPI info, ShopEntry entry) {
            if (entry.isCurio()) {
                info.addPara(entry.isOn() ? "Switched on." : "Switched off.",
                        entry.isOn() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor(), 16f);

                info.addPara("Already paid for. Switching it off costs nothing and does not lose"
                        + " it.", Misc.getGrayColor(), 4f);
                return;
            }

            if (entry.isMaxed()) {
                info.addPara("Fully upgraded.", Misc.getPositiveHighlightColor(), 16f);
                return;
            }

            if (entry.isFitted()) {
                info.addPara("Fitted and ready.", Misc.getPositiveHighlightColor(), 16f);
                return;
            }

            //empty slot never had a price; an owned module was already paid for once
            if (entry.tackle == Tackle.NONE) {
                info.addPara("No charge for emptying a slot.", Misc.getGrayColor(), 16f);
                return;
            }

            if (entry.isOwned()) {
                info.addPara("Already yours - fitting it costs nothing.", Misc.getGrayColor(), 16f);
                return;
            }

            ShopPricing.Price price = entry.getPrice();
            if (price == null) return; //defensive; the branches above cover the only null cases

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

            String label = entry.isCurio() ? (entry.isOn() ? "SWITCH OFF" : "SWITCH ON")
                    : entry.isUpgrade() ? "UPGRADE" : "FIT";
            Color base = afford
                    ? (rarity == null ? Misc.getBasePlayerColor() : rarity.color)
                    : Misc.getGrayColor();

            buyId = new Object();

            info.setButtonFontOrbitron20Bold();
            ButtonAPI button = info.addButton(label, buyId, base, Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.TL_BR, 240f, 34f, 20f);
            info.setButtonFontDefault();

            button.setEnabled(afford);

            //tooltip only stacks vertically, so add below then re-anchor beside the real button
            if (Global.getSettings().isDevMode() && !entry.isCurio()) {
                devBuyId = new Object();

                ButtonAPI dev = info.addButton("DEV", devBuyId, Misc.getHighlightColor(),
                        Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 70f, 34f, 10f);

                dev.getPosition().rightOfMid(button, 10f);
            }
        }

        /** The selected entry, falling back to the first visible one if the selection is stale. */
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
            if (devBuyId != null && buttonId == devBuyId) {
                ShopEntry entry = getSelected();
                if (entry == null || !entry.devBuy()) return;

                Global.getSoundPlayer().playUISound(SOUND_BOUGHT, 1f, 1f);

                refreshWallet();
                rebuild(true);
                return;
            }

            if (buttonId != buyId) return;

            ShopEntry entry = getSelected();
            if (entry == null || !entry.buy()) return;

            Global.getSoundPlayer().playUISound(SOUND_BOUGHT, 1f, 1f);

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

        /** Escape closes the dialog without confirmation. */
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
