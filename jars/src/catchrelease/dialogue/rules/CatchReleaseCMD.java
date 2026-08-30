package catchrelease.dialogue.rules;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.crime.HarpoonHitman;
import catchrelease.campaign.crime.LampOffence;
import catchrelease.campaign.crime.LampPatrolResponse;
import catchrelease.campaign.fish.FishingTaboo;
import catchrelease.campaign.fish.colony.AquariumTankPanel;
import catchrelease.campaign.fish.colony.AquariumTankScript;
import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.crab.CrabBackdrops;
import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.crab.CrablobabBarPresence;
import catchrelease.campaign.fish.crab.CrablobabIdentity;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanBycatch;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.fisherman.FishermanQuest;
import catchrelease.campaign.fish.fisherman.FishermanShelf;
import catchrelease.campaign.fish.fisherman.FishermanSurveyDialog;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.camp.CampedSpotJob;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishShopDialog;
import catchrelease.campaign.fish.tutorial.Castaway;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.campaign.fish.tutorial.TutorialWreck;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CatchReleaseCMD extends BaseCommandPlugin {

    protected static final Pattern CREDIT_REWARD =
            Pattern.compile("\\b(?:[\\d,.]+ credits(?: guaranteed, plus [\\d.]+x the value of "
                    + "the fish handed in)?|[\\d.]+x the value of the fish handed in)\\b");

    public static final String DRIFT = "$catchreleaseDrift";
    public static final String STAGE = "$catchreleaseStage";

    public static final String TARGET = "$catchreleaseTarget";
    public static final String TARGET_WHERE = "$catchreleaseTargetWhere";
    public static final String TARGET_MET = "$catchreleaseTargetMet";
    public static final String TARGET_POND = "$catchreleaseTargetPond";
    public static final String TARGET_DEEP = "$catchreleaseTargetDeep";
    public static final String TARGET_HERE = "$catchreleaseTargetHere";
    public static final String TARGET_SET = "$catchreleaseTargetSet";
    public static final String TARGET_PLACED = "$catchreleaseTargetPlaced";

    public static final String CARRYING = "$catchreleaseCarrying";
    public static final String DEEP_HANDOFF = "$catchreleaseDeepHandoff";
    public static final String CONTINUITY_QUESTION_AVAILABLE =
            "$catchreleaseContinuityQuestionAvailable";
    public static final String OUTFITTER = "$catchreleaseOutfitter";
    public static final String CAN_SKIP = "$catchreleaseCanSkip";
    public static final String RATING_PLANET_NAME = "$ratingPlanetName";
    public static final String RATING_PLANET_KNOWN = "$catchreleaseRatingPlanetKnown";

    public static final String WORK = "$catchreleaseWork";
    public static final String WORK_AVAILABLE = "$catchreleaseWorkAvailable";
    public static final String WORK_MET = "$catchreleaseWorkMet";
    public static final String WORK_FISH = "$catchreleaseWorkFish";
    public static final String WORK_WHERE = "$catchreleaseWorkWhere";
    public static final String WORK_PAY = "$catchreleaseWorkPay";
    public static final String WORK_POND = "$catchreleaseWorkPond";
    public static final String WORK_ROLLED = "$catchreleaseWorkRolled";

    public static final String SHELF = "$catchreleaseShelf";
    public static final String HAS_FISH = "$catchreleaseHoldHasFish";

    public static final String SELL_COMMON = "$catchreleaseSellCommon";
    public static final String SELL_UNCOMMON = "$catchreleaseSellUncommon";
    public static final String SELL_RARE = "$catchreleaseSellRare";
    public static final String SELL_EPIC = "$catchreleaseSellEpic";

    public static final String RUMOR = "$catchreleaseRumor";
    public static final String RUMOR_SYSTEM = "$catchreleaseRumorSystem";
    public static final String RUMOR_STRANGER = "$catchreleaseRumorStranger";
    public static final String RUMOR_RARITY = "$catchreleaseRumorRarity";
    public static final String RUMOR_LOOT = "$catchreleaseRumorLoot";
    public static final String RUMOR_OUTSIDER = "$catchreleaseRumorOutsider";
    public static final String BYCATCH_PENDING = "$catchreleaseBycatchPending";

    protected static final String FISHER_ASK_PAGE = "$catchreleaseFisherAskPage";
    protected static final String FISHER_ASK_COUNT = "$catchreleaseFisherAskCount";
    protected static final int FISHER_ASK_PAGE_SIZE = 6;

    public static final String CRAB_ANY = "$catchreleaseCrabAny";
    public static final String CRAB_EXPLOSIVE_TARGET = "$catchreleaseCrabExplosiveTarget";
    public static final String CRAB_CRAB_NAME = "$catchreleaseCrabBassName";
    public static final String CRAB_CRAB_PRICE = "$catchreleaseCrabBassPrice";
    public static final String CRAB_CRAB_CRAB_PRICE = "$catchreleaseCrabBassCrabPrice";
    public static final String CRAB_CRAB_AFFORD = "$catchreleaseCrabBassAfford";
    public static final String CRAB_BACKDROP = "$catchreleaseCrabBackdrop";
    public static final String CRAB_BACKDROP_NAME = "$catchreleaseCrabBackdropName";
    public static final String CRAB_BACKDROP_PRICE = "$catchreleaseCrabBackdropPrice";
    public static final String CRAB_BACKDROP_CRABS = "$catchreleaseCrabBackdropCrabs";
    public static final String CRAB_BACKDROP_AFFORD = "$catchreleaseCrabBackdropAfford";
    public static final String FISH_WELCOME = "$catchreleaseFishWelcome";

    public static final String LAMP_CONV = "$catchrelease_lampConv";
    public static final String LAMP_RUNG = "$catchrelease_lampRung";
    public static final String LAMP_FINE = "$catchrelease_lampFine";
    public static final String LAMP_FINE_TEXT = "$catchrelease_lampFineDGS";
    public static final String LAMP_WHERE = "$catchrelease_lampWhere";
    public static final String LAMP_HAUL = "$catchrelease_lampHaul";

    protected transient InteractionDialogPlugin behind;
    protected transient boolean panelOpen;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) return false;

        String verb = params.get(0).getString(memoryMap);
        if (verb == null) return false;

        String arg = params.size() > 1 ? params.get(1).getString(memoryMap) : null;

        switch (verb) {
            case "tokens":
                writeTokens(dialog, memoryMap);
                return true;

            case "dropCutComm":
                return dropCutComm(dialog);

            case "leaveEncounter":
                return leaveEncounter(dialog);
            case "bribeHitman":
                return bribeHitman(dialog);

            case "openShop":
                return openPanel(dialog, new FishShopDialog(this::resume));
            case "openCharts":
                return openPanel(dialog, new FishermanSurveyDialog(this::resume));
            case "openBuyer":
                return FishBuyer.show(dialog);

            case "sellUpTo":
                return FishBuyer.sellUpTo(dialog, arg);
            case "colorBulkSaleOptions":
                return colorBulkSaleOptions(dialog);
            case "beginFisherQuestions":
                return beginFisherQuestions(dialog, memoryMap);
            case "addFisherQuestion":
                return addFisherQuestion(dialog, params, memoryMap);
            case "finishFisherQuestions":
                return finishFisherQuestions(dialog, params, memoryMap);
            case "highlightJobText":
                return highlightJobText(ruleId, dialog, params, memoryMap);
            case "highlightWorkText":
                return highlightWorkText(ruleId, dialog, params, memoryMap);
            case "highlightIntroText":
                return highlightIntroText(ruleId, dialog, params, memoryMap);
            case "highlightFishText":
                return highlightQuestText(ruleId, dialog, params, memoryMap,
                        Collections.emptyList());
            case "showIntroMap":
                return showIntroMap(dialog, params.size() > 1
                        ? params.get(1).getStringWithTokenReplacement(ruleId, dialog, memoryMap)
                        : null);
            case "showWorkMap":
                return showWorkMap(dialog, params.size() > 1
                        ? params.get(1).getStringWithTokenReplacement(ruleId, dialog, memoryMap)
                        : null);

            case "point":
                FishingIntro.point();
                return true;
            case "rememberRatingPlanet":
                return rememberRatingPlanet(dialog);
            case "showIntroIntel":
                return FishingIntro.showCurrentIntel(text(dialog));
            case "giveRod":
                FishingIntro.giveRod(text(dialog));
                return true;
            case "sendOut":
                FishingIntro.sendOut(text(dialog));
                return true;
            case "giveOutfitter":
                FishingIntro.giveOutfitter(text(dialog));
                return true;
            case "giveDeepGear":
                FishingIntro.giveDeepGear(text(dialog));
                return true;
            case "giveCharts":
                FishingIntro.giveCharts(text(dialog));
                return true;
            case "finishIntro":
                FishingIntro.finish(text(dialog));
                return true;
            case "takeTarget":
                return FishingIntro.takeTarget();
            case "skipIntro":
                FishingIntro.skip(text(dialog));
                return true;

            case "rescueCastaway":
                return rescueCastaway(dialog);
            case "castawayEligible":
                return dialog != null && dialog.getInteractionTarget()
                        instanceof com.fs.starfarer.api.campaign.PlanetAPI planet
                        && Castaway.isEligible(planet);
            case "startCastaway":
                return dialog != null && dialog.getInteractionTarget()
                        instanceof com.fs.starfarer.api.campaign.PlanetAPI planet
                        && Castaway.start(planet);

            case "carryFisherProperty":
                FishingIntro.takeFisherProperty();
                if (dialog != null) TutorialWreck.retire(dialog.getInteractionTarget());
                return true;
            case "dropFisherProperty":
                FishingIntro.dropFisherProperty();
                return true;

            // Aliases keep an old rules sheet usable during a hot reload of this update.
            case "carryHarpoon":
                FishingIntro.takeFisherProperty();
                return true;
            case "dropHarpoon":
                FishingIntro.dropFisherProperty();
                return true;

            case "rollWork":
                return rollWork(memoryMap);
            case "takeWork":
                return takeWork(dialog);
            case "turnInWork":
                return FishermanQuest.showTurnInPicker(dialog, memoryMap);

            case "rumor":
                return FishingIntro.isAtLeast(FishingIntro.DONE)
                        && FishRumors.isAvailable() && FishRumors.create() != null;
            case "showRumorIntel":
                return FishRumors.showCurrentIntel(text(dialog));

            case "ackBycatch":
                FishermanBycatch.markExplained();
                return true;
            case "bycatchExplained":
                return FishermanBycatch.isExplained();
            case "longlinerEncountered":
                return LegendaryChases.wasEncountered(LegendaryShields.POP_SHIELD_SPECIES);

            case "lampStop":
                return openLampStop(dialog);
            case "lampsOff":
                return putLampsOut();
            case "lampRefused":
                return chargeLampStanding(dialog, true);
            case "lampForgive":
                return forgiveLampStop(dialog);
            case "seizeFish":
                return seizeFish(dialog);

            case "crabBarAvailable":
                return CrablobabBarPresence.isAvailable(dialog);
            case "showCrabPortrait":
                return CrablobabIdentity.show(dialog);
            case "crabBuy":
                return buyCrabWare(arg);
            case "crabBuyBass":
                return CrabWares.buyFallbackBass();
            case "crabExplosivePending":
                return CrabWares.hasUnmentionedExplosiveUse();
            case "crabExplosiveSettled":
                return !CrabWares.hasUnmentionedExplosiveUse();
            case "beginCrabOptions":
                return beginCrabOptions(dialog);
            case "addCrabOption":
                return addCrabOption(dialog, params, memoryMap);
            case "crabAcknowledgeExplosive":
                CrabWares.acknowledgeExplosiveUse();
                return true;
            case "crabBuyBackdrop":
                return CrabBackdrops.buy(getMarket(dialog));
            case "crabShowBackdrop":
                return showBackdrop(dialog);

            default:
                return false;
        }
    }

    protected boolean showIntroMap(InteractionDialogAPI dialog, String title) {
        FishingIntro.Target target = FishingIntro.getTarget();
        FishingIntro.IntroIntel intel = new FishingIntro.IntroIntel();

        return QuestDialogMap.showRemote(dialog,
                target == null ? null : target.systemId,
                intel.getMapLocation(null),
                title == null ? "Target" : title,
                intel.getFishermanFaction(), intel.getIcon(), intel.getIntelTags(null));
    }

    protected boolean showWorkMap(InteractionDialogAPI dialog, String title) {
        FishermanQuest.Saved work = FishermanQuest.getActive();
        if (work == null) work = FishermanQuest.getOffer();
        if (work == null) return QuestDialogMap.hide(dialog);

        FishermanQuest.QuestIntel intel = new FishermanQuest.QuestIntel(work);
        return QuestDialogMap.showRemote(dialog, work.systemId, intel.getMapLocation(null),
                title == null ? "Target" : title,
                intel.getFishermanFaction(), intel.getIcon(), intel.getIntelTags(null));
    }

    protected boolean showBackdrop(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        Backdrop scene = CrabBackdrops.getOffer(getMarket(dialog));
        if (scene == null) return false;

        AquariumTankPanel pane = new AquariumTankPanel(null, dialog);
        pane.setPreview(scene);

        dialog.getVisualPanel().showCustomPanel(AquariumTankScript.getPanelWidth(),
                AquariumTankScript.PANEL_HEIGHT, pane);

        return true;
    }

    protected boolean buyCrabWare(String wareName) {
        if (wareName == null) return false;

        try {
            return CrabWares.valueOf(wareName.trim().toUpperCase()).buy();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    protected boolean beginCrabOptions(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getOptionPanel() == null) return false;

        dialog.getOptionPanel().clearOptions();
        return true;
    }

    protected boolean addCrabOption(InteractionDialogAPI dialog, List<Token> params,
                                    Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || dialog.getOptionPanel() == null || params.size() < 3) return false;

        String optionId = params.get(1).getString(memoryMap);
        String label = params.get(2).getString(memoryMap);
        String stock = params.size() > 3 ? params.get(3).getString(memoryMap) : null;
        if (optionId == null || label == null) return false;

        dialog.getOptionPanel().addOption(label, optionId);
        if (stock == null || stock.isEmpty()) return true;

        if ("BACKDROP".equalsIgnoreCase(stock)) {
            Backdrop scene = CrabBackdrops.getOffer(getMarket(dialog));
            if (scene == null) return true;

            addCrabCostTooltip(dialog.getOptionPanel(), optionId,
                    "A rolled aquarium backdrop: " + scene.getDisplayName() + ".",
                    CrabBackdrops.getCredits(scene), CrabBackdrops.getCrabs(scene));
            return true;
        }

        if ("BASS".equalsIgnoreCase(stock)) {
            addCrabCostTooltip(dialog.getOptionPanel(), optionId,
                    CrabWares.getFallbackBassDescription(), CrabWares.FALLBACK_CRAB_CREDITS,
                    CrabWares.FALLBACK_CRAB_CRABS);
            return true;
        }

        try {
            CrabWares ware = CrabWares.valueOf(stock.trim().toUpperCase());
            addCrabCostTooltip(dialog.getOptionPanel(), optionId, ware.description,
                    ware.credits, ware.crabs);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    protected void addCrabCostTooltip(OptionPanelAPI panel, Object optionId, String description,
                                      int credits, int crabs) {
        final String creditText = Misc.getDGSCredits(credits);
        final String crabText = crabs == 1 ? "1 crab" : crabs + " crabs";

        panel.addOptionTooltipAppender(optionId, new OptionPanelAPI.OptionTooltipCreator() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean hadOtherText) {
                tooltip.addPara(description, hadOtherText ? 10f : 0f);
                tooltip.addPara("Cost: %s and %s.", 10f, Misc.getTextColor(),
                        Misc.getHighlightColor(), creditText, crabText);
            }
        });
    }

    protected boolean colorBulkSaleOptions(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getOptionPanel() == null) return false;

        if (FishBuyer.countUpTo(FishRarity.COMMON) > 0) {
            dialog.setOptionColor("catchrelease_fisherSellCommon", FishRarity.COMMON.color);
            FishBuyer.addDescriptionTooltip(dialog, "catchrelease_fisherSellCommon",
                    FishRarity.COMMON);
        }
        if (FishBuyer.countUpTo(FishRarity.UNCOMMON) > 0) {
            dialog.setOptionColor("catchrelease_fisherSellUncommon", FishRarity.UNCOMMON.color);
            FishBuyer.addDescriptionTooltip(dialog, "catchrelease_fisherSellUncommon",
                    FishRarity.UNCOMMON);
        }
        if (FishBuyer.countUpTo(FishRarity.RARE) > 0) {
            dialog.setOptionColor("catchrelease_fisherSellRare", FishRarity.RARE.color);
            FishBuyer.addDescriptionTooltip(dialog, "catchrelease_fisherSellRare",
                    FishRarity.RARE);
        }
        if (FishBuyer.countUpTo(FishRarity.EPIC) > 0) {
            dialog.setOptionColor("catchrelease_fisherSellEpic", FishRarity.EPIC.color);
            FishBuyer.addDescriptionTooltip(dialog, "catchrelease_fisherSellEpic",
                    FishRarity.EPIC);
        }

        return true;
    }

    protected boolean beginFisherQuestions(InteractionDialogAPI dialog,
                                           Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap == null ? null : memoryMap.get("local");
        if (dialog == null || dialog.getOptionPanel() == null || local == null) return false;

        dialog.getOptionPanel().clearOptions();
        local.set(FISHER_ASK_COUNT, 0, 0);
        return true;
    }

    protected boolean addFisherQuestion(InteractionDialogAPI dialog, List<Token> params,
                                        Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap == null ? null : memoryMap.get("local");
        if (dialog == null || dialog.getOptionPanel() == null || local == null
                || params.size() < 4) {
            return false;
        }

        String optionId = params.get(1).getString(memoryMap);
        String label = params.get(2).getString(memoryMap);
        boolean asked = params.get(3).getBoolean(memoryMap);
        if (optionId == null || label == null) return false;

        int index = local.getInt(FISHER_ASK_COUNT);
        int page = Math.max(0, local.getInt(FISHER_ASK_PAGE));
        int first = page * FISHER_ASK_PAGE_SIZE;

        if (index >= first && index < first + FISHER_ASK_PAGE_SIZE) {
            if (asked) {
                dialog.getOptionPanel().addOption(label, optionId, Misc.getGrayColor(), null);
            } else {
                dialog.getOptionPanel().addOption(label, optionId);
            }
        }

        local.set(FISHER_ASK_COUNT, index + 1, 0);
        return true;
    }

    protected boolean finishFisherQuestions(InteractionDialogAPI dialog, List<Token> params,
                                             Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap == null ? null : memoryMap.get("local");
        if (dialog == null || dialog.getOptionPanel() == null || local == null
                || params.size() < 7) {
            return false;
        }

        int page = Math.max(0, local.getInt(FISHER_ASK_PAGE));
        int count = local.getInt(FISHER_ASK_COUNT);

        String previousId = params.get(1).getString(memoryMap);
        String previousLabel = params.get(2).getString(memoryMap);
        String nextId = params.get(3).getString(memoryMap);
        String nextLabel = params.get(4).getString(memoryMap);
        String backId = params.get(5).getString(memoryMap);
        String backLabel = params.get(6).getString(memoryMap);

        if (page > 0) dialog.getOptionPanel().addOption(previousLabel, previousId);
        if (count > (page + 1) * FISHER_ASK_PAGE_SIZE) {
            dialog.getOptionPanel().addOption(nextLabel, nextId);
        }
        dialog.getOptionPanel().addOption(backLabel, backId);
        return true;
    }

    protected boolean highlightJobText(String ruleId, InteractionDialogAPI dialog,
                                       List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (memoryMap == null) return false;

        FishJob job = null;
        for (MemoryAPI memory : memoryMap.values()) {
            if (memory == null) continue;
            Object value = memory.get(FishJob.REF_KEY);
            if (!(value instanceof FishJob)) value = memory.get(CampedSpotJob.REF_KEY);
            if (value instanceof FishJob) {
                job = (FishJob) value;
                break;
            }
        }

        return job != null && highlightQuestText(ruleId, dialog, params, memoryMap,
                job.getAsks());
    }

    protected boolean highlightWorkText(String ruleId, InteractionDialogAPI dialog,
                                        List<Token> params, Map<String, MemoryAPI> memoryMap) {
        FishermanQuest.Saved work = FishermanQuest.getActive();
        if (work == null) work = FishermanQuest.getOffer();
        if (work == null || work.speciesId == null) return false;

        FishRequirement ask = new FishRequirement();
        ask.speciesId = work.speciesId;
        List<FishRequirement> asks = new ArrayList<>();
        asks.add(ask);

        return highlightQuestText(ruleId, dialog, params, memoryMap, asks);
    }

    protected boolean highlightIntroText(String ruleId, InteractionDialogAPI dialog,
                                         List<Token> params, Map<String, MemoryAPI> memoryMap) {
        FishingIntro.Target target = FishingIntro.getTarget();
        if (target == null) return false;

        List<FishRequirement> asks = new ArrayList<>();
        if (!target.anySpecies) {
            for (String speciesId : target.speciesIds) {
                FishRequirement ask = new FishRequirement();
                ask.speciesId = speciesId;
                asks.add(ask);
            }
        }

        return highlightQuestText(ruleId, dialog, params, memoryMap, asks);
    }

    protected boolean highlightQuestText(String ruleId, InteractionDialogAPI dialog,
                                         List<Token> params, Map<String, MemoryAPI> memoryMap,
                                         List<FishRequirement> asks) {
        com.fs.starfarer.api.campaign.TextPanelAPI panel = text(dialog);
        if (panel == null) return false;

        List<String> values = new ArrayList<>();
        for (int i = 1; i < params.size(); i++) {
            String value = params.get(i).getStringWithTokenReplacement(ruleId, dialog, memoryMap);
            if (value != null && !value.isEmpty()) values.add(value);
        }

        List<FishRequirement.RarityHighlight> rarity =
                new ArrayList<>(FishRequirement.getRarityHighlights(asks));
        for (FishRequirement.RarityHighlight entry :
                FishRequirement.getFishNameHighlights(values.toArray(new String[0]))) {
            boolean duplicate = false;
            for (FishRequirement.RarityHighlight existing : rarity) {
                if (existing.text.equals(entry.text)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) rarity.add(entry);
        }

        Map<String, Color> highlights = new LinkedHashMap<>();
        for (String value : values) {
            Map<String, Color> inValue = new LinkedHashMap<>();
            for (FishRequirement.RarityHighlight entry : rarity) {
                if (value.contains(entry.text)) inValue.put(entry.text, entry.rarity.color);
            }

            if (inValue.isEmpty()) {
                highlights.putIfAbsent(value, Misc.getHighlightColor());
                continue;
            }

            Matcher credits = CREDIT_REWARD.matcher(value);
            while (credits.find()) {
                inValue.putIfAbsent(credits.group(), Misc.getHighlightColor());
            }

            List<String> ordered = new ArrayList<>(inValue.keySet());
            ordered.sort((left, right) -> Integer.compare(
                    value.indexOf(left), value.indexOf(right)));
            for (String text : ordered) {
                highlights.putIfAbsent(text, inValue.get(text));
            }
        }
        if (highlights.isEmpty()) return true;

        panel.highlightInLastPara(highlights.keySet().toArray(new String[0]));
        panel.setHighlightColorsInLastPara(highlights.values().toArray(new Color[0]));
        return true;
    }

    protected com.fs.starfarer.api.campaign.TextPanelAPI text(InteractionDialogAPI dialog) {
        return dialog == null ? null : dialog.getTextPanel();
    }

    protected boolean openLampStop(InteractionDialogAPI dialog) {
        CampaignFleetAPI patrol = getOtherFleet(dialog);
        if (patrol == null) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        String factionId = mem.getString(LampPatrolResponse.FACTION_KEY);
        if (factionId == null && patrol.getFaction() != null) factionId = patrol.getFaction().getId();

        // read before record(), which is what moves this faction's ladder in this system on
        int rung = LampOffence.getRung(player, factionId);
        LampOffence.record(player, factionId);

        mem.set(LAMP_CONV, true, 0);
        mem.set(LAMP_RUNG, rung, 1f);
        mem.set(LAMP_FINE, LampOffence.FINE, 1f);
        mem.set(LAMP_FINE_TEXT, Misc.getWithDGS(LampOffence.FINE), 1f);
        mem.set(LAMP_WHERE, LampOffence.getClosestInhabitedName(player), 1f);
        mem.set(LAMP_HAUL, FishBuyer.hasAnything(), 1f);

        if (factionId != null) {
            LampOffence.applyRepLoss(factionId, LampOffence.REP_LOSS, text(dialog));
        }

        if (!SearchlightAbilityPlugin.isBreaching()) {
            text(dialog).addParagraph("The lamps are already dark. The patrol has the earlier "
                    + "sensor return on display and proceeds with the stop.");
        }

        return true;
    }

    protected boolean putLampsOut() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        AbilityPlugin lamps = player.getAbility(SearchlightAbilityPlugin.ABILITY_ID);
        if (lamps == null || !lamps.isActiveOrInProgress()) return false;

        lamps.deactivate();

        return true;
    }

    protected boolean chargeLampStanding(InteractionDialogAPI dialog, boolean refused) {
        CampaignFleetAPI patrol = getOtherFleet(dialog);
        if (patrol == null || patrol.getFaction() == null) return false;

        LampOffence.applyRepLoss(patrol.getFaction().getId(),
                refused ? LampOffence.REP_REFUSE : LampOffence.REP_LOSS, text(dialog));

        return true;
    }

    protected boolean forgiveLampStop(InteractionDialogAPI dialog) {
        CampaignFleetAPI patrol = getOtherFleet(dialog);
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (patrol == null || player == null || patrol.getFaction() == null) return false;

        LampOffence.forgive(player, patrol.getFaction().getId());
        return true;
    }

    protected boolean seizeFish(InteractionDialogAPI dialog) {
        int taken = FishCurrency.seizeAll();
        if (taken <= 0) return false;

        com.fs.starfarer.api.campaign.TextPanelAPI panel = text(dialog);
        if (panel == null) return true;

        panel.setFontSmallInsignia();
        panel.addPara("Lost " + taken + Strings.X + " specimen" + (taken == 1 ? "" : "s"),
                Misc.getNegativeHighlightColor());
        panel.highlightLastInLastPara(taken + Strings.X, Misc.getHighlightColor());
        panel.setFontInsignia();

        return true;
    }

    protected MarketAPI getMarket(InteractionDialogAPI dialog) {
        SectorEntityToken target = dialog == null ? null : dialog.getInteractionTarget();

        return target == null ? null : target.getMarket();
    }

    protected CampaignFleetAPI getOtherFleet(InteractionDialogAPI dialog) {
        SectorEntityToken target = dialog == null ? null : dialog.getInteractionTarget();

        return target instanceof CampaignFleetAPI ? (CampaignFleetAPI) target : null;
    }

    protected void writeTokens(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap.get("local");
        if (local == null) return;

        SectorEntityToken target = dialog == null ? null : dialog.getInteractionTarget();

        FishermanIdentity.preparePortrait(getOtherFleet(dialog));

        local.set(DRIFT, FishermanIdentity.getDialogueBand(FishermanIdentity.getDrift(
                target == null ? null : target.getContainingLocation())), 0);

        int stage = FishingIntro.getStage();
        boolean targetMet = FishingIntro.isTargetMet();
        boolean deepHandoff = FishingIntro.isDeepHandoffPending();

        local.set(STAGE, stage, 0);
        local.set(CARRYING, FishingIntro.isCarryingFisherProperty(), 0);
        local.set(DEEP_HANDOFF, deepHandoff, 0);
        local.set(OUTFITTER, FishingIntro.isAtLeast(FishingIntro.FISH_TWO), 0);
        local.set(CAN_SKIP, FishingIntro.canSkip(), 0);

        String ratingPlanetName = Global.getSector().getMemoryWithoutUpdate()
                .getString(TutorialConstants.RATING_PLANET_NAME_KEY);
        local.set(RATING_PLANET_NAME, ratingPlanetName == null ? "" : ratingPlanetName, 0);
        local.set(RATING_PLANET_KNOWN,
                ratingPlanetName != null && !ratingPlanetName.isBlank(), 0);

        FishingIntro.Target rung = FishingIntro.getTarget();

        local.set(TARGET, FishingIntro.describeTarget(), 0);
        local.set(TARGET_MET, targetMet, 0);
        local.set(TARGET_WHERE, rung == null || rung.systemName == null ? "" : rung.systemName, 0);
        local.set(TARGET_POND, rung != null && rung.atPond, 0);
        local.set(TARGET_DEEP, rung != null && rung.needsDeepGear, 0);
        local.set(TARGET_SET, rung != null, 0);
        local.set(TARGET_PLACED, rung != null && rung.systemName != null, 0);
        boolean targetHere = rung != null && rung.systemId != null && target != null
                && target.getContainingLocation() != null
                && rung.systemId.equals(target.getContainingLocation().getId());

        local.set(TARGET_HERE, targetHere, 0);
        local.set(CONTINUITY_QUESTION_AVAILABLE,
                stage == FishingIntro.FISH_ONE
                        && !targetMet
                        && targetHere
                        && !deepHandoff
                        && !Global.getSector().getMemoryWithoutUpdate()
                        .getBoolean("$catchrelease_fisherAsked_fourJumps"), 0);

        local.set(SHELF, !FishermanShelf.getOffers(target).isEmpty(), 0);
        local.set(HAS_FISH, FishBuyer.hasAnything(), 0);
        local.set(SELL_COMMON, FishBuyer.countUpTo(FishRarity.COMMON) > 0, 0);
        local.set(SELL_UNCOMMON, FishBuyer.countUpTo(FishRarity.UNCOMMON) > 0, 0);
        local.set(SELL_RARE, FishBuyer.countUpTo(FishRarity.RARE) > 0, 0);
        local.set(SELL_EPIC, FishBuyer.countUpTo(FishRarity.EPIC) > 0, 0);
        local.set(RUMOR, FishingIntro.isAtLeast(FishingIntro.DONE)
                && FishRumors.isAvailable(), 0);

        FishRumors.Saved rumor = FishRumors.getActive();
        local.set(RUMOR_SYSTEM, rumor == null ? "" : rumor.systemName, 0);
        local.set(RUMOR_STRANGER, FishRumors.getStrangerDisplayName(rumor), 0);
        local.set(RUMOR_RARITY, rumor != null && rumor.type == FishRumors.TYPE_RARITY, 0);
        local.set(RUMOR_LOOT, rumor != null && rumor.type == FishRumors.TYPE_LOOT, 0);
        local.set(RUMOR_OUTSIDER, rumor != null && rumor.type == FishRumors.TYPE_STRANGER, 0);
        local.set(BYCATCH_PENDING, FishermanBycatch.isPending(), 0);

        Backdrop scene = CrabBackdrops.getOffer(getMarket(dialog));

        local.set(CRAB_ANY, CrabWares.isAnythingLeft() || scene != null, 0);
        local.set(CRAB_EXPLOSIVE_TARGET, CrabWares.getLastExplosiveTarget(), 0);
        local.set(CRAB_CRAB_NAME, CrabWares.getFallbackBassName(), 0);
        local.set(CRAB_CRAB_PRICE, Misc.getDGSCredits(CrabWares.FALLBACK_CRAB_CREDITS), 0);
        local.set(CRAB_CRAB_CRAB_PRICE,
                CrabWares.FALLBACK_CRAB_CRABS == 1
                        ? "1 crab" : CrabWares.FALLBACK_CRAB_CRABS + " crabs", 0);
        local.set(CRAB_CRAB_AFFORD, CrabWares.canAffordFallbackBass(), 0);
        local.set(FISH_WELCOME, !FishingTaboo.isTaboo(getMarket(dialog)), 0);

        local.set(CRAB_BACKDROP, scene != null, 0);
        local.set(CRAB_BACKDROP_NAME, scene == null ? "" : scene.getDisplayName(), 0);
        local.set(CRAB_BACKDROP_PRICE,
                Misc.getDGSCredits(CrabBackdrops.getCredits(scene)), 0);
        local.set(CRAB_BACKDROP_CRABS, CrabBackdrops.getCrabs(scene), 0);
        local.set(CRAB_BACKDROP_AFFORD, CrabBackdrops.canAfford(scene), 0);

        for (CrabWares ware : CrabWares.values()) {
            String key = "$catchreleaseCrab" + Misc.ucFirst(ware.name().toLowerCase());

            local.set(key + "Owned", ware.isOwned(), 0);
            local.set(key + "Afford", ware.canAfford(), 0);
            local.set(key + "Price", Misc.getDGSCredits(ware.credits), 0);
            local.set(key + "Crabs", ware.crabs, 0);
        }

        FishermanQuest.Saved work = FishermanQuest.getActive();

        local.set(WORK, work != null, 0);
        local.set(WORK_AVAILABLE, work == null && FishermanQuest.isAvailable(), 0);
        local.set(WORK_MET, work != null && FishermanQuest.isSatisfied(), 0);

        if (work != null) {
            local.set(WORK_FISH, FishermanQuest.describe(work), 0);
            local.set(WORK_WHERE, work.systemName, 0);
            local.set(WORK_PAY, Misc.getDGSCredits(work.credits), 0);
            local.set(WORK_POND, work.atPond, 0);
        }
    }

    protected boolean leaveEncounter(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        if (dialog.getPlugin() instanceof
                com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl fid) {
            fid.cleanUpBattle();
        }

        dialog.dismiss();

        return true;
    }

    protected boolean bribeHitman(InteractionDialogAPI dialog) {
        if (dialog == null || !(dialog.getInteractionTarget() instanceof CampaignFleetAPI fleet)) {
            return false;
        }

        return HarpoonHitman.acceptBribe(fleet);
    }

    protected boolean rescueCastaway(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        Castaway.rescue(dialog.getInteractionTarget());

        return true;
    }

    protected boolean rememberRatingPlanet(InteractionDialogAPI dialog) {
        MarketAPI market = getMarket(dialog);
        if (market == null || market.getName() == null) return false;

        Global.getSector().getMemoryWithoutUpdate()
                .set(TutorialConstants.RATING_PLANET_NAME_KEY, market.getName());

        return true;
    }

    protected boolean dropCutComm(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getOptionPanel() == null) return false;

        dialog.getOptionPanel().removeOption(
                com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.OptionId.CUT_COMM);

        String[] ruleOptions = {
                "cutCommLink", "cutCommLink2", "cutCommLinkPolite",
                "cutCommLinkNoText", "cutCommLinkNoText2"
        };
        for (String option : ruleOptions) dialog.getOptionPanel().removeOption(option);

        return true;
    }

    protected boolean openPanel(InteractionDialogAPI dialog, Object panel) {
        if (dialog == null) return false;

        behind = dialog.getPlugin();

        if (panel instanceof FishShopDialog shop) {
            panelOpen = true;
            dialog.setPlugin(shop);
            shop.init(dialog);

            return true;
        }

        if (panel instanceof FishermanSurveyDialog counter) {
            panelOpen = true;
            dialog.setPlugin(counter);
            counter.init(dialog);

            return true;
        }

        return false;
    }

    protected void resume(InteractionDialogAPI dialog) {
        if (dialog == null || !panelOpen) return;

        panelOpen = false;

        dialog.setBackgroundDimAmount(
                catchrelease.campaign.fish.fisherman.FishermanConstants.DIALOG_DIM);

        dialog.showTextPanel();
        dialog.showVisualPanel();

        if (behind != null) dialog.setPlugin(behind);

        dialog.getOptionPanel().clearOptions();

        com.fs.starfarer.api.impl.campaign.rulecmd.FireBest.fire(null, dialog,
                behind == null ? null : behind.getMemoryMap(), "CatchReleaseFisherResume");
    }

    protected boolean rollWork(Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap == null ? null : memoryMap.get("local");
        if (local != null) local.set(WORK_ROLLED, false, 0);

        FishermanQuest.Saved offer = FishermanQuest.getOrRollOffer();
        if (offer == null) return false;

        if (local == null) return true;

        local.set(WORK_FISH, FishermanQuest.describe(offer), 0);
        local.set(WORK_WHERE, offer.systemName, 0);
        local.set(WORK_PAY, Misc.getDGSCredits(offer.credits), 0);
        local.set(WORK_POND, offer.atPond, 0);
        local.set(WORK_ROLLED, true, 0);

        return true;
    }

    protected boolean takeWork(InteractionDialogAPI dialog) {
        FishermanQuest.Saved offer = FishermanQuest.getOffer();
        if (offer == null) return false;

        FishermanQuest.accept(offer, text(dialog));

        return true;
    }

    public static FishRarity parseRarity(String name) {
        if (name == null) return null;

        try {
            return FishRarity.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
