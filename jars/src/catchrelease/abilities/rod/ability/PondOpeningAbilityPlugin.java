package catchrelease.abilities.rod.ability;

import catchrelease.campaign.ponds.entities.MaskedFishingPondEntityPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class PondOpeningAbilityPlugin extends BaseDurationAbility {

    @Override
    protected String getActivationText() {
        return "Unlocking Pond";
    }

    @Override
    protected void activateImpl() {
        if (entity.isPlayerFleet()) {
            getPond().activate();
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
    }

    @Override
    protected void applyEffect(float amount, float level) {}

    @Override
    protected void deactivateImpl() {
        cleanupImpl();
    }

    @Override
    protected void cleanupImpl() {}

    @Override
    public boolean isUsable() {
        MaskedFishingPondEntityPlugin pond = getPond();
        if (pond == null) return false;
        return super.isUsable();
    }

    protected MaskedFishingPondEntityPlugin getPond() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return null;

        MaskedFishingPondEntityPlugin pond = null;
        for (SectorEntityToken t : fleet.getContainingLocation().getEntitiesWithTag(MaskedFishingPondEntityPlugin.ENTITY_ID)){
            float distance = Misc.getDistance(t, fleet);
            MaskedFishingPondEntityPlugin plugin = (MaskedFishingPondEntityPlugin) t.getCustomPlugin();
            if (distance < t.getRadius() * 1.5f) pond = plugin;
        }

        return pond;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
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
            MaskedFishingPondEntityPlugin pond = getPond();
            if (pond == null) {
                tooltip.addPara("Your fleet is not currently near a pond rupture.", Misc.getNegativeHighlightColor(), pad);
            }
        }

        addIncompatibleToTooltip(tooltip, expanded);
    }

    public boolean hasTooltip() {
        return true;
    }
}
