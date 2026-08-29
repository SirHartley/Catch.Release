package catchrelease.campaign.fish.jobs.camp;

import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.DemandScore;
import catchrelease.campaign.fish.jobs.QuestDuration;
import catchrelease.campaign.fish.jobs.QuestRewards;
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

import java.util.List;
import java.util.Map;

public abstract class CampedSpotJob extends FishJob {

    protected enum Update {

        CAMP_CLEARED,
        RECEIPT_CAUGHT,
        RECEIPT_LOST
    }

    public static final String REF_KEY = "$catchrelease_campRef";
    public static final String IN_PROGRESS_KEY = "$catchrelease_campInProgress";
    public static final float MAX_LY = 14f;

    protected CampSize size;
    protected String speciesId;
    protected String systemName;
    protected CampaignFleetAPI camper;
    protected SectorEntityToken pond;
    protected boolean cleared = false;
    protected boolean receiptAboard = false;
    protected long acceptedAt = 0L;

    protected abstract CampType getType();

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

        setReceiptAsk();

        // Add the camp's value to the receipt ask when scoring rewards.
        // Allow time for both the confrontation and the catch.
        float score = size.value / QuestRewards.CREDITS_PER_POINT + DemandScore.of(asks);
        addRewards(QuestRewards.roll(new QuestRewards.Request(asks)
                .score(score).random(genRandom)).rewards);

        float ly = Misc.getDistanceLY(createdAt.getLocationInHyperspace(),
                system.getLocation());
        days = QuestDuration.forDays(QuestDuration.WORKING_DAYS * 2f
                + ly * QuestDuration.DAYS_PER_LY * 2f).days;

        setUpSpine();

        return true;
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.acceptImpl(dialog, memoryMap);

        acceptedAt = Global.getSector().getClock().getTimestamp();

        camper = CampedSpot.spawn(getType(), size, pond, random());
        if (camper == null) return;

        setReceiptAsk();
        CampedSpot.setPondBlocked(pond, true);
        QuestPond.claim(pond, REF_KEY);

        // the map points at whoever is sitting on the water, which is where the player has to go first regardless of how they intend to deal with it
        makeImportant(camper, null, Stage.WANTED);
    }

    protected StarSystemAPI pickSystem(MarketAPI from) {
        if (from == null || from.getPrimaryEntity() == null) return null;

        WeightedRandomPicker<StarSystemAPI> picker = new WeightedRandomPicker<>(genRandom);

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (QuestPond.findFreePond(system) == null) continue;

            float ly = Misc.getDistanceLY(from.getLocationInHyperspace(),
                    system.getLocation());
            if (ly > MAX_LY) continue;

            // nearer is likelier, so a fisher describes somewhere they could plausibly have been working out of rather than somewhere across the sector
            picker.add(system, Math.max(1f, MAX_LY - ly));
        }

        return picker.pick();
    }

    protected void setReceiptAsk() {
        if (pond == null || pond.getId() == null) return;

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

    @Override
    public boolean isSatisfied() {
        return cleared && super.isSatisfied();
    }

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
            CampedSpot.setPondBlocked(pond, true);
            return;
        }

        if (CampedSpot.isCleared(camper)) {
            CampedSpot.returnToSource(camper);
        }

        CampedSpot.setPondBlocked(pond, false);
        cleared = true;

        if (camper != null) {
            Misc.makeUnimportant(camper.getMemoryWithoutUpdate(), getReason());
        }
        camper = null;

        updateInteractionDataImpl();
        FishIntelNotifications.update(this, Update.CAMP_CLEARED);
    }

    protected void setReceiptAboard(boolean aboard) {
        if (receiptAboard == aboard) return;

        receiptAboard = aboard;
        if (aboard) {
            QuestPond.release(pond, REF_KEY);
        } else {
            QuestPond.claim(pond, REF_KEY);
        }

        updateInteractionDataImpl();
    }

    @Override
    protected Object getDisplayedProgressUpdate(List<Integer> previous, List<Integer> current) {
        int before = previous.isEmpty() ? 0 : previous.get(0);
        int after = current.isEmpty() ? 0 : current.get(0);

        return after > before ? Update.RECEIPT_CAUGHT : Update.RECEIPT_LOST;
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

    @Override
    protected void notifyEnded() {
        super.notifyEnded();

        CampedSpot.despawn(camper);
        camper = null;

        CampedSpot.setPondBlocked(pond, false);
        QuestPond.release(pond, REF_KEY);
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (camper != null && !camper.isExpired()) return camper;
        if (pond != null && !pond.isExpired()) return pond;

        return super.getMapLocation(map);
    }

    @Override
    protected SectorEntityToken getFishRequestRouteTarget() {
        return getMapLocation(null);
    }

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
        FishRequirement.highlight(terms, getAsks(), null, reward);

        // the same helper as the list row's clock, so the two surfaces say it the same way
        if (days > 0f) {
            addDays(info, "left before they give the spot up for good.", getDaysLeft(),
                    getBulletColorForMode(ListInfoMode.IN_DESC), pad);
        }
    }

    @Override
    protected String getIntelPurpose() {
        return "A fisher wants a productive rupture back in service after another fleet forced "
                + "the crews off it. A fresh catch from that exact rupture proves the spot can "
                + "be worked again.";
    }

    @Override
    public String getBaseName() {
        return "A Spot Worth Keeping";
    }
}
