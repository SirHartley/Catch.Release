package catchrelease.abilities.rod.ability;

import catchrelease.ModPlugin;
import catchrelease.abilities.rod.entities.RodMoteEntityPlugin;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.abilities.rod.scripts.RoamingDroneSwarmScript;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.jobs.camp.CampedSpot;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
import catchrelease.skillshot.render.AreaReticuleRenderer;
import catchrelease.skillshot.render.SkillshotRenderer;
import catchrelease.skillshot.render.ValidatedAreaReticuleRenderer;
import catchrelease.skillshot.render.validators.PondProximityValidator;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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
        tooltip.addPara("Forces open a pond rupture.", pad);

        tooltip.addPara("Away from any rupture, with a %s fitted and the %s lit, sends a drone screen"
                        + " out around the fleet instead - it flies with you and goes through the"
                        + " beams' own openings after whatever they have found down there.", pad,
                highlight, Tackle.BREACH_COUPLER.name, "breach lamps");

        if (!Global.CODEX_TOOLTIP_MODE) {
            SectorEntityToken pond = getPond();

            if (CampedSpot.isPondBlocked(pond)) {
                tooltip.addPara("A fleet is sitting on this rupture. The ROD cannot be deployed here.",
                        Misc.getNegativeHighlightColor(), pad);
            } else if (!isPondActive(pond) && RodMoteEntityPlugin.isOpening(pond)) {
                tooltip.addPara("This rupture is already being forced open.", gray, pad);
            } else if (SearchlightAbilityPlugin.isBreaching() && !hasBreachCoupler()) {
                tooltip.addPara("The breach lamps are lit, but the drone rig needs a %s to use their"
                                + " openings.", pad, Misc.getNegativeHighlightColor(),
                        Tackle.BREACH_COUPLER.name);
            } else if (isRoamingAvailable()) {
                tooltip.addPara("The breach lamps are lit. The drones will roam.", highlight, pad);
            } else if (pond == null) {
                tooltip.addPara("Your fleet is not currently near a pond rupture.", Misc.getNegativeHighlightColor(), pad);
            }

            FishingDroneSwarmScript swarm = FishingDroneSwarmScript.getExisting();
            if (swarm != null && !swarm.isRecalling() && swarm.hasRecallableDrones()) {
                tooltip.addPara("Drones are out. Activate again to recall them.", highlight, pad);
            } else if (swarm != null) {
                tooltip.addPara("Drones are on their way back.", gray, pad);
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
        if (fleet == null) return null;

        SectorEntityToken pond = null;
        for (SectorEntityToken t : fleet.getContainingLocation().getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)) {
            float distance = Misc.getDistance(t, fleet);
            if (distance < t.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT) pond = t;
        }

        return pond;
    }
}
