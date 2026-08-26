package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MafiaJob extends FishJob {

    public static final String BET_FLAG = "$catchrelease_ringBet";

    public static final int VALUE = 3000;
    public static final float DAYS = 35f;
    public static final float BASE_ODDS = 0.38f;
    public static final float QUALITY_SWING = 0.30f;
    public static final float WIN_MULT = 2f;

    protected static final String LEFT_FIRST_NAME = "Salvatore";
    protected static final String RIGHT_FIRST_NAME = "Enzo";

    protected String left = LEFT_FIRST_NAME;
    protected String right = RIGHT_FIRST_NAME;
    protected PersonAPI partner;
    protected transient FishHandoffPicker.Selection pendingSelection;
    protected transient FishCatch leftFighter;
    protected transient FishCatch rightFighter;
    protected float leftOdds = BASE_ODDS;
    protected float rightOdds = BASE_ODDS;
    protected String wager = null;
    protected boolean won = false;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_ringRef", "$catchrelease_ringInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.VILLAIN);

        if (!setUpGiver(createdAt)) return false;
        setUpPeople(createdAt);

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 2;

        if (genRandom.nextFloat() > 0.4f) ask.minRarity = FishRarity.UNCOMMON;
        if (genRandom.nextFloat() > 0.5f) ask.method = FishLogEntry.Method.HARPOON;

        addAsk(ask);

        addRewards(FishRewardRoller.roll(genRandom, VALUE, true));

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
            giver.setName(new FullName(LEFT_FIRST_NAME, "", Gender.MALE));
            ensureMalePortrait(giver);
            left = giver.getName().getFirst();
        }

        if (partner == null && market != null && market.getFaction() != null) {
            for (int i = 0; i < 8; i++) {
                partner = market.getFaction().createRandomPerson(Gender.MALE, random());
                if (giver == null || giver.getPortraitSprite() == null
                        || !giver.getPortraitSprite().equals(partner.getPortraitSprite())) break;
            }
        }

        if (partner != null) {
            partner.setName(new FullName(RIGHT_FIRST_NAME, "", Gender.MALE));
            ensureMalePortrait(partner);
            partner.setRankId(Ranks.CITIZEN);
            partner.setVoice(Voices.VILLAIN);
            right = partner.getName().getFirst();
        }
    }

    protected void ensureMalePortrait(PersonAPI person) {
        if (person == null || person.getFaction() == null) return;
        if (person.getFaction().getPortraits(Gender.MALE).getItems()
                .contains(person.getPortraitSprite())) return;

        PersonAPI replacement = person.getFaction().createRandomPerson(Gender.MALE, random());
        if (replacement != null) person.setPortraitSprite(replacement.getPortraitSprite());
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

        boolean opened = FishHandoffPicker.show(dialog, "Select two fighters", "Enter", asks,
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

    protected void clearFighters() {
        pendingSelection = null;
        leftFighter = null;
        rightFighter = null;
        leftOdds = BASE_ODDS;
        rightOdds = BASE_ODDS;
        wager = null;
        won = false;
    }

    protected static float odds(FishCatch backed, FishCatch opponent) {
        float backedQuality = backed == null ? 0.5f : backed.getSizeFraction();
        float opponentQuality = opponent == null ? 0.5f : opponent.getSizeFraction();

        return Math.max(0.10f, Math.min(0.85f,
                BASE_ODDS + (backedQuality - opponentQuality) * QUALITY_SWING));
    }

    @Override
    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
        if (wager == null) return;

        float odds = wager.equals(left) ? leftOdds : rightOdds;

        won = random().nextFloat() < odds;

        rewards.clear();

        if (won) addRewards(FishRewardRoller.roll(random(), (int) (VALUE * WIN_MULT), true));
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        if (mem == null) return;

        setUpPeople(getGiverMarket());

        token(mem, "$personFirstName", left);
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
    }

    protected static String describeFighter(FishCatch fish, String fallback) {
        if (fish == null) return fallback;

        return "the " + fish.getGrade().name.toLowerCase()
                + " " + fish.getDisplayName()
                + " (" + Misc.getRoundedValue(fish.length) + " m)";
    }

    protected static String percent(float odds) {
        int tenths = Math.round(odds * 1000f);
        return (tenths / 10) + "." + Math.abs(tenths % 10) + "%";
    }

    @Override
    public String getBaseName() {
        return "The Tank";
    }
}
