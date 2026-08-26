<!--
Master document: the rules language as verified against decompiled engine source.
Supplied by another modder for their own mod and adopted here as the more accurate of
the two guides this repo had. Their project's specifics - id prefixes, command classes,
tool paths - have been repointed at this one; the engine behaviour is theirs and
unaltered. The two reference documents it cites are vendored verbatim under docs/rules/.
The Catch.Release appendix at the end is carried over from the guide this replaced.
-->

## Rules System (`data/campaign/rules.csv`)

The **primary mechanism for in-game interactions** — dialog, flavor text, bar events, market screens, and more are all driven by rule rows. Vanilla file: `starsector-core/data/campaign/rules.csv` (~41k lines). Mod file: `data/campaign/rules.csv` (additive).

For deep engine mechanics verified against decompiled source, see:
[`rules/engine_workflow.md`](rules/engine_workflow.md).

### CSV columns
`id,trigger,conditions,script,text,options,notes`

- **id** — unique rule id. Prefix with something mod-specific - `catchrelease_` here - to avoid collisions. Multiple rows with the same id form one logical rule: first row defines id/trigger/condition; subsequent rows (empty id column) append commands to the script.
- **trigger** — a bucket that groups rules, not "fires when." The engine fetches all rules for a trigger and filters via conditions. Common triggers:
  - Dialog flow: `DialogStart`, `OpenInteractionDialog`, `PopulateOptions`, `DialogOptionSelected`
  - Fleet encounters: `BeginFleetEncounter`, `FleetEncounterResolved`, `OpenCommLink`
  - Markets/bars: `BarPrintDesc`, `BarEncounterOption`, `TradePanelFlavorText`, `RelationshipLevelDesc`
  - Salvage/raids: `BeginSalvage`, custom triggers like `BeatDefendersContinue`
  - Custom mod-defined triggers via `FireAll CatchReleaseFisherResume` from code/script.
- **conditions** — newline-separated predicate expressions. ALL must pass for the rule to match. Empty = always matches. See Operators section below. Append `score:N` to a condition line for priority in `getBestMatching`.
- **script** — newline-separated command invocations executed sequentially when the rule fires. Token before first space is a `CommandPlugin` name (resolved across all mods + `api/impl/campaign/rulecmd/*`). Quoted arguments preserve spaces; `""` escapes quotes inside CSV. Bare assignment lines (`$var = value`) are valid and common (~33% of real script lines).
- **text** — shortcut for dialog display text shown when the rule fires. Supports `$var` substitution at display time. For multiple paragraphs or highlights, prefer `script` with `AddText`/`Highlight`.
- **options** — newline-separated option definitions: `order:id:text` or `id:text`. Lower order = displayed higher. Selecting an option fires `DialogOptionSelected` with `$option == optionId`. Options are batched after script execution, sorted by order, then added to the panel.
- **notes** — free-form comments; ignored by engine.

### Memory scopes
Conditions and commands reference memory through dotted scopes:

| Scope | Meaning | Persistence |
|---|---|---|
| `$global.*` | Sector-wide state (`Global.getSector().getMemoryWithoutUpdate()`) | Campaign-long |
| `$player.*` | Player fleet/character memory | Campaign-long |
| `$market.*` | Current market being interacted with | Per-market |
| `$faction.*` | Faction of the interaction target | Derived at runtime |
| `$entity.*` | Sector entity being interacted with (fleet, planet, derelict) | Derived at runtime |
| `$local.*` | Per-dialog scratch memory | Cleared when dialog closes |

Unqualified `$var` resolves to **local** memory by default.

Special variables:
- `$option` — set by dialog driver when player clicks an option (stored in local). Cleared before each rule's script runs, so the rule cannot see which option led to it firing.
- `$last` — previous option ID (also cleared per-rule).
- `$opt0`, `$opt1`, … — option text labels for display substitution.

Set from Java: `entity.getMemoryWithoutUpdate().set("$myFlag", true, expireDays)`; `expireDays = 0` means never expires.
Read from Java: `mem.getBoolean("$myFlag")`, `.getString(...)`, `.getInt(...)`, `.contains(...)`.

### Operators (verified against decompiled source)

| Operator | Symbol | Description |
|---|---|---|
| Equality | `==` | Value or string comparison |
| Not equal | `!=` | Inequality |
| Less/greater | `<` `>` | Numeric comparison |
| Less/greater or equal | `<=` `>=` | Numeric comparison |
| Identity | `is` | Exact match (same as == for primitives) |
| Contains | `in` | List contains value |
| Not identity | `is_not` | Negated identity |
| Not contains | `is_not_in` | List does not contain value |
| Key exists | `has` | Memory key has a value (right operand ignored) |
| Key absent | `does_not_have` / `has_not` | Memory key does not exist (right operand ignored) |
| Assignment | `=` `+=` `-=` `*=` `/=` | Writes to memory, returns the written value |

