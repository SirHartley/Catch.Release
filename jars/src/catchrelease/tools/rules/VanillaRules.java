package catchrelease.tools.rules;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

// What vanilla fires and handles, so mod rows that join vanilla triggers are not reported. Two sources:
// ENGINE lists the triggers the 0.98.5a-RC7 game code fires with a literal name; the index lists what vanilla
// rules.csv rows use and fire, generated from the vanilla file by "RulesCheck --index" (RULES.md "Rules check tool").
final class VanillaRules {

    // Kept outside jars/src: IntelliJ copies every non-Java file of a source root into catchrelease.jar.
    static final String INDEX = "docs/rules-reference/vanilla-rules-index.txt";

    // Every call in the 0.98.5a-RC7 sources that fires or opens a trigger given as a string literal: FireBest.fire,
    // FireAll.fire, the dialog plugins' fireBest/fireAll, getBestMatching and RuleBasedInteractionDialogPluginImpl.
    // Found by searching starfarer.api.zip and starfarer_obf.jar decompiled with Vineflower 1.11.2; references are
    // file and line under com/fs/starfarer/api, or "obf" and the class under com.fs.starfarer. Triggers fired from
    // a variable (defeat triggers, raid triggers, "<creator>OfferDesc") are vanilla-specific and covered by the
    // index, because vanilla rows use them.
    record EngineTrigger(String trigger, boolean fireAll, String source) {
    }

