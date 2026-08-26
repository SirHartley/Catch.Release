# Catch.Release — file and feature map

What is where, and which file to open first. 282 Java files across twelve top-level packages, plus
the data tables that register them.

Kept by hand, and updated by every change — not only when a package gains or loses a file, but
whenever what a file does, what registers it, or how the pieces fit moves. The update belongs in
the same commit as the change. A map that is wrong is worse than no map, because it is believed.

`CLAUDE.md` owns the comment, documentation, and Java class-layout policy. Source comments
stay sparse: keep engine quirks, save compatibility, and non-obvious math or lifecycle
constraints; names, reader-first member order, and responsibility-based field groups carry the
rest. This map records the current shape, not change history.

Not mapped below, because none of it is ours: `lib/` holds the game's API source and the three
dependency mods, zipped, to be read rather than edited.

**Repository boundary:** implementation writes happen only in the current task checkout. ChatGPT
uses local Git for source work and the connected GitHub app for pull requests and merges. Live mod
installs and other checkouts are read-only audit sources unless the user explicitly asks for a write
or synchronization; merging a PR never authorizes a post-merge sync. This boundary is defined in
`CLAUDE.md`.

**[`LORE.md`](LORE.md) is the setting and writing authority** — what a breach, a pattern, the ROD,
the Fisherman and Crablobab actually are; who notices contradictions; who is allowed to know what;
the tutorial's information-release order; faction voices; terminology; and the prose rules every
player-facing line follows. Read it before writing `rules.csv`, a species description, a tackle
blurb, an intel note, colony text or UI copy. It also carries the settled unknowns the fiction must
not explain.

## Build layout

The IntelliJ module compiles only `jars/src` into `out/production/catchrelease`; the artifact
packages that module output as `jars/catchrelease.jar`. Compiler output must stay outside `jars/`:
making the whole directory a source root recursively packages the artifact's own JAR and stale
`production/` classes, allowing new data such as rules rows to run against old mission bytecode.

Runtime-affecting branches are not merge-ready until the full clean Java 17 compile gate in
`CLAUDE.md` passes against the exact remote branch. Documentation-only changes are exempt.

---

## Where a feature lives

| If you are changing… | Start in |
|---|---|
| The pond itself — look, open/close, mote spawning | `campaign/ponds/terrain/MaskedFishingPondTerrainPlugin.java` |
| Where ponds appear in a system | `campaign/ponds/listener/PondCreator.java` |
| The catch minigame's rules | `campaign/fish/minigame/FishingMinigame.java` (no rendering in it) |
| The catch minigame's look or input | `campaign/fish/minigame/FishingMinigamePanel.java` |
| What a fish *is* | `campaign/fish/data/FishSpec.java` + `data/campaign/fish.csv` |
| Where a fish lives | `campaign/fish/data/FishHabitat.java` — one question, one answer |
| A caught specimen's stats and grading | `campaign/fish/data/FishCatch.java` |
| Bar jobs | `campaign/fish/jobs/` — `FishJob.java` is the spine |
| Fleet-given jobs | `campaign/fish/jobs/fleet/` |
| The shop | `campaign/fish/shop/FishShopDialog.java` |
| Anything anybody **says** | `data/campaign/rules.csv` — all of it, without exception |
| What the sheet can *do* | `dialogue/rules/CatchReleaseCMD.java` |
| Upgrades | `memory/upgrades/` + `data/config/UpgradeData.csv` |
| Tackle modules | `campaign/fish/tackle/Tackle.java` |
| An ability's behaviour | `abilities/<name>/ability/` |
| An ability's tuning | `abilities/<name>/constants/` |
| Aiming and reticules | `skillshot/` (has its own README) |
| Distress-call scheduling and third-party event hooks | `distress/` (has its own README) |
| A pane, widget or list row on any screen but the minigame | `ui/` — the shared component framework |
| Shaders and GL helpers | `rendering/` + `data/catchrelease/shaders/` |
| The sector-map fish filter | `campaign/fish/map/` |
| The low-coherence screen overlay | `campaign/fish/coherence/CoherenceOverlayScript.java` |
| The fishing boats, standing and visiting | `campaign/fish/fisherman/` |
| How anybody starts fishing | `campaign/fish/tutorial/` |
| The colony structure and its aquarium | `campaign/fish/colony/` |
| Consequences of harpooning a fleet, or of running the lamps over one | `campaign/crime/` |
| Anything that must survive a save | `memory/` |

---

## Boot order

Everything game-facing is wired from `ModPlugin.java`.

`onCodexDataGenerated()`
1. `FishCodex.install()` — the only moment the codex category can be added; it is generated once at
   load and never rebuilt.

`onGameLoad(newGame)`
1. `OnJumpPondSpawner.register()` — ponds appear as the player jumps into systems
2. `BuriedMoteSpawner.register()` — maintains the buried-mote population near the player
3. `ChargeManager.register()` — regenerating charge pools for the charged abilities
4. `CatchReleaseCampaignPlugin.register()` — hands harpooned fleets their custom encounter screen;
   the fishing boats' conversations are rules rows and need no plugin
5. `HarpoonPatrolResponse.register()` and `LampPatrolResponse.register()` — the crime responses: a
   patrol after an outstanding harpooning, and the stop over lit lamps
6. `FleetQuestSpawner.register()` — the five local scavenger offers; the stranded and dead-engine
   jobs enter through the distress framework instead
7. `FishermanSpawner.register()` — the daily roll for the visiting fishing boat
8. `CoreFisherSpawner.register()` — one standing boat to every inhabited system
9. `FishermanQuest.Keeper.register()` — keeps a chart request's specimen in the water
10. `TutorialWreck.Watcher.register()`, `RatingBarEvent.VisitCounter.register()`,
   `FishermanInterception.register()`, `FishingIntro.Keeper.register()` — the introduction's
   hooks and its errand keeper; the castaway's planet scene needs no watcher, the sheet takes
   eligible planets over itself
11. `ConservatoryOptionProvider.register()` — the conservatory's options on the colony screen
12. `AquariumTankScript.register()` — the aquarium on the colony's main menu
13. `Aberration.Watcher.register()` — the aberration index fills on arriving somewhere and on the
   sector map opening; this is its only registration site
14. `FishRanges.register()` — the month-end range reassessor, plus one immediate assessment on a
   save that has never had one, so a fresh sector's homeless species are re-homed before the first
   cast rather than a month in
15. `LegendaryHaunt.register()` — the whaling chase's stage manager as a transient script, plus a
   tag sweep that removes any haunt leftovers a hard exit stranded in a save
16. `LonglinerDecoy.register()` — the Longliner's boat keeper as a transient script, plus a
   sweep that despawns any decoy boat whose booking an old save lost
17. `UpgradeManager.getInstance().updateBaseValues()` — re-reads the upgrade sheet into the save:
   stats missing from the save are seeded and every sheet-owned field of a held stat is refreshed,
   so a save carries levels and nothing else that matters
18. `CatchReleaseDistressProvider.register()` then `DistressCallFramework.register()` — the two
   fleet-job adapters, merged distress-call registry and its persistent, idempotent coordinator,
   bound to vanilla's live nearby-event cadence
19. `SkillshotFramework.register()` — the aiming framework
20. `FishMapFilterScript` as a transient script — the sector-map filter
21. `FishIntelPlanetPanel` as a transient script — the intel Planets view's fish panel
22. `CoherenceOverlayScript` as a transient script — the low-coherence screen overlay
23. `BlackHoleSpiralWarp.install()` — the portable circular post-process, registered transiently
   and fed every black-hole star in the current system by its small Catch.Release adapter
24. `sweepPondClaims()` then `FishLog.relockLegendaryRangeData()` — one walk, taking the mission marker off every rupture no errand is holding
   any more and fading every planted specimen no errand is still waiting on; repairs saves
   carrying either, since transitions cannot
25. `DevShortcut.register()` — the J key, as a transient `CampaignInputListener`; successive presses grant the testing loadout, every backdrop, then every outfitter schematic; inert unless dev mode is on

`beforeGameSave()` — `SkillshotFramework.reset()`.

Registration is idempotent: the `register()` methods unregister by id first, and transient scripts
are rebuilt every load because their state lives in sector memory rather than in fields.

---

## Registered by data, not by code

Classes the game instantiates by name. Grep the data file, not the call sites — there aren't any.

**`data/campaign/abilities.csv`** — 4 rows. Three live fishing abilities are
`unlockedAtStart=FALSE` and granted by `FishingIntro`; the fourth is the skillshot framework's
hidden example. The old outfitter migration stub is gone — development assumes a new game.

| Id | Class |
|---|---|
| `catchrelease_searchlights` | `abilities/searchlight/ability/SearchlightAbilityPlugin` |
| `catchrelease_rod` | `abilities/rod/ability/PondInteractionAbilityPlugin` |
| `catchrelease_harpoon` | `abilities/harpoon/ability/HarpoonAbilityPlugin` |
| `skillshot_example` | `skillshot/example/ExampleSkillshotAbility` |

**`data/config/settings.json`** — `ruleCommandPackages`, listing only
`catchrelease.dialogue.rules`: settings arrays merge across mods, so the mod's entry adds to
vanilla's five packages rather than replacing them.
The `graphics.characters` additions also register all five Fisherman coherence portraits with the
settings texture loader; `FishermanIdentity` resolves those ids rather than handing the portrait UI
an unloaded raw path. The `graphics.catchrelease` additions likewise preload the tutorial wreck interaction illustration and the dedicated
`fisherman_map_icon` and `unstable_fabric_map_icon` glyphs used by the boat marker and live pond
terrain, the three source-specific fishing-intel icons resolved by `FishIntelIcon`, plus the four pane category marks consumed through `FishType`. Seven dedicated outfitter
marks feed the main kinds and rig shelves through `ShopEntry.Kind` and `ShopGroup`; Catch and
Extras deliberately reuse the pane fish and miscellaneous marks.
`catchreleaseBlackHoleSpiralWarpRange` is the portable black-hole pass's
world-space radius and defaults to `6000`; setting it to zero disables the draw without removing
the renderer. Minigame sound ids and the click press/release toggle are Java constants, not settings.

**`data/campaign/bar_events.csv`** — 14 jobs, all `FishJob`s: 11 in `campaign/fish/jobs`, plus the
three camp events in `campaign/fish/jobs/camp`, whose shared base `CampedSpotJob` extends `FishJob`
like the rest. The two bar encounters that are *not* jobs — Crablobab and the rating — are
`AddBarEvents` rows in `rules.csv` instead. Crablobab's availability row calls the shared command
bridge for its per-market roll. His entry rows preserve the bar visual before mounting the
settings-registered portrait; stall returns remount that portrait directly, backdrop previews do
not overwrite the saved slot, and the authored exit restores the bar visual before returning to
the bar menu. **Three of the job ids do not match
their class name:**
Bar-job offer and active-contact menus fire the private `JobSpecificOptions` trigger. This keeps
vanilla person options, especially `cutCommLink`, out of job dialogue by construction. Picker
cancel and payout callbacks resume through that same trigger; authored job exits close directly.

Accepted contacts also own a scored `PickGreeting` wrapper. It shows the contact visual, refreshes
the job's live tokens, fires the one `$missionId`-specific `catchreleaseJobGreeting`, and then builds
the private option batch. All 14 jobs therefore open from intel with their own rules-authored scene
instead of a generic vanilla voice greeting.


| Id | Class | | Id | Class |
|---|---|---|---|---|
| `catchrelease_standingOrder` | `StandingOrderJob` | | `catchrelease_academy` | `AcademyJob` |
| `catchrelease_chef` | `ChefJob` | | `catchrelease_tuber` | `TuberJob` |
| `catchrelease_startup` | `StartupJob` | | `catchrelease_cult` | `CultJob` |
| `catchrelease_butler` | `ButlerJob` | | `catchrelease_campPirate` | `PirateCampJob` |
| `catchrelease_curator` | `CuratorJob` | | `catchrelease_campMerc` | `MercCampJob` |
| `catchrelease_duel` | **`KidsJob`** | | `catchrelease_campPath` | `PatherCampJob` |
| `catchrelease_ring` | **`MafiaJob`** | | | |
| `catchrelease_client` | **`CompanionJob`** | | | |

**`data/campaign/distress_calls.csv`** — the merged event registry for the reusable distress-call
framework. One un-commented, namespaced row is one enabled event; provider adapters own eligibility
and all quest content. The framework owns only the vanilla-scheduled entity, breadcrumb intel and
the rules trigger named by the row. Catch.Release contributes the stranded trade fleet and the
scavenger with a dead engine; both enter the existing `FleetQuest` spine through one provider.

**`data/world/factions/default_ranks.json`** — contributes the `catchrelease_crabMerchant`
rank label used by Crablobab's person card and the Fisherman's stable `catchrelease_none` label.
People keep their own factions; the merged default-rank registry supplies only the custom display
strings `Crab Merchant` and `none`.

**`data/campaign/terrain.json`** — `catchrelease_StaticPond` → `MaskedFishingPondTerrainPlugin`,
`catchrelease_coherence_field` → `CoherenceTerrain`. Carries the plugin class only; name, radius,
layers, tags and the live pond's unstable-fabric map icon all come from the plugin — including the
terrain id as a tag, which `BaseTerrain` does **not** add for you and which anything looking a
terrain up by tag depends on.

**`data/campaign/special_items.csv`** — `catchrelease_fish` → `FishItemPlugin`,
`catchrelease_fish_bundle` → `FishBundleItemPlugin`, `catchrelease_fish_pile` → `FishPileItemPlugin`.

**`data/campaign/industries.csv`** — `catchrelease_conservatory` → `BreachConservatory`,
the colony structure that opens the fishing trade and keeps the aquarium.

**`data/campaign/backdrops.csv`** — the scenes that can hang behind the aquarium's water. Read by
`BackdropLoader` into `Backdrop`; no class is registered from it, it is a table of art. Columns are
name, sprite path, rarity, whether Crablobab may stock it and whether a conservatory has it on the
day it is built. `crabStock` gates **his rotation only** — a row he may not carry is a row a job
hands out, and the reward roller leans on exactly those, so every row has a source. The art is cropped to cover a `388 x 170` pane with the glass line over its edge —
`386 x 168` visible, near enough `2.3:1` — so about `772 x 336` at 2x.

**`data/config/custom_entities.json`** — the motes, harpoon, drone, the haunt's ghost asteroid and
mine (`catchrelease_GhostAsteroid` → `GhostAsteroidEntityPlugin`, `catchrelease_HauntMine` →
`HauntMineEntityPlugin`), and the fishing boats' dedicated
fisher-hook map mark (`catchrelease_FisherMapIcon` → `FishermanMapIcon`). The introduction owns no
custom entity any more: the castaway's scene lives on the host planet's market memory, and the
tutorial wreck is vanilla's own salvage-entity wreck. The pond is **not** here any more.

**`data/campaign/rules.csv`** — all dialogue. See the contract below. One row registers *behaviour*
rather than words and is easy to miss when hunting for it in Java: `catchrelease_fisherEncounter`,
on `BeginFleetEncounter`, does `unset $ignorePlayerCommRequests` then `OpenComms`, which is the whole
reason walking up to a fishing boat opens the conversation instead of the engage/disengage screen.
There is no plugin behind it — see the gotcha below.

**`data/config/sounds.json`** — 32 ids at the top level, merged into vanilla's ~600: twenty-nine of
our own (the searchlight UI set, six cargo handling sounds, the coherence whispers, the
ROD's mono directional LYNE drone launch, orbit-to-chase lock, mote impact, and catch layer;
harpoon fire, mote impact and charge-ready UI report, the ROD pond-opening boom, and the
minigame's coin-filled chest
opening, treasure spawn/pickup cues, one continuously renewed line loop backed by `unheld_loop.ogg`,
the input click, the indicator-exit cue and two outcome hooks), the
skillshot framework's denied blip, and two vanilla character-screen ids re-declared at reduced volume.
The minigame success and failure ids currently point at vanilla's reputation-raise and reputation-drop
files, respectively, so replacing those placeholders later needs no Java change.
Ability sounds are named in `abilities.csv` (`uiOn`/`uiOff`/`uiLoop`/`world*`), not in code.

**data/config/LunaSettings.csv** — the Audio radio setting for the harpoon charge-ready
report, plus an Accessibility toggle for the pond camera snap. The snap remains on by default;
turning it off leaves the viewport under manual control while a pond is open.

**`data/console/commands.csv`** — optional Console Commands integration. `AllFish` maps to
`catchrelease.commands.AllFish` — compiled into the jar since the commands moved out of the loose
`data/console` scripts, which makes `lib/lw_Console.jar` a compile dependency — and accepts one
positive amount while on the campaign map or at a market. It reads the merged fish table, rolls
that many real specimens of every species, and adds one species crate per row to player cargo.
`AddFish` accepts a positive amount after a fish id or multi-word display name, resolves exact and
unique partial matches before using Console Commands' typo-correction matcher, and reports
ambiguous partials instead of silently choosing. Its shared resolver and native autocomplete expose
both ids and display names, with word-by-word continuation for manually typed names. `SpawnFish`
uses that same lookup to place one named pattern in a nearby active rupture or under a live breach-
lamp beam, respecting its reachable method. A legendary spawn clears the prior test chase, resets
its singleton ledger and shields into the current system, and suppresses the Longliner's separate
boat so every legendary can be tested repeatedly through its real mote and haunt path.
`HauntStatus` has no arguments and prints the legendary chase and haunt diagnostics: script
registration, the live haunt's species/intensity/module count, and per legendary its host system,
provocation, suppression, nearest own mote and sighting.
`SpawnFisherman` has no
arguments and places the ordinary visiting Fisherman in the player's current star system; it reuses
an existing local boat and retires an off-system visitor before making the standard full fleet.
`SpawnFleetQuest <quest>` is available only inside a star system and autocompletes the five local
procgen quest ids. It creates a route-backed vanilla scavenger near the player and hangs the chosen
offer through the ordinary `FleetQuest`/`FleetQuestEncounter` path, while retaining the normal
one-active-quest limit.
`SpawnDistressCall <event>` is available only while fully in hyperspace and autocompletes every
merged framework event plus `vanilla_normal`, `vanilla_pirate_ambush`,
`vanilla_pirate_ambush_trap` and `vanilla_derelict_ship`. It bypasses only cadence, probability and
cooldown: target systems still pass vanilla's nearby-system filters and reservations, framework
providers still approve their event, and both paths use their normal routes, entities and intel.
Console Commands remains runtime-optional: its own loader is the only code that ever loads these
classes, so the mod runs
without the console installed, and it is deliberately not a `mod_info.json` dependency.

---

## The rules.csv contract

The language itself - triggers, memory scopes, operators, the truthiness gate, scoring, and
the traps this repo has hit - is in [`RULES.md`](RULES.md), with
[`rules/engine_workflow.md`](rules/engine_workflow.md) and
[`rules/command_table.md`](rules/command_table.md) behind it. What follows is only what this
mod does on top of it.

**Option labels are UI actions, not quoted speech.** The `options` column never wraps a displayed
label in quotation marks; quotes there are reserved for CSV structure, while quotes in a command's
script arguments remain tokenizer syntax and are not rendered. This keeps every rules-authored
choice in vanilla's unquoted option-panel style, including the introduction, job declines and
follow-up questions.

**Every word anybody speaks is in the sheet — jobs, the Fisherman, the introduction, the props.**
The current sheet is 560 logical rules. Its supplied dialogue is kept verbatim except where a
later requested rewrite explicitly supersedes it; the additional
rows are routing twins and interrupted-conversation resumes needed to make that dialogue executable.

**Dialogue voice is governed by `LORE.md`, then checked against vanilla faction usage.** Patrols
describe only the law, sensor return, damage, and prohibited equipment they can observe: they do not
recognise fabric mechanics or explain an anomaly. Hegemony lines carry blunt command, Tri-Tachyon
lines polished corporate menace, Church lines vivid and sincere stewardship, League lines legally
confident commerce, Diktat lines offended state authority, Independents practical local judgement,
and pirates humour sharpened by cost and leverage. Path dialogue normally follows its authored
scene, but breach-lamp escalation and the hostile fishing camp are intentionally full-throated:
Moloch, defilement, base appetite and violence against creation, screamed without accurate fabric
physics. Institutional vocabulary must not flatten any of these into placeholder procedure.

Every player-facing line is drafted and audited through the Codex app's Starsector Editor with the
full current `LORE.md` attached. The prompt identifies the exact display surface and its mechanical,
space and layout constraints; a regression repair also supplies the prior and current copy. Distinct
speakers and surfaces receive focused passes before final integrated context QA, preventing generic
tightening from erasing required voice, information density or UI function.

The Fisherman and Crablobab never share a cadence. The former is a warm, patient old captain in
presentation with a colder strategic calculus underneath; he teaches in full human beats, then
chooses where an explanation must stop. The latter is low coherence becoming exuberant merchant
caricature, shaping his shop's reality like clay while the transaction remains perfectly legible.
Both scenes demonstrate physical contradictions without narrating their meaning or prescribing the
player's reaction. Voice-only passes may change the `text` column, displayed option labels and the
quoted prose literal of an `AddText` command, but preserve rule ids, triggers, conditions, all
non-dialogue script commands, notes, option ids/order, memory tokens, mechanical facts and every
return route.

