package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// T4 and the legendary list. Every legendary rumour carries the name its teller used; the latest one heard for each
// species is kept, from the first rumour on, so the list can show it once it appears.
public final class LegendaryHunt {

    // The bar spacer's tellings (CatchReleaseRatingLegendaryReport rows). Three of them name nothing, so their names
    // come from the story; the dialogue still says no name was given.
    protected static final Map<String, String> SPACER_NAMES = Map.of(
            "lantern_jack", "Lantern Jack",
            "quorum", "The Many",
            "slipstream_moray", "The Moray",
            "false_dawn", "The Burning Lie",
            "longliner", "The Imposter",
            "abyssal_ghost_manta", "The Abyss");

    private LegendaryHunt() {
    }

    // One intel from T4 to the last legendary; title and body switch with the stage, as FishingIntro.IntroIntel does.
    public static class HuntIntel extends BaseIntelPlugin {

        @Override
        public boolean shouldRemoveIntel() {
            if (isEnded()) return true;
            // the finished list stays up for its end delay, counted down by the script checkAllCaught adds
            if (!isActive() && !FishermanChain.isAt(FishermanChain.Stage.HUNT_DONE)) endImmediately();

            return isEnded();
        }

        @Override
        public String getName() {
            return switch (FishermanChain.getStage()) {
                case T4_LANDED -> "Listen for rumours: report to the Fisherman";
                case HUNTING -> "Hunting the legendary fish";
                case HUNT_DONE -> "Hunting the legendary fish: every one caught";
                default -> "Listen for rumours";
            };
        }

        @Override
        public String getSmallDescriptionTitle() {
            return getName();
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);

