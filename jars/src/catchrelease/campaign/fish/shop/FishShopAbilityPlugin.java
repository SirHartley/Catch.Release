package catchrelease.campaign.fish.shop;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Opens the outfitter. An ability (not a skill) only because a skill has no press to hang a panel
 * off; temporary until this moves to a market/station.
 */
public class FishShopAbilityPlugin extends BaseDurationAbility {

    @Override
    protected void activateImpl() {
        FishShopDialog.open();
    }

    @Override
    protected void applyEffect(float amount, float level) {
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

    @Override
    public boolean isUsable() {
        return Global.getSector().getPlayerFleet() != null && super.isUsable();
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle(spec.getName());

        tooltip.addPara("Opens the outfitter. Upgrades and tackle are paid for in specimens - the"
                + " better the gear, the rarer the fish it costs.", 10f);

        tooltip.addPara("Temporary: this belongs on a market rather than on the ability bar.",
                Misc.getGrayColor(), 10f);
    }
}