The rupture-interception twin for `catchrelease_introCurious` omits only the final supplied
`Come alongside` sentence because the interception greeting has already delivered that same line.
The Fisherman's `Ask about something else` submenu opens at stage 2, in the same `giveRod()` call
that assigns the first tutorial target. The question rows retain their own information-release
gates, so later subjects do not appear merely because the menu itself is available.
His outfitter lesson and repeatable module/upgrade answers explain that jobs award schematics,
schematics unlock purchases rather than grant hardware, and only the final two upgrade tiers need them.
Each repeatable topic records a campaign-long asked flag only when its answer opens. Unasked
and answered topics share the same submenu without a separate repeat-question state. Two dedicated
rules triggers stream every relevant unasked topic first and every answered topic afterward; the
command pages that ordered stream six at a time, so new questions occupy the first positions and
answered questions fall to the final positions and page. Answered options use vanilla grey so they
recede without disappearing. Labels and information-release gates stay
in `rules.csv`; Java owns only the page arithmetic. Every rebuild appends Previous/Next as relevant,
then the always-present back option and Escape shortcut, so no page can become a dead end.
Its name question asks `Do you have a name?`; Baha is introduced by the Fisherman's registry answer
rather than assumed by a player option before any scene has supplied it.
During the tutorial the intro option is the single route for both target reminders and hand-ins;
the older `Ask about the fish they want` producer remains as a preserved row but adds no option.
When the stage-3 target is in the current system, the two continuity questions are root Fisherman
menu options under the same target and deep-handoff gates that formerly nested them under that
reminder. Each answer writes its own permanent campaign asked flag as it opens, removing only that
root option thereafter; neither question enters the general question menu. Their existing
answer chains return through `catchrelease_fisherBack` to the root menu.
The Fisherman's fish-selling option is withheld until stage 3, after the first tutorial catch has
been handed in; carrying fish before that point does not expose the general sales flow.
If the rumor roller has no lead, its reply ends there instead of appending the successful-rumor
seasonal line.
Landing treasure with a fish records the first bycatch recovery; the next Fisherman question menu
adds a new topic about what came up with the catch. Selecting it
plays the existing explanation and consumes the pending state; the topic then remains in the asked
tail of the question list in vanilla grey, including for saves that explained bycatch before the
question was moved.
The safety interception remains higher-scored and therefore still takes precedence.
Every fish name emitted through a rules token is coloured from its species rarity, including tutorial reminders, cult repetition, stranger rumors, Crablobab's fish references, selected duel/ring contenders, and range-data rewards; the sheet delegates that lookup to the shared command instead of hard-coding species or colours. The command submits mixed rarity and ordinary highlights in the sheet's parameter order, as required by `TextPanelAPI`, so a fish before its system is not dropped. Every option that completes a fish quest is coloured with rules-engine `SetOptionColor ... highlight`:
the tutorial swaps its normal work prompt for `I caught a fish.` when its target is aboard, while
bar jobs, their duel/ring choice variants, fleet jobs and Fisherman work highlight their hand-off
options without changing the dialogue or callback routing behind them.
The retained tutorial target-reminder handlers also separate open-space targets by equipment rung:
stage 3 reuses the supplied ROD-only briefing verbatim, while the supplied lights-and-harpoon
reminder is gated to stage 4 or later.
The supplied second-return row remains verbatim but is dormant; its live routing twin says
`Different pattern. Different fabric.` because the second errand deliberately rolls a distinct
species rather than asking for the first specimen again.
Opening the bar rating's event sets `$global.catchrelease_metRating`; the two first-contact entry
families use that flag to include the supplied rating-specific ROD option only after that meeting.
The rating's three answers share a conversation-local option producer: each answer consumes only
its own question, then returns to the remaining questions and the always-available bar exit, so
the last answer cannot leave the event without a route out.
Java is reached from a row in exactly one way, `CatchReleaseCMD <verb> [arg]`: in *conditions*,
`tokens` writes the booleans and strings the rows branch on and always returns true, so it never
changes whether a row matches; in *script*, a verb does the thing and returns whether it worked. The
machinery panels — shop, chart counter, cargo picker — stay Java, because a shelf of cards is not
something a sheet has anything to say about. Java owns only what a sheet cannot do — counting the hold,
spending it, rolling the payment, settling a bet — and writes the outcome into memory for the rows
to read. A job that says something different when a wager comes off is **a second row**, not a
second branch in Java.

**Remote quest destinations borrow vanilla's person-info map slot.** Pond Camper offers and
accepted reminders call their `FishJob` reference, while tutorial errands and persisted Fisherman
chart-request offers use the shared command bridge. Every route uses the quest's real map target,
intel icon/tags and vanilla destination-colour rules, suppresses the map when the player is already
in that system, and removes only the person-info map marker on accept, decline, return, hand-in or
conversation exit. Ordinary fish orders and the range-data planner rung have no single remote
destination and never mount one.

**Rows per job.** Three are wiring, four are voice, and a job with a decision at the hand-over
brings its own options under its own flag.

| Trigger | Carries |
|---|---|
| `<missionId>_blurbBar` | The bar prompt. Id composed by vanilla — a row named anything else is never found |
| `<missionId>_optionBar` | The bar option. **Id must start with the mission id** (see below) |
| `DialogOptionSelected` on `<missionId>_ask` | The offer, and the accept/decline options |
| `catchreleaseJobAccepted` / `…Declined` / `…Remind` / `…Paid` | Gated on `$missionId ==` |

**Tokens Java sets, rows read.** The first two live as long as the job does, written on the giver.
The rest are written through `token()` with a zero expiry, which unsets them the moment the game
unpauses — the life of a conversation. Capitalised twins exist because the engine does not
capitalise for you and a sentence has to be able to start with one.

| Token | Set by | Means |
|---|---|---|
| `$catchrelease_jobRef` | `setPersonMissionRef` / `setEntityMissionRef` | The job itself, for `Call $catchrelease_jobRef <action>` |
| `$catchrelease_jobDeliver` | `markDeliverable()` | The fish are owed; puts the hand-over option up |
| `$catchreleaseHasFish` | `updateTokens` | Whether the hold covers the whole ask |
| `$catchreleaseAsk` · `…AskCap` | `updateTokens` | What is wanted, as a sentence |
| `$catchreleaseReward` · `…RewardCap` | `updateTokens` | What is paid |
| `$catchreleasePaid` | `handOver` | Whether the exchange happened |
| `$catchreleaseBonus` | `handOver` | Whether an extra was earned |
| `$catchreleaseMore` | `handOver` | Whether the job is asking again |
| Anything else | a job's `setJobTokens` | Per-job names: the dish, the species, the two men in a bar |

**A row cannot print what its own script writes.** `FireAll`/`FireBest` `applyRule` emits the text
column - tokens replaced - and *then* runs the script, so a row that grants a rung and rolls the next
errand is printing tokens the roll has not written yet. The introduction's briefings therefore live
on their own trigger, `CatchReleaseIntroBrief`: the granting row ends its script by firing it, and
the brief row loads the tokens in its own conditions, by which time the roll has happened. The same
ordering makes `Highlight` in a script column correct - `SetTextHighlights` calls
`highlightInLastPara`, which is that row's own text precisely because the text went first.
Each briefing carries a `Continue` option; a fired sub-trigger with text but no option leaves the
rules dialog with no next selection.
The first outbound transition is stage-gated: only stage 2 may call `sendOut`, while a stale or
repeated `catchrelease_introFirstDone` selection at stage 3 replays the existing brief instead of
rolling another target. That brief's unchanged `Continue` label routes through the real Fisherman
encounter exit; the later briefing rungs continue to return to the Fisherman menu.
The deep-gear explanation uses a conversation-local `CatchReleaseIntroDeepQuestions` submenu.
Each supplied answer records its own transient asked flag and repopulates only the unanswered
questions; `What do you want caught?` appears after the lights, return trace, and weapon-safety
answers have all been read. An interrupted handoff simply starts that three-question menu again.
The live stage-3 hand-in also lets the player ask what a breach conservatory is, then how to get
one. Those two short branches contain no grant or progression command: each reuses the existing
outfitter hand-in choices, while the latter directs plans to Crablobab and construction to a
player-owned colony.
The live outfitter explanation also offers tackle and upgrade definitions without naming future
Harpoon or breach-light categories. Both intro answers set the same permanent topic flags as their
ordinary Fisherman entries. Answered and unanswered topics share the same question menu; there is
no separate `Ask again` route, and neither intro path carries a tutorial grant or advance.
The page-one outfitter-payment topic explains only the immediate barter transaction — trade buys
catch, the Fisherman sells hardware, and fish avoid two invoices. It deliberately says nothing
about what ultimately becomes of the catch.
Rumors are tutorial-graduation content: both the option producer and Java command reject stages
below 6. Graduation idempotently grants one immediate rumor/intel lead without checking the monthly
cooldown; completed saves receive the same migration on load, and an already-active rumor satisfies
it. Manual asks and live graduation both fire `CatchReleaseRumorText` after the saved rumor exists;
three rows turn its system, type and optional stranger-species tokens into distinct spoken scenes
before reporting that the matching intel was added.
At the range-data handoff, the existing `You want both of these?` route is quest-highlighted and is
the only route to the next assignment. The optional range-source answer returns to it, ensuring the
supplied sector-map, fishing-planner control, and two-route explanation is always delivered.

**Scoring is a single sheet-wide ladder, not per-family.** Two families keyed on different
flags can both match one hull - a harpooned fishing boat is the case that bit - and the higher
number wins regardless of which family it belongs to. The harpooned-crew greetings therefore
lead with `!$entity.catchrelease_fisherman`, so the boat's own screen is never replaced by
them. Anything new keyed on a flag an arbitrary fleet can carry needs the same check.

A job with its own hand-over decision overrides `getDeliverFlag()` so the shared "hand over the
catch" row does not appear beside its own — `KidsJob` and `MafiaJob` both do this.

---

## The tree

### `campaign/fish`
One file sits at the root, because it is a fact about the setting rather than about any subsystem.

| File | What it does |
|---|---|
| `FishingTaboo.java` | Who will not touch the water at all — the Church and the Path — and the three questions asked about them: is this flag one of them, is this port one of theirs, do they run this system. Read by the bar jobs, the fleet-quest spawner, the core trawler spawner and the lamp response, so the two faction ids appear exactly once in the mod |

### `campaign/fish/data`
The data model: species, individual catches, the player's log, and the enums everything reads off.

| File | What it does |
|---|---|
| `FishSpec.java` | One row of the fish table: identity, minigame stats, value/size range, where it lives, and the shared catch-implement predicate used by normal and rumor spawning |
| `FishCatch.java` | One rolled specimen — length, weight, aberration, region, exact source rupture, catch timestamp, and how it was taken; Fisherman chart catches additionally append their target ID and system to the backward-compatible encoded tail so exact provenance survives loose cargo and containers |
| `FishGrade.java` | Five-step quality ladder, size fraction → value multiplier and colour. `rank` is the explicit ladder position - comparisons never read `ordinal()` |
| `FishRarity.java` | The sole palette source for every player-facing rarity surface, plus the speed and wander ladder. Common is a warm beige-creme distinct from both vanilla standard text and disabled grey; every caller uses `rarity.color` instead of substituting either. `rank` is the explicit ladder position every comparison and rarity-graded price reads - never `ordinal()`, so reordering the enum cannot silently reshuffle the ladder |
| `FishMotion.java` | Minigame movement archetypes (SMOOTH, DARTER, SINKER, FLOATER, MIXED) |
| `FishLog.java` | Sector-persistent per-species record; unlocks location data for codex and map - except for legendaries, which it refuses centrally: their range is never sold, given or rolled, and a load repair relocks any a save bought before the rule. Its `getSystemName` is public - the per-catch ledger files the same reading of where a catch was taken |
| `FishLogEntry.java` | Per-species log data: counts, records, first/record location and time, capture method |
| `AberrationSource.java` | The registry: every kind of source as a row — label, tags (vanilla and foreign), light-year reach, weight, and whether it waits to be surveyed. The in-system reach is derived from the light-year one, never tabulated beside it. Adding a source is a line; adding another mod's version of one is a tag |
| `Aberration.java` | 0–1 aberration for a location — the inverse of coherence — strongest destabilizer minus strongest colony stabilizer. Any inhabited economy market makes its own system exactly Stable and cuts a five-light-year quadratic stability field around it. One crawl of the sector builds both flat mark indexes; system readings are computed off them and cached; `localPull` is the only per-frame figure and never leaves the system it is asked about. `Watcher` fills on arrival and on the sector map opening |
| `SectorRegion.java` | Nine-way sector location enum (8 quadrant bands + ABYSSAL) |
| `StarColour.java` | What a system's sun looks like, from its star's planet type |
| `FishHabitat.java` | Everything a place says about itself — sun, tags, region, constellation age, coherence — read once and cached until the monthly reassessment drops the cache |
| `FishRanges.java` | Where a species can spawn in this sector, right now: the runtime question between the sheet and everything that asks. Legendaries answer first, from `LegendaryChases`' one-host ledger, and never pin or relax - their single host only occupies a cap slot in the relax bookkeeping. Pinned quest targets answer from a frozen system list; everything else from `FishSpec.matches` at the species' current relaxation level. Reassessed at month end |
| `CatchImplement.java` | What made a fish reachable — a pond or a breach lamp — read off the mote's own provenance |
| `FishLocationSummary.java` | Builds the "where this swims" sentence from every habitat criterion a spec sets |

### `campaign/fish/jobs`
Bar-given jobs on a shared spine, plus the ask/reward rollers they share.

| File | What it does |
|---|---|
| `FishJob.java` | The spine: asks, rewards, hand-over, intel, and the `rules.csv` token contract. Comm-directory greetings and exits ask the live mission whether it is at the active `WANTED` delivery stage and person-given; its `callEvent` intercepts that boolean query because `BaseHubMission` treats a false `callAction` result as an unhandled action. `BaseHubMission` leaves the stage null until acceptance, so this works for accepted jobs loaded from older saves without leaking generic hand-in options into specialized jobs or bar routing into entity-given fleet quests. Its contact-visual hook owns the primary portrait and lets multi-person jobs add vanilla secondary portraits. Offer rows can call back into it to inject item-style explanation cards for schematic rewards. Hand-over opens an asynchronous exact-specimen picker, then fires the existing payout rows only after the chosen fish have been spent. Jobs normally complete as part of that hand-over, but entity-given jobs can defer the stage change until their paid dialogue has resumed. Fish names and explicit rarity floors in its intel and picker wear their canonical rarity colours, while the list headline uses the giver faction's base UI colour. Its stage wrapper puts the shared navigation button after every active standard or specialized description and omits it from completed intel, handing the complete ask to the fishing map including broad category asks. Active orders render each requirement as its own live aboard/needed count, while ending and completed intel suppress that live cargo line after hand-in has spent the specimens; the central landed-catch hook schedules an intel update on the next unpaused campaign frame whenever a multi-specimen order advances. Vanilla hub acceptance still supplies the initial job notification directly in the accepting dialogue's text panel. Its list and map icon comes from `FishIntelIcon`: lamp-only asks use the lamp mark, rupture-only asks use the ROD mark, and open or mixed asks use the shared mark. A `FishAsker`, so its asks reach the wanted-fish marks Its rules callback also supplies a remote-only variant of vanilla's offer/reminder map action, using each job's own map location and intel presentation. |
| `FishHandoffPicker.java` | The hand-in's two routes. The picker: eligible loose fish, an optional specimen-provenance gate, an optional caller-authored confirmation verb, a side readout ticking each ask off live as the selection covers it, and an exact non-overlapping assignment validated on confirm - a wrong confirm reopens the picker with the reason said, since the engine re-enables the stock confirm button every frame anything is picked and cannot be gated. And `autoSelect`: the minimum set that covers the asks, chosen worst-first from the whole hold, crates and pile included, spent by encoded identity with part-taken containers repacked |
| `FishJobAsks.java` | Rolls ask parameters — weight floors, species, type variety — off the fish table; legendaries are excluded, because a fish that exists once can never be a standing order |
| `FishReward.java` | Reward base plus Credits, old-save direct Upgrade/Tackle grants, UpgradeSchematic, TackleSchematic, LocationData, Backdrop and Blueprint. Schematic rewards provide their own 48px offer card using the exact item-or-category icon resolved by their outfitter entry: it names the exact upgrade tier or rig module, shows the value change or compatible rig, states that the reward is purchase permission rather than hardware, and previews the later credit-and-catch purchase price. An upgrade schematic card overlays the shared quest-yellow dot when its exact tier is marked in the outfitter. New gear rewards unlock one outfitter purchase rather than handing over hardware or a free level. LocationData is the internal range-data reward; its standard image-with-text card overlays the shared live `FishIcons` silhouette on the mod's transparent item icon, and its rolled cash value turns into that credit payment if the species' range becomes known before handoff. The retained Commodity class is only an old-save shell and converts serialized goods payouts to credits |
| `FishRewardRoller.java` | Rolls a commodity-free payment scaled to a job's worth and preserves that value on range-data rewards for live redundancy conversion; a range-data roll never lands on a legendary. Cash outcomes pay at five times the internal barter value so ordinary fish jobs compete with sector work without multiplying blueprints; if both reward slots independently land on credits, the offer coalesces them into one payment. Credit figures round to the nearest 1,000 through 100,000 and the nearest 10,000 above it, including a combined two-slot payout. Stocked modules and the next quest-gated upgrade tier enter as purchase-unlock schematics; known plans and plans promised by accepted, non-ending jobs are reserved out of the pool, including across both slots of one roll. Lower upgrade tiers remain ordinary outfitter purchases, and Crablobab's unstocked consumable head stays outside this pool. Neither schematic kind is offered for a rig the player does not hold - the ungrouped catch stats stay open, being the minigame's own. Backdrop outcomes stay out of every shared job roll until the conservatory plans have been bought or learned. |
| `QuestPond.java` | Claims and releases a pond for a job, hangs vanilla's gold mission marker on it while claimed, and seeds a flagged quest mote into it. Holds are a **set** of job ids, so two errands on one rupture cannot strand each other's marker; `releaseAll` lets go sector-wide and `sweep` is the load-time repair for saves that already have one burned in. A planted mote records its planter, so `clearMotes` takes it back out when the errand ends — a holding specimen never expires by itself |
| `StandingOrderJob.java` | The plain quantity/rarity/grade baseline. Its rules-authored provisioner treats the generated ask as one line on an overfull supplier manifest, states the sixty-day window and pays only after the containers match it |
| `AcademyJob.java` | Wants one to three low-coherence specimens at Galatia or a large Independent market once the shared fishing-work gate is open. Its rules-authored researcher insists on original retrieval records, states the sixty-day commission and records contradictory measurements instead of explaining them |
| `ButlerJob.java` | One fish above a rolled weight floor, paid by that floor. Its rules-authored offer uses a conversation-local who/purpose/terms submenu: optional answers return to the remaining questions, decline is always available, and acceptance appears only after the generated specification and reward have been stated. The giver serves the household's own butler; controlled distance, restricted household access and delegated handling carry his contempt without an insult |
| `ChefJob.java` | Three different type requirements for one dish, each asking for one or two specimens and sometimes Fine grade. Its rules-authored cook treats them as separate prep lines, states the forty-day menu deadline and checks handling tags before payment |
| `CompanionJob.java` | Hegemony-only private contract for one specimen over a rolled mass floor, with a bonus at the upper two-fifths of its species range. Its rules-authored liaison states the forty-day term and premium before a small hub; the optional purpose answer returns with accept and decline still available |
| `CultJob.java` | One specific named species for a non-credit reward. Its rules-authored three-person offer states species, consideration, quantity and fifty-five-day deadline before a small hub; asking purpose makes all three repeat the exact species, then returns with accept and decline still available |
| `CuratorJob.java` | One to three Uncommon-or-higher specimens, each accepted if Fine grade or low coherence. Its rules-authored public curator separates DISPLAY from RECORD accession value, states the seventy-day window and checks provenance before payment |
| `KidsJob.java` | Two unrestricted fish for a children's tournament, with a thirty-day item-reward contract and a Fine-or-better extra prize. The initial encounter restores BarCMD's saved bar backdrop instead of showing a person card; its necessary comm-directory mission anchor uses vanilla's generic portrait through the settings sprite registry, so the two-child scene is not represented by one random adult face. A conversation-local rules/allocator/terms hub keeps the two optional questions on the offer page, then presents only accept or decline once the complete terms have been shown. At hand-in it holds an exact picker selection without spending it, names both contenders by grade, species and length, then spends only after the player assigns the specimen with the higher within-species size fraction to one child; reselect and not-yet routes remain non-consuming. Successful allocation clears the stale offer state, fires the appropriate authored payout outro once, and leaves one Escape-bound close instead of rebuilding generic person/acceptance options |
| `MafiaJob.java` | Two generated-requirement fish for Salvatore and Enzo's protected fighting book, due in thirty-five days. Both men are persistent named `PersonAPI` records: Salvatore remains the sole comm-directory giver and active rules person, while Enzo occupies vanilla's secondary portrait slot in the offer and accepted-job contact. Rules prose reads their live first-name tokens instead of duplicating names. A conversation-local people/tank/wager/terms hub gates acceptance behind the complete offer; the writing establishes the operation through a cleared table, quiet favors, the house book and obligations rather than contract jargon or theatrical threats. At delivery an exact non-consuming picker pair is randomly assigned to Salvatore and Enzo, named by grade, species and length, and given separately displayed odds from both specimens' within-species size fractions. The line is displayed to a tenth of a percentage point from the same odds value used for settlement. The player then takes the sure payment or backs either side at that line; reselect and not-yet consume nothing. Equal fighters sit at 38% each, preserving the house edge. A successful wager replaces the promised payment with a new twice-value roll; a loss pays nothing, while a failed cargo spend restores the originally quoted reward |
| `StartupJob.java` | Three rounds of growing quantity, no time limit. Its rules-authored pitch is a compact demand/operations/terms submenu: optional due-diligence questions return to the remaining topics, terms unlock acceptance, and decline is always available. A read-only round token lets the hand-in dialogue distinguish pilot, scale-up and production while preserving the existing three-delivery mechanic |
| `TuberJob.java` | Two rounds: a Fine Uncommon/Rare specimen for the polished first post, then a low-coherence specimen when clean footage loses retention. The insufferable short-form creator voice is authored in `rules.csv` and bounded by `LORE.md`. |

### `campaign/fish/jobs/camp`
A fisher whose one good rupture has somebody parked on it. Three bar events, three ways through.

| File | What it does |
|---|---|
| `CampedSpotJob.java` | The shared job. Two conditions rather than one — clearing the camp is the work, and any landed specimen whose catch record names that exact rupture and was landed at or after acceptance is the receipt; no fish/crab/mollusc/other type tag narrows it. Both the authored offer and the intel state this plainly: the fleet goes first, then a catch from that exact water is brought back as proof. Asks `CampedSpot.isGone` and nothing more specific, so it never has an opinion about how the player did it. Its poll tracks camp-cleared and receipt-aboard independently, sends a dedicated intel update for each transition, removes the marker from a surviving retreating fleet, and releases the rupture's own claim as soon as proof is aboard. If that specimen leaves cargo before hand-in, the claim and catch instruction return. The three authored paid rows close by printing the resolved `$catchreleaseReward` after the grant, so every reward type is acknowledged in the text panel. The offer only chooses the rupture and terms; the physical camper and pond claim are created on acceptance so discarded bar-event rolls cannot leave fleets behind. Active older saves repair their named-species or fish-only ask to the unrestricted pond receipt and begin a new proof-time window on their first repaired tick |
| `CampType.java` | Who is out there: pirates (there for money, will take money), mercenaries (paid to be there, and say so), pathers (not selling anything, and the bribe does the least good). Mercenary rather than independent deliberately — see the note in the file |
| `CampSize.java` | Small, medium, large, and the words the fisher uses for each. The estimate is honest; it is the only warning the player gets |
| `CampedSpot.java` | Spawns the camper on the rupture. When the player first enters its location, the fleet intercepts until its one-time warning hail has fired; it then returns to a passive hold and always uses vanilla's allow-disengage flag. The camp conversation's cut-link option uses vanilla's no-continue exit, returning directly to the fleet menu. The rupture carries a separate live camp flag that blocks the ROD only until the camper is gone; refreshing that flag clears the obsolete named-species memory from older camp jobs. Spawned rather than borrowed, because the job is about one specific pond and there is no fleet already parked on it |
| `PirateCampJob.java` · `MercCampJob.java` · `PatherCampJob.java` | One per bar event, so each fisher gets their own pitch. The pirate variant frames the occupation as a protection racket through repair accounts and recurring payments; the mercenary variant uses a filed-boundary dispute made physical and prefers a contractual exit; the Pather variant shows sabotage and personal intimidation through damaged rack hardware and door-checking. Every offer states the camp size, system, clearance-first proof sequence, seventy-day deadline and exact reward |

