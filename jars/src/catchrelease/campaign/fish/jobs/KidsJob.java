package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
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

            handOver(dialog == null ? null : dialog.getTextPanel(), dialog, memoryMap);

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    @Override
    protected void printPaid(TextPanelAPI text, FishCatch offered) {
        String winner = toLoud ? "the loud one" : "the quiet one";
        String other = toLoud ? "the quiet one" : "the loud one";

        text.addPara("You hand the better of the two to %s. There is a silence of the kind that "
                        + "precedes either delight or a formal complaint.", Misc.getTextColor(),
                Misc.getHighlightColor(), winner);

        boolean impressive = offered != null && offered.getGrade().ordinal() >= BONUS_GRADE.ordinal();

        if (impressive) {
            //paid for the specimen rather than for the choice, since the choice cannot be got wrong
            //and rewarding it would only teach the player to guess
            text.addPara("It is a serious fish. Both of them go very quiet, and then %s empties "
                            + "a pocket onto the table without being asked.", Misc.getTextColor(),
                    Misc.getHighlightColor(), other);

            for (FishReward extra : FishRewardRoller.roll(random(), VALUE / 2, false)) {
                extra.grant();
                rewards.add(extra);
            }
        } else {
            text.addPara("%s declares this unfair. The declaration is noted and ignored.",
                    Misc.getTextColor(), Misc.getHighlightColor(), Misc.ucFirst(other));
        }

        text.addPara("Between them they produce %s, which they clearly consider a fortune, and "
                        + "which they are not wrong about.", Misc.getTextColor(),
                Misc.getHighlightColor(), describeRewards());
    }

    @Override
    protected void printBlurb(TextPanelAPI text) {
        text.addPara("Two children who should not be in here are conducting negotiations at a "
                + "corner table. One of them is doing all the talking. The other one has a bucket.");
    }

    @Override
    protected void printOffer(TextPanelAPI text) {
        text.addPara("\"We need two,\" the loud one says. \"For the battle.\"");

        text.addPara("The quiet one turns the bucket so you can see into it. There is nothing in "
                + "the bucket. This does not appear to have discouraged anyone.");

        text.addPara("\"%s. One each. Then they fight.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), Misc.ucFirst(describeAsks()));

        text.addPara("You point out that they will not fight. This is dismissed as an adult "
                + "opinion. The payment, when it is produced, is %s - and it is produced from four "
                + "pockets, so you understand that it is all of it.", Misc.getTextColor(),
                Misc.getHighlightColor(), describeRewards());
    }

    @Override
    protected void printAccepted(TextPanelAPI text) {
        text.addPara("The loud one shakes your hand. The quiet one does not, but does nod, which "
                + "you gather is the binding part.");
    }

    @Override
    protected void printDeclined(TextPanelAPI text) {
        text.addPara("They take this well, which is somehow worse.");
    }

    @Override
    protected void printReminder(TextPanelAPI text) {
        text.addPara("\"Have you got them yet.\" It is the fourth time today for somebody.");
    }

    @Override
    public String getBaseName() {
        return "The Battle";
    }
}
