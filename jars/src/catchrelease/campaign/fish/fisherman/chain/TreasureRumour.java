package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.shop.PaymentSpecimens;
import catchrelease.campaign.fish.shop.ShopPricing;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.fish.treasure.TreasureAward;
import catchrelease.campaign.fish.treasure.TreasureRarity;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// T1: the treasure rumour. The Fisherman's compass comes up as a golden chest in one system's lamp catches.
public final class TreasureRumour {

    public enum Lost {

        SOLD,
        STORED,
        GONE
    }

    public static final String COMPASS = "catchrelease_compass";

    public static final String COMPASS_ICON = "graphics/catchrelease/icon/compass.png";

    public static final float MIN_LY = 2f;
    public static final float MAX_LY = 10f;

    // how long the rumour keeps paying once the lens is bought
    public static final float EXTENSION_DAYS = 60f;

    public static final float CHEST_CHANCE = 0.3f;
    // the chest is certain on the 4th qualifying minigame, then on every 2nd after it until taken
    public static final int CHEST_GUARANTEED_AT = 4;
    public static final int CHEST_GUARANTEED_EVERY = 2;

    private TreasureRumour() {
    }

    // Records where the compass was sold, so "I lost it" can name the market. Registered only while T1 is active.
    public static class CompassWatcher implements ColonyInteractionListener {

        public static void register() {
            unregister();
            if (FishermanChain.isAt(FishermanChain.Stage.T1_ACTIVE)) {
                Global.getSector().getListenerManager().addListener(new CompassWatcher(), true);
            }
        }

        public static void unregister() {
            Global.getSector().getListenerManager().removeListenerOfClass(CompassWatcher.class);
        }

        @Override
        public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
            FishermanChain.Saved saved = FishermanChain.get(false);
            if (saved == null || !FishermanChain.isAt(FishermanChain.Stage.T1_ACTIVE)) return;

            boolean sold = transaction.getSold() != null
                    && transaction.getSold().getQuantity(CargoAPI.CargoItemType.SPECIAL, compass()) > 0f;
            boolean bought = transaction.getBought() != null
                    && transaction.getBought().getQuantity(CargoAPI.CargoItemType.SPECIAL, compass()) > 0f;
            if (!sold && !bought) return;

            boolean storage = transaction.getSubmarket() != null && transaction.getSubmarket().getPlugin() != null
                    && transaction.getSubmarket().getPlugin().isFreeTransfer();
            boolean sale = sold && !storage && transaction.getMarket() != null;

            saved.soldMarketId = sale ? transaction.getMarket().getId() : null;
            saved.soldMarketName = sale ? transaction.getMarket().getName() : null;
            saved.lost = null;
            saved.lostMarketName = null;
            updateIntel();
        }

        @Override
        public void reportPlayerOpenedMarket(MarketAPI market) {
        }

        @Override
        public void reportPlayerClosedMarket(MarketAPI market) {
        }

