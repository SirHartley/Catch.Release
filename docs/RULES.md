# Starsector `rules.csv` — Self-Contained Authoring Guide

Everything needed to write, test and debug Starsector dialog rules. No external
lookups required. Distilled from the Fractal Softworks developer post
"You Merely Adopted Rules.csv, I Was Born Into It" (13 Nov 2023) and its companion
sample rule chunk (`bqfs.txt`, the "free supplies from a quartermaster" feature).

Scope note: this is a description of the rules language as evidenced by that post and
sample. Where a detail is uncertain it is flagged as such. Anything not covered here
is resolved by reading the Java command implementations in the game/mod source, or by
dumping memory in game (Section 5.4).

---

# PART I — CONCEPTS

## 1. What the system is

`rules.csv` is the data file behind a small custom scripting language that drives
essentially every interaction in Starsector that is not the combat map or the
campaign map. That includes:

- conversations with NPCs
- interactions with campaign objects: planets, stations, derelicts, sensor arrays,
  jump points
- the bulk of missions and story events

The interaction loop it powers: text appears in a box, the player picks one of the
listed response options, script commands execute, new text and a new option set are
produced, and this repeats until the dialog closes.

The design derives from Elan Ruskin's GDC talk on Valve's rule-based contextual
dialog system. The point of the architecture is workflow: a writer with no ability to
write Java can still ship complex, deeply conditional content by editing a
spreadsheet, and can hot-reload it into a running game in seconds.

## 2. The mental model (read this before writing anything)

The tempting model is "a graph of dialog nodes, one active at a time". Rules *can* be
used that way, and that model will not stop you from shipping something — but it
drastically limits the system and will make the sample content look incomprehensible.

The accurate model:

> A rule is a function, guarded by a condition set, listening on a named trigger.
> When a trigger fires, the engine queries **every** rule registered to that trigger,
> evaluates each one's conditions against current memory, and executes the qualifying
> rules. The dialog state the player sees is the **accumulated side effects of many
> rules that all ran together.**

A single displayed frame is routinely produced by several cooperating rules: one adds
an option, a second disables that option, a third attaches a tooltip to it, a fourth
prints an unrelated line of narration — and all of them ran because the same
`PopulateOptions` trigger fired.

Two consequences worth internalising:

1. Adding a rule never means editing an existing one. New content is additive: write
   a new row with more specific conditions.
2. "Which text shows" is decided by conditions and scoring, not by wiring.

---

# PART II — REFERENCE

## 3. File format

The file is a CSV whose header row is exactly:

```
id,trigger,conditions,script,text,options,notes
```

Format rules that matter in practice:

- **Multi-line cells.** `conditions`, `script`, `text` and `options` routinely contain
  several lines, one statement per line. Any such cell must be wrapped in double
  quotes in the CSV.
- **Escaping quotes.** Inside a quoted cell, a literal double quote is written as two
  double quotes (`""`). Dialog text is full of these because spoken lines are quoted.
- **Comments.** A line starting with `#` is ignored by the parser. Putting `#` at the
  start of the `id` cell comments out the entire rule — the idiomatic way to disable a
  rule without deleting it. Section headers in the file are written the same way,
  e.g. a row whose id is `# BQFS real version`.
- Empty cells are normal and meaningful; most rules use only three or four columns.

### 3.1 Column reference

**`id`** — a unique name for the rule. Must be unique across the whole file (and
across all loaded mods, in practice). Convention: a short project/feature prefix plus
a descriptive camelCase name, e.g. `bqfsAskForSuppliesOptionFirst`. Ids are how you
find things later; be verbose.

**`trigger`** — exactly one string, the event name this rule listens to. Never a list.

**`conditions`** — zero or more condition expressions, one per line. All must pass for
the rule to be eligible. Empty means "always eligible for this trigger".

**`script`** — zero or more commands, one per line, executed in order when the rule
fires. Two kinds of line: memory assignment, and `CommandName arg1 arg2 ...` calls
into registered Java methods.

**`text`** — prose printed into the dialog box when the rule fires. Token substitution
applies (Section 8). Blank for rules that only manipulate options or memory.

**`options`** — player response buttons, one per line, in the form `key:text`.

**`notes`** — free-form comments for humans. Ignored by the engine. Use it; the
conditions column does not have room to explain itself.

## 4. Triggers

A trigger is just a name. Some are conventional and fired by the game engine; you may
also invent your own for internal dispatch.

### 4.1 Engine / convention triggers

- **`PopulateOptions`** — "build the option list for the current dialog state". Fired
  when a player-initiated dialog opens, and, by convention, at the end of nearly every
  rule so control returns to the conversation loop. This is where you register
  options, and also where you disable/annotate options that other rules registered.