            addBulletPoints(info, mode);
        }

        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color text = getBulletColorForMode(mode);
            float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);
            switch (FishermanChain.getStage()) {
                case T4_LANDED -> info.addPara("Return to the nearest fishing boat", text, pad);
                case HUNTING, HUNT_DONE -> addHuntingLines(info, text, pad);
                default -> {
                    info.addPara("Improve your gear", text, pad);
                    info.addPara("Listen for rumours", text, 0f);
                }
            }
            unindent(info);
        }

        // One line per legendary: the real name once caught, else the latest told name, else question marks.
        protected void addHuntingLines(TooltipMakerAPI info, Color text, float pad) {
            float linePad = pad;
            for (FishSpec spec : getLegendaries()) {
                if (LegendaryChases.isCaught(spec.id)) {
                    info.addPara("%s - caught", linePad, text, FishRarity.LEGENDARY.color, spec.getDisplayName());
                } else {
                    String told = getToldName(spec.id);
                    if (told == null) {
                        info.addPara("???", text, linePad);
                    } else {
                        info.addPara("%s - heard of", linePad, text, Misc.getHighlightColor(), told);
                    }
                }
                linePad = 0f;
            }
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            float opad = 10f;
            FactionAPI faction = Global.getSector().getFaction(FishermanConstants.FACTION);

            info.addImages(width, 128, opad, opad, FishermanIdentity.getPortrait(0f), faction.getCrest());

            if (FishermanChain.isAt(FishermanChain.Stage.T4_LANDED)) {
                info.addPara("You landed one of the stranger things the Fisherman told you about. He will want to "
                        + "hear about it.", opad);
                addBulletPoints(info, ListInfoMode.IN_DESC);
                FishIntelMapButton.addSetAutopilot(info, width, FishingIntro.getNearestBoat());
                return;
            }

            if (FishermanChain.isAt(FishermanChain.Stage.HUNTING)
                    || FishermanChain.isAt(FishermanChain.Stage.HUNT_DONE)) {
                List<FishSpec> legendaries = getLegendaries();
                long caught = legendaries.stream().filter(spec -> LegendaryChases.isCaught(spec.id)).count();

                info.addPara("There is more than one legendary, and no two are alike. Rumours from the Fisherman and "
                        + "from bars give their names as the tellers know them; a catch shows what each one is.", opad);
                info.addPara("Caught: %s of %s.", opad, Misc.getHighlightColor(), String.valueOf(caught),
                        String.valueOf(legendaries.size()));
                addBulletPoints(info, ListInfoMode.IN_DESC);
                return;
            }

            info.addPara("The Fisherman says there are stranger things out there than epics, and that the talk "
                    + "about them is worth following once your gear is very good.", opad);
            addBulletPoints(info, ListInfoMode.IN_DESC);
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            if (FishermanChain.isAt(FishermanChain.Stage.T4_LANDED)
                    && FishIntelMapButton.handleSetAutopilot(buttonId, FishingIntro.getNearestBoat())) {
                return;
            }

            super.buttonPressConfirmed(buttonId, ui);
        }

        @Override
        public String getIcon() {
            return FishermanIdentity.getPortrait(0f);
        }

        @Override
        public String getSortString() {
            return getSortStringNewestFirst();
        }

        @Override
        public FactionAPI getFactionForUIColors() {
            return Global.getSector().getPlayerFaction();
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_MISSIONS);
            tags.add(Tags.INTEL_ACCEPTED);
            tags.add(FishermanConstants.FACTION);

            return tags;
        }
    }

    // A long-term quest: no deadline, and a legendary landed before it starts completes it at once.
    public static void start(TextPanelAPI text) {
        FishermanChain.setStage(FishermanChain.Stage.T4_LISTEN);
        FishIntelNotifications.queue(new HuntIntel(), text);
        checkLanded();
    }

    // The lamp ship, unannounced until now, and the news that there is more than one legendary.
    public static boolean complete(TextPanelAPI text) {
        if (!FishermanChain.isAt(FishermanChain.Stage.T4_LANDED)) return false;

        List<FishReward.Receipt> receipts = new ArrayList<>();
        receipts.add(FishReward.ship(FishingShips.Ship.LAMP.hullId).grantWithReceipt());

        FishermanChain.setStage(FishermanChain.Stage.HUNTING);
        FishReward.showReceipts(text, receipts);
        updateIntel();
        checkAllCaught();

        return true;
    }

    public static boolean isActive() {
        FishermanChain.Stage stage = FishermanChain.getStage();

        return stage == FishermanChain.Stage.T4_LISTEN || stage == FishermanChain.Stage.T4_LANDED
                || stage == FishermanChain.Stage.HUNTING;
    }

    // Called by LegendaryChases.noteCaught for every legendary catch.
    public static void noteCaught(String legendaryId) {
        checkLanded();
        if (FishermanChain.isAt(FishermanChain.Stage.HUNTING)) updateIntel();
        checkAllCaught();
    }

    // The list concludes once every legendary is caught. What that brings is still open (FISHERMAN_CHAIN.md).
    protected static void checkAllCaught() {
        if (!FishermanChain.isAt(FishermanChain.Stage.HUNTING)
                || !getLegendaries().stream().allMatch(spec -> LegendaryChases.isCaught(spec.id))) return;

        FishermanChain.setStage(FishermanChain.Stage.HUNT_DONE);
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(HuntIntel.class)) {
            // the end delay only counts down in advance(), which the IntelManager never calls
            ((HuntIntel) intel).endAfterDelay();
            Global.getSector().addScript((HuntIntel) intel);
            FishIntelNotifications.update((HuntIntel) intel, null);
        }
    }

    protected static void checkLanded() {
        if (!FishermanChain.isAt(FishermanChain.Stage.T4_LISTEN) || getLegendaries().stream()
                .noneMatch(spec -> LegendaryChases.isCaught(spec.id))) return;

        FishermanChain.setStage(FishermanChain.Stage.T4_LANDED);
        updateIntel();
    }

    public static void updateIntel() {
        if (Global.getSector() == null) return;

        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(HuntIntel.class)) {
            FishIntelNotifications.update((HuntIntel) intel, null);
        }
    }

    // Every legendary the game can spawn, in fish.csv order; counted from the data, not a constant.
    public static List<FishSpec> getLegendaries() {
        List<FishSpec> legendaries = new ArrayList<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec != null && spec.rarity == FishRarity.LEGENDARY && spec.spawnWeight > 0f) legendaries.add(spec);
        }
        return legendaries;
    }

    // The Fisherman's leads name every legendary by its real name.
    public static String getFishermanName(String legendaryId) {
        FishSpec spec = FishSpecLoader.getFishSpec(legendaryId);

        return spec == null ? null : spec.getDisplayName();
    }

    public static String getSpacerName(String legendaryId) {
        String told = SPACER_NAMES.get(legendaryId);

        return told != null ? told : getFishermanName(legendaryId);
    }

    // Called by FishRumors.publish for every legendary rumour, whoever told it.
    public static void recordTold(String legendaryId, String toldName) {
        if (legendaryId == null || toldName == null || toldName.isEmpty()) return;

        FishermanChain.Saved saved = FishermanChain.get(true);
        if (saved == null) return;
        if (saved.toldNames == null) saved.toldNames = new LinkedHashMap<>();

        saved.toldNames.put(legendaryId, toldName);
    }

    public static String getToldName(String legendaryId) {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null || saved.toldNames == null ? null : saved.toldNames.get(legendaryId);
    }
}
