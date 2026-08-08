package catchrelease.campaign.fish.jobs.camp;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.FishRewardRoller;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.Map;

/**
 * Somebody's fishing spot with somebody else parked on it.
 * <p>
 * The fisher at the bar is not a broker and is not offering work in the usual sense - they had one
 * rupture that reliably produced, somebody is sitting on it, and they cannot go back. What they want
 * is the spot, and the fish is only how they know you went. That is why the job has two conditions
 * rather than one: clearing the camp is the job, and the specimen is the receipt.
 * <p>
 * Three variants, which are three bar events and three thin subclasses, because the difference is
 * entirely in who is out there - see {@link CampType}. The size is rolled per job and is the only
 * warning the player gets before flying out, so the fisher's estimate is honest.
 * <p>
 * Every route through is legitimate. Kill them, buy them off, or spend a point convincing them the
 * water is not worth it; the job asks {@link CampedSpot#isGone} and nothing more specific, so it
 * never has an opinion about which one the player picked.
 */
public abstract class CampedSpotJob extends FishJob {

    /** One at a time across the whole family - three of these running at once is three fleets parked in space. */
    public static final String REF_KEY = "$catchrelease_campRef";
    public static final String IN_PROGRESS_KEY = "$catchrelease_campInProgress";

    public static final float DAYS = 70f;

    /** How far out to look for somewhere with water in it, in light years. */
    public static final float MAX_LY = 14f;

    /** Which of the three is out there. Fixed per subclass, since each is its own bar event. */
    protected abstract CampType getType();

    protected CampSize size;
    protected String speciesId;
    protected String systemName;

    protected CampaignFleetAPI camper;
    protected SectorEntityToken pond;

    /** Set once the water is free again, whatever freed it. */
    protected boolean cleared = false;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference(REF_KEY, IN_PROGRESS_KEY)) return false;

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.SPACER);

        if (!setUpGiver(createdAt)) return false;

        StarSystemAPI system = pickSystem(createdAt);
        if (system == null) return false;

        pond = QuestPond.findFreePond(system);
        if (pond == null) return false;

        FishSpec spec = pickSpecies();
        if (spec == null) return false;

        size = CampSize.roll(genRandom);
        speciesId = spec.id;
        systemName = system.getName();

        //the water is claimed and stocked before anybody is put on top of it, so a job that fails
        //to raise its camper leaves nothing planted
        camper = CampedSpot.spawn(getType(), size, pond, genRandom);
        if (camper == null) return false;

        QuestPond.claim(pond, REF_KEY);
        QuestPond.placeMote(pond, speciesId);

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.speciesId = speciesId;

        addAsk(ask);
        addRewards(FishRewardRoller.roll(genRandom, size.value, true));

        setUpSpine();

        //the map points at whoever is sitting on the water, which is where the player has to go
        //first regardless of how they intend to deal with it
        makeImportant(camper, null, Stage.WANTED);

        return true;
    }

    /**
     * Somewhere with water in it, not too far, and not somewhere the giver would have to fly past a
     * war to reach.
     * <p>
     * A free rupture is the hard requirement and the reason this can fail: the job is about one
     * specific pond, so there is no version of it that works in a system without one.
     */
    protected StarSystemAPI pickSystem(MarketAPI from) {
        if (from == null || from.getPrimaryEntity() == null) return null;

        WeightedRandomPicker<StarSystemAPI> picker = new WeightedRandomPicker<>(genRandom);

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (QuestPond.findFreePond(system) == null) continue;

            float ly = Misc.getDistanceLY(from.getLocationInHyperspace(),
                    system.getLocation());
            if (ly > MAX_LY) continue;

            //nearer is likelier, so a fisher describes somewhere they could plausibly have been
            //working out of rather than somewhere across the sector
            picker.add(system, Math.max(1f, MAX_LY - ly));
        }

        return picker.pick();
    }

    /**
     * Something worth the trip.
     * <p>
     * Uncommon and up, because the specimen is a receipt rather than a reward and a receipt that
     * could have been picked up anywhere proves nothing. The planted mote is what makes it findable
     * at all; the rarity is what makes it credible that this pond was worth camping.
     */
    protected FishSpec pickSpecies() {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>(genRandom);

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (spec.rarity.ordinal() < 1) continue;

            picker.add(spec, 1f);
        }

        return picker.pick();
    }

    //---------------------------------------------------------------- the two conditions

    /** The catch alone is not enough: the spot has to be free, which is what the fisher actually wants. */
    @Override
    public boolean isSatisfied() {
        return cleared && super.isSatisfied();
    }

    /**
     * Notices the water going quiet.
     * <p>
     * Polled rather than reported, because the four ways it can happen do not share a hook - a fleet
     * bought off cuts the link, a fleet talked off does the same, a fleet killed never gets to say
     * anything, and a fleet that quietly went away was never in a conversation at all. One question
     * asked on the mission's own tick covers all four.
     */
    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        if (cleared || !CampedSpot.isGone(camper)) return;

        cleared = true;
        camper = null;

        updateInteractionDataImpl();
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        if (mem == null) return;

        token(mem, "$catchreleaseCampWho", getType().token);
        token(mem, "$catchreleaseCampMany", size == null ? "" : size.describe);
        token(mem, "$catchreleaseCampManyCap", size == null ? "" : Misc.ucFirst(size.describe));
        token(mem, "$catchreleaseCampWhere", systemName == null ? "" : systemName);
        token(mem, "$catchreleaseCampCleared", cleared);
    }

    /**
     * Whatever is left of the camp goes with the job.
     * <p>
     * On any ending rather than only on success: an abandoned job that left a raiding pack orbiting
     * a rupture for the rest of the campaign would be a fleet nobody can explain, and the pond has
     * to come back into circulation either way.
     */
    @Override
    protected void notifyEnded() {
        super.notifyEnded();

        CampedSpot.despawn(camper);
        camper = null;

        QuestPond.release(pond, REF_KEY);
    }

    //---------------------------------------------------------------- the log entry

    /** Points at the camp while there is one, and at the water once there is not. */
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (camper != null && !camper.isExpired()) return camper;
        if (pond != null && !pond.isExpired()) return pond;

        return super.getMapLocation(map);
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float pad = 10f;

        if (!cleared) {
            info.addPara("There is %s of %s sitting on a rupture in %s, and the fisher who worked"
                            + " it cannot go back while they are there.", pad,
                    Misc.getHighlightColor(),
                    size == null ? "something" : size.describe, getType().token, systemName);

            info.addPara("Kill them, pay them off, or talk them out of it - nobody has asked for it"
                    + " to be done a particular way.", pad);
        } else {
            info.addPara("The rupture in %s is clear. What is left is the proof.", pad,
                    Misc.getHighlightColor(), systemName);
        }

        info.addPara("They want %s out of that rupture, and are offering %s.", pad,
                Misc.getHighlightColor(), describeAsks(), describeRewards());

        //the same helper as the list row's clock, so the two surfaces say it the same way
        if (days > 0f) {
            addDays(info, "left before they give the spot up for good.", getDaysLeft(),
                    getBulletColorForMode(ListInfoMode.IN_DESC), pad);
        }
    }

    @Override
    public String getBaseName() {
        return "A Spot Worth Keeping";
    }
}