- **`DialogOptionSelected`** — fired automatically when the player clicks an option.
  Before it fires, local memory `$option` is set to the clicked option's key.
- **`PickGreeting`** — fired when a conversation is being opened, to choose the
  opening behaviour. The sample uses it to refuse the comm link entirely when a flag
  is set.

Other conventional trigger names exist in the shipped file for other contexts
(salvage, fleet interaction, mission beats). The reliable way to discover them is to
search the shipped `rules.csv` for the situation you want to hook.

### 4.2 Custom triggers

Any name you like. In the sample, `BQFSaskForSuppliesOptCheck` is a private trigger
whose only job is to run a family of "should this option be greyed out, and with what
tooltip?" checks. This is a good pattern: it keeps a single option-creating rule
simple while allowing arbitrarily many mutually exclusive annotation rules.

### 4.3 Firing triggers

```
FireAll SomeTrigger     # run EVERY qualifying rule on that trigger
FireBest SomeTrigger    # run ONLY the single highest-scoring qualifying rule
```

`FireAll` is for accumulation: populate the whole option list, print every applicable
narration. `FireBest` is for selection: choose the one most specific response.

**Critical:** a rule that prints text and then does nothing dead-ends the dialog. The
game will log an error and offer the player an escape, but it is a bug. Every
terminal rule must either call `FireAll PopulateOptions`, offer options of its own, or
deliberately end the conversation (`EndConversation`).

## 5. Memory

Memory is the entire world-state the rule engine reasons over. Keys are prefixed with
`$`. Values are strings, numbers or booleans.

### 5.1 Containers

A dot means container access, conceptually like folders in a filesystem:
`$market.id`, `$player.supplies`, `$faction.isHostile`.

| Container | Holds | Lifetime / scope |
|---|---|---|
| (no prefix) | The memory of whatever you are currently interacting with. While talking to a person, unprefixed keys live on that person. | That one character or object |
| `$entity` | The on-map object being interacted with: a planet, a station, a derelict. | That object |
| `$market` | The market attached to a location. Reachable from both the planet and its orbiting station. | That market |
| `$faction` | The faction that owns the market/location you are at. | Faction-wide |
| `$personFaction` | The faction of the character you are speaking to. Often equals `$faction`, but not always — always check which one you mean. | Faction-wide |
| `$player` | Follows the player everywhere, forever. | Global to the save, player-centric |
| `$global` | Accessible from anywhere. | Global to the save |

Important structural fact: a planet and the station orbiting it are **separate map
entities**, but they usually share one `$market`. So a flag on `$entity` can be missed
by a player who docks at the other one; a flag on `$market` will not be.

Choosing the container **is** the design decision about who remembers:

- unprefixed -> only this quartermaster remembers he helped you
- `$faction.x` -> the entire faction remembers, so flying to another of their worlds
  will not reset it
- `$player.x` -> the memory follows the player regardless of where they go
- `$global.x` -> world-level state

For player-facing dialog `$player` and `$global` behave similarly, since the player
always has access to both; prefer `$player` for things conceptually about the player.

### 5.2 Writing memory

Assignment happens in the `script` cell (single `=`):

```
$flag = true
$player.everAskedForFreeSupplies = true
$faction.playerReceivedCommissionResupply = true 365
$faction.playerReceivedCommissionResupplyOn = $global.daysSinceStart
$option = bqfs_askForSupplies2 0
unset $flag
unset $faction.playerReceivedCommissionResupply
```

The optional trailing integer is an **expiry in game-days**:

- omitted -> permanent (until unset)
- `3` -> expires three game-days later
- `365` -> one Starsector cycle; the standard "long cooldown"
- `0` -> **special**: survives only until the player exits the dialog back to the
  campaign map. It does *not* expire merely from cutting a comm link and reopening it.
  This is the workhorse for "print this only once per visit" bookkeeping and for
  transient dispatch values.

`unset` deletes a memory immediately.

**Ordering rule:** set flags *before* firing downstream triggers. Rules fired
afterwards may need to evaluate conditions against those flags.

### 5.3 Values worth knowing

Observed in the sample and post. Treat this as a starting vocabulary, not an
exhaustive list — always confirm by dumping memory.

```
$isPerson                 boolean, you are talking to a character
$postId                   backend job id of the character, e.g. supplyOfficer
$post                     displayed job title, e.g. Quartermaster
$personName               character's name
$entity.name              e.g. Jangala
$entity.id  $market.id    ids used for location conditions
$faction.id               e.g. hegemony, tritachyon, luddic_church, persean,
                          sindrian_diktat
$faction.isHostile        boolean
$faction.c:offersCommissions   a faction config flag (see c: below)
$player.fcm_faction       id of the faction whose commission the player holds
$player.supplies          count of supplies in cargo
$player.fleetLowCR        boolean, combat readiness degraded
$player.fleetDamaged      boolean
$player.fleetDamagedLots  boolean
$player.numColonies       integer
$global.daysSinceStart    integer, in-game day counter
$global.isDevMode         boolean, dev mode on
$voice                    NPC archetype: soldier, faithful, pather, spacer, aristo,
                          official, business, scientist, villain
```

