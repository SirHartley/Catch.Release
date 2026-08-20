package catchrelease.campaign.fish.jobs.camp;

import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.FishRewardRoller;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.ui.LabelAPI;
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
 * is the spot, and the catch is only how they know you went. That is why the job has two conditions
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

    /** Intermediate transitions that need their own intel update copy without adding mission stages. */
    protected enum Update {
        CAMP_CLEARED,
        RECEIPT_CAUGHT,
        RECEIPT_LOST
    }

    /** One at a time across the whole family - three of these running at once is three fleets parked in space. */
    public static final String REF_KEY = "$catchrelease_campRef";
    public static final String IN_PROGRESS_KEY = "$catchrelease_campInProgress";

    public static final float DAYS = 70f;

    /** How far out to look for somewhere with water in it, in light years. */
    public static final float MAX_LY = 14f;

    /** Which of the three is out there. Fixed per subclass, since each is its own bar event. */
    protected abstract CampType getType();

    protected CampSize size;
    /** Retained only so older saves deserialize; receipt asks no longer name a species. */
    protected String speciesId;
    protected String systemName;

    protected CampaignFleetAPI camper;
    protected SectorEntityToken pond;

    /** Set once the water is free again, whatever freed it. */
    protected boolean cleared = false;

    /** Mirrors whether a qualifying receipt is currently in cargo; drives the marker and intel. */
    protected boolean receiptAboard = false;

    /** Campaign timestamp when the player accepted; pre-existing cargo cannot serve as proof. */
    protected long acceptedAt = 0L;

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

        size = CampSize.roll(genRandom);
        speciesId = null;
        systemName = system.getName();

        days = DAYS;

        setReceiptAsk();
        addRewards(FishRewardRoller.roll(genRandom, size.value, true));

        setUpSpine();

        return true;
    }

    /**
     * Creates the physical camp only once the player takes the job.
     * <p>
     * Bar-event offers are built speculatively and {@code abort()} does not call
     * {@link #notifyEnded()}. Spawning during {@link #create(MarketAPI, boolean)} therefore left a
     * fleet behind whenever an unaccepted offer was rerolled. Keeping world mutation here makes
     * one accepted camp produce one camper, one pond claim, and one planted specimen.
     */
    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.acceptImpl(dialog, memoryMap);

        acceptedAt = Global.getSector().getClock().getTimestamp();

        camper = CampedSpot.spawn(getType(), size, pond, random());
        if (camper == null) return;

        setReceiptAsk();
        CampedSpot.setPondBlocked(pond, true);
        QuestPond.claim(pond, REF_KEY);

        //the map points at whoever is sitting on the water, which is where the player has to go
        //first regardless of how they intend to deal with it
        makeImportant(camper, null, Stage.WANTED);
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
     * One landed specimen of any type, but only if its catch record names this exact rupture. Also
     * repairs active saves whose receipt still carries the old fish-only or named-species filter.
     */
    protected void setReceiptAsk() {
        if (pond == null || pond.getId() == null) return;

        //An accepted old save cannot reveal its historical acceptance instant. Starting its proof
        //window now is the only repair that cannot bless a fish which predates the agreement.
        if (acceptedAt <= 0L && currentStage != null) {
            acceptedAt = Global.getSector().getClock().getTimestamp();
        }

        if (asks.size() == 1) {
            FishRequirement current = asks.get(0);
            if (current != null && current.speciesId == null && current.tag == null
                    && pond.getId().equals(current.sourceId)
                    && current.minCaughtAt == acceptedAt) {
                return;
            }
        }

        FishRequirement receipt = new FishRequirement();
        receipt.count = 1;
        receipt.sourceId = pond.getId();
        receipt.minCaughtAt = acceptedAt;

        asks.clear();
        asks.add(receipt);
        speciesId = null;
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
        setReceiptAsk();
        super.advanceImpl(amount);

        if (isEnding() || isEnded()) return;

        setReceiptAboard(super.isSatisfied());

        if (cleared) return;

        if (!CampedSpot.isGone(camper)) {
            CampedSpot.updateWarningPursuit(camper, pond);
            CampedSpot.allowPlayerToLeave(camper, pond);
            //Refresh pond-side memory for old saves too, clearing their obsolete named species.
            CampedSpot.setPondBlocked(pond, true);
            return;
        }

        CampedSpot.setPondBlocked(pond, false);
        cleared = true;

        //The fleet is no longer the objective even when it is still alive and flying away.
        //BaseHubMission's stage-bound marker otherwise follows it until the whole job ends.
        if (camper != null) {
            Misc.makeUnimportant(camper.getMemoryWithoutUpdate(), getReason());
        }
        camper = null;

        updateInteractionDataImpl();
        FishIntelNotifications.update(this, Update.CAMP_CLEARED);
    }

    /**
     * Moves the second objective with the actual specimen rather than merely remembering that one
     * existed once. Selling or losing it before hand-in therefore puts the marked water back.
     */
    protected void setReceiptAboard(boolean aboard) {
        if (receiptAboard == aboard) return;

        receiptAboard = aboard;
        if (aboard) {
            QuestPond.release(pond, REF_KEY);
        } else {
            QuestPond.claim(pond, REF_KEY);
        }

        updateInteractionDataImpl();
        FishIntelNotifications.update(this,
                aboard ? Update.RECEIPT_CAUGHT : Update.RECEIPT_LOST);
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        if (mem == null) return;

        token(mem, "$catchreleaseCampWho", getType().token);
        token(mem, "$catchreleaseCampMany", size == null ? "" : size.describe);
        token(mem, "$catchreleaseCampManyCap", size == null ? "" : Misc.ucFirst(size.describe));
        token(mem, "$catchreleaseCampWhere", systemName == null ? "" : systemName);
        token(mem, "$catchreleaseCampCleared", cleared);
        token(mem, "$catchreleaseCampReceiptAboard", receiptAboard);
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

        CampedSpot.setPondBlocked(pond, false);
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

    /** This receipt belongs to one named rupture; habitat search would point elsewhere. */
    @Override
    protected SectorEntityToken getFishRequestRouteTarget() {
        return getMapLocation(null);
    }

    /** Transition messages say what changed and what remains instead of repeating "1 fish". */
    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        if (getListInfoParam() == Update.CAMP_CLEARED) {
            info.addPara(receiptAboard
                            ? "The camp has left. Return to the fisher for payment."
                            : "The camp has left. Catch any fish from the marked rupture.",
                    getBulletColorForMode(mode), 0f);
            return;
        }
        if (getListInfoParam() == Update.RECEIPT_CAUGHT) {
            info.addPara(cleared
                            ? "Proof secured. Return to the fisher for payment."
                            : "Proof secured. The camp still needs to be cleared.",
                    getBulletColorForMode(mode), 0f);
            return;
        }
        if (getListInfoParam() == Update.RECEIPT_LOST) {
            info.addPara("The proof is no longer aboard. Catch another fish from the marked rupture.",
                    getBulletColorForMode(mode), 0f);
            return;
        }

        super.addBulletPoints(info, mode);
    }

    /** Compact objective text follows the two independent conditions in either order. */
    @Override
    public String getNextStepText() {
        if (isEnding()) return null;

        if (!cleared) {
            return receiptAboard
                    ? "Clear the camp at the marked rupture; the proof is already aboard."
                    : "Clear the camp at the marked rupture, then catch any fish there as proof.";
        }

        if (!receiptAboard) return "Catch any fish from the marked rupture as proof.";

        MarketAPI market = getGiverMarket();
        if (getPerson() == null || market == null) return "Return to the fisher for payment.";

        return "Return to " + getPerson().getNameString() + " on " + market.getName()
                + " for payment.";
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

            if (receiptAboard) {
                info.addPara("The proof is already aboard. What remains is clearing the camp.", pad);
            } else {
                info.addPara("After the camp is gone, catch any fish from this exact rupture and"
                        + " bring it back as proof that the spot can be worked again.", pad);
            }
        } else if (receiptAboard) {
            info.addPara("The rupture in %s is clear and the proof is aboard. Return to the fisher"
                            + " for payment.", pad, Misc.getHighlightColor(), systemName);
        } else {
            info.addPara("The rupture in %s is clear. Catch any fish from this exact rupture and"
                            + " bring it back as proof.", pad,
                    Misc.getHighlightColor(), systemName);
        }

        String reward = describeRewards();
        LabelAPI terms = info.addPara("The promised payment is %s.", pad,
                Misc.getHighlightColor(), reward);
        terms.setHighlight(reward);

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
