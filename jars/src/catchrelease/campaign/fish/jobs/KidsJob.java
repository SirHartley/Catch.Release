package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Two children who have decided that fish fight each other.
 * <p>
 * They do not. The job is two specimens and a decision about which child gets the better one, which
 * is a decision with no correct answer and no mechanical consequence beyond who is pleased - the
 * bonus is paid for bringing something worth arguing over, not for choosing right.
 * <p>
 * No credits, on purpose. Children do not have money, and a job that paid out in credits because
 * the framework found that easiest would be the framework talking rather than the children. What
 * they have is the contents of their pockets and things adults gave them without looking.
 */
public class KidsJob extends FishJob {

    /** The flag that puts this job's own pair of options up instead of the shared hand-over. */
    public static final String CHOICE_FLAG = "$catchrelease_duelChoice";

    public static final int VALUE = 2200;

    public static final float DAYS = 30f;

    /** What the better specimen has to grade to earn the extra. */
    public static final FishGrade BONUS_GRADE = FishGrade.FINE;

    /** Which of them ends up with the better fish. Flavour, and the only thing the choice moves. */
    protected boolean toLoud = true;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_duelRef", "$catchrelease_duelInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.SPACER);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        //two, because there are two of them, and that is the entire specification they gave
        FishRequirement ask = new FishRequirement();
        ask.count = 2;

        addAsk(ask);

        addRewards(FishRewardRoller.roll(genRandom, VALUE, false));

        setUpSpine();

        return true;
    }

    @Override
    protected String getDeliverFlag() {
        return CHOICE_FLAG;
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        if ("turnInLoud".equals(action) || "turnInQuiet".equals(action)) {
            toLoud = "turnInLoud".equals(action);

            handOver(dialog, memoryMap);

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    /**
     * Paid for the specimen rather than for the choice, since the choice cannot be got wrong and
     * rewarding it would only teach the player to guess.
     */
    @Override
    protected boolean payBonus(FishCatch offered) {
        if (offered == null || offered.getGrade().ordinal() < BONUS_GRADE.ordinal()) return false;

        for (FishReward extra : FishRewardRoller.roll(random(), VALUE / 2, false)) {
            extra.grant();
            rewards.add(extra);
        }

        return true;
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        token(mem, "$catchreleaseKid", toLoud ? "the loud one" : "the quiet one");
        token(mem, "$catchreleaseOther", toLoud ? "the quiet one" : "the loud one");
        token(mem, "$catchreleaseOtherCap", toLoud ? "The quiet one" : "The loud one");
    }

    @Override
    public String getBaseName() {
        return "The Battle";
    }
}