The `c:` prefix inside a container, as in `$faction.c:offersCommissions`, reads a
config/capability value defined for that faction rather than a runtime memory flag.

Note: there are usable memory keys that do **not** appear in a memory dump, plus
special cases. Absence from the dump is not proof a key is unavailable.

### 5.4 Inspecting memory in game

1. Set dev mode on. It is a boolean in `settings.json`.
2. Open the dialog you want to extend.
3. Choose the `>> (dev) dump memory` option.

This prints every key visible from the current dialog node, as `$key = value` lines.
Keys with dots show their container. Because you are "inside" the character's memory
container while talking to them, an undotted key in the dump belongs to that character
(or has been copied into local context for convenience).

**Always dump memory before writing conditions.** It is the difference between
guessing at key names and knowing them.

## 6. Conditions

One expression per line in the `conditions` cell. **All lines must pass** or the rule
is not eligible. Evaluation is in order, so ordering has performance consequences.

### 6.1 Syntax

```
$isPerson                          # bare key: passes if non-null and not false
$postId == supplyOfficer           # equality is TWO equals signs
$faction.id == luddic_church
$player.fcm_faction != $personFaction.id
$player.supplies < 10
$player.numColonies > 0
!$saidAlreadyGaveSupplies          # ! is logical NOT
$faction.c:offersCommissions       # faction config flag
Commission doesPlayerFleetNeedRepairs      # Java call used as a condition
!Commission doesPlayerFleetNeedRepairs     # negated Java call
```

- `$flag` alone is equivalent to `$flag == true`, and also passes for any non-null,
  non-false value.
- `!$flag` passes when the flag is false **or does not exist**. This is the normal way
  to express "hasn't happened yet".
- Comparison operands may be literals or other memory keys.
- There is no OR operator. Express alternatives as separate rules — that is the
  intended idiom and it composes better with scoring anyway.

### 6.2 Ordering for performance

Put cheap and/or highly restrictive conditions first. The post explicitly recommends
leading with something like `$isPerson` even when it is strictly redundant (because
`$postId == supplyOfficer` already implies it), since it is trivially cheap and prunes
early.

### 6.3 Java calls as conditions

A registered script command that returns a boolean can be used directly as a
condition. Crucially, such a call usually **also writes memory as a side effect**, so
one line both gates the rule and supplies variables the rule's text and tooltips use
later.

The canonical idiom: a mission's `updateData` call populates local memory with
mission-relevant values and returns false if the mission is not active. One call
replaces a check plus a fetch.

The sample uses three custom calls this way, all namespaced `Commission`:

- `recalcFreeSupplyDaysRemaining` — computes and sets `$daysLeft`
- `isCargoPodsScam` — detects the "dump your cargo nearby so you look destitute"
  exploit (adapted from the code patrols use)
- `doesPlayerFleetNeedRepairs` — sets several variables describing how damaged the
  fleet is, so downstream rules can respond proportionally

### 6.4 Scoring

Every condition that **passes** contributes one point to the rule's score. Score is
irrelevant to `FireAll` and decisive for `FireBest`: the highest-scoring qualifying
rule wins.

Because "more conditions" normally means "more specific", this usually does the right
thing automatically. When it does not, override by appending `score:N` to a condition
line:

```
$player.fleetDamaged score:2
$player.numColonies > 0 score:100
```

A `score:100` is the blunt "this branch must always win if it applies" hammer — the
sample uses it so that "you own a planet and you are asking me for handouts?" beats
every other response.

## 7. Options

### 7.1 Declaring options

In the `options` cell, one per line, format `key:text`:

```
bqfs_askForSupplies:"Do you have any supplies to spare?"
bqfs_receiveSupplies:Continue
cutCommLink:Cut the comm link
```

Quotes around the display text are only needed when the text itself is a quoted
spoken line; `Continue` works bare. (Remember the CSV-level doubling of quotes when
the cell is quoted.)

Rules:

- **Keys must be unique** among everything currently populated into the dialog.
- Options appear at the bottom of the box in the order the rules created them, unless
  ordering is explicitly overridden.
- Prefix keys with your feature name to avoid collisions with the base game and other
  mods.

### 7.2 Handling a click

Clicking an option sets local memory `$option` to that key and fires
`DialogOptionSelected`. So the handler is:

```
trigger:    DialogOptionSelected
conditions: $option == bqfs_askForSupplies
```

### 7.3 Routing several buttons to one outcome, and re-dispatch

