package catchrelease.campaign.fish.legendary;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.util.Misc;

/**
 * Vanilla-style interdiction with no source on the plot: burn abilities knocked onto
 * cooldown. Delivered only by things that touched the fleet - the moray's flung motes
 * and the False Dawn's blue mines - never on a random timer.
 */
public final class InterdictionPulse {

    public static final float ABILITY_COOLDOWN_DAYS = 1f;

    private InterdictionPulse() {
    }

    public static void fire(CampaignFleetAPI player) {
        if (player == null) return;

        for (AbilityPlugin ability : player.getAbilities().values()) {
            if (ability == null || ability.getSpec() == null) continue;

            boolean interdictable = ability.getSpec().hasTag(Abilities.TAG_BURN + "+")
                    || ability.getSpec().hasTag(Abilities.TAG_DISABLED_BY_INTERDICT);
            if (!interdictable) continue;

            ability.deactivate();
            ability.setCooldownLeft(Math.max(ability.getCooldownLeft(),
                    ABILITY_COOLDOWN_DAYS));
        }

        Global.getSector().getCampaignUI().addMessage(
                "Interdiction pulse detected. No source appears on the sensor plot.",
                Misc.getNegativeHighlightColor());
    }

    /** The abort-side release: a lingering lockout would be a trace of the haunt. */
    public static void release(CampaignFleetAPI player) {
        if (player == null) return;

        for (AbilityPlugin ability : player.getAbilities().values()) {
            if (ability == null || ability.getSpec() == null) continue;

            boolean interdictable = ability.getSpec().hasTag(Abilities.TAG_BURN + "+")
                    || ability.getSpec().hasTag(Abilities.TAG_DISABLED_BY_INTERDICT);
            if (!interdictable) continue;

            ability.setCooldownLeft(Math.min(ability.getCooldownLeft(), 0.1f));
        }
    }
}
