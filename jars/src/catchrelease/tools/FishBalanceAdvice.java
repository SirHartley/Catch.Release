package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishMotion;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.FishTuningSheet.Field;
import static catchrelease.tools.SimulatedAngler.Skill;

final class FishBalanceAdvice {

    static String describe(Result result, Skill skill) {
        Stats stats = result.skills().get(skill);
        Spec fish = result.request().fish();
        Setup setup = result.request().setup();
        double gain = FishConstants.MINIGAME_CATCH_RATE * compressed(fish.value(Field.GAIN)) * setup.gain() * setup.tackle().progressMult;
        double loss = FishConstants.MINIGAME_ESCAPE_RATE * compressed(fish.value(Field.LOSS)) * setup.loss() * setup.tackle().escapeMult;
        double required = 100 * loss / (gain + loss);
        StringBuilder text = new StringBuilder("\n\nREADING THIS RESULT\n");
        text.append("The bot caught ").append(number(stats.catchRate())).append("% of attempts. Higher means easier for this bot, not necessarily more enjoyable for a person.\n");
        text.append("It covered the true fish position for ").append(number(stats.coverage())).append("% of the measured time; this setup needs about ")
                .append(number(required)).append("% to avoid losing progress on average. Timing matters: one long miss can still lose the fish.\n");
        text.append("Longest miss: ").append(number(stats.gap())).append("s. A continuous miss drains the starting progress in about ")
                .append(number(FishConstants.MINIGAME_PROGRESS_START / loss)).append("s, or a full meter in ").append(number(1 / loss)).append("s.\n");
        text.append("Early losses (under 2s): ").append(stats.earlyLosses()).append(". Lost after reaching 90% progress: ")
                .append(stats.nearLosses()).append(". Hold/release changes: ").append(number(stats.switching())).append(" per second.\n");
        text.append("Failed attempts consumed ").append(number(stats.failedSeconds())).append(" seconds per attempt on average, including timeouts.\n");
        if (stats.attempts().size() < 100) text.append("This is a quick sample. Increase attempts before reacting to a small difference; even 0% or 100% is not a guarantee.\n");
        if (stats.count(Outcome.CAUGHT) < 5) text.append("There are too few catches to judge typical catch duration or its spread reliably.\n");
        if (stats.count(Outcome.TIMEOUT) > 0) text.append("Some attempts did not finish. Successful catch times hide these stalls; try a longer limit to distinguish slow catches from ongoing stalemates.\n");
        if (stats.count(Outcome.CAUGHT) >= 5 && stats.time(0.9) > 2 * stats.time(0.1)) {
            text.append("Catch times vary widely: the slower end takes over twice as long as the faster end. Check whether long waits or bursts fit this fish, and confirm with more seeds.\n");
        }
        text.append("\nTHINGS TO TEST IF YOU WANT IT EASIER\n");
        if (stats.coverage() < required) {
            text.append("The bot is losing ground overall. Try slightly less movement speed or restlessness so it can track for longer. Difficulty affects both speed and timing, so use it for broader changes.\n");
        } else if (stats.count(Outcome.LOST) > 0) {
            text.append("Average tracking is sufficient, but some attempts still fail. Try a small reduction in escape loss to forgive missed bursts without flattening the movement.\n");
        } else {
            text.append("No losses were observed. Compare with a movement-matched reference before making it easier; the current sample may already be forgiving enough.\n");
        }
        if (stats.earlyLosses() > 0) text.append("For opening losses, try lower escape loss first. Lower speed or restlessness may also give the player time to recover.\n");
        if (stats.nearLosses() > 0) text.append("For near-catch losses, compare slightly higher catch progress with slightly lower escape loss. The first shortens the fight; the second leaves the movement intact but forgives mistakes.\n");
        if (stats.time(0.5) > 15) text.append("Successful attempts are relatively long. If that feels tedious in manual play, test higher catch progress before changing the movement's character.\n");
        if (fish.value(Field.JITTER) > 1) text.append("Visual shake is above the usual multiplier. It moves the picture, not the catch position. Test less shake separately if readability is the problem.\n");
        text.append("Change one field at a time in Experiments, then compare. These are hypotheses from observed results, not an automatic diagnosis. To make a fish harder, test the reverse change cautiously rather than increasing every field.\n");
        text.append("\nMOVEMENT\n").append(movement(fish.motion()));
        return text.toString();
    }

    static double compressed(float value) {
        return 1 + (value - 1) * FishConstants.MINIGAME_RATE_COMPRESSION;
    }

    static String movement(FishMotion motion) {
        return switch (motion) {
            case SMOOTH -> "Smooth picks targets across the track. Speed controls pursuit; restlessness changes how often it chooses another target.";
            case DARTER -> "Darter favors opposite ends with longer waits. Preserve the dash/wait contrast; try longer rests before simply slowing every dash.";
            case SINKER -> "Sinker favors the lower track. Compare it with other sinkers: the bias and bar physics make it different from a floater.";
            case FLOATER -> "Floater favors the upper track. Holding and braking matter differently here; compare against other floaters.";
            case WEAVER -> "Weaver sweeps between ends and waits on arrival. Speed changes the crossing; restlessness affects waits only until the minimum dwell time is reached.";
            case TWITCHER -> "Twitcher mixes short hops with occasional long leaps. Averages can hide the leaps; check long misses and losses after high progress.";
            case LUNGER -> "Lunger creeps near a target, then moves sharply toward the next. The pause is its recovery window; preserve the contrast when tuning.";
            case MIXED -> "Mixed chooses different movement modes during a fight. Large differences between attempts may be part of its identity. Compare mixed fish separately, not as an average of pure types.";
        };
    }
}
