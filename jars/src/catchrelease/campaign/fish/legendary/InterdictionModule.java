package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

/**
 * Sourceless interdiction pulses: burn abilities knocked onto cooldown the way vanilla's
 * pulse does it, plus a few seconds of dragging slow. Nothing appears on the plot.
 */
public class InterdictionModule extends BaseHauntModule {

    public static final float FIRST_MIN_SECONDS = 40f;
    public static final float FIRST_MAX_SECONDS = 80f;
    public static final float PULSE_MIN_SECONDS = 100f;
    public static final float PULSE_MAX_SECONDS = 180f;
    public static final float ABILITY_COOLDOWN_DAYS = 1f;
    public static final float SLOW_SECONDS = 3f;

    protected float pulseTimer;
    protected float slowLeft;

    public InterdictionModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);

        pulseTimer = MathUtils.getRandomNumberInRange(FIRST_MIN_SECONDS, FIRST_MAX_SECONDS);
    }

    @Override
    public void advance(float amount) {
        CampaignFleetAPI player = player();
        if (player == null) return;

        pulseTimer -= amount;
        if (pulseTimer <= 0f) {
            pulseTimer = MathUtils.getRandomNumberInRange(
                    PULSE_MIN_SECONDS, PULSE_MAX_SECONDS);
            pulse(player);
            slowLeft = SLOW_SECONDS;
        }

        if (slowLeft > 0f) {
            slowLeft -= amount;
            player.goSlowOneFrame();
        }
    }

    /** Vanilla-style interdiction with no source: shared with the moray's flung motes. */
    public static void pulse(CampaignFleetAPI player) {
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
                "Interdiction pulse - no source on the plot.",
                Misc.getNegativeHighlightColor());
    }

    @Override
    public void cleanup() {
        slowLeft = 0f;

        // a lingering ability lockout would be a trace; the chase ends with the throttle back
        CampaignFleetAPI player = player();
        if (player != null) {
            for (AbilityPlugin ability : player.getAbilities().values()) {
                if (ability == null || ability.getSpec() == null) continue;

                boolean interdictable = ability.getSpec().hasTag(Abilities.TAG_BURN + "+")
                        || ability.getSpec().hasTag(Abilities.TAG_DISABLED_BY_INTERDICT);
                if (!interdictable) continue;

                ability.setCooldownLeft(Math.min(ability.getCooldownLeft(), 0.1f));
            }
        }

        super.cleanup();
    }
}
