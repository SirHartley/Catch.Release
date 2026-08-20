package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Two children running a fiercely serious fish tournament. The player first chooses the exact two
 * contenders, then decides which child receives the better specimen. No credits reward - items
 * only, since children do not have money.
 */
public class KidsJob extends FishJob {

    public static final String GROUP_PORTRAIT_ID = "catchrelease_duel_group";

    /** The flag that puts this job's own pair of options up instead of the shared hand-over. */
    public static final String CHOICE_FLAG = "$catchrelease_duelChoice";

    public static final int VALUE = 2200;

    public static final float DAYS = 30f;

    /** What the better specimen has to grade to earn the extra. */
    public static final FishGrade BONUS_GRADE = FishGrade.FINE;

    /** Which of them ends up with the better fish. Flavour, and the only thing the choice moves. */
    protected boolean toLoud = true;

    /** Dialog-only selection; nothing leaves cargo until the player confirms its allocation. */
    protected transient FishHandoffPicker.Selection pendingSelection;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_duelRef", "$catchrelease_duelInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.SPACER);

        if (!setUpGiver(createdAt)) return false;
        // The giver only anchors the comm-directory entry; it is not one of the two children.
        getPerson().setPortraitSprite(Global.getSettings()
                .getSpriteName("characters", GROUP_PORTRAIT_ID));


        days = DAYS;

        //two specimens, one per kid
        FishRequirement ask = new FishRequirement();
        ask.count = 2;

        addAsk(ask);

        addRewards(FishRewardRoller.roll(genRandom, VALUE, false));

        setUpSpine();

        return true;
    }

    @Override
    protected String getDeliverFlag() {
        return CHOICE_FLAG;
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        if ("showBarBackdrop".equals(action)) {
            if (dialog != null && dialog.getVisualPanel() != null) {
                dialog.getVisualPanel().restoreSavedVisual();
            }

            return true;
        }

        if ("chooseContenders".equals(action)) {
            showContenderPicker(dialog, memoryMap);

            return true;
        }

        if ("turnInLoud".equals(action) || "turnInQuiet".equals(action)) {
            toLoud = "turnInLoud".equals(action);

            finishContenders(dialog, memoryMap);

            return true;
        }

        if ("cancelContenders".equals(action)) {
            pendingSelection = null;

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    protected void showContenderPicker(final InteractionDialogAPI dialog,
                                       final Map<String, MemoryAPI> memoryMap) {

        pendingSelection = null;

        boolean opened = FishHandoffPicker.show(dialog, "Select two contenders", "Choose", asks,
                new FishHandoffPicker.Listener() {
                    @Override
                    public void picked(FishHandoffPicker.Selection selection) {
                        pendingSelection = selection;

                        MemoryAPI mem = memoryMap == null ? null : memoryMap.get(MemKeys.LOCAL);
                        updateTokens(mem);

                        FireBest.fire(null, dialog, memoryMap,
                                "CatchReleaseDuelAllocationReady");
                        FireAll.fire(null, dialog, memoryMap,
                                "CatchReleaseDuelAllocationOptions");
                    }

                    @Override
                    public void cancelled() {
                        pendingSelection = null;
                        afterPickerCancelled(dialog, memoryMap);
                    }
                });

        if (!opened) afterPickerCancelled(dialog, memoryMap);
    }

    protected void finishContenders(InteractionDialogAPI dialog,
                                    Map<String, MemoryAPI> memoryMap) {

        if (pendingSelection == null) {
            showContenderPicker(dialog, memoryMap);
            return;
        }

        FishHandoffPicker.Selection selection = pendingSelection;

        if (handOver(selection, dialog, memoryMap)) {
            afterPickerPaid(dialog, memoryMap);
        } else {
            afterPickerCancelled(dialog, memoryMap);
        }

        pendingSelection = null;
    }

    /** The tournament has an authored terminal exchange; generic person options must not replace it. */
    @Override
    protected void afterPickerPaid(InteractionDialogAPI dialog,
                                   Map<String, MemoryAPI> memoryMap) {

        MemoryAPI mem = memoryMap == null ? null : memoryMap.get(MemKeys.LOCAL);
        if (mem != null) mem.unset("$menuState");

        if (dialog != null && dialog.getOptionPanel() != null) {
            dialog.getOptionPanel().clearOptions();
        }

        FireBest.fire(null, dialog, memoryMap, "catchreleaseJobPaid");
    }

    /** Bonus for specimen grade, not for the (consequence-free) loud/quiet choice. */
    @Override
    protected boolean payBonus(FishCatch offered) {
        if (offered == null || offered.getGrade().rank < BONUS_GRADE.rank) return false;

        for (FishReward extra : FishRewardRoller.roll(random(), VALUE / 2, false)) {
            extra.grant();
            rewards.add(extra);
        }

        return true;
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        token(mem, "$catchreleaseKid", toLoud ? "the loud one" : "the quiet one");
        token(mem, "$catchreleaseKidCap", toLoud ? "The loud one" : "The quiet one");
        token(mem, "$catchreleaseOther", toLoud ? "the quiet one" : "the loud one");
        token(mem, "$catchreleaseOtherCap", toLoud ? "The quiet one" : "The loud one");

        FishCatch better = null;
        FishCatch other = null;

        if (pendingSelection != null) {
            List<FishCatch> selected = pendingSelection.getContents();
            if (selected != null && selected.size() >= 2) {
                int betterIndex = 0;
                for (int i = 1; i < selected.size(); i++) {
                    if (selected.get(i).getSizeFraction()
                            > selected.get(betterIndex).getSizeFraction()) {
                        betterIndex = i;
                    }
                }

                better = selected.get(betterIndex);
                other = selected.get(betterIndex == 0 ? 1 : 0);
            }
        }

        token(mem, "$catchreleaseDuelSelectionReady", pendingSelection != null
                && pendingSelection.getContents() != null
                && pendingSelection.getContents().size() >= 2);
        token(mem, "$catchreleaseDuelBetterFish",
                describeContender(better, "the better specimen"));
        token(mem, "$catchreleaseDuelOtherFish",
                describeContender(other, "the other specimen"));
    }

    protected static String describeContender(FishCatch fish, String fallback) {
        if (fish == null) return fallback;

        return "the " + fish.getGrade().name.toLowerCase()
                + " " + fish.getDisplayName()
                + " (" + Misc.getRoundedValue(fish.length) + " m)";
    }

    @Override
    public String getBaseName() {
        return "The Battle";
    }
}