Since keys must be unique, you cannot give two buttons the same key. Instead, assign
`$option` yourself and re-fire the trigger:

```
$option = bqfs_askForSupplies2 0
FireBest DialogOptionSelected
```

This is functionally identical to the player having clicked an option with that key,
but it executes immediately, so the player sees an instantaneous transition rather
than a new frame. The `0` expiry stops the synthetic value from lingering past the
dialog.

This "print a shared lead-in, then re-dispatch to `FireBest`" pattern is the backbone
of the sample content. It lets one entry point fan out to a dozen mutually exclusive
outcomes (scam detected, fleet critically damaged, fleet lightly damaged, fleet fine,
player owns a colony, wrong faction, hostile) with the scoring system picking the
single best one.

Be aware of the narrative cost: a player reading the raw rules later can see that
their distinct-looking choices funnelled into the same key. The post jokes about
players getting annoyed by exactly this.

### 7.4 Manipulating existing options

These act on an option by key, and must run **after** the option has been created —
i.e. from another `PopulateOptions` rule, or from a custom trigger fired by the rule
that created it.

```
SetEnabled bqfs_askForSupplies false
SetTooltip bqfs_askForSupplies "You must wait another $daysLeft days before ..."
SetTooltipHighlights bqfs_askForSupplies "$daysLeft days" "$faction"
SetTooltipHighlightColors bqfs_askForSupplies hColor $faction.id
SetOptionColor bqfs_devResetCounter gray
RemoveOption bqfs_askForSupplies
```

- `SetEnabled ... false` greys the option out and makes it unselectable but leaves it
  visible. This is deliberate good UX: showing an unavailable option tells the player
  the path exists and that some condition is unmet. Always pair it with a tooltip
  explaining what is missing.
- `SetTooltipHighlights` highlights the **first occurrence** of each given string
  within the tooltip already set on that option — so set the tooltip first. Multiple
  quoted strings may be listed.
- `SetTooltipHighlightColors` assigns colours positionally to those highlights.
  Values seen: a named colour constant (`hColor`, the standard highlight colour) and a
  faction id (`$faction.id`), which resolves to that faction's colour.
- `SetOptionColor` tints the option text itself; `gray` is used for dev-only options.
- `RemoveOption` deletes an option outright — used after a one-shot reward is consumed
  so the button disappears for the rest of the conversation.

Design choice worth noting: greying out with an explanatory tooltip and letting the
player click through to a refusal line are two different UX answers to the same
situation, and implementing the first suppresses the second. Pick one per context
deliberately.

## 8. Text and tokens

The `text` cell is printed into the dialog box. Any `$token` appearing in it is
substituted with the memory value of the same name.

If the key is missing or misspelled, **the raw `$token` is printed to the player**.
That is the cause of the classic "there's a dollar sign in my dialog" bug report; if
you see one, it is a typo or an unset memory key, every time.

This works for ordinary flags too: putting `$gaveFreeSupplies` in text prints `true`
when it is set.

### 8.1 Pronoun and person tokens

Automatically populated for character entities. **Capitalisation of the token controls
capitalisation of the output**, so choose based on sentence position:

```
$heOrShe      $HeOrShe
$hisOrHer     $HisOrHer
$himOrHer     $HimOrHer
$himOrHerself $HimOrHerself
$brotherOrSister        (Luddic flavour)
$personName             the character's name
$post                   the character's displayed job title
```

### 8.2 Player tokens (available in all contexts)

```
$playerName
$playerSirOrMadam       resolves to "sir", "ma'am" or "captain"
$shipOrFleet            singular or plural depending on the player's fleet
```

### 8.3 Faction tokens

```
$faction                the faction name
$theFaction             the faction name with its article, for mid-sentence use
```

### 8.4 Highlighting body text

```
SetTextHighlightColors $faction.id
Highlight $faction
```

**Ordering trap, explicitly flagged in the source material:** for body text the
colours are set **before** the `Highlight` call — the opposite order from the tooltip
commands, where the tooltip must exist first. Getting this backwards silently
produces unhighlighted text.

## 9. Script command reference

Every `script` line is either an assignment (Section 5.2) or `CommandName arg1 arg2
...`, where the command name maps to a Java method registered for rules use and the
remaining tokens are passed as arguments. Arguments containing spaces are
double-quoted.

Commands evidenced in the source material:

