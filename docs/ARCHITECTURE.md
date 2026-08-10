# Catch.Release — file and feature map

What is where, and which file to open first. 220 Java files across eight top-level packages, plus
the data tables that register them.

Kept by hand, and updated by every change — not only when a package gains or loses a file, but
whenever what a file does, what registers it, or how the pieces fit moves. The update belongs in
the same commit as the change. A map that is wrong is worse than no map, because it is believed.

Not mapped below, because none of it is ours: `lib/` holds the game's API source and the three
dependency mods, zipped, to be read rather than edited.

**[`LORE.md`](LORE.md) is the setting and writing authority** — what a breach, a pattern, the ROD,
the Fisherman and Crablobab actually are; who notices contradictions; who is allowed to know what;
the tutorial's information-release order; faction voices; terminology; and the prose rules every
player-facing line follows. Read it before writing `rules.csv`, a species description, a tackle
blurb, an intel note, colony text or UI copy. It also carries the settled unknowns the fiction must
not explain.

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
6. `FleetQuestSpawner.register()` — fleets out in the world that want fish
7. `FishermanSpawner.register()` — the daily roll for the visiting fishing boat
8. `CoreFisherSpawner.register()` — one standing boat to every inhabited system
9. `FishermanQuest.Keeper.register()` — keeps a chart request's specimen in the water
10. `TutorialWreck.Watcher.register()`, `Castaway.Watcher.register()`,
   `RatingBarEvent.VisitCounter.register()`, `FishermanInterception.register()`,
   `FishingIntro.Keeper.register()` — the introduction's hooks and its errand keeper
11. `ConservatoryOptionProvider.register()` — the conservatory's options on the colony screen
12. `AquariumTankScript.register()` — the aquarium on the colony's main menu
13. `UpgradeManager.getInstance().updateBaseValues()` — re-reads the upgrade sheet into the save
14. `SkillshotFramework.register()` — the aiming framework
15. `FishMapFilterScript` as a transient script — the sector-map filter
16. `FishIntelPlanetPanel` as a transient script — the intel Planets view's fish panel
17. `CoherenceOverlayScript` as a transient script — the low-coherence screen overlay
18. `sweepPondClaims()` — one walk, taking the mission marker off every rupture no errand is holding
   any more and fading every planted specimen no errand is still waiting on; repairs saves
   carrying either, since transitions cannot
19. `DevShortcut.register()` — the Ü key, as a transient `CampaignInputListener`; inert unless dev mode is on

`beforeGameSave()` — `SkillshotFramework.reset()`.

Registration is idempotent: the `register()` methods unregister by id first, and transient scripts
are rebuilt every load because their state lives in sector memory rather than in fields.

---

## Registered by data, not by code

Classes the game instantiates by name. Grep the data file, not the call sites — there aren't any.

**`data/campaign/abilities.csv`** — 5 rows. Three live fishing abilities are
`unlockedAtStart=FALSE` and granted by `FishingIntro`; `catchrelease_shop` is a hidden, inert
one-release migration stub so old saves can deserialize before load cleanup removes it.

| Id | Class |
|---|---|
| `catchrelease_searchlights` | `abilities/searchlight/ability/SearchlightAbilityPlugin` |
| `catchrelease_rod` | `abilities/rod/ability/PondInteractionAbilityPlugin` |
| `catchrelease_harpoon` | `abilities/harpoon/ability/HarpoonAbilityPlugin` |
| `catchrelease_shop` | `campaign/fish/shop/FishShopAbilityPlugin` (legacy migration stub) |
| `skillshot_example` | `skillshot/example/ExampleSkillshotAbility` |

**`data/config/settings.json`** — `ruleCommandPackages`, listing vanilla's five packages **plus**
`catchrelease.dialogue.rules`. The key is read once from merged settings and **replaces** rather than
merges, so vanilla's have to be re-listed; dropping any of them breaks every rule in the game.

**`data/campaign/bar_events.csv`** — 14 jobs, all `FishJob`s: 11 in `campaign/fish/jobs`, plus the
three camp events in `campaign/fish/jobs/camp`, whose shared base `CampedSpotJob` extends `FishJob`
like the rest. The two bar encounters that are *not* jobs — Crablobab and the rating — are
`AddBarEvents` rows in `rules.csv` instead, with no Java at all. **Three of the job ids do not match
their class name:**

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

**`data/campaign/terrain.json`** — `catchrelease_StaticPond` → `MaskedFishingPondTerrainPlugin`,
`catchrelease_coherence_field` → `CoherenceTerrain`. Carries the plugin class only; name, radius,
layers and tags all come from the plugin — including the terrain id as a tag, which `BaseTerrain`
does **not** add for you and which anything looking a terrain up by tag depends on.

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

**`data/config/custom_entities.json`** — the motes, harpoon, drone, the fishing boats' map mark
(`catchrelease_FisherMapIcon` → `FishermanMapIcon`) and the introduction's one live prop
(`catchrelease_TutorialWreck` → `TutorialWreck`). `catchrelease_Castaway` remains registered only
so legacy saves can deserialize and retire its old beacon during migration. The pond is **not** here
any more.

**`data/campaign/rules.csv`** — all dialogue. See the contract below. One row registers *behaviour*
rather than words and is easy to miss when hunting for it in Java: `catchrelease_fisherEncounter`,
on `BeginFleetEncounter`, does `unset $ignorePlayerCommRequests` then `OpenComms`, which is the whole
reason walking up to a fishing boat opens the conversation instead of the engage/disengage screen.
There is no plugin behind it — see the gotcha below.

**`data/config/sounds.json`** — 6 ids of our own, merged into vanilla's ~600. Ability sounds are
named in `abilities.csv` (`uiOn`/`uiOff`/`uiLoop`/`world*`), not in code.

---

## The rules.csv contract

The language itself - triggers, memory scopes, operators, the truthiness gate, scoring, and
the traps this repo has hit - is in [`RULES.md`](RULES.md), with
[`rules/engine_workflow.md`](rules/engine_workflow.md) and
[`rules/command_table.md`](rules/command_table.md) behind it. What follows is only what this
mod does on top of it.

