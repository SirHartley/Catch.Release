package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

// T3's follow-up: a drunk at an independent Core bar trades his old Reliquary for epic fish, then the player reports
// back and the closing conversation starts T4.
public final class ReliquaryTrade {

    public static final int PRICE = 3;
    public static final int HAGGLED_PRICE = 2;

    // vanilla's marketOptBar hides the bar while this market flag is set
    public static final String NO_BAR = "$noBar";

    private ReliquaryTrade() {
    }

    public static class TradeIntel extends BaseIntelPlugin {

        protected static boolean isReturning() {
            return FishermanChain.isAt(FishermanChain.Stage.T3_TRADED);
        }

        @Override
        public boolean shouldRemoveIntel() {
            if (isEnded()) return true;
            if (!isTrading() && !isReturning()) endImmediately();

            return isEnded();
        }

        @Override
        public String getName() {
            return isReturning() ? "An Epic tale: report to the Fisherman" : "An Epic tale: Chapter 3";
        }

        @Override
        public String getSmallDescriptionTitle() {
            return getName();
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);

            addBulletPoints(info, mode);
        }

        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color text = getBulletColorForMode(mode);
            Color h = Misc.getHighlightColor();
            float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);
            if (isReturning()) {
                info.addPara("Return to the nearest fishing boat", text, pad);
            } else {
                info.addPara("Find the man in the bar on %s", pad, text, h, getMarketName());
            }
            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            float opad = 10f;
            Color h = Misc.getHighlightColor();
            FactionAPI faction = Global.getSector().getFaction(FishermanConstants.FACTION);

            info.addImages(width, 128, opad, opad, FishermanIdentity.getPortrait(0f), faction.getCrest());

            if (isReturning()) {
                info.addPara("The %s is in your fleet. The Fisherman will want to hear about it.", opad, h,
                        getShipName());
                addBulletPoints(info, ListInfoMode.IN_DESC);
                FishIntelMapButton.addSetAutopilot(info, width, FishingIntro.getNearestBoat());
                return;
            }

            info.addPara("A man in the bar on %s asked the Fisherman for help. He owns an old %s and would "
                    + "rather have epic fish.", opad, h, getMarketName(), getShipName());
            addBulletPoints(info, ListInfoMode.IN_DESC);
            FishIntelMapButton.addPlotRoute(info, width, getMapLocation(null));
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            if (isReturning() && FishIntelMapButton.handleSetAutopilot(buttonId, FishingIntro.getNearestBoat())) {
                return;
            }
            if (FishIntelMapButton.handlePlotRoute(buttonId, getMapLocation(null))) return;

            super.buttonPressConfirmed(buttonId, ui);
        }

        @Override
        public String getIcon() {
            return FishermanIdentity.getPortrait(0f);
        }

        @Override
        public String getSortString() {
            return getSortStringNewestFirst();
        }

        @Override
        public FactionAPI getFactionForUIColors() {
            return Global.getSector().getPlayerFaction();
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_MISSIONS);
            tags.add(Tags.INTEL_ACCEPTED);
            tags.add(FishermanConstants.FACTION);

            return tags;
        }

        @Override
        public SectorEntityToken getMapLocation(SectorMapAPI map) {
            return isTrading() ? getMarketEntity() : null;
        }
    }

    public static boolean isTrading() {
        return FishermanChain.isAt(FishermanChain.Stage.T3_TRADE);
    }

    // Picked before the hand-in text names it, and kept while it still has a bar the player can visit.
    public static boolean prepare() {
        FishermanChain.Saved saved = FishermanChain.get(true);
        if (saved == null) return false;
        if (isCandidate(getMarket(), true)) return true;

        MarketAPI market = pickMarket(new Random());
        saved.t3MarketId = market == null ? null : market.getId();
        saved.t3MarketName = market == null ? null : market.getName();
        saved.t3MarketSystemName = market == null || market.getStarSystem() == null ? null
                : market.getStarSystem().getNameWithLowercaseType();

        return market != null;
    }

    // Called from T3's hand-in, after its range data.
    public static boolean start(TextPanelAPI text) {
        if (getMarket() == null) return false;

        FishermanChain.setStage(FishermanChain.Stage.T3_TRADE);
        // added inside the dialogue, so its card follows the Fisherman's line
        FishIntelNotifications.addNow(new TradeIntel(), text);

        return true;
    }

    // The bar visit's condition: the saved market while the trade is open.
    public static boolean isHere(MarketAPI market) {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return isTrading() && market != null && saved != null && market.getId().equals(saved.t3MarketId);
    }

    public static void haggle() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved != null && isTrading()) saved.t3Haggled = true;
    }

    public static int getPrice() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved != null && saved.t3Haggled ? HAGGLED_PRICE : PRICE;
    }

    public static boolean canPay() {
        return FishCurrency.count(FishRarity.EPIC) >= getPrice();
    }

    // The epics go, the Reliquary joins; FishCurrency.spend takes loose specimens before breaking a crate.
    public static boolean trade(TextPanelAPI text) {
        if (!isTrading() || !canPay()) return false;

        int price = getPrice();
        if (!FishCurrency.spend(FishRarity.EPIC, price)) return false;

        List<FishReward.Receipt> lost = new ArrayList<>();
        lost.add(new FishReward.Receipt("Epic fish x" + price, "Epic fish", FishRarity.EPIC.color));
        FishReward.showReceipts(text, lost, "Lost");

        List<FishReward.Receipt> gained = new ArrayList<>();
        gained.add(FishReward.ship(FishingShips.Ship.ROD.hullId).grantWithReceipt());

        FishermanChain.setStage(FishermanChain.Stage.T3_TRADED);
        FishReward.showReceipts(text, gained);
        updateIntel();

        return true;
    }

    // A legendary reported before the trade (LegendaryHunt.skipChain): the Reliquary all the same.
    public static void skip(List<FishReward.Receipt> receipts) {
        if (FishermanChain.isBefore(FishermanChain.Stage.T3_TRADED)) {
            receipts.add(FishReward.ship(FishingShips.Ship.ROD.hullId).grantWithReceipt());
        }
    }

    // The closing conversation ends T3 and starts T4, which it introduces.
    public static void finish(TextPanelAPI text) {
        if (FishermanChain.isAt(FishermanChain.Stage.T3_TRADED)) LegendaryHunt.start(text);
    }

    public static void updateIntel() {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(TradeIntel.class)) {
            FishIntelNotifications.update((TradeIntel) intel, null);
        }
    }

    public static String getShipName() {
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec(FishingShips.Ship.ROD.hullId);

        return spec == null ? "LYNE Tender" : spec.getHullName();
    }

    public static MarketAPI getMarket() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null || saved.t3MarketId == null ? null
                : Global.getSector().getEconomy().getMarket(saved.t3MarketId);
    }

    public static SectorEntityToken getMarketEntity() {
        MarketAPI market = getMarket();

        return market == null ? null : market.getPrimaryEntity();
    }

    protected static String getMarketName() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null || saved.t3MarketName == null ? "the market" : saved.t3MarketName;
    }

    // An independent Core world; any Core faction's bar if none qualifies.
    protected static MarketAPI pickMarket(Random random) {
        List<MarketAPI> independent = new ArrayList<>();
        List<MarketAPI> any = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (isCandidate(market, false)) any.add(market);
            if (isCandidate(market, true)) independent.add(market);
        }

        List<MarketAPI> pool = independent.isEmpty() ? any : independent;

        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    protected static boolean isCandidate(MarketAPI market, boolean independent) {
        if (market == null || market.isHidden() || !market.isInEconomy() || market.isPlayerOwned()) return false;
        if (market.getPrimaryEntity() == null || market.getStarSystem() == null
                || !market.getStarSystem().hasTag(Tags.THEME_CORE)) return false;
        if (market.getMemoryWithoutUpdate().getBoolean(NO_BAR)) return false;

        FactionAPI faction = market.getFaction();
        if (faction == null || faction.isNeutralFaction()) return false;

        return !independent || Factions.INDEPENDENT.equals(market.getFactionId());
    }
}