| Command | Purpose |
|---|---|
| `FireAll <trigger>` | Run every qualifying rule on the trigger |
| `FireBest <trigger>` | Run only the highest-scoring qualifying rule |
| `AddRemoveCommodity <id> <n> [bool]` | Add (positive n) or remove (negative n) cargo. Third arg controls whether the "you received X" text is auto-inserted; defaults to true, so pass `false` only to suppress it |
| `AdjustRepActivePerson <n>` | Change reputation with the character currently being spoken to |
| `SetEnabled <optKey> <bool>` | Grey out or re-enable an option |
| `SetTooltip <optKey> "text"` | Attach a tooltip to an option |
| `SetTooltipHighlights <optKey> "a" "b"` | Highlight first occurrences within that tooltip |
| `SetTooltipHighlightColors <optKey> c1 c2` | Colours for those highlights, positionally |
| `SetOptionColor <optKey> <color>` | Tint an option's text |
| `RemoveOption <optKey>` | Delete an option |
| `SetTextHighlightColors <color>` | Colour(s) for upcoming body-text highlights |
| `Highlight <text>` | Highlight text in the body copy |
| `ShowDefaultVisual` | Show the default portrait/visual for the context |
| `EndConversation [NO_CONTINUE]` | Close the dialog; `NO_CONTINUE` suppresses the continue prompt |
| `unset <$key>` | Delete a memory value |

Two general truths about commands, both important:

1. If you do not know what an argument does, read the Java method. The post's author
   does exactly this mid-write and discovers an argument was redundant.
2. Commands that return booleans double as conditions, and commands used as
   conditions commonly write memory as a side effect (Section 6.3). Design custom
   commands to do both: check and populate in one call.

### 9.1 Adding your own commands

A custom command is a Java method exposed to the rules parser. Once registered it is
callable from any `script` or `conditions` cell. The recommended shape: return a
boolean indicating "is this applicable / did this succeed", and write any derived
values into memory so that text, tooltips and later conditions can use them
immediately.

---

# PART III — PRACTICE

## 10. Workflow

1. **Author in a spreadsheet.** Multi-line cells and a wide screen let you see a whole
   interconnected cluster of rules at once, which is the main difficulty of this
   format. Real-time collaborative sheets also let a writer and a programmer work the
   same file.
2. **Export as CSV.** In Google Sheets: File > Download > Comma Separated Values.
3. **Launder through a plain text editor.** Open the export in something like Notepad,
   select all, copy, and paste into the master `rules.csv`. This strips formatting
   artefacts that otherwise cause parse disagreements.
4. **If handed a `.txt` rules chunk**, rename it to `.csv` to open it in a CSV editor,
   and drop the header row before merging it into an existing file.
5. **Hot-reload.** The game reloads `rules.csv` while running. Do not restart: cut the
   comm link, reopen the conversation, and the new rules are live. Iterations take
   seconds, so test after every small change.
6. **Use dev mode to reach states.** Reproducing esoteric conditions naturally is
   enormously time-consuming. Write hidden rules gated on `$global.isDevMode` whose
   only job is to set memory values to your test case, and colour them grey so they
   are obviously not real content. The sample ships exactly such a pair of rules.
   (Natural-state testing still matters before release; that is what QA is for.)

## 11. Worked example — build it in this order

Goal: let the player ask a quartermaster for free supplies, with a memory of having
done so.

**Step 1 — add the option.**

```
id:          exAskOption
trigger:     PopulateOptions
conditions:  $isPerson
             $postId == supplyOfficer
options:     ex_ask:"Any supplies to spare?"
```

Cheap condition first. At this point the option appears in game but clicking it
errors, because nothing handles it.

**Step 2 — handle the click.**

```
id:          exAsk
trigger:     DialogOptionSelected
conditions:  $option == ex_ask
script:      AddRemoveCommodity supplies 10
             $gaveFreeSupplies = true 3
             FireAll PopulateOptions
text:        <the quartermaster grudgingly agrees>
```

Note the order: the commodity transfer, then the flag, then the return to the loop —
flags before triggers, always. The `3` means he forgets after three game-days.

**Step 3 — a refusal variant.** Copy the entire row, give it a new unique id
(`exAskDenied`), and add one condition line: `$gaveFreeSupplies`. Replace the script's
commodity transfer with nothing and write refusal text. It now wins whenever the flag
is set, because it has strictly more passing conditions.

Container choice matters here: as written, the flag lives on that one quartermaster,
so flying to another world of the same faction gets you another ten supplies. Change
it to `$faction.gaveFreeSupplies` for faction-wide memory, or
`$player.gaveFreeSupplies` to follow the player everywhere.

**Step 4 — proactive flavour plus a lockout.** Add a third rule on `PopulateOptions`:

```
id:          exAskGaveAlready
trigger:     PopulateOptions
conditions:  $gaveFreeSupplies
             !$saidAlready
script:      $saidAlready = true 0
             SetTooltip ex_ask "$personName remembers giving you supplies. Wait 3 days."
             SetTooltipHighlights ex_ask "3 days"
             SetEnabled ex_ask false
text:        <he greets you with recognition>
```

