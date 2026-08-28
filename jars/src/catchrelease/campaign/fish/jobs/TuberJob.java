package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

public class TuberJob extends FishJob {

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_tuberRef", "$catchrelease_tuberInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.SPACER);

        if (!setUpGiver(createdAt)) return false;

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.minRarity = genRandom.nextFloat() > 0.5f ? FishRarity.RARE : FishRarity.UNCOMMON;
        ask.minGrade = FishGrade.FINE;

        addAsk(ask);

        setDurationForAsks(createdAt);
        addRewards(QuestRewards.roll(
                new QuestRewards.Request(asks).random(genRandom)).rewards);

        setUpSpine();

        return true;
    }

    @Override
    protected boolean onDelivered() {
        if (getRound() > 1) return false;

        asks.clear();
        rewards.clear();

        FishRequirement grim = new FishRequirement();
        grim.count = 1;
        grim.lowCoherence = true;

        addAsk(grim);
        // the reveal shoot pays over its score - the second clip is the whole point
        addRewards(QuestRewards.roll(new QuestRewards.Request(asks)
                .budgetMult(1.6f).random(random())).rewards);

        return true;
    }

    @Override
    protected String getIntelSpecialTerms() {
        if (getRound() < 1) {
            return "This is the first of two shoots. The first calls for a Fine specimen of the "
                    + "requested rarity; the second will call for a low-coherence catch.";
        }

        return "This is the final shoot. The remaining request is the promised low-coherence "
                + "catch, and completing it closes out the arrangement.";
    }

    @Override
    protected String getIntelPurpose() {
        return "A TriTuber has commissioned catches for a pair of short clips. Each is meant to "
                + "give the feed a strong opening and a reveal.";
    }

    @Override
    public String getBaseName() {
        return "Content";
    }
}
