package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.chain.SensorBuild;
import catchrelease.campaign.fish.fisherman.chain.TreasureRumour;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.jobs.FishHandoffPicker;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tackle.RigLoadout;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.config.FishingSounds;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.ui.FishPurse;
import catchrelease.ui.ListRow;
import catchrelease.ui.ShopUi;
import catchrelease.ui.UiKit;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FishShopDialog implements InteractionDialogPlugin {

    public static final float WIDTH = 920f;
    public static final float HEIGHT = 640f;
    public static final float PAD = 12f;
    public static final float CONTENT_WIDTH = WIDTH - 2f * PAD;
    public static final float CONTENT_HEIGHT = HEIGHT - 2f * PAD;
    public static final String TITLE = "Outfitter";
    public static final String SOUND_BOUGHT = "ui_upgrade_industry";
    public static final String SOUND_UNDONE = "ui_cancel_construction_or_upgrade_industry";

    public static final float TAB_Y = 30f;
    public static final float TAB_WIDTH = 104f;
    public static final float TAB_PITCH = 108f;
    public static final float RULE_Y = TAB_Y + UiKit.BUTTON_HEIGHT;
    public static final float TOP = 62f;
    public static final float CREDITS_ICON = 18f;
    public static final float CREDITS_GAP = 4f;
    public static final float COUNTER_GAP = 24f;

    public static final float LIST_WIDTH = 320f;
    public static final float LIST_BOTTOM = 580f;
    public static final float LIST_GAP = 6f;
    public static final float RIG_TAB_HEIGHT = 44f;
    public static final float ROW_HEIGHT = 26f;
    public static final float ROW_GAP = 4f;
    public static final float ROW_PITCH = ROW_HEIGHT + ROW_GAP;
    public static final float SLOT_WIDTH = 158f;
    public static final float SLOT_HEIGHT = 46f;
    public static final float SLOT_PITCH = 162f;
    public static final float DIVIDER_X = 332f;

    public static final float DETAIL_X = 344f;
    public static final float DETAIL_WIDTH = CONTENT_WIDTH - DETAIL_X;
    public static final float BOX_SIZE = 72f;
    public static final float TILE_INSET = 4f;
    public static final float ICON_SIZE = 56f;
    public static final float HEAD_TEXT_X = DETAIL_X + 84f;
    public static final float NAME_Y = 66f;
    public static final float SUB_Y = 94f;
    public static final float SUB_PIPS_Y = 97f;
    public static final float DESCRIPTION_Y = 150f;
    public static final float PARA_GAP = 12f;
    public static final float LINE_STEP = 32f;
    public static final float HOLE_ALPHA = 0.3f;

    public static final float PRICE_LABEL_WIDTH = 132f;
    public static final float PRICE_ROW = 22f;
    public static final float PRICE_ICON = 18f;
    public static final float PRICE_ICON_GAP = 4f;
    public static final float PRICE_BOTTOM = 34f;

    public static final float MAIN_BUTTON_WIDTH = 160f;
    public static final float SELECT_WIDTH = 120f;
    public static final float DEV_WIDTH = 70f;
    public static final float BUTTON_GAP = 6f;
    public static final float STATUS_Y = 32f;
    public static final float STATUS_GAP = 4f;

    public static final float FOOTER_Y = CONTENT_HEIGHT - UiKit.BUTTON_HEIGHT;
    public static final float UNDO_WIDTH = 200f;
    public static final float LEAVE_WIDTH = 120f;
    public static final float UNDO_TOOLTIP_WIDTH = 280f;
    public static final float TOOLTIP_WIDTH = 320f;

    protected static final Runnable NOTHING = () -> {
    };

    protected InteractionDialogAPI dialog;
    protected Delegate delegate;
    protected boolean closed;
    protected boolean reopenPending;
    protected final OnClose onClose;

    public interface OnClose {

        void onShopClosed(InteractionDialogAPI dialog);
    }

    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin,
            ShopRowPlugin.Host, ShopTabPlugin.Host, ShopSlotPlugin.Host {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;
        protected PositionAPI pos;
        protected CustomPanelAPI body;
        protected TooltipMakerAPI list;
        protected PositionAPI listViewport;
        // kept across rebuilds and the payment picker's reopen; reset when the shelf changes
        protected float listOffset;

        protected final List<Runnable> pending = new ArrayList<>();
        protected boolean dirty;

        protected final List<ShopEntry> entries = new ArrayList<>();
        protected String selectedKey;
        protected ShopEntry.Kind mainTab = ShopEntry.Kind.UPGRADE;
        protected ShopGroup category = ShopGroup.SEARCHLIGHTS;
        // kept across the payment picker's reopen; reset when the shelf changes
        protected int targetSlot = 0;

        protected Map<FishRarity, Integer> wallet = new HashMap<>();
        protected int credits = 0;
        protected final List<Receipt> purchases = new ArrayList<>();

        protected ShopEntry selectedPaymentEntry;
        protected Receipt selectedPaymentReceipt;

        protected class Receipt {

            final String entryKey;
            final String markKey;
            final List<Object[]> fishAboard;
            final float creditsAboard;
            final boolean marked;
            final int statLevel;
            final boolean tackleOwned;
            final Tackle.Fit rig;
            final Tackle tackle;
            final int slot;
            final Tackle fitted;

            Receipt(ShopEntry entry, int slot) {
                entryKey = entry.getKey();
                markKey = ShopMarks.getMarkKey(entry);
                fishAboard = snapshotFish();
                creditsAboard = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
                marked = ShopMarks.isMarked(markKey);

                statLevel = entry.isUpgrade() ? entry.getLevel() : -1;
                tackleOwned = entry.kind == ShopEntry.Kind.TACKLE
                        && TackleManager.isOwned(entry.tackle);
                rig = entry.rig;
                tackle = entry.tackle;
                this.slot = slot;
                fitted = entry.kind == ShopEntry.Kind.TACKLE
                        ? TackleManager.getSlot(entry.rig, slot) : null;
            }
        }

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            body = null;
            list = null;
            listViewport = null;
            pos = null;
            pending.clear();
            dirty = false;
            entries.clear();

            buildEntries();
            reconcileTabs();
            selectFirstVisible();
            refreshWallet();

            rebuild();
        }

        // drops the remembered tab and rig back onto something stocked: the fields start on the lamp shelf, which
        // stays locked until the introduction's third catch, and a reopen after Select can land on a stale pair
        protected void reconcileTabs() {
            List<ShopEntry.Kind> kinds = getStockedKinds();
            if (kinds.isEmpty()) return;

            if (!kinds.contains(mainTab)) mainTab = kinds.get(0);

            ShopGroup[] groups = getCategories(mainTab);
            if (groups.length == 0) return;

            for (ShopGroup group : groups) {
                if (group == category) return;
            }

            category = groups[0];
            targetSlot = 0;
        }

        protected void buildEntries() {
            List<UpgradeStat> stats = new ArrayList<>(UpgradeManager.getInstance().getAll().values());

            stats.removeIf(stat -> ShopGroup.forStat(stat) == ShopGroup.THE_CATCH);
            stats.sort(Comparator.comparing(stat -> stat.id));

            // a shelf for gear nobody has been handed yet gives away what the introduction has left to give
            for (ShopGroup group : ShopGroup.values()) {
                if (!group.isUnlocked()) continue;

                for (UpgradeStat stat : stats) {
                    if (ShopGroup.forStat(stat) == group) entries.add(ShopEntry.of(stat));
                }
            }

            for (Tackle.Fit rig : Tackle.Fit.values()) {
                if (!rig.isRig()) continue;

                ShopGroup shelf = ShopGroup.forRig(rig);
                if (shelf != null && !shelf.isUnlocked()) continue;

                List<Tackle> options = TackleManager.getOptions(rig);
                options.remove(Tackle.NONE);

                // a module never offered keeps its place at the end of the list, nameless
                List<Tackle> undiscovered = new ArrayList<>(options);
                undiscovered.removeIf(tackle -> ShopSchematics.has(tackle) || ShopSchematics.isSeen(tackle));
                options.removeAll(undiscovered);

                if (options.isEmpty()) continue;

                entries.add(ShopEntry.of(Tackle.NONE, rig));

                for (Tackle tackle : options) entries.add(ShopEntry.of(tackle, rig));
                for (Tackle tackle : undiscovered) entries.add(ShopEntry.of(tackle, rig));
            }

            for (CrabWares ware : CrabWares.getSwitchable()) entries.add(ShopEntry.of(ware));
        }

        protected void refreshWallet() {
            wallet = FishCurrency.count();

            credits = Global.getSector().getPlayerFleet() == null ? 0
                    : (int) Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        }

        protected Runnable later(Runnable action) {
            return () -> pending.add(action);
        }

        protected void rememberScroll() {
            ScrollPanelAPI scroller = list == null ? null : list.getExternalScroller();
            if (scroller != null) listOffset = scroller.getYOffset();
        }

        protected void rebuild() {
            if (panel == null) return;

            rememberScroll();
            if (body != null) panel.removeComponent(body);

            list = null;
            listViewport = null;
            body = UiKit.plain(panel, CONTENT_WIDTH, CONTENT_HEIGHT);
            panel.addComponent(body).inTL(PAD, PAD);

            buildHeader();
            float top = buildSlots(buildRigTabs());
            buildList(top);
            UiKit.vrule(body, DIVIDER_X, TOP, LIST_BOTTOM - TOP);
            buildDetail();
            buildFooter();
        }

        protected void buildHeader() {
            UiKit.title(body, CONTENT_WIDTH, TITLE, null, this::describeOutfitter);

            List<ShopEntry.Kind> kinds = getStockedKinds();
            for (int i = 0; i < kinds.size(); i++) {
                ShopEntry.Kind kind = kinds.get(i);
                boolean on = kind == mainTab;

                UiKit.tab(body, i * TAB_PITCH, TAB_Y, TAB_WIDTH, kind.tabTitle, on,
                        on ? NOTHING : later(() -> onTabClicked(kind)));
            }

            UiKit.rule(body, 0f, RULE_Y, CONTENT_WIDTH);

            SpriteAPI coin = Global.getSettings().getSprite("fleetScreen", "icon_logistics_credits");
            float coinLeft = UiKit.iconValue(body, CONTENT_WIDTH, TAB_Y, UiKit.BUTTON_HEIGHT, coin,
                    Color.WHITE, CREDITS_ICON, CREDITS_GAP, Misc.getDGSCredits(credits),
                    Misc.getHighlightColor());
            float creditsLeft = UiKit.labelLeftOf(body, coinLeft - FishPurse.LABEL_GAP, TAB_Y,
                    UiKit.BUTTON_HEIGHT, "Credits", Misc.getGrayColor());

            FishPurse.add(body, creditsLeft - COUNTER_GAP, TAB_Y, UiKit.BUTTON_HEIGHT, wallet);
        }

        protected void describeOutfitter(TooltipMakerAPI tooltip) {
            tooltip.addPara("UPGRADES", Misc.getBasePlayerColor(), 0f);
            tooltip.addPara("Buy tiers in order. All purchased tiers and upgrades remain"
                    + " active together.", 8f);

            tooltip.addPara("EQUIPMENT", Misc.getBasePlayerColor(), 12f);
            tooltip.addPara("Each rig has one equipment slot of its own. Swap owned"
                    + " equipment freely at no cost. Equipment that is consumed on use"
                    + " says so in its description.", 8f);
            if (anySecondSlot()) {
                tooltip.addPara("A fishing ship in your fleet adds a second slot to its rig and brings a"
                        + " module you can fit while the ship stays. A module cannot fill both slots, and"
                        + " modules with similar or opposing effects cannot share a rig.", 8f);
            }

            tooltip.addPara("PURCHASING", Misc.getBasePlayerColor(), 12f);
            tooltip.addPara("Payment takes only fish that meet the requirement, using"
                    + " loose fish before opening boxes or piles. When several fish of"
                    + " the same species are required but none is named, it uses the"
                    + " eligible species you have most of.", 8f);
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
            ShopEntry selected = getSelected();

            selectedKey = selected == null ? null : selected.getKey();
        }

        // the extras have one shelf and no rig, so their list starts under the tab line
        protected float buildRigTabs() {
            if (mainTab == ShopEntry.Kind.CURIO) return TOP;

            ShopGroup[] groups = getCategories(mainTab);
            if (groups.length == 0) return TOP;

            for (int i = 0; i < groups.length; i++) {
                CustomPanelAPI tab = body.createCustomPanel(TAB_WIDTH, RIG_TAB_HEIGHT,
                        new ShopTabPlugin(groups[i], groups[i].tabTitle, groups[i].iconId, this));
                body.addComponent(tab).inTL(i * TAB_PITCH, TOP);
            }

            return TOP + RIG_TAB_HEIGHT + LIST_GAP;
        }

        @Override
        public boolean isActiveTab(Object id) {
            return id == category;
        }

        @Override
        public void onTabClicked(Object id) {
            if (id instanceof ShopEntry.Kind kind) {
                mainTab = kind;

                ShopGroup[] groups = getCategories(mainTab);
                if (groups.length > 0) category = groups[0];
            } else if (id instanceof ShopGroup group) {
                category = group;
            } else {
                return;
            }

            targetSlot = 0;
            selectFirstVisible();
            rememberScroll();
            listOffset = 0f;
            list = null;
            dirty = true;
        }

        protected Tackle.Fit getShelfRig() {
            return mainTab == ShopEntry.Kind.TACKLE && category != null ? category.getRig() : null;
        }

        protected boolean hasTwoSlots() {
            Tackle.Fit rig = getShelfRig();

            return rig != null && FishingShips.hasSecondSlot(rig);
        }

        @Override
        public int getTargetSlot() {
            return hasTwoSlots() ? targetSlot : 0;
        }

        @Override
        public void onSlotClicked(int slot) {
            targetSlot = slot;
            dirty = true;
        }

        // the two slot tiles over the module list while the shelf's rig has its second slot; returns where the list starts
        protected float buildSlots(float top) {
            if (!hasTwoSlots()) return top;

            Tackle.Fit rig = getShelfRig();
            boolean compact = needsCompactSlotText(rig);

            for (int slot = 0; slot < RigLoadout.SLOTS; slot++) {
                int index = slot;
                CustomPanelAPI cell = body.createCustomPanel(SLOT_WIDTH, SLOT_HEIGHT,
                        new ShopSlotPlugin(slot, TackleManager.getSlot(rig, slot), compact, this));
                body.addComponent(cell).inTL(slot * SLOT_PITCH, top);

                UiKit.tooltip(body, cell, TOOLTIP_WIDTH, TooltipLocation.BELOW, false,
                        tooltip -> describeSlot(tooltip, rig, index));
            }

            return top + SLOT_HEIGHT + LIST_GAP;
        }

        // one font for the whole row, so a single long name cannot put the two tiles in different typefaces
        protected boolean needsCompactSlotText(Tackle.Fit rig) {
            LazyFont font = ShopUi.getBodyFont();
            if (font == null) return false;

            float room = ShopSlotPlugin.getTextWidth(SLOT_WIDTH);

            for (int slot = 0; slot < RigLoadout.SLOTS; slot++) {
                Tackle fitted = TackleManager.getSlot(rig, slot);
                String name = fitted == Tackle.NONE ? "Empty" : fitted.name;

                if (font.calcWidth(name, font.getBaseHeight()) > room) return true;
            }

            return false;
        }

        protected void describeSlot(TooltipMakerAPI tooltip, Tackle.Fit rig, int slot) {
            tooltip.addPara("Slot " + (slot + 1), Misc.getHighlightColor(), 0f);
            tooltip.addPara(getTargetSlot() == slot
                    ? "Fit puts the selected module here."
                    : "Click to select this slot. Fit then puts the selected module here.", 6f);
            if (slot == 0) return;

            String hull = getShipName(rig);
            Tackle module = FishingShips.getShipModule(rig);
            if (module == null) {
                tooltip.addPara("Provided by your %s. If the ship leaves your fleet, this slot is emptied.",
                        6f, Misc.getHighlightColor(), hull);
            } else {
                tooltip.addPara("Provided by your %s. If the ship leaves your fleet, this slot is emptied"
                        + " and the %s leaves the rig.", 6f, Misc.getHighlightColor(), hull, module.name);
            }
        }

        protected boolean anySecondSlot() {
            for (Tackle.Fit rig : Tackle.Fit.values()) {
                if (rig.isRig() && FishingShips.hasSecondSlot(rig)) return true;
            }

            return false;
        }

        protected String getShipName(Tackle.Fit rig) {
            FishingShips.Ship ship = FishingShips.forRig(rig);
            try {
                return Global.getSettings().getHullSpec(ship.hullId).getHullName();
            } catch (RuntimeException e) {
                return "fishing ship";
            }
        }

        protected void buildList(float top) {
            int rows = Math.max(1, (int) ((LIST_BOTTOM - top + ROW_GAP) / ROW_PITCH));
            float height = rows * ROW_PITCH - ROW_GAP;

            // the extra inset on the right is the scrollbar's
            list = body.createUIElement(LIST_WIDTH + 2f * UiKit.INSET, height, true);

            List<ShopEntry> visible = getVisible();
            for (int i = 0; i < visible.size(); i++) {
                ShopEntry entry = visible.get(i);
                CustomPanelAPI row = body.createCustomPanel(LIST_WIDTH, ROW_HEIGHT,
                        new ShopRowPlugin(entry, this));
                list.addCustom(row, i == 0 ? 0f : ROW_GAP);

                addRowTooltips(row, entry);
            }

            listViewport = body.addUIElement(list).inTL(-UiKit.INSET, top);

            ScrollPanelAPI scroller = list.getExternalScroller();
            if (scroller != null) {
                float max = Math.max(0f, list.getHeightSoFar() - height);
                scroller.setYOffset(Math.max(0f, Math.min(max, listOffset)));
            }
        }

        // transparent hotspots over the drawn parts, so each tooltip covers only its part of the row
        protected void addRowTooltips(CustomPanelAPI row, ShopEntry entry) {
            if (entry.isUndiscovered()) {
                CustomPanelAPI spot = UiKit.plain(row, LIST_WIDTH, ROW_HEIGHT);
                row.addComponent(spot).inTL(0f, 0f);

                UiKit.tooltip(body, spot, TOOLTIP_WIDTH, TooltipLocation.BELOW, false, tooltip -> {
                    tooltip.addPara(ShopRowPlugin.UNDISCOVERED, Misc.getHighlightColor(), 0f);
                    tooltip.addPara("You have not come across this schematic yet.", 6f);
                });
                return;
            }

            if (ShopMarks.isMarked(entry) || ShopMarks.isMarkable(entry)) {
                CustomPanelAPI mark = UiKit.plain(row, ShopRowPlugin.MARK_SLOT, ROW_HEIGHT);
                row.addComponent(mark).inTL(ListRow.ACCENT_WIDTH, 0f);

                UiKit.tooltip(body, mark, TOOLTIP_WIDTH, TooltipLocation.BELOW, false,
                        tooltip -> describeMark(tooltip, entry));
            }

            int slot = getTargetSlot();
            Tackle blocker = entry.getBlocker(slot);
            if (blocker != null && !entry.isFittedIn(slot)) {
                CustomPanelAPI spot = UiKit.plain(row, LIST_WIDTH - ShopRowPlugin.NAME_LEFT, ROW_HEIGHT);
                row.addComponent(spot).inTL(ShopRowPlugin.NAME_LEFT, 0f);

                UiKit.tooltip(body, spot, TOOLTIP_WIDTH, TooltipLocation.BELOW, false,
                        tooltip -> describeBlocked(tooltip, entry, blocker, slot));
            }

            if (entry.isUpgrade() && !entry.isMaxed()) {
                float left = LIST_WIDTH - ShopRowPlugin.PAD_SIDE
                        - ShopRowPlugin.getPipRowWidth(entry.getMaxLevel());

                addPipTooltips(row, entry, left, (ROW_HEIGHT - ShopRowPlugin.PIP_SIZE) * 0.5f);
            }
        }

        protected void addPipTooltips(CustomPanelAPI parent, ShopEntry entry, float left, float top) {
            for (int rung = 1; rung <= entry.getMaxLevel(); rung++) {
                int tier = rung;
                CustomPanelAPI pip = UiKit.plain(parent, ShopRowPlugin.PIP_SIZE, ShopRowPlugin.PIP_SIZE);
                parent.addComponent(pip).inTL(Math.round(left + (rung - 1) * ShopRowPlugin.PIP_PITCH),
                        Math.round(top));

                UiKit.tooltip(body, pip, TOOLTIP_WIDTH, TooltipLocation.BELOW, false,
                        tooltip -> describePip(tooltip, entry, tier));
            }
        }

        protected void describeBlocked(TooltipMakerAPI tooltip, ShopEntry entry, Tackle blocker, int slot) {
            String other = String.valueOf(2 - slot);

            if (blocker == entry.tackle) {
                tooltip.addPara("Already fitted", Misc.getHighlightColor(), 0f);
                tooltip.addPara("This module is fitted in slot %s. One module cannot fill both slots of a"
                        + " rig.", 6f, Misc.getHighlightColor(), other);
                return;
            }

            tooltip.addPara("Conflict", Misc.getHighlightColor(), 0f);
            tooltip.addPara("%s is fitted in slot %s. Modules with similar or opposing effects cannot share"
                    + " a rig.", 6f, Misc.getHighlightColor(), blocker.name, other);
        }

        protected void describeMark(TooltipMakerAPI tooltip, ShopEntry entry) {
            boolean marked = ShopMarks.isMarked(entry);

            tooltip.addPara("Shopping list", Misc.getHighlightColor(), 0f);
            tooltip.addPara(marked ? "Click the star to remove this from your list."
                    : "Click the star to add this to your list.", 6f);
            tooltip.addPara("Eligible fish are marked with a %s and listed first in"
                            + " the route planner.", 6f,
                    Misc.getGrayColor(), Misc.getHighlightColor(), "star");
            tooltip.addPara("If the upgrade is locked, schematics offered by quests will also be marked by the %s in dialogue.", 6f,
                    Misc.getGrayColor(), Misc.getHighlightColor(), "star");
        }

        protected void describePip(TooltipMakerAPI tooltip, ShopEntry entry, int rung) {
            String tier = String.valueOf(rung);

            if (rung <= entry.getLevel()) {
                tooltip.addPara("Purchased", Misc.getPositiveHighlightColor(), 0f);
                tooltip.addPara("Upgrade tier %s is installed.", 6f,
                        Misc.getHighlightColor(), tier);
                return;
            }

            if (ShopSchematics.requires(entry.stat, rung)
                    && !ShopSchematics.has(entry.stat, rung)) {
                tooltip.addPara("Locked", Misc.getNegativeHighlightColor(), 0f);
                tooltip.addPara("Upgrade tier %s requires a schematic."
                                + " Fishing jobs can offer it.", 6f,
                        Misc.getHighlightColor(), tier);
                tooltip.addPara("Each schematic unlocks one upgrade tier for purchase;"
                        + " it does not grant the upgrade.", Misc.getGrayColor(), 6f);
                tooltip.addPara("Mark this tier to highlight jobs offering its schematic"
                        + " until you learn it.", Misc.getGrayColor(), 6f);
                return;
            }

            tooltip.addPara("Available", Misc.getHighlightColor(), 0f);

            if (rung == entry.getLevel() + 1) {
                tooltip.addPara("Upgrade tier %s is available for purchase.", 6f,
                        Misc.getHighlightColor(), tier);
            } else {
                tooltip.addPara("Upgrade tier %s is unlocked."
                                + " Purchase the preceding tiers first.", 6f,
                        Misc.getHighlightColor(), tier);
            }
        }

        protected void buildDetail() {
            ShopEntry entry = getSelected();
            if (entry == null) return;

            int slot = getTargetSlot();
            buildDetailHead(entry, slot);

            float y = DESCRIPTION_Y;
            Color text = Misc.getTextColor();

            // passed as a value so a percent sign in the description is not read as a format
            y += UiKit.para(body, DETAIL_X, y, DETAIL_WIDTH, "%s", text, text, entry.getDescription())
                    + PARA_GAP;

            if (entry.isUpgrade() && !entry.isMaxed()) {
                y += step(UiKit.para(body, DETAIL_X, y, DETAIL_WIDTH, "Now %s - next tier %s", text,
                        Misc.getHighlightColor(), entry.getValueAt(entry.getLevel()),
                        entry.getValueAt(entry.getLevel() + 1)));
            }

            if (entry.kind == ShopEntry.Kind.TACKLE && !entry.isFittedIn(slot) && entry.canFitIn(slot)) {
                Tackle fitted = TackleManager.getSlot(entry.rig, slot);

                if (fitted != entry.tackle && fitted != Tackle.NONE) {
                    y += step(UiKit.para(body, DETAIL_X, y, DETAIL_WIDTH,
                            FishingShips.isShipModule(fitted) && !TackleManager.isOwned(fitted)
                                    ? "Replaces %s. It stays with the ship and can be fitted again."
                                    : "Replaces %s. You keep the removed equipment.",
                            Misc.getGrayColor(), Misc.getHighlightColor(), fitted.name));
                }
            }

            if (entry.isCurio()) {
                y += step(UiKit.para(body, DETAIL_X, y, DETAIL_WIDTH, "%s", Misc.getGrayColor(),
                        entry.isOn() ? Misc.getPositiveHighlightColor() : Misc.getGrayColor(),
                        entry.isOn() ? "Switched on." : "Switched off."));
            }

            ShopPricing.Price price = getShownPrice(entry, slot);
            if (price != null) y += buildPrice(y, price);

            buildActions(entry, slot, y);
        }

        protected float step(float height) {
            return Math.max(LINE_STEP, height + PARA_GAP);
        }

        protected void buildDetailHead(ShopEntry entry, int slot) {
            SpriteAPI icon = entry.getIcon();
            Color frame = ShopUi.fill();
            Color tint = UiKit.base();
            boolean blocked = entry.kind == ShopEntry.Kind.TACKLE && !entry.isFittedIn(slot)
                    && !entry.canFitIn(slot);

            UiKit.painted(body, DETAIL_X, TOP, BOX_SIZE, BOX_SIZE, (pos, alpha) -> {
                float x = pos.getX();
                float y = pos.getY();

                UiKit.outline(x, y, BOX_SIZE, BOX_SIZE, frame, alpha);
                UiKit.quad(x + TILE_INSET, y + TILE_INSET, BOX_SIZE - 2f * TILE_INSET,
                        BOX_SIZE - 2f * TILE_INSET, frame, ShopUi.FILL_IDLE * alpha);

                if (icon == null || icon.getWidth() <= 0f || icon.getHeight() <= 0f) return;

                float scale = Math.min(ICON_SIZE / icon.getWidth(), ICON_SIZE / icon.getHeight());
                icon.setSize(icon.getWidth() * scale, icon.getHeight() * scale);
                icon.setColor(tint);
                icon.setNormalBlend();
                icon.setAlphaMult((blocked ? 0.5f : 1f) * alpha);
                icon.renderAtCenter(Math.round(x + BOX_SIZE * 0.5f), Math.round(y + BOX_SIZE * 0.5f));
            }, null);

            float textWidth = CONTENT_WIDTH - HEAD_TEXT_X;

            UiKit.text(body, HEAD_TEXT_X, NAME_Y, textWidth, entry.getName(), Fonts.ORBITRON_20AABOLD,
                    UiKit.bright(), Alignment.LMID);

            if (!entry.isUpgrade()) {
                String group = entry.isCurio() ? entry.kind.tabTitle : entry.group.title;

                UiKit.text(body, HEAD_TEXT_X, SUB_Y, textWidth, group, UiKit.LABEL, Misc.getGrayColor(),
                        Alignment.LMID);
                return;
            }

            float pipsWidth = ShopRowPlugin.getPipRowWidth(entry.getMaxLevel());
            UiKit.painted(body, HEAD_TEXT_X, SUB_PIPS_Y, pipsWidth, ShopRowPlugin.PIP_SIZE,
                    (pos, alpha) -> ShopRowPlugin.drawPips(pos.getX(), pos.getY(), entry,
                            Misc.getGrayColor(), HOLE_ALPHA, alpha), null);
            addPipTooltips(body, entry, HEAD_TEXT_X, SUB_PIPS_Y);

            float tierX = HEAD_TEXT_X + entry.getMaxLevel() * ShopRowPlugin.PIP_PITCH + 8f;
            UiKit.text(body, tierX, SUB_Y, CONTENT_WIDTH - tierX,
                    "Tier " + entry.getLevel() + " / " + entry.getMaxLevel(), UiKit.LABEL,
                    Misc.getGrayColor(), Alignment.LMID);
        }

        // the price table shows only while there is something to pay; the reasons it does not are the status line's
        protected ShopPricing.Price getShownPrice(ShopEntry entry, int slot) {
            if (entry.isCurio() || entry.isMaxed() || entry.isFittedIn(slot)) return null;
            if (entry.tackle == Tackle.NONE || entry.isOwned() || entry.isShipModule()) return null;
            if (entry.isLocked()) return null;

            ShopPricing.Price price = entry.getPrice();

            return price == null || price.fish == null ? null : price;
        }

        // returns the height the table takes, gap below included
        protected float buildPrice(float top, ShopPricing.Price price) {
            float x = DETAIL_X + PRICE_LABEL_WIDTH;
            float textX = x + PRICE_ICON + PRICE_ICON_GAP;
            float textWidth = CONTENT_WIDTH - textX;
            float y = top;

            addPriceLabel(y, "Price");
            UiKit.sprite(body, x, y, PRICE_ICON, PRICE_ICON,
                    Global.getSettings().getSprite("fleetScreen", "icon_logistics_credits"), Color.WHITE);
            UiKit.textIn(body, textX, y, textWidth, PRICE_ICON, Misc.getDGSCredits(price.credits), UiKit.BODY,
                    credits >= price.credits ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(),
                    Alignment.LMID);
            y += PRICE_ROW;

            FishRarity rarity = price.fish.getDisplayRarity();
            Color fishColor = rarity == null ? Misc.getHighlightColor() : rarity.color;

            addPriceLabel(y, "Fish required");
            UiKit.sprite(body, x, y, PRICE_ICON, PRICE_ICON, SpriteLoader.getSprite("pane_fish"), fishColor);
            float fishHeight = UiKit.para(body, textX, y, textWidth, "%s", fishColor, fishColor,
                    price.fish.describe());
            y += Math.max(PRICE_ROW, fishHeight + STATUS_GAP + 1f);

            int have = FishCurrency.count(price.fish);
            addPriceLabel(y, "Matching aboard");
            UiKit.textIn(body, x, y, textWidth, PRICE_ICON, String.valueOf(have), UiKit.BODY,
                    have >= price.fish.count ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor(),
                    Alignment.LMID);

            return y + PRICE_BOTTOM - top;
        }

        protected void addPriceLabel(float y, String label) {
            UiKit.textIn(body, DETAIL_X, y, PRICE_LABEL_WIDTH, PRICE_ICON, label, UiKit.BODY,
                    Misc.getGrayColor(), Alignment.LMID);
        }

        protected void buildActions(ShopEntry entry, int slot, float y) {
            boolean open = !entry.isDone(slot) && entry.canAfford() && entry.canFitIn(slot);
            ShopPricing.Price price = entry.getPrice();
            boolean paid = !entry.isCurio() && price != null;
            boolean select = paid && price.fish != null;
            boolean dev = Global.getSettings().isDevMode() && !entry.isCurio();
            int count = 1 + (select ? 1 : 0) + (dev ? 1 : 0);
            CutStyle cut = count == 1 ? CutStyle.ALL : CutStyle.TL_BR;

            String label = entry.isCurio() ? (entry.isOn() ? "Switch off" : "Switch on")
                    : entry.isUpgrade() ? "Upgrade" : hasTwoSlots() ? "Fit in slot " + (slot + 1) : "Fit";
            FishHandoffPicker.Selection automatic = open && entry.isUpgrade() ? getAutomaticPayment(entry) : null;
            boolean green = paid && open;

            float x = DETAIL_X;
            ButtonAPI main = UiKit.button(body, x, y, MAIN_BUTTON_WIDTH, UiKit.BUTTON_HEIGHT, label,
                    green ? Misc.getTextColor() : UiKit.base(), green ? UiKit.fill(UiKit.GREEN) : UiKit.dark(),
                    cut, open ? later(() -> buyClicked(entry, false, automatic)) : null);
            x += MAIN_BUTTON_WIDTH + BUTTON_GAP;

            if (automatic != null) {
                UiKit.tooltip(body, main, UiKit.TOOLTIP_WIDTH, TooltipLocation.BELOW, false,
                        tooltip -> describeAutomaticPayment(tooltip, automatic));
            }

            if (select) {
                UiKit.button(body, x, y, SELECT_WIDTH, "Select", cut,
                        open ? later(() -> selectedPaymentClicked(entry)) : null);
                x += SELECT_WIDTH + BUTTON_GAP;
            }

            if (dev) {
                UiKit.button(body, x, y, DEV_WIDTH, "Dev", cut, entry.isDone(slot) || !entry.canFitIn(slot)
                        ? null : later(() -> buyClicked(entry, true, null)));
            }

            addStatus(entry, slot, y + STATUS_Y);
        }

        // the one reason or state under the buttons
        protected void addStatus(ShopEntry entry, int slot, float y) {
            Color gray = Misc.getGrayColor();
            Color highlight = Misc.getHighlightColor();

            if (entry.isCurio()) {
                status(y, "Toggle at any time, no cost.", gray);
                return;
            }

            if (entry.isMaxed()) {
                status(y, "Fully upgraded.", Misc.getPositiveHighlightColor());
                return;
            }

            if (entry.isFittedIn(slot)) {
                status(y, "Fitted and ready.", Misc.getPositiveHighlightColor());
                return;
            }

            // an upgrade carries no tackle, so an unguarded blocker == entry.tackle matched null to null
            Tackle blocker = entry.getBlocker(slot);
            String other = String.valueOf(2 - slot);
            if (blocker != null && blocker == entry.tackle) {
                UiKit.para(body, DETAIL_X, y, DETAIL_WIDTH,
                        "Fitted in slot %s. A module can fill only one slot of a rig.", gray, highlight, other);
                return;
            }
            if (blocker != null) {
                UiKit.para(body, DETAIL_X, y, DETAIL_WIDTH, "Conflicts with %s in slot %s. Modules with"
                        + " similar or opposing effects cannot share a rig.", gray, highlight, blocker.name, other);
                return;
            }

            if (entry.tackle == Tackle.NONE) {
                status(y, "No charge for emptying a slot.", gray);
                return;
            }

            if (entry.isOwned()) {
                status(y, "Owned - fitting it is free.", gray);
                return;
            }

            if (entry.isShipModule()) {
                UiKit.para(body, DETAIL_X, y, DETAIL_WIDTH, "Comes with your %s - fitting it is free.", gray,
                        highlight, getShipName(entry.rig));
                return;
            }

            if (entry.isPurchaseLocked()) {
                status(y, "Schematic required.", Misc.getNegativeHighlightColor());
                return;
            }

            ShopPricing.Price price = entry.getPrice();
            if (price == null) return;

            // without a fish price nothing can satisfy it, so the button stays grey; say why
            if (price.fish == null) {
                float height = status(y, "Not for sale here.", Misc.getNegativeHighlightColor());
                status(y + height + STATUS_GAP, "The trade has no standing price for this one.", gray);
                return;
            }

            if (FishCurrency.count(price.fish) < price.fish.count) {
                status(y, "Not enough matching fish aboard.", Misc.getNegativeHighlightColor());
                return;
            }

            if (credits < price.credits) {
                status(y, "Not enough credits.", Misc.getNegativeHighlightColor());
            }
        }

        protected float status(float y, String text, Color color) {
            return UiKit.para(body, DETAIL_X, y, DETAIL_WIDTH, "%s", color, color, text);
        }

        protected FishHandoffPicker.Selection getAutomaticPayment(ShopEntry entry) {
            ShopPricing.Price price = entry == null ? null : entry.getPrice();
            if (price == null || price.fish == null) return null;

            return FishHandoffPicker.autoSelect(Collections.singletonList(price.fish), null);
        }

        protected void describeAutomaticPayment(TooltipMakerAPI tooltip, FishHandoffPicker.Selection payment) {
            tooltip.addPara("Automatic payment will remove these specimens:", 0f);

            for (FishCatch fish : payment.getContents()) {
                FishSpec spec = fish.getSpec();
                FishGrade grade = fish.getGrade();
                String name = fish.getDisplayName();
                String length = String.format("%.2f m", fish.length);
                String weight = String.format("%.1f kg", fish.weight);
                Color rarityColor = spec == null
                        ? Misc.getHighlightColor() : spec.rarity.color;

                tooltip.addPara("%s: %s, %s, %s", 4f,
                        new Color[]{rarityColor, grade.getColor(),
                                Misc.getHighlightColor(), Misc.getHighlightColor()},
                        name, grade.name, length, weight);
            }
        }

        protected void buildFooter() {
            ButtonAPI undo = UiKit.button(body, 0f, FOOTER_Y, UNDO_WIDTH, "Undo last purchase", CutStyle.ALL,
                    purchases.isEmpty() ? null : later(this::undoClicked));
            UiKit.tooltip(body, undo, UNDO_TOOLTIP_WIDTH, TooltipLocation.ABOVE, false,
                    tooltip -> tooltip.addPara("Returns the last purchase made this visit and refunds the"
                            + " credits and exact fish used to pay for it.", 0f));

            UiKit.button(body, CONTENT_WIDTH - LEAVE_WIDTH, FOOTER_Y, LEAVE_WIDTH, "Leave", CutStyle.ALL,
                    later(this::leave));
        }

        protected void leave() {
            if (callbacks != null) callbacks.dismissDialog();
        }

        protected void buyClicked(ShopEntry entry, boolean free,
                                  FishHandoffPicker.Selection automaticPayment) {
            int slot = getTargetSlot();
            if (!entry.canFitIn(slot)) return;

            Receipt receipt = !free && entry.getPrice() != null
                    ? new Receipt(entry, slot) : null;

            ShopPricing.Price price = entry.getPrice();
            // only a legendary would cover it: the player must choose to spend it
            if (!free && automaticPayment == null && !entry.isCurio()
                    && price != null && price.fish != null && !FishCurrency.canAutoPay(price.fish)) {
                selectedPaymentClicked(entry);
                return;
            }

            boolean bought;
            if (free) {
                bought = entry.devBuy(slot);
            } else if (automaticPayment != null) {
                bought = entry.buyWithFishPayment(slot, automaticPayment::spend);
            } else {
                bought = entry.buy(slot);
            }

            if (!bought) return;

            if (receipt != null) purchases.add(receipt);

            FishingSounds.playUISound(SOUND_BOUGHT, 1f, 1f);

            refreshWallet();
            dirty = true;
        }

        protected void selectedPaymentClicked(ShopEntry entry) {
            ShopPricing.Price price = entry.getPrice();
            if (entry.isCurio() || price == null || price.fish == null
                    || !entry.canAfford() || !entry.canFitIn(getTargetSlot()) || callbacks == null) return;

            selectedPaymentEntry = entry;
            selectedPaymentReceipt = new Receipt(entry, getTargetSlot());
            rememberScroll();
            callbacks.dismissDialog();
        }

        protected void openSelectedPaymentPicker() {
            final ShopEntry entry = selectedPaymentEntry;
            final Receipt receipt = selectedPaymentReceipt;
            selectedPaymentEntry = null;
            selectedPaymentReceipt = null;

            ShopPricing.Price price = entry == null ? null : entry.getPrice();
            if (entry == null || receipt == null || price == null || price.fish == null) {
                if (receipt != null) restoreFish(receipt);
                reopenPending = true;

                return;
            }

            String confirm = entry.isUpgrade() ? "Upgrade" : "Fit";
            boolean opened = FishHandoffPicker.show(dialog, "Select specimens for payment",
                    confirm, Collections.singletonList(price.fish),
                    new FishHandoffPicker.Listener() {
                        @Override
                        public void picked(FishHandoffPicker.Selection selection) {
                            if (entry.buyWithFishPayment(receipt.slot, selection::spend)) {
                                purchases.add(receipt);
                                FishingSounds.playUISound(SOUND_BOUGHT, 1f, 1f);
                            } else {
                                restoreFish(receipt);
                            }

                            reopenPending = true;
                        }

                        @Override
                        public void cancelled() {
                            restoreFish(receipt);
                            reopenPending = true;
                        }
                    });

            if (!opened) {
                restoreFish(receipt);
                reopenPending = true;
            }
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

        protected void restoreFish(Receipt receipt) {
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
        }

        protected void undoClicked() {
            if (purchases.isEmpty()) return;

            Receipt receipt = purchases.remove(purchases.size() - 1);
            ShopEntry entry = findEntry(receipt.entryKey);
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

            restoreFish(receipt);
            cargo.getCredits().set(receipt.creditsAboard);

            if (entry != null && entry.isUpgrade()) {
                UpgradeManager.getInstance().setLevel(entry.stat.id, receipt.statLevel);
                ShopEntry.stopAbility(StatIds.getAbilityId(entry.stat.id));
            } else if (receipt.rig != null && receipt.tackle != null) {
                if (!receipt.tackleOwned) TackleManager.consume(receipt.tackle);

                // a ship module whose ship has gone cannot come back; a refused restore leaves the slot as consume left it
                Tackle prior = receipt.fitted == null ? Tackle.NONE : receipt.fitted;
                if (FishingShips.isShipModule(prior) && !TackleManager.isOwned(prior)
                        && !FishingShips.isShipModuleAvailable(prior)) {
                    prior = Tackle.NONE;
                }
                TackleManager.fit(receipt.rig, receipt.slot, prior);
                ShopEntry.stopAbility(entry != null ? entry.getRigAbilityId() : null);
            }

            if (receipt.marked) ShopMarks.mark(receipt.markKey);

            selectedKey = receipt.entryKey;
            showSelected(entry, receipt.slot);
            refreshWallet();
            dirty = true;

            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                    "Refunded " + (entry != null ? entry.getName() : "the last purchase"));
            FishingSounds.playUISound(SOUND_UNDONE, 1f, 1f);
        }

        // brings the refunded entry's shelf to the front, so the detail never describes a row the list is not showing
        protected void showSelected(ShopEntry entry, int slot) {
            if (entry == null) return;

            if (entry.kind != mainTab || entry.group != category) {
                rememberScroll();
                listOffset = 0f;
                list = null;
            }

            mainTab = entry.kind;
            category = entry.group;
            // the slot the refund put the previous module back into; getTargetSlot clamps it if that slot has gone
            targetSlot = slot;
        }

        protected ShopEntry findEntry(String key) {
            for (ShopEntry entry : entries) {
                if (entry.getKey().equals(key)) return entry;
            }

            return null;
        }

        protected ShopEntry getSelected() {
            ShopEntry first = null;

            for (ShopEntry entry : getVisible()) {
                if (entry.isUndiscovered()) continue;
                if (entry.getKey().equals(selectedKey)) return entry;
                if (first == null) first = entry;
            }

            return first;
        }

        @Override
        public boolean isSelected(ShopEntry entry) {
            return entry.getKey().equals(selectedKey);
        }

        @Override
        public void onRowClicked(ShopEntry entry) {
            if (isSelected(entry)) return;

            selectedKey = entry.getKey();
            dirty = true;
        }

        @Override
        public PositionAPI getListViewport() {
            return listViewport;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (buttonId instanceof Runnable action) action.run();
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

        @Override
        public void reportDismissed(int option) {
            if (selectedPaymentEntry != null) {
                openSelectedPaymentPicker();

                return;
            }

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
        reopenPending = false;
        FishingShips.reconcile();

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
        // ownership is final here: undo works only inside the session
        TreasureRumour.checkLensBought();
        SensorBuild.checkBought();

        if (onClose == null) {
            dialog.dismiss();
            return;
        }

        onClose.onShopClosed(dialog);
    }

    @Override
    public void advance(float amount) {
        if (!reopenPending || closed || dialog == null || delegate == null) return;

        reopenPending = false;
        dialog.showCustomVisualDialog(WIDTH, HEIGHT, delegate);
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