    static final List<EngineTrigger> ENGINE = List.of(
            new EngineTrigger("OpenInteractionDialog", false, "impl/campaign/RuleBasedInteractionDialogPluginImpl.java:65 default initial trigger"),
            new EngineTrigger("DialogOptionSelected", false, "impl/campaign/RuleBasedInteractionDialogPluginImpl.java:215 optionSelected"),
            new EngineTrigger("NewGameOptionSelected", false, "impl/campaign/NewGameDialogPluginImpl.java:120"),
            new EngineTrigger("BeginNewGameCreation", false, "impl/campaign/NewGameDialogPluginImpl.java:140"),
            new EngineTrigger("BeginNewGameCreationExt", false, "impl/campaign/NewGameDialogPluginImpl.java:138"),
            new EngineTrigger("OpenCDE", false, "obf ui.newui.O0Oo:300 (comm directory entry)"),
            new EngineTrigger("TradePanelFlavorText", false, "obf campaign.ui.E:830 (trade panel)"),
            new EngineTrigger("BeginFleetEncounter", false, "impl/campaign/FleetInteractionDialogPluginImpl.java:273, 296, 401"),
            new EngineTrigger("BeginFleetEncounter2", false, "impl/campaign/FleetInteractionDialogPluginImpl.java:294, 399"),
            new EngineTrigger("OngoingBattleEncounter", false, "impl/campaign/FleetInteractionDialogPluginImpl.java:405 (commented out)"),
            new EngineTrigger("OpenCommLink", false, "impl/campaign/FleetInteractionDialogPluginImpl.java:1340"),
            new EngineTrigger("MarketPostOpen", true, "impl/campaign/AbandonMarketPluginImpl.java:139, impl/campaign/rulecmd/salvage/MarketCMD.java:2980 (FireAll, commented out); impl/campaign/rulecmd/EndConversation.java:54 (FireBest)"),
            new EngineTrigger("PopulateOptions", true, "impl/campaign/rulecmd/salvage/MarketCMD.java:2997, impl/campaign/rulecmd/OpenCoreTab.java:52, impl/campaign/rulecmd/EndConversation.java:56, impl/campaign/missions/hub/BaseMissionHub.java:216"),
            new EngineTrigger("FireAllIntercept", false, "impl/campaign/rulecmd/FireAll.java:75"),
            new EngineTrigger("AddBarEvents", true, "impl/campaign/rulecmd/missions/BarCMD.java:225"),
            new EngineTrigger("ReturnFromBar", false, "impl/campaign/rulecmd/missions/BarCMD.java:454"),
            new EngineTrigger("BarEventFinished", false, "impl/campaign/intel/bar/BarEventDialogPlugin.java:67 (commented out)"),
            new EngineTrigger("BarEventFinishedNoContinue", false, "impl/campaign/intel/bar/BarEventDialogPlugin.java:69 (commented out)"),
            new EngineTrigger("HistorianBackstoryBlurb", false, "impl/campaign/intel/bar/events/historian/HistorianBarEvent.java:259, 264"),
            new EngineTrigger("AddMHCloseOption", false, "impl/campaign/missions/hub/BaseMissionHub.java:257"),
            new EngineTrigger("MHPostMissionListText", false, "impl/campaign/missions/hub/BaseMissionHub.java:266"),
            new EngineTrigger("SOEDuelFinished", false, "impl/campaign/eventide/DuelDialogDelegate.java:75"),
            new EngineTrigger("SOETutorialFinished", false, "impl/campaign/eventide/DuelDialogDelegate.java:77"),
            new EngineTrigger("FoodShortageEndedByPlayerSale", false, "impl/campaign/events/FoodShortageEvent.java:469, 490"),
            new EngineTrigger("PersonnelOfficerPicksMade", false, "impl/campaign/events/OfficerManagerEvent.java:771"),
            new EngineTrigger("CPCBlueprintsPicked", false, "impl/campaign/missions/CustomProductionContract.java:629, 659"),
            new EngineTrigger("SubstrateWeaponsPicked", false, "impl/campaign/rulecmd/DwellerCMD.java:263"),
            new EngineTrigger("SalvageSpecialFinished", false, "impl/campaign/rulecmd/salvage/SalvageSpecialInteraction.java:101"),
            new EngineTrigger("SalvageSpecialFinishedNoContinue", false, "impl/campaign/rulecmd/salvage/SalvageSpecialInteraction.java:103, 152"),
            new EngineTrigger("AICoresTurnedIn", false, "impl/campaign/rulecmd/salvage/AICores.java:165"),
            new EngineTrigger("CargoPodsOptions", true, "impl/campaign/rulecmd/salvage/CargoPods.java:338"),
            new EngineTrigger("CargoPodsOptionsUpdate", true, "impl/campaign/rulecmd/salvage/CargoPods.java:339"),
            new EngineTrigger("CallFleetTargetSelected", false, "impl/campaign/rulecmd/salvage/MiscCMD.java:400"),
            new EngineTrigger("SellToFleetFinished", false, "impl/campaign/rulecmd/salvage/SellToFleet.java:256"),
            new EngineTrigger("PostGroundRaid", false, "impl/campaign/rulecmd/salvage/MarketCMD.java:2179"),
            new EngineTrigger("BeatDefendersContinue", false, "impl/campaign/rulecmd/salvage/SalvageDefenderInteraction.java:115"),
            new EngineTrigger("WormholeDeploymentFinished", false, "impl/campaign/rulecmd/salvage/Objectives.java:722"),
            new EngineTrigger("PerformSalvage", false, "impl/campaign/rulecmd/salvage/SalvageEntity.java:289, 290 (commented out)"),
            new EngineTrigger("ZGRItemsTurnedIn", false, "impl/campaign/rulecmd/salvage/ZGRTurnIn.java:159"),
            new EngineTrigger("ShipRecoveryCustomText", false, "impl/campaign/rulecmd/salvage/special/ShipRecoverySpecial.java:296"),
            new EngineTrigger("PostShipRecoverySpecial", true, "impl/campaign/rulecmd/salvage/special/ShipRecoverySpecial.java:711"),
            new EngineTrigger("AfterZigguratDefeat", false, "impl/campaign/world/TTBlackSite.java:176 dialog"),
            new EngineTrigger("SD7SQarasAgentDefeated", false, "impl/campaign/missions/askonia/SDSevenSons.java:1581 dialog"),
            new EngineTrigger("SevenSonsAndradaFileBegin", false, "impl/campaign/intel/misc/SevenSonsIntel.java:485, impl/campaign/missions/askonia/SDSevenSons.java:325 dialog"),
            new EngineTrigger("SevenSonsAndradaDecryptBegin", false, "impl/campaign/intel/misc/SevenSonsIntel.java:498, impl/campaign/missions/askonia/SDSevenSons.java:335 dialog"),
            new EngineTrigger("ShroudedSubstrateRightClick", false, "campaign/impl/items/ShroudedSubstratePlugin.java:94 dialog"),
            new EngineTrigger("RemoveAICoreAdmin", false, "obf campaign.command.AdminPickerDialog:722 dialog"),
            new EngineTrigger("OutpostEstablished", false, "obf campaign.ui.marketinfo.PlanetSurveyPanel:561 dialog"));

    // Hub missions fire "<missionId>_blurb" and "_option" (BaseMissionHub.java:247, 251), "_blurbBar" and
    // "_optionBar" (HubMissionBarEventWrapper.java:107, 108) and "_startBar" (HubMissionWithBarEvent.java:34,
    // commented out). The mission id is not known here, so the suffix is accepted.
    static final List<String> ENGINE_SUFFIXES = List.of("_blurb", "_option", "_blurbBar", "_optionBar", "_startBar");

    // ruleCommandPackages in starsector-core data/config/settings.json (line 1303).
    static final List<String> COMMAND_PACKAGES = List.of(
            "com.fs.starfarer.api.impl.campaign.rulecmd",
            "com.fs.starfarer.api.impl.campaign.rulecmd.salvage",
            "com.fs.starfarer.api.impl.campaign.rulecmd.newgame",
            "com.fs.starfarer.api.impl.campaign.rulecmd.missions",
            "com.fs.starfarer.api.impl.campaign.rulecmd.academy");

