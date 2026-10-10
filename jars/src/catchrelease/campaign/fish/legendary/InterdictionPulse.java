package catchrelease.campaign.fish.legendary;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;

/** Ability lockout from False Dawn's blue mines. */
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
