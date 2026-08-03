package catchrelease.campaign.fish.shop;

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
    public static final float GROUP_HEIGHT = 30f;
    public static final float DETAIL_GAP = 14f;

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
            ShopRowPlugin.Host, ShopHeaderPlugin.Purse {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;

        protected final List<ShopEntry> entries = new ArrayList<>();
        protected String selectedKey;

        /** Counted once per change rather than once per frame - the purse walks the whole hold. */
        protected Map<FishRarity, Integer> wallet = new HashMap<>();

        /** The right-hand pane, torn down and rebuilt whenever what it shows stops being true. */
        protected TooltipMakerAPI detail;
        protected PositionAPI listViewport;
        protected Object buyId;

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            buildEntries();
            if (!entries.isEmpty()) selectedKey = entries.get(0).getKey();

            refreshWallet();
            buildHeader();
            buildList();
            buildDetail();
        }

        /**
         * Everything on sale, shelf by shelf: every stat the sheet knows grouped by its gear, then
         * each rig's modules with the empty slot listed last as a way out of all of them.
         */
        protected void buildEntries() {
            List<UpgradeStat> stats = new ArrayList<>(UpgradeManager.getInstance().getAll().values());

            //the sheet documents its own format with a row, which is not a thing for sale
            stats.removeIf(stat -> stat.id == null || stat.id.equalsIgnoreCase("example"));
            stats.sort(Comparator.comparing(stat -> stat.id));

            for (ShopGroup group : ShopGroup.values()) {
                for (UpgradeStat stat : stats) {
                    if (ShopGroup.forStat(stat) == group) entries.add(ShopEntry.of(stat));
                }
            }

            for (Tackle.Fit rig : new Tackle.Fit[]{Tackle.Fit.DRONE, Tackle.Fit.HARPOON}) {
                for (Tackle tackle : TackleManager.getOptions(rig)) {
                    if (tackle != Tackle.NONE) entries.add(ShopEntry.of(tackle, rig));
                }

                entries.add(ShopEntry.of(Tackle.NONE, rig));
            }
        }

        protected void refreshWallet() {
            wallet = FishCurrency.count();
        }

        protected void buildHeader() {
            CustomPanelAPI header = panel.createCustomPanel(WIDTH - PAD * 2f, HEADER_HEIGHT,
                    new ShopHeaderPlugin(this));

            panel.addComponent(header).inTL(PAD, PAD);
        }

        protected void buildList() {
            float top = PAD + HEADER_HEIGHT + 10f;
            float height = HEIGHT - top - PAD;

            TooltipMakerAPI list = panel.createUIElement(LIST_WIDTH, height, true);

            ShopGroup current = null;
            for (ShopEntry entry : entries) {
                if (entry.group != current) {
                    current = entry.group;

                    CustomPanelAPI groupRow = panel.createCustomPanel(ROW_WIDTH, GROUP_HEIGHT,
                            new ShopGroupRowPlugin(current, this));
                    list.addCustom(groupRow, 0f);
                }

                CustomPanelAPI row = panel.createCustomPanel(ROW_WIDTH, ROW_HEIGHT,
                        new ShopRowPlugin(entry, this));
                list.addCustom(row, 3f);
            }

            listViewport = panel.addUIElement(list);
            listViewport.inTL(PAD, top);
        }

        protected void buildDetail() {
            if (detail != null) panel.removeComponent(detail);

            float top = PAD + HEADER_HEIGHT + 10f;
            float height = HEIGHT - top - PAD;
            float width = WIDTH - PAD * 2f - LIST_WIDTH - DETAIL_GAP;

            detail = panel.createUIElement(width, height, false);

            ShopEntry entry = getSelected();
            if (entry != null) buildDetailContent(detail, width, entry);

            panel.addUIElement(detail).inTL(PAD + LIST_WIDTH + DETAIL_GAP, top);
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

        /** The tag: what the next one costs, and whether the hold can cover it. */
        protected void buildPrice(TooltipMakerAPI info, ShopEntry entry) {
            if (entry.isMaxed()) {
                info.addPara("Fully upgraded.", Misc.getPositiveHighlightColor(), 16f);
                return;
            }

            if (entry.isFitted()) {
                info.addPara("Fitted and ready.", Misc.getPositiveHighlightColor(), 16f);
                return;
            }

            FishRarity rarity = entry.getPriceRarity();
            if (rarity == null) {
                info.addPara("No charge for emptying a slot.", Misc.getGrayColor(), 16f);
                return;
            }

            int cost = entry.getPriceCost();
            int held = wallet.get(rarity) == null ? 0 : wallet.get(rarity);

            info.addPara("Price: %s", 16f, rarity.color,
                    cost + " x " + Misc.ucFirst(rarity.name().toLowerCase()) + " specimens");

            info.addPara("In the hold: %s", 4f,
                    held >= cost ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                    String.valueOf(held));
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

        protected ShopEntry getSelected() {
            for (ShopEntry entry : entries) {
                if (entry.getKey().equals(selectedKey)) return entry;
            }

            return entries.isEmpty() ? null : entries.get(0);
        }

        @Override
        public boolean isSelected(ShopEntry entry) {
            return entry.getKey().equals(selectedKey);
        }

        @Override
        public void onRowClicked(ShopEntry entry) {
            if (isSelected(entry)) return;

            selectedKey = entry.getKey();
            buildDetail();
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
            if (buttonId != buyId) return;

            ShopEntry entry = getSelected();
            if (entry == null || !entry.buy()) return;

            Global.getSoundPlayer().playUISound("ui_char_increase_aptitude", 1f, 1f);

            //the purse and the pane are stale the moment the money moved; the rows are not,
            //because a row never stops reading the live data
            refreshWallet();
            buildDetail();
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