Command plugins can also serve as conditions: if their `execute()` returns a boolean, that determines pass/fail. Examples: `hasMarket`, `hasPerson`, `hasFleet`, `isFactionHostile`, custom commands like `NullGateCMD CanBeAdded`.

### Truthiness gate (critical)

A condition passes ONLY when its result is:
- Boolean `true`, or
- A String that equals `"true"` after `.toLowerCase().trim()` — so `"True"`, `"TRUE "`, `" true "` all pass.

`null`, numbers, and every other type **FAIL**. This means assignment conditions like `$local.x = 5` perform the write but the condition fails (returns 5, which is not Boolean/String). Only `$x = true` both writes and passes.

### Score mechanics
- Lives on CONDITIONS: `score:N` token parsed per condition line. Default is **0** (not 1).
- A rule's effective score in `getBestMatching` = sum of all its conditions' scores + optional rule-level bonus (effectively 0 for CSV rules).
- Higher wins; exact ties are chosen randomly via `WeightedRandomPicker`.
- Score does NOT affect `getAllMatching` ordering.

### Self-skip behavior
The rule that just fired is excluded from the next matching round (`currentRuleId`). This prevents infinite loops when a rule's condition is always true. During initial `DialogStart`, no rule is skipped (`currentRuleId = null`).

### Dialog lifecycle (for writing chains)

1. **Initialization**: Fresh local memory created → `DialogStart` trigger fires → first matching rule applied → its text/options displayed.
2. **Option selection**: Player clicks option → `$option` set in local → `DialogOptionSelected` fires with self-skip active → first/best matching rule applied (conditions filter by `$option == ...`) → new options displayed.
3. **Failsafe**: If no rules match, engine tries `TerminateInteraction`, then `FireAll fire("leave")`, then dismisses dialog.
4. **Termination**: Script calls `EndConversation` or `DismissDialog`, no rules match (failsafe), or player uses escape option.

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

Register by **simple class name** in the script column: `MyRuleCMD argA "quoted arg"`. Engine looks up the class on the classpath at startup. This mod ships exactly one: `dialogue/rules/CatchReleaseCMD.java`, a multi-command dispatcher whose first argument selects the branch (`CatchReleaseCMD tokens`, `CatchReleaseCMD giveRod`).

### Integration patterns with Java code