**Every word anybody speaks is in the sheet — jobs, the Fisherman, the introduction, the props.**
The current overhaul is 470 logical rules. Its supplied dialogue is kept verbatim except where a
later requested rewrite explicitly supersedes it; the additional
rows are routing twins and interrupted-conversation resumes needed to make that dialogue executable.
The rupture-interception twin for `catchrelease_introCurious` omits only the final supplied
`Come alongside` sentence because the interception greeting has already delivered that same line.
The Fisherman's `Ask about something else` submenu is a post-tutorial menu: its root option is
gated on stage 6, while the question rows retain their own information-release gates.
Each repeatable topic records a campaign-long asked flag only when its answer opens. Unasked
topics stay in the root submenu; answered topics move to `Ask again`. Both panes page their
topics six at a time, retain their current page after an answer returns, and hide a next-page
option that has no topic left to show.
Its name question asks `Do you have a name?`; Baha is introduced by the Fisherman's registry answer
rather than assumed by a player option before any scene has supplied it.
During the tutorial the intro option is the single route for both target reminders and hand-ins;
the older `Ask about the fish they want` producer remains as a preserved row but adds no option.
When the stage-3 target is in the current system, the two continuity questions are root Fisherman
menu options under the same target and deep-handoff gates that formerly nested them under that
reminder. Each answer writes its own permanent campaign asked flag as it opens, removing only that
root option thereafter; neither question enters the post-tutorial `Ask again` menu. Their existing
answer chains return through `catchrelease_fisherBack` to the root menu.
The Fisherman's fish-selling option is withheld until stage 3, after the first tutorial catch has
been handed in; carrying fish before that point does not expose the general sales flow.
If the rumor roller has no lead, its reply ends there instead of appending the successful-rumor
seasonal line.
Landing treasure with a fish records the first bycatch recovery; the next Fisherman root menu
offers a highlighted one-time question about what came up with the catch. Selecting it plays the
existing explanation, consumes the pending state, and returns to the usual menu without entering
`Ask again`.
The safety interception remains higher-scored and therefore still takes precedence.
Every option that completes a fish quest is coloured with rules-engine `SetOptionColor ... highlight`:
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
post-tutorial Fisherman entries, moving the topic to the appropriate `Ask again` page; neither
path carries a tutorial grant or advance.
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
| `FishSpec.java` | One row of the fish table: identity, minigame stats, value/size range, where it lives |
| `FishCatch.java` | One rolled specimen — length, weight, aberration, origin, and how it was taken; grades, values, encodes to a string |
| `FishGrade.java` | Five-step quality ladder, size fraction → value multiplier and colour |
| `FishRarity.java` | Rarity ladder with mote colour, speed and wander multipliers |
| `FishMotion.java` | Minigame movement archetypes (SMOOTH, DARTER, SINKER, FLOATER, MIXED) |
| `FishLog.java` | Sector-persistent per-species record; unlocks location data for codex and map |
| `FishLogEntry.java` | Per-species log data: counts, records, first/record location and time, capture method |
| `AberrationSource.java` | The registry: every kind of source as a row — label, tags (vanilla and foreign), light-year reach, weight, and whether it waits to be surveyed. The in-system reach is derived from the light-year one, never tabulated beside it. Adding a source is a line; adding another mod's version of one is a tag |
| `Aberration.java` | 0–1 aberration for a location — the inverse of coherence — strongest source wins. One crawl of the sector builds a flat mark index; system readings are computed off it and cached; `localPull` is the only per-frame figure and never leaves the system it is asked about. `Watcher` fills on arrival and on the sector map opening |
| `SectorRegion.java` | Nine-way sector location enum (8 quadrant bands + ABYSSAL) |
| `StarColour.java` | What a system's sun looks like, from its star's planet type |
| `FishHabitat.java` | Everything a place says about itself — sun, tags, region, constellation age, coherence — read once and cached |
| `CatchImplement.java` | What made a fish reachable — a pond or a breach lamp — read off the mote's own provenance |
| `FishLocationSummary.java` | Builds the "where this swims" sentence from every habitat criterion a spec sets |

### `campaign/fish/jobs`
Bar-given jobs on a shared spine, plus the ask/reward rollers they share.

| File | What it does |
|---|---|
| `FishJob.java` | The spine: asks, rewards, hand-over, intel, and the `rules.csv` token contract. Hand-over opens an asynchronous exact-specimen picker, then fires the existing payout rows only after the chosen fish have been spent. A `FishAsker`, so its asks reach the wanted-fish marks |
| `FishHandoffPicker.java` | Builds the eligible loose-fish cargo, validates an exact non-overlapping assignment against every ask (including same-species orders), and spends only the specimens the player selected |
| `FishJobAsks.java` | Rolls ask parameters — weight floors, species, type variety — off the fish table |
| `FishReward.java` | Reward base plus Credits, Upgrade, Tackle, LocationData, Backdrop and Blueprint. LocationData is the internal range-data reward; it carries its rolled cash value and turns into that credit payment if the species' range becomes known before handoff. The retained Commodity class is only an old-save shell and converts serialized goods payouts to credits |
| `FishRewardRoller.java` | Rolls a commodity-free payment scaled to a job's worth and preserves that value on range-data rewards for live redundancy conversion. Cash outcomes pay at five times the internal barter value so ordinary fish jobs compete with sector work without multiplying upgrades, tackle or blueprints. Equipment-gated tackle does not enter the reward pool before its prerequisite rig is in the player's hands |
| `QuestPond.java` | Claims and releases a pond for a job, hangs vanilla's gold mission marker on it while claimed, and seeds a flagged quest mote into it. Holds are a **set** of job ids, so two errands on one rupture cannot strand each other's marker; `releaseAll` lets go sector-wide and `sweep` is the load-time repair for saves that already have one burned in. A planted mote records its planter, so `clearMotes` takes it back out when the errand ends — a holding specimen never expires by itself |
| `StandingOrderJob.java` | The plain one: quantity, rarity, grade, no extra mechanic. The baseline |
| `AcademyJob.java` | Wants a low-coherence specimen; Galatia or large independent markets |
| `ButlerJob.java` | One fish above a rolled weight floor, paid by that floor |
| `ChefJob.java` | Three *different* types for a dish; sometimes FINE grade |
| `CompanionJob.java` | Hegemony only; one fish over a floor, bonus for exceeding it |
| `CultJob.java` | One specific named species, no credits |
| `CuratorJob.java` | Several fish, accepting either fine-grade or low-coherence uncommon+ |
| `KidsJob.java` | Two fish for two children; player picks who gets the better one |
| `MafiaJob.java` | Two fish for a betting ring; flat fee, or wager with odds off specimen quality |
| `StartupJob.java` | Three rounds of growing quantity, no time limit |
| `TuberJob.java` | Two rounds: a fine rare first, then a low-coherence one "for content" |

### `campaign/fish/jobs/camp`
A fisher whose one good rupture has somebody parked on it. Three bar events, three ways through.

| File | What it does |
|---|---|
| `CampedSpotJob.java` | The shared job. Two conditions rather than one — clearing the camp is the work, the specimen is only the receipt. Asks `CampedSpot.isGone` and nothing more specific, so it never has an opinion about how the player did it. The offer only chooses the rupture and terms; the physical camper, pond claim and planted specimen are created on acceptance so discarded bar-event rolls cannot leave fleets behind |
| `CampType.java` | Who is out there: pirates (there for money, will take money), mercenaries (paid to be there, and say so), pathers (not selling anything, and the bribe does the least good). Mercenary rather than independent deliberately — see the note in the file |
| `CampSize.java` | Small, medium, large, and the words the fisher uses for each. The estimate is honest; it is the only warning the player gets |
| `CampedSpot.java` | Spawns the camper on the rupture. When the player first enters its location, the fleet intercepts until its one-time warning hail has fired; it then returns to a passive hold and always uses vanilla's allow-disengage flag. The rupture carries a separate live camp flag that blocks the ROD only until the camper is gone. Spawned rather than borrowed, because the job is about one specific pond and there is no fleet already parked on it |
| `PirateCampJob.java` · `MercCampJob.java` · `PatherCampJob.java` | One per bar event, so each fisher gets their own pitch |

### `campaign/fish/jobs/fleet`
Jobs hung on a hull that was already out there, which then has to still be there when you return.

