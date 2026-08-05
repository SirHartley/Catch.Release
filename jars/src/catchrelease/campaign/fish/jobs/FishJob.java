package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Somebody in a bar who wants fish, and what they are offering for it.
 * <p>
 * The shape every fishing job shares, so a job is written as what is wanted and what is paid rather
 * than as a state machine. Vanilla's hub mission underneath already knows how to be a multi-stage
 * thing with intel, a time limit, an abandon button and a reputation consequence, and there is no
 * version of that worth writing again - what it does not know is anything about fish.
 * <p>
 * So this adds the two halves that are ours. A job holds a list of {@link FishRequirement}s, which
 * is the same class the shop prices its gear in, so "three crabs, graded fine or better, taken in
 * the Abyss" is expressed once and understood everywhere. And it holds a list of
 * {@link FishReward}s, which hand themselves over without the job knowing what they are.
 * <p>
 * Everything a job says is written in Java rather than in the rules sheet. The sheet gets three
 * short rows per job and no prose at all: what a person in a bar says runs to paragraphs with
 * quotes and commas in them, which is exactly the shape that takes the game down at load when one
 * of them lands wrong in a CSV. The rows that remain are the ones vanilla insists on owning - the
 * bar prompt and the bar option - and even those only route back into here.
 */
public abstract class FishJob extends HubMissionWithBarEvent {

    /**
     * Where the job hangs itself on its giver, so the sheet can reach it without naming it.
     * <p>
     * One key for every fishing job rather than one per job, which is what lets ten jobs share six
     * rules rows between them: a row saying {@code Call $catchrelease_jobRef turnIn} does not care
     * whose job it is. The cost is that a person may only be running one fishing job at a time,
     * which is a sentence nobody would argue with anyway.
     */
    public static final String REF_KEY = "$catchrelease_jobRef";

    /** Set on the giver while the fish are owed, which is what puts the hand-over option up. */
    public static final String DELIVER_FLAG = "$catchrelease_jobDeliver";

    /** Whether the hold covers the whole ask, refreshed every time the dialogue asks. */
    public static final String HAS_FISH_KEY = "$catchreleaseHasFish";

    public enum Stage {
        /** Accepted, and the fish are not caught yet. Where a job spends nearly all of its life. */
        WANTED,

        /** Handed over and paid for. */
        DONE,

        /** The clock ran out, or the giver stopped wanting it. */
        FAILED,

        ABANDONED
    }

    /**
     * What is being asked for, in order.
     * <p>
     * A list rather than one, because several of these jobs want more than one thing at once - three
     * kinds for a dish, a pair for a battle - and a job that could only ask for one would have to
     * fake the rest with stages.
     */
    protected List<FishRequirement> asks = new ArrayList<>();

    /** What is on offer for it. Granted together, when the last ask is satisfied. */
    protected List<FishReward> rewards = new ArrayList<>();

    /**
     * Which faction's people ask for this, or null for anybody's.
     * <p>
     * Checked before the job is built rather than after, which is the hook vanilla gives for exactly
     * this - a job that cannot be offered here should never have been made.
     */
    protected String factionId = null;

    /** Days before the giver gives up, or zero for a job with no clock on it. */
    protected float days = 0f;

    /** How many hand-overs this one has taken, for the jobs that ask twice. */
    protected int round = 0;

    protected void addAsk(FishRequirement ask) {
        if (ask != null) asks.add(ask);
    }

    protected void addReward(FishReward reward) {
        if (reward != null) rewards.add(reward);
    }

    protected void addRewards(List<FishReward> rolled) {
        if (rolled != null) rewards.addAll(rolled);
    }

    public List<FishRequirement> getAsks() {
        return asks;
    }

    public List<FishReward> getRewards() {
        return rewards;
    }

    public int getRound() {
        return round;
    }

