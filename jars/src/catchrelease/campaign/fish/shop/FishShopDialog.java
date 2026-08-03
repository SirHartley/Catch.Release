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
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

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
 * Rebuilt from scratch after every purchase rather than updated in place. A shop is a list of prices
 * and what is in the hold, and both change when something is bought - rebuilding is both simpler and
 * harder to get wrong than reaching back into the elements that are already there.
 */
public class FishShopDialog implements InteractionDialogPlugin {

    public static final float WIDTH = 720f;
    public static final float HEIGHT = 560f;

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

    protected class Delegate implements CustomVisualDialogDelegate, CustomUIPanelPlugin {

        protected CustomPanelAPI panel;
        protected DialogCallbacks callbacks;

        /** What each button on screen would buy, so the press does not have to be decoded. */
        protected final Map<Object, Runnable> actions = new HashMap<>();

        @Override
        public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
            this.panel = panel;
            this.callbacks = callbacks;

            build();
        }

        /** Everything torn down and put back, because everything on it can change with one purchase. */
        protected void rebuild() {
            if (panel == null) return;

            panel.removeComponent(null);
            build();
        }

        protected void build() {
            actions.clear();

            float pad = 10f;
            float column = (WIDTH - pad * 3f) * 0.5f;

            TooltipMakerAPI left = panel.createUIElement(column, HEIGHT - pad * 2f, true);
            buildHold(left);
            buildUpgrades(left, column, UpgradeStat.Category.CAMPAIGN, "Campaign");
            panel.addUIElement(left).inTL(pad, pad);

            TooltipMakerAPI right = panel.createUIElement(column, HEIGHT - pad * 2f, true);
            buildUpgrades(right, column, UpgradeStat.Category.MINIGAME, "The catch");
            buildTackle(right, column);
            panel.addUIElement(right).inTL(column + pad * 2f, pad);
        }

        /** What is aboard to spend, since every price below is quoted in it. */
        protected void buildHold(TooltipMakerAPI info) {
            info.addSectionHeading("In the hold", Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, 0f);

            Map<FishRarity, Integer> counts = FishCurrency.count();

            for (FishRarity rarity : FishRarity.values()) {
                info.addPara("%s   %s", 3f, rarity.color,
                        Misc.ucFirst(rarity.name().toLowerCase()), "" + counts.get(rarity));
            }
        }

        protected void buildUpgrades(TooltipMakerAPI info, float width,
                                     UpgradeStat.Category category, String title) {

            info.addSectionHeading(title, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, 10f);

            List<UpgradeStat> stats = UpgradeManager.getInstance().getByCategory(category);

            if (stats.isEmpty()) {
                info.addPara("Nothing here yet.", Misc.getGrayColor(), 5f);
                return;
            }

            for (final UpgradeStat stat : stats) {
                addUpgradeRow(info, width, stat);
            }
        }

        protected void addUpgradeRow(TooltipMakerAPI info, float width, final UpgradeStat stat) {
            if (ShopPricing.isMaxed(stat)) {
                info.addPara("%s - fully upgraded", 5f, Misc.getPositiveHighlightColor(),
                        describe(stat));
                return;
            }

            FishRarity rarity = ShopPricing.getRarity(stat);
            int cost = ShopPricing.getCost(stat);
            boolean afford = ShopPricing.canAfford(rarity, cost);

            String label = describe(stat) + "   " + cost + " "
                    + Misc.ucFirst(rarity.name().toLowerCase());

            Object id = new Object();
            com.fs.starfarer.api.ui.ButtonAPI button = info.addButton(label, id,
                    afford ? rarity.color : Misc.getGrayColor(), Misc.getDarkPlayerColor(),
                    width - 10f, 22f, 5f);

            button.setEnabled(afford);

            if (stat.description != null && !stat.description.isEmpty()) {
                info.addTooltipToPrevious(makeTooltip(stat.description), TooltipMakerAPI.TooltipLocation.LEFT);
            }

            actions.put(id, new Runnable() {
                @Override
                public void run() {
                    FishRarity price = ShopPricing.getRarity(stat);
                    if (!FishCurrency.spend(price, ShopPricing.getCost(stat))) return;

                    UpgradeManager.getInstance().addLevels(stat.id, 1);
                }
            });
        }

        protected void buildTackle(TooltipMakerAPI info, float width) {
            for (final Tackle.Fit rig : new Tackle.Fit[]{Tackle.Fit.DRONE, Tackle.Fit.HARPOON}) {
                info.addSectionHeading(rig == Tackle.Fit.DRONE ? "Drone tackle" : "Harpoon tips",
                        Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, 10f);

                Tackle fitted = TackleManager.get(rig);
                info.addPara("Fitted: %s", 3f, Misc.getHighlightColor(), fitted.name);

                for (final Tackle tackle : TackleManager.getOptions(rig)) {
                    if (tackle == fitted) continue;

                    addTackleRow(info, width, rig, tackle);
                }
            }
        }

        protected void addTackleRow(TooltipMakerAPI info, float width, final Tackle.Fit rig,
                                    final Tackle tackle) {

            FishRarity rarity = ShopPricing.getRarity(tackle);
            int cost = ShopPricing.getCost(tackle);
            boolean afford = ShopPricing.canAfford(rarity, cost);

            String label = tackle.name
                    + (rarity == null ? "" : "   " + cost + " " + Misc.ucFirst(rarity.name().toLowerCase()));

            Object id = new Object();
            com.fs.starfarer.api.ui.ButtonAPI button = info.addButton(label, id,
                    afford ? Misc.getBasePlayerColor() : Misc.getGrayColor(),
                    Misc.getDarkPlayerColor(), width - 10f, 22f, 5f);

            button.setEnabled(afford);
            info.addTooltipToPrevious(makeTooltip(tackle.description), TooltipMakerAPI.TooltipLocation.LEFT);

            actions.put(id, new Runnable() {
                @Override
                public void run() {
                    if (!FishCurrency.spend(ShopPricing.getRarity(tackle), ShopPricing.getCost(tackle))) return;

                    TackleManager.fit(rig, tackle);
                }
            });
        }

        protected TooltipMakerAPI.TooltipCreator makeTooltip(final String text) {
            return new com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator() {
                @Override
                public boolean isTooltipExpandable(Object tooltipParam) {
                    return false;
                }

                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return 320f;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara(text, 0f);
                }
            };
        }

        protected String describe(UpgradeStat stat) {
            String name = stat.id.replace('_', ' ');

            return Misc.ucFirst(name) + "  " + stat.level + "/" + Math.max(1, stat.maxLevel);
        }

        @Override
        public void buttonPressed(Object buttonId) {
            Runnable action = actions.get(buttonId);
            if (action == null) return;

            action.run();
            rebuild();
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
