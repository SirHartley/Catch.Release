package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
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
 * Stages are deliberately few. A job is asked for, then it is owed, then it is settled - anything
 * with more shape than that declares its own stages on top and uses this one's as the spine.
 */
public abstract class FishJob extends HubMissionWithBarEvent {

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
     * A list rather than one, because several of the jobs this is being built for want more than one
     * thing at once - three kinds for a dish, a pair for a battle - and a job that could only ask
     * for one would have to fake the rest with stages.
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

    protected void addAsk(FishRequirement ask) {
        if (ask != null) asks.add(ask);
    }

    protected void addReward(FishReward reward) {
        if (reward != null) rewards.add(reward);
    }

    public List<FishRequirement> getAsks() {
        return asks;
    }

    public List<FishReward> getRewards() {
        return rewards;
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
     * The hand-over, reached from the job's own rules rows.
     * <p>
     * Vanilla routes anything it does not recognise here, which is where a mod's own verbs live. Two
     * of them: whether the player has the fish, so an option can be greyed or hidden, and the actual
     * exchange.
     */
    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        if ("hasFish".equals(action)) return isSatisfied();

        if ("turnIn".equals(action)) {
            if (!turnIn()) return false;

            setCurrentStage(Stage.DONE, dialog, memoryMap);

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    /**
     * The tokens a job's dialogue can use, on top of the person and stage ones vanilla sets.
     * <p>
     * Written here rather than in each job's rules, so the text of a job is prose with two holes in
     * it rather than a list of everything the job happens to want.
     */
    @Override
    protected void updateInteractionDataImpl() {
        set("$catchreleaseAsk", describeAsks());
        set("$catchreleaseReward", describeRewards());
    }

    /** Where the job is being run from, for anything that wants to talk about home. */
    protected MarketAPI getGiverMarket() {
        return getMapLocation(null) instanceof MarketAPI ? (MarketAPI) getMapLocation(null) : null;
    }

    protected static boolean hasPlayerFleet() {
        return Global.getSector() != null && Global.getSector().getPlayerFleet() != null;
    }
}