The `0` expiry means the recognition line prints once per visit to the dialog, not
once per re-opened comm link. The `!$saidAlready` condition is what enforces that.

Now look at what happened: three separate rules, none of which knows about the others,
cooperated to produce one dialog frame containing a greeting, a greyed-out option and
an explanatory tooltip. That is the system working as designed.

## 12. Annotated map of the production sample

The shipped sample (~25 rules) implements "a commissioned captain may request
emergency supplies from a faction quartermaster, once per cycle". It is worth
studying because it demonstrates every technique above in combination. Structure
below; dialog prose is described rather than reproduced.

**Dev scaffolding**

- `bqfsAskForSuppliesOptionDEV` — trigger `PopulateOptions`; conditions
  `$global.isDevMode`, `$postId == supplyOfficer`, `$faction.c:offersCommissions`;
  adds an option coloured grey via `SetOptionColor`.
- `bqfsAskForSuppliesOptionDEV2` — handles that option; `unset`s the cooldown flag and
  calls `FireAll PopulateOptions`. Instant test-state reset.

**Punishment gate**

- `bqfsIgnoringComms` — trigger `PickGreeting`; conditions `$postId == supplyOfficer`,
  `$ignorePlayerCommRequests`; script `ShowDefaultVisual` then
  `EndConversation NO_CONTINUE`. The comm request is simply refused. This is how the
  anti-exploit penalty manifests.

**Entry points (two, mutually exclusive on a first-time flag)**

- `bqfsAskForSuppliesOptionFirst` — `PopulateOptions`; requires the right post, a
  commission-offering faction, and `!$player.everAskedForFreeSupplies`. Adds the ask
  option with no annotations, so a first-time player always gets to click it and
  therefore always learns the rule exists.
- `bqfsAskForSuppliesOptionAgain` — same, but requires
  `$player.everAskedForFreeSupplies`; adds the same option and then calls
  `FireAll BQFSaskForSuppliesOptCheck` to run the annotation pass.

**Annotation pass (custom trigger)**

- `bqfsAskForSuppliesOptAgainCheck` — conditions call
  `Commission recalcFreeSupplyDaysRemaining` (which populates `$daysLeft`), plus the
  cooldown flag and a commission match; disables the option and sets a tooltip
  interpolating `$daysLeft` and `$faction`, with faction-coloured highlights.
- `bqfsAskForSuppliesOptAgainCheck2` — the "you do not hold our commission" variant;
  disables with a different tooltip.

**Refusals on first ask**

- `bqfsAskedForSuppliesFirstNo` — the player has no matching commission; sets
  `$player.everAskedForFreeSupplies`, highlights the faction name in body text
  (colours first, then `Highlight`), returns to `PopulateOptions`.
- `bqfsAskedForSuppliesFirstNoLuddic` — identical but with an extra
  `$faction.id == luddic_church` condition, so it outscores the generic version and
  supplies religiously flavoured refusal text. This is the standard mechanism for
  faction-specific variation: add a rule, add a condition, let scoring pick it.
- `bqfsAskedForSuppliesHostile` — faction is hostile; sets
  `$ignorePlayerCommRequests = true 7` and does **not** set the first-time flag,
  because the player never received the informational payload. That deliberate
  omission is a nice piece of craft.

**Dispatch hub**

- `bqfsAskedForSupplies` / `bqfsAskedForSuppliesB` — a pair differing only by
  `Commission doesPlayerFleetNeedRepairs` versus its negation. Both print the same
  short lead-in beat, set the first-time flag, then
  `$option = bqfs_askForSupplies2 0` and `FireBest DialogOptionSelected`. The Java call
  exists mainly for its memory side effects, which the downstream branches consume.

**Exploit detection**

- `bqfsAskedForSuppliesScam` — conditions: the dispatched key, `$player.fleetLowCR`,
  `$player.supplies < 10`, and `Commission isCargoPodsScam`. Re-dispatches to a scam
  key.
- Five sibling rules (`...Scam2generic`, `...ScamHeg`, `...ScamTT`, `...ScamChurch`,
  `...ScamLeague`, `...ScamSD`) each add one `$faction.id ==` condition to give a
  faction-voiced dressing-down, then re-dispatch to a common exit key.
- `bqfsAskedForSuppliesScamOut` — the shared consequence: `AdjustRepActivePerson -2`,
  set the one-cycle cooldown and its timestamp, set both a local and a permanent
  `$player.attemptedComSupCargoPodScamEver` record, and `$ignorePlayerCommRequests =
  true 7`. Its only option is to cut the comm link.

**Success branches, chosen by `FireBest`**

- `bqfsAskedForSuppliesAcceptA` — low CR, low supplies. Offers a confirm/decline pair,
  because spending a once-per-cycle resource deserves an "are you sure".