### `campaign/fish/jobs/fleet`
Jobs hung on a hull that was already out there, which then has to still be there when you return.

| File | What it does |
|---|---|
| `FleetQuest.java` | A `FishJob` whose giver is a fleet. `offer()` hangs it, keeps vanilla scavenger AI from avoiding the player, and otherwise leaves its work alone; a distress-sourced offer suppresses the local cyan marker because vanilla's distress intel is the locator. Accepting renders vanilla's intel-added card in the live conversation, while `take()` still waits for that conversation to close before it supplants the hull with a copy, then `mark()` and `hold()`. The player-avoid override is also refreshed while the offer is live, using the same memory and navigation reset as the Fisherman. Its hand-over uses the shared specimen picker, resumes the fleet sheet while the entity mission reference is still valid, then completes the mission so the paid text and completion intel update resolve in that order. Successful completion releases the active quest state as soon as ending starts, immediately clearing its marks and giving the hull vanilla's return-to-source chain ending in despawn rather than waiting through the intel end delay. It leaves only the quest type's one-shot acknowledgement on the returning hull, which outranks the waiting greeting on the next comm request and clears itself when read. Later end cleanup sees the vanilla returning flag and does not restart the route |
| `FleetQuestSpawner.java` | Hangs one of five local offers on a hull already in the player's system; natural rolls spawn nothing. **Scavengers only**, and never the Fisherman — the errand assumes somebody already picking over the system with no schedule to keep. Rare on purpose: one active at a time, 7% a check, 45-day cooldown. Its explicit console/testing entry creates a route-backed vanilla scavenger near the player, bypassing only the natural roll and cooldown before using the same offer and encounter path |
| `FleetQuestEncounter.java` | Runs one offer — reads the answer once the dialogue closes, resolves a framework distress entity before accepting or declining, re-hangs only local offer marks after a load, and times the offer out |
| `FleetQuestType.java` | Seven flavours of trouble, with pitch text, ask rolling and base worth. The collector always names one habitat-backed uncommon-or-rarer species; specific asks use that species' actual rarity for payment and schematic rolls. Five types remain in the local scavenger picker; `STRANDED` and `SCAVENGER_ENGINE` are retained in place for save compatibility and selected by the distress adapter |
| `CatchReleaseDistressProvider.java` | The only Catch.Release dependency of the generic distress package: gates both CSV rows behind fishing work and the shared one-job limit, prepares their existing `FleetQuest`, and abandons it if the vanilla-style distress fleet expires |

### `campaign/fish/colony`
The Breach Conservatory: the structure that brings the fishing trade to the player's own colony.

| File | What it does |
|---|---|
| `BreachConservatory.java` | The structure itself; also holds the aquarium's stock, its on/off switch and which backdrop this tank hangs |
| `ConservatoryOptionProvider.java` | The two colony-screen options: the fish outfitter and the aquarium office |
| `AquariumManageDialog.java` | The office: stock the tank, empty it, change the scene behind the water, or shut the display off. The scenery rack pages owned backdrops six at a time so Previous, Next and Back always fit under the nine-option ceiling; every option uses its `FishRarity.color`, including Common, and previews on hover with an actual `AquariumTankPanel` rather than a picture of the art |
| `AquariumTransfers.java` | Hold-to-tank and back, both through the vanilla cargo picker. Depositing unboxes the hold first so every specimen is independently selectable; withdrawing is already loose fish from the tank |
| `AquariumTankScript.java` | Hangs the tank on the colony main menu, below the planet's image, and takes it down again whenever another visual is showing. Mounts as soon as the docked core UI is anything short of fully covering, rather than waiting for its fader to finish, so the tank comes back with the menu |
| `AquariumTankPanel.java` | The tank: GL water with caustics and light shafts, kelp and stones, an optional backdrop png, and every specimen swimming its own way at the size it was actually landed. How one *carries* itself is its `Build`, off the crab/mollusc/fish tags rather than off its motion: fish slant up to `MAX_PITCH` and no further, molluscs and oddments never turn and only list, crabs live on the stones. The drawn angle is the bounded pitch, never the raw heading, so nothing rotates up through the vertical to come about |
| `Backdrop.java` | One row of `data/campaign/backdrops.csv`: a scene for behind the water — name, art path, rarity, whether Crablobab stocks it, whether a conservatory has it from the start |
| `Backdrops.java` | Two scopes: which scenes the *player* has come by (sector memory) and which one a *conservatory* is hanging (the industry). Resolution, ownership and the has-the-art-been-drawn question |

### `campaign/fish/fisherman`
The fishing trade. **One man, many boats** — a standing trawler in every inhabited system working the
outer reaches off one shared shelf, and a visiting one that turns up in uninhabited water for a
fortnight with a shelf of its own. Every one of them answers as the same person, whose portrait
follows the local five-rung coherence reading, and none of them explains how.

| File | What it does |
|---|---|
| `FishermanSpawner.java` | The visiting boat: one roll per arrival in an uninhabited system - a small base leaned on by a full hold and a long absence - after which the system is locked for a month so re-entry is not a re-roll. Its sector sweep recovers a visiting boat whose old-save pointer was lost, keeps one visitor sector-wide and one Fisherman per system, and marks on-screen legacy extras for off-screen retirement rather than making them vanish; the Longliner's decoy boat is excluded from that bookkeeping entirely, so reconciliation can neither retire it nor be confused by it. Both the arrival decision and the actual fleet-creation boundary yield to any local Fisherman, including lesson two's temporary standing posting in otherwise uninhabited water. Its explicit console/testing entry point bypasses only the natural roll, reuses a local boat, and retires an off-system visitor before creating the standard visiting fleet |
| `CoreFisherSpawner.java` | One boat to every inhabited system, re-posted if it is lost - weekly, and again the moment the player arrives, so a destroyed boat is back by the time anybody looks. `ensureBoat` takes the canonical live Fisherman of either schedule before posting, and the posting boundary repeats that guard, so neither a directed tutorial errand nor a later caller can place a standing boat beside another Fisherman |
| `CoreFisherBehavior.java` | The standing boat: the same rig and the same man, no visit clock, and the outer-reaches route |
| `OuterReaches.java` | Where a boat is willing to be, and which legs clear the inhabited worlds. `place()` is the one gate every boat placement goes through: clamped into the band in an inhabited system, unconstrained where there is nobody |
| `FishermanBehavior.java` | The stay: yellow fan lamps, staged motes, the leaving. Its lamp reach and cone are Fisherman constants used by rendering, mote placement and light tests, independent of the player's breach-light upgrades. `keepStanding()` pins both sides of its player relationship non-hostile even when the Independents are enemies, blocks vanilla's separate friendly-fleet collision avoidance, clears any cached player-avoidance course, and puts it outside every other fleet's business in both directions; `keepPace()` holds it to burn 4 unless it is closing on somebody |
| — | Talking to the boat is not a file. The encounter goes straight to comms (`catchrelease_fisherEncounter`), and the range-data counter, outfitter, buyer, rumours and chart requests are all rows under `$menuState == catchreleaseFisher` |
| `FishermanShelf.java` | What range data is on sale and on which boat — two slots to start, the pool that stops duplicates, and the restock dated off each sale. Legendaries are never stocked |
| `FishermanQuest.java` | Chart requests: one named specimen from one named place, kept in the water until it is landed. The first previewed fish-and-system pair is saved as a pending offer and reused across declines, dialog exits, and save/load until acceptance consumes it. Acceptance mints a unique target ID and timestamp; the planted mote carries that ID into the landed `FishCatch`, alongside its catch timestamp and system, so neither an older specimen nor a same-species catch from elsewhere can satisfy the request. The request exposes all of that provenance as one shared `FishRequirement`; progress, satisfaction, picker eligibility, and spending all run through the same `FishRequirement`/`FishCurrency` path as the other quests. Stowing that exact catch updates the note and releases the marker immediately; the keeper's hold poll remains as the reverse path when the specimen leaves cargo. `QuestIntel` resolves the authoritative persistent request whenever it renders or routes, rather than trusting a serialized copy of the old landed flag after load. Hand-over uses the shared picker and revalidates the same requirement before spending, then reports both credits and the permanent additional range-data slot in vanilla's small green reward face before removing every matching intel note from a snapshot because Starsector exposes the manager's live repository list. Completion starts a 90-day (three campaign month) timestamp gate before another offer may roll. Acceptance renders the vanilla intel-added card into the live Fisherman dialogue and still uses the shared next-unpaused-frame notification delivery, while landed-state updates retain the shared delayed update path; neither campaign message is consumed behind the Fisherman dialogue or catch minigame. Its `QuestIntel` is a `FishAsker` dressed as an accepted bar job: its headline uses the player faction's base UI colour, followed by the Fisherman's default (stable) portrait beside the independent crest at 128px, a given-by line in the faction's colour, live aboard/needed progress with rarity colouring and the requested coherence, a destination-and-method block, then the shared navigation action. The compact list card uses the same live progress followed by the current next step, and there is no pay line anywhere, the way the bar jobs keep the price out of their notes. Its list and map icon identifies the request as rupture-only or lamp-only through `FishIntelIcon`. It explicitly appears under vanilla's Accepted intel tag, plots a route to the named system while the specimen is outstanding, then offers autopilot to the nearest fishing boat once it is aboard. The saved pending offer and accepted request also supply the same transient sidebar map on the offer and later clarification pages. |
| `FishermanSurveyDialog.java` | The chart counter: the shelf as silhouette cards, component-built in the sidebar's language. It clears the host interaction's options immediately before opening its custom visual, and hands the Fisherman's sheet back exactly once on every close route |
| `FishermanMapIcon.java` | The boat's dedicated fisher-hook mark on the system map — one per undetected boat while the player shares its location. It yields to the fleet's own visible sensor contact at detection range, returns if contact is lost, and reconciles old-save duplicates and marks in departed locations away. Its tooltip keeps the authored `The Fisherman` casing with vanilla's `ucFirst` title styling instead of `getNameWithFaction()` lower-casing the named fleet |
| `FishermanIdentity.java` | The one person, kept for the campaign — and how far gone he reads where the fabric is thin. The five portraits follow `FishItemPlugin`'s canonical coherence bands; `preparePortrait` mutates the shared person only for the boat being hailed, immediately before vanilla draws the comm portrait, so off-screen boats cannot overwrite it. Every identity lookup clears rank and post - vanilla's own rankless presentation: the person card shows one muted None and dedups the post line against the rank by string, so a registered "none" rank label breaks the dedup and prints the label twice. Repairs old saves that persisted either field |
| `FishermanBycatch.java` | The bridge between recovered treasure and dialogue: remembers the first landed bycatch until the player asks the Fisherman about it, then exposes the explained state so the topic remains in the asked tail of the general question list |
| `FishRumors.java` | One rumor a month — rarer rolls, richer treasure, or a stranger species. It exposes only the saved facts to the rules sheet, which owns the spoken scene; `RumorIntel` is queued through the shared next-unpaused-frame notification delivery, uses the player faction's base UI colour for its headline and the Fisherman's stable portrait as its intel icon, and can render that same queued-or-active intel card into the rumor dialogue instead of a generic receipt. The entry gives the same lead in precise intel prose with the stranger's name in its rarity colour, counts down against the rumor's own timestamp, and reports expiry only while the player is in hyperspace or the reported system. Stranger-species leads plot a route to that system; loot/rarity leads honestly use **Set autopilot** instead of opening a fish planner with no fish target. `ensureTutorialLead` idempotently creates the graduate's first rumor outside the monthly ask gate and migrates already-completed saves. Stranger rolls never name a legendary: the rumor's spawn boost bypasses the range gate, and a legendary's one-host residency and caught-forever flag must hold |
| `FishermanConstants.java` | Every number the above read |

The Fisherman map mark is also an autopilot proxy. Once per second while the player is local,
its plugin replaces a course to the mark with a course to its moving fleet.

### `dialogue/rules`
The one rule command the mod ships, and the only place the sheet reaches into Java.

| File | What it does |
|---|---|
| `CatchReleaseCMD.java` | `CatchReleaseCMD <verb> [arg]` — writes the branch tokens (including stage-gated Fisherman outfitter access, local-target location, interrupted deep-gear handoff, pending first-bycatch explanation, the active rumor's system/type/stranger, the bulk-sale rungs that currently have eligible fish, and Crablobab's live regular-or-empty stock state), opens the panels, walks the ladder, resolves the one-use castaway rescue, and reaches into the encounter screen where a row cannot. Its condition verbs also expose Crablobab's per-market bar roll without putting lifecycle state in the sheet. Its `highlightJobText`, `highlightWorkText`, and `highlightIntroText` verbs mirror vanilla `Highlight` plus `SetTextHighlightColors`: fish names/rarity floors take their rarity colours while places and non-fish rewards remain yellow; the generic `highlightFishText` verb applies that same merged-table lookup to non-quest dialogue; the work highlighter reads the rolled pending offer before acceptance as well as the active quest afterward. `beginFisherQuestions`/`addFisherQuestion`/`finishFisherQuestions` page the rules-authored unasked-then-answered stream and create every completed-tail option with the canonical Common beige-creme in the same call, after parsing the authored asked flag as a boolean. The Crablobab option stream attaches structured cost cards to regular wares, backdrops, and the terrible fallback bass; its explosive-pending and settled predicates read the saved use record directly so greeting selection does not depend on a token write during condition evaluation. Other panel verbs include `leaveEncounter`, `dropCutComm`, and `colorBulkSaleOptions`; `dropCutComm` strips every vanilla cut-link variant after legacy custom menus such as the Fisherman's build their own options; bar jobs instead use the isolated `JobSpecificOptions` trigger, while the colour verb gives the common, uncommon, rare and epic sale rungs their canonical rarity colours, and attaches exact rarity-coloured cargo previews to all four. A panel return restores the Fisherman's plugin then clears and rebuilds its options once, so custom-visual dismissal cannot duplicate or lose the sheet Its tutorial/work map verbs resolve only already-persisted target data and hand the corresponding intel presentation to the shared vanilla-style map helper. Tutorial stage verbs queue or update intel silently by default; `showIntroIntel` separately renders the current queued-or-active tutorial card into the dialogue without changing stage, target, queue state, or notification timing. |
| `QuestDialogMap.java` | The shared person-info marker bridge: mirrors vanilla `BaseHubMission.showMap` icon, tags and colour selection; refuses local or unresolved destinations; and removes only the temporary map marker before every redraw/cleanup. |
| `rules.csv` tutorial graduation | Applies the narrative `Highlight` before `finishIntro` appends its four range-data receipts, because vanilla highlight commands replace the highlight set on the last paragraph. Each receipt therefore keeps the fish name's rarity colour. |
| `rules.csv` fleet-resolution exits | Peacefully completed fleet conversations fire the shared `CatchReleaseFleetResolutionOptions` trigger after showing their result. It leaves that result on-screen behind one **Leave** option with Escape bound to it; selecting it runs `CatchReleaseCMD leaveEncounter`, so vanilla tears down the encounter battle before dismissal. Explicit **cut the comm link** and fleeing outcomes remain immediate. The lamp ladder's automatic guns-stage speeches instead set the fleet hostile and call vanilla `EndConversation`: the FID keeps its `BattleAPI`, offers **Continue**, then rebuilds into the ordinary hostile encounter/combat options rather than dismissing the dialog. |
| `rules.csv` Fisherman stability aside | The Barely Holding dialogue band exposes a one-time `Are you okay?` main-menu branch. While the first-catch tutorial still offers `I just left you in another system.`, that continuity question takes precedence; once it has been asked or its tutorial opportunity has passed, `Are you okay?` becomes available without requiring the earlier question. The acknowledgement, mirror question, and knot follow-up all terminate in an explicit `catchrelease_fisherBack` route, while campaign-persistent asked flags prevent either observation from repeating on another boat. |
| `FishBuyer.java` | Selling the catch: the picker, four batch rungs (common through epic), and the immutable sale preview shared by labels, tooltips, confirmation and execution. The picker's left column carries a normal custom-panel `Pack into crates` button: it returns the current selection, compacts both picker and transactional hold mirrors by species, then capability-crawls from its panel to vanilla's stable `updateCargoViews()` hook so both one-time cargo grids rebuild without naming obfuscated classes or fields; the exit path unboxes those temporary crates before restoring the original cargo layout minus anything sold. Bulk tooltips render one row per species so every fish name carries its own rarity colour without substring collisions. Its confirmation uses vanilla's large Insignia prompt face and highlights the full credit amount. Bulk sale rebuilds that prompt instead of selling if the hold changed, and protects every yellow-dotted specimen requested by either marked shop gear or any active `FishAsker` - containers are read specimen by specimen, so a wanted fish keeps itself while the eligible rest sell and repack under the container's own id. It counts every copy in stacked identical containers |

### `campaign/fish/tutorial`
Learning to fish, in six lessons and one shortcut. **Entirely detached from the ordinary loop** — the
trade runs whether or not any of it has happened. What it gates is *equipment*, and through that
everything downstream. Not a word of what it says is in Java.

`IntroIntel` also uses the shared map action on every rung, handing the current named, multi-species
or any-species ask to the same request-aware map filter as ordinary jobs.

The any-catch lesson is satisfied only by the specimen planted for that lesson. The planter identity
travels from mote to cargo, so fish already aboard cannot clear its pond marker; catching the planted
specimen releases the marker immediately, and hand-in spends that same identified catch.

| File | What it does |
|---|---|
| `FishingIntro.java` | The seven stages, the errand targets, the grants, the shortcut, and `IntroIntel` under the Accepted intel tag with a player-faction headline. Its list and notification icon follows the lesson rather than the rolled location: POINT uses the tutorial mark, the first two catch lessons use the ROD mark even when their target is in open space, the Breach Lights/Harpoon lesson uses the lamp mark, and the range-data lesson uses the mixed-method mark. Named quarry in that intel wears its rarity colour. Stage changes queue or update the note without rendering it into the live dialogue, while the forced comm queue still delivers the campaign notification on the first unpaused campaign frame after the conversation closes. `CatchReleaseCMD showIntroIntel` is the on-demand inline path: it reads the queued note first and falls back to the active note after the queue has flushed, rendering exactly one card without advancing the stage; active intel keeps its zero-day `DelayedActionScript` campaign update. Replacing a target explicitly updates the persistent intel destination; system-bound fish errands use **Plot route**, range-data errands use the fishing planner, and boat-finding/return objectives use **Set autopilot**; the two-chart rung returns no single map location, leaving its several destinations to the planner instead of falling through to the prior system's boat; its intel lists both species separately, shows each aboard/needed state, and sends a delayed update as either one is landed. Its open panel is dressed as an accepted bar job: tutorial entry points have no image, while lessons given by the Fisherman use the default portrait beside the independent crest at 128px, followed by the given-by line, current context, wanted-specimen progress, destination and method, the shared navigation action, then the compact bullets repeated at the bottom the way vanilla's notes do - and no reward section; what a rung unlocks stays in the conversation. System-bound tutorial species use the real spawner's `FishHabitat` plus `CatchImplement` predicate, with Common-only targets for the ROD lessons and a Common/Uncommon cap for the Harpoon lesson. The two-chart lesson instead ranks every remaining uncaught and unmapped Common by its combined natural spawn weight across pond and lamp pools in valid systems within 10 LY, then assigns the two highest; it cannot hand out a target with no nearby matching habitat. Every pond-bound lesson claims its exact rupture as soon as the target is stored, including the any-catch lesson; `Keeper` repairs that claim for an old save before planting, and the plant path claims the same rupture defensively. The Harpoon lesson further requires a species whose source metadata is exactly `BREACH_LAMP`: it cannot come from a pond, and drones only catch pond fish, so the live target can only be landed by harpoon. If a normal target system has no capped real-spawn candidate, the first lesson remains in the current system and later lessons use an id-sorted thin-first fallback that still obeys their 2–10 LY range; the fallback is logged, while an all-empty pool logs a warning and creates no impossible target. Assigning the second lesson immediately reserves the canonical Fisherman already in its target system; an uninhabited-system posting is held for that errand and despawns only after the lesson ends and the player leaves, while a reused visitor is held then released back to its own lifecycle. `Keeper` re-applies that reservation from the saved target, making mid-lesson load repair idempotent. The second-catch handoff introduces the Fisherman's outfitter before the deep rigs arrive and carries a pending flag so an interrupted conversation resumes correctly; it grants no shop ability - the dev-era ability-bar shortcut and its migration shims are gone, since development assumes a new game. Every tutorial range-data grant emits one vanilla-style small green `Gained: Range data for <pattern>` receipt per newly unlocked entry, with the pattern name in its rarity colour; the handoff row formats its narrative before Java appends those receipts, so no later last-paragraph command strips the final pattern's highlight. This includes the two-chart lesson, graduation, and the shortcut. The shortcut grants the same 2 common/1 uncommon/1 rare range-data mix as the full route. `Keeper` both plants the specimen and watches the hold for it: every pond lesson marks and reserves the exact rupture containing its guaranteed specimen; landing one releases the rupture, takes the planted specimen back out of the fabric and re-points the note at the boat. `dropNote` snapshots every active or queued old-save `IntroIntel` before removing it, so duplicate tutorial notes can be cleaned up safely. A `FishAsker`, so the rung's quarry wears the wanted-fish mark Its system-bound targets supply the transient dialogue sidebar map on each outbound briefing and later location reminder; local targets and the multi-system planner rung deliberately do not. |
| `TutorialWreck.java` | A battered cruiser beside the first rupture seen out where nobody lives, carrying the Fisherman's damaged LYNE service assembly as a navigation breadcrumb rather than usable early gear. Vanilla's own wreck, made vanilla's own way - `DerelictShipData` through `BaseThemeGenerator.addSalvageEntity` with a recovery special - so the real hull renders as the hulk and the standard salvage screen, map icon and sensor behaviour all come for free. The hulk uses the registered wreck illustration as its interaction image, and the assembly scene is a score-boosted rules row on the hulk's own memory flag; recovering the assembly retires flag and mission marker, and the wreck goes back to being ordinary salvage |
| `Castaway.java` | A rating missed during a badly reconciled crew transfer. Found vanilla's mission-target way: a score-boosted `OpenInteractionDialog` row takes an eligible planet's interaction over before the survey menu gets a word in, and this class is only the eligibility/host/rescue state behind that row's `CatchReleaseCMD` gates - plain market memory flags, no entity, no listener, nothing spawned. Leaving keeps the host flags so the scene re-opens on the next visit; the rescue closes it and points the tutorial |
| `RatingBarEvent.java` | The port counter the sheet's bar version is gated on, and nothing else |
| `FishermanInterception.java` | The boat that is simply *there* when somebody nears a rupture unequipped - and the only thing that lets it off burn 4 while it closes. Its drop point is clamped into the reaches, so a rupture in the inner system no longer parks a trawler against the star |
| `TutorialConstants.java` | Every number the above read |

