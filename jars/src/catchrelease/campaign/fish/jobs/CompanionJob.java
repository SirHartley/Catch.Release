package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

import java.util.List;

public class CompanionJob extends FishJob {

    public static final float BONUS_FRACTION = 0.6f;

    @Override
    protected String getRequiredFactionId() {
        return Factions.HEGEMONY;
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_clientRef", "$catchrelease_clientInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.BUSINESS);

        if (!setUpGiver(createdAt)) return false;

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.minLength = FishJobAsks.lengthFloor(0.35f + genRandom.nextFloat() * 0.35f);

        addAsk(ask);

        if (!setDurationForAsks(createdAt)) return false;
        addRewards(QuestRewards.roll(
                new QuestRewards.Request(asks).random(genRandom)).rewards);

        setUpSpine();

        return true;
    }

    @Override
    protected boolean payBonus(FishCatch offered, List<FishCatch> handedIn) {
        if (!earnsLengthBonus(offered)) return false;

        for (FishReward extra : QuestRewards.roll(new QuestRewards.Request(asks)
                .budgetMult(0.5f).random(random())).rewards) {
            grantReward(extra, handedIn);
            rewards.add(extra);
        }

        return true;
    }

    protected boolean earnsLengthBonus(FishCatch offered) {
        FishSpec spec = offered == null ? null : offered.getSpec();
        if (spec == null || spec.lengthMax <= spec.lengthMin) return false;
        return offered.length >= spec.lengthMin
                + (spec.lengthMax - spec.lengthMin) * BONUS_FRACTION;
    }

    public static void migrateSavedJobs() {
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(CompanionJob.class)) {
            ((CompanionJob) intel).migrateLengthRequirement();
        }
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getCommQueue(CompanionJob.class)) {
            ((CompanionJob) intel).migrateLengthRequirement();
        }
    }

    protected void migrateLengthRequirement() {
        if (asks == null || isEnding() || isEnded()) return;
        for (FishRequirement ask : asks) {
            if (ask == null || ask.minWeight <= 0f) continue;
            if (ask.minLength <= 0f) {
                ask.minLength = FishJobAsks.lengthFloorForLegacyWeight(ask.minWeight);
            }
            ask.minWeight = 0f;
            displayedProgress = null;
        }
    }

    @Override
    protected void updateTokens(MemoryAPI mem) {
        // Unaccepted bar offers are not in the intel manager.
        migrateLengthRequirement();
        super.updateTokens(mem);
    }

    @Override
    protected String getIntelSpecialTerms() {
        return "The contract sets a minimum length. A qualifying specimen in the upper "
                + "two-fifths of its species' length range earns an additional premium.";
    }

    @Override
    protected String getIntelPurpose() {
        return "A discreet private-buyer contract calls for a specimen matching the written "
                + "specification. The client's purpose is outside the brief.";
    }

    @Override
    public String getBaseName() {
        return "A Client's Preference";
    }
}
