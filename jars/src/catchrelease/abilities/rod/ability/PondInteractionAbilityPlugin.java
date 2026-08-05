package catchrelease.abilities.rod.ability;

import catchrelease.ModPlugin;
import catchrelease.abilities.rod.entities.RodMoteEntityPlugin;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.abilities.rod.scripts.RoamingDroneSwarmScript;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
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

    //Press once to unlock nearby pond
    //once unlocked, this ability changes to a targetted skillshot instead for the angler behaviour
    //away from any pond, with the breach lamps lit, the press sends a roaming screen instead
    //while a swarm is out the press is the recall instead, and the ability reads as active until
    //the last drone is home

    @Override
    protected String getActivationText() {
        return isRoamingAvailable() ? "Dispatching Drones" : "Unlocking Pond";
    }

    /**
     * The two presses that involve no aiming: sending a screen out, and forcing a shut pond open.
     * <p>
     * The lamps come first, and it is the same rule read from either side. The rod cannot force a
     * rupture open under them - the two rigs take turns on the fabric - and it has something better
     * to do than try, because the beams have already cut the windows the drones would be going
     * through. With the lights out the press is exactly what it always was.
     */
    @Override
    protected void onActivatedWithoutReticule() {
        if (!entity.isPlayerFleet()) return;

        if (isRoamingAvailable()) {
            RoamingDroneSwarmScript.dispatch();
            return;
        }

        unlockClosestPond();
    }

    /**
     * Whether the rod can fish without a pond, which is entirely a question about the lights.
     * <p>
     * A screen with no windows to reach through would fly a circle around the fleet catching nothing
     * at all, so the lamps burning is the whole of the condition - and it is also the condition
     * under which the rod could not have opened a rupture anyway, the two rigs taking turns on the
     * fabric. Those being one question rather than two is why the press does not have to choose:
     * beside an open pond the lamps have already yielded, and everywhere else they are what the rod
     * has to work with.
     */
    public boolean isRoamingAvailable() {
        return SearchlightAbilityPlugin.isBreaching();
    }

    public void unlockClosestPond() {
        SectorEntityToken pond = getPond();

        SectorEntityToken t = entity.getContainingLocation().addCustomEntity(Misc.genUID(), null, RodMoteEntityPlugin.ENTITY_ID, null,
                new RodMoteEntityPlugin.RodMoteEntityPluginData(entity.getLocation(), pond, Color.CYAN));
        t.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    public boolean closestPondActive() {
        MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(getPond());
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
    public SkillshotRenderer createReticule() {
        //sized to the ring the swarm fishes rather than the tight circle it patrols, so the reticule
        //shows what the cast will actually cover. Doubled because the reticule takes a diameter, and
        //read off the upgrade so a wider ring is visible while aiming rather than only afterwards
        float radius = FishingDroneSwarmScript.getRingRadius() * 2f;
        return new ValidatedAreaReticuleRenderer(radius, new PondProximityValidator(radius));
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        SkillshotFramework.log("Casting at " + worldTarget + " (" + angleFromFleet + " degrees)");

        FishingDroneSwarmScript.dispatch(getPond(), worldTarget);
    }

    /**
     * The press means "bring them back" while a swarm is out, and only casts when the rod is idle.
     * <p>
     * Deliberately ahead of the vanilla path: a cast leaves the ability on its spec cooldown, so the
     * ordinary press would be swallowed for exactly the stretch the drones are away - the one stretch
     * a recall has to be possible.
     */
    @Override
    public void pressButton() {
        if (entity != null && entity.isPlayerFleet()) {
            FishingDroneSwarmScript swarm = FishingDroneSwarmScript.getExisting();

            if (swarm != null && !swarm.isRecalling()) {
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

        //out fishing: the button is the recall, so it stays live regardless of the cast's rearm.
        //already coming home: nothing left to ask for until they land.
        //Asked before the pond is, because a roaming screen is out nowhere near one and the recall
        //has to stay reachable wherever the drones happen to be working
        if (swarm != null) return !swarm.isRecalling() && disableFrames <= 0;

        //a screen can go out anywhere there are windows to send it through. Without them the rod is
        //back to needing water in reach, which is the only other thing this button has ever done
        if (!isRoamingAvailable() && getPond() == null) return false;

        return super.isUsable();
    }

    /** Reads as active for as long as the drones are away, recall included - they are still out. */
    @Override
    public boolean isActive() {
        return FishingDroneSwarmScript.getExisting() != null;
    }

    @Override
    public boolean showActiveIndicator() {
        return isActive();
    }

    /**
     * The wait is the drones' trip home, not a fixed timer.
     * <p>
     * While they are fishing there is nothing to wait for - the button is the recall - so the icon
     * reads active rather than darkened. Once they are on their way back it fills as they land.
     */
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

        tooltip.addPara("Away from any rupture, with the %s lit, sends a drone screen out around the"
                + " fleet instead - it flies with you and goes through the beams' own windows after"
                + " whatever they have found down there.", pad, highlight, "breach lamps");

        if (!Global.CODEX_TOOLTIP_MODE) {
            //one line, in the order the press itself decides in. The lamps burning is not a
            //complaint any more - it is what the button is going to do
            if (isRoamingAvailable()) {
                tooltip.addPara("The breach lamps are lit. The drones will roam.", highlight, pad);
            } else if (getPond() == null) {
                tooltip.addPara("Your fleet is not currently near a pond rupture.", Misc.getNegativeHighlightColor(), pad);
            }

            FishingDroneSwarmScript swarm = FishingDroneSwarmScript.getExisting();
            if (swarm != null && !swarm.isRecalling()) {
                tooltip.addPara("Drones are out. Activate again to recall them.", highlight, pad);
            } else if (swarm != null) {
                tooltip.addPara("Drones are on their way back.", gray, pad);
            }
        }

        addIncompatibleToTooltip(tooltip, false);
    }

    /** The working icon whenever the press will send drones, whichever of the two ways it would. */
    @Override
    public String getSpriteName() {
        if (closestPondActive() || isRoamingAvailable()) {
            return Global.getSettings().getSpriteName(ModPlugin.MOD_ID, "placeholder2");
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
