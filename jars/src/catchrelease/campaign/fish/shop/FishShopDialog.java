package catchrelease.campaign.fish.shop;

import catchrelease.ui.PaneWidgets;
import catchrelease.ui.ShopUi;
import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FishShopDialog implements InteractionDialogPlugin {

    public static final float WIDTH = 920f;
    public static final float HEIGHT = 640f;
    public static final float PAD = 12f;

    public static final float HEADER_HEIGHT = 46f;
    public static final float LIST_WIDTH = 320f;

    public static final float ROW_WIDTH = LIST_WIDTH - 6f;
    public static final float ROW_HEIGHT = 26f;
    public static final float DETAIL_GAP = 14f;
    public static final String SOUND_BOUGHT = "ui_upgrade_industry";

    public static final float TOOLTIP_WIDTH = 320f;
    public static final float MAIN_TAB_HEIGHT = 28f;
    public static final float CATEGORY_TAB_HEIGHT = 44f;
    public static final float TAB_GAP = 4f;

    public static final float LEAVE_WIDTH = 120f;
    public static final float LEAVE_HEIGHT = 26f;

    public static final float UNDO_WIDTH = 200f;
    public static final float UNDO_HEIGHT = 26f;
    public static final String SOUND_UNDONE = "ui_cancel_construction_or_upgrade_industry";

    protected InteractionDialogAPI dialog;
    protected Delegate delegate;
    protected boolean closed;
    protected final OnClose onClose;

    public interface OnClose {

        void onShopClosed(InteractionDialogAPI dialog);
    }

    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin,
            ShopRowPlugin.Host, ShopTabPlugin.Host, ShopHeaderPlugin.Purse {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;
        protected final List<ShopEntry> entries = new ArrayList<>();
        protected String selectedKey;
        protected ShopEntry.Kind mainTab = ShopEntry.Kind.UPGRADE;
        protected ShopGroup category = ShopGroup.SEARCHLIGHTS;
        protected TooltipMakerAPI list;

        protected Map<FishRarity, Integer> wallet = new HashMap<>();
        protected int credits = 0;
        protected final List<UIComponentAPI> added = new ArrayList<>();
        protected final List<Receipt> purchases = new ArrayList<>();

        protected TooltipMakerAPI detail;
        protected PositionAPI listViewport;
        protected PositionAPI pos;

        protected class Receipt {

            final String entryKey;
            final String markKey;
            final List<Object[]> fishAboard;
            final float creditsAboard;
            final boolean marked;
            final int statLevel;
            final boolean tackleOwned;
            final Tackle fitted;

            Receipt(ShopEntry entry) {
                entryKey = entry.getKey();
                markKey = ShopMarks.getMarkKey(entry);
                fishAboard = snapshotFish();
                creditsAboard = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
                marked = ShopMarks.isMarked(markKey);

                statLevel = entry.isUpgrade() ? entry.getLevel() : -1;
                tackleOwned = entry.kind == ShopEntry.Kind.TACKLE
                        && TackleManager.isOwned(entry.tackle);
                fitted = entry.kind == ShopEntry.Kind.TACKLE
                        ? TackleManager.get(entry.rig) : null;
            }
        }

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            buildEntries();
            selectFirstVisible();
            refreshWallet();

            build();
        }

        protected void buildEntries() {
            List<UpgradeStat> stats = new ArrayList<>(UpgradeManager.getInstance().getAll().values());

            // "example" is a format-documentation row, not for sale; catch tuning stats aren't equipment
            stats.removeIf(stat -> stat.id == null || stat.id.equalsIgnoreCase("example"));
            stats.removeIf(stat -> ShopGroup.forStat(stat) == ShopGroup.THE_CATCH);
            stats.sort(Comparator.comparing(stat -> stat.id));

            // a shelf for gear nobody has been handed yet is a shop advertising a game the player is not playing, and it gives away what the introduction has left to give
            for (ShopGroup group : ShopGroup.values()) {
                if (!group.isUnlocked()) continue;

                for (UpgradeStat stat : stats) {
                    if (ShopGroup.forStat(stat) == group) entries.add(ShopEntry.of(stat));
                }
            }

            // iterate every Fit rather than a fixed list, so a new rig automatically gets a shelf
            for (Tackle.Fit rig : Tackle.Fit.values()) {
                if (!rig.isRig()) continue;

                ShopGroup shelf = ShopGroup.forRig(rig);
                if (shelf != null && !shelf.isUnlocked()) continue;

                List<Tackle> options = TackleManager.getOptions(rig);
                options.remove(Tackle.NONE);
                options.removeIf(tackle -> !ShopSchematics.has(tackle));

                if (options.isEmpty()) continue;

                entries.add(ShopEntry.of(Tackle.NONE, rig));

                for (Tackle tackle : options) entries.add(ShopEntry.of(tackle, rig));
            }

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
            buildUndo();
            buildLeave();
        }

        protected void buildLeave() {
            CustomPanelAPI leave = panel.createCustomPanel(LEAVE_WIDTH, LEAVE_HEIGHT,
                    new catchrelease.ui.PaneWidgets.TextButton(() -> "LEAVE",
                            () -> true, () -> {
                                if (callbacks != null) callbacks.dismissDialog();
                            }));

            panel.addComponent(leave).inTL(WIDTH - PAD - LEAVE_WIDTH, HEIGHT - PAD - LEAVE_HEIGHT);
            added.add(leave);
        }

        protected void buildUndo() {
            TooltipMakerAPI footer = panel.createUIElement(UNDO_WIDTH, UNDO_HEIGHT, false);

            CustomPanelAPI undo = panel.createCustomPanel(UNDO_WIDTH, UNDO_HEIGHT,
                    new catchrelease.ui.PaneWidgets.TextButton(
                            () -> "UNDO LAST PURCHASE", () -> !purchases.isEmpty(),
                            this::undoClicked));
            footer.addCustom(undo, 0f);
            footer.addTooltipToPrevious(new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return 280f;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                                          Object tooltipParam) {
                    tooltip.addPara("Takes back the newest purchase made this visit and restores"
                            + " its credits and fish exactly as they were.", 0f);
                }
            }, TooltipMakerAPI.TooltipLocation.ABOVE);

            place(footer, PAD, HEIGHT - PAD - UNDO_HEIGHT);
        }

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

        protected void buildTabs() {
            float top = PAD + HEADER_HEIGHT + 10f;

            List<ShopEntry.Kind> kinds = getStockedKinds();
            if (kinds.isEmpty()) return;

            float mainWidth = (LIST_WIDTH - (kinds.size() - 1) * TAB_GAP) / kinds.size();

            for (int i = 0; i < kinds.size(); i++) {
                ShopEntry.Kind kind = kinds.get(i);

                addTab(kind, kind.tabTitle, kind.iconId,
                        false, PAD + i * (mainWidth + TAB_GAP), top, mainWidth, MAIN_TAB_HEIGHT);
            }

            float categoryTop = top + MAIN_TAB_HEIGHT + TAB_GAP;
            ShopGroup[] groups = getCategories(mainTab);

            if (groups.length == 0) return;

            float categoryWidth = (LIST_WIDTH - (groups.length - 1) * TAB_GAP) / groups.length;

            for (int i = 0; i < groups.length; i++) {
                addTab(groups[i], groups[i].tabTitle, groups[i].iconId,
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
            float height = HEIGHT - top - PAD - UNDO_HEIGHT - 8f;

            list = panel.createUIElement(LIST_WIDTH, height, true);

            for (ShopEntry entry : getVisible()) {
                CustomPanelAPI row = panel.createCustomPanel(ROW_WIDTH, ROW_HEIGHT,
                        new ShopRowPlugin(entry, this));
                list.addCustom(row, 3f);

                if (entry.isLocked()) {
                    list.addTooltipTo(createLockedTooltip(entry), row,
                            TooltipMakerAPI.TooltipLocation.BELOW);
                    continue;
                }

                // the ring's explanation rides a transparent hotspot over its slot, so the stock tooltip machinery scopes it to the ring rather than the whole row
                if (ShopMarks.isMarked(entry) || ShopMarks.isMarkable(entry)) {
                    CustomPanelAPI ring = panel.createCustomPanel(ShopRowPlugin.MARK_SLOT,
                            ROW_HEIGHT, new BaseCustomUIPanelPlugin() {
                            });
                    row.addComponent(ring).inTL(ShopRowPlugin.ACCENT_WIDTH, 0f);

                    list.addTooltipTo(createMarkTooltip(entry), ring,
                            TooltipMakerAPI.TooltipLocation.BELOW);
                }

                // the grey tier squares get the same treatment - hotspot sized to the pip row ShopRowPlugin.renderState draws at the row's right edge
                if (entry.isUpgrade() && !entry.isMaxed()
                        && ShopSchematics.hasMissingSchematic(entry.stat)) {
                    float pipsWidth = ShopUi.getPipRowWidth(entry.getMaxLevel(),
                            ShopRowPlugin.PIP_SIZE, ShopRowPlugin.PIP_GAP);
                    CustomPanelAPI pips = panel.createCustomPanel(pipsWidth, ROW_HEIGHT,
                            new BaseCustomUIPanelPlugin() {
                            });
                    row.addComponent(pips).inTL(ROW_WIDTH - ShopRowPlugin.PAD_SIDE - pipsWidth, 0f);

                    list.addTooltipTo(createPipTooltip(), pips,
                            TooltipMakerAPI.TooltipLocation.BELOW);
                }
            }

            listViewport = place(list, PAD, top);
        }

        protected TooltipMakerAPI.TooltipCreator createLockedTooltip(ShopEntry entry) {
            return new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return TOOLTIP_WIDTH;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                                          Object tooltipParam) {
                    tooltip.addPara("Schematic required", Misc.getHighlightColor(), 0f);
                    tooltip.addPara("Upgrade tier %s is one of this upgrade's final two tiers."
                                    + " Fishing jobs can offer its schematic.", 6f,
                            Misc.getHighlightColor(), String.valueOf(entry.getLevel() + 1));
                    tooltip.addPara("Each schematic unlocks one upgrade tier for purchase; it does not"
                            + " grant the upgrade.", Misc.getGrayColor(), 6f);
                    tooltip.addPara("Marking this tier dots its schematic on job offers until"
                            + " the plan is learned.", Misc.getGrayColor(), 6f);
                }
            };
        }

        protected TooltipMakerAPI.TooltipCreator createMarkTooltip(ShopEntry entry) {
            return new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return TOOLTIP_WIDTH;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                                          Object tooltipParam) {
                    boolean marked = ShopMarks.isMarked(entry);

                    tooltip.addPara("Shopping list", Misc.getHighlightColor(), 0f);
                    tooltip.addPara(marked ? "On the list - click the ring to clear it."
                            : "Click the ring to save for this purchase.", 6f);
                    tooltip.addPara("Fish that would pay for it wear the %s everywhere, and the"
                                    + " route planner lists the entry at the top.", 6f,
                            Misc.getGrayColor(), Misc.getHighlightColor(), "yellow dot");
                }
            };
        }

        protected TooltipMakerAPI.TooltipCreator createPipTooltip() {
            return new BaseTooltipCreator() {
                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return TOOLTIP_WIDTH;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded,
                                          Object tooltipParam) {
                    tooltip.addPara("%s - find a schematic to unlock", 0f,
                            Misc.getTextColor(), Misc.getHighlightColor(), "Locked");
                }
            };
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

            // hotspot mirroring ShopDetailHeaderPlugin.renderLadder's pip position: text column, one title line and its two pads down from the panel top
            if (entry.isUpgrade() && !entry.isMaxed()
                    && ShopSchematics.hasMissingSchematic(entry.stat)) {
                LazyFont titleFont = ShopUi.getTitleFont();
                float nameHeight = titleFont == null ? 20f : titleFont.getBaseHeight();
                float pipsWidth = ShopUi.getPipRowWidth(entry.getMaxLevel(),
                        ShopDetailHeaderPlugin.PIP_SIZE, ShopDetailHeaderPlugin.PIP_GAP);

                CustomPanelAPI pips = panel.createCustomPanel(pipsWidth,
                        ShopDetailHeaderPlugin.PIP_SIZE + 4f, new BaseCustomUIPanelPlugin() {
                        });
                head.addComponent(pips).inTL(
                        ShopDetailHeaderPlugin.BOX_SIZE + ShopDetailHeaderPlugin.TEXT_GAP,
                        6f + nameHeight + 10f - 2f);

                info.addTooltipTo(createPipTooltip(), pips,
                        TooltipMakerAPI.TooltipLocation.BELOW);
            }

            info.addPara(entry.getDescription(), 12f);

            if (entry.isUpgrade() && !entry.isMaxed()) {
                info.addPara("Now %s - next tier %s", 12f, Misc.getHighlightColor(),
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

            // empty slot never had a price; an owned module was already paid for once
            if (entry.tackle == Tackle.NONE) {
                info.addPara("No charge for emptying a slot.", Misc.getGrayColor(), 16f);
                return;
            }

            if (entry.isOwned()) {
                info.addPara("Already yours - fitting it costs nothing.", Misc.getGrayColor(), 16f);
                return;
            }

            if (entry.isLocked()) {
                info.addPara("Schematic required.", Misc.getNegativeHighlightColor(), 16f);
                info.addPara("Fishing jobs can offer the schematic for this upgrade tier.",
                        Misc.getGrayColor(), 4f);
                return;
            }

            ShopPricing.Price price = entry.getPrice();
            if (price == null) return; // defensive; the branches above cover the only null cases

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

            String label = entry.isCurio() ? (entry.isOn() ? "SWITCH OFF" : "SWITCH ON")
                    : entry.isUpgrade() ? "UPGRADE" : "FIT";

            boolean dev = Global.getSettings().isDevMode() && !entry.isCurio();

            CustomPanelAPI row = panel.createCustomPanel(240f + (dev ? 80f : 0f), 30f,
                    new com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin() {
                    });

            CustomPanelAPI buy = panel.createCustomPanel(240f, 30f,
                    new catchrelease.ui.PaneWidgets.TextButton(() -> label,
                            entry::canAfford, () -> buyClicked(entry, false)));
            row.addComponent(buy).inTL(0f, 0f);

            if (dev) {
                CustomPanelAPI grant = panel.createCustomPanel(70f, 30f,
                        new catchrelease.ui.PaneWidgets.TextButton(() -> "DEV",
                                () -> true, () -> buyClicked(entry, true)));
                row.addComponent(grant).inTL(250f, 0f);
            }

            info.addCustom(row, 20f);
        }

        protected void buyClicked(ShopEntry entry, boolean free) {
            Receipt receipt = !free && entry.getPrice() != null
                    ? new Receipt(entry) : null;

            if (!(free ? entry.devBuy() : entry.buy())) return;

            if (receipt != null) purchases.add(receipt);

            Global.getSoundPlayer().playUISound(SOUND_BOUGHT, 1f, 1f);

            refreshWallet();
            rebuild(true);
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

        protected void undoClicked() {
            if (purchases.isEmpty()) return;

            Receipt receipt = purchases.remove(purchases.size() - 1);
            ShopEntry entry = findEntry(receipt.entryKey);
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                if (data == null) continue;
                if (!FishItems.FISH.equals(data.getId()) && !FishItems.isContainer(data)) continue;

                cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, stack.getSize());
            }
            for (Object[] stack : receipt.fishAboard) {
                cargo.addSpecial((SpecialItemData) stack[0], (Integer) stack[1]);
            }
            cargo.getCredits().set(receipt.creditsAboard);

            if (entry != null && entry.isUpgrade()) {
                UpgradeManager.getInstance().setLevel(entry.stat.id, receipt.statLevel);
                ShopEntry.stopAbility(StatIds.getAbilityId(entry.stat.id));
            } else if (entry != null && entry.kind == ShopEntry.Kind.TACKLE) {
                if (!receipt.tackleOwned) TackleManager.consume(entry.tackle);
                TackleManager.fit(entry.rig, receipt.fitted == null ? Tackle.NONE : receipt.fitted);
                ShopEntry.stopAbility(entry.getRigAbilityId());
            }

            if (receipt.marked) ShopMarks.mark(receipt.markKey);

            selectedKey = receipt.entryKey;
            refreshWallet();
            rebuild(true);

            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                    "Refunded " + (entry != null ? entry.getName() : "the last purchase"));
            Global.getSoundPlayer().playUISound(SOUND_UNDONE, 1f, 1f);
        }

        protected ShopEntry findEntry(String key) {
            for (ShopEntry entry : entries) {
                if (entry.getKey().equals(key)) return entry;
            }

            return null;
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
        }

        @Override
        public CustomUIPanelPlugin getCustomPanelPlugin() {
            return this;
        }

        @Override
        public float getNoiseAlpha() {
            return 0f;
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public void reportDismissed(int option) {
            FishShopDialog.this.close();
        }

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
            pos = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            ShopUi.drawPanel(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(),
                    0.7f, alphaMult);
        }

        @Override
        public void render(float alphaMult) {
        }
    }

    public FishShopDialog() {
        this(null);
    }

    public FishShopDialog(OnClose onClose) {
        this.onClose = onClose;
    }

    public static boolean open() {
        return Global.getSector().getCampaignUI()
                .showInteractionDialog(new FishShopDialog(), Global.getSector().getPlayerFleet());
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        closed = false;

        // returns anything a save is still holding in shop storage - that button no longer exists
        ShopStorage.reclaim();

        dialog.setPromptText("");
        dialog.hideVisualPanel();
        dialog.hideTextPanel();
        dialog.setBackgroundDimAmount(0.6f);

        delegate = new Delegate();

        dialog.getOptionPanel().clearOptions();
        dialog.showCustomVisualDialog(WIDTH, HEIGHT, delegate);
    }

    protected void close() {
        if (closed || dialog == null) return;

        closed = true;

        // the New! tags are first-visit news, and this visit has now seen them
        ShopSchematics.clearAllFresh();

        if (onClose == null) {
            dialog.dismiss();
            return;
        }

        onClose.onShopClosed(dialog);
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