| File | What it does |
|---|---|
| `FleetQuest.java` | A `FishJob` whose giver is a fleet. `offer()` hangs it and touches nothing else; `take()` supplants the hull with a copy, then `mark()` and `hold()`. Its hand-over uses the shared specimen picker, then resumes the fleet sheet and leaves the encounter from the callback |
| `FleetQuestSpawner.java` | Hangs an offer on a hull already in the player's system; spawns nothing. **Scavengers only**, and never the Fisherman — the errand assumes somebody already picking over the system with no schedule to keep, and the trade's own boat would be copied away by accepting it. Rare on purpose: one active at a time, 7% a check, 45-day cooldown |
| `FleetQuestEncounter.java` | Runs one offer — reads the answer once the dialogue closes, re-hangs the mark after a load, times the offer out |
| `FleetQuestType.java` | Seven flavours of trouble, with pitch text, ask rolling and base worth. `fleetType` is a preference between candidates, not a recipe |

### `campaign/fish/colony`
The Breach Conservatory: the structure that brings the fishing trade to the player's own colony.

| File | What it does |
|---|---|
| `BreachConservatory.java` | The structure itself; also holds the aquarium's stock, its on/off switch and which backdrop this tank hangs |
| `ConservatoryOptionProvider.java` | The two colony-screen options: the fish outfitter and the aquarium office |
| `AquariumManageDialog.java` | The office: stock the tank, empty it, change the scene behind the water, or shut the display off. The scenery rack pages owned backdrops six at a time so Previous, Next and Back always fit under the nine-option ceiling; it previews on hover with an actual `AquariumTankPanel` rather than a picture of the art |
| `AquariumTransfers.java` | Hold-to-tank and back, both through the vanilla cargo picker. Depositing unboxes the hold first so every specimen is independently selectable; withdrawing is already loose fish from the tank |
| `AquariumTankScript.java` | Hangs the tank on the colony main menu, below the planet's image, and takes it down again whenever another visual is showing. Mounts as soon as the docked core UI is anything short of fully covering, rather than waiting for its fader to finish, so the tank comes back with the menu |
| `AquariumTankPanel.java` | The tank: GL water with caustics and light shafts, kelp and stones, an optional backdrop png, and every specimen swimming its own way at the size it was actually landed. How one *carries* itself is its `Build`, off the crab/mollusc/fish tags rather than off its motion: fish slant up to `MAX_PITCH` and no further, molluscs and oddments never turn and only list, crabs live on the stones. The drawn angle is the bounded pitch, never the raw heading, so nothing rotates up through the vertical to come about |
| `Backdrop.java` | One row of `data/campaign/backdrops.csv`: a scene for behind the water — name, art path, rarity, whether Crablobab stocks it, whether a conservatory has it from the start |
| `Backdrops.java` | Two scopes: which scenes the *player* has come by (sector memory) and which one a *conservatory* is hanging (the industry). Resolution, ownership and the has-the-art-been-drawn question |

### `campaign/fish/fisherman`
The fishing trade. **One man, many boats** — a standing trawler in every inhabited system working the
outer reaches off one shared shelf, and a visiting one that turns up in uninhabited water for a
fortnight with a shelf of its own. Every one of them answers with the same face, and none of them
explains how.

| File | What it does |
|---|---|
| `FishermanSpawner.java` | The visiting boat: one roll per arrival in an uninhabited system - a small base leaned on by a full hold and a long absence - after which the system is locked for a month so re-entry is not a re-roll. Its sector sweep recovers a visiting boat whose old-save pointer was lost, keeps one visitor sector-wide and one Fisherman per system, and marks on-screen legacy extras for off-screen retirement rather than making them vanish |
| `CoreFisherSpawner.java` | One boat to every inhabited system, re-posted if it is lost - weekly, and again the moment the player arrives, so a destroyed boat is back by the time anybody looks. `ensureBoat` takes the canonical live Fisherman of either schedule before posting, so a directed tutorial errand cannot place a standing boat beside a visitor |
| `CoreFisherBehavior.java` | The standing boat: the same rig and the same man, no visit clock, and the outer-reaches route |
| `OuterReaches.java` | Where a boat is willing to be, and which legs clear the inhabited worlds. `place()` is the one gate every boat placement goes through: clamped into the band in an inhabited system, unconstrained where there is nobody |
| `FishermanBehavior.java` | The stay: yellow fan lamps, staged motes, the leaving. `keepStanding()` pins it non-hostile and un-fleeing, and puts it outside every other fleet's business in both directions; `keepPace()` holds it to burn 4 unless it is closing on somebody |
| — | Talking to the boat is not a file. The encounter goes straight to comms (`catchrelease_fisherEncounter`), and the range-data counter, outfitter, buyer, rumours and chart requests are all rows under `$menuState == catchreleaseFisher` |
| `FishermanShelf.java` | What range data is on sale and on which boat — two slots to start, the pool that stops duplicates, and the restock dated off each sale |
| `FishermanQuest.java` | Chart requests: one named specimen from one named place, kept in the water until it is landed - at which point the claim comes off the rupture, the planted specimen comes out of the water and the note turns into "take it back". Hand-over uses the shared exact-specimen picker. Its `QuestIntel` is a `FishAsker`, shows the shared fish silhouette until its target has been landed, and carries the bullets vanilla's mission notes carry |
| `FishermanSurveyDialog.java` | The chart counter: the shelf as silhouette cards, component-built in the sidebar's language. It clears the host interaction's options immediately before opening its custom visual, and hands the Fisherman's sheet back exactly once on every close route |
| `FishermanMapIcon.java` | The boat's mark on the system map — one per boat while the player shares its location, with old-save duplicates and marks in departed locations reconciled away |
| `FishermanIdentity.java` | The one person, kept for the campaign — and how far gone he reads where the fabric is thin |
| `FishermanBycatch.java` | The one-shot bridge between recovered treasure and dialogue: remembers the first landed bycatch until the player asks the Fisherman about it, then permanently retires the topic |
| `FishRumors.java` | One rumor a month — rarer rolls, richer treasure, or a stranger species. It exposes only the saved facts to the rules sheet, which owns the spoken scene; `RumorIntel` gives the same lead in precise intel prose and counts down against the rumor's own timestamp. `ensureTutorialLead` idempotently creates the graduate's first rumor outside the monthly ask gate and migrates already-completed saves |
| `FishermanConstants.java` | Every number the above read |

### `dialogue/rules`
The one rule command the mod ships, and the only place the sheet reaches into Java.

| File | What it does |
|---|---|
| `CatchReleaseCMD.java` | `CatchReleaseCMD <verb> [arg]` — writes the branch tokens (including stage-gated Fisherman outfitter access, local-target location, interrupted deep-gear handoff, pending first-bycatch explanation, the active rumor's system/type/stranger, and the bulk-sale rungs that currently have eligible fish), opens the panels, walks the ladder, resolves the one-use castaway rescue, and reaches into the encounter screen where a row cannot: `leaveEncounter` (vanilla's battle teardown, then dismiss), `dropCutComm` — the latter needed once per menu state, since vanilla's `convOptionLeave` is conditioned on `$isPerson` alone and rejoins every `FireAll PopulateOptions` — and `colorBulkSaleOptions`, which applies the canonical common/uncommon colours and exact cargo previews to the Fisherman's eligible bulk-sale choices. A panel return restores the Fisherman's plugin then clears and rebuilds its options once, so custom-visual dismissal cannot duplicate or lose the sheet |
| `FishBuyer.java` | Selling the catch: the picker, the batch rungs, and the immutable whole-stack sale preview shared by labels, tooltips, confirmation and execution. Bulk sale confirms its exact fish/credit total, rebuilds the prompt instead of selling if the hold changed, leaves any container with a marked fish intact, and counts every copy in stacked identical containers. Opening the picker unboxes first so crates and the pile do not force an all-or-nothing sale |

