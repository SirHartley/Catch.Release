package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.Set;

// T1: the treasure rumour. The Fisherman's compass comes up as a golden chest in one system's lamp catches.
public final class TreasureRumour {

    public static final String COMPASS = "catchrelease_compass";

    public static final float MIN_LY = 2f;
    public static final float MAX_LY = 10f;

    private TreasureRumour() {
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
            if (isReturning()) return "The Fisherman's compass: take it back";

            return "The Fisherman's golden chest";
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
            } else {
                info.addPara("Lamp catches in %s", pad, text, Misc.getHighlightColor(), getSystemName());
                info.addPara("Take the golden chest when it comes up", text, 0f);
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

            info.addPara("The Fisherman has lost a golden chest in %s and is sure it is there.", opad, h,
                    getSystemName());
            info.addPara("It can come up with any catch made under Breach Lights in that system, never from a "
                    + "rupture. Take it in the catch window like any other bycatch.", opad);
            info.addPara("Reward: a rig module schematic, with the fish and credits to buy it.", opad, h,
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
            if (!FishermanChain.isAt(FishermanChain.Stage.T1_ACTIVE) || isReturning()) return null;

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
