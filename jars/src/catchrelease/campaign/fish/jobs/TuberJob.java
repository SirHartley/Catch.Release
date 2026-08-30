package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

import java.util.ArrayList;
import java.util.List;

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

        if (!setDurationForAsks(createdAt)) return false;
        addRewards(QuestRewards.roll(
                new QuestRewards.Request(asks).random(genRandom)).rewards);

        setUpSpine();

        return true;
    }

    @Override
    protected boolean onDelivered() {
        if (getRound() > 1) return false;

        List<FishReward> previousRewards = new ArrayList<>(rewards);
        asks.clear();
        rewards.clear();

        FishRequirement grim = new FishRequirement();
        grim.count = 1;
        grim.lowCoherence = true;

        addAsk(grim);
        addRewards(QuestRewards.rollLaterStage(new QuestRewards.Request(asks)
                .budgetMult(1.6f).random(random()), previousRewards).rewards);

        return true;
    }

    @Override
    protected String getIntelSpecialTerms() {
        if (getRound() < 1) {
            return "The first shoot needs a Fine specimen of the requested rarity. After that, "
                    + "the second clip calls for a low-coherence catch.";
        }

        return "The second shoot is the last. It needs the promised low-coherence catch.";
    }

    @Override
    protected String getIntelPurpose() {
        return "A TriTuber has commissioned catches for two short clips, each planned around a "
                + "specimen reveal.";
    }

    @Override
    public String getBaseName() {
        return "Content";
    }
}
