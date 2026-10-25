package catchrelease;

import catchrelease.campaign.fish.fisherman.chain.CaptainsFleet;
import catchrelease.combat.salvage.FighterStoreRefitButton;
import catchrelease.combat.salvage.LyneDroneAI;
import catchrelease.combat.salvage.RefitStoreView;
import catchrelease.combat.salvage.WreckTracker;
import catchrelease.combat.tug.HarpoonAutofireAI;
import catchrelease.combat.tug.HarpoonOnHit;
import catchrelease.combat.tug.TugDriveHullmod;
import catchrelease.campaign.fish.fisherman.chain.WoundedEpic;
import catchrelease.config.PlayerSettings;
import catchrelease.campaign.crime.CatchReleaseCampaignPlugin;
import catchrelease.campaign.crime.HarpoonPatrolResponse;
import catchrelease.campaign.crime.LampPatrolResponse;
import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.legendary.EpicHunt;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.legendary.LegendaryHaunt;
import catchrelease.campaign.fish.legendary.LonglinerDecoy;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.coherence.CoherenceOverlayScript;
import catchrelease.campaign.fish.colony.AquariumTankScript;
import catchrelease.campaign.fish.colony.ConservatoryOptionProvider;
import catchrelease.campaign.fish.fisherman.chain.FishermanChain;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.fisherman.CoreFisherSpawner;
import catchrelease.campaign.fish.fisherman.FishermanQuest;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.campaign.fish.items.SoldFishRestock;
import catchrelease.campaign.fish.tutorial.RatingBarEvent;
import catchrelease.campaign.fish.tutorial.FishermanInterception;
import catchrelease.campaign.fish.tutorial.TutorialWreck;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import catchrelease.campaign.fish.jobs.fleet.ExpeditionRouteManager;
import catchrelease.campaign.fish.jobs.fleet.FleetQuestSpawner;
import catchrelease.campaign.fish.jobs.fleet.ProspectingRouteManager;
import catchrelease.campaign.fish.jobs.fleet.CatchReleaseDistressProvider;
import catchrelease.campaign.fish.map.FishIntelPlanetPanel;
import catchrelease.campaign.fish.map.FishMapFilterScript;
import catchrelease.memory.charges.ChargeManager;
import catchrelease.campaign.fish.spawner.BuriedMoteSpawner;
import catchrelease.campaign.ponds.listener.OnJumpPondSpawner;
import catchrelease.rendering.spiral.BlackHoleSpiralWarp;
import catchrelease.distress.DistressCallFramework;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.testing.DevShortcut;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin.PickPriority;
import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;

import java.util.LinkedHashSet;
import java.util.Set;

public class ModPlugin extends BaseModPlugin {

    public static final String MOD_ID = "catchrelease";

    @Override
    public void onApplicationLoad() {
        PlayerSettings.install();
        FighterStoreRefitButton.register();
    }

    @Override
    public PluginPick<AutofireAIPlugin> pickWeaponAutofireAI(WeaponAPI weapon) {
        if (!HarpoonOnHit.WEAPON_ID.equals(weapon.getId())) return null;
        return new PluginPick<>(new HarpoonAutofireAI(weapon), PickPriority.MOD_SPECIFIC);
    }

    @Override
    public PluginPick<ShipAIPlugin> pickShipAI(FleetMemberAPI member, ShipAPI ship) {
        if (!ship.isFighter() || !ship.getHullSpec().hasTag(WreckTracker.DRONE_TAG)) return null;
        return new PluginPick<>(new LyneDroneAI(ship), PickPriority.MOD_SPECIFIC);
    }

    @Override
    public void onCodexDataGenerated() {
        super.onCodexDataGenerated();

        FishCodex.install();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);
        PlayerSettings.install();

        OnJumpPondSpawner.register();
        EpicHunt.register();
        BuriedMoteSpawner.register();
        ChargeManager.register();

        CatchReleaseCampaignPlugin.register();
        HarpoonPatrolResponse.register();
        LampPatrolResponse.register();

        FleetQuestSpawner.register();
        ExpeditionRouteManager.register();
        ProspectingRouteManager.register();

        FishermanSpawner.register();
        CoreFisherSpawner.register();
        FishermanQuest.Keeper.register();

        TutorialWreck.Watcher.register();
        RatingBarEvent.VisitCounter.register();
        FishermanChain.register();
        FishingShips.register();
        TugDriveHullmod.BurnWatch.register();
        RefitStoreView.register();
        SoldFishRestock.register();
        FishermanInterception.register();
        FishingIntro.Keeper.register();

        ConservatoryOptionProvider.register();
        AquariumTankScript.register();

        Aberration.Watcher.register();
        FishRanges.register();
        LegendaryHaunt.register();
        LonglinerDecoy.register();
        CatchReleaseDistressProvider.register();
        DistressCallFramework.register();
        SkillshotFramework.register();

        // transient - a save should never carry a screen-watcher
        Global.getSector().addTransientScript(new FishMapFilterScript());
        Global.getSector().addTransientScript(new FishIntelPlanetPanel());
        Global.getSector().addTransientScript(new CoherenceOverlayScript());

        // housekeeping, once, before anything is looked at
        sweepPondClaims();

        // an input listener rather than a script; inert unless dev mode is on
        DevShortcut.register();
    }

    protected void sweepPondClaims() {
        Set<String> known = new LinkedHashSet<>();
        known.add(TutorialConstants.TARGET_KEY);
        known.add(FishermanQuest.STATE_KEY);
        known.add(FishJob.REF_KEY);
        known.add(CaptainsFleet.PLANTER);
        known.add(WoundedEpic.PLANTER);

        Set<String> live = new LinkedHashSet<>();
        if (FishingIntro.getTarget() != null) live.add(TutorialConstants.TARGET_KEY);
        if (FishermanQuest.getActive() != null) live.add(FishermanQuest.STATE_KEY);
        if (CaptainsFleet.isSearching()) live.add(CaptainsFleet.PLANTER);
        if (WoundedEpic.isHunting()) live.add(WoundedEpic.PLANTER);

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
            if (intel instanceof FishJob && !intel.isEnded()) {
                live.add(FishJob.REF_KEY);
                break;
            }
        }

        QuestPond.sweep(known, live);
    }

    @Override
    public void beforeGameSave() {
        super.beforeGameSave();
        SkillshotFramework.reset();
        catchrelease.campaign.fish.legendary.FalseDawnCorona.beforeSave();
    }
}