**Command-class registration (this mod's pattern):**
Custom commands registered by simple name in the script column: `CatchReleaseCMD tokens`, `CatchReleaseCMD openShop`. The command class handles all logic; rules invoke it declaratively. Catch.Release deliberately has exactly one such class, so there is one bridge into Java rather than a scatter of them — see the rules.csv contract in [`ARCHITECTURE.md`](ARCHITECTURE.md).

**Nexerelin style (memory references):**
Java objects stored in global memory (`$global.nex_mission_ref`) and called directly from rules: `Call $global.nex_mission_ref updateStage`, or used as conditions: `Call $global.nex_mission_ref hasCores`. This enables complex state machines driven entirely by rules while keeping logic in code. Catch.Release uses this for the jobs: `$catchrelease_jobRef` is set by `setPersonMissionRef`/`setEntityMissionRef` and rows call it as `Call $catchrelease_jobRef <action>`. Objects reached this way must serialize across save/load.

**Rule-driven mission chains:**
For multi-step missions without timers/map markers:
- Store stage in memory: `$missionStage == START`, `== IN_PROGRESS`, etc.
- Each step's rules check the stage, do work, advance the stage flag.
- Spawn subsequent intels from the previous intel's `endMission()` rather than building monolithic multi-stage objects.

### Useful built-in commands (subset)
`AddText`, `AddTextSmall`, `Highlight`, `SetTextHighlights`, `SetTextHighlightColors`, `FireAll` / `FireBest`, `Call`, `BeginConversation`, `EndConversation`, `DismissDialog`, `AdjustRep`, `AddCredits`, `AddCommodity`, `SetShortcut`, `ShowDefaultVisual` / `ShowImageVisual`, `DumpMemory` (debug), `MakeOptionOpenCore`, `RemoveOption`.

Full list: ~200 commands under `api/impl/campaign/rulecmd/`. For comprehensive reference, see [`rules/command_table.md`](rules/command_table.md).

### Text features
- `$var` substitution happens at **display time**, not rule definition time.
- Multiple text alternatives separated by `OR` on separate lines are randomly chosen (Nexerelin pattern; engine supports it). Example:
  ```
  "Option A text."
  OR
  "Option B text."
  ```

### Firing rules from Java
```java
Global.getSector().getRules().fireAll("CatchReleaseFisherResume", dialog);
// or single best match:
Global.getSector().getRules().fireBest("CatchReleaseHarpoonedGreeting", dialog);
```

### Library shortcuts
- `MagicLib.MagicBountyIntel` / `MagicBountyCoordinator` — full bounty flow (intel, rule hooks, bar event) from JSON. Use before rolling custom bounty dialogs.
- `LunaLib` Kotlin extensions (`lunalib.lunaExtensions.DialogExtensions`, `MemoryExtensions`) — concise `dialog.addText(...)`, typed memory get/set, `LunaMemory` property delegates.

### Validation tool: Rules Visualizer

The Rules Visualizer is the other mod's tool and is **not** in this repo - only the two reference documents it was written against, which is why they are vendored under `docs/rules/`. It simulates rule chains offline without game state, traversing dialogs the same way they appear in-game and permutating memory at each step. Key capabilities:

- **Chain traversal**: Follows dialog flows from root rules through option selections, exactly as the engine would.
- **Memory tracking**: Documents memory state snapshots at every step — what was set, changed, or cleared.
- **Reachability analysis**: Identifies unreachable rules (no path from any root reaches them).
- **Condition validation**: Catches incorrect logic early — conditions that never pass, self-skip issues, score conflicts.

Recorded here because the two reference documents describe the engine it models, and because the workflow is worth knowing if the tool is ever brought over:
1. Agent writes rules → 2. Reviewer runs visualizer on the relevant chain → 3. Share traversal output and memory states with agent → 4. Agent iterates based on findings.

This makes ambitious rule-driven features viable — you can debug a multi-step mission chain or branching dialog tree entirely offline before ever launching Starsector.

### Practical tips for working on Catch.Release rules

1. **Read before writing**: Grep the existing rows in `data/campaign/rules.csv`, and the vanilla sheet through the `starsector-knowledge` skill. Match the established style.
2. **Namespace everything**: Prefix ids and option keys with `catchrelease_`. This mod scores its own rows in bands - 1000s for comm greetings, 1200s+ for the harpoon and lamp responses - so a new row has to be placed in that ladder deliberately, not just given a big number.
3. **Prefer extending over duplicating**: Use `FireAll`/`Call` to hook into existing rules rather than rewriting them.
4. **Use libraries before reinventing**: Check LazyLib, MagicLib, LunaLib for utilities - their sources are in `lib/`, and they are the only thing the `starsector-knowledge` skill does not cover.
5. **Validate with the visualizer first**, then verify in-game. The Rules Visualizer catches logic errors offline; in-game testing confirms runtime behavior and integration. Use `DumpMemory` command in rules to inspect state during live play.
6. **When unsure about engine behavior**, consult [`rules/engine_workflow.md`](rules/engine_workflow.md) — it's verified against decompiled source with tracked corrections.

---

# APPENDIX — Catch.Release addendum

Not part of the master document above, and not from the modder who wrote it. Everything
below was read out of the game sources for **0.98a-RC8** (the build the
`starsector-knowledge` skill carries), and each item either refines something the master
says or records a trap this repo has actually paid for. Verify against the sources before
trusting any of it for another build.

**`EndConversation` does not end a fleet encounter.** The lifecycle section above lists
it as a way to terminate, which holds for market and person dialogs. Inside a
`FleetInteractionDialogPluginImpl` it sets `inConversation = false` and then calls
`reinit()` on the plugin — and `reinit` re-fires `BeginFleetEncounter`, so a row that
still matches puts a **Continue** button up that walks the player straight back into
the conversation. To actually leave a fleet encounter from a row, use `DismissDialog`,
which calls `dialog.dismiss()`. See `catchrelease_fisherLeave`.

The reinit is also the tool, not just the trap: a comm row that turns the fleet
hostile and then calls `EndConversation` drops the player back into the fleet
encounter, where the re-fired `BeginFleetEncounter` — blocked for this mod's own
hail row by `$catchrelease_harpoonPatrolDone` and `$isHostile` — lands on vanilla's
hostile greeting and the default hostile fleet actions, with the row's text still
readable in the panel. The `catchrelease_fineDemandRepeat` family and the twice-harpooned crews'
`catchrelease_harpoonedCommsHostile*` family work exactly this way; `DismissDialog`/`leaveEncounter` there would eject the player mid-sentence and
make the hostility look like it only starts on the next contact.

**`DismissDialog` is not enough to leave a fleet encounter.**
`FleetInteractionDialogPluginImpl.init` builds a real `BattleAPI` between the player and the
other fleet on *every* encounter, fight or no fight, and vanilla only takes it apart in its own
`LEAVE` handler, which calls `cleanUpBattle()` *before* dismissing. Close the window without
that and the battle stays attached to both hulls, so the next approach finds
`otherFleet.getBattle() != null`, decides an engagement is already under way, and opens on the
join-battle screen instead of a conversation. Any row that leaves a *fleet* dialog must call
`CatchReleaseCMD leaveEncounter`, which runs vanilla's teardown and then dismisses. Rows that
leave a market, person or custom-entity dialog are unaffected - there is no encounter and no
battle - and the verb checks the plugin type, so it is safe everywhere.

**`PopulateOptions` is not fired for you after `OpenCommLink`.** It is easy to read the
trigger list above as implying the engine keeps the option loop turning by itself. It
does fire after a `DialogOptionSelected` row, but *not* after `OpenCommLink` — a comm row that sets
`$menuState` and stops produces a conversation with no options under it. Every row here
that opens a menu from a comm link calls `FireAll PopulateOptions` itself.

**`AddBarEvent` takes an undocumented fourth argument.** `AddBarEvent <id> "<option>"
"<blurb>" [<colour>]`. It goes through `Token.getColor`, so `highlight` resolves to the
settings colour `buttonShortcut` (255,210,0) and a faction id resolves to that
faction's colour. The bar screen reads it back off `BarEventData.optionColor`. Used by
`catchrelease_ratingBarAdd`.

**Edit `rules.csv` through a CSV round-trip, and prove the round-trip first.** The
file's line endings have changed once already and will change again the moment somebody
opens it in the wrong editor. As of `00b0d1e` it is **pure LF throughout**; before that
it was **CRLF at record boundaries with bare LF inside quoted fields**, and the appendix
said so, which by then was wrong.

So do not hard-code either shape. Read the file, round-trip it unmodified, and compare
bytes:

```python
src  = open(path, newline='', encoding='utf-8').read()
rows = list(csv.reader(io.StringIO(src)))
out  = io.StringIO()
csv.writer(out, lineterminator='\n', quoting=csv.QUOTE_MINIMAL).writerows(rows)
assert out.getvalue() == src      # if this fails, try '\r\n' before touching anything
```

A round-trip that is byte-identical means editing the parsed rows and writing them back
changes only what you changed - a one-line edit stays a one-line diff. A round-trip that
is *not* identical means the file's shape is not what you assumed, and finding out why is
the job before the edit, not after it.

Two hazards that survive whatever the endings are:

- A `\r` that ends up *inside* a quoted script cell makes the command on that line
  unrecognisable (`ShowDefaultVisual\r`), silently.
- The `notes` column takes prose, so a comma in it turns a 7-column row into 8. Let the
  CSV writer do the quoting rather than hand-writing the commas.

After any edit, check: `csv.reader` parses, every row has exactly 7 fields, and the diff
is the size of the change you made.

**Where the mod's own conventions live.** [`ARCHITECTURE.md`](ARCHITECTURE.md) has a
"The rules.csv contract" section covering what this mod does on top of the language —
how `CatchReleaseCMD` is the single bridge into Java, the per-job row shapes, and the
tokens Java writes for rows to read.

**One correction the master brings that is worth saying out loud.** Condition score
defaults to **0**, not 1, and a rule's score is the sum of its conditions' explicit
`score:N` tokens. The guide this replaced said every passing condition contributes a
point, which would mean "more conditions wins" for free. It does not. Rows in this repo
that rely on being outscored — the harpooned-comms ladder, the fisher greetings — carry
explicit scores for exactly that reason, and a new row without one scores zero.

The practical failure mode is not a tie inside one family - those are usually kept apart by
opposed conditions (`$catchreleaseMore` against `!$catchreleaseMore`). It is two *different*
families both matching the same hull. A fishing boat the player had harpooned carried
`$catchrelease_harpooned`, so the harpooned-crew greetings applied to it; the Independent
variant scores 1110 and the boat's own greetings top out at 1100, so one harpoon silently
replaced the shop, the charts, the buyer and the whole introduction with a crew complaining
about a hole, for the thirty days the flag lives. Nothing was wrong with either family on its
own. When adding a family keyed on a flag any hull can carry, check what else can be true of
that hull at the same time - the harpooned rows now all lead with
`!$entity.catchrelease_fisherman`.