### `campaign/fish/tutorial`
Learning to fish, in six lessons and one shortcut. **Entirely detached from the ordinary loop** — the
trade runs whether or not any of it has happened. What it gates is *equipment*, and through that
everything downstream. Not a word of what it says is in Java.

| File | What it does |
|---|---|
| `FishingIntro.java` | The seven stages, the errand targets, the grants, the shortcut, and `IntroIntel` on vanilla's tutorial-mission icon. Replacing a target explicitly updates the persistent intel destination; the two-chart rung returns no single map location, leaving its several destinations to the planner instead of falling through to the prior system's boat. Tutorial species are filtered through the same `FishHabitat` plus `CatchImplement` predicate as the real spawner, then capped common-only for the first two target rungs and common/uncommon for the next two, so a destination is never paired with an impossible or prematurely rare species. The Harpoon lesson further requires a species whose source metadata is exactly `BREACH_LAMP`: it cannot come from a pond, and drones only catch pond fish, so the live target can only be landed by harpoon. If a normal target system has no capped real-spawn candidate, the first lesson remains in the current system and later lessons use an id-sorted thin-first fallback that still obeys their 2–10 LY range; the fallback is logged, while an all-empty pool logs a warning and creates no impossible target. Assigning the second lesson immediately reserves the canonical Fisherman already in its target system; an uninhabited-system posting is held for that errand and despawns only after the lesson ends and the player leaves, while a reused visitor is held then released back to its own lifecycle. `Keeper` re-applies that reservation from the saved target, making mid-lesson load repair idempotent. The second-catch handoff introduces the Fisherman's outfitter before the deep rigs arrive and carries a pending flag so an interrupted conversation resumes correctly; it grants no shop ability, and load migration removes that dev-era shortcut and its hotbar slots from existing saves. The shortcut grants the same 2 common/1 uncommon/1 rare range-data mix as the full route. `Keeper` both plants the specimen and watches the hold for it: the first any-species lesson guarantees a specimen without reserving or marking its rupture, while named-location rungs claim theirs; landing one releases the rupture, takes the planted specimen back out of the fabric and re-points the note at the boat. A `FishAsker`, so the rung's quarry wears the wanted-fish mark |
| `TutorialWreck.java` | A stripped auxiliary beside the first rupture seen out where nobody lives, carrying the Fisherman's damaged LYNE service assembly as a navigation breadcrumb rather than usable early gear |
| `Castaway.java` | A rating missed during a badly reconciled crew transfer, discovered by intercepting the host planet's ordinary survey selection and routing into the preserved rules dialogue. The host and rescue are persistent market flags; legacy orbiting beacons are recognised only by their stable type/tag, converted to their orbit planet if unfinished, and retired without ever touching a planet |
| `RatingBarEvent.java` | The port counter the sheet's bar version is gated on, and nothing else |
| `FishermanInterception.java` | The boat that is simply *there* when somebody nears a rupture unequipped - and the only thing that lets it off burn 4 while it closes. Its drop point is clamped into the reaches, so a rupture in the inner system no longer parks a trawler against the star |
| `TutorialConstants.java` | Every number the above read |

### `campaign/fish/minigame`
The catch itself. Rules are separated from rendering on purpose.

| File | What it does |
|---|---|
| `FishingMinigame.java` | Rules only: bar/fish physics, progress meter, treasure rolls. No GL, no input |
| `FishingMinigamePanel.java` | Draws the track, bar, fish and meter; handles mouse and keyboard; records first-bycatch discovery only when the fish and its held treasure are actually landed |
| `FishingMinigameDialogPlugin.java` | Hosts it as a custom *visual* dialog; owns the dev controls |
| `FishingMinigameLayout.java` | Per-frame positions for track, meter and result cards |
| `CatchResultPanel.java` | The catch readout: specimen box, stats revealed line by line, best-ever banner |
| `LootResultPanel.java` | The mirror card listing treasure recovered alongside the fish |
| `CatchCelebration.java` | Flash, backlight and flourish on a landed fish. The confetti is bought — see `campaign/fish/crab` |

### `campaign/fish/treasure`
Optional loot found mid-catch.

| File | What it does |
|---|---|
| `MinigameTreasure.java` | A stationary timed pickup that must be held over to be taken |
| `TreasureRoller.java` | Rolls whether treasure appears and what is in it |
| `TreasureAward.java` | What a roll granted, for the loot card |
| `TreasureRarity.java` | Four-tier rarity with weight and colour |

### `campaign/fish/entities`
The two forms a fish takes in the world.

| File | What it does |
|---|---|
| `FishEntityPlugin.java` | The swimming mote: motion archetypes, diving, held/stunned states, glow. Pond motes let the terrain draw that glow inside its stencil; pondless motes draw themselves, so a buried mote surfaced by a breach-lamp harpoon remains visible through the shove and catch |
| `BuriedMoteEntityPlugin.java` | Invisible mote under the fabric; `unearth()` turns it into a real one |

### `campaign/fish/spawner`
Which fish, where.

| File | What it does |
|---|---|
| `PondFishSpawner.java` | Weighted selection filtered by star type, tags and region; biased by drone tackle and rumors |
| `BuriedMoteSpawner.java` | Keeps a target buried-mote population around the player |

### `campaign/fish/shop`
The outfitter: upgrades and tackle bought with fish.

| File | What it does |
|---|---|
| `FishShopDialog.java` | The dialog: tabs, list, detail pane, buy - the store/retrieve counter is gone. It clears the host interaction's options immediately before opening its custom visual, and delivers the close callback once whether it was reached by LEAVE, Escape, or the visual's own dismissal |
| `ShopEntry.java` | Wraps one shelf item — upgrade, tackle or curio — behind uniform price/state/buy |
| `ShopGroup.java` | The shelves, and which stat ids and rigs belong to which |
| `ShopPricing.java` | Per-campaign seeded prices in credits and fish. The capability-changing Breach Coupler occupies the unique top tackle tier: 20,000 credits plus a tier-five named catch ask |
| `ShopMarks.java` | The shopping list: marked upgrades feed the route planner and hang the quest-yellow dot on every fish that would pay for them. `isMarked` is the marks alone, which only the outfitter asks; `isWanted` counts every `FishAsker` in the log too, which is what the dot means on every other screen, and is cached because it is asked per cell per frame |
| `FishAsker.java` | The interface anything waiting on a fish implements — `FishJob`, `FishingIntro.IntroIntel`, `FishermanQuest.QuestIntel`. What `ShopMarks` walks the intel log for, so a species an errand wants wears the mark whether or not the errand is a bar job |
| `FishCurrency.java` | Counts and spends fish as payment, worst specimens first |
| `FishRequirement.java` | An ask: count, rarity, grade, species, origin, coherence — and how to describe it |
| `ShopStorage.java` | Migration only — returns fish left in the removed store/retrieve counter. See Dead or dormant |
| `FishShopAbilityPlugin.java` | Hidden inert migration stub for the removed ability-bar shortcut; load cleanup removes it and its hotbar references from old saves |
| `ShopRowPlugin.java` | One clickable row, plus the shopping-list ring. Reports the ring's hover upwards rather than drawing its own card |
| `ShopTabPlugin.java` | One tab button |
| `ShopHeaderPlugin.java` | Title, credits and the per-rarity fish purse |
| `ShopDetailHeaderPlugin.java` | The detail pane's portrait, name and ladder readout |
| `ShopUi.java` | Shared drawing helpers: fonts, quads, clipping, card placement, and `drawPanel` - the sidebar dressing every panel wears |

