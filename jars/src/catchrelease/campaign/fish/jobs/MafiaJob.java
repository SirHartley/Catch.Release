package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MafiaJob extends FishJob {

    public static final String BET_FLAG = "$catchrelease_ringBet";

    public static final float BASE_ODDS = 0.38f;
    public static final float QUALITY_SWING = 0.30f;
    public static final float WIN_MULT = 2f;

    public static final String NIGHTS_SUPPLIED_KEY = "$catchrelease_fightNightsSupplied";
    public static final String COMMISSIONER_RANK = "catchrelease_fightCommissioner";
    public static final String HOUSE_RANK = "catchrelease_fightHouse";
    public static final int CURRENT_VERSION = 1;

    protected static final String LEGACY_LEFT_NAME = "Salvatore";
    protected static final String LEGACY_RIGHT_NAME = "Enzo";

    protected String left = "left entry";
    protected String right = "right entry";
    protected PersonAPI partner;
    protected transient FishHandoffPicker.Selection pendingSelection;
    protected transient FishCatch leftFighter;
    protected transient FishCatch rightFighter;
    protected float leftOdds = BASE_ODDS;
    protected float rightOdds = BASE_ODDS;
    protected String wager = null;
    protected boolean won = false;
    protected boolean leftWon = false;
    protected boolean nightRecorded = false;
    protected int fightNightVersion = 0;
    protected transient boolean identitiesChecked = false;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_ringRef", "$catchrelease_ringInProgress")) {
            return false;
        }

        setGiverRank(COMMISSIONER_RANK);
        setGiverVoice(Voices.OFFICIAL);

        if (!setUpGiver(createdAt)) return false;
        setUpPeople(createdAt);

        FishRequirement ask = new FishRequirement();
        ask.count = 2;
        addAsk(ask);
        fightNightVersion = CURRENT_VERSION;

        if (!setDurationForAsks(createdAt)) return false;
        addRewards(QuestRewards.roll(
                new QuestRewards.Request(asks).random(genRandom)).rewards);

        setUpSpine();

        return true;
    }

    @Override
    protected String getDeliverFlag() {
        return BET_FLAG;
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if ("chooseFighters".equals(action)) {
            showFighterPicker(dialog, memoryMap);

            return true;
        }

        if ("turnInFlat".equals(action) || "turnInLeft".equals(action)
                || "turnInRight".equals(action)) {
            if ("turnInLeft".equals(action)) wager = left;
            else if ("turnInRight".equals(action)) wager = right;
            else wager = null;

            won = false;
            finishFighters(dialog, memoryMap);

            return true;
        }

        if ("cancelFighters".equals(action)) {
            clearFighters();

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    protected void setUpPeople(MarketAPI market) {
        PersonAPI giver = getPerson();
        if (giver != null) {
            replaceLegacyIdentity(giver, LEGACY_LEFT_NAME);
            giver.setRankId(COMMISSIONER_RANK);
            giver.setPostId(null);
            giver.setVoice(Voices.OFFICIAL);
        }

        if (partner == null && market != null && market.getFaction() != null) {
            for (int i = 0; i < 8; i++) {
                partner = market.getFaction().createRandomPerson(Gender.ANY, random());
                if (giver == null || giver.getPortraitSprite() == null
                        || !giver.getPortraitSprite().equals(partner.getPortraitSprite())) break;
            }
        }

        if (partner != null) {
            replaceLegacyIdentity(partner, LEGACY_RIGHT_NAME);
            partner.setRankId(HOUSE_RANK);
            partner.setPostId(null);
            partner.setVoice(Voices.BUSINESS);
        }
    }

    protected void replaceLegacyIdentity(PersonAPI person, String legacyFirstName) {
        if (person == null || person.getFaction() == null || person.getName() == null
                || !legacyFirstName.equals(person.getName().getFirst())) return;

        PersonAPI replacement = person.getFaction().createRandomPerson(Gender.ANY, random());
        if (replacement == null) return;

        person.setName(replacement.getName());
        person.setGender(replacement.getGender());
        person.setPortraitSprite(replacement.getPortraitSprite());
    }

    @Override
    protected void advanceImpl(float amount) {
        migrateLegacyJob();

        if (!identitiesChecked) {
            setUpPeople(getGiverMarket());
            identitiesChecked = true;
        }

        super.advanceImpl(amount);
    }

    protected void migrateLegacyJob() {
        if (fightNightVersion >= CURRENT_VERSION) return;

        asks.clear();
        FishRequirement ask = new FishRequirement();
        ask.count = 2;
        asks.add(ask);
        fightNightVersion = CURRENT_VERSION;
        resetDisplayedProgress();
    }

    @Override
    protected void showContactVisual(InteractionDialogAPI dialog) {
        setUpPeople(getGiverMarket());
        super.showContactVisual(dialog);

        if (dialog != null && partner != null) {
            dialog.getVisualPanel().showSecondPerson(partner);
        }
    }

    protected void showFighterPicker(final InteractionDialogAPI dialog,
                                     final Map<String, MemoryAPI> memoryMap) {
        clearFighters();

        boolean opened = FishHandoffPicker.show(dialog,
                "Choose exactly two fish for the main event.", "Use this pair.", asks,
                new FishHandoffPicker.Listener() {
                    @Override
                    public void picked(FishHandoffPicker.Selection selection) {
                        assignFighters(selection);

                        MemoryAPI mem = memoryMap == null ? null : memoryMap.get(MemKeys.LOCAL);
                        updateTokens(mem);

                        FireBest.fire(null, dialog, memoryMap, "CatchReleaseRingWagerReady");
                        FireAll.fire(null, dialog, memoryMap, "CatchReleaseRingWagerOptions");
                    }

                    @Override
                    public void cancelled() {
                        clearFighters();
                        afterPickerCancelled(dialog, memoryMap);
                    }
                });

        if (!opened) afterPickerCancelled(dialog, memoryMap);
    }

    protected void assignFighters(FishHandoffPicker.Selection selection) {
        clearFighters();
        if (selection == null || selection.getContents() == null
                || selection.getContents().size() < 2) return;

        pendingSelection = selection;

        int leftIndex = random().nextBoolean() ? 0 : 1;
        leftFighter = selection.getContents().get(leftIndex);
        rightFighter = selection.getContents().get(leftIndex == 0 ? 1 : 0);

        left = fightName(leftFighter);
        right = fightName(rightFighter);
        if (left.equals(right)) {
            left += " Red";
            right += " Blue";
        }

        leftOdds = odds(leftFighter, rightFighter);
        rightOdds = odds(rightFighter, leftFighter);
    }

    protected void finishFighters(InteractionDialogAPI dialog,
                                  Map<String, MemoryAPI> memoryMap) {
        if (pendingSelection == null || leftFighter == null || rightFighter == null) {
            showFighterPicker(dialog, memoryMap);
            return;
        }

        FishHandoffPicker.Selection selection = pendingSelection;
        List<FishReward> promisedRewards = new ArrayList<>(rewards);

        if (handOver(selection, dialog, memoryMap)) {
            afterPickerPaid(dialog, memoryMap);
            clearFighters();
        } else {
            // beforePayment may have replaced the pool for a wager before spend discovered that one selected item had gone missing; a failed hand-in keeps the quoted fee
            rewards.clear();
            rewards.addAll(promisedRewards);
            clearFighters();
            afterPickerCancelled(dialog, memoryMap);
        }
    }

    @Override
    protected void afterPickerPaid(InteractionDialogAPI dialog,
                                   Map<String, MemoryAPI> memoryMap) {
        FireBest.fire(null, dialog, memoryMap, "catchreleaseJobPaid");
        FireBest.fire(null, dialog, memoryMap, "CatchReleaseRingSettlement");
        showRewardReceipts(dialog);
        FireAll.fire(null, dialog, memoryMap, OPTIONS_TRIGGER);
    }

    protected void clearFighters() {
        pendingSelection = null;
        leftFighter = null;
        rightFighter = null;
        leftOdds = BASE_ODDS;
        rightOdds = BASE_ODDS;
        wager = null;
        won = false;
        leftWon = false;
    }

    protected static float odds(FishCatch backed, FishCatch opponent) {
        float backedQuality = backed == null ? 0.5f : backed.getSizeFraction();
        float opponentQuality = opponent == null ? 0.5f : opponent.getSizeFraction();

        return Math.max(0.10f, Math.min(0.85f,
                BASE_ODDS + (backedQuality - opponentQuality) * QUALITY_SWING));
    }

    @Override
    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
        if (wager == null) {
            float decided = leftOdds + rightOdds;
            leftWon = decided <= 0f || random().nextFloat() < leftOdds / decided;
            return;
        }

        float odds = wager.equals(left) ? leftOdds : rightOdds;

        won = random().nextFloat() < odds;
        leftWon = won == wager.equals(left);

        rewards.clear();

        if (won) {
            addRewards(QuestRewards.roll(new QuestRewards.Request(asks)
                    .budgetMult(WIN_MULT).random(random())).rewards);
        }
    }

    @Override
    protected String getIntelSpecialTerms() {
        return "You may take the fixed supplier fee of " + describeRewards()
                + " or stake the entire fee on either fish. A winning stake replaces the fee "
                + "with a newly rolled, larger purse; a loss pays nothing and leaves no debt. "
                + "The house keeps the share not assigned to either fighter.";
    }

    @Override
    protected String getIntelPurpose() {
        return "Supply any two fish from your own catch for the bar's next Fight Night main "
                + "event. At hand-in, the Commissioner measures each fish against the normal "
                + "size range for its species and posts both public lines along with the house "
                + "share.";
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        if (mem == null) return;

        migrateLegacyJob();
        setUpPeople(getGiverMarket());

        token(mem, "$catchreleaseLeft", left);
        token(mem, "$catchreleaseRight", right);
        token(mem, "$catchreleaseRingSelectionReady",
                pendingSelection != null && leftFighter != null && rightFighter != null);

        token(mem, "$catchreleaseLeftFish",
                describeFighter(leftFighter, "the left-hand fighter"));
        token(mem, "$catchreleaseRightFish",
                describeFighter(rightFighter, "the right-hand fighter"));

        token(mem, "$catchreleaseLeftOdds", percent(leftOdds));
        token(mem, "$catchreleaseRightOdds", percent(rightOdds));
        token(mem, "$catchreleaseHouseOdds", percent(1f - leftOdds - rightOdds));
        token(mem, "$catchreleaseWon", won);

        // separate boolean since a rules-engine condition needs a true/false, not a name string
        token(mem, "$catchreleaseHasWager", wager != null);

        token(mem, "$catchreleaseWager", wager == null ? "nobody" : wager);
        token(mem, "$catchreleaseFoe", wager == null ? "nobody"
                : wager.equals(left) ? right : left);

        FishCatch wagered = wager == null ? null
                : wager.equals(left) ? leftFighter : rightFighter;
        FishCatch opposing = wager == null ? null
                : wager.equals(left) ? rightFighter : leftFighter;

        token(mem, "$catchreleaseWagerFish",
                describeFighter(wagered, "the backed fighter"));
        token(mem, "$catchreleaseFoeFish",
                describeFighter(opposing, "the opposing fighter"));

        String winner = leftWon ? left : right;
        String loser = leftWon ? right : left;
        FishCatch winningFish = leftWon ? leftFighter : rightFighter;
        FishCatch losingFish = leftWon ? rightFighter : leftFighter;

        token(mem, "$catchreleaseWinner", winner);
        token(mem, "$catchreleaseLoser", loser);
        token(mem, "$catchreleaseWinnerFish",
                describeFighter(winningFish, "the winning fighter"));
        token(mem, "$catchreleaseLoserFish",
                describeFighter(losingFish, "the other fighter"));

        int nights = getNightsSupplied();
        token(mem, "$catchreleaseFightNights", nights);
        token(mem, "$catchreleaseFightNightWord", nights == 1 ? "entry" : "entries");
        token(mem, "$catchreleaseFightDate", Global.getSector() == null
                ? "Tonight" : Global.getSector().getClock().getDateString());
    }

    protected static String fightName(FishCatch fish) {
        if (fish == null) return "Open Line";

        String species = fish.getDisplayName();
        float size = fish.getSizeFraction();
        if (size < 0.2f) return species + " Nipper";
        if (size < 0.4f) return species + " Scrapper";
        if (size < 0.6f) return species + " Stayer";
        if (size < 0.8f) return species + " Bruiser";
        return species + " Crown";
    }

    protected int getNightsSupplied() {
        if (Global.getSector() == null) return 0;
        return Math.max(0, Global.getSector().getMemoryWithoutUpdate()
                .getInt(NIGHTS_SUPPLIED_KEY));
    }

    @Override
    protected boolean onDelivered() {
        if (!nightRecorded && Global.getSector() != null) {
            MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
            memory.set(NIGHTS_SUPPLIED_KEY, Math.max(0,
                    memory.getInt(NIGHTS_SUPPLIED_KEY)) + 1);
            nightRecorded = true;
        }

        return false;
    }

    protected static String describeFighter(FishCatch fish, String fallback) {
        if (fish == null) return fallback;

        return Misc.ucFirst(fish.getGrade().name.toLowerCase())
                + " " + fish.getDisplayName()
                + ", " + Misc.getRoundedValue(fish.length) + " m";
    }

    protected static String percent(float odds) {
        int tenths = Math.round(odds * 1000f);
        return (tenths / 10) + "." + Math.abs(tenths % 10) + "%";
    }

    @Override
    public String getBaseName() {
        return "Fight Night: Main Event";
    }
}