    // Triggers whose rows option clicks select; PaginatedOptions fires one or the other (PaginatedOptions.java:154).
    static final Set<String> OPTION_TRIGGERS = Set.of("DialogOptionSelected", "NewGameOptionSelected");

    private static final String[] SECTIONS = {"triggers", "fireAll", "fireBest", "options", "keys"};

    // triggers: triggers of vanilla rows. fireAll, fireBest: literal targets of those commands in vanilla rows.
    // options: option ids with a vanilla "$option == <id>" handler. keys: memory keys in Conditions and Script.
    final Map<String, Set<String>> sets = new TreeMap<>();

    private VanillaRules() {
        for (String section : SECTIONS) {
            sets.put(section, new TreeSet<>());
        }
    }

    Set<String> triggers() {
        return sets.get("triggers");
    }

    Set<String> fireAll() {
        return sets.get("fireAll");
    }

    Set<String> fireBest() {
        return sets.get("fireBest");
    }

    Set<String> options() {
        return sets.get("options");
    }

    Set<String> keys() {
        return sets.get("keys");
    }

    static boolean engineFires(String trigger) {
        for (EngineTrigger engine : ENGINE) {
            if (engine.trigger.equals(trigger)) return true;
        }
        for (String suffix : ENGINE_SUFFIXES) {
            if (trigger.endsWith(suffix) && trigger.length() > suffix.length()) return true;
        }
        return false;
    }

    static boolean engineFiresAll(String trigger) {
        for (EngineTrigger engine : ENGINE) {
            if (engine.fireAll && engine.trigger.equals(trigger)) return true;
        }
        return false;
    }

    static VanillaRules fromIndex(Path index) throws IOException {
        VanillaRules rules = new VanillaRules();
        Set<String> section = null;
        for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = rules.sets.get(line.substring(1, line.length() - 1));
                if (section == null) throw new IOException("unknown section " + line + " in " + index);
            } else if (section == null) {
                throw new IOException("entry before the first section in " + index);
            } else {
                section.add(line);
            }
        }
        return rules;
    }

    static VanillaRules fromCsv(Path csv) throws IOException {
        RulesFile file = RulesFile.read(csv);
        if (file.error != null) throw new IOException(csv + ": " + file.error);
        VanillaRules rules = new VanillaRules();
        for (RulesFile.Record record : file.records.subList(1, file.records.size())) {
            if (record.fields().size() != RulesFile.HEADER.size()) continue;
            RulesFile.Row row = RulesFile.Row.of(record);
            if (!row.isLoaded()) continue;
            rules.triggers().add(row.trigger());
            for (String line : RulesFile.lines(row.conditions())) {
                RuleExpression e = RuleExpression.parse(line);
                rules.addUses(e);
                if (OPTION_TRIGGERS.contains(row.trigger()) && isOptionTest(e)) {
                    rules.options().add(e.second.text());
                }
            }
            for (String line : RulesFile.lines(row.script())) {
                rules.addUses(RuleExpression.parse(line));
            }
        }
        return rules;
    }

    static boolean isOptionTest(RuleExpression e) {
        return e.error == null && e.operator == RuleExpression.Operator.EQUAL && e.first != null
                && e.first.text().equals("$option") && e.second != null && !e.second.isVariable();
    }

    private void addUses(RuleExpression e) {
        if (e.error != null) return;
        if ((e.isCommand("FireAll") || e.isCommand("FireBest")) && !e.params.isEmpty() && !e.params.get(0).isVariable()) {
            (e.isCommand("FireAll") ? fireAll() : fireBest()).add(e.params.get(0).text());
        }
        for (RuleExpression.Token token : tokens(e)) {
            if (token.isVariable()) keys().add(token.key());
        }
    }

    static List<RuleExpression.Token> tokens(RuleExpression e) {
        List<RuleExpression.Token> tokens = new java.util.ArrayList<>(e.params);
        if (e.first != null) tokens.add(e.first);
        if (e.second != null) tokens.add(e.second);
        return tokens;
    }

    void write(PrintStream out, String gameVersion) {
        out.println("# Vanilla rules index for catchrelease.tools.rules.RulesCheck.");
        out.println("# Game version " + gameVersion + ". Generated from starsector-core/data/campaign/rules.csv with");
        out.println("#   java -cp \"<build output>:<compile jars>\" catchrelease.tools.rules.RulesCheck --index <vanilla rules.csv> " + gameVersion);
        out.println("# Regenerate it for a new game version; do not edit it by hand.");
        for (String section : SECTIONS) {
            out.println();
            out.println("[" + section + "]");
            for (String entry : sets.get(section)) {
                if (!entry.isBlank()) out.println(entry);
            }
        }
    }
}
