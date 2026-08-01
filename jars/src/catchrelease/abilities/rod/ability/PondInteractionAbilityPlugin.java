package catchrelease.abilities.rod.ability;

import catchrelease.ModPlugin;
import catchrelease.abilities.rod.entities.RodMoteEntityPlugin;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.entities.MaskedFishingPondEntityPlugin;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
import catchrelease.skillshot.render.AreaReticuleRenderer;
import catchrelease.skillshot.render.SkillshotRenderer;
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

    @Override
    protected String getActivationText() {
        return "Unlocking Pond";
    }

    /** Pond still locked: no aiming involved, the press just forces it open. */
    @Override
    protected void onActivatedWithoutReticule() {
        if (!entity.isPlayerFleet()) return;
        unlockClosestPond();
    }

    public void unlockClosestPond() {
        SectorEntityToken pond = getPond();

        SectorEntityToken t = entity.getContainingLocation().addCustomEntity(Misc.genUID(), null, RodMoteEntityPlugin.ENTITY_ID, null,
                new RodMoteEntityPlugin.RodMoteEntityPluginData(entity.getLocation(), pond, Color.CYAN));
        t.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    public boolean closestPondActive() {
        SectorEntityToken pond = getPond();
        return pond != null && ((MaskedFishingPondEntityPlugin) pond.getCustomPlugin()).isActive();
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
        //for an ability that lands on a spot instead, return new AreaReticuleRenderer(400f) - or
        //new ValidatedAreaReticuleRenderer(400f, new MarketProximityValidator(500f)) to forbid
        //firing near inhabited worlds
        return new AreaReticuleRenderer(100f);
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        SkillshotFramework.log("Example skillshot fired at " + worldTarget + " (" + angleFromFleet + " degrees)");

        SectorEntityToken marker = getFleet().getContainingLocation().createToken(worldTarget);
        getFleet().getContainingLocation().addEntity(marker);
        Misc.fadeAndExpire(marker, 1f);
    }

    @Override
    public boolean isUsable() {
        SectorEntityToken pond = getPond();
        if (pond == null) return false;
        return super.isUsable();
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

        if (!Global.CODEX_TOOLTIP_MODE) {
            SectorEntityToken pond = getPond();
            if (pond == null) {
                tooltip.addPara("Your fleet is not currently near a pond rupture.", Misc.getNegativeHighlightColor(), pad);
            }
        }

        addIncompatibleToTooltip(tooltip, false);
    }

    @Override
    public String getSpriteName() {
        if (closestPondActive()) return Global.getSettings().getSpriteName(ModPlugin.MOD_ID, "placeholder2");
        return super.getSpriteName();
    }

    protected SectorEntityToken getPond() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return null;

        SectorEntityToken pond = null;
        for (SectorEntityToken t : fleet.getContainingLocation().getEntitiesWithTag(MaskedFishingPondEntityPlugin.ENTITY_ID)) {
            float distance = Misc.getDistance(t, fleet);
            if (distance < t.getRadius() * PondConstants.POND_INTERACT_RANGE_MULT) pond = t;
        }

        return pond;
    }
}
