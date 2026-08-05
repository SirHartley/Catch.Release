package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.Misc;

/**
 * People who want one particular species and will not say what for.
 * <p>
 * The only job that names its fish. Every other buyer describes a shape - three of a kind, one over
 * forty kilograms, something barely holding together - because every other buyer has a use and the
 * use is what the description is made of. These have a use too. They simply are not going to tell
 * you it, and what is left when you take the reason out of a request is a name.
 * <p>
 * No credits. Not out of principle, as far as anybody can tell - they seem to regard money as one
 * more thing they have, rather than as the thing you would want.
 */
public class CultJob extends FishJob {

    public static final int VALUE = 2800;

    public static final float DAYS = 55f;

    /** The species, which is the entire brief. */
    protected String speciesId;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_cultRef", "$catchrelease_cultInProgress")) {
            return false;
        }

        setGiverRank(Ranks.BROTHER);
        setGiverVoice(Voices.FAITHFUL);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        speciesId = FishJobAsks.rollSpecies(genRandom, FishRarity.UNCOMMON);
        if (speciesId == null) return false;

        FishRequirement ask = new FishRequirement();
        ask.speciesId = speciesId;
        ask.count = 1;

        addAsk(ask);

        //no money, which is the one thing they are consistent about
        addRewards(FishRewardRoller.roll(genRandom, VALUE, false));

        setUpSpine();

        return true;
    }

    /** The name they use, which is the table's name, said without elaboration. */
    protected String getSpeciesName() {
        FishSpec spec = FishSpecLoader.getFishSpec(speciesId);

        return spec == null ? speciesId : spec.getDisplayName();
    }

    @Override
    protected void printBlurb(TextPanelAPI text) {
        text.addPara("Three people in matching, unremarkable coats are sharing one drink between "
                + "them and have not touched it. They are waiting, and they are not impatient.");
    }

    @Override
    protected void printOffer(TextPanelAPI text) {
        text.addPara("One of them says a name. Not yours - the fish's.");

        text.addPara("\"%s,\" they repeat, when you do not answer immediately. \"One. Whole.\"",
                Misc.getTextColor(), Misc.getHighlightColor(), getSpeciesName());

        text.addPara("You ask what for. There is a pause of exactly the length of somebody deciding "
                + "not to be offended, and then the same sentence again, with the same emphasis, as "
                + "though the problem had been the volume.");

        text.addPara("\"%s,\" the second one adds, and produces it, and puts it on the table, and "
                        + "leaves it there while you think about it.", Misc.getTextColor(),
                Misc.getHighlightColor(), Misc.ucFirst(describeRewards()));
    }

    @Override
    protected void printAccepted(TextPanelAPI text) {
        text.addPara("None of them thanks you. All three of them nod at the same time, which is "
                + "worse.");
    }

    @Override
    protected void printDeclined(TextPanelAPI text) {
        text.addPara("They do not seem to mind. You get the strong impression they will be here "
                + "when you come back.");
    }

    @Override
    protected void printReminder(TextPanelAPI text) {
        text.addPara("\"%s,\" one of them says. That is the whole of the conversation.",
                Misc.getTextColor(), Misc.getHighlightColor(), getSpeciesName());
    }

    @Override
    public String getBaseName() {
        return "One, Whole";
    }
}