### `campaign/fish/minigame`
The catch itself. Rules are separated from rendering on purpose.

| File | What it does |
|---|---|
| `FishingMinigame.java` | Rules only: bar/fish physics, progress meter, treasure rolls. A hooked legendary skips the treasure lottery: at least three spawns, every one epic-tier. No GL, no input |
| `FishingMinigamePanel.java` | Draws the track, bar, fish and meter. Until sonar reveals the specimen, the catch is the same rarity-coloured glow as its campaign mote, or the aspect-preserved chicken icon over a slight rarity-coloured backlight while Crablobab's Chicken Profile is switched on; a Sonar Head always replaces either unidentified marker with the hooked species. The target is drawn after treasure so it keeps visual priority, and the live treasure marker uses the custom closed-chest sprite authored for the bar's small resolution. Handles mouse and keyboard; records first-bycatch discovery only when the fish and its held treasure are actually landed; files the per-catch `CatchLogIntel` entry at the readout moment, after treasure resolves, and feeds the same specimen to every tracking `FishRouteIntel` and to the legendary ledger's caught flag; owns the unconditional, once-per-outcome caught and failed sound hooks independently of the optional celebration (registered behind mod ids, currently using vanilla reputation placeholders), plus a lightly pitch-varied cue only when the mote leaves the green catch indicator and one-shot cues for each treasure spawn and successful pickup. It reads the click and unified line-loop controls directly from `FishConstants`, plays the click hook on the compile-time-selected press or release edge, and renews one uninterrupted UI loop every running frame while a short asymmetric envelope swells its volume for held input |
| `FishingMinigameDialogPlugin.java` | Hosts it as a custom *visual* dialog; owns the dev controls and records the exact source rupture and campaign timestamp on landed specimens from either drones or harpoons. It keeps that visual/source anchor separate from the caught mote, so a rupture-based drone catch and a direct harpoon catch both carry the planted chart-request identity into cargo. While the interaction host opens, it scopes vanilla's keep-location-music memory flag to that synchronous call and then restores the anchor's prior value and expiry, preserving the already-playing campaign background track without leaving entity state behind. For the dialog lifetime it also feeds vanilla's frame-scoped music suppressor an IDE-configurable multiplier of the player's current music level, then restores the exact prior runtime multiplier on every resolution/reopen path |
| `FishingMinigameLayout.java` | Per-frame positions for track, meter and result cards |
| `CatchResultPanel.java` | The catch readout: specimen box, stats revealed line by line, and a banner that prefers a gold first-ever species discovery over the same catch's green personal record. A discovery also borrows the aquarium's restrained blue-white surface-light shafts under its ordinary bubbles; records keep bubbles alone |
| `LootResultPanel.java` | The mirror card listing treasure recovered alongside the fish; its side-panel exhibit uses the closed treasure chest while the delayed tally waits, then switches to the open chest and plays its coin-filled opening sound in the same update that reveals the first recovered-item row (including an immediate skipped reveal). Item names wrap inside its default width and rows grow vertically instead of stretching the screen for long blueprint names |
| `CatchCelebration.java` | Flash, backlight and flourish on a landed fish. Also owns the minigame's UI-sound hook, including a pitch-selecting overload for repeated effects. The confetti is bought — see `campaign/fish/crab` |

### `campaign/fish/treasure`
Optional loot found mid-catch.

| File | What it does |
|---|---|
| `MinigameTreasure.java` | A stationary timed pickup that must be held over to be taken |
| `TreasureRoller.java` | Rolls whether treasure appears and what is in it |
| `TreasureAward.java` | What a roll granted, for the loot card |
| `TreasureRarity.java` | Four-tier rarity with weight, colour and the explicit `rank` the loot card's best-of read uses |

### `campaign/fish/entities`
The two forms a fish takes in the world.

