package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.Misc;

/**
 * A man with a project and no supply chain.
 * <p>
 * The multi-stage one, and the reason a job may ask again instead of ending. He does not want fish;
 * he wants proof that fish can be got, and having been shown once he wants it at a scale that would
 * mean it was worth proving. So the same job runs three rounds, each larger than the last, paid at
 * every step - which is what a supply chain looks like from the supplier's end, and the shape the
 * later follow-ups will hang off when he has something to be the supplier for.
 */
public class StartupJob extends FishJob {

    public static final int VALUE_PER_FISH = 800;

    /** How many rounds he asks for before he stops asking. Three is a pilot, a scale-up, and a run. */
    public static final int ROUNDS = 3;

    /** How much bigger each round is than the last. */
    public static final float GROWTH = 2f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_startupRef", "$catchrelease_startupInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.BUSINESS);

        if (!setUpGiver(createdAt)) return false;

        //no clock. A man building something is not going to give up on it in forty days, and a time
        //limit on a three-round job is a time limit on the third round
        days = 0f;

        setAsk(2 + genRandom.nextInt(2));

        setUpSpine();

        return true;
    }

    /** Replaces the ask and the payment with this round's, which is how the job grows. */
    protected void setAsk(int count) {
        asks.clear();
        rewards.clear();

        FishRequirement ask = new FishRequirement();
        ask.count = count;
        ask.sameSpecies = true;

        addAsk(ask);

        //he pays a little over the odds and knows it. Somebody proving a supply exists is buying the
        //proof, not the fish, and the proof is worth more to him than the crates are
        addRewards(FishRewardRoller.roll(genRandom, (int) (VALUE_PER_FISH * count * 1.2f), true));
    }

    @Override
    protected boolean onDelivered(TextPanelAPI text) {
        if (getRound() >= ROUNDS) {
            text.addPara("\"That is a supply chain,\" he says, to himself more than to you. "
                    + "\"That is a supply chain and I have one.\"");

            return false;
        }

        int grown = (int) (asks.get(0).count * GROWTH) + genRandom.nextInt(3);
        setAsk(grown);

        text.addPara("He is already scrolling. \"Right. Right. Now the same again, at volume, "
                + "because the figure I showed them was not the figure you just delivered.\"");

        text.addPara("\"%s. Same terms, better terms, whatever you want - I have investors now.\"",
                Misc.getTextColor(), Misc.getHighlightColor(), Misc.ucFirst(describeAsks()));

        return true;
    }

    @Override
    protected void printBlurb(TextPanelAPI text) {
        text.addPara("A young man with a very good jacket and a very cheap drink is explaining "
                + "something to two people who are not listening, and to a third who left.");
    }

    @Override
    protected void printOffer(TextPanelAPI text) {
        text.addPara("\"You fish,\" he says. It is not a question and he does not wait. \"Everyone "
                + "in this sector eats reconstituted protein because nobody has solved logistics. "
                + "I have solved logistics. What I do not have is a demonstrated inbound.\"");

        text.addPara("\"%s. That is all. I need to be able to say, truthfully, that it arrived.\"",
                Misc.getTextColor(), Misc.getHighlightColor(), Misc.ucFirst(describeAsks()));

        text.addPara("\"%s for the first run. There will be more runs.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), Misc.ucFirst(describeRewards()));
    }

    @Override
    protected void printAccepted(TextPanelAPI text) {
        text.addPara("\"Excellent. I will need an invoice. Do you do invoices?\"");
    }

    @Override
    protected void printDeclined(TextPanelAPI text) {
        text.addPara("\"That is fine. That is fine. I have other conversations.\" He does not.");
    }

    @Override
    public String getBaseName() {
        return "Demonstrated Inbound";
    }
}