    /**
     * Finds or invents the person asking, and hangs the job off them.
     * <p>
     * In the comm directory rather than out of it, which is not a cosmetic choice: an important
     * person who is not listed cannot be talked to again, and a delivery job whose giver cannot be
     * found once the fish are caught is a job that can only ever be abandoned.
     *
     * @return false if this person is already running a fishing job, in which case the job is not
     *         built - two people in one bar wanting fish is a coincidence, one person wanting fish
     *         twice is a bug the player would notice
     */
    protected boolean setUpGiver(MarketAPI market) {
        if (market == null) return false;

        findOrCreateGiver(market, true, true);

        PersonAPI person = getPerson();
        if (person == null) return false;

        return setPersonMissionRef(person, REF_KEY);
    }

    /**
     * Sets up the spine every job shares. Call from a subclass's own create, after the asks and
     * rewards are decided, and add any further stages afterwards.
     */
    protected void setUpSpine() {
        setStartingStage(Stage.WANTED);
        setSuccessStage(Stage.DONE);
        setFailureStage(Stage.FAILED);
        setAbandonStage(Stage.ABANDONED);

        if (days > 0f) setTimeLimit(Stage.FAILED, days, null, Stage.DONE);

        //while the fish are owed and no longer once they are not, which is the whole lifetime of the
        //hand-over option - the flag going away is what takes the option away
        makeImportant(getPerson(), getDeliverFlag(), Stage.WANTED);
    }

    /**
     * Which flag puts this job's hand-over options up.
     * <p>
     * The shared one for the jobs whose hand-over is "give them the fish". A job whose hand-over is
     * a decision - which child gets the better specimen, whether to bet on a fight - answers with a
     * flag of its own and brings its own rows, so the shared option never appears beside them.
     */
    protected String getDeliverFlag() {
        return DELIVER_FLAG;
    }

    /**
     * Only where the right people drink.
     * <p>
     * Asked before the job exists, so a Hegemony-only job on a pirate station costs nothing but the
     * question. Subclasses that want more than a faction - a market size, a condition, a hostility -
     * override this and call up to it.
     */
    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (market == null) return false;
        if (factionId == null) return true;