- `bqfsAskedForSuppliesAcceptB` — `$player.fleetDamaged score:2`. Single Continue.
- `bqfsAskedForSuppliesAcceptC` — damaged **and** `$player.fleetDamagedLots score:2`,
  so it outscores B. Single Continue, graver text.
- `bqfsAskedForSuppliesAcceptAbort` — handles the decline key, returns to the loop.
- `bqfsAskedForSuppliesDecline` — the fallback with the fewest conditions: fleet is
  fine, so refuse, and `RemoveOption` the ask button for the rest of the conversation.
- `bqfsASkedForSuppliesDeclineColony` — `$player.numColonies > 0 score:100`. The score
  hammer guarantees this beats every other branch. Costs one reputation point.
- `bqfsAskedForSuppliesAccept2` — the actual payout: `AddRemoveCommodity supplies 60`,
  set the 365-day cooldown plus a `$global.daysSinceStart` timestamp,
  `FireAll PopulateOptions`, `RemoveOption`.

**Pattern summary:** one option, two entry rules, a custom annotation trigger, a
lead-in hub that re-dispatches, then a fan of scored, mutually exclusive outcomes
converging on shared consequence rules. Reuse this shape.

## 13. Checklist

- [ ] Every `id` is unique. Every simultaneously-populated option `key` is unique.
- [ ] Ids and option keys carry a feature prefix to avoid mod collisions.
- [ ] Every terminal rule calls `FireAll PopulateOptions`, offers options, or ends the
      conversation. No dead ends.
- [ ] Equality is `==`. A single `=` in a condition is a bug; in a script it is an
      assignment.
- [ ] Memory flags are written **before** downstream triggers fire.
- [ ] Each flag is in the container matching its intended scope (person / entity /
      market / faction / player / global).
- [ ] Expiry values are deliberate. `0` means "until this dialog closes", not
      "immediately".
- [ ] Multi-line cells are quoted; inner quotes are doubled.
- [ ] Tokens are spelled exactly, with the intended capitalisation.
- [ ] Cheap, restrictive conditions are listed first.
- [ ] Body-text highlight colours are set **before** `Highlight`; tooltip highlights
      are set **after** `SetTooltip`.
- [ ] If `FireBest` picks the wrong branch, count passing conditions and add
      `score:N`.
- [ ] Disabled options have tooltips explaining what is missing.
- [ ] Dev-only rules are gated on `$global.isDevMode` and visually marked.

## 14. Symptom-to-cause table

| Symptom | Likely cause |
|---|---|
| A raw `$token` appears in game text | Misspelled key, or the memory is unset in this context |
| Clicking an option errors / dead-ends | No rule with trigger `DialogOptionSelected` and a matching `$option` condition, or the handler never returns to `PopulateOptions` |
| Rule never fires | A condition is false; check with a memory dump. Common culprits: `$faction` vs `$personFaction`, `=` instead of `==`, container prefix omitted |
| Wrong variant chosen | `FireBest` scoring; add `score:N` or more conditions to the intended winner |
| A line repeats every time the box refreshes | Missing a `!$saidX` guard plus `$saidX = true 0` |
| Flag resets when travelling | Flag is on the person or entity; move it to `$faction` or `$player` |
| Tooltip highlights do nothing | `SetTooltipHighlights` ran before `SetTooltip`, or the string does not occur verbatim |
| Body highlight does nothing | `SetTextHighlightColors` ran after `Highlight` instead of before |
| Edits have no effect | The CSV was not laundered through a plain text editor, or the file failed to parse — check the game log |

## 15. Design guidance

- **Assume optimisation.** Anything free will be farmed. The sample's answers: a
  one-cycle cooldown so repetition is not worth planning around; a hard eligibility
  gate (once the player owns a colony, no more handouts — you cannot stuff a colony
  into storage); and explicit code detection of the "dump cargo nearby to look
  destitute" trick, punished with reputation loss and a week of refused comms. When
  designing a giveaway, enumerate the ways a player could manufacture the qualifying
  state, and either detect or price them.
- **Restate known information.** Repeating things the player already knows is often
  good UX even where it would be unnatural in real speech, because dialog is one of
  the few channels for signalling game state.
- **Be consistent about when information appears.** Decide whether a character reacts
  at greeting time or only when asked, and hold to it across the feature.
- **Ration variation.** Beyond factions, each NPC carries a `$voice` archetype
  (soldier, faithful, pather, spacer, aristo, official, business, scientist, villain).
  Writing all nine variants everywhere multiplies the work tenfold for little return;
  reserve voice- and faction-specific lines for high-repetition generic interactions,
  where they break monotony, or where flavour is load-bearing (a Luddic refusal
  framed as a sermon, a commission pitch).