        @Override
        public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        }
    }

    // One intel for the whole of T1; its title and lines follow the chain stage, as FishingIntro.IntroIntel does.
    public static class CompassIntel extends BaseIntelPlugin {

        protected static boolean isReturning() {
            return FishermanChain.isAt(FishermanChain.Stage.T1_ACTIVE) && isCarried();
        }

        @Override
        public boolean shouldRemoveIntel() {
            if (isEnded()) return true;

            FishermanChain.Stage stage = FishermanChain.getStage();
            if (stage != FishermanChain.Stage.T1_ACTIVE && stage != FishermanChain.Stage.T1_LENS) endImmediately();

            return isEnded();
        }

        @Override
        public String getName() {
            if (FishermanChain.isAt(FishermanChain.Stage.T1_LENS)) return "Buy the Glitter Lens";
            if (isReturning()) return "Return the Fisherman's compass";

            return "The Fisherman's lost compass";
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
            float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);
            if (FishermanChain.isAt(FishermanChain.Stage.T1_LENS)) {
                info.addPara("Buy the %s at the Outfitter", pad, text, Misc.getHighlightColor(),
                        Tackle.GLITTER_LENS.name);
            } else if (isReturning()) {
                info.addPara("Return to the nearest fishing boat", text, pad);
            } else if (isMislaid()) {
                addMislaidLine(info, text, pad);
            } else {
                info.addPara("Lamp catches in %s", pad, text, Misc.getHighlightColor(), getSystemName());
                info.addPara("Take the Fisherman's Chest when it comes up", text, 0f);
            }
            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            float opad = 10f;
            Color h = Misc.getHighlightColor();
            FactionAPI faction = Global.getSector().getFaction(FishermanConstants.FACTION);

            info.addImages(width, 128, opad, opad, FishermanIdentity.getPortrait(0f), faction.getCrest());

            if (FishermanChain.isAt(FishermanChain.Stage.T1_LENS)) {
                info.addPara("The Fisherman gave you the %s schematic, with the fish and credits to buy it.",
                        opad, h, Tackle.GLITTER_LENS.name);
                info.addPara("Buy it at the Outfitter.", opad);
                addBulletPoints(info, ListInfoMode.IN_DESC);
                return;
            }

            if (isReturning()) {
                info.addPara("The chest held a compass. The Fisherman is expecting it back.", opad);
                addBulletPoints(info, ListInfoMode.IN_DESC);
                FishIntelMapButton.addSetAutopilot(info, width, FishingIntro.getNearestBoat());
                return;
            }

            if (isMislaid()) {
                info.addPara("The chest held a compass, but it is no longer in your cargo. The Fisherman is "
                        + "expecting it back.", opad);
                addBulletPoints(info, ListInfoMode.IN_DESC);
                return;
            }

            info.addPara("The Fisherman has lost something in %s and is sure it is there.", opad, h,
                    getSystemName());
            info.addPara("It can come up with any catch made under Breach Lights in that system. Gather it like any other bycatch.", opad);
            info.addPara("Reward: a rig module schematic and whatever is needed to build it.", opad, h,
                    "a rig module schematic");
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

        protected static boolean isMislaid() {
            FishermanChain.Saved saved = FishermanChain.get(false);

            return FishermanChain.isAt(FishermanChain.Stage.T1_ACTIVE) && saved != null && saved.compassPulled
                    && !isCarried();
        }

        protected void addMislaidLine(TooltipMakerAPI info, Color text, float pad) {
            FishermanChain.Saved saved = FishermanChain.get(false);
            Color h = Misc.getHighlightColor();

            if (saved.soldMarketName != null) {
                info.addPara("Buy it back at %s", pad, text, h, saved.soldMarketName);
            } else if (saved.lost == Lost.STORED && saved.lostMarketName != null) {
                info.addPara("Fetch it from storage at %s", pad, text, h, saved.lostMarketName);
            } else {
                info.addPara("Find it, or tell the Fisherman it is lost", text, pad);
            }
        }

        protected String getSystemName() {
            FishermanChain.Saved saved = FishermanChain.get(false);

            return saved == null || saved.systemName == null ? "the system" : saved.systemName;
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
            if (!FishermanChain.isAt(FishermanChain.Stage.T1_ACTIVE) || isReturning() || isMislaid()) return null;

            FishermanChain.Saved saved = FishermanChain.get(false);
            StarSystemAPI system = saved == null ? null : FishRumors.findSystem(saved.systemId);

            return system == null ? null : system.getHyperspaceAnchor();
        }
    }

    public static void markPitched() {
        FishermanChain.Saved saved = FishermanChain.get(true);
        if (saved != null) saved.pitched = true;
    }

    // Keeps the saved system while it can still host the rumour, so a declined offer names the same place again.
    public static boolean prepareOffer() {
        FishermanChain.Saved saved = FishermanChain.get(true);
        if (saved == null) return false;

        saved.pitched = true;
        if (isCandidate(FishRumors.findSystem(saved.systemId))) return true;

        StarSystemAPI system = pickSystem();
        if (system == null) return false;

        saved.systemId = system.getId();
        saved.systemName = system.getNameWithNoType();

        return true;
    }

    public static boolean start(TextPanelAPI text) {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null || !FishermanChain.isAt(FishermanChain.Stage.T1_OFFERED)) return false;

        StarSystemAPI system = FishRumors.findSystem(saved.systemId);
        if (!isCandidate(system) || FishRumors.publishHeld(system, FishRumors.Kind.LOOT) == null) return false;

        FishermanChain.setStage(FishermanChain.Stage.T1_ACTIVE);
        CompassWatcher.register();
        FishIntelNotifications.queue(new CompassIntel(), text);

        return true;
    }

    public static void updateIntel() {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(CompassIntel.class)) {
            FishIntelNotifications.update((CompassIntel) intel, null);
        }
    }

    public static FishRumors.Saved getRumour() {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null ? null : FishRumors.findActive(saved.systemId, FishRumors.Kind.LOOT);
    }

    // "I lost it": a recorded sale names that market; otherwise every storage and colony submarket the player can
    // open is searched once. GONE ends the quest as if the compass had come back.
    public static Lost checkLost() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null) return null;

        if (saved.soldMarketId != null) {
            saved.lost = Lost.SOLD;
            saved.lostMarketName = saved.soldMarketName;
        } else {
            MarketAPI stored = findStored();
            saved.lost = stored == null ? Lost.GONE : Lost.STORED;
            saved.lostMarketName = stored == null ? null : stored.getName();
        }

        return saved.lost;
    }

    protected static MarketAPI findStored() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.isPlayerOwned() && !Misc.playerHasStorageAccess(market)) continue;

            for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                if (submarket.getPlugin() == null || !submarket.getPlugin().isFreeTransfer()) continue;
                if (submarket.getCargo().getQuantity(CargoAPI.CargoItemType.SPECIAL, compass()) > 0f) return market;
            }
        }

        return null;
    }

    // Ends the compass errand, with or without the compass: T1 moves on to buying the lens.
    public static boolean complete(TextPanelAPI text) {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null || !FishermanChain.isAt(FishermanChain.Stage.T1_ACTIVE)) return false;

        CargoAPI cargo = getPlayerCargo();
        if (cargo != null && isCarried()) cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, compass(), 1f);

        saved.lost = null;
        saved.lostMarketName = null;
        CompassWatcher.unregister();
        FishermanChain.setStage(FishermanChain.Stage.T1_LENS);
        FishReward.showReceipts(text, grantRewards());
        updateIntel();
        checkLensBought();

        return true;
    }

    // The follow-up ends once the lens is owned, checked when the Outfitter closes and at hand-in. From then on the
    // rumour runs its ordinary course for EXTENSION_DAYS; the Fisherman remarks on it at the next call.
    public static void checkLensBought() {
        if (!FishermanChain.isAt(FishermanChain.Stage.T1_LENS) || !TackleManager.isOwned(Tackle.GLITTER_LENS)) return;

        FishRumors.release(getRumour(), EXTENSION_DAYS);
        FishermanChain.setStage(FishermanChain.Stage.T1_BOUGHT);
    }

    public static void finish() {
        if (FishermanChain.isAt(FishermanChain.Stage.T1_BOUGHT)) FishermanChain.setStage(FishermanChain.Stage.T1_DONE);
    }

    // The Glitter Lens schematic and the means to buy it: the specimens its saved fish payment asks for, plus its
    // credit price after the player's cost setting. Without a payment that specimens can meet, credits alone. A
    // player who already owns the lens gets its credit price only.
    protected static List<FishReward.Receipt> grantRewards() {
        List<FishReward.Receipt> receipts = new ArrayList<>();
        Tackle lens = Tackle.GLITTER_LENS;

        if (!ShopSchematics.has(lens)) receipts.add(FishReward.tackleSchematic(lens).grantWithReceipt());

        ShopPricing.Price price = ShopPricing.getPrice(lens);
        if (price == null) return receipts;

        if (!TackleManager.isOwned(lens)) {
            List<FishCatch> specimens = PaymentSpecimens.build(price.fish);
            CargoAPI cargo = getPlayerCargo();
            if (specimens != null && cargo != null) {
                for (FishCatch specimen : specimens) FishItems.stow(cargo, specimen);
                receipts.add(describe(specimens));
            }
        }

        receipts.add(FishReward.credits(price.credits).grantWithReceipt());

        return receipts;
    }

    protected static FishReward.Receipt describe(List<FishCatch> specimens) {
        FishSpec spec = specimens.get(0).getSpec();
        String name = spec.getDisplayName();

        return new FishReward.Receipt(specimens.size() > 1 ? name + " x" + specimens.size() : name, name,
                spec.rarity.color);
    }

    // Any won or lost minigame on a non-legendary lamp catch in the target system counts toward the guarantee.
    public static boolean isChestQualifying(FishSpec fish, SectorEntityToken anchor) {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null || saved.compassPulled || !FishermanChain.isAt(FishermanChain.Stage.T1_ACTIVE)) return false;
        if (fish == null || fish.rarity == FishRarity.LEGENDARY) return false;
        if (CatchImplement.of(anchor) != CatchImplement.BREACH_LAMP) return false;

        return anchor.getContainingLocation() instanceof StarSystemAPI system
                && system.getId().equals(saved.systemId);
    }

    public static boolean isChestDue() {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null) return false;

        int index = saved.chestMinigames + 1;
        if (index >= CHEST_GUARANTEED_AT
                && (index - CHEST_GUARANTEED_AT) % CHEST_GUARANTEED_EVERY == 0) return true;

        return Math.random() < CHEST_CHANCE;
    }

    public static void recordChestMinigame(boolean pulled) {
        FishermanChain.Saved saved = FishermanChain.get(false);
        if (saved == null) return;

        saved.chestMinigames++;
        if (pulled && !saved.compassPulled) {
            saved.compassPulled = true;
            updateIntel();
        }
    }

    // The chest's whole contents; granted by the minigame panel on a win, like any bycatch.
    public static TreasureAward awardCompass() {
        CargoAPI cargo = getPlayerCargo();
        if (cargo != null) cargo.addSpecial(compass(), 1f);

        TreasureAward award = new TreasureAward(TreasureRarity.EPIC, "Fisherman's Chest", Misc.getHighlightColor());
        award.items.add(new TreasureAward.Item(getCompassName(), COMPASS_ICON, 1, Misc.getHighlightColor()));

        return award;
    }

    public static String getCompassName() {
        try {
            return Global.getSettings().getSpecialItemSpec(COMPASS).getName();
        } catch (RuntimeException e) {
            return "Compass";
        }
    }

    public static SpecialItemData compass() {
        return new SpecialItemData(COMPASS, null);
    }

    public static boolean isCarried() {
        CargoAPI cargo = getPlayerCargo();

        return cargo != null && cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, compass()) > 0f;
    }

    // A nearby system first; failing that, the nearest one that qualifies at any distance.
    protected static StarSystemAPI pickSystem() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        Vector2f from = player == null ? new Vector2f() : player.getLocationInHyperspace();

        WeightedRandomPicker<StarSystemAPI> near = new WeightedRandomPicker<>();
        StarSystemAPI nearest = null;
        float best = Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!isCandidate(system)) continue;

            float distance = Misc.getDistanceLY(from, system.getLocation());
            if (distance >= MIN_LY && distance <= MAX_LY) near.add(system, 1f);
            if (distance < best) {
                best = distance;
                nearest = system;
            }
        }

        StarSystemAPI pick = near.pick();

        return pick != null ? pick : nearest;
    }

    // The chest only comes up in lamp catches, so the system must hold a non-legendary pattern the lamps can reach.
    protected static boolean isCandidate(StarSystemAPI system) {
        if (system == null || system.getId() == null || system.getCenter() == null) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER) || system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) return false;
        if (FishRumors.isOccupied(system.getId())) return false;

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.rarity == null || spec.rarity == FishRarity.LEGENDARY) continue;
            if (spec.spawnWeight <= 0f || !spec.hasHabitat()) continue;
            if (FishRanges.matches(spec, system, CatchImplement.BREACH_LAMP)) return true;
        }

        return false;
    }

    protected static CargoAPI getPlayerCargo() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return null;

        return Global.getSector().getPlayerFleet().getCargo();
    }
}