### `campaign/fish/items`
Fish in cargo.

| File | What it does |
|---|---|
| `FishItems.java` | Ids and the encode/decode used by all three item kinds, plus `stow` — where a landed fish actually goes — and `unbox`, which expands crates and the pile into independently selectable specimens before a hand-off picker |
| `FishItemPlugin.java` | One landed specimen; right-click stows it into a bundle. Owns the shared coherence-label ladder used by cargo, catch results, ruptures and the terrain readout; its first non-stable rung is `unsettled` |
| `FishBundleItemPlugin.java` | A crate of one species; right-click unpacks, ctrl sweeps the hold into the pile, and the contained species' art is perspective-fitted to the four measured corners of the box label |
| `FishPileItemPlugin.java` | Every fish aboard on one line; right-click restores lone species as loose specimens and repeated species as crates |
| `FishItemRenderer.java` | Icon plus rarity and grade pips over the cargo cell, including a vanilla-blueprint-style four-corner icon pass for box labels |

### `campaign/fish/crab`
`rules.csv` (`catchrelease_crabBarAdd` and the rows under it); only the wares are Java.
Crablobab's four wares. The stall itself is `AddBarEvents` rows in `rules.csv` — no Java; only the

| File | What it does |
|---|---|
| `CrabWares.java` | The four wares, what each costs in credits and crabs, where each one's ownership lives, and which of them has a switch. The explosive head is offered whenever none is currently owned, so detonating its single charge reopens the same Crablobab purchase loop. The conservatory is a vanilla `industry_bp` chip with the industry id in its data — the game's own plugin names it and teaches the faction, so nothing here knows what a blueprint screen looks like |
| `CrabBackdrops.java` | The rolled scene under his arm: one at a time, a rotation down `backdrops.csv` rather than a roll, and the port remembers what he had there — so the same rock offers the same thing twice and the next rock offers the next thing. Priced off rarity; anything already owned drops out of the rotation |

### `campaign/fish/tackle`
Modules bolted to a rig.

| File | What it does |
|---|---|
| `Tackle.java` | The modules, which rig each fits, and the multipliers each applies. `coherenceBonus` is the odd one out: it is taken off the water's aberration at the catch site rather than read during play. `BREACH_COUPLER` is the drone rig's permission to use lamp-cut openings in open space |
| `TackleManager.java` | Two facts: which modules are **owned**, and which is in each rig's slot. `get()` always returns non-null, possibly `NONE`; `consume()` removes a consumable from both facts at once. Gear-dependent modules stay off the shelf and out of rewards until their prerequisite is owned, while an already-owned permanent module remains refittable for save compatibility |

### `campaign/fish/map`
The sector-map fish filter.

| File | What it does |
|---|---|
| `FishMapFilterScript.java` | Inserts the filter button, resizes the map, mounts pane, overlay, planner popup; feeds the route's arrows to the map's own arrow list. The planner borrows the sidebar's slot, so `paneStanding` tracks whether the sidebar is actually on screen - `applied` only records that `activate()` ran, and a failed hand-back would otherwise never reconcile |
| `FishMapPane.java` | The side panel: planner button, search, type chips, species list, the coherence toggle on its floor |
| `FishPresence.java` | What the player is allowed to see, and where |
| `FishPresenceField.java` | Builds merged organic blobs — metaball field, marching triangles, smoothing |
| `FishPresenceOverlay.java` | Draws the blobs through a stencil, striped where they overlap; route badges, the close-route label, and the coherence heat map under it all |
| `CoherenceHeatField.java` | The sector's stability as a gradient - Aberration sampled onto a light-year grid on a per-frame budget. Bare points, not systems, so it needs `openSpaceReading` to answer with the whole index; `ALPHA_CAP` is the layer's single ceiling and `HEAT_EASE` above 1 keeps the bottom of the range faint; bounds are the sector rectangle exactly, because past it `getAbyssalDepth` measures how far off the map you are rather than the water |
| `FishSystemPane.java` | The system view's sidebar: the viewed system's catch as holder cells, same map hand-over as the big pane |
| `FishHolderPlugin.java` | One round fish holder - rarity ring, art/mark/question - shared by every screen that lines fish up in circles |
| `FishIcons.java` | A species' face by knowledge: the art once landed, its rimmed black silhouette while only surveyed. The rim **is** the artwork (a multiply cannot lighten), so it is withheld until the black copy covering it is nearly opaque — see `RIM_COVER_FLOOR` |
| `FishRoute.java` | The saved route: ordered stops in the save, until closed by hand |
| `FishRoutePlanner.java` | Suggestions from every `FishAsker` in the log plus the shopping list, broad asks expanded to whatever could pay them; cover + exact ordering, stability- and slipstream-aware |
| `FishRoutePopup.java` | The planner in the sidebar's slot, built from the sidebar's own parts: search, chips, pick up to five, plot |
| `PaneWidgets.java` | The panes' shared widgets - type chip, text button, ghost-text tending - one face for sidebar and planner |
| `FishTooltips.java` | The one species tooltip every fish icon answers a hover with |
| `FishIntelPlanetPanel.java` | The intel Planets view's fish panel, beside the planet card |
| `FishType.java` | Filter categories with colour and icon |
| `CoreUiCrawler.java` | Reflection into the obfuscated core UI to find the filter row |

### `campaign/fish/codex`
Codex pages for species.

| File | What it does |
|---|---|
| `FishCodex.java` | Installs the category and per-species entries; opens the codex on a species |
| `FishCodexEntry.java` | One page: description, catch data, record, location, art, and the jump to the sector map |

### `campaign/fish/coherence`
The low-coherence overlay: the screen warps purple at its edges while a rig runs, an open pond
is close, or one of the trade's boats is - whichever of the three pulls hardest, weighted by
distance for the last two.

| File | What it does |
|---|---|
| `CoherenceOverlayScript.java` | The rules: which of the three sources is loudest, how hard, the ease in and out, the whisper loop. Drawing is `rendering/plugins/CoherenceOverlayRenderer` |
| `CoherenceTerrain.java` | The terrain-bar line. Invisible terrain covering a whole location whose `containsEntity` is "is the overlay up" rather than a distance — IndEvo's trick, so nothing has to be moved under the fleet |