| File | What it does |
|---|---|
| `FishEntityPlugin.java` | The swimming mote: motion archetypes, diving (rare and up - the periodic submerge that only deep-reaching gear can follow), held/stunned states, glow, and the exact source rupture retained for catch provenance. Pond motes let the terrain draw that glow inside its stencil; pondless motes draw themselves. Its `init` is where a legendary counts as sighted - every spawn path lands there - and a `phantom` mote (a haunt's decoy) skips that note, fails `isAvailable`, and shrugs off blasts, so no harpoon, drone or beam can touch it. It also carries the legendary defence behaviours: satellite orbit around a shield anchor with tether vfx, the flee mode (a weaving away-line while the fleet presses within range; most species pulse sprint-and-cruise so the chase has closable windows, the moray instead runs, runs harder, and only drops to a short breather, and the False Dawn sprints flat out with fast cruises between - a hard runner, so its minefield is crossed at chase speed; the Lantern Jack never flees at all - it prowls fast sweeping patrol legs, rushes prey, and answers any harpoon contact with seconds of hard alternating jinks - and its flare ring plus the lure state on every mote live here too: a called mote runs at the caller and holds just off its jaws), the flung dash and the moray's surviving travel dash, the shield bubble - a rim-weighted soap-bubble film under a GraphicsLib `WaveDistortion` lens tracked per frame - and deflection flash, the shell-game decoy state (steered by `QuorumShellGame`, fading when its real anchor is gone), and the Lantern Jack's mote-eating - all delegated to `LegendaryShields`, including the radius shared with harpoon collision. Phantoms, shell-game bodies, shield escorts and every shield visual are lamp-bound: they fade with the player's beam strength at the mote (`getBeamStrengthAt`, smoothed) and are invisible outside it |
| `GhostAsteroidEntityPlugin.java` | A rock that is not there: drifts, spins, shimmers additively with the translucent placeholder sprites under `fx/`, holds no interaction. Spawned and removed only by the ghost-asteroid haunt module |
| `HauntMineEntityPlugin.java` | The False Dawn's blinking mine, three tempers by colour: red bursts and shoves, blue interdicts and drags, yellow implodes with an inward distortion ripple and a damage-free pull. Arms after a beat, strobes hard - three times faster near the fleet - and breathes a ring out to its trigger radius on a cycle so its position and reach read from across the field (its render range is widened to match, so the ring survives the mine sitting just off-screen), triggers by proximity or by harpoon strike (`detonate` - the full show from range, but the shove, interdict and pull only land on a fleet inside the effect radius) and fades itself once its effect is spent |
| `BuriedMoteEntityPlugin.java` | Invisible mote under the fabric; `unearth()` atomically replaces it with an ordinary mote, so the searchlight cannot leave a stationary impression behind while a harpoon shoves the surfaced fish |

### `campaign/fish/spawner`
Which fish, where.

| File | What it does |
|---|---|
| `PondFishSpawner.java` | Weighted selection filtered by habitat and catch implement; biased by drone tackle and rumors. A stranger rumor bypasses only its species' normal range, never the implement that can reach it. The Longliner never rolls here at all - it enters the water only through `LonglinerDecoy`'s reveal |
| `BuriedMoteSpawner.java` | Keeps a target buried-mote population around the player |

### `campaign/fish/shop`
The outfitter: upgrade tiers and rig modules bought with fish. Player-facing copy calls upgrade
levels **tiers**, and names modules by their shelf: **Harpoon Tips**, **Drone Cores**, or **Lens
Arrays**. `Tackle` remains the serialized/internal type only and must never appear in display text.

| File | What it does |
|---|---|
| `FishShopDialog.java` | The dialog: tabs, list, detail pane and buy. Its lower-left undo takes paid purchases back newest-first for this visit, restoring the exact fish cargo, credits, prior upgrade/module state and any exact-tier mark cleared by that purchase; closing drops the session receipts and makes what remains final. A successful paid purchase clears that purchase's shopping mark. Modules remain absent until their schematics are known; only a gated upgrade tier appears as a locked row, whose hover explains the purchase-only distinction and final-two-tier rule. Selecting that row replaces the ordinary price with a red schematic requirement and a muted pointer to fishing jobs. Every rendered tier square has its own exact-size transparent hotspot in the list and detail header, mirroring the renderer's geometry without covering neighbouring squares or gaps. Its tooltip reports that exact tier as purchased, available, or locked; only a locked square carries the schematic explanation. It clears the host interaction's options immediately before opening its custom visual, and delivers the close callback once whether it was reached by LEAVE, Escape, or the visual's own dismissal |
| `ShopEntry.java` | Wraps one shelf item — upgrade, tackle or curio — behind uniform price/state/buy. Its `Kind` owns each main tab's registered art. Its purchase guard covers both schematic types, while its visible locked state is upgrade-only because unknown tackle never enters the outfitter list. Its shared icon resolver prefers the optional item texture path and otherwise uses the shelf's registered category mark, exposing both a fresh sprite for custom shop rendering and the texture name needed by stock tooltip cards. Non-slot displays resolve dual-fit modules against the first rig the player owns |
| `ShopGroup.java` | The shelves, which stat ids and rigs belong to which, their shared tab/fallback-entry icon (with separate upgrade/modifier art for Lamps and Drones), and the centralized player-facing module noun for each rig (`harpoon tip`, `drone core`, `lens array`; generic `rig module` for shared fits) |
| `ShopPricing.java` | Per-campaign seeded prices in credits and fish. The capability-changing Breach Coupler occupies the unique top tackle tier: 20,000 credits plus a tier-five named catch ask; Retrieval Head shares the high-value tier below it. Exact-rung lookup lets a promised schematic preview the same later price even if the player's current rung changes |
| `ShopMarks.java` | The shopping list: upgrade marks identify one exact target rung (`stat:id:level`), including a currently locked rung, while tackle marks identify the module and rig. Legacy whole-ladder keys migrate to the next rung. A learned rung's mark feeds the route planner and hangs the quest-yellow dot on every fish that would pay for it; before it is learned, the same identity dots a matching schematic job reward instead. `mark`, `unmark` and `toggle` keep that persisted set and its wanted-fish cache in step; purchase clears a mark while session undo restores it. `isMarked` is the marks alone, which only the outfitter asks; `isWanted` counts every active `FishAsker` in the log too, skipping intel as soon as it begins ending so completed jobs cannot remain in a fish's `Required by` reasons. The cache retains named asks rather than bare requirements, so cargo's dot and its tooltip reason are always derived from the same snapshot |
| `FishAsker.java` | The interface anything waiting on a fish implements — `FishJob`, `FishingIntro.IntroIntel`, `FishermanQuest.QuestIntel`. What `ShopMarks` walks the intel log for, so a species an errand wants wears the mark whether or not the errand is a bar job |
| `FishCurrency.java` | Counts and spends fish as payment, worst specimens first |
| `FishRequirement.java` | An ask: count, rarity, grade, species, region, exact source rupture, earliest catch timestamp, coherence — how to describe it, format its live aboard/needed progress, identify its exact rarity-bearing substrings, and apply their canonical colours to UI labels. Its shared arbitrary-text resolver scans the merged fish table longest-name-first, so species embedded in dialogue tokens, reward prose, rumors, or selected-contender descriptions always receive their rarity colour without substring collisions. It also decides whether a species could ever satisfy an ask from maximum dimensions and reachable implement/method constraints without pretending individual grade, origin or coherence is guaranteed |
| `ShopStorage.java` | Migration only — returns fish left in the removed store/retrieve counter. See Dead or dormant |
| `ShopSchematics.java` | Persistent quest-earned purchase permissions for stocked tackle and each of an upgrade ladder's final two rungs. A freshly learned schematic also lands in a fresh set that the shelf rows read as a gold New! tag beside the indicators - cleared per ware by buying or marking it, wholesale when the shop closes, and never set by the dev bulk grant. Ownership/current level counts as permission for migrated saves; gated upgrade plans become eligible sequentially when the preceding rung has been bought. Its bulk grant records every real outfitter permission without buying hardware or levels, for developer campaign setup |
| `ShopRowPlugin.java` | One shelf row on the shared `ui/ListRow` skeleton, plus the shopping-list ring: the ring's slot splits the click. A fresh upgrade's gold New! label sits immediately left of its tier pips; non-ladder rows retain the label at the right edge with their state shifted left. The ring's explanation is a stock tooltip the pane hangs off a transparent hotspot over the slot - see the gotcha on hand-drawn controls |
| `ShopTabPlugin.java` | One tab button |
| `ShopHeaderPlugin.java` | Title, credits and the per-rarity fish purse |
| `ShopDetailHeaderPlugin.java` | The detail pane's item-or-category portrait, name and ladder readout |

The drawing helpers these plugins share (`ShopUi`) live in the top-level `ui` package.

### `campaign/fish/items`
Fish in cargo.

| File | What it does |
|---|---|
| `FishItems.java` | Ids and the encode/decode used by all three item kinds, plus `stow` — where a landed fish actually goes — and the single post-stow catch hook which fans progress updates out to chart requests, tutorial errands and accepted jobs; `unbox` expands containers into independently selectable specimens, while `packIntoCrates` compacts loose cargo to one crate per species for transaction screens |
| `FishItemPlugin.java` | One landed specimen; right-click stows it into a bundle. Packing snapshots every source, removes the clicked source and writes the replacement first, then clears the other sources, so vanilla fills the clicked cargo cell instead of moving the result to the earliest vacated cell. Its tooltip explicitly names the yellow dot and every purchase or errand currently asking for that specimen. Owns the shared five-band coherence ladder used by cargo, catch results, ruptures, the terrain readout and the Fisherman's portraits; its first non-stable rung is `unsettled` |
| `FishBundleItemPlugin.java` | A crate of one species; right-click unpacks with the first specimen inheriting the crate's cell, ctrl sweeps the hold into a pile anchored in the clicked cell, and the contained species' art is perspective-fitted to the four measured corners of the box label. Its yellow-dot tooltip reason is the union of its contents |
| `FishPileItemPlugin.java` | Every fish aboard on one line; right-click explicitly removes the pile before adding its contents so the first restored loose specimen or crate inherits that cell. Existing cargo cells stay fixed while the remaining outputs append. Its yellow-dot tooltip reason is the union of its contents |
| `FishItemRenderer.java` | Icon plus rarity and grade pips over the cargo cell, including a vanilla-blueprint-style four-corner icon pass for box labels |

### `campaign/fish/crab`
Crablobab's per-port bar roll, five regular wares, rotating backdrop, and empty-shelf bass. The stall dialogue, labels and ordered option stream live in `rules.csv`;
`CatchReleaseCMD` mounts those options immediately so each ware can carry its structured description
and highlighted credits-and-crabs tooltip. Chicken Profile follows the same affordable/shortfall
pair and purchase routing as the other switchable curios: every refusal returns to the stall, and
successful purchases either pass through the first `Baha?` explanation or return directly.
Its full sales pitch keeps Crablobab expansive and physically comic while stating the marker,
Sonar override, permanent switch, and complete cost on both affordability branches.
The same bridge mounts the campaign-persistent
`CrablobabIdentity` person card on every entry path, then saves that person visual as the stall
return target so opening merchandise and returning from backdrop previews cannot fall back to the
bar scene. Its portrait is resolved from the `graphics.characters` registration in `settings.json`,
never from a hard-coded asset path. The ware
state and prices are Java. The first
switchable-curio purchase routes through the shared `Baha?` correction; after that campaign-long
answer, later switchable purchases give the short outfitter reminder directly. The switch itself
lives in the Fisherman's outfitter. His full introduction
records the first meeting when it opens; later bar visits use the established-customer greeting and
retain routes to the merchandise, crab question and exit.
Every displayed credits-and-crabs price in a ware pitch uses the rules engine's highlight colour,
matching the conservatory and backdrop price treatment. Insufficient-payment variants repeat the
full ask before explaining the shortage, so price information never disappears behind affordability.
Every merchandise pitch returns to the stall through an explicit `Another time.` decline; Continue
is reserved for completed transactions and the one-time Baha correction.
Celebration Charges leave the streamed stall menu as soon as their permanent bought flag is set;
the transaction cannot be presented a second time.
An explosive detonation records the struck species or fleet as a pending story; the two repeat
greetings query that saved state directly through mutually exclusive command predicates rather than
depending on a condition's local-token side effect. The pending greeting names that latest target
once, acknowledges it, and then returns to the ordinary greeting.
The Conservatory-plans option is gated by the ware's combined sold-or-industry-known ownership
predicate, so receiving the chip removes the offer before the player has to consume it.
When every regular ware is owned and this port has no backdrop, the bar encounter remains available:
the stall says its shelves are empty and offers a repeatable, minimum-size Green Bass for a wildly
inflated credits-and-crabs price. Its hover card states both the Terrible quality and the overpricing.

| File | What it does |
|---|---|
| `CrablobabBarPresence.java` | The rules row's availability predicate. Dev mode returns true in every bar; ordinary play rolls independently in each eligible market using vanilla's bar seed and `barEventProbOneMore`, caches the result until that market's normal bar re-roll, and caps it at 60 days. Market-local state allows the same merchant to be present in several ports at once |
| `CrablobabIdentity.java` | The campaign-persistent `Crab Merchant` shown by all three bar-entry greetings. It resolves `crabolabob.png` through the registered `graphics.characters` sprite id, refreshes the data-registered custom rank for old saves, and mounts the minimal vanilla person card without adding him to a market's permanent people or comm directory |
| `CrabWares.java` | The five regular wares, what each costs in credits and crabs, where each one's ownership lives, and which of them has a switch. Chicken Profile is a one-time, switchable cosmetic purchase whose live state replaces only the minigame's unidentified mote; Sonar keeps its exact-species reveal. It also constructs and sells the empty-shelf fallback as the Green Bass row's exact minimum-size Terrible specimen for 10,000 credits and one crab. The explosive head is offered whenever none is currently owned, so detonating its single charge reopens the same Crablobab purchase loop; each actual blast also replaces the saved latest-target name and marks it for one acknowledgement at the next stall meeting. The conservatory is a vanilla `industry_bp` chip with the industry id in its data — the game's own plugin names it and teaches the faction, so nothing here knows what a blueprint screen looks like. Its shared conservatory-plans ownership predicate treats either the completed sale or a faction-known industry as the point when aquarium scenery becomes relevant. |
| `CrabBackdrops.java` | The rolled scene under his arm: one at a time, a rotation down `backdrops.csv` rather than a roll, and the port remembers what he had there — so the same rock offers the same thing until it sells and the next rock offers the next thing. A completed sale leaves that port's backdrop slot empty for 60 days before the next scene is assigned. Priced off rarity; anything already owned drops out of the rotation. The rotation remains empty until the conservatory plans are owned, so no port can assign or advertise scenery before that progression point. |

### `campaign/fish/tackle`
Modules bolted to a rig.

| File | What it does |
|---|---|
| `Tackle.java` | The modules, which rig each fits, the optional outfitter icon path for an individual module, and the multipliers each applies. `coherenceBonus` is the odd one out: it is taken off the water's aberration at the catch site rather than read during play. `BREACH_COUPLER` is the drone rig's permission to use lamp-cut openings in open space; `RETRIEVAL_HEAD` marks a player harpoon mote hit for a capped charge refund |
| `TackleManager.java` | Two facts: which modules are **owned**, and which is in each rig's slot. `get()` always returns non-null, possibly `NONE`; `consume()` removes a consumable from both facts at once. Gear-dependent modules stay off the shelf and out of rewards until their prerequisite is owned, while an already-owned permanent module remains refittable for save compatibility |

### `campaign/fish/map`
The sector-map fish filter.

| File | What it does |
|---|---|
| `FishMapFilterScript.java` | Inserts the filter button, resizes the map, mounts pane, overlay, planner popup and the route save dialog (closed automatically if the route is cleared beneath it, its confirmed name/purpose minted into a `FishRouteIntel` added on the spot); feeds the arrows of the plotted route and of every saved route (dimmer, live duplicates skipped) to the map's own arrow list. It stages every outside handoff until the real map and pane attach: Codex species focus, complete intel requirements, or a place-only overview/system center. A Codex return preserves an already-open underlying map instead of re-selecting vanilla's toggle-style MAP tab and closing the core UI. Request-constrained category meshes bypass the ordinary cached whole-category union. Species opened without available range data produce the overlay's no-data state instead of a mesh or camera jump; dev mode supplies a complete non-persistent chart. The planner borrows the sidebar's slot, so `paneStanding` tracks whether the sidebar is actually on screen - `applied` only records that `activate()` ran, and a failed hand-back would otherwise never reconcile |
| `FishMapPane.java` | The side panel: planner button, search, type chips, species list, the coherence toggle on its floor. An intel handoff replaces stale pane state: one-to-three exact species with available range data become ordinary selections, while category/quality asks become a restricted survey union of every charted species that could satisfy the request. **Deselect all** is available for visible picks, active category filters, or an intel-only restriction; it clears picks, every category chip, and any hidden request allowlist while the empty type set deliberately leaves the species list itself unfiltered. A request or direct species focus with no available range gets a centred no-data state and deferred reset button; reset safely replaces its own list on the following advance, restores the unfiltered overview, and re-cuts the map shading. Missing species IDs are treated as unavailable rather than silently ignored |
| `FishPresence.java` | What the player is allowed to see, and where. Ordinary play gets range data from a catch or unlocked location entry; dev mode receives the complete chart as a computed view without mutating those save unlocks, so every dev-visible habitat-backed row has matching meshes, focus coordinates, status and route data. Its optional allowed-species set constrains both rows and category range unions for an intel request, including an intentionally empty result when no compatible range is known |
| `FishPresenceField.java` | Builds merged organic blobs — metaball field, marching triangles, smoothing |
| `FishPresenceOverlay.java` | Draws the blobs through a stencil, striped where they overlap; route badges - full-size fish icons clustered pair/triangle/square/pentagon inside a ring sized to the cluster - for the plotted route and every saved `FishRouteIntel` alike, stops merged by system so overlapping routes share one ring; the `TRACK ROUTE` and `X - CLEAR ROUTE` labels for a live route, and the coherence heat map under it all. The tracking label asks its listener to open the save dialog, and greys to `ROUTE TRACKED` once a `FishRouteIntel` with the same stops exists (cached per route object - a fresh overlay re-checks on the next map screen). An uncharted focused species suppresses its range and draws a square-bordered, bold red `NO DATA` notice at the centre of the visible hyperspace map |
| `CoherenceHeatField.java` | The sector's stability as a gradient - Aberration sampled onto a light-year grid on a per-frame budget, including the clear five-light-year basins colonies cut into unstable water. Bare points, not systems, so it needs `openSpaceReading` to answer with both complete indexes; `ALPHA_CAP` is the layer's single ceiling and `HEAT_EASE` above 1 keeps the bottom of the range faint; bounds are the sector rectangle exactly, because past it `getAbyssalDepth` measures how far off the map you are rather than the water |
| `FishSystemPane.java` | The system view's sidebar: the viewed system's catch as holder cells, same map hand-over as the big pane |
| `FishHolderPlugin.java` | One round fish holder - rarity ring, art/mark/question - shared by every screen that lines fish up in circles |
| `FishListRow.java` | The species row both panes' lists extend, on `ui/ListRow`: caught-circle, name, wanted dot, F2 to the codex. The dot is the whole who-wants-it story - the tooltip names the askers |
| `FishRoute.java` | The saved route: ordered stops in the save, until closed by hand |
| `FishRoutePlanner.java` | Suggestions from every `FishAsker` in the log plus the shopping list, broad asks expanded to whatever could pay them; cover + exact ordering, stability- and slipstream-aware. Suggestions, coverage and validation require available range data: learned data in ordinary play, or the complete computed chart in dev mode |
| `FishRoutePopup.java` | The planner in the sidebar's slot, built from the sidebar's own parts: search, chips, pick up to five, plot. Its rows follow the same available-range capability as the map, including the complete computed chart in dev mode |
| `FishRouteSaveDialog.java` | Small centred dialog over the map, in the sidebar's visual language: route name and optional-note fields with direct placeholder text, start-tracking and close. Pure UI - the host turns the confirmed pair into a `FishRouteIntel` |
| `FishTooltips.java` | The one species tooltip every fish icon answers a hover with |
| `FishIntelPlanetPanel.java` | The intel Planets view's fish panel, beside the planet card |
| `FishType.java` | Filter categories with colour and their registered pane-widget sprite id |
| `CoreUiCrawler.java` | Reflection into the obfuscated core UI to find the filter row |

### `campaign/fish/codex`
Codex pages for species.

| File | What it does |
|---|---|
| `FishCodex.java` | Installs the category and per-species entries; owns every guarded custom/F2 link into a fish entry |
| `FishCodexEntryState.java` | The central three-state unlock policy (`UNKNOWN`, `RANGE_DATA`, `CAUGHT`): index visibility, link access, description/art, records, range and map action all derive from the landed count and range flag rather than the legacy `hintOnly` field |
| `FishCodexEntry.java` | One page driven by `FishCodexEntryState`: range-only entries show the species silhouette in list and detail both - the detail draws the shared rimmed `FishIcons` portrait live, while the list uses vanilla's private icon tint for the same alpha-shaped black body - and full colour/description remain catch-locked; every known range, bought or caught, gets the same guarded staged jump to the pre-filtered hyperspace map. Unknown-state copy states whether the missing requirement is a landed specimen or range data. A legendary's page identifies it as a unique specimen, states that it will not return once landed, and says directly that no range data is available |

### `campaign/fish/legendary`

The whaling chase: unique fish, one host system each, and the haunting that marks it.

| File | What it does |
|---|---|
| `LegendaryChases.java` | The persistent ledger: per legendary, its one host system, the last sighting, whether a harpoon has provoked it this residency, whether the Longliner's disguise is blown, whether that disguise has ever been exposed, and whether it has been landed. Both range matching and host lookup stay dormant until `FishingIntro.isComplete()`, so ordinary legendary motes and the Longliner's boat cannot spawn before graduation. The permanent encounter bit drives post-Longliner dialogue and migrates old revealed/caught saves. A row created through `getState` carries no host, so `getChase` self-repairs any null-host row on sight - without that, such a species could never spawn or haunt, silently. A sighting starts the ninety-day relocation clock; the fish re-occurs in its host until caught, moves on when the clock runs out unseen-side (never out from under a player in-system), and once caught never spawns again. A revealed Longliner skips the clock entirely and relocates the moment the player leaves its system. Host picking prefers systems without civilization (`OuterReaches.isPopulated`), falling back to settled space only when no unsettled candidate matches at any relaxation rung. Relocation clears the provoked and revealed flags with the sighting clock, but not the permanent encounter memory. `FishRanges` answers every legendary range question from here |
| `LegendaryHaunt.java` | Stage manager, transient, provocation-and-sighting-driven: a haunt begins only once the fish has been provoked - a harpoon has touched it this residency - and its own mote has been visibly seen near the fleet; it then ramps in over a few seconds, holds for a minute after the fish is lost, fades out, and needs a fresh sighting to restart. Intensity is pushed into every module each frame - spawning modules stop escalating below full, screen effects scale with it. Leaving the system, landing the fish, or the fish moving on still tears everything down at once, and registration sweeps the haunt tag from every location so a hard exit strands nothing in a save. Its narrow testing reset tears down the live modules before `SpawnFish` starts another legendary chase. Each species levies only a couple of modules from the pool, never all of them |
| `HauntModule.java` / `BaseHauntModule.java` | The module contract - advance plus a no-trace cleanup - and the shared base: spawn tracking under the haunt tag, near-player placement, and hard removal that despawns fleets and unhooks entities in the same frame |
| `DistractionMotesModule.java` | Phantom motes in the legendary's own colours: unhookable, unslowable decoys that dissolve when approached, clustered around the fish itself while it is in the water and around the player only when it is not |
| `InterdictionPulse.java` | The shared sourceless interdiction: burn abilities knocked onto cooldown vanilla-style, plus the abort-side release that clears the lockout. Fired only by things that touched the fleet - the moray's flung motes and the False Dawn's blue mines - never on a timer |
| `MinefieldModule.java` / `entities/HauntMineEntityPlugin.java` | The False Dawn's minefield: dense, frequent waves of strobing mines seeded across the player's course, each breathing a position-pulse ring out to its trigger radius, with generous trigger and effect radii - the field is meant to be felt at chase speed. Red bursts and shoves the fleet away, blue delivers the interdiction pulse with a short dragging slow, yellow implodes - an inward GraphicsLib ripple through `CampaignDistortionRenderer` and a damage-free pull toward where it was. Nothing harms the hull, a thrown harpoon detonates a mine from range as the field's counterplay, and every mine is hard-removed on cleanup |
| `SensorGhostsModule.java` | A steady stream of vanilla sensor-ghost entities crossing the sensor bubble, unclickable, fading before anything is made of them - and a share of them stalk, bending their course after the fleet for as long as they live, so the contacts demand reactions instead of reading as scenery |
| `GhostFleetsModule.java` | Dark-transponder fleets on intercept courses - up to two at once, ignored by all other fleets, unclickable, comm-dead - each despawning the instant it should have arrived. Hand-built per vanilla's CustomFleets recipe (members, then `forceSync`), with a flat detected-range bonus so the contact actually reads on sensors from spawn distance - dark frigates are otherwise invisible out there and the haunt plays to an empty theater |
| `FakeWrecksModule.java` | Wreck entities seeded around the player's course that fade out at working distance, before any salvage dialog could open. The sensor presence vanilla wrecks get from the salvage generator (`addCustomEntity` bypasses it) is applied by hand - profile, detected range, extended detection - so the bait shows up from afar |
| `ChromaticAberrationModule.java` / `CoherenceSurgeModule.java` | The screen effects: full-frame chromatic aberration through `rendering/plugins/ChromaticAberrationOverlay` (the manta's - its abyss already runs coherence at the floor), and the low-coherence overlay held at full force through the script's haunt floor (the False Dawn's). Both enter at a visible floor rather than creeping up from zero, ease in from there, and cut to nothing on cleanup |
| `SlipDashModule.java` | The moray's escape: a curving surge away from the fleet, steered per frame, that grows a live in-system slipstream behind it on vanilla's sensor-ghost recipe (`GBIGenerateSlipstream`): segments laid as it swims and faded in behind the fish, the current running toward it, and the trail rolled up oldest-first so the standing window of stream chases the fish toward where it went. Segments are only ever faded, never removed - dropping them breaks the texture offsets - and the fully-faded terrain is hard-removed. Dashes come often (a nine-to-sixteen-second cadence), run for variable lengths, and curve on a per-dash randomized wave, so no two escapes trace the same route |
| `QuorumShellGame.java` | The Quorum's endgame, session-transient and rebuilt from the decoys' persisted anchors on load: with the splinter escort gone, the school divides into three drifting bodies that trade places - two empty, one real, and the real one never moves during a split. An empty body is a Quorum Splinter under the hood - same row, same rare-band minigame, same catch - but its decoy anchor makes it glow in the real one's colours everywhere: campaign mote, minigame and celebration, through the mote-keyed `LegendaryShields.getPresentedColor` carried on the minigame as its presented colour. Only the result screen tells, and a splinter landed while the shield is already bare never thins the escort or resets its regen. Landing or blasting a body deals a fresh one from the real one's position, and a body on somebody's line sits the shuffle out. The old harpoon pop is sidelined behind `POP_DECOYS`, off by default |
| `LonglinerDecoy.java` | The Longliner's residency as a boat: a full Fisherman fleet through the same fittings as the real ones - identity, interactions, icon, coherence presence - flagged so `FishermanSpawner`'s reconciliation never books it. The one test it fails is the player's own breach lamp on its position: lit, the boat reports its despawn, loses its AI and is removed from the encounter immediately before the running mote surfaces in its place; the disguise stays blown until relocation. Never affected by any boat's own lamps |
| `GhostAsteroidsModule.java` | A temporary asteroid field with nothing solid in it: a cluster of `GhostAsteroidEntityPlugin` rocks drifting as one body, topped up while the chase runs. The field follows the chase: rocks left far behind fade to free the cap and the center reseeds beside the fleet, since the fleet outruns any static field |
| `LegendaryShields.java` | The defences, answered at their shared shield radius, and their presentation: the Longliner's shield is red, the Quorum's blue, and everything else - the Lantern Jack included - wears the common green shell. The Longliner: a hull shield only an Explosive Head pops, permanently - until then it deflects everything and raises no haunt. The Quorum: a shield held up by three fast-orbiting Quorum Splinter motes (`quorum_shard`, a harpoonable rare-band catch of its own, spawn weight zero so it exists only as escort); the shield stands while any orbit, splinters regrow one a month, and the haunt stays gentle to compensate. The Lantern Jack: the base shell plus up to three stored shells layered on top, earned one per lamp-exposed mote it hunts down and swallows - a hunter that is never placid and wears its larder openly as extra shield circles; a hit burns a stored shell first, and only with the larder empty does the base shell answer; running out of shells fires the flare call (`lureFlare`, on a cooldown held by the mote): a shockwave ring drawn to the full pull radius while every edible mote within it turns and runs at the Jack - prey delivering itself to the eater. Everyone without a named shield - moray, False Dawn, manta - wears the base shell: one deflection, regrown ten seconds later on the mote's own clock, so landing a throw means following the first with a second inside the window. All state lives in the `LegendaryChases` ledger, so a popped shield stays popped across relocations and abandoned chases; a bare Quorum hands over to `QuorumShellGame`. Every legendary is placid and slow until the first throw of a residency touches it - that throw always deflects, whatever the head, and wakes the fish: from then on the flight envelope takes over (`isFleeing` plus the mote's own flee mode - a weaving away-line in sprint-and-cruise pulses; the moray gets its own envelope instead: a wider weave, a farther pressure range, and a run-run-harder-breather cycle, so its chase is wild with only short intermissions; the False Dawn shares the far pressure range and runs flat out, because its minefield only works on a fleet actually chasing it). Three exceptions: the Quorum never flees - its fight is the escort and the shell game; the Lantern Jack stands its water - prowl, hunt and jink bursts set its pace instead (`isProwler`, driven by the mote's own prowl mode); and the Longliner skips the placid stage entirely, running from the moment its disguise burns, with no first-throw grace: a first explosive contact pops its shell on the spot. Every shield event - deflection with a per-species hint, the pop, the dive after a blast, a swallowed mote, a thinned escort - floats as a direct status sentence at the thing it happened to (`say`, shared by the shell game's decoy result and the Longliner reveal), never the message feed. Neither the eater nor the moray's flung dashes ever consume a quest-planted mote or a shell-game body |
| `MoteDashModule.java` | The moray's extra countermeasure: exposed motes near its own mote are flung at the fleet like a skillshot, aim fuzzed so some miss; a connecting mote delivers an interdiction pulse and burns out, and an abandoned chase releases mid-dash motes unhurt. Its water is usually empty - dead host systems, and it runs from the lamps - so with nothing real in reach it conjures an ordinary common mote in its own wake and throws that; conjured ammunition is tracked as a haunt prop and removed with the haunt, while real victims stay |

### `campaign/fish/coherence`
The low-coherence overlay: the screen warps purple at its edges while a rig runs, an open pond
is close, or one of the trade's boats is - whichever of the three pulls hardest, weighted by
distance for the last two.

| File | What it does |
|---|---|
| `CoherenceOverlayScript.java` | The rules: which of the three sources is loudest, how hard, the ease in and out, the whisper loop, plus a session-only haunt floor a legendary chase can hold at full force. Drawing is `rendering/plugins/CoherenceOverlayRenderer` |
| `CoherenceTerrain.java` | The terrain-bar line. Invisible terrain covering a whole location whose `containsEntity` is "is the overlay up" rather than a distance — IndEvo's trick, so nothing has to be moved under the fleet |

### `campaign/fish/constants` · `campaign/fish/intel`

| File | What it does |
|---|---|
| `FishConstants.java` | Every magic number for minigame, result cards, celebration, treasure and codex, including the IDE-editable input sound ids, click-edge toggle and campaign-music volume multiplier |
| `FishIntelIcon.java` | Central source icon resolver for fish-targeting intel. It collapses every requirement and `anyOf` branch to lamp-only, rupture-only, or mixed/open; drones and exact source ruptures count as rupture-only, while Harpoon asks without an implement remain open |
| `FishIntelMapButton.java` | Shared intel navigation with three explicit contracts: **Open fishing map** narrows habitat data when fish are the target but no destination is known; **Plot route** uses vanilla's course planner for system-bound fish requests; **Set autopilot** uses the same planner for objectives with no fish target. Jobs, tutorial/chart quests and rumors all hand off through it |
| `FishIntelNotifications.java` | Shared notification delivery for every custom fish intel: new tutorial, chart-request and rumor entries enter through vanilla's forced comm queue and appear on the first unpaused campaign frame; updates use a zero-day vanilla delayed script. Its separate inline path renders the same vanilla intel-added card without adding or dequeuing the entry, used by the tutorial's explicit `showIntroIntel` command and fleet acceptance; bar jobs retain vanilla hub acceptance's built-in card |
| `CatchLogIntel.java` | The per-catch ledger: one intel entry per landed specimen under its own **Catch log** category (the tag string is the category button, like vanilla's Fleet log). Filed once from the minigame's readout moment - after treasure resolves, so the entry knows its bycatch - and added silently, never marked new, sorted newest first, no map location. The list row carries grade with measurements, coherence, tackle through implement, date and system, and bycatch rarities; the open panel adds the shared backlit portrait stage, the item-tooltip stat block with value, chart-request provenance, and the full bycatch receipts kept as words |
| `FishRouteIntel.java` | A plotted route saved as a standing intel entry under its own **Fishing routes** category, with the player's name and optional note from the save dialog. Added to the manager directly, so it exists the moment the save confirms. Keeps its own copy of the stops - the live `FishRoute` keeps being replotted and cleared. The minigame's landed-catch hook bumps a per-species caught count on every tracking entry and sends the shared delayed update, so a needed catch notifies through the ordinary intel-update toast. The open panel shows the species strip on the shared icon stage, the quoted note, then each stop as its own bulleted block: every fish with its caught count and whether it is rupture-only, lamp-only or either, in rarity colours. Below that, current requests for the route's species (jobs, errands, marks - read live from the planner's suggestion walk), and two full-width sidebar buttons: re-plot the saved stops onto the hyperspace map, or stop tracking and remove the entry. Saved routes also draw their fish badges and arrow chains on the hyperspace map like the plotted route (through the overlay and filter script), and the entry supplies its own arrow chain to the intel screen's map |
| `FishMapIntel.java` | **Dead.** A husk kept so old saves load and delete it themselves |

### `campaign/ponds`
The pond, as terrain.

| File | What it does |
|---|---|
| `terrain/MaskedFishingPondTerrainPlugin.java` | The live pond: activation, motes, depth field, hole rendering, temporary and visual-only ponds; discoverable ponds return the registered unstable-fabric map glyph while look-only ponds remain iconless |
| `listener/PondCreator.java` | Fills each entered system toward its planet-scaled pond target, capped at two, and finds clear spots away from planets, ponds, nebulae and rings; starless procgen systems use the base centre clearance without a stellar-radius offset |
| `listener/OnJumpPondSpawner.java` | Triggers pond creation when the player jumps into a system |
| `scripts/PondCameraFocusScript.java` | Eases the camera onto an open pond and closes it once left behind. The Luna setting can keep the viewport under manual control without disabling the lifecycle half of the script; changing it live releases or reacquires external control while the pond remains open, and missing settings data preserves the default snap. Each acquisition snapshots the live viewport immediately before clearing Free View, then eases that displacement independently of the fleet-visible destination clamp; even a viewport wholly off the fleet therefore begins without a snap, and reacquiring the same pond uses the new camera position rather than stale transition state. An in-range open pond takes control on its first unobscured frame; the near-fleet handback threshold applies only while returning |
| `renderer/PondDepthField.java` | Motes of light spiralling at depth inside the pond |
| `renderer/PondHoleRenderer.java` | The stencil-and-gradient hole look. Dormant: `PondConstants.POND_HOLE_LOOK` is off, so the shader swirl in the terrain plugin - the pond's rolled-back original look - is what draws |
| `renderer/RippleData.java` | One ripple emitter, spawning ring renderers into LunaLib's list |
| `renderer/UnstableFabricRippleTerrainRenderer.java` | Extra randomised ripples around the main one |
| `constants/PondConstants.java` | Placement, pond-camera setting and timing, depth field, hole, opening distortion |
| `entities/StenciledFishingPondEntityPlugin.java` | **Dead.** The old custom-entity pond |

### `campaign/crime`
What harpooning a fleet costs, and what running the breach lamps over somebody's head costs.

| File | What it does |
|---|---|
| `LampOffence.java` | Where the lamps may be run and what a stop costs, plus the burn counter — a stop settles the burn it was about, so putting the lamps out and lighting them again is a fresh offence rather than a continuation of the settled one. Any patrol objects to lamps over an inhabited world regardless of that world’s flag; away from one, a flag polices only systems it holds something in and only the Church and the Path object. Warning history lives on the current system center with a separate count/timestamp pair per enforcing faction, so neither another flag nor the same flag in another system inherits the ladder; legacy sector-global counts are intentionally inert. Four rungs: warning, fine, inspection, guns, the last only reached by doing it again inside a month |
| `LampPatrolResponse.java` | Patrols coming over about the lit lamps. Nothing is dispatched — while the lamps burn, every eligible patrol which can see the player puts an explicit intercept at the front of its existing assignment queue, while vanilla's pursuit flags keep the tactical AI committed; patrols already in battle are never pulled out. The first responder whose encounter opens claims that burn's dialogue and releases every other responder by removing only the temporary intercept, so each resumes its interrupted duty and no second queue forms behind the winner. Putting the lamps out ends that burn but does not cancel committed stops, so the existing warning/consequence ladder is still booked when the first hail opens; `CatchReleaseCMD lampStop` adds one lights-already-out bridge paragraph before that unchanged dialogue when needed. Relighting after lights-out starts a fresh run once that encounter is settled. The three-day unresolved-burn retry is keyed by both original system and enforcing faction, retained on each patrol until cleanup, so a failed approach cannot suppress another jurisdiction |
| `HarpoonOffence.java` | Incident history, outstanding debts, evasions, the three-point per-hit rep loss, and the escalation ladders. Armed crews turn on you at the second hit; unarmed ones are split by strength — a crew that is plainly outmatched (`isOutmatched`, vanilla's own 1.25× engage threshold) and has somebody to tell (`isCivilised`) runs on the *first* hole with an emergency burn and fetches a patrol, and everyone else works ignore → run you down for the bill → run and tell. Once a civilian becomes hostile, whether through a charge or the incident's reputation loss, that ladder ends: a fleet clearing the reciprocal 1.25× threshold gets an explicit player intercept, while every other fleet clears any conflicting pursuit and flees. `isPlayerIdentified()` is the transponder, and is what decides whether anybody can name you |
| `HarpoonPatrolResponse.java` | Sends one patrol at a time after the player. Any faction **not hostile to the offended one** will take it — the infraction belongs to the space, not to a flag |
| `HarpoonWitness.java` | An unarmed crew flying to a patrol to report it. The report lands on arrival, so it can be outrun, jumped away from, or shot down; it snapshots whether the original victim permits a private revenge contract so a failed report cannot turn a bounty or no-impact target into one later. A later repair demand, flight, or hostile response abandons the witness errand without touching the replacement orders |
| `HarpoonHitman.java` | Mercenaries, when there was nobody to report to. Only a faction that owns a visible size-three-or-larger market in the economy may book one, excluding Remnants and other non-colonial factions without a faction blacklist; once funded, however, the contract survives the loss of the client's final colony and its hail files the provenance as **Call From The Grave**. Bounty targets (vanilla person bounties and MagicLib bounties) and fleets marked for low or no reputation impact are also ineligible. Every remaining request, including a charge recovered under a live transponder, makes one 30% booking roll. A booked contract waits one month before dispatch and then holds for the player's next ordinary system, with only one pending or hunting at a time. Their map name identifies the client faction, while their memory carries the original fleet, location, recovered ROD projectile and an 80,000-120,000 credit cancellation price into a one-time forced hail. Paying clears every hostile, aggressive and pursuit source before retargeting the AI and assigning the hunters to despawn; cutting the link uses vanilla's no-text/no-Continue exit, and later uncoloured comm requests reach vanilla's denial. The hunters carry vanilla's full no-reputation-impact flag, not its reduced-impact flag |
| `HarpoonedFleetFID.java` | Vanilla's encounter dialog plus one line, and a comm link highlighted only while the crew is actually owed something — `wasHarpooned` stays true for a month and colouring on it alone left a settled bill looking unsettled for weeks. A one-shot contact flag enters the comm link immediately after vanilla initializes the encounter; failed dialog opens clear the request so a later manual hail is not hijacked. |
| `CatchReleaseCampaignPlugin.java` | Hands harpooned fleets that dialog at the narrowest priority - the one custom encounter screen left. It also recognizes the one-shot contact request long enough to supply that dialog for fleets which do not carry an offence record. |

### `abilities`
Three rigs — searchlight, R.O.D., harpoon. Each is `ability/` (the plugin), `constants/`
(tuning), and usually `entities/`; `charges/` is shared between them rather than a rig of its own.

| File | What it does |
|---|---|
| `FishingRigs.java` | One answer to "is any rig running" - lamps lit, swarm out, or a line in the water |
| `charges/BaseChargedSkillshotAbility.java` | Shared charge-pool rearm for the charged abilities; bans them all from hyperspace. A partial pool keeps the stock refill indicator moving toward the next charge without disabling charges already in hand, using a lighter shade while usable and vanilla's full cooldown shade once empty |
| `rod/ability/PondInteractionAbilityPlugin.java` | Unlocks the nearest pond, then casts and recalls the swarm; away from any pond, a fitted Breach Coupler plus lit breach lamps sends a roaming one instead. Lit lamps disable the stock ROD rather than granting that mode for free. An occupied camp-job rupture locks new ROD deployments while always preserving an existing swarm's recall. An in-flight opener disables another shot until its rupture activates. The button stays active but disabled once only catch carriers are returning, since no drone remains to command. Selects the pond-opening UI cue only for an inactive-pond opener; aimed, roaming, and recall presses use the drone-dispatch UI cue |
| `rod/entities/RodMoteEntityPlugin.java` | The mote flown at a pond to open it. Its one center-arrival flash starts the opening cue and replaces the old searchlight placeholder with the dedicated boom. The live mote is also the authoritative in-flight opening state, queried by target rupture so the ability cooldown cannot create a duplicate opener |
| `rod/entities/FishingDroneEntityPlugin.java` | One drone: launch, orbit, chase, return — steering, not pathing. Committing to a new target emits the directional target-lock report from passive orbit and while still flying back to that orbit after a broken chase; only the initial launch-to-ring flight stays silent. Its circle's centre is asked for per frame, so a roaming drone flies the same circle around the fleet. A catch carried home is marked held for the whole return and fade, so the harpoon's shared takeability gate and every other rig reject it |
| `rod/scripts/FishingDroneSwarmScript.java` | Owns one cast: launches the first drone immediately and queues the upgraded remainder at the configured offset, with one world-positioned report per entity; recall cancels that queue before bringing the live drones home. It assigns chasers, layers the directional impact and catch reports at confirmed mote contact only after the catch path accepts it, and handles recall. Four hooks — search centre, search area, what counts as fish, when it is over — are what the roaming variant replaces. Reachability is asked for the whole of a chase, so a drone breaks off whatever goes dark or dives under it |
| `rod/scripts/RoamingDroneSwarmScript.java` | The pondless swarm: with a Breach Coupler fitted, a screen flying with the fleet goes after buried motes the breach lamps have **lit outright** and unearths them on contact. A dent is not a hole — taking one is the harpoon's Fathom Head and nothing else. Losing either the lamp opening or the coupler recalls the screen |
| `rod/rendering/FishingRingRenderer.java` | The dashed ring showing the fishing radius |
| `rod/rendering/FishingDroneDebugRenderer.java` | Dev only: ring and per-drone spokes |
| `rod/animation/Flash.java` | Short additive glow burst |
| `rod/constants/RodConstants.java` | Drone launch offset, speed, steering, orbit, return acceleration, ring look, and ROD sound ids |
| `harpoon/ability/HarpoonAbilityPlugin.java` | Fires the line and reports only a successfully created shot; aim assist; press again to cut while hauling. Its ability-bar icon follows the fitted head, replacing the stock harpoon with the explosive variant while an Explosive Head is installed. Its static refill definition plays the charge-ready UI cue according to the LunaLib radio setting, defaulting to lit breach lamps or an open pond in interaction range, and is also the one route for a Retrieval Head refund |
| `harpoon/entities/HarpoonEntityPlugin.java` | The whole cast: flight, strike, hauling, catch, return, rope rendering. Pond and breach-lamp targets share one acquisition path that normalizes both to an ordinary fish mote before the common hold/shove state; a pond mote hands its exact parent rupture to the minigame while remaining the separate catch target, so harpooned specimens retain the same source proof as drone catches. Outbound flight sweeps each frame's path against active legendary shield circles and stops at the first boundary, so fast heads cannot tunnel through a bubble; a haunt mine in the head's path detonates and ends the throw before any fish behind it is considered; a deflection hooks nothing and refunds nothing, while popping the Longliner's shield spends the explosive head on the shield instead of the fish. A confirmed mote strike asks `QuorumShellGame` first - only the sidelined pop mode acts there; by default an empty body is simply hooked and fights its own decoy-spec minigame - then retains the legendary contact fallback for first-throw defences without a persistent bubble, before refunding one capped charge when Retrieval Head is fitted and reporting once ahead of the ordinary/explosive split. A player's fitted explosive head gets a layered, irregular red warning pulse; its glow is a private filename-loaded sprite whose mutable render state is reset after every draw, never the shared campaign-entity sprite. Fleet collision eligibility excludes only the Fisherman, so normal and explosive shots pass through his boat and can hit something beyond it. An explosive impact records the mote's species name or struck fleet name before consuming the head - and an unshielded legendary never dies to it: it dives on the spot and resurfaces far away, the chase intact. An NPC-owned line skips the minigame and always lands. When the player is the stronger end, the haul continues until the two fleet circles actually touch, then cuts the line and requests immediate comms; using a stronger target as an anchor retains the clearance release and never opens a dialog. |
| `harpoon/constants/HarpoonConstants.java` | Flight, catch radius, haul physics, rope spring and wave params, sound/setting and alternate-icon ids, plus the explosive head's red halo/core palette and pulse tuning |
| `searchlight/ability/SearchlightAbilityPlugin.java` | The breach lamps: spools them up, beam slow, detectability penalty, yields to open ponds. Three questions about a buried mote, and they are **not** interchangeable — `isLit` (a beam is on it, so it can be taken), `isDetected` (it is showing as a dent at all, including the passive reach, so it can be seen), `isBreaching` (the lamps are lit at all) — plus `getBeamStrengthAt`, the raw player-beam position test anything can ask (the Longliner's disguise fails it). Owns every beam and runtime renderer as one location-bound activation: activation, deactivation, engine cleanup, plugin replacement and load repair all expire before discarding handles, and a location change cannot carry LunaLib's global coordinates into another system. |
| `searchlight/scripts/Searchlight.java` | One beam: sweep, lock-on, picks its face, drives distortion and ripples. A save-persistent beam must prove each frame that the installed, active ability still owns it in the same location; otherwise it immediately expires every transient face and distortion. Post-load ownership recovers only through membership in that ability's saved light list, and the arc rebinds to the fleet's current live position vector every frame. |
| `searchlight/rendering/SearchlightGlowRenderer.java` | The circular beam, purple over its window. A zero-duration shutdown marks it expired synchronously rather than waiting for a LunaLib advance. |
| `searchlight/rendering/SearchlightFanRenderer.java` | The wedge beam, for the fan-beam tackle. Player-owned instances follow the live breach-light area upgrade; the fixed-geometry constructor keeps non-player beams such as the Fisherman's independent. A zero-duration shutdown marks it expired synchronously rather than waiting for a LunaLib advance. |
| `searchlight/rendering/SearchlightBreachRenderer.java` | The window under a spot: world-anchored hyperspace under the beam's falloff, with parallax. A zero-duration shutdown marks it expired synchronously rather than waiting for a LunaLib advance. |
| `searchlight/rendering/SearchlightFanBreachRenderer.java` | The same window cut as the fan's wedge, falloff for falloff. A zero-duration shutdown marks it expired synchronously rather than waiting for a LunaLib advance. |
| `searchlight/rendering/SearchlightBurnRenderer.java` | The old pond-style burn look — sidelined, nothing uses it |
| `searchlight/rendering/SearchlightImpressionRenderer.java` | Dents for all beams together: passive bruises near a light, and a beam over a mote reveals its pond self. The tracking upgrade retains that normalized reveal strength and fades the mote and impression together instead of dropping the exposed glow when the beam leaves. Bound to the exact owning ability activation and location, and self-expires if either stops matching. |

### `distress`
Reusable distress-call framework. **Has its own README — read that first.**

| File | What it does |
|---|---|
| `DistressCallFramework.java` | Idempotent registration, the provider registry, resolution entry point and logging; also exposes the merged ids and narrow testing entry points used by the optional console command |
| `DistressCallSettings.java` | Mutable paths, memory keys, route id and concurrency/reservation tuning |
| `DistressCallSpec.java` | One validated merged-sheet row: provider, weighting, fleet shape, limits, trigger and opaque tags |
| `DistressCallRegistry.java` | Loads `distress_calls.csv` through the game's merged-spreadsheet API and rejects malformed rows without creating entities |
| `DistressCallProvider.java` | The narrow external-content seam: eligibility, fleet preparation, expiry and resolution |
| `DistressCallInstance.java` | Save-safe ids and live entity/intel handles; the rules `Call` target that fires the row's dialogue trigger |
| `DistressCallManager.java` | Persistent idempotent coordinator and route spawner. Watches vanilla's actual distress interval, yields when vanilla creates the call, shares its system reservations, supplies the fleet and exact vanilla breadcrumb intel, and owns no quest logic. Its test entry selects through the same system/provider gates and starts the chosen framework route; the route manager still waits until the player is close enough to instantiate its fleet |
| `vanilla/NearbyEventsBridge.java` | The only protected-state seam: binds to the live `NearbyEventsEvent` interval and timeout tracker through `ReflectionUtils`; fails closed rather than starting an independent scheduler |
| `vanilla/VanillaDistressCallSpawner.java` | Console-test bridge over vanilla's protected generators. It selects one of the four stock distress event types, then lets `NearbyEventsEvent` create its ordinary route, entity or salvage special and adds the same breadcrumb intel |

### `skillshot`
Reusable aim-and-fire framework. **Has its own README — read that first.**

| File | What it does |
|---|---|
| `SkillshotFramework.java` | `register()` / `reset()` / `log()`. The entry point |
| `SkillshotSettings.java` | Sprites, sounds, sizes, colours, ability tag — all mutable statics |
| `GuideLineStyle.java` | SOLID / DASHED / DOTTED |
| `ability/BaseSkillshotAbility.java` | Wires activation, tooltip and blocked-reason logic |
| `ability/SkillshotAbility.java` | What the input layer needs from an ability |
| `input/OnKeyPressSkillshotListener.java` | Number keys 1–9: hold to aim, release to fire |
| `input/OnClickSkillshotListener.java` | Per-session: waits for the next map click |
| `input/SkillshotActivationManager.java` | Keeps exactly one targeting session live |
| `input/SkillshotInputListener.java` | `isActive` / `reset` |
| `render/SkillshotRenderer.java` | What a reticule is: LunaLib's rendering plugin plus done/valid/cursor hooks |
| `render/BaseReticuleRenderer.java` | Fleet ring and guide lines; subclass supplies the cursor |
| `render/AreaReticuleRenderer.java` | Circle at cursor, sized to an effect radius |
| `render/DirectionReticuleRenderer.java` | Arrow at cursor |
| `render/ValidatedAreaReticuleRenderer.java` | The above, gated by a validator, red when rejected |
| `render/PositionValidator.java` | `isValid(Vector2f)` |
| `render/validators/PondProximityValidator.java` | Only inside a pond |
| `render/validators/MarketProximityValidator.java` | Not near a market |
| `util/SkillshotUtils.java` | Cursor world position, and solid/dashed/dotted GL lines |
| `util/DelayedActionScriptRunWhilePaused.java` | A delayed action that ticks while paused |
| `example/ExampleSkillshotAbility.java` | A working copy-paste starting point |

### `ui`
The shared component framework - the sidebar's visual language written once, worn by the map
panes, the outfitter, the chart counter and the aquarium alike. The minigame is the deliberate
exception: it is its own encapsulated universe and keeps its own dress.

| File | What it does |
|---|---|
| `ShopUi.java` | Shared drawing helpers: fonts, quads, vertical gradients (constant colour with graded alpha, or both ends their own), clipping, pips, card placement, and `drawPanel` - the transparent-black, half-strength-border face every pane wears |
| `PaneWidgets.java` | The shared widgets: category-art type chip, text button, title row, list header with its help mark, the standalone help mark, the centred empty-space note, and the hand-worked ghost text a bare `TextFieldAPI` does not provide. Each chip owns a fresh sprite instance for its supplied registered id, so render-state mutation stays local to that chip |
| `ListRow.java` | The scrolling-list row skeleton every list row extends: cull against the list window, clip to it, the graded dark field, the accent strip, clicks that only land in view. `campaign/fish/map/FishListRow` and `campaign/fish/shop/ShopRowPlugin` build on it |
| `FishIcons.java` | A species' face by Codex knowledge: the art once landed, its rimmed black silhouette while only surveyed. `drawBacklit` is the complete named-species portrait shared by the chart shop, Codex detail and Intel note, including the same rarity-coloured light. The rim **is** the artwork (a multiply cannot lighten), so it is withheld until the black copy covering it is nearly opaque — see `RIM_COVER_FLOOR`. Draws on its own fresh sprite instance per call - the engine mints one per `getSprite`, so nothing here can blacken cargo or result screens |

### `rendering`
Shader and GL machinery.

| File | What it does |
|---|---|
| `distortion/CampaignDistortionRenderer.java` | GraphicsLib's distortion pass, rebuilt to run on the campaign map |
| `plugins/MaskedWarpedSpriteRenderer.java` | Fill + alpha mask + optional swirl and well radial warps |
| `plugins/CoherenceOverlayRenderer.java` | Full-screen post-process: the screen redrawn warped and leaned purple, at a level set from outside. Both warp and tint sit under a screen-edge mask whose uniform rectangular inset advances linearly as coherence fails; high-coherence states remain shallow at the corners instead of inheriting a circle's permanently saturated wedges |
| `plugins/ChromaticAberrationOverlay.java` | Whole-screen chromatic aberration, UI and tooltips included, through vanilla's above-UI-and-tooltips render hook: the finished frame is copied into a texture and redrawn with red and blue shifted apart. Fixed-function GL, so it survives GraphicsLib's shaders being off. Level-driven from the legendary haunt; at zero it holds no listener registration at all, and it sits out open dialogs and core tabs - the paused game does not shimmer |
| `plugins/MaskGlowRenderer.java` | Additive glow shaped by a sprite's alpha |
| `plugins/NoiseMappedCircularRingRenderer.java` | Ring shaped and animated by scrolling noise |
| `plugins/WarpGrid.java` | The animated vertex grid the warp renderers share; borders pinned |
| `plugins/WarpedRectRenderer.java` | A sprite warped per-vertex by a grid, no shader |
| `spiral/CircularSpiralWarpRenderer.java` | Portable circular campaign post-process recovered from the pond's rolled-back centre whirlpool. A host supplies cached live world locations; the renderer owns the bounded spiral shader, screen copies, source composition and world-to-screen conversion. Its config exposes range and shader/tuning seams without coupling the pass to black holes or Catch.Release state |
| `spiral/BlackHoleSpiralWarp.java` | Catch.Release adapter and lifecycle: caches every black-hole star in the current system (including secondary stars), registers the generic pass transiently on load, and reads `catchreleaseBlackHoleSpiralWarpRange` from merged settings, default `6000` world units |
| `renderers/FleetMarkerRenderer.java` | A small icon off a fleet's corner, in vanilla's own geometry and whoever's colour — the quest offer's cyan `!` |
| `renderers/RippleRingRenderer.java` | One growing, fading ring, pinned to one location. Zero-duration retirement is synchronous, so a location handoff cannot strand a ring awaiting an absent renderer advance. |
| `renderers/SimpleRippleDataRunner.java` | Advances and expires a `RippleData` |
| `helper/Stencil.java` | Depth-mask sprite masking. Stencil-buffer variants are deprecated |
| `helper/ParallaxUtil.java` | Background drift and camera-relative parallax UV offsets |
| `helper/Disc.java` | Filled or outlined circle |
| `helper/RoundedBorder.java` | Rounded-rectangle outline |

### `memory` · `helper` · `reflection` · `testing`

| File | What it does |
|---|---|
| `memory/upgrades/UpgradeManager.java` | Save-persisted levels. `getValue` is the single read entry point; `updateBaseValues` re-walks the sheet each load, seeding stats the save predates and refreshing every sheet-owned field, so the save owns the levels and the sheet owns everything else |
| `memory/upgrades/StatIds.java` | The ids joining code to `UpgradeData.csv` |
| `memory/upgrades/UpgradeStat.java` | One row: base, FLAT/MULT per level, category, optional outfitter icon path, current value |
| `memory/charges/ChargeManager.java` | Float charge pools that regenerate only while the campaign is unpaused; timed refill and explicit capped gains preserve fractional progress and receive one callback when a step crosses a whole-charge boundary, without treating initial full-pool creation as a gain |
| `memory/TransientMemory.java` | Session-only cache. Keys must start with `$`, never persisted |
| `memory/RandomMemoryHelper.java` | A per-star-system `Random`, stored in that system's memory |
| `helper/loading/FishSpecLoader.java` | `fish.csv` → `FishSpec`, cached |
| `helper/loading/UpgradeStatLoader.java` | `UpgradeData.csv` → `UpgradeStat`, including its optional outfitter icon path, cached |
| `helper/loading/BackdropLoader.java` | `backdrops.csv` → `Backdrop`, cached |
| `helper/loading/SpriteLoader.java` | Sprites by id or path, a fresh instance per ask - the engine mints a new `SpriteAPI` around the shared GL texture on every `getSprite`, so instance state is always the caller's own. Only the loaded-or-missing fact per path is cached, so a texture uploads once and a missing file logs once. The old shared-instance-plus-reset model is gone; it was the root of every cross-screen sprite leak |
| `helper/CampaignHelper.java` | The small campaign questions asked from several corners: `isPlayerHere` - is the player fleet in this entity's location - for the boats' visit clocks, the tutorial's held postings and the camps' one warning chase |
| `helper/cache/TimedValue.java` | The expensive-read-asked-every-frame cache: caller's own clock, a TTL in its units, an optional key that forces the read when it changes. Behind the shop's wanted-ask cache (wall millis), the coherence bar's reading (accumulated seconds) and the boats' names (game days + location). `Aberration`'s stamp cache stays hand-rolled on purpose - its invalidation check sits in the hottest read path and must not allocate |
| `helper/math/TrigHelper.java` | Circle intersection and fitting, smoothing, normal distribution |
| `helper/math/Circle.java` · `CircularArc.java` | Point/angle helpers and arc traversal |
| `helper/animation/BaseCircleTrajectoryFollowingParticle.java` | Position and facing along a circular arc between two points |
| `helper/animation/ArchedTrajectoryFollowingMote.java` | A glowing mote drawn along that arc |
| `reflection/ReflectionUtils.java` | Reflection via `MethodHandle`, to dodge the classloader ban |
| `testing/DevShortcut.java` | The J key in three presses: skips the introduction, grants every rig and the shop, and issues charts of every rung; then unlocks every backdrop; then records every tackle and upgrade schematic without buying either. A low-priority `CampaignInputListener` on the pre-core pass, matched on the typed character rather than a scancode. Dev mode only, read per press |
| `testing/TestStencilRenderer.java` | Dev renderer. Commented out of `ModPlugin` |

---

## Things that will catch you out

**Terrain is not a custom entity.** `getPlugin()` not `getCustomPlugin()`; radius only through
`CampaignTerrainAPI`; `getActiveLayers()` and `getRenderRange()` throw unless overridden; and
`BaseTerrain.advance` sweeps local fleets unless the plugin opts out.

**Terrain and entity scripts advance in every location, not just the player's.** Anything that draws
or plays a sound must gate on `isInCurrentLocation()` — LunaLib's renderer list is one list for the
whole sector, drawn wherever the player is.

**`callAction` must return true for anything it handles.** Vanilla throws on an unhandled action
rather than reading it as a failed condition. Outcomes travel back through memory flags.

**A mission's time limit is measured against its *total* elapsed days, not the current stage's.**
`setTimeLimit` stores a figure that `advanceImpl` compares to `elapsed`, which counts from when the
job was accepted and never restarts. A `FishJob` that asks for something else instead of finishing
stays in `WANTED`, so without re-arming the clock every round after the first shares the leftovers
of the first one — and when it runs out the stage moves to `FAILED`, which tears down the flag that
raises the hand-over option. The giver just stops offering to take the catch. `setClock()` re-arms
it; `getDaysLeft()` is what the intel must use, since `getElapsedInCurrentStage()` is a different
number from the one the failure is measured against.

**A bar option id must start with its mission id.** `BarCMD` finds the wrapper by testing whether
the selected option *begins with* the mission's trigger prefix, and `abortMissions` kills every
mission that fails that test — including the one the option was meant to open. An option named for
the job rather than for the mission silently aborts it. No mission id may be a prefix of another,
either.

**`BarCMD` has no `close` verb.** It is `returnFromEvent`, which is also what puts the player back
in the bar. An unrecognised command falls through the switch and leaves the dialogue with no options
at all.

**A bare `score:` line takes the game down at load.** The score token is stripped before the
condition is parsed, so a line with nothing else on it leaves an empty token list and throws. Append
`score:` to a real condition, never put it on a line of its own.

**Rule scores are summed across the matching condition lines, with no bonus for being more
specific.** Two rows that both match at 0 are picked between *at random* — so a general row and its
special case need an explicit `score:` between them, not just an extra condition.

**A condition that is only a variable hands the engine back whatever the key holds,** and it has to
be a true or a false. Testing a key that holds a name is not a truthiness check; it is a type error
waiting for the row to be reached. Set a companion boolean — `MafiaJob` carries
`$catchreleaseHasWager` beside `$catchreleaseWager` for exactly this.

**Token replacement is longest-key-first,** so `$catchreleaseAskCap` resolves before
`$catchreleaseAsk` rather than leaving a stray `Cap`. Relied on, not merely observed.

**Every memory key starts with `$`.** `Memory.set` throws on one that does not, and it throws
whenever the write happens rather than where the key was written down - which can be a stage change
minutes later.

**A sound id is a string nothing checks until it is played.** The compiler cannot see it and the
game only disputes it at the moment the sound is asked for — so a wrong id in a rarely-hit branch
survives indefinitely, and one in the shop's buy button fires on the most common action there is.
`ui_char_increase_aptitude` looked exactly like the real ids around it (`ui_char_level_up`,
`ui_char_decrease_skill`, `ui_char_reset`) and did not exist.

To check them: **`#` starts a comment anywhere in the game's `.json` files** — a custom parser, not
strict JSON, which also tolerates trailing commas. Strip `#`-to-end-of-line before parsing vanilla's
`sounds.json`, or commented-out entries read as valid ids. The ids live at the **top level** beside
`"music"`, and both `"id":[…]` and `"id":{"sounds":[…]}` are legal forms.

**Sound files have a shape that depends on how they are played.** `SoundPlayerAPI` is explicit:
`playUISound` wants **stereo**, `playSound` (positional) **must be mono**, `playLoop` should be
mono. Our `spotlight_toggle.ogg` is mono because it goes through `playSound`; `skillshot_denied.ogg`
is stereo because it goes through `playUISound`. The minigame's line audio follows vanilla's own
per-frame `playUILoop` pattern, keeping one id alive while changing only its volume; this avoids the
engine's fade-based handover between different loop ids. Getting a new one backwards is not a
compile error.

**`BaseHubMission` assumes there is a person, in about a dozen places that do not check.** The
intel's icon and faction colour, the reputation lines, the reward text and the distance readouts all
reach through `getPerson()` bare. A mission with no bar contact must call `setPersonOverride(...)`
with somebody - a fleet's `getCommander()` will do - or it will throw, once per method, on whichever
one the game happens to reach first. `FleetQuest` cost three crashes learning this a method at a
time; give it a person instead.

**The two `makeImportant` overloads do not take the same kind of string,** and the compiler cannot
tell them apart. `Misc.makeImportant(entity, reason)` takes a *reason*, held alongside the flag, and
must **not** start with `$`. `BaseHubMission.makeImportant(entity, flag, stages...)` takes a memory
*key* it writes on a stage change, and must. Handing a reason to the mission's version compiles
cleanly and throws in the campaign. Whichever is used, pair it with the matching `makeUnimportant`.

**`Tackle.Fit.BOTH` is not a rig.** It is a declaration of what a module fits. Code walking rigs must
use `Fit.isRig()` or it will offer a shelf for a slot nobody owns.

**Owning a module and wearing one are different questions.** `TackleManager.isOwned()` asks the
first, `get(rig)` the second. A permanent module is bought once and can be moved between slots for
nothing after that, so anything that charges for tackle must ask `isOwned()` first — `ShopEntry.getPrice()`
returns null for one already owned, which is what makes fitting it free. Anything that *grants* a
module must `own()` it as well as `fit()` it, or the player pays for their own gift the first time
they take it off. Saves predating ownership seed the owned set from whatever is in a slot.

**The shop has three kinds of thing on a shelf, and the third one is not for sale.** A `ShopEntry`
is an upgrade (a ladder), a tackle (a slot), or a **curio** (a switch). A curio was bought from
Crablobab; all the shop offers is the only thing left to do with it, which is turn it off. So its
price is null, `isDone()` is never true — a switch is never finished with — and the buy button reads
SWITCH ON/OFF. Both the main tab row and the shelves under it are derived from the entries that
exist, so the Extras tab appears the moment the first curio is bought and not before.

**The celebration is bought, not configured.** It had a LunaLib toggle; it now has no setting at all,
and `CrabWares.CELEBRATION` answers three separate questions — bought (his business, never undone),
owned (where it is kept), and switched on (the player's, through the shop). `FishingMinigamePanel`
asks `isOn()` and simply does not build a `CatchCelebration`; nothing inside that class asks again.
The celebration remains absent from LunaLib. The mod's LunaSettings page exists only for the
harpoon charge-ready sound policy; it does not make the celebration configurable again.

**Stocking a module and owning one are a third question.** `Tackle.stocked` says whether the
outfitter carries it, and `TackleManager.getOptions()` lists what it stocks *plus anything already
owned* — without the second half a module bought anywhere else could never be taken off and put back
on. `Tackle.EXPLOSIVE_HEAD` is the only unstocked one; it comes out of Crablobab's coat. It is also
the only consumable: a miss returns it unfired, while `detonate()` calls `TackleManager.consume()`
to remove ownership and clear the harpoon slot. `CrabWares.EXPLOSIVE_HEAD` deliberately asks that
live ownership state instead of a permanent bought flag, so the bar event and its existing
credits-and-crabs purchase route become available again after every detonation.

**Anything granted from outside the shop still goes through `ShopEntry.grant()`.** It is the only
place that knows a running rig has to be stopped so it comes back up reading what it now has — see
the note on abilities reading their numbers once. `CrabWares.EXPLOSIVE_HEAD` grants through it for
exactly that reason, rather than calling `own()` and `fit()` itself.

**Retrieval Head pays for accuracy, not firing.** A confirmed player mote collision adds one to
the same persistent float pool used by timed regeneration, capped at the current upgraded maximum and
without discarding fractional recharge progress. Misses, fleet hits and NPC-owned lines do not
refund. The gain crosses the ordinary refill callback, so the existing charge-ready sound policy
still owns the cue.

**An explosive head is a different ability, not a better harpoon.** It cannot land anything: the
strike blows the mote up, consumes the fitted charge, and throws the head off its own line, and
`HarpoonEntityPlugin.BLASTED` is a terminal state that is not an arrival — nothing is reeled in and
`land()` never runs. Against a hull
it books the harpooning the ordinary way and then skips the crew's patience outright, which is the
only caller of `HarpoonOffence.turnHostile()` that does not go through the hit count. The fireball is
vanilla's `Entities.EXPLOSION`, which brings the shockwave, the sound and the fleet damage with it.

**A null price means free, and there are two kinds of free.** `Tackle.NONE` never had a price;
an owned module has already been paid for. The shop tells them apart explicitly, because one line
covering both said that fitting a module you own was "emptying a slot".

**An ability reads its numbers once, when it starts.** Buying a rung or fitting a module while the
rig is running leaves it on the old figures until it is switched off and on — so the shop switches
it off for you. Both grants go through `ShopEntry.grant()`, which is the only place that knows to;
anything else that changes a running ability's inputs has to do the same or the purchase looks like
it did nothing. `StatIds.getAbilityId()` is the stat-to-ability link, **listed rather than matched
on the id prefix** — `fishing_bar_size` belongs to the minigame despite reading like a drone stat.

**Fish encode format is save-critical.** Four fields are always written; origin, method and
implement follow as an optional tail, written only as far as there is anything to say. Fields are
read **by position**, so a blank holds the place of one that has no value — a specimen with no
origin but a known method encodes as `bass|1.2|4.5|0.6||HARPOON`. Four- and five-field specimens
already in saves still parse. Changing the format breaks fish already in saves.

**Where a fish lives is one question, and everything asks it the same way.** `FishHabitat.of()`
reads a place once — sun colour, system tags, region, constellation age, how well reality is holding
— and `FishRanges.matches()` is the only thing that tests a species against it, applying the pin
and relaxation state on top of `FishSpec.matches()`. They used to be several: the spawner tested
star type, tags and region; the map, the route planner and the intel panel tested the region alone,
so the map shaded systems under the wrong sun and said so beside a spawner that would never have
offered the fish there. `FishPresence.livesIn()` is what every screen calls, and it routes through
the ranges. Habitats are cached because their one moving input — coherence — only moves as far as
anyone can see when the month-end reassessment drops the cache and re-reads every system.

**Ranges are dynamic, floored, capped, and pinned.** At the end of every month (and once, on a save
that has never had it) `FishRanges.reassess()` re-reads every habitat — gates light, slipstorms
move — and re-homes any non-abyssal species with fewer than three real systems by relaxing its
sheet gates one rung at a time: constellation age first, then a ±0.25 coherence widening, then star
colour, then regions, never across the abyss boundary in either direction. A rung is refused if it
would push any real system past fifteen species, and the most starved species are processed first
so they claim the headroom. Species named by an active quest ask (`FishAsker` intel) are pinned —
their range is frozen as the system list they had before the reassessment, and it thaws only when
the quest ends — so a reassessment can never pull a quest target out of its quest system.

**Blank means "anywhere" on every habitat criterion except the abyss.** `ABYSSAL` has to be named,
because a species that says nothing about where it lives is one somebody could describe, and nothing
describable lives down there — without the exception the deepest water in the game offered the same
roach as a core world. It is the one asymmetry in the table and it is deliberate.

**The species list is a 10 : 4 : 2 : 1 pyramid over exactly one hundred fish.** Legendaries do not
count toward the total. The hundred split 59 / 23 / 12 / 6 across commons, uncommons, rares and
epics — the abyss pool holding 7 / 2 / 1 / 1 of those, the main sheet 52 / 21 / 11 / 5 — so
generation and habitat grouping follow a real pyramid instead of a jumble where rares outnumber
uncommons. Rarity is assigned from the description: a fish whose text reads ordinary sits low
regardless of its art, and only the genuinely inexplicable hold the rare tier. A row that changes
tier takes its new band's catch columns, spawn weight and value with it, and adding or re-tiering
a row means rebalancing the pyramid back to the hundred. Mechanism rows with spawn weight zero
(the Quorum's splinter, which doubles as its shell-game body) never spawn from ranges, take no job
asks and no cap slot, and sit outside the hundred.

**The abyss is its own scale.** Abyssal rarity follows the same pyramid within its pool — seven
commons, two uncommons, a rare, an epic, the manta on top — but the label only sets encounter
frequency and value down there. The catch columns never drop below rare-band numbers: the abyssal
ladder runs d150 for its commons up to d190 for its epic, all above the main sheet's epic band, with
the manta's 195 / 0.6 / 2.7 still the ceiling. Re-tiering an abyssal row means moving it along that
ladder, never onto the main one.

**The catch columns are one ladder, and the rate columns are its upper half.** `fish.csv` tunes the
minigame per rarity band through five columns moved together: `difficulty`, `restlessness`,
`motionSpeed`, `progressRateMult`, `escapeRateMult`. The difficulty column stops mattering once the
bar-size upgrade lands — at 160px the window covers enough track that no fish speed makes net
progress negative — so what keeps rare-and-up meaningful against an upgraded kit is the rate pair:
`escapeRateMult` climbs from 0.95 (gentle commons) to 2.7 (the legendary) while `progressRateMult`
falls to 0.6. Retuning a band means moving all five and simulating; moving `difficulty` alone
changes the early game and nothing else.

**Movement modes are the in-band variety, and two of them are tier-bounded.** Seven motions —
smooth, darter, sinker, floater, weaver, twitcher, lunger — each with its own signature on the
track, in the campaign mote's swimming and in the aquarium tank, plus MIXED, which rerolls the
whole pool. They are not equally hard and are not meant to be: floater and weaver sit easiest,
sinker and lunger hardest. Two placement rules are load-bearing, both simulated rather than
guessed: **weaver is never a species motion above UNCOMMON** — its end-to-end sweep out-runs the
bar's top speed past that and the catch turns binary-by-skill, then impossible — and **lunger
never sits on a COMMON**, because its freeze-and-lunge plays a full band harder than the row's
numbers say. MIXED may still roll either anywhere, which is survivable because a roll lasts one
think.

**Ranges are one or two quadrants for ordinary species, and the gate is the range for gated ones —
capped at fifteen species per system.** An ordinary row lives in its home quadrant plus at most one
adjacent one. A hard-gated row — deep-coherence, neutron/black-hole stars, theme tags, a colour gate
stacked on a coherence gate — names a half-sector, sometimes less a region, or nothing at all,
because the gate already does the narrowing and a single quadrant on top of it is how a species ends
up existing in one system. The cap is verified by sweeping the sheet over every region × star colour
× age × coherence × theme combination; it currently sits exactly at fifteen, so any widened or added
row must be re-swept, and the deep-coherence rows are the ones that stack in thin-fabric systems.
When the legendaries landed, each wide gated row gave back the one region the sweep flagged — that
is where the half-sector-less-one shapes come from. Every region keeps at least two commons free of
star, age and coherence gates so no pond can come up empty.

**A legendary is one fish, one system, one chance — and its art is pending.** One legendary per
quadrant (`lantern_jack`, `slipstream_moray`, `quorum`, `false_dawn`), one across the four core
regions (`longliner`), the manta in the abyss — six in all, and every one of them lamp-only: a
legendary is a whaling chase, worked with the breach lamps, never handed over by a pond. The sheet's
quadrant and `minAberration 0.5` gates only choose candidates; `LegendaryChases` holds the single
live host system, the ninety-day post-sighting relocation, and the permanent caught flag — landed
once, gone forever, and no job may name one. While the player is in a living legendary's host
system and has laid eyes on the fish, `LegendaryHaunt` runs a couple of that species' interference
modules - sighting-driven, lingering, fading when the fish is lost - and a hooked legendary carries
at least three epic-tier treasures. An explosive strike never kills one: it dives and resurfaces
far away. Five of them fight back in kind - the Longliner's Fisherman disguise and poppable hull
shield, the Quorum's splinter escort and then its three-body shell game, the Lantern Jack's
shell-stacking hunt, the moray's flung motes and rideable slip-dash, the False
Dawn's minefield - through `LegendaryShields`, the harpoon's deflection hook and the haunt modules. The two calm-fabric commons (`nav_bobber`, `plume_remora`) carry `maxAberration
0.4` for the opposite reason — the crowded end of the sweep is the deep end, and they fit the cap
only by staying out of it. The eight rows that filled the list to its hundred follow the same
pattern: bounded to calm-or-mid fabric or gated to dead stars, because the deep bands sit at the
cap everywhere. All fifteen reuse existing sprites until their own art lands and are marked
`placeholder art` in the table's final `comment` column.

**A species can be reachable by only one kind of gear.** `reachedBy` names `POND`, `BREACH_LAMP`, or
neither for both — the same `CatchImplement` a buyer asks about, so "only ever out of a rupture" and
"wanted pond-caught" are one vocabulary rather than two that happen to agree. It is also a second
way to write an unfillable ask: a named species that only comes up out of a rupture cannot be asked
for through a breach lamp, which is why `FishJobAsks.pickImplement()` reads the species first.

**How a fish was caught is two questions, and they are not independent.** The method is the tackle
at the end of the line; the implement is what made the fish reachable. The drones are played against
the rupture itself, so anything they bring up came out of a pond by definition — `DRONE` +
`BREACH_LAMP` is a requirement that reads fine and can never be filled. Only the harpoon, which is
played against the mote rather than the hole, can be asked about either way. `FishJobAsks
.rollCatchTerms()` is where that rule lives; anything else composing the two axes must respect it.

**Bundles are identity-by-contents.** Spending part of one removes it and adds a new one with the
remainder. Never mutate in place.

**`GL_LINE_STIPPLE` is useless here.** GL restarts the pattern at every segment of a `GL_LINES`
batch, so anything shorter than one dash draws solid. Dashes are cut as geometry in `SkillshotUtils`.

**The fan's breach window copies its light's shape, but not its colour.** `SearchlightFanBreachRenderer`
reuses `SearchlightFanRenderer`'s `STEPS_ACROSS`/`STEPS_ALONG` and both falloff curves vertex for
vertex, so the hole opens exactly where the light above it is bright. Changing the geometry or
either alpha ramp in one and not the other pulls them apart — which is why the rim bands are a hue
lean in rgb alone, and not the brighter edge that would have been the obvious way to draw them.

**The three lamp renderers share one resting alpha on purpose.** Fan, glow and impression all read
`0.12f - 0.04f * flicker.getBrightness()`. Fitting a module is meant to change the *shape* of the
light, not how much of it there is, so any change to that figure belongs in all three at once.

**Being harpooned escalates two different ways, and which one depends on what the crew is for.**
`HarpoonOffence.isCombatCrew()` reads vanilla's own `$isPatrol` / `$isWarFleet` / `$isPirate`. A crew
that fights for a living turns hostile on the second hit, which is the old rule. Everybody else gets
a ladder of their own: once is a complaint to your face and nothing more, twice brings them after you
for the repair bill — pursuit without hostility, which is a state vanilla supports and uses for its
own hasslers — and three times and they stop talking, set `AVOID_PLAYER_SLOWLY`, and put it on the
channel through `HarpoonPatrolResponse.callForHelp()`. Asked rather than inferred from strength: a
heavily escorted convoy is still a convoy.

**Hostility ends the civilian middle ground.** A relationship crossing into hostile on the incident's
reputation loss is handled after that loss, not left for route AI to notice eventually. From there
the reciprocal 1.25× test vanilla uses for an engagement decides the visible response: a civilian
fleet strong enough to engage clears its route and takes an explicit `INTERCEPT`; anything else
removes an old repair-bill intercept, clears aggressive pursuit, and flees. A pending witness script
recognizes either replacement response and abandons without clearing its new assignment.

**A rules row can take credits but cannot close a faction's books.** Both the patrol's fine and a
holed crew's repair bill hand the outcome back through memory flags that the crime script reads on
its next tick, rather than through `CatchReleaseCMD` — the handoff predates the command and there is
no reason to move it. The bill adds a **global** marker beside the per-fleet flags, because the crew
that was talked to need not still be near the player when anything reads it back; the marker is what
keeps the sector-wide search off every other tick.

**Walking up to a fishing boat must not show the fleet screen.** Everything the trawler is for is behind a comm link, and vanilla's encounter renders that link as one unlabelled line under engage and disengage - a combat prompt with a shop hidden in it. `OpenComms` on a `BeginFleetEncounter` row is vanilla's own answer: it sets the flag the encounter reads at the end of its own `init`, which takes the `OPEN_COMM` branch instead of building the fleet screen at all. No plugin needed. The mod briefly had one that lit the comm option up instead - `$hailing` and `$highlightComms`, written before `super.init`, the same trick `HarpoonedFleetFID` still uses and which does work. It was the wrong question: a lit option on a screen that should never have opened is still that screen.

**`PopulateOptions` is never fired for you - not after `OpenCommLink`, and not after
`DialogOptionSelected` either.** The engine's whole contribution is what `FireBest`/`FireAll` do
with the fired rows' own options columns: the panel is cleared and rebuilt *only when a fired row
carries options*, and left standing untouched when none does. So a handler that only sets
`$menuState` visibly does nothing - the old menu stays up and the new one never appears - and a
handler whose options column holds a submenu works without any firing at all, because `FireBest`
rebuilds the panel from that column. Every row that *switches* menus must do both halves itself:
`$menuState = <state> 0` and an explicit `FireAll` of that menu's trigger. General interaction menus
use `PopulateOptions`; bar-job offer and active-contact menus use `JobSpecificOptions` so generic
person options cannot join the batch. This map used to claim the engine fires `PopulateOptions`
after option selections; it does not, and the fisherman's whole menu tree was broken by rows
written to that claim.

**The Church and the Path are against the water, not against fish.** A rupture is a hole opened between here and hyperspace and everything pulled through it came from the wrong side of that hole, so the whole trade is people making a living off a wound in creation. Neither flag therefore produces a fisher, a buyer, a broker, or anybody in a bar with a favour to ask - no bar job at a Church or Path port, no trawler working a system either of them runs, no fishing offer on one of their hulls. What they do produce is everything in `campaign/crime`, plus the cells in `jobs/camp` that sit on ruptures so nobody can work them, and that is the only shape a Luddic interaction with this mod takes. `campaign/fish/FishingTaboo.java` is the one list; nothing hardcodes the two faction ids anywhere else.

**A hostile fleet can still be talked to, and the hail is what makes it possible.** The camp job needs a conversation with a pirate pack that is hostile by default, which no memory flag softens. Vanilla's answer is in the Galatia arc's gate-sitting pirates: `HailPlayer` on `BeginFleetEncounter` opens the link regardless of the relationship, and `MakeOtherFleetGoAway` is what ends it when they agree to leave. Nothing about the fleet is made friendly — the conversation is a thing the player gets to have, not a promise about how it ends.

**The camp job polls rather than being told.** Being killed, bought off, talked off and quietly wandering away do not share a hook, and only two of the four happen inside a conversation. So `CampedSpotJob.advanceImpl` asks one question on the mission's own tick instead of four rules rows each reporting a different way.

**Hidden until discovered, and it is one rule rather than a judgement per object.** Everything that sits inside a system waits to be surveyed; exactly two things are exempt, and neither is an object you find - a star (so a black hole is routable from the first day) and a slipstream (visible the moment it runs). `Mark.isFound` is the single test — it asks the source's own `survey` flag and then the entity, live, so surveying something changes the planner's mind at once rather than when the index next rebuilds. The mod's own placed entities follow it too: the tutorial wreck and the castaway beacon are `setDiscoverable(true)`, having previously argued themselves out of it.

Two things deliberately stay outside the rule because they are not survey finds at all: the Fisherman's map icon rides a fleet and inherits that fleet's visibility, and motes have their own reveal mechanic - the breach lamps - which is the whole point of the lamps.

**Nothing that thins the fabric moves, so nothing is measured twice.** Every reading used to walk the sector six times — `SectorAPI.getEntitiesWithTag` iterates hyperspace and every system per call — and readings are taken from tooltips and terrain readouts that ask every frame, and by the route planner once per candidate system. A gate does not move. Neither does a hypershunt, a black hole or a foreign engine; slipstreams are the one exception and they are hyperspace terrain that changes on the scale of a cycle. So `Aberration` crawls once into flat lists of destabilizer and colony marks, invalidated when the day rolls over, the gates switch on, or the economy's market count changes, and every reading after that is a loop over floats already in memory.

**Three fill points, and the one per-frame figure is deliberately a different question.** The index and the per-system readings fill when the player arrives somewhere (that one system) and when the sector map opens (all of them at once, paused, off a single crawl); anything else that asks fills on demand. What cannot come from that cache is where in a system the player is standing, because at sector scale every object in a system shares the system's coordinates — so `localPull` measures world-unit distance to that location's own entities, from `LocationAPI.getEntitiesWithTag`, never the sector's. It is what makes the overlay brighten as the fleet crosses a system towards whatever is causing the reading. **It lifts the system's reading and never scales it** (`ABERRATION_LOCAL_LIFT`): zero means "nothing here to stand near", which is the ordinary case — most systems are thin on account of something outside them — and scaling by it took the system's own reading out of the overlay entirely for exactly one commit.

**The abyss is read uncapped, because the cap is what made it a slab.** `Misc.getAbyssalDepth` capped is 1 from the far corner across most of the wedge and 0 within ten thousand units of its edge — one flat maximum with a wall around it, which is what every abyssal catch and the whole map layer inherited. The uncapped figure runs about 20 in the corner down to 1 at the line where the cap starts holding, and `ABERRATION_ABYSS_SPAN` divides it back into a gradient. It is a *reading* knob, not a rendering one: map, catch and terrain readout all move together, which is the point. `ABERRATION_ABYSS_SPAN = 1` restores the old cliff exactly.

**A point belonging to no system reads everything, and that is not a hyperspace nicety.** `CoherenceHeatField` samples a grid of bare points across the sector to paint the map, so `openSpaceReading` is the call that decides what the heat map can show — answering it with the abyss and the streams alone (which it did briefly) leaves a picture of the two sources that happen to be terrain. Open space is the ordinary case minus the one rule that needs a system: a source standing *in* the place being read counts at full weight. `Mark` carries its reach squared in world units so a point can be rejected without a square root, which is the operation that actually runs at that sample count.

**Colonies are the same field read in the other direction.** Every inhabited market in the economy contributes one system mark at full strength, falling quadratically to nothing at five light-years; condition-only market shells do not count. Several markets in one system and overlapping colony fields do not stack: the strongest stabilizer is subtracted from the strongest destabilizer, matching the maximum-not-sum rule on the unstable side. A colony's own system bypasses specimen jitter and reads exactly zero aberration, so Stable is a guarantee rather than a likely label. The colony marks share the source crawl and caches; the economy's market count joins the day and gate state in the invalidation stamp, so founding or losing a colony is reflected immediately. Because `CoherenceHeatField` samples `baseAt` at bare hyperspace points, the map receives the same stabilizing basins without a renderer-only approximation.

**A slipstream is a ribbon and goes into the index as one.** It is the only source with a shape, and `CampaignTerrainAPI.getLocation()` is a single point at one end of it — so a stream crossing thirty light-years of sector used to thin the fabric at the corner it started in and nowhere along its length. `Aberration.addStream` walks `SlipstreamTerrainPlugin2.getSegments()` at a fixed stride (`ABERRATION_STREAM_SAMPLE_LY`) and marks each sample, which bounds the error in distance-to-ribbon at half a stride and keeps a long stream to dozens of marks rather than hundreds. Anything that is not vanilla's plugin falls back to the anchor, the same courtesy the foreign tags get. Note that `SlipstreamTerrainPlugin` — no `2` — is dead in 0.98a (`advance` and `containsPointCaching` both open with `if (true) return`); the terrain id `slipstream` maps to `velfield.SlipstreamTerrainPlugin2`.

**Marks check that their source is still alive.** The index rebuilds on the day, so anything that dies inside one would read until midnight — slipsurges are streams that last hours. `Mark.isLive` is one field read and covers gates or stations removed by other mods for the same cost.

**The in-system reach is arithmetic on the light-year reach, not a second table.** `ABERRATION_LOCAL_BASE + ABERRATION_LOCAL_PER_LY × reachLY` — one reach per source, expressed twice by formula rather than twice by hand. A tabulated version shipped briefly and was five numbers of which four were the light-year figure retyped and the fifth was a fudge for the hypershunt. The honest conversion (`unitsPerLightYear`, 2000) is not what is wanted either: it puts a hypershunt's reach at 24000 units, flat across any system it stands in — true, and nothing for a screen effect to work with. The base is what a source is worth for being present at all; the slope keeps the ordering. A gate lighting up widens both scales at once, which is why `AberrationSource.GATE` overrides reach and weight and nothing else.

**Hyperspace has no entity reading at all.** Not an optimisation: the only two sources out there are the abyss, which is a depth field, and slipstreams, which are hyperspace terrain. Nothing else is reachable from a point that is not in a system, and nothing asks — ponds are only ever placed in systems and the rigs will not run outside one.

**A gate is two sources, not one, so it cannot go through the nearest-of helper.** Dormant it reaches 3 ly at 0.3; lit it reaches 6 ly at 0.85. Different reach *and* different depth means the nearest gate is not reliably the worst one - a dormant gate overhead can matter less than a live one two systems away - so each gate becomes its own mark carrying its own reach and weight (`AberrationSource.GATE` overrides both), and the index is taken at its strongest rather than at its nearest. `GateEntityPlugin.isActive` casts the custom plugin, so it is only safe on a vanilla gate; a foreign one falls back to the sector-wide `areGatesActive`.

**Other mods' equivalents are named by tag and cost nothing when absent.** `Global.getSector().getEntitiesWithTag` on an unregistered tag returns an empty list, so the foreign tags on `AberrationSource`'s rows are a hard dependency on nothing - `bifrost` reads as a gate, `aotd_hypershunt_receiver` as a hypershunt, `aotd_pluto_station` as planet-scale machinery. Adding another mod's object to the model is one string in one array.

**A mote handed its own spawn point as a destination dies on the first frame.** `FishEntityPlugin` swims toward its target and expires on arrival, so spawn and target must be different points - `QuestPond.placeMote` used to pass one point as both, and the keepers that replant a missing specimen then put it somewhere else in the pond, over and over. That is what "the quest mote teleports" was: not movement, a fade and a replant. The open-water planting in `FishermanQuest` had always spawned on one side and aimed at the other for exactly this reason; the pond path never got the same treatment.

**A named specimen can hold its water.** `FishEntityPlugin.HOLDS_KEY` makes a mote pick a new corner of its own pond instead of expiring on arrival, so it mills about and is still there when the player flies over. The introduction's first catch uses it; the chart requests and the camped-spot job deliberately do not, because a fish that is hard to pin down is a difficulty and the first catch is not where difficulty is taught.

**An industry blueprint is one item, not one per industry.** `industry_bp` is a single special item whose *data* field carries the industry id, which is what vanilla's own `AddRemoveAnyItem SPECIAL industry_bp <id>` is doing. So selling the plans for the conservatory needs no new item row anywhere - a `SpecialItemData(Items.INDUSTRY_BP, BreachConservatory.ID)` in the hold is the whole of it, and the game names it, describes it and teaches the faction from the industry spec.

**But an industry is not gated by being in a blueprint.** Nothing asks `knowsIndustry` on an industry's behalf: `BaseIndustry.isAvailableToBuild` answers "yes, if the market has people on it", so an industry that is meant to be learned has to refuse to build itself. Vanilla's `PlanetaryShield` overrides `isAvailableToBuild` *and* `showWhenUnavailable` against `knowsIndustry`, and that pair is the entire reason its blueprint means anything - `BreachConservatory` now does the same. Selling a chip for an industry that has not been gated teaches a faction something it could already build.

**`despawn()` files the paperwork; it does not clear the board.** Accepting a fleet quest copies the giver into a hull of the mod's own and retires the original, and calling `despawn(reason, null)` tells whatever was managing that fleet it is gone - but leaves the hull sitting in the system beside its copy, still steered by its own AI, visibly two fleets. The disposal is `setAI(null)`, move to the origin, then `Misc.fadeAndExpire` (note the name - there is no `fadeAndDespawn`).

**`EndConversation` hands the screen back to the fleet encounter, which is not an exit.** The encounter then offers the comm link again, which reopens the same conversation: continue, leave them to it, continue, with no way out but the escape key. Any ending that means "and now you are done with this fleet" wants `DismissDialog`.

**Two flags are the only things that colour a comm link, and both erase themselves.** `$hailing` and `$highlightComms` are read and `unset` by `FleetInteractionDialogPluginImpl` as it builds the option, so a highlight is a one-shot that has to be re-set for every encounter it should appear in. Which means the bug is never a highlight that fails to clear - it is something re-setting the flag on a condition that outlived the reason for it.

**A memory flag makes a fleet willing to chase; an assignment makes it chase.** `MEMORY_KEY_PURSUE_PLAYER` and friends are read when a fleet already has the player as a target, so a freighter told to come and collect a repair bill went on flying its trade route instead. `MEMORY_KEY_MAKE_ALWAYS_PURSUE` plus an explicit `FleetAssignment.INTERCEPT` is what turns the willingness into a course change - see `HarpoonOffence.demand`.

**There is no `FleetAssignment` for running away.** `MEMORY_KEY_AVOID_PLAYER_SLOWLY` is the whole of vanilla's civilian evasion and it does what it says - it biases steering and shortens committed headings, which against anything faster is dawdling in the right direction. A crew that is meant to read as fleeing needs the emergency burn (`Abilities.EMERGENCY_BURN`, asked for with `isUsable()` and never forced) on top of it.

**The lamp response is the transponder's shape, not the harpoon patrol's.** A harpooning is an
incident on a faction's books that a patrol is sent about days later; lit lamps are something the
player is doing right now that anybody in line of sight can see. `LampPatrolResponse` therefore
dispatches nothing: every eligible patrol that can see a near-world offence interrupts its current
assignment with an intercept, regardless of its faction or the world's. The first responder to open
an encounter claims the dialogue; every other responder drops only that intercept and resumes its
queued duty. The stop is committed in the same way a transponder violation remains seen after the
switch is flipped. Lights-out establishes the run boundary immediately but does not clear those
intercepts, so the first encounter still books the warning and later encounters apply the existing
fine/inspection/guns ladder. The persistent state is the sector-wide rung count, the resolved **burn
number**, and each crew's last handled burn number, not the rules row's temporary stopped boolean.
Relighting after lights-out starts a new burn once the committed encounter is settled. A continuous
burn keeps one number, so it cannot produce a queue of identical stops.

**The loot card has two clocks, and only one of them is the readout's.** Its list is held back
until the specimen has finished being tallied, so `elapsed` is zero for the whole of the first
readout — a backdrop driven off it hung motionless over an open card. `advanceBackdrop()` runs from
the moment the card exists and is what the coin rain reads; `advance()` stays the list's own.

**A landed fish goes into a crate, not into the hold.** `FishItems.stow()` is the only landing
path — a good night produced forty single-fish stacks and a hold nobody could read. Loose specimens
still exist and every buyer and job still spends them; nothing *makes* one by default any more.

**A crate and a pile are the same shape, and that is the whole reason the pile was cheap.**
`FishItems.isContainer()` is the question everything spending, selling or counting fish is really
asking — it used to be spelled `BUNDLE.equals(...)` at a dozen call sites, which is a line that has
to be found again every time a container is added. Anything taking fish out of one must put the
remainder back with `FishItems.repack(id, …)` rather than `toBundle`: a part-spent pile rebuilt as a
crate would file every species in it under whichever happened to be first.

**A hand-drawn control grows a stock tooltip through a hotspot, not a hand-drawn card.** The
shopping-list ring is painted by `ShopRowPlugin`, not a `ButtonAPI`, so there is nothing to hang a
tooltip on directly — but `TooltipMakerAPI.addTooltipTo` accepts any component, including one
nested inside another custom panel. The outfitter drops a transparent `CustomPanelAPI` over the
ring's slot in each row and attaches the tooltip to that, which scopes it to the ring and lets
vanilla own placement, clipping and timing. (The old fear that a card in the scrolling list would
be sliced off by the scissor box only applied to hand-drawn cards; stock tooltips render above the
whole screen and never did — the locked-row tooltip in the same list proved it.)

**A fleet quest never spawns a fleet, and until it is accepted it only stops one from avoiding the
player.** The offer is memory keys and a `FleetQuestMarker` hung on a scavenger already in the
player's system — no rename, no orders, no `$missionImportant`. The quest refreshes vanilla's
never-avoid flag under its own reason and removes the player from the navigation module's avoid
list, as the Fisherman does, so the hull does not run from the fleet it is trying to hail. Release
removes that reason on completion, refusal or expiry without disturbing another system's reason for
the same flag.

**Accepting supplants the hull.** `FleetQuest.supplant()` builds a copy (fresh members off the same
variants, so nothing is owned by two fleets at once; only the source market carried over from the
old memory) and despawns the original *with a report*, so whatever was running it — a trade route, a
scavenger sweep — hears that it is gone rather than waiting forever on a fleet parked in a system
for two months. What is left answers to nobody but the job, which is what makes it safe to pin. A
successful hand-in calls `release()` as soon as the mission begins ending; failures still clean up
when the intel finishes. Release uses vanilla's return-to-source assignments, whose final order
despawns the hull.

**The mark's colour is the message, and it is drawn rather than flagged.** `$missionImportant` is one
boolean with one colour, and setting it on somebody's trade fleet would also make the game treat it
as story furniture. `FleetQuestMarker` copies vanilla's sprite, corner and zoom arithmetic exactly
and changes only the tint. Once the offer is accepted the cyan comes off and vanilla's own takes
over — at that point it is no longer passive.

**The Fisherman is pinned visible while anybody is in the system to see him.** His lamps are drawn
wherever the boat is, whether or not the hull carrying them can be made out — so a Fisherman at the
edge of a sweep was two searchlights working the dark on their own. `keepVisible()` does both halves:
a flat `getDetectedRangeMod()` so he is never a blip, and `forceSensorFaderBrightness(1f)` every tick,
which is a per-frame override rather than a setting and is how vanilla drives its own faders. It is
re-applied each tick rather than set at spawn, so it heals a boat that predates it.

**The Fisherman is one person, made once — and he is on every boat.** Every other fleet in the game
is fresh hulls under a fresh officer with a fresh name; he deliberately is not, because the same
person turning up four jumps and eight months later is the point of him. Standing boat or visiting
one, the encounter shows the portrait belonging to the local coherence band and says nothing about
it. The flag that separates the two is
`$catchrelease_fisherman_visiting`, and it is about the *schedule* — nothing about who the player is
talking to hangs off it. `FishermanIdentity` keeps the `PersonAPI` in sector
memory and hands the same object back at every spawn, and the encounter shows the portrait rather
than a fleet readout — a thing a hull list cannot say. What changes is how well he is holding:
`getDrift()` reads the system's own instability through `Aberration.baseAt` (the deterministic
figure, not a specimen's jittered one), and the boat's name, portrait, greeting, and map-tooltip line
all come apart by degrees as it climbs. The portrait uses the complete five-rung specimen ladder;
the existing rules greetings keep their coarser four bands. Since every boat shares the same mutable
`PersonAPI`, `CatchReleaseCMD tokens` applies the hailed boat's portrait during `OpenCommLink`, before
vanilla calls `showPersonInfo`; background boats never compete over it. The five paths are registered
as `graphics.characters` sprite ids in `settings.json`, so every selected portrait has been loaded
before the comm panel draws it. Letters are taken out by position, so the same system spells him
wrong the same way every time — the degradation is a fact about the water, not an animation.

**All dialogue is in the sheet — all of it.** The Fisherman's whole conversation, the introduction's
six lessons, the hulk, the castaway and the bar rating are rows in `rules.csv`. Java is reached only
through `CatchReleaseCMD <verb>`: in a row's *conditions* `tokens` writes the dozen booleans the rows
branch on and always returns true, and in a row's *script* a verb does the thing and returns whether
it worked. The panels — shop, chart counter, cargo picker — stay Java, because a shelf of cards is
machinery and there is nothing for a sheet to say about it.

The Starsector Editor is a draft source, not the lore authority. ChatGPT runs it at High thinking,
supplies the exact display context, and rejects generic AI patterns and em-dash overuse before
integration. Every returned player-facing line is independently compared with `docs/LORE.md` and
its stated display context; an Editor QA result does not replace that check. The Fisherman's
question menu then keeps the same division: sheet rows add unasked topics first and asked topics
second, and `addFisherQuestion` gives the latter vanilla grey. The Longliner topic is exposed only
after breach light burns the boat disguise, then remains available across relocations and after the
catch; its answer denies the player's conclusion without defining what crossed the fabric. Question
answers never offer a direct return to business: terminal answers offer only `Something else`, and
follow-up branches return to the same question menu. The menu's own `I've heard enough.` option
remains the single route back to the Fisherman's business menu. The
searchlight/drone question joins that framework at `FISH_TWO` (`$catchreleaseStage >= 4`), records
`$global.catchrelease_fisherAsked_searchlightDrones`, and returns both its acknowledgement and its
schematic follow-up to the same menu. It explains the `BREACH_COUPLER` gate without granting it: a
lamp-made opening is not a full breach, an uncoupled drone would be stuck beyond the fabric, and
the harpoon's LINE keeps the retrieval system connected without being a physical cable.

**A work offer rolls once before it speaks.** `catchrelease_workOffer` gets or creates a pending chart
request in sector persistent data and fires `CatchReleaseWorkOffer`; declining, leaving, saving, or
returning to the offer reuses that exact fish-and-system pair until acceptance consumes it.
`catchrelease_workOfferText` then reads the saved fish, place and
payment tokens after the roller has set the explicit `$catchreleaseWorkRolled` success token. RC8's
rules parser does not implement `has` as a key-existence operator, so testing the fish-name string
itself can never select an offer row. A mutually exclusive failure row says there is no marked work
and returns to business, so an exhausted roller cannot leave an empty panel. Keeping the supplied
offer text on the success row means the range has been selected before token replacement, rather
than printing the fallback token names from before the roll. Its
`SetTextHighlights` call colours those three supplied terms, while the active-work reminder highlights
only its fish and place: it never makes a second promise about payment. The root menu keeps the
`catchrelease_fisherWork` option id in both states, but its mutually exclusive producers label it
`Ask about work` before a request and `What did I need to fetch again?` while one is active.

**The ladder gates equipment, and equipment gates the world.** `unlockedAtStart` is off for all four
abilities. A shelf needs two things before it is on the floor (`ShopGroup.isUnlocked`): the rig in
your hands, **and** the introduction's blessing. The lamp and harpoon shelves stay shut for one rung
*after* that gear arrives, because the gear and the errand to use it in are handed over in the same
breath and the upgrades for it are what finishing that errand buys — opening them together would make
the errand a formality. No bar job and no fleet job is offered at
all until the first errand is behind you (`FishingIntro.isOpenForWork`), and while the ladder runs an
ask never names gear you have not got — `FishJobAsks.rollCatchTerms` narrows to what is in your
hands, because an order you cannot act on is not an order.

**Three ways into the introduction, none required, and each fitted to where the player already is.**
Out where nobody lives, the first rupture they come within sight of gets a holed cruiser next to it —
sited *then*, not at sector generation, so it is always this rupture and never a thing already on the
map. Surveying a world out there instead turns up the one crewman who was put off a boat for looking
at the catch. In an inhabited system there is already a boat posted, so it simply *is* somewhere
else the next time anybody looks — `FishermanInterception` teleports it in outside the viewport and
nobody aboard remarks on it, which is the cheapest way to say what the Fisherman is. And hailing a
boat works on its own. All of them call `FishingIntro.point()`, which is idempotent. The first-hail
option keeps that provenance: recovered property takes precedence, then a rescued crewman, then the
rating's market name captured at the bar; direct starts and older saves without an origin keep the
generic fishing-work label.

**The shelf restocks off the sale, not off a calendar.** A monthly tick pays out to whoever happens
to ask just after it, which rewards standing still. Every purchase books a replacement due 30 days
later and asking is what redeems the ones that have come due, so the wait is the same wait for
everybody and starts when the player caused it. The shelf is two charts wide to begin with;
`FishermanShelf.widen()` is what a chart request pays out in, on top of the money, and it is the only
thing that ever raises it.

**A chart request puts the fish there and keeps it there.** A quest that names a system and then
leaves it to the spawn tables can be arrived at correctly and fail for an hour. `FishermanQuest.Keeper`
replants the specimen whenever the player is in the target system and it is missing — only then,
because a mote is an entity with a plugin on it and keeping one alive in a system nobody is standing
in is upkeep bought for nothing. In open water it is spawned on one side of the marked patch aiming
at the other, because a mote swims to its target and expires there; one spawned on top of its own
destination blinks out immediately. It always reads **barely holding**, forced in the minigame's
roll — the request is a question about the water, not about the animal.

**A chart request earns its chart before it is offered.** The offer is a fish-and-system pair, never
a species picked first and pasted into an arbitrary destination: it must live in that system, clear
the round's distance and theme limits, and be difficult, rare or epic, in pulsar water, or the
species a current camp is holding. Camps remember that species on the blocked rupture itself, rather
than on their planted mote, because catching the mote does not mean the people sitting on the water
have stopped making it notable. The request still claims only a free rupture; an occupied one is
evidence for the ask, never a destination that makes the ask impossible.

**A patrol assignment could not be told to stay out of the way.** `PATROL_SYSTEM` wanders the whole
system and will cut straight across an inhabited orbit getting anywhere, so the standing boats
route themselves: `OuterReaches` defines a band from past the outermost inhabited world out to a
little beyond the furthest thing in orbit — the far-flung planets are on the route, the populated
inner system is not — and `pick()` rejects a destination *and* the leg to it, since a fleet flies its
assignment in a straight line and a good destination reached by cutting through Jangala is the thing
being avoided. Legs are issued one at a time from where the boat actually is; a queue of them would
be a queue planned from a position it has since left. A system too crowded for any leg falls back to
the far edge, which is clear of everything by construction.

**Finding a fishing boat is a map problem, not a space problem.** Nothing is painted over the hull —
out in space the boat is already lit, and a mark there says nothing the lamps did not. The map is the
screen where it is one blip among forty, so `FishermanMapIcon` is a custom entity that rides the
fleet with `showInCampaign` false and `showIconOnMap` true — the pair vanilla's own `base_intel_icon`
is built from. It carries no sensor profile, so it is never a contact to be found; it is simply
drawn, which is what makes the boat locatable while it is out of sight. It exists only while the
player is in the boat's location: the behaviour removes every mark for its fleet when unwatched,
and `FishermanSpawner` strips all departed-location marks on the first frame of a load and every
location transition. `findOrAdd()` keeps a watched boat to one mark and cleans duplicates that an
older save may have carried.

**The Fisherman's visit is counted in days the player was not there for.** He cannot despawn in
front of anybody: the clock in `FishermanBehavior` only advances while the player is elsewhere, a
wind-down interrupted by the player turning up is cancelled outright, and the patrol assignment is
topped up rather than cut to fit a stay that no longer has a fixed length. The same check silences
him while nobody is watching — his lamps go into LunaLib's one sector-wide renderer list and his
sounds play wherever the player is standing, not where they were asked for.

**Closing the outfitter is not the same as closing the dialog.** `FishShopDialog` takes an optional
`OnClose`; without one, escape closes the standalone interaction, as the colony conservatory wants.
The Fisherman passes one, because the shop runs inside his conversation and dropping the encounter
would read as the shop having hung up on somebody the player was mid-sentence with. The way back is
the opener's to write: the shop hides the text and visual panels and dims the background going in,
and `InteractionDialogAPI` has no getter for what any of that was before.

**A harpoon has two kinds of target, with different rules.** An ordinary mote has to be unheld and
above the fabric (or the head has to reach under); a **buried** mote has to be lit by a beam, or
merely detected if a fathom head is fitted — and it carries `catchrelease_buried_mote`, not
`catchrelease_mote`, so a scan by the ordinary tag misses the entire sweep-expose-harpoon loop.
`HarpoonEntityPlugin.canTake()` is the single answer to "could a shot take this"; the strike and the
aim assist both read it, and they must not disagree in either direction. Assist on a looser test
pulls shots onto things the strike then refuses — worse than no assist, because the player would
have hit what they aimed at.

**`Stencil.startStencil` is deprecated — it breaks the campaign radar.** Use the depth-mask pair.

**A camera snapped to a thing kills that thing's parallax.** `ParallaxUtil`'s camera term is the
distance to screen centre, which is zero for whatever the camera is on. See `PondDepthField`.

**`showCustomDialog` always builds a confirm button.** For a panel that wants none, use
`showCustomVisualDialog` with a `CustomVisualDialogDelegate` — which is how the minigame is hosted.

**`ReflectionUtils` goes through `MethodHandle`** because the classloader rejects any mod class that
names `java.lang.reflect.Field` or `Method` directly.

**Upgrade sheet column names are forgiving on purpose.** `UpgradeStatLoader` accepts both
`type`/`baseType` and `increaseType`/`upgradeType` — a mismatch once made every MULT upgrade silently
do nothing. Its `icon` column is an optional texture path; a blank cell deliberately falls back to the
entry's `ShopGroup` mark.

---

## Dead or dormant

| What | State |
|---|---|
| `campaign/ponds/entities/StenciledFishingPondEntityPlugin` | Dead. The pond is terrain now |
| `campaign/fish/intel/FishMapIntel` | Dead husk, kept so old saves can delete it. Removable once no save predates the map filter |
| `campaign/fish/shop/ShopStorage` | The store/retrieve/sell counter is gone. Kept only to hand back fish a save is still holding in it, once, on the next shop open |
| `testing/DevShortcut` | Registered from `ModPlugin` as a transient listener; does nothing unless dev mode is on |
| `testing/TestStencilRenderer` | Commented out of `ModPlugin` |
| `campaign/ponds/renderer/PondHoleRenderer` | Dormant behind `PondConstants.POND_HOLE_LOOK`, which currently selects the shader swirl - the pond's intended look, rolled back to after a trial of the stencil hole |