- **The conversation hub is the most useful structure.** One rule populates the hub's
  options; many conditional rules attach to, annotate, or replace them.

## 16. Glossary

- **cycle** — a Starsector year. The standard long cooldown; `365` game-days.
- **CR / combat readiness** — per-ship readiness stat, depleted by deployment and
  restored by supplies. `$player.fleetLowCR` reflects it.
- **commission** — an ongoing contract with a faction; `$player.fcm_faction` holds the
  faction id. Only some factions offer them (`$faction.c:offersCommissions`).
- **market** — the trade/population layer attached to a location, shared by a planet
  and its orbiting station.
- **post / postId** — a character's job. `$postId` is the backend id
  (e.g. `supplyOfficer`); `$post` is the displayed title (e.g. Quartermaster), which
  varies by faction.
- **voice** — an NPC's dialog archetype, used for text variation.
- **dev mode** — a boolean in `settings.json` enabling in-game debug options,
  including the memory dump.
- **hot-reload** — the game re-reads `rules.csv` without restarting.
- **cargo pods scam** — dumping cargo into space near a market so your fleet appears
  destitute when begging; detected and penalised in the sample.

---

## 17. Blank templates

Copy-paste starting points. Header included; drop it if merging.

```
id,trigger,conditions,script,text,options,notes
myFeatureOption,PopulateOptions,"$isPerson
$postId == someOfficer",,,"my_ask:""What I say to them.""",
myFeatureHandler,DialogOptionSelected,"$option == my_ask","$myFlag = true 0
FireAll PopulateOptions","What they say back.",,
myFeatureVariant,DialogOptionSelected,"$option == my_ask
$faction.id == hegemony","$myFlag = true 0
FireAll PopulateOptions","A Hegemony-specific reply.",,
myFeatureAnnotate,PopulateOptions,"$isPerson
$postId == someOfficer
$myFlag","SetTooltip my_ask ""Why this is unavailable.""
SetTooltipHighlights my_ask ""unavailable""
SetEnabled my_ask false",,,# runs after the option-creating rule
```

Disable any rule by prefixing its id with `#`.

---

# APPENDIX — Catch.Release addendum

Not part of the original guide. Everything below was read out of the game sources for
**0.98a-RC8** (the build the `starsector-knowledge` skill carries) after the guide
above was written, and each item either corrects something the guide states or covers
a trap this repo has actually hit. Verify against the sources before trusting any of
it for another build.

**`EndConversation` does not end a fleet encounter.** Section 9 lists it as "close the
dialog", which holds for market and person dialogs. Inside a
`FleetInteractionDialogPluginImpl` it sets `inConversation = false` and then calls
`reinit()` on the plugin — and `reinit` re-fires `BeginFleetEncounter`, so a row that
still matches puts a **Continue** button up that walks the player straight back into
the conversation. To actually leave a fleet encounter from a row, use `DismissDialog`,
which calls `dialog.dismiss()`. See `catchrelease_fisherLeave`.

**`PopulateOptions` is not fired for you after `OpenCommLink`.** The guide says it
fires "by convention, at the end of nearly every rule". The engine does fire it after
a `DialogOptionSelected` row, but *not* after `OpenCommLink` — a comm row that sets
`$menuState` and stops produces a conversation with no options under it. Every row here
that opens a menu from a comm link calls `FireAll PopulateOptions` itself.

**`AddBarEvent` takes an undocumented fourth argument.** `AddBarEvent <id> "<option>"
"<blurb>" [<colour>]`. It goes through `Token.getColor`, so `highlight` resolves to the
settings colour `buttonShortcut` (255,210,0) and a faction id resolves to that
faction's colour. The bar screen reads it back off `BarEventData.optionColor`. Used by
`catchrelease_ratingBarAdd`.

**Line endings in this repo's `rules.csv` are not uniform, and an ordinary text edit
will corrupt them.** The file uses **CRLF at record boundaries only**, with **bare LF
inside quoted fields**. Three consequences, all of which have bitten:

- An editor that rewrites the file normalises every line ending and produces a
  several-hundred-line diff for a one-line change. Edit it as **bytes** — read it
  binary, splice, write binary.
- A `\r` that ends up *inside* a quoted script cell makes the command on that line
  unrecognisable (`ShowDefaultVisual\r`), silently.
- The `notes` column takes prose, so a comma in it turns a 7-column row into 8. Quote
  it.

After any edit, check: `csv.reader` parses, every row has exactly 7 fields, and
`b.count(b'\r') == b.count(b'\r\n')`.

**Where the mod's own conventions live.** [`ARCHITECTURE.md`](ARCHITECTURE.md) has a
"The rules.csv contract" section covering what this mod does on top of the language —
how `CatchReleaseCMD` is the single bridge into Java, the per-job row shapes, and the
tokens Java writes for rows to read.