### `campaign/fish/constants` · `campaign/fish/intel`

| File | What it does |
|---|---|
| `FishConstants.java` | Every magic number for minigame, result cards, celebration, treasure, codex |
| `FishMapIntel.java` | **Dead.** A husk kept so old saves load and delete it themselves |

### `campaign/ponds`
The pond, as terrain.

| File | What it does |
|---|---|
| `terrain/MaskedFishingPondTerrainPlugin.java` | The live pond: activation, motes, depth field, hole rendering, temporary and visual-only ponds |
| `listener/PondCreator.java` | Finds clear spots away from planets, ponds, nebulae and rings |
| `listener/OnJumpPondSpawner.java` | Triggers pond creation when the player jumps into a system |
| `scripts/PondCameraFocusScript.java` | Eases the camera onto an open pond and closes it once left behind |
| `renderer/PondDepthField.java` | Motes of light spiralling at depth inside the pond |
| `renderer/PondHoleRenderer.java` | The stencil-and-gradient hole look. Current default |
| `renderer/RippleData.java` | One ripple emitter, spawning ring renderers into LunaLib's list |
| `renderer/UnstableFabricRippleTerrainRenderer.java` | Extra randomised ripples around the main one |
| `constants/PondConstants.java` | Placement, camera timing, depth field, hole, opening distortion |
| `entities/StenciledFishingPondEntityPlugin.java` | **Dead.** The old custom-entity pond |

### `campaign/crime`
What harpooning a fleet costs, and what running the breach lamps over somebody's head costs.

