package catchrelease.abilities.rod.ability;

import catchrelease.ModPlugin;
import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.abilities.rod.entities.RodMoteEntityPlugin;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.abilities.rod.scripts.RoamingDroneSwarmScript;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.jobs.camp.CampedSpot;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
import catchrelease.skillshot.render.AreaReticuleRenderer;
import catchrelease.skillshot.render.SkillshotRenderer;
import catchrelease.skillshot.render.ValidatedAreaReticuleRenderer;
import catchrelease.skillshot.render.validators.PondProximityValidator;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class PondInteractionAbilityPlugin extends BaseSkillshotAbility {

    protected static final String SOUND_POND_OPEN_UI = "catchrelease_ui_rod_pond_open_sfx";
    protected static final String SOUND_DRONE_DISPATCH_UI = "catchrelease_ui_rod_drone_dispatch";

    @Override
    protected String getActivationText() {
        return isRoamingAvailable() ? "Dispatching drones" : "Forcing the rupture";
    }

    @Override
    protected void onActivatedWithoutReticule() {
        if (!entity.isPlayerFleet()) return;

        if (isRoamingAvailable()) {
            RoamingDroneSwarmScript.dispatch();
            return;
        }

        unlockClosestPond();
    }

    public boolean isRoamingAvailable() {
        return SearchlightAbilityPlugin.isBreaching() && hasBreachCoupler();
    }

    protected boolean hasBreachCoupler() {
        return TackleManager.get(Tackle.Fit.DRONE).breachCoupling;
    }

    public void unlockClosestPond() {
        SectorEntityToken pond = getPond();
        if (pond == null || isPondActive(pond) || RodMoteEntityPlugin.isOpening(pond)) return;

        SectorEntityToken t = entity.getContainingLocation().addCustomEntity(Misc.genUID(), null, RodMoteEntityPlugin.ENTITY_ID, null,
                new RodMoteEntityPlugin.RodMoteEntityPluginData(entity.getLocation(), pond, Color.CYAN));
        t.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    public boolean closestPondActive() {
        return isPondActive(getPond());
    }

    protected boolean isPondActive(SectorEntityToken pond) {
        MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
        return plugin != null && plugin.isActive();
    }

    @Override
    protected void deactivateImpl() {
        cleanupImpl();
    }

    @Override
    public boolean showReticuleOnActivation() {
        return closestPondActive();
    }

    @Override
    public String getOnSoundUI() {
        // Keep recalls in drone mode even if the pond or breach-lamp state has changed since the swarm was dispatched.
        if (FishingDroneSwarmScript.getExisting() != null
                || closestPondActive()
                || isRoamingAvailable()) {
            return SOUND_DRONE_DISPATCH_UI;
        }

        return SOUND_POND_OPEN_UI;
    }

    @Override
    public SkillshotRenderer createReticule() {
        float radius = FishingDroneSwarmScript.getRingRadius() * 2f;
        return new ValidatedAreaReticuleRenderer(radius, new PondProximityValidator(radius));
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        SkillshotFramework.log("Casting at " + worldTarget + " (" + angleFromFleet + " degrees)");

        FishingDroneSwarmScript.dispatch(getPond(), worldTarget);
    }

    @Override
    public void pressButton() {
        if (entity != null && entity.isPlayerFleet()) {
            FishingDroneSwarmScript swarm = FishingDroneSwarmScript.getExisting();

            if (swarm != null && !swarm.isRecalling() && swarm.hasRecallableDrones()) {
                swarm.recall();
                playActivationSound();
                return;
            }
        }

        super.pressButton();
    }

    @Override
    public boolean isUsable() {
        FishingDroneSwarmScript swarm = FishingDroneSwarmScript.getExisting();

        if (swarm != null) {
            return !swarm.isRecalling() && swarm.hasRecallableDrones() && disableFrames <= 0;
        }

        // lamps replace the natural rupture with temporary openings the stock drone rig cannot use
        if (SearchlightAbilityPlugin.isBreaching() && !hasBreachCoupler()) return false;

        SectorEntityToken pond = getPond();

        // an occupied rupture is the camp's leverage: leaving is allowed, fishing is not
        if (CampedSpot.isPondBlocked(pond)) return false;

        if (!isPondActive(pond) && RodMoteEntityPlugin.isOpening(pond)) return false;

        // roaming needs no pond; otherwise falls back to requiring a pond in range
        if (!isRoamingAvailable() && pond == null) return false;

        return super.isUsable();
    }

    @Override
    public boolean isActive() {
        return FishingDroneSwarmScript.getExisting() != null;
    }

    @Override
    public boolean showActiveIndicator() {
        return isActive();
    }

    @Override
    public float getCooldownFraction() {
        FishingDroneSwarmScript swarm = FishingDroneSwarmScript.getExisting();
        if (swarm == null) return super.getCooldownFraction();

        if (!swarm.isRecalling()) return 1f;

        return swarm.getRecallProgress();
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        Color gray = Misc.getGrayColor();
        Color highlight = Misc.getHighlightColor();

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addTitle(spec.getName());
        } else {
            tooltip.addSpacer(-10f);
        }

        float pad = 10f;
        tooltip.addPara("Opens a nearby rupture. Activate again to send fishing drones to the selected spot."
                + " Activate while they are hunting to recall them.", pad);

        float reach = FishingDroneSwarmScript.getRingRadius() + FishingDroneSwarmScript.getChaseMargin();
        boolean roaming = isRoamingAvailable();
        if (roaming) reach += Searchlight.getMaxReach();

        tooltip.addPara("Drones: %s    Speed: %s", pad, highlight,
                "" + FishingDroneSwarmScript.getDroneCount(),
                Misc.getRoundedValue(UpgradeManager.getValue(StatIds.DRONE_SPEED, RodConstants.DRONE_SPEED)) + " units/s");
        tooltip.addPara("Pursuit radius: %s from %s", 3f, highlight,
                Misc.getRoundedValue(reach) + " units", roaming ? "your fleet" : "the selected spot");
        tooltip.addPara("Chase time per target: %s", 3f, highlight,
                Misc.getRoundedValueOneAfterDecimalIfNotWhole(UpgradeManager.getValue(
                        StatIds.DRONE_CHASE_TIME, RodConstants.CHASE_TIME_FALLBACK)) + " seconds");

        float rare = UpgradeManager.getValue(StatIds.DRONE_RARE_PRIORITY, 0f);
        if (rare > 0f) {
            tooltip.addPara("Chance to choose the rarest nearby fish: %s", 3f, highlight,
                    Math.round(rare * 100f) + "%");
        }

        Tackle fitted = TackleManager.get(Tackle.Fit.DRONE);
        if (fitted != Tackle.NONE) tooltip.addPara("Fitted: %s", pad, highlight, fitted.name);
        if (hasBreachCoupler()) {
            tooltip.addPara("With breach lamps on, drones follow the fleet and catch illuminated fish.", 3f);
        } else {
            tooltip.addPara("Using drones with breach lamps requires a %s.", pad, highlight,
                    Tackle.BREACH_COUPLER.name);
        }

        if (!Global.CODEX_TOOLTIP_MODE) {
            FishingDroneSwarmScript swarm = FishingDroneSwarmScript.getExisting();
            SectorEntityToken pond = getPond();

            if (swarm != null) {
                if (!swarm.isRecalling() && swarm.hasRecallableDrones()) {
                    tooltip.addPara("Drones deployed. Activate to recall.", highlight, pad);
                } else {
                    tooltip.addPara("Waiting for all drones to return.", gray, pad);
                }
            } else if (CampedSpot.isPondBlocked(pond)) {
                tooltip.addPara("A fleet is blocking this rupture.",
                        Misc.getNegativeHighlightColor(), pad);
            } else if (!isPondActive(pond) && RodMoteEntityPlugin.isOpening(pond)) {
                tooltip.addPara("Opening the rupture.", gray, pad);
            } else if (SearchlightAbilityPlugin.isBreaching() && !hasBreachCoupler()) {
                tooltip.addPara("Turn off the breach lamps to use this rig.", Misc.getNegativeHighlightColor(), pad);
            } else if (!roaming && pond == null) {
                tooltip.addPara("No rupture in range.", Misc.getNegativeHighlightColor(), pad);
            }
        }

        addIncompatibleToTooltip(tooltip, false);
    }

    @Override
    public String getSpriteName() {
        if (closestPondActive() || isRoamingAvailable()) {
            return Global.getSettings().getSpriteName(ModPlugin.MOD_ID, "lyne");
        }

        return super.getSpriteName();
    }

    protected SectorEntityToken getPond() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null || fleet.getContainingLocation() == null) return null;

        SectorEntityToken pond = null;
        for (CampaignTerrainAPI t : fleet.getContainingLocation().getTerrainCopy()) {
            if (!t.hasTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)
                    || !(t.getPlugin() instanceof MaskedFishingPondTerrainPlugin)) continue;
            float distance = Misc.getDistance(t, fleet);
            if (distance < t.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT) pond = t;
        }

        return pond;
    }
}
