# Rules language and project contracts

Read this document for rules syntax, execution, Catch.Release routing contracts and CSV validation. [RULES_WRITING.md](RULES_WRITING.md) explains how to design and structure rules content: the writing process, choosing between plain chains, `FireAll` and `FireBest`, conditions, state, text, options, exits and layout. [RULES_AUTHORING.md](RULES_AUTHORING.md) is the companion implementation guide for using and debugging commands, memory and text replacements, including their Java bindings. All three are technical guides: this one describes the language and project contracts; the writing guide owns structure and process; the authoring guide owns the full command/key integration procedures. The short examples here do not replace those.

[DIALOGUE.md](DIALOGUE.md) governs all player-facing wording, text presentation and dialogue flow, including rules-authored text and options. Use [LORE.md](LORE.md) for fiction and voice. [UI.md](UI.md) covers Java-bound custom UI only; consult it when integrating a Java panel, not as a separate standard for rules dialogue. Repository workflow and required reading are in [CLAUDE.md](../CLAUDE.md#which-guide-to-read).

The engine guidance was adapted from another modder's reference. The detailed [engine workflow](rules/engine_workflow.md) and [command table](rules/command_table.md) are preserved upstream copies. Their simulator-specific instructions describe an external tool, not a requirement to build that tool in Catch.Release. Project constraints are listed below.

Before using, changing or debugging commands or memory/text keys, read the relevant sections of the [Rules implementation guide](RULES_AUTHORING.md). It links the [vanilla command dictionary](rules-reference/COMMANDS.md), [memory/text dictionary](rules-reference/MEMORY.md), and [complete literal-key usage index](rules-reference/KEY_USAGE.md). Look for a vanilla or existing project mechanism before writing a new plugin. Its [source corrections](RULES_AUTHORING.md#corrections-to-the-preserved-simulator-references) govern the listed differences from the preserved simulator guides for the named build; they do not certify unlisted claims. Follow the [technical conflict procedure](../CLAUDE.md#technical-references-and-conflicts) for other discrepancies.

## Rules System (`data/campaign/rules.csv`)

Rule rows drive dialogue, bar events, market interactions and other campaign text. Vanilla file: `starsector-core/data/campaign/rules.csv` (~41k lines). Mod file: `data/campaign/rules.csv` (additive).

For the preserved engine discussion, see [rules/engine_workflow.md](rules/engine_workflow.md), together with the [source corrections](RULES_AUTHORING.md#corrections-to-the-preserved-simulator-references). It is supporting reference material, not a replacement for the project contracts or the implementation guide.

### CSV columns
`id,trigger,conditions,script,text,options,notes`

- **id** — unique rule id. Prefix with `catchrelease_` to avoid collisions with other mods. The loader skips rows with an empty id and rows whose id starts with `#` (comment or disabled rows). It rejects a duplicate id only under the same trigger; the same id under two triggers loads without error, so keep ids unique yourself. Separate rows do not continue the previous rule.
- **trigger** — a bucket that groups rules, not "fires when." The engine fetches all rules for a trigger and filters via conditions. Common triggers:
  - Dialog flow: `OpenInteractionDialog` (default initial trigger of the standard rules dialog), `PopulateOptions`, `DialogOptionSelected`. The simulator guide's `DialogStart` is not fired anywhere in this build.
  - Fleet encounters: `BeginFleetEncounter`, `BeginFleetEncounter2`, `OpenCommLink`
  - Markets/bars: `MarketPostOpen`, `MarketPostDock`, `AddBarEvents`, `BarPrintDesc`, `TradePanelFlavorText`, `RelationshipLevelDesc`
  - Salvage/raids: `BeginSalvage`, custom triggers like `BeatDefendersContinue`
  - Custom mod-defined triggers via `FireAll CatchReleaseFisherResume` from code/script.
- **conditions** — newline-separated predicate expressions. ALL must pass for the rule to match; they are checked top to bottom and matching stops at the first failure. Empty = always matches. See Operators section below. Append `score:N` to a condition line for priority in `getBestMatching`. Lines starting with `#` are comments.
- **script** — newline-separated command invocations in one cell, executed sequentially when the rule fires. Quote the whole CSV cell when it contains multiple lines. Each line is split by `Misc.tokenize`: a token ends at a space or tab outside quotes and at an unescaped `"`, so `AddText"x"` splits the same way as `AddText "x"`; `\` escapes the next character. The first token is a `CommandPlugin` name (resolved across all mods + `api/impl/campaign/rulecmd/*`) unless the line starts with a variable. Names are resolved when `rules.csv` loads: an unknown command, or a Script line using `==`, stops the load with an error (`Rules` loader and expression constructor in `sources-obf/campaign.rules.java`). Quoted arguments preserve spaces; `""` escapes quotes inside CSV. Bare assignment lines (`$var = value`) are valid and common. Every line runs: a command that returns false does not stop the lines after it. Lines starting with `#` are comments. In Conditions and Script, a line that holds only spaces aborts loading the file (`No tokens found`); a truly empty line is skipped.
- **text** — display text shown when the rule fires, added as **one paragraph**: line breaks inside the cell stay inside that paragraph. Supports `$var` substitution at display time. `SetTextHighlights` in the same row's Script applies to this paragraph unless the Script adds another paragraph first. See [Text](RULES_WRITING.md#text).
- **options** — newline-separated option definitions: `order:id:text` or `id:text`. An `id:text` option has order 0. Lower order = displayed higher; equal orders keep collection order. Labels get token replacement; quote characters display as written. The loader splits on every colon: a colon in an `id:text` label fails loading, and in `order:id:text` everything after the third colon is dropped, so write labels without colons. A label that needs a colon is set once the option exists with `SetOptionText <id> "<label>"`, which reads its argument as written (`Token.string`, 0.98a-RC8 `rulecmd/SetOptionText.java`). An option id must not start with `$`. Selecting an option fires `DialogOptionSelected` with `$option == optionId`. FireBest/FireAll collect and add options before ordinary Script execution; prepare option text beforehand. See [display ordering](RULES_AUTHORING.md#create-a-custom-text-token).
- **notes** — free-form comments; ignored by engine.

### Memory scopes
Conditions and commands reference memory through dotted scopes:

| Scope | Meaning | Persistence |
|---|---|---|
| `$global.*` | Sector-wide state (`Global.getSector().getMemory()` in the standard rules dialog, which also refreshes global facts) | Campaign-long |
| `$player.*` | Player character-data memory in the standard rules dialog | Owner persists; individual keys may expire |
| `$market.*` | Current market being interacted with | Per-market |
| `$faction.*` | Faction of the interaction target | Derived at runtime |
| `$entity.*` | Underlying target when an active person occupies local; optional scope | Owner persists; individual keys may expire |
| `$local.*` | Current interaction's selected entity/person memory | Not automatically cleared on dialogue close |

Unqualified `$var` in an expression resolves to **local** memory by default. Text substitution also has generated-token and memory-replacement passes; see [ownership and replacement](RULES_AUTHORING.md#text-replacements-are-not-all-memory-keys). A scope's owner and a key's expiry are separate decisions.

Special interaction key: `$option` is written by the dialog driver into local with zero expiry when an option is selected. In this build, rule `runScript` does not clear it before executing commands. Do not treat it as persistent quest state. The simulator's `$last` and `$optN` claims are not established vanilla APIs; do not use them without an actual producer. See the [source corrections](RULES_AUTHORING.md#corrections-to-the-preserved-simulator-references).

Lifetime: no duration is persistent, including after dialogue closure on a persistent owner. `0` expires on the next advancing memory update, normally when the dialogue closes and the campaign unpauses. Positive durations are campaign-day timers. `set(key, value)` overwrites the value and cancels its previous expiry. Use the [rules/Java lifetime table](RULES_AUTHORING.md#memory-lifetime); never treat zero as permanent or assume local means disposable.
Read from Java: `mem.getBoolean("$myFlag")`, `.getString(...)`, `.getInt(...)`, `.contains(...)`.

### Operators (verified against decompiled source)

| Operator | Symbol | Description |
|---|---|---|
| Equality | `==` | String, Boolean or number comparison |
| Not equal | `!=` | Inequality |
| Less/greater | `<` `>` | Numeric comparison; both sides are parsed as numbers |
| Less/greater or equal | `<=` `>=` | Numeric comparison |
| Not | `!` | `!$flag` passes when the key is unset or not `"true"`; `!Command args` negates a command's result |
| Assignment | `=` | Writes to memory; an optional trailing number is the expiry in days |
| Increment / decrement | `++` `--` | `$count++` adds 1 and stores a Float; an optional trailing number is the expiry |

No other operators exist in this build. The operator characters are `=<>!+-`, and only the ten strings `=`, `!`, `!=`, `==`, `>=`, `<`, `<=`, `>`, `++`, `--` are recognized. The preserved simulator guide also lists `is`, `in`, `is_not`, `is_not_in`, `has`, `does_not_have`, `has_not`, `+=`, `-=`, `*=` and `/=`: the word forms, `+=` and `-=` silently reduce the line to a bare `$var` check, and `*=` and `/=` raise an error when evaluated. There is no OR; use separate rows or a command.

Unset keys in comparisons:

| Expression | Unset `$x` |
|---|---|
| `$x` | fails |
| `!$x` | passes |
| `$x == value` | fails, except for the values below |
| `$x == false`, `$x == 0`, `$x == null` | passes: `$x` counts as 0, and 0 equals `false` and `null` |
| `$x != value` | passes |
| `$x != null` | fails; use it to test that a key is set |
| `$x > 3`, `<`, `<=`, `>=` | `$x` counts as 0 |
| `$x == $y`, both unset | passes |

`$x != false` therefore passes whether `$x` is unset or holds any value other than `false`; it does not test that the key exists (comparison in the rule expression's `isTrueFor`, `sources-obf/campaign.rules.java` bundle lines 1024–1131).

`$x = 5` stores the String `"5"`; `$x = $y` copies whatever object `$y` holds; `$x++` stores a Float. Quote a String with spaces: `$x = "two words" 0`.

Command plugins can also serve as conditions: their Boolean return determines pass/fail. Examples include `PlayerHasCargo supplies 10` and `CheckSetting <booleanSettingId>`. Use the command dictionary for exact class names; `$hasMarket` and `$isPerson` are facts, not plugins named hasMarket or hasPerson. Conditions must not perform acceptance/payment mutations.

### Condition results

A condition passes ONLY when its result is:
- Boolean `true`, or
- A String that equals `"true"` after `.toLowerCase().trim()` — so `"True"`, `"TRUE "`, `" true "` all pass.

`null`, numbers, and every other type **FAIL**.

The CSV loader rejects plain assignment (`=`) in Conditions before a rule can run.
This includes `$local.x = 5` and `$x = true`. Put assignments in Script and comparisons
in Conditions. The loader also rejects equality comparisons (`==`) in Script.

For example, test a value in Conditions:

```text
$local.x == 5
```

Set a value in Script:

```text
$local.x = 5
```

See the [source corrections](RULES_AUTHORING.md#corrections-to-the-preserved-simulator-references)
for the loader and condition evaluator.

### Score mechanics
- Lives on CONDITIONS. Every condition line scores **1** by default. A `score:N` token at the end of a line replaces that line's score with N; it does not add to it.
- A rule's effective score in `getBestMatching` = sum of all its conditions' scores + optional rule-level bonus (effectively 0 for CSV rules). A row with more passing condition lines therefore beats a less specific row; a row with no conditions scores 0.
- Higher wins; exact ties are chosen randomly via `WeightedRandomPicker`, each tied row with weight 1. The draw uses `Math.random()`, or the `Random` that `RulesAPI.setRandomForNextRulePick` set for the next pick only; vanilla sets one only for `HistorianBackstoryBlurb` (`HistorianBarEvent`). Row order decides only which tied row a given draw selects, so moving rows cannot change how often each tied row wins (`Rules.getBestMatching` in `sources-obf/campaign.rules.java` 480–549, `setRandomForNextRulePick` in `sources-obf/campaign.java` 4999–5000, `WeightedRandomPicker.pick` in `sources-api/util.java`).
- Score does NOT affect `getAllMatching` ordering.
- Source: the condition class's score field is initialized to 1 and overwritten only by a `score:` token; `getBestMatching` sums `getScore()` over the passing conditions (`sources-obf/campaign.rules.java` in `starsector-knowledge`: field at bundle line 733, parsing at 790–794, summation at 505–527). The preserved simulator guide's "default 0" claim is wrong; see the [source corrections](RULES_AUTHORING.md#corrections-to-the-preserved-simulator-references).

### Self-skip behavior
The rule that just fired is excluded from the next matching round (`currentRuleId`). This prevents infinite loops when a rule's condition is always true. When the dialog fires its initial trigger (`OpenInteractionDialog` by default), or Java fires a trigger with a null rule id, no rule is skipped.

### FireAll and FireBest

- `FireBest <trigger> [keepOptions]` applies the single best match by the score rules above and returns false when nothing matches. `keepOptions` (a literal or a `$variable`) adds the winner's options to the current ones instead of replacing them.
- `FireAll <trigger>` applies every match: the options of all matches are collected, sorted by order and shown first; then each match's Text and Script run in load order. Scores are ignored. The matches are all chosen before any of their scripts runs (`getAllMatching`, then each rule applied; 0.98a-RC8 `rulecmd/FireAll.java`), so a row's conditions do not see what an earlier row's Script of the same call changed.
- Before matching, `FireAll` writes the requested trigger name to `$fireAllTrigger` (expiry 0, entity memory or local) and fires `FireBest FireAllIntercept`. If a `FireAllIntercept` row matches, it runs instead and the requested trigger's rows never run. Vanilla has one such row, `gaATGkantasDenHostileOverride1`. Any `FireAllIntercept` row must check `$fireAllTrigger`, or it replaces every `FireAll` in the game.
- Either command takes a `$variable` holding the trigger name.
- The option panel is cleared only when at least one option was collected, and for `FireBest` only without `keepOptions`. A trigger with no option rows leaves the old menu on screen.
- Option ids starting with `(dev)` are skipped unless the game runs in dev mode.
- Used in Conditions, either command runs in full (text, options, script) while the engine is still matching rows. Do not use them there.
- A `FireAll` or `FireBest` inside a rule's Script excludes that rule from its own matching round (self-skip), so a row can fire the trigger it belongs to.
- Trigger names are case sensitive. A trigger nothing is registered under matches nothing and reports no error.
- From Java, use the static `FireAll.fire` / `FireBest.fire` helpers; see [Firing rules from Java](#firing-rules-from-java).

### Dialog lifecycle (for writing chains)

1. **Initialization**: The dialog builds its memory map from the target/person and starts its configured trigger. The standard RuleBasedInteractionDialogPluginImpl defaults to `OpenInteractionDialog`; wrappers can use other entry paths. Local is not inherently fresh memory.
2. **Option selection**: The standard driver writes `$option` into local with zero expiry and uses FireBest for `DialogOptionSelected`. The selected rule's text/options and script follow the display ordering above.
3. **Failsafe**: The standard driver adds an error and an explicit exit option when a selection has no matching rule (except its confirmation path). Do not depend on a missing rule to close a conversation safely.
4. **Termination**: Use the explicit exit appropriate to the wrapper. EndConversation, DismissDialog, bar return and fleet teardown are different operations; see [fleet and bar exits](#fleet-and-bar-exits).

### Writing a custom rule command
Extend `BaseCommandPlugin`:

```java
public class MyRuleCMD extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String arg = params.get(0).getString(memoryMap);
        // Use dialog.getTextPanel(), dialog.getInteractionTarget(), memoryMap, etc.
        return true;
    }
}
```

Register the class's package in `ruleCommandPackages`, then invoke its exact simple class name in Script: `MyRuleCMD argA "quoted arg"`. The resolver tries registered packages and caches successful class lookups. Catch.Release uses `dialogue/rules/CatchReleaseCMD.java`, a multi-command dispatcher. Read [custom command integration](RULES_AUTHORING.md#create-a-command-only-when-needed) before adding another class; ordinary mod actions should extend the existing bridge.

### Integration patterns with Java code

**Command-class registration (this mod's pattern):**
Custom commands registered by simple name in the script column: `CatchReleaseCMD tokens`, `CatchReleaseCMD openShop`. The command class handles all logic; rules invoke it declaratively. Catch.Release uses one command bridge; see [Project routing](#project-routing).

**Nexerelin style (memory references):**
`Call $reference <action>` requires an object implementing `CallEvent.CallableEvent`; it delivers action tokens to `callEvent`, not to an arbitrary reflected Java method. Catch.Release uses the BaseHubMission dispatch through `$catchrelease_jobRef`, set by `setPersonMissionRef`/`setEntityMissionRef`. Persistent referenced objects must remain save-compatible. See [Call integration](RULES_AUTHORING.md#reuse-a-mission-object-through-call).

**Quest state:**
Keep quest progress on its Java owner (a hub mission, intel or manager) and let rows ask it through a condition verb or `Call`. Memory holds conversation flags on the speaker, fleet roles and display values. See [State and memory keys](RULES_WRITING.md#state-and-memory-keys).

### Useful built-in commands (subset)
`AddText`, `AddTextSmall`, `Highlight`, `SetTextHighlights`, `SetTextHighlightColors`, `FireAll` / `FireBest`, `Call`, `BeginConversation`, `EndConversation`, `DismissDialog`, `AdjustRep`, `AddCredits`, `AddCommodity`, `SetShortcut`, `ShowDefaultVisual` / `ShowImageVisual`, `DumpMemory` (debug), `MakeOptionOpenCore`, `RemoveOption`.

Use the [vanilla command dictionary](rules-reference/COMMANDS.md) for recipes, exact classes and real call sites. The preserved `rules/command_table.md` is a simulator vocabulary with recorded no-ops, not a comprehensive game command manual. In particular, `Highlight` aliases `SetTextHighlights`; it does not emit a separate paragraph.

### Text features
- `$var` substitution happens at **display time**, not rule definition time.
- The whole Text cell, or the chosen alternative, is one paragraph.
- Multiple text alternatives separated by a line containing only `OR` are chosen at random with equal weight, again every time the row fires. The Text column splits at load time and replaces tokens in the chosen alternative; `AddText` and `AddTextSmall` replace tokens first, then split and trim the chosen alternative. Example:
  ```
  "Option A text."
  OR
  "Option B text."
  ```

### Firing rules from Java

`FireAll.fire(null, dialog, memoryMap, "CatchReleaseFisherResume")` and `FireBest.fire(null, dialog, memoryMap, "catchreleaseJobPaid")` fire a trigger into an open dialog with the active interaction's memory map. Imports and the boundary to `RulesAPI` are in [Command invocation and Java integration](RULES_AUTHORING.md#command-invocation-and-java-integration).

### Library shortcuts
- `MagicLib.MagicBountyIntel` / `MagicBountyCoordinator` — full bounty flow (intel, rule hooks, bar event) from JSON. Use before rolling custom bounty dialogs.
- `LunaLib` Kotlin extensions (`lunalib.lunaExtensions.DialogExtensions`, `MemoryExtensions`) — concise `dialog.addText(...)`, typed memory get/set, `LunaMemory` property delegates.

## Project routing

Code owners are mapped in [ARCHITECTURE.md](ARCHITECTURE.md). Shared text-presentation requirements belong in [DIALOGUE.md](DIALOGUE.md#shared-text-presentation), regardless of the content or its source.

| Entry / state | Contract |
|---|---|
| `CatchReleaseCMD <verb> [arg]` | Sole custom rules command. Queries return whether their requirement is met. The `tokens` verb prepares temporary display values and returns true. Put actions in Script. A handled mission `callAction()` returns true. |
| `$catchrelease_jobRef` | Active FishJob; rows invoke its methods through Call. |
| `$catchrelease_jobDeliver`, `$catchreleaseHasFish` | Valid delivery route; matching cargo available. |
| `$catchreleaseAsk` / `…AskCap`, `$catchreleaseReward` / `…RewardCap` | Shared demand/reward descriptions. |
| `$catchreleaseDays` / `…DaysCap`, `$catchreleaseDaysLeft` | Total deadline; remaining time. |
| `$catchreleasePaid`, `$catchreleaseBonus`, `$catchreleaseMore` | Payout completed; bonus eligible; another round follows. |
| `<missionId>_blurbBar`, `<missionId>_optionBar`, `<missionId>_ask` | Bar description, bar entry, and DialogOptionSelected offer handler. |
| `JobSpecificOptions` | Private bar-job options; accepted contacts use the mission-owned greeting wrapper. |
| `catchreleaseJobAccepted` / `…Declined` / `…Remind` / `…Paid` | Shared job lifecycle triggers. |
| `$catchrelease_fleetQuest` | Fleet-owned Boolean required on external active-job routes. |
| `$catchrelease_fleetQuestType` | Saved type selects the rules-authored dialogue, title, assignment, breadcrumb and intel rows. Non-dialogue consumers use the same rules lookup. |
| `$catchrelease_fleetQuestThanksPending` | Separate gate for completed-job thanks; consumed with its saved text. |
| `CatchReleaseFisherOptions` | Private Fisherman business menu; enter with `$menuState == catchreleaseFisher`. |
| `CatchReleaseFisherResume` | Rebuild after picker/panel cancellation. |
| `CatchReleaseFisherQuestions` | Private Fisherman question menu. Entry and page rows run `CatchReleaseCMD tokens` and `CatchReleaseCMD fisherQuestions` before firing it; `$catchreleaseFisherAskPage` selects the page. Unasked questions have no order, asked ones order 50 in gray, Previous and Next 90, Back 100. Every question row ends with `CatchReleaseCMD fisherAskOnPage`. |
| `CatchReleaseRumorText` | Private Fisherman lead display. `CatchReleaseCMD tokens` prepares `$catchreleaseRumorKind` from the saved rumor before selecting one complete Text row, including combined effects or `none`. Rendering never rolls a new lead; the caller supplies Continue. |
| `CatchReleaseFleetResolutionOptions` | Peaceful fleet result menu with Escape-bound Leave. |

- Namespace IDs and options. Bar option IDs must begin with their mission ID; mission IDs must not prefix one another. `BarCMD` aborts a wrapper whose option prefix differs.
- Private type-specific triggers and Java text lookups are reached only through gated routes. Shared distress entries require `$distressFramework`; the instance verifies the exact fleet and instance reference. The Catch.Release provider also requires its fleet-quest flag.
- Conditions are evaluated before script actions. Prepare generated display tokens on an earlier row: a row cannot display a token its own script has not yet created.
- Use Boolean flags for eligibility. Strings and numbers do not pass a bare-memory condition. Every memory key written through MemoryAPI begins with `$`.
- A bare `score:` line is invalid. Put the score on a real condition. Scores sum, and each condition line counts 1 unless it carries `score:N`, so more conditions do confer priority. Check overlap with unrelated rule families on the same fleet, not just alternatives within one family.
- A harpoon offence may coexist with Fisherman flags. Preserve the Fisherman exclusion on generic harpooned-crew greetings.
- Explicitly fire the intended menu trigger on entry and return; do not rely on the trigger's name to schedule it. Rows with no options may retain an old panel. Check actual rules and driver behavior for the path being edited.
- `$hailing` and `$highlightComms` are consumed while vanilla builds fleet interaction. Do not treat them as lasting quest state.
- Colour an option after it has been added: in the Script of the row that adds it, or of a later row, never earlier. In a `FireAll` every collected option exists before any matched row's Script runs. Retain the shared `highlightJobText` path for job dialogue.

Check displayed highlight occurrences using [DIALOGUE.md](DIALOGUE.md#shared-text-presentation). Java `QuestDialogMap` placement and cleanup are covered by [UI.md](UI.md#intel-and-sidebar-maps).

## Fleet and bar exits

`EndConversation` ends the current person or comm conversation inside the open dialog. In a rules-based dialog it clears the active person and then rebuilds the base menu: a fleet dialog calls reinit, which can fire `BeginFleetEncounter` again; a market or person dialog fires `FireBest MarketPostOpen` when the market has not been docked yet (no `$menuState`), otherwise `FireAll PopulateOptions`. `DO_NOT_FIRE`, or `$doNotFireOnConvEnd` on local memory, skips the rebuild. `NO_CONTINUE` shows the default visual at once and, on a fleet, skips the Continue step before `BeginFleetEncounter`. Without `NO_CONTINUE` the last portrait stays on screen, which is why vanilla's comm-link exits run `ShowDefaultVisual` first. In a dialog whose plugin is not rules based it clears no person and rebuilds no menu. Use it on a fleet only when rebuilding fleet/combat options is the intended result.

`DismissDialog` alone does not clean up a fleet encounter's BattleAPI. Use `CatchReleaseCMD leaveEncounter`, which performs vanilla teardown before dismissal and checks the plugin type. It is also safe for non-fleet interactions.

Bar-event wrappers close with `BarCMD returnFromEvent`, not `close`. It hands the dialog back to the bar, clears the active person and lists the bar events again. `BarCMD returnFromEvent true` instead clears the options and adds Continue (`barContinue`), which lists the bar again when clicked; a finished Java bar event ends the same way unless it sets `noContinue` (`BaseBarEvent.endWithContinue`, `BarEventDialogPlugin.endEvent`). Check confirm, cancel and Escape paths after a custom panel.

`AddBarEvent <id> "<option>" "<blurb>" [<colour>]` queues a blurb and an option on the market's temporary bar event list during `AddBarEvents`; `BarCMD` shows them afterwards, so the row itself has no Options column. The optional fourth argument goes through Token.getColor: `highlight` resolves to the buttonShortcut colour; faction IDs resolve to faction colour. The tutorial spacer's bar event uses this argument. `BarCMD.showOptions` lists these entries before the Java bar events, at most `maxBarEvents` of them, and they take slots from the Java events. The row's conditions run on every visit to the bar, so an entry appears at every market where they pass. Clicking the option stores the `BarCMD` in entity memory as `$BarCMD` (expiry `0`), gives the dialog back to the rules plugin, sets `$option` with expiry `0` and fires `FireBest DialogOptionSelected`; no person is made active, so the handler starts the conversation with `BeginConversation` (0.98a-RC8 `rulecmd/missions/BarCMD.java`).

More of the same source (0.98a-RC8 `BarCMD`, `BarEventDialogPlugin`; `VisualPanel` in `sources-obf/ui.newui.java`):

- A rules entry also takes a slot from `isAlwaysShow()` Java events, which otherwise do not count toward the random number of Java events. It has no frequency, timeout, random pick or tutorial check of its own.
- A click makes a person active only for a hub mission's bar wrapper.
- `returnFromEvent` also aborts the hub missions offered at the bar. Without `true` the list shows at once after "You unobtrusively watch the patrons of the bar...". Listing the bar restores the saved visual, so portraits and `HideVisual` inside the event do not carry over.
- `BeginConversation <id> <minimal> <showRelationship>` calls `showPersonInfo(person, minimal, showRelationship)`. The two-argument `showPersonInfo(person, minimal)` a Java event uses shows the relationship bar only when not minimal and the person's relationship to the player is at least 0.05 either way, so `BeginConversation <id> true false` reproduces `showPersonInfo(person, true)`. `ShowPersonVisual <minimal> [<id>]` calls the two-argument form and does not change the speaker.

## Editing and validation

1. Read the relevant existing rows and required Starsector rules references. Check [the authoring guide and dictionaries](RULES_AUTHORING.md) for the commands and keys being used, changed or debugged, not only new ones. Use vanilla source for uncertain engine behavior, and read-only `lib/` archives for third-party APIs.
2. Prove a byte-identical CSV round-trip before changing parsed rows. Detect the current line endings; do not assume LF or CRLF from an old note.
3. Preserve row IDs, seven columns, quoting, embedded newlines, tokens, commands and ordering except where the technical task explicitly changes them. The loader keeps one list of rows per trigger, in file order (`Rules` loader, `sources-obf/campaign.rules.java` 681–686), and only rows of the same trigger that can match in the same call depend on that order: `FireAll` runs their Text and Script in it and keeps it among options of equal order (a stable `Collections.sort`, `rulecmd/FireAll.java`), so `AddBarEvents` entries are listed and take `maxBarEvents` slots in it; `getAllMatching` readers such as intel bullets and descriptions show it. Before moving rows between blocks, compare each trigger's row sequence before and after, and check every pair whose order changes.
4. Check every affected entry, question loop, accept/decline path, hand-in, cancellation and exit. Include overlapping flags, completed states and save/load. Follow the [dialogue route checklist](DIALOGUE.md#technical-handoff) and [shared text checks](DIALOGUE.md#shared-text-presentation). When a Java custom panel is affected, also use its [UI checks](UI.md#review-the-affected-screen).
5. Parse the edited file again, require seven fields per row, and inspect the diff. A small change must not rewrite unrelated rows. Run the [rules check tool](#rules-check-tool) and fix every error it reports.
6. Report static checks separately from in-game QA. The external Rules Visualizer is not bundled with this repository; use it only if available. Its absence is not a new tooling project or a reason to claim a test was run. DumpMemory can help during live QA.

A round-trip probe for a file that uses LF record endings:

```python
src = open(path, newline='', encoding='utf-8').read()
rows = list(csv.reader(io.StringIO(src)))
out = io.StringIO()
csv.writer(out, lineterminator='\n', quoting=csv.QUOTE_MINIMAL).writerows(rows)
assert out.getvalue() == src
```

If it differs, inspect the source format before editing. The loader strips carriage returns from Conditions, Script and Options, but not from Text: a CRLF inside a Text cell stops `OR` from splitting it. Commas in notes must remain inside a correctly quoted field.

### Rules check tool

`catchrelease.tools.rules.RulesCheck` reads `data/campaign/rules.csv` and reports problems in the mod's rows. It runs outside the game and touches no game class that needs `Global`.

Run it after every change to `rules.csv`: use the `Rules Check` run configuration, or put the build output and the compile jars from [Building](../CLAUDE.md#building) on the class path:

```sh
java -cp "<build output>:<compile jars>" catchrelease.tools.rules.RulesCheck <repository root> [<vanilla rules.csv>]
```

It prints one line per finding, sorted by CSV line, then a summary with counts per check:

```
ERROR data/campaign/rules.csv:120 catchrelease_exCaptainOpt [handler] option catchrelease_exAccept has no DialogOptionSelected row with $option == catchrelease_exAccept
```

The exit status is 1 when there is an error, 0 when there are only warnings or none, and 2 for a usage or input problem.

**Parsing.** Records are read as RFC 4180 CSV; the line number is the physical line where the record starts. Rows with an empty id and rows whose id starts with `#` are skipped, as the loader does. Conditions, Script and Options cells are split into lines the way the 0.98a-RC8 `Rules` loader does, and each Conditions or Script line is parsed with a port of `Misc.tokenize` and the rule expression constructor (`RuleExpression`). Running the tool on vanilla `rules.csv` gives no load, command or option-format error, which matches the game loading that file. The engine's `LoadingUtils` CSV reader is not in the `starsector-knowledge` sources, so the record parsing itself is standard CSV, not a port.

**Checks.** An error is something that stops the file loading or leaves the player stuck; a warning is likely wrong but can be deliberate or caused by keys and triggers that Java builds at run time.

| Check | Severity | Reports |
|---|---|---|
| `columns` | error | A header other than `id,trigger,conditions,script,text,options,notes`, or a row without exactly seven columns |
| `csv` | error | An unterminated quote |
| `empty-id` | warning | A row with content but an empty id, which the loader skips |
| `duplicate-id` | error / warning | An id used twice under the same trigger (the file fails to load) / under different triggers |
| `whitespace` | error | A Conditions, Script or Options line that holds only spaces |
| `load` | error | A line the engine rejects at load: unmatched quotes, several operators, an operator in a command line, a bad `score:`, `=` in Conditions, `==` in Script |
| `command` | error | A command that is not a class in the vanilla `ruleCommandPackages` or the mod's `data/config/settings.json` list. Only the class file is looked up; no class is loaded |
| `option-format` | error | A colon in an option label (load failure for `id:text`, cut label for `order:id:text`), a line that is neither form, an option id starting with `$` |
| `highlight-order` | error | A `SetTextHighlightColors` line that follows `SetTextHighlights` (or `Highlight`) in one Script with only highlight commands between them. `SetTextHighlightColors` calls `highlightInLastPara(color, "")`, which replaces the paragraph's phrases, so the earlier phrases are lost ([Highlights](RULES_WRITING.md#highlights-and-small-text)). Any other command may add a paragraph and ends the pair |
| `text-cr` | warning | A carriage return in Text, which stops `OR` variants from splitting |
| `fire-in-conditions` | error | `FireAll` or `FireBest` in Conditions |
| `fire-target` | error | A literal `FireAll` or `FireBest` target that no mod or vanilla row uses |
| `unreachable` | warning | A trigger with mod rows that nothing fires, reported once at its first row |
| `handler` | error | An option id from the Options column, an `AddBarEvent` call or a `$option = <id>` line without a `DialogOptionSelected` or `NewGameOptionSelected` row testing `$option == <id>`, in the mod or vanilla |
| `case` | warning | A trigger or memory key of a mod row that differs only by case from another trigger or key in the mod, vanilla or the engine list |
| `unwritten` | warning | A `$catchrelease` key read in Conditions, Script, Text or option labels that no mod row writes and that no Java string literal under `jars/src` names |
| `identical` | warning | Two rows on one trigger with the same condition lines in any order, unless both notes contain `variant`. Triggers fired with `FireAll` by a row, by vanilla rows or by the engine are skipped, because every match runs there |

**What counts as fired.** A trigger is fired when a mod row fires it with `FireAll` or `FireBest`; when the engine list in `VanillaRules.ENGINE` names it; when it ends with a hub mission suffix (`_blurb`, `_option`, `_blurbBar`, `_optionBar`, `_startBar`); when vanilla rows use or fire it; or when a Java string literal under `jars/src` equals it.

**Vanilla lists.** `VanillaRules.ENGINE` lists every trigger the 0.98a-RC8 game code fires or opens with a literal name (`FireBest.fire`, `FireAll.fire`, the dialog plugins' `fireBest` and `fireAll`, `getBestMatching`, `RuleBasedInteractionDialogPluginImpl`), each with its bundle file and line in the `starsector-knowledge` sources; triggers vanilla fires from variables, such as defeat triggers, are covered because vanilla rows use them. `VanillaRules.COMMAND_PACKAGES` is vanilla's `ruleCommandPackages`. `docs/rules-reference/vanilla-rules-index.txt` lists the triggers vanilla rows use, the literal `FireAll` and `FireBest` targets in vanilla rows, the option ids vanilla rows handle and the memory keys vanilla rows use. The tool reads it from the repository at run time (`VanillaRules.INDEX`); it sits outside `jars/src` because IntelliJ copies every non-Java file of a source root into `catchrelease.jar`. Regenerate it from a game version's `starsector-core/data/campaign/rules.csv`:

```sh
java -cp "<build output>:<compile jars>" catchrelease.tools.rules.RulesCheck --index <vanilla rules.csv> <game version> > docs/rules-reference/vanilla-rules-index.txt
```

Passing a vanilla `rules.csv` as the second argument of a check builds the same lists from that file instead of the index.

**Limits.**

- Other mods' rule command packages are not known, so rows that call another mod's command get a `command` error.
- A mod row whose id equals a vanilla row id is not reported; the index holds no vanilla ids.
- Keys and triggers that Java builds by concatenation are not found in its string literals. `CatchReleaseCMD` writes the crab ware keys as `key + "Owned"`, `"Afford"` and so on, and the distress framework fires its provider's trigger by name, so those give `unwritten` and `unreachable` warnings.

## Maintenance

Update verified language and project contracts here when their implementation changes. Preserve exact syntax and source evidence. Full command/key integration procedures belong in `RULES_AUTHORING.md`; link there rather than adding a competing procedure here. Record corrections to the preserved simulator references in its [source correction table](RULES_AUTHORING.md#corrections-to-the-preserved-simulator-references), then align affected summaries and links here. Keep upstream reference files under `docs/rules/` unchanged unless intentionally updating the vendored version; simulator behavior described there is not a substitute for a live game check. Follow [CLAUDE.md](../CLAUDE.md#documentation-upkeep) for ownership and commit requirements.