        return factionId.equals(market.getFactionId());
    }

    /** Whether the player is carrying everything this job asked for, right now. */
    public boolean isSatisfied() {
        for (FishRequirement ask : asks) {
            if (FishCurrency.count(ask) < ask.count) return false;
        }

        return true;
    }

    /**
     * The best thing aboard that would go towards the first ask, or null.
     * <p>
     * For the jobs that pay more for a better specimen. Read before anything is spent, since after
     * the hand-over there is nothing left to measure.
     */
    public FishCatch getBestOffered() {
        return asks.isEmpty() ? null : FishCurrency.findBest(asks.get(0));
    }

    /**
     * Takes the fish and pays for them, all of it or none of it.
     * <p>
     * Counted in full before anything is spent, because the alternative is a job that eats the first
     * two asks, finds the third short, and leaves the player worse off than before they walked in.
     * The spending itself is the shop's own, so a job turns fish in the same way a purchase spends
     * them, bundles broken and all.
     *
     * @return whether the hand-over happened
     */
    public boolean turnIn() {
        if (!isSatisfied()) return false;

        for (FishRequirement ask : asks) {
            if (!FishCurrency.spend(ask)) return false;
        }

        for (FishReward reward : rewards) {
            reward.grant();
        }

        round++;

        return true;
    }

    /** The whole ask as one sentence, for the bar text and the intel entry alike. */
    public String describeAsks() {
        List<String> parts = new ArrayList<>();
        for (FishRequirement ask : asks) parts.add(ask.describe());

        return join(parts);
    }

    /** The whole offer, the same way. */
    public String describeRewards() {
        List<String> parts = new ArrayList<>();
        for (FishReward reward : rewards) parts.add(reward.describe());

        return join(parts);
    }

    protected static String join(List<String> parts) {
        if (parts.isEmpty()) return "nothing";
        if (parts.size() == 1) return parts.get(0);

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(i == parts.size() - 1 ? " and " : ", ");
            out.append(parts.get(i));
        }

        return out.toString();
    }

    /**
     * Everything the job says, reached from the sheet by name.
     * <p>
     * Note that returning false here is not a way to say no: vanilla throws on an unhandled action
     * rather than treating it as a failed condition, so every verb this job knows answers true and
     * says its piece, and a hand-over that cannot happen says so in prose instead.
     */
    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        TextPanelAPI text = dialog == null ? null : dialog.getTextPanel();

        switch (action) {
            case "blurb":
                printBlurb(text);
                return true;

            case "offer":
                printOffer(text);
                return true;

            case "accepted":
                printAccepted(text);
                return true;

            case "declined":
                printDeclined(text);
                return true;

            case "remind":
                printReminder(text);
                return true;

            case "turnIn":
                handOver(text, dialog, memoryMap);
                return true;

            default:
                return super.callAction(action, ruleId, dialog, params, memoryMap);
        }
    }

    /**
     * The exchange itself, kept in one place because every job that adds a decision to it still ends
     * here - the decision changes what is said and what is paid, not what is taken.
     */
    protected void handOver(TextPanelAPI text, InteractionDialogAPI dialog,
                            Map<String, MemoryAPI> memoryMap) {

        FishCatch offered = getBestOffered();

        if (!turnIn()) {
            printShort(text);
            return;
        }

        printPaid(text, offered);

        //a job that wants another round says so by setting itself a new ask, and stays where it is.
        //Nothing else has to change: the flag is still set, the option is still there, and the
        //intel entry reads as the same person wanting more, which is what a supply chain looks like
        if (!onDelivered(text)) setCurrentStage(Stage.DONE, dialog, memoryMap);
    }

    /**
     * A chance to ask for something else instead of finishing.
     *
     * @return true if the job goes on, having set itself fresh asks and rewards
     */
    protected boolean onDelivered(TextPanelAPI text) {
        return false;
    }

    /** What the bar shows before anybody has spoken to them. One paragraph, no options. */
    protected abstract void printBlurb(TextPanelAPI text);

    /** What they say when asked, ending on what they want and what they are paying. */
    protected abstract void printOffer(TextPanelAPI text);

    protected void printAccepted(TextPanelAPI text) {
        if (text != null) text.addPara("The arrangement is made.");
    }

    protected void printDeclined(TextPanelAPI text) {
        if (text != null) text.addPara("You leave them to it.");
    }

    /** What they say when you turn up without the whole catch. */
    protected void printReminder(TextPanelAPI text) {
        if (text == null) return;

        text.addPara("You are still owed for %s.", Misc.getTextColor(), Misc.getHighlightColor(), describeAsks());
    }

    /** The rare case: the option was up and the hold emptied between one frame and the next. */
    protected void printShort(TextPanelAPI text) {
        if (text != null) text.addPara("The count comes up short, and the matter is left there.");
    }

    /**
     * @param offered the best specimen that went towards the first ask, for a job that pays on
     *                quality. Null if there was nothing to measure
     */
    protected void printPaid(TextPanelAPI text, FishCatch offered) {
        if (text == null) return;

        text.addPara("The crates change hands, and you are paid %s.", Misc.getTextColor(),
                Misc.getHighlightColor(), describeRewards());
    }

    /**
     * The tokens the shared rows use, on top of the person and stage ones vanilla sets.
     * <p>
     * All three expire the moment the game unpauses, which is what {@code set} does and what is
     * wanted here - they describe a conversation, not a save.
     */
    @Override
    protected void updateInteractionDataImpl() {
        set("$missionId", getMissionId());
        set("$catchreleaseAsk", describeAsks());
        set("$catchreleaseReward", describeRewards());
        set(HAS_FISH_KEY, isSatisfied());
    }

    /** Where the job is being run from, for anything that wants to talk about home. */
    protected MarketAPI getGiverMarket() {
        PersonAPI person = getPerson();

        return person == null ? null : person.getMarket();
    }

    protected static boolean hasPlayerFleet() {
        return Global.getSector() != null && Global.getSector().getPlayerFleet() != null;
    }
}
