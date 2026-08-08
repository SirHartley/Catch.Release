package catchrelease.campaign.fish.shop;

import java.util.List;

/**
 * Anything in the intel log that is waiting on a specimen.
 * <p>
 * The point of it is the mark. {@link ShopMarks#isWanted} is what hangs the quest-yellow dot on a
 * species - in the hold, in the codex, on the map, in the route planner - and what it means is
 * "something is asking for this, do not sell it". That question used to be asked of
 * {@code FishJob} alone, which is one of three things in the mod that asks for a fish: the
 * introduction's ladder and the trade's chart requests are notes rather than bar jobs, and neither
 * of them is a job, so a specimen either of them had sent the player after wore no mark at all.
 * <p>
 * An interface rather than a common base class because the three have nothing else in common - a
 * job is a vanilla mission with a giver and a clock, the other two are plain notes - and because
 * the next thing that wants a fish should be able to say so by implementing two methods.
 */
public interface FishAsker {

    /**
     * What is wanted, as the same requirements the shop prices in. Empty is a legitimate answer:
     * an errand that will take anything at all has nothing to single out.
     */
    List<FishRequirement> getAsks();

    /** Who is asking, for the "Required by" line. Short and stable - it names the errand, not its
     *  current rung. */
    String getAskerName();
}