| File | What it does |
|---|---|
| `LampOffence.java` | Where the lamps may be run and what a stop costs, plus the burn counter — a stop settles the burn it was about, so putting the lamps out and lighting them again is a fresh offence rather than a continuation of the settled one. System-bound like the transponder law — a flag polices systems it holds something in; inside those, everybody objects near an inhabited world and the Church and the Path object anywhere. Four rungs: warning, fine, inspection, guns, the last only reached by doing it again inside a month |
| `LampPatrolResponse.java` | Patrols coming over about the lit lamps. Nothing is booked and nothing is dispatched — the sweep asks only whether they are burning, whether this is somebody's space, and whether anybody is looking, so putting them out before the patrol arrives ends it |
| `HarpoonOffence.java` | Incident history, outstanding debts, evasions, rep loss, and the escalation ladders. Armed crews turn on you at the second hit; unarmed ones are split by strength — a crew that is plainly outmatched (`isOutmatched`, vanilla's own 1.25× engage threshold) and has somebody to tell (`isCivilised`) runs on the *first* hole with an emergency burn and fetches a patrol, and everyone else works ignore → run you down for the bill → run and tell. `isPlayerIdentified()` is the transponder, and is what decides whether anybody can name you |
| `HarpoonPatrolResponse.java` | Sends one patrol at a time after the player. Any faction **not hostile to the offended one** will take it — the infraction belongs to the space, not to a flag |
| `HarpoonWitness.java` | An unarmed crew flying to a patrol to report it. The report lands on arrival, so it can be outrun, jumped away from, or shot down |
| `HarpoonHitman.java` | Mercenaries, when there was nobody to report to. One at a time; guaranteed for a charge fired under a live transponder. Their map name identifies the client faction, while their memory carries the original fleet, location and recovered ROD projectile into the encounter hail |
| `HarpoonedFleetFID.java` | Vanilla's encounter dialog plus one line, and a comm link highlighted only while the crew is actually owed something — `wasHarpooned` stays true for a month and colouring on it alone left a settled bill looking unsettled for weeks |
| `CatchReleaseCampaignPlugin.java` | Hands harpooned fleets that dialog at the narrowest priority - the one custom encounter screen left |

### `abilities`
Three rigs — searchlight, R.O.D., harpoon. Each is `ability/` (the plugin), `constants/`
(tuning), and usually `entities/`; `charges/` is shared between them rather than a rig of its own.

| File | What it does |
|---|---|
| `FishingRigs.java` | One answer to "is any rig running" - lamps lit, swarm out, or a line in the water |
| `charges/BaseChargedSkillshotAbility.java` | Shared charge-pool rearm for the charged abilities; bans them all from hyperspace |
| `rod/ability/PondInteractionAbilityPlugin.java` | Unlocks the nearest pond, then casts and recalls the swarm; away from any pond, a fitted Breach Coupler plus lit breach lamps sends a roaming one instead. Lit lamps disable the stock ROD rather than granting that mode for free. An occupied camp-job rupture locks new ROD deployments while always preserving an existing swarm's recall. An in-flight opener disables another shot until its rupture activates. The button stays active but disabled once only catch carriers are returning, since no drone remains to command |
| `rod/entities/RodMoteEntityPlugin.java` | The mote flown at a pond to open it. The live mote is also the authoritative in-flight opening state, queried by target rupture so the ability cooldown cannot create a duplicate opener |
| `rod/entities/FishingDroneEntityPlugin.java` | One drone: launch, orbit, chase, return — steering, not pathing. Its circle's centre is asked for per frame, so a roaming drone flies the same circle around the fleet. A catch carried home is marked held for the whole return and fade, so the harpoon's shared takeability gate and every other rig reject it |
| `rod/scripts/FishingDroneSwarmScript.java` | Owns one cast: spawns drones, assigns chasers, handles recall. Four hooks — search centre, search area, what counts as fish, when it is over — are what the roaming variant replaces. Reachability is asked for the whole of a chase, so a drone breaks off whatever goes dark or dives under it |
| `rod/scripts/RoamingDroneSwarmScript.java` | The pondless swarm: with a Breach Coupler fitted, a screen flying with the fleet goes after buried motes the breach lamps have **lit outright** and unearths them on contact. A dent is not a hole — taking one is the harpoon's Fathom Head and nothing else. Losing either the lamp opening or the coupler recalls the screen |
| `rod/rendering/FishingRingRenderer.java` | The dashed ring showing the fishing radius |
| `rod/rendering/FishingDroneDebugRenderer.java` | Dev only: ring and per-drone spokes |
| `rod/animation/Flash.java` | Short additive glow burst |
| `rod/constants/RodConstants.java` | Drone speed, steering, orbit, return acceleration, ring look |
| `harpoon/ability/HarpoonAbilityPlugin.java` | Fires the line; aim assist; press again to cut while hauling |
| `harpoon/entities/HarpoonEntityPlugin.java` | The whole cast: flight, strike, hauling, catch, return, rope rendering; an NPC-owned line skips the minigame and always lands |
| `harpoon/constants/HarpoonConstants.java` | Flight, catch radius, haul physics, rope spring and wave params |
| `searchlight/ability/SearchlightAbilityPlugin.java` | The breach lamps: spools them up, beam slow, detectability penalty, yields to open ponds. Three questions about a buried mote, and they are **not** interchangeable — `isLit` (a beam is on it, so it can be taken), `isDetected` (it is showing as a dent at all, including the passive reach, so it can be seen), `isBreaching` (the lamps are lit at all) |
| `searchlight/scripts/Searchlight.java` | One beam: sweep, lock-on, picks its face, drives distortion and ripples |
| `searchlight/rendering/SearchlightGlowRenderer.java` | The circular beam, purple over its window |
| `searchlight/rendering/SearchlightFanRenderer.java` | The wedge beam, for the fan-beam tackle |
| `searchlight/rendering/SearchlightBreachRenderer.java` | The window under a spot: world-anchored hyperspace under the beam's falloff, with parallax |
| `searchlight/rendering/SearchlightFanBreachRenderer.java` | The same window cut as the fan's wedge, falloff for falloff |
| `searchlight/rendering/SearchlightBurnRenderer.java` | The old pond-style burn look — sidelined, nothing uses it |
| `searchlight/rendering/SearchlightImpressionRenderer.java` | Dents for all beams together: passive bruises near a light, and a beam over a mote reveals its pond self |

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

### `rendering`
Shader and GL machinery.

| File | What it does |
|---|---|
| `distortion/CampaignDistortionRenderer.java` | GraphicsLib's distortion pass, rebuilt to run on the campaign map |
| `plugins/MaskedWarpedSpriteRenderer.java` | Fill + alpha mask + optional swirl and well radial warps |
| `plugins/CoherenceOverlayRenderer.java` | Full-screen post-process: the screen redrawn warped and leaned purple, at a level set from outside. Both warp and tint sit under a radial mask that leaves the middle of the screen clear |
| `plugins/MaskGlowRenderer.java` | Additive glow shaped by a sprite's alpha |
| `plugins/NoiseMappedCircularRingRenderer.java` | Ring shaped and animated by scrolling noise |
| `plugins/WarpGrid.java` | The animated vertex grid the warp renderers share; borders pinned |
| `plugins/WarpedRectRenderer.java` | A sprite warped per-vertex by a grid, no shader |
| `renderers/FleetMarkerRenderer.java` | A small icon off a fleet's corner, in vanilla's own geometry and whoever's colour — the quest offer's cyan `!` |
| `renderers/RippleRingRenderer.java` | One growing, fading ring, pinned to one location |
| `renderers/SimpleRippleDataRunner.java` | Advances and expires a `RippleData` |
| `helper/Stencil.java` | Depth-mask sprite masking. Stencil-buffer variants are deprecated |
| `helper/ParallaxUtil.java` | Background drift and camera-relative parallax UV offsets |
| `helper/Disc.java` | Filled or outlined circle |
| `helper/RoundedBorder.java` | Rounded-rectangle outline |

### `memory` · `helper` · `reflection` · `testing`

| File | What it does |
|---|---|
| `memory/upgrades/UpgradeManager.java` | Save-persisted levels. `getValue` is the single read entry point |
| `memory/upgrades/StatIds.java` | The ids joining code to `UpgradeData.csv` |
| `memory/upgrades/UpgradeStat.java` | One row: base, FLAT/MULT per level, category, current value |
| `memory/charges/ChargeManager.java` | Float charge pools that regenerate continuously |
| `memory/TransientMemory.java` | Session-only cache. Keys must start with `$`, never persisted |
| `memory/RandomMemoryHelper.java` | A per-star-system `Random`, stored in that system's memory |
| `helper/loading/FishSpecLoader.java` | `fish.csv` → `FishSpec`, cached |
| `helper/loading/UpgradeStatLoader.java` | `UpgradeData.csv` → `UpgradeStat`, cached |
| `helper/loading/BackdropLoader.java` | `backdrops.csv` → `Backdrop`, cached |
| `helper/loading/SpriteLoader.java` | Sprites by id or path, cached, misses logged once. One object per path is shared by every caller, so it is handed back neutral - native size, white, full alpha, normal blend - and a caller that wants it otherwise says so |
| `helper/math/TrigHelper.java` | Circle intersection and fitting, smoothing, normal distribution |
| `helper/math/Circle.java` · `CircularArc.java` | Point/angle helpers and arc traversal |
| `helper/animation/BaseCircleTrajectoryFollowingParticle.java` | Position and facing along a circular arc between two points |
| `helper/animation/ArchedTrajectoryFollowingMote.java` | A glowing mote drawn along that arc |
| `reflection/ReflectionUtils.java` | Reflection via `MethodHandle`, to dodge the classloader ban |
| `testing/DevShortcut.java` | The Ü key: skips the introduction, grants every rig and the shop, issues charts of every rung. A `CampaignInputListener` on the post-core pass, so anything with a text field has already eaten the key; matched on the typed character rather than a scancode. Dev mode only, read per press |
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
is stereo because it goes through `playUISound`. Getting a new one backwards is not a compile error.

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
The mod ships no `LunaSettings.csv` any more, so it has no page in LunaLib's menu.

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
— and `FishSpec.matches()` is the only thing that tests a species against it. They used to be
several: the spawner tested star type, tags and region; the map, the route planner and the intel
panel tested the region alone, so the map shaded systems under the wrong sun and said so beside a
spawner that would never have offered the fish there. `FishPresence.livesIn()` is what every screen
calls. Habitats are cached for the session because none of their inputs change during a game.

**Blank means "anywhere" on every habitat criterion except the abyss.** `ABYSSAL` has to be named,
because a species that says nothing about where it lives is one somebody could describe, and nothing
describable lives down there — without the exception the deepest water in the game offered the same
roach as a core world. It is the one asymmetry in the table and it is deliberate.

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
`$menuState = <state> 0` and `FireAll PopulateOptions`. This map used to claim the engine fires it
after option selections; it does not, and the fisherman's whole menu tree was broken by rows
written to that claim.

**The Church and the Path are against the water, not against fish.** A rupture is a hole opened between here and hyperspace and everything pulled through it came from the wrong side of that hole, so the whole trade is people making a living off a wound in creation. Neither flag therefore produces a fisher, a buyer, a broker, or anybody in a bar with a favour to ask - no bar job at a Church or Path port, no trawler working a system either of them runs, no fishing offer on one of their hulls. What they do produce is everything in `campaign/crime`, plus the cells in `jobs/camp` that sit on ruptures so nobody can work them, and that is the only shape a Luddic interaction with this mod takes. `campaign/fish/FishingTaboo.java` is the one list; nothing hardcodes the two faction ids anywhere else.

**A hostile fleet can still be talked to, and the hail is what makes it possible.** The camp job needs a conversation with a pirate pack that is hostile by default, which no memory flag softens. Vanilla's answer is in the Galatia arc's gate-sitting pirates: `HailPlayer` on `BeginFleetEncounter` opens the link regardless of the relationship, and `MakeOtherFleetGoAway` is what ends it when they agree to leave. Nothing about the fleet is made friendly — the conversation is a thing the player gets to have, not a promise about how it ends.

**The camp job polls rather than being told.** Being killed, bought off, talked off and quietly wandering away do not share a hook, and only two of the four happen inside a conversation. So `CampedSpotJob.advanceImpl` asks one question on the mission's own tick instead of four rules rows each reporting a different way.

**Hidden until discovered, and it is one rule rather than a judgement per object.** Everything that sits inside a system waits to be surveyed; exactly two things are exempt, and neither is an object you find - a star (so a black hole is routable from the first day) and a slipstream (visible the moment it runs). `Mark.isFound` is the single test — it asks the source's own `survey` flag and then the entity, live, so surveying something changes the planner's mind at once rather than when the index next rebuilds. The mod's own placed entities follow it too: the tutorial wreck and the castaway beacon are `setDiscoverable(true)`, having previously argued themselves out of it.

Two things deliberately stay outside the rule because they are not survey finds at all: the Fisherman's map icon rides a fleet and inherits that fleet's visibility, and motes have their own reveal mechanic - the breach lamps - which is the whole point of the lamps.

**Nothing that thins the fabric moves, so nothing is measured twice.** Every reading used to walk the sector six times — `SectorAPI.getEntitiesWithTag` iterates hyperspace and every system per call — and readings are taken from tooltips and terrain readouts that ask every frame, and by the route planner once per candidate system. A gate does not move. Neither does a hypershunt, a black hole or a foreign engine; slipstreams are the one exception and they are hyperspace terrain that changes on the scale of a cycle. So `Aberration` crawls once into a flat list of marks, invalidated only when the day rolls over or the gates switch on, and every reading after that is a loop over floats already in memory.

**Three fill points, and the one per-frame figure is deliberately a different question.** The index and the per-system readings fill when the player arrives somewhere (that one system) and when the sector map opens (all of them at once, paused, off a single crawl); anything else that asks fills on demand. What cannot come from that cache is where in a system the player is standing, because at sector scale every object in a system shares the system's coordinates — so `localPull` measures world-unit distance to that location's own entities, from `LocationAPI.getEntitiesWithTag`, never the sector's. It is what makes the overlay brighten as the fleet crosses a system towards whatever is causing the reading. **It lifts the system's reading and never scales it** (`ABERRATION_LOCAL_LIFT`): zero means "nothing here to stand near", which is the ordinary case — most systems are thin on account of something outside them — and scaling by it took the system's own reading out of the overlay entirely for exactly one commit.

**The abyss is read uncapped, because the cap is what made it a slab.** `Misc.getAbyssalDepth` capped is 1 from the far corner across most of the wedge and 0 within ten thousand units of its edge — one flat maximum with a wall around it, which is what every abyssal catch and the whole map layer inherited. The uncapped figure runs about 20 in the corner down to 1 at the line where the cap starts holding, and `ABERRATION_ABYSS_SPAN` divides it back into a gradient. It is a *reading* knob, not a rendering one: map, catch and terrain readout all move together, which is the point. `ABERRATION_ABYSS_SPAN = 1` restores the old cliff exactly.

**A point belonging to no system reads everything, and that is not a hyperspace nicety.** `CoherenceHeatField` samples a grid of bare points across the sector to paint the map, so `openSpaceReading` is the call that decides what the heat map can show — answering it with the abyss and the streams alone (which it did briefly) leaves a picture of the two sources that happen to be terrain. Open space is the ordinary case minus the one rule that needs a system: a source standing *in* the place being read counts at full weight. `Mark` carries its reach squared in world units so a point can be rejected without a square root, which is the operation that actually runs at that sample count.

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
player is doing right now that anybody in line of sight can see. So `LampPatrolResponse` books
nothing and remembers nothing — it re-asks the three questions every tick and calls the stop off the
moment any of them turns false, which is why turning the lamps out makes the whole thing go away.
The two things that do persist are the sector-wide rung count and a 30-day marker on each crew that
has already had the conversation, so one system cannot produce a queue of identical stops.

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

**A hand-drawn control has no tooltip, and cannot grow one where it lives.** The shopping-list ring
is painted by `ShopRowPlugin` inside the list's scissor box, so a card drawn there would be sliced
off at the edge of the list — and it is not a `ButtonAPI`, so there is nothing to hang a stock
tooltip on. The row reports the hover to the pane, which draws the card from its own
`CustomUIPanelPlugin.render()`; that runs *after* the panel's children, which is what puts it on top
of everything. The card's `DrawableString`s are rebuilt only when the hovered ring or its marked
state changes — each one is a display list.

**A fleet quest never spawns a fleet, and until it is accepted it never touches one either.** The
offer is two memory keys and a `FleetQuestMarker` hung on a civilian hull already in the player's
system — no rename, no orders, no `$missionImportant`. Turning one down costs nothing because there
is nothing to tidy away.

**Accepting supplants the hull.** `FleetQuest.supplant()` builds a copy (fresh members off the same
variants, so nothing is owned by two fleets at once; only the source market carried over from the
old memory) and despawns the original *with a report*, so whatever was running it — a trade route, a
scavenger sweep — hears that it is gone rather than waiting forever on a fleet parked in a system
for two months. What is left answers to nobody but the job, which is what makes it safe to pin. When
the job ends or its clock runs out, `release()` sends it home to despawn.

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
is fresh hulls under a fresh officer with a fresh name; he deliberately is not, because the same face
turning up four jumps and eight months later is the point of him. Standing boat or visiting one, the
encounter shows the same portrait and says nothing about it. The flag that separates the two is
`$catchrelease_fisherman_visiting`, and it is about the *schedule* — nothing about who the player is
talking to hangs off it. `FishermanIdentity` keeps the `PersonAPI` in sector
memory and hands the same object back at every spawn, and the encounter shows the portrait rather
than a fleet readout — a thing a hull list cannot say. What changes is how well he is holding:
`getDrift()` reads the system's own instability through `Aberration.baseAt` (the deterministic
figure, not a specimen's jittered one), and the boat's name, the greeting, and its map-tooltip line all
come apart by degrees as it climbs. Letters are taken out by position, so the same system spells him
wrong the same way every time — the degradation is a fact about the water, not an animation.

**All dialogue is in the sheet — all of it.** The Fisherman's whole conversation, the introduction's
six lessons, the hulk, the castaway and the bar rating are rows in `rules.csv`. Java is reached only
through `CatchReleaseCMD <verb>`: in a row's *conditions* `tokens` writes the dozen booleans the rows
branch on and always returns true, and in a row's *script* a verb does the thing and returns whether
it worked. The panels — shop, chart counter, cargo picker — stay Java, because a shelf of cards is
machinery and there is nothing for a sheet to say about it.

**A work offer rolls before it speaks.** `catchrelease_workOffer` only rolls the pending chart request
and fires `CatchReleaseWorkOffer`; `catchrelease_workOfferText` then reads the saved fish, place and
payment tokens. Keeping the supplied offer text on that second row means the range has been selected
before token replacement, rather than printing the fallback token names from before the roll. Its
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
boat works on its own. All of them call `FishingIntro.point()`, which is idempotent.

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
do nothing.

---

## Dead or dormant

| What | State |
|---|---|
| `campaign/ponds/entities/StenciledFishingPondEntityPlugin` | Dead. The pond is terrain now |
| `campaign/fish/intel/FishMapIntel` | Dead husk, kept so old saves can delete it. Removable once no save predates the map filter |
| `campaign/fish/shop/ShopStorage` | The store/retrieve/sell counter is gone. Kept only to hand back fish a save is still holding in it, once, on the next shop open |
| `testing/DevShortcut` | Registered from `ModPlugin` as a transient listener; does nothing unless dev mode is on |
| `testing/TestStencilRenderer` | Commented out of `ModPlugin` |
| The pond's shader swirl | Dormant behind `PondConstants.POND_HOLE_LOOK`, which currently selects the stencil hole renderer |
