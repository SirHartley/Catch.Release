package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
 * Nothing here says anything. Every word a job speaks lives in data/campaign/rules.csv, which is
 * where dialogue belongs: it can be read, edited and translated without a compiler, and a row is
 * the unit the engine already scores and picks between, so a job that wants to say something
 * different when a wager comes off writes a second row rather than a second branch.
 * <p>
 * What Java owns is the part a sheet cannot do - counting the hold, spending it, rolling the
 * payment, settling a bet. The two meet at a handful of memory tokens: this side writes what
 * happened, and the rows read it and do the talking.
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

    /**
     * What the rows say the job wants and pays, in words.
     * <p>
     * Written out rather than described in the sheet, because the ask is assembled from a
     * requirement that can say six different things at once and no row wants to spell that out.
     */
    public static final String ASK_KEY = "$catchreleaseAsk";
    public static final String ASK_CAP_KEY = "$catchreleaseAskCap";
    public static final String REWARD_KEY = "$catchreleaseReward";
    public static final String REWARD_CAP_KEY = "$catchreleaseRewardCap";

    /** How the hand-over went, for the rows that describe it. */
    public static final String PAID_KEY = "$catchreleasePaid";
    public static final String BONUS_KEY = "$catchreleaseBonus";
    public static final String MORE_KEY = "$catchreleaseMore";

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
        markDeliverable();
    }

    /**
     * Puts the flag that raises the hand-over options wherever the player will be standing when
     * they bring the fish.
     * <p>
     * A person, for a job given across a counter. Not every job has one: a giver that is a hull in
     * space has no person to flag and no market to flag them at, and asking vanilla to mark a null
     * important throws rather than doing nothing. Those override this and flag what they do have.
     */
    protected void markDeliverable() {
        PersonAPI person = getPerson();
        if (person == null) return;

        makeImportant(person, getDeliverFlag(), Stage.WANTED);
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
     * Whose people ask for this, or null for anybody's.
     * <p>
     * A method rather than the field, because the question is put before {@link #create} has run -
     * a job that assigned its own faction on the way to being built would be answering with whatever
     * the last one happened to leave behind. Overriding this is the only way a faction gate works.
     */
    protected String getRequiredFactionId() {
        return factionId;
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

        String required = getRequiredFactionId();

        return required == null || required.equals(market.getFactionId());
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
     * The mechanics the sheet asks for by name. None of them says anything.
     * <p>
     * What a job says lives in rules.csv, which is where dialogue belongs - it can be read, edited
     * and translated without a compiler, and a row is the unit the engine already scores and picks
     * between. What Java owns is the part a sheet cannot do: counting the hold, spending it, rolling
     * the payment, settling a wager. The two meet at a handful of memory tokens the rows read.
     * <p>
     * Note that returning false here is not a way to say no: vanilla throws on an unhandled action
     * rather than treating it as a failed condition, so every verb answers true and reports what
     * happened in a flag instead.
     */
    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        if ("turnIn".equals(action)) {
            handOver(dialog, memoryMap);

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    /**
     * The exchange itself, kept in one place because every job that adds a decision to it still ends
     * here - the decision changes what is said and what is paid, not what is taken.
     * <p>
     * Every branch the prose needs to know about comes back out as a flag, so a row can ask whether
     * the payment happened, whether an extra was earned, and whether the job is asking again.
     */
    protected void handOver(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI mem = memoryMap == null ? null : memoryMap.get(MemKeys.LOCAL);

        //asked before anything is spent or paid, since a job that settles a wager afterwards has
        //already handed over the stake it was wagering
        if (!isSatisfied()) {
            token(mem, PAID_KEY, false);
            return;
        }

        FishCatch offered = getBestOffered();

        beforePayment(offered, mem);

        if (!turnIn()) {
            token(mem, PAID_KEY, false);
            return;
        }

        token(mem, PAID_KEY, true);
        token(mem, BONUS_KEY, payBonus(offered));

        //a job that wants another round says so by setting itself a new ask, and stays where it is.
        //Nothing else has to change: the flag is still set, the option is still there, and the
        //intel entry reads as the same person wanting more, which is what a supply chain looks like
        boolean more = onDelivered();

        token(mem, MORE_KEY, more);

        //re-read after a new round has been set, so the row describing what is wanted next describes
        //what is wanted next rather than what was just handed over
        updateTokens(mem);

        if (!more) setCurrentStage(Stage.DONE, dialog, memoryMap);
    }

    /**
     * A last chance to change what is about to be paid.
     * <p>
     * The hand-over is settled and cannot now fail, and nothing has been granted yet - which is the
     * only moment a job can decide that the payment is double, or nothing at all.
     *
     * @param offered the best specimen going towards the first ask, or null
     */
    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
    }

    /**
     * An extra for a specimen worth remarking on, granted on top of what was agreed.
     *
     * @return whether anything extra was paid, which is what the prose branches on
     */
    protected boolean payBonus(FishCatch offered) {
        return false;
    }

    /**
     * A chance to ask for something else instead of finishing.
     *
     * @return true if the job goes on, having set itself fresh asks and rewards
     */
    protected boolean onDelivered() {
        return false;
    }

    /**
     * The tokens the rows read, on top of the person and stage ones vanilla sets.
     * <p>
     * All of them expire the moment the game unpauses, which is what a conversation is. Capitalised
     * twins are set alongside the plain ones because the engine does not capitalise a token for you
     * and a sentence has to be able to start with one.
     */
    protected void updateTokens(MemoryAPI mem) {
        if (mem == null) return;

        token(mem, "$missionId", getMissionId());

        String ask = describeAsks();
        String reward = describeRewards();

        token(mem, ASK_KEY, ask);
        token(mem, ASK_CAP_KEY, Misc.ucFirst(ask));
        token(mem, REWARD_KEY, reward);
        token(mem, REWARD_CAP_KEY, Misc.ucFirst(reward));
        token(mem, HAS_FISH_KEY, isSatisfied());

        setJobTokens(mem);
    }

    /** Anything a particular job's rows need naming: a dish, a species, two men in a bar. */
    protected void setJobTokens(MemoryAPI mem) {
    }

    /** Written straight to the conversation's memory, which is the only place a row will look. */
    protected static void token(MemoryAPI mem, String key, Object value) {
        if (mem != null) mem.set(key, value, 0f);
    }

    @Override
    protected void updateInteractionDataImpl() {
        updateTokens(interactionMemory);
    }

    /**
     * A source of chance that still works after the job has been accepted.
     * <p>
     * The mission's own seeded random is the right one while the job is being built, since that is
     * what makes a bar say the same thing twice. It is the wrong one for a coin flipped at the
     * hand-over, which should be flipped then and not decided when the job was written - and it is
     * not guaranteed to have survived the trip through a save, so this never returns null.
     */
    protected Random random() {
        return genRandom == null ? new Random() : genRandom;
    }

    /** Where the job is being run from, for anything that wants to talk about home. */
    protected MarketAPI getGiverMarket() {
        PersonAPI person = getPerson();

        return person == null ? null : person.getMarket();
    }

    protected static boolean hasPlayerFleet() {
        return Global.getSector() != null && Global.getSector().getPlayerFleet() != null;
    }

    //---- intel ----------------------------------------------------------------------------------

    /**
     * A job is a job rather than a mission, which is the word vanilla puts in the abandon prompt and
     * the end-stage lines it writes for us.
     */
    @Override
    protected String getMissionTypeNoun() {
        return "job";
    }

    /**
     * The entry while the fish are owed.
     * <p>
     * Only this stage is written here: the base class already says the right thing about a job that
     * was finished, failed or abandoned, and repeating it would be two entries disagreeing about a
     * job that is over.
     */
    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();
        Color text = getBulletColorForMode(ListInfoMode.IN_DESC);

        PersonAPI person = getPerson();
        MarketAPI market = getGiverMarket();

        if (person != null && market != null) {
            info.addPara("%s is waiting on %s for the catch.", opad, highlight,
                    person.getNameString(), market.getName());
        }

        info.addPara("What is wanted:", opad);

        bullet(info);
        for (FishRequirement ask : asks) {
            //highlighted after the fact rather than through a format string, since the ask writes
            //its own sentence and there is no %s in it to hang the count on
            LabelAPI line = info.addPara(Misc.ucFirst(ask.describe()), text, 0f);
            line.setHighlightColor(highlight);
            line.setHighlight(String.valueOf(ask.count));
        }

        //the clock belongs with the ask rather than with the payment - it is a fact about how long
        //there is to catch them, not about what is being handed over
        if (days > 0f) {
            addDays(info, "remaining", Math.max(0f, days - getElapsedInCurrentStage()), text);
        }
        unindent(info);

        info.addPara("On delivery:", opad);

        bullet(info);
        for (FishReward reward : rewards) {
            info.addPara(Misc.ucFirst(reward.describe()), text, 0f);
        }
        unindent(info);
    }

    /** The compact form, for the list down the side of the intel screen. */
    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color highlight = Misc.getHighlightColor();
        Color text = getBulletColorForMode(mode);

        float pad = mode == ListInfoMode.IN_DESC ? 10f : 0f;

        LabelAPI line = info.addPara(Misc.ucFirst(describeAsks()), text, pad);
        line.setHighlightColor(highlight);

        //an empty highlight would ask the label to find nothing, which is not the same as finding
        //nothing to highlight
        if (!asks.isEmpty()) line.setHighlight(String.valueOf(asks.get(0).count));

        if (days > 0f && !isEnding()) {
            addDays(info, "remaining", Math.max(0f, days - getElapsedInCurrentStage()), text, 0f);
        }
    }

    /**
     * The one line under the entry's title, which is the only part most players read.
     * <p>
     * Deliberately not conditional on whether the hold already covers the ask. Answering that means
     * decoding every specimen in every stack, and this is asked while a list is being drawn rather
     * than while somebody is waiting for an answer - so it says both halves of the errand and stays
     * cheap.
     */
    @Override
    public String getNextStepText() {
        if (isEnding()) return null;

        PersonAPI person = getPerson();
        MarketAPI market = getGiverMarket();

        if (person == null || market == null) return "Catch " + describeAsks() + ".";

        return "Catch " + describeAsks() + ", then find " + person.getNameString()
                + " on " + market.getName() + ".";
    }
}
