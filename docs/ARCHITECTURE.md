# Architecture

Technical routing for the current implementation. Java paths below are relative to `jars/src/catchrelease/`; data paths are repository-relative. Search the named owner before adding another implementation.

| Reference | Scope |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | Workflow, build gate and document upkeep |
| [DIALOGUE.md](DIALOGUE.md) | All player-facing text, rules or Java: workflow, shared text presentation and dialogue flow; prose constraints in LORE.md |
| [LORE.md](LORE.md) | Setting facts, knowledge limits, character voices and source-labelled prose examples |
| [Needle quest concept](NEEDLE_QUEST_CONCEPT.md#overview) | Deferred narrative plan, with a short theme, philosophy and content overview; not current canon or implementation |
| [RULES.md](RULES.md) | Rules syntax, execution and project routing contracts |
| [RULES_WRITING.md](RULES_WRITING.md) | Writing process and structure of rules content: plain chains, `FireAll` menus, `FireBest` picks, entry points, state, text, options and exits |
| [RULES_AUTHORING.md](RULES_AUTHORING.md) | Using and debugging commands, memory and text replacements, including Java integration; vanilla dictionaries and source corrections |
| [UI.md](UI.md) | Java custom panels, widgets, renderers, sprites, tooltips, layout and input; shared text guidelines in DIALOGUE.md |
| [Distress README](../jars/src/catchrelease/distress/README.md) | Reusable distress integration |
| [Skillshot README](../jars/src/catchrelease/skillshot/README.md) | Reusable targeting integration |

## Start here

| Change / symptom | Route |
|---|---|
| Spawn, habitat, known range, no-data | `FishSpecLoader -> FishSpec/FishHabitat -> FishRanges -> FishPresence -> map/Codex` |
| Catch eligibility, crates, hand-in | `FishCatch -> FishItems -> FishRequirement/FishCurrency -> FishHandoffPicker` |
| Quest generation, payout, deadline | `FishJobAsks/FleetQuestType -> DemandScore -> QuestRewards/QuestDuration -> FishJob` |
| Camp, chart or tutorial proof | `CampedSpotJob/FishermanQuest/FishingIntro -> QuestPond/FishRequirement -> FishItems.stow` |
| Fish shop and schematics | `FishShopDialog -> ShopEntry -> ShopPricing/ShopSchematics -> TackleManager/UpgradeManager` |
| Fisherman fleet, identity, shelf | `CoreFisherSpawner/FishermanSpawner -> behavior -> FishermanIdentity/FishermanShelf` |
| Rules menu, panel return, highlights | `rules.csv -> CatchReleaseCMD`; [project routing](RULES.md#project-routing), [option wording and quotation](LORE.md#dialogue-and-player-options), [shared text presentation](DIALOGUE.md#shared-text-presentation) and [Java panel returns](UI.md#custom-dialog-hosts) |
| Command arguments, mission calls, memory lifetime, missing text replacements | [Rules implementation guide](RULES_AUTHORING.md) and its dictionaries, including for Java-only fixes; [project routing](RULES.md#project-routing) for local contracts |
| Harpoon, drones, Breach Lights | `abilities/*/ability -> entities/scripts -> renderers`; shared targeting in `skillshot/` |
| Camera, pond opening | `PondInteractionAbilityPlugin -> RodMoteEntityPlugin -> MaskedFishingPondTerrainPlugin -> PondCameraFocusScript` |
| Ponds missing from a charted system | `OnJumpPondSpawner -> PondCreator`; fills charted systems on load and before Map/Intel draws via `CoreUITabListener`. `CurrentLocationChangedListener` also fills the entered system, including non-jump travel. No periodic scan. Uses vanilla `isEnteredByPlayer()`, which includes acquired maps and known core systems; ponds remain ordinary map-visible terrain. |
| Charge count / regeneration | `BaseChargedSkillshotAbility -> ChargeManager -> ability callback` |
| Fleet offence / pursuit | `LampOffence/HarpoonOffence -> patrol response -> CatchReleaseCampaignPlugin/HarpoonedFleetFID` |
| Legendary reveal / cleanup | `LegendaryChases -> LegendaryHaunt/LonglinerDecoy -> HauntModule/LegendaryShields` |
| Map/Codex/intel handoff | `FishIntelMapButton/FishCodex -> FishMapFilterScript -> FishMapPane/FishPresence` |
| Stale campaign effect | Owner location/ability validity -> cleanup; shared renderer registration in `rendering/` |
| Shared UI widgets / fish icons | `ui/PaneWidgets`, `ShopUi`, `ListRow`, `FishIcons`; [Java UI contracts](UI.md). Minigame rendering is separate. |
| Map / planner list rebuilds | `FishMapPane` and `FishRoutePopup` replace the list's owning custom panel; [lifetime contract](UI.md#rebuilding-lists) |
| Campaign distortion / masking | `rendering/distortion/CampaignDistortionRenderer`, `rendering/helper/Stencil`, `rendering/plugins/*` |
| Aquarium | `BreachConservatory -> AquariumTransfers/Backdrops -> AquariumTankScript/Panel`; [backdrop dimensions](UI.md#portraits-and-sprites) |

## Registration and lifecycle

| Registry / hook | Owners |
|---|---|
| `ModPlugin.onCodexDataGenerated()` | `FishCodex`, only while Codex data is generated |
| `ModPlugin.onGameLoad()` | Idempotent script/listener registration and current-state cleanup; order below |
| `ModPlugin.beforeGameSave()` | Reset transient skillshot targeting; remove False Dawn's owned coronal flares before serialization |
| `data/campaign/fish.csv` | Species; `FishSpecLoader` |
| `data/campaign/abilities.csv` | catchrelease_searchlights, catchrelease_rod, catchrelease_harpoon |
| `data/config/settings.json` | `catchrelease.dialogue.rules` command package and sprites; black-hole warp settings belong to the deprecated test below |
| `data/config/sounds.json` | Sound registry; callers in abilities and `FishConstants` |
| `data/config/LunaSettings.csv` | Charge-ready sound policy, camera snap, returning-player tutorial skip |
| `data/campaign/bar_events.csv` | 11 ordinary FishJob subclasses + 3 camp jobs; Crablobab and the tutorial spacer use AddBarEvents rules |
| `data/campaign/distress_calls.csv` | Merged, namespaced specs; # IDs disabled; `CatchReleaseDistressProvider` |
| `data/campaign/rules.csv` | Dialogue and type-selected fleet quest/intel text; Java supplies mechanics/state |
| `data/world/factions/default_ranks.json` | Contact roles |
| `data/campaign/terrain.json` | Pond/coherence terrain; ponds are not custom entities |
| `data/config/custom_entities.json` | Motes, drones, harpoons, legendary props and map proxies |
| `data/campaign/special_items.csv` | Fish cargo |
| `data/campaign/industries.csv` | Breach Conservatory |
| `data/campaign/backdrops.csv` | Aquarium scenes and ownership source |
| `data/config/UpgradeData.csv` -> `memory/upgrades/` | Current stat definitions, saved levels and runtime values |

Load order in `ModPlugin`: pond listeners -> buried motes -> charges -> harpooned FID selector -> offence responses -> local fleet offers -> expedition/prospecting routes -> visiting Fishermen -> standing Fishermen -> chart upkeep -> tutorial/wreck/bar referral/interception -> colony options -> aquarium -> coherence cache -> monthly ranges (including initial assessment) -> legendary cleanup -> Imposter cleanup -> distress provider/framework -> skillshot -> map filter -> intel planet panel -> coherence overlay -> stale pond claims -> dev shortcut.

IntelliJ classes: `out/production/catchrelease`; artifact: `jars/catchrelease.jar`. Keep compiler output outside `jars/`. Build procedure: [CLAUDE.md](../CLAUDE.md#building).

Optional Console Commands entry points: `AllFish`, `AddFish`, `SpawnFish`, `HauntStatus`, `SpawnFisherman`, `SpawnFleetQuest`, `SpawnDistressCall`. They live in `commands/`, are the only classes that need `lw_Console.jar`, and nothing else references them; a build without the jar leaves them out ([Building](../CLAUDE.md#building)).

Authoring tools live in `jars/src/catchrelease/tools/`, in the normal `catchrelease`
module. They run as standalone Java 17 programs, without starting Starsector.
Use the `Fish Difficulty Tuner` or `Fish Facing Picker` run configuration, or run
either `main` with the `catchrelease` classpath and mod root as working directory
(or first argument). Optional second argument: starting fish ID.

| Entry point | Use / owners |
|---|---|
| `tools/FishFacingPicker.java` | Click a head to save `spriteDirection` and advance; Back revisits, Skip leaves the row unchanged. Image centre is the origin; right/up/left/down are 0/90/180/270 degrees. |
| `tools/FishDifficultyTuner.java` | Live fish tuning with three simulated angler levels, the recorded Player profile or manual hold/release. `FishTuningSheet` holds unsaved values; `FishTuningSession` owns attempts; `SimulatedAngler` consumes screen observations and hands Player to `PlayerModel`; `FishPreview` draws the track for Live tuning and Record play. `help`/`refreshHelp` route control help and preview readouts to the bottom bar. Record play: `FishRecordingPanel` UI, `FishRecordingSession` fish order and phases, `FishRecording` files, reading and replay. `FishAnglerCalibration` fits `PlayerModel.RECORDED` to `fish-recordings/`. [Tool contract](UI.md#fish-difficulty-tuner). |

The tools' `FishCsv` is their shared cell-preserving CSV reader/writer. It leaves other
text and line endings intact, checks for external edits, and replaces the file
through a temporary file. The first changed save creates `fish.csv.facing-*.bak`
or `fish.csv.tuning-*.bak` beside the CSV. External edits require reopening the
tool. The tuner writes only on explicit Save; the facing picker saves each click.
Facing imports match species by `id` and copy only `spriteDirection`; a backup may
have older balance values or descriptions. Commit the edited CSV, not the backup.

`tools/FishingSimulation` owns the simulator's catch model. It reads the existing
`FishConstants`, `FishMotion` and `Tackle` directly; do not duplicate those defaults
or modify them from the tool. Keep tools in the normal source tree. Do not edit
or refactor the classes running the in-game minigame to support a tool, and do
not make runtime code depend on `tools/`. The simulator owns its state and loop.
When game physics changes, compare and update the tool model using
`tools/FishingParityChecks`. Both it and `tools/FishTunerChecks` have IDE run
configurations in the normal module; run them from the mod root in a separate
JVM. Parity checks use the real minigame, fish, rumors and LazyLib RNG at base-kit values.
The check's nested `Environment` supplies API proxies and private campaign data;
it refuses to run with existing game globals and clears its globals on exit.
`CatchOnlyMinigame` overrides only treasure generation, leaving catch physics
unchanged. No duplicate game classes, source rewriting or separate test tree.
This covers deterministic catch traces, tells, and the jitter and tell formulas, not treasure or the engine.

`tools/MantaHauntChecks` verifies formation spacing, stationary slots during swaps,
blackout duration/interval, held-target safety and cleanup. `tools/MantaMinigameChecks`
compares decoy motion/tells against the real model at 30/60/144 Hz and checks
real-only scoring, treasure isolation and restart/end behavior. These are standalone
checks with API proxies; they do not test OpenGL output or engine callback ordering.
`tools/LegendaryEscapeChecks` exercises failure dispatch, four-turn shell-game
bursts at 30/60/144 Hz, repeated-loss decoy caps, emergency slipstream duration
and cleanup, immediate manta swaps, and the Imposter's ordinary-hit recovery shield.
It also checks corona-safe movement segments, invalid-position recovery, overlapping
and companion stars, long-frame dashes, buried movement, formation clearance, and
the False Dawn/ordinary-fish exclusions.

The tuner's Balance results tab uses `tools/FishBalance` for immutable run
snapshots, per-attempt telemetry and bounded parallel batches; its `game` and
`visibleFish` also build the simulation and drawn position for Record play. `FishBalancePanel`
owns session results, exact-input cache, filters and stale-result checks;
`FishBalanceChart` draws catch-rate and successful-time plots. Workers never
read mutable sheet rows or game globals. `FishBalanceChecks` checks batch/preview
agreement and deterministic parallel results.
`FishBalanceComparison` keeps saved/checkpoint and edited requests separate,
and compares complete results on matching seeds without modifying the sheet.
`FishBalanceAdvice` explains measured coverage, miss duration, early/near-catch
losses and movement-specific tuning options. It never changes fish values.
`FishBalanceExperiments` builds one-field candidate requests and keeps their
results outside the normal per-fish result map. Apply uses the tuner's existing
edit/Undo path and refuses a changed source fish or test setup.
`FishBalanceReferences` groups fixed reference snapshots by movement and loads
named test setups into the tuner. `FishBalanceNotebook` stores references,
notes and equipment presets in the ignored mod-root `.fish-balancing.properties`.
References store fish values by `FishTuningSheet.Field` position, so new fields
go last; an older reference loads the missing fields at their defaults.
It validates input, checks external edits, backs up an existing notebook before
its first replacement, and writes through a temporary file. It never writes the
fish CSV. Run `Fish Balance Checks` from IntelliJ for the workbench checks.

`tools/rules/RulesCheck` checks `data/campaign/rules.csv` against the engine's loader
and reads `docs/rules-reference/vanilla-rules-index.txt`. It touches no game class that
needs `Global`. Run it with the `Rules Check` run configuration; usage, checks and
limits are in [RULES.md](RULES.md#rules-check-tool).

`tools/rules/DialogueCheck` compares the paragraph shape of the mod's `rules.csv` Text with
`docs/rules-reference/vanilla-pacing-baseline.txt`, reports stage-note fragments and checks that
the rows `docs/LORE.md` quotes still match (`LoreExcerpts`). Run it with the `Dialogue Check` run
configuration; usage, checks, bands and exemptions are in [DIALOGUE.md](DIALOGUE.md#dialogue-check).

`AddFish <fishId> [quality] [coherence]` adds one bundled specimen. Exact IDs and ID-only autocomplete; optional finite values in `[0,1]`. Quality interpolates both length and weight directly; coherence is stored as `1 - coherence`. Omitted quality uses the normal size roll; omitted coherence uses the species' aberration midpoint. `SpawnFish` retains the shared name/fuzzy matcher and its separate suggestions.

## Data identity

| Stable ID | Display name |
|---|---|
| `mackerel` | Moiré Mackerel |
| `cutout` | Volley Dolphin |
| `hull_grazer` | Hull Grazer |
| `longliner` | The Imposter |
| `miscount` | Relic Crab |

Display names and internal IDs are separate. `LonglinerDecoy` and Longliner-named memory, sound and option keys still identify The Imposter. Asset status is recorded beside each species row (`placeholder art`). The `desc` column loads into `FishSpec.desc`, shared by `FishItemTooltips` and `FishCodexEntry`; handling advice and hazards in that text do not add campaign mechanics.

`RatingBarEvent` and rating-named rule IDs, commands and memory keys still identify the tutorial crew referrals. Player-facing job descriptions do not rename these bindings or saved keys.

`RatingBarEvent.VisitCounter.reportPlayerOpenedMarket()` also prepares the same spacer's post-graduation legendary report. `REPORT_KEY` saves one unshared sighting across bar/market reopening; invalid sightings reroll only on a market-open callback. Both sources share `FishRumors`' unlock and 30-day publication cooldown. `hasRatingReport` is a read-only menu condition; `hearRatingReport` publishes once and snapshots dialogue tokens before `CatchReleaseRatingLegendaryReport`. Each species has a three-page rules chain; continuation options neither reroll nor republish the report. The final page displays the intel receipt and `QuestDialogMap` preview, then opens `CatchReleaseRatingLegendaryQuestions`. Location and shared gear answers both return to that menu; the location answer restores the map, while the gear answer and every bar exit remove it. Every story page has a bar exit. These are leads, not accepted quests; tutorial referral routes remain separate.

## Source owners

Folders contain related renderers, constants, widgets and helpers; use `rg --files jars/src/catchrelease/<folder>` for their complete inventory. The entries below identify state and integration owners, not every class.

### `campaign/fish` and `campaign/fish/data`

| File | Owner / connection |
|---|---|
| `FishingTaboo.java` | Central list of factions that reject fishing: the Church and the Path. |
| `FishSpec.java` | Species row, stable save ID and display fields, minigame tuning, the campaign pace and wander factors `FishSpecLoader` derives from it, value/size, habitat and implements; display renames do not rename saved IDs. |
| `FishLocationSummary.java` | Shared habitat prose for range-data and caught-fish hovers (`FishTooltips`) and Codex range panels. Always names the catch sources, including both breach lights and ruptures for blank or mixed `reachedBy`. |
| `FishCatch.java` | One specimen: size, weight, aberration, region, source rupture, timestamp, method, and optional chart-request provenance. |
| `FishLog.java` | Persistent per-species discovery and record data. |
| `Aberration.java` | Caches ordinary aberration from the strongest destabilizer minus the strongest colony field, then applies temporary system rumors. `naturalAt` bypasses rumors for target selection. |
| `FishRanges.java` | Authoritative current range test. |

### `campaign/fish/jobs`

| File | Owner / connection |
|---|---|
| `FishJob.java` | Mission base -> FishRequirement/FishCurrency -> picker -> QuestRewards -> intel. Shared acceptance-time restriction; progress compares capped displayed counts; selected specimens reach rewards before the next round rolls. |
| `ChefJob.java` | Three ingredient types and a saved dish; supplies `$catchreleaseDish` to the chef's rules dialogue. |
| `TuberJob.java` | Two paid deliveries: a Fine Uncommon/Rare specimen, then a low-coherence catch. Rules disclose both before acceptance; follow-up and camera questions return to the offer without changing mission state. |
| `DemandScore.java` | Scores actual requirements (unmodified Common = 10), including diminishing extra specimens and cheapest anyOf branch; supplies ambition/count helpers and EASY/MEDIUM/HARD/SEVERE reward tiers. |
| `QuestRewards.java` | Shared reward budget: score × 600 credits × 0.75–1.35; fixed rewards, tier-gated extras, remaining guaranteed credits, saved hand-in value multiplier. Later cash stages guarantee ≥20% total and ≥25% base-credit growth, each ≥2,000; multiplier cannot decrease. |
| `QuestDuration.java` | Satisfiability gate and deadline: nearest valid range + round-trip fleet travel + work, rounded to 30/60/90/120/180 days or unlimited; +30 for required Rare/Epic and +30 for post-acceptance catch. |
| `FishHandoffPicker.java` | Validates non-overlapping specimen assignments; autoSelect takes the minimum valid set, worst first. Invalid confirmation reopens next frame after modal release; preserves container ID when repacking. |
| `FishReward.java` | Reward values, grants and actual grant results; quest credits use the exact selected specimens. Known range grants convert to stored credit fallback. Equipment rewards unlock schematics, not purchased tiers or modules. |
| `FishRewardRoller.java` | Tier/ownership/active-job exclusions; distinct chart reservations; 3–10× fish-value multiplier (10× = 0.5%). Backdrop rolls require conservatory plans. |
| `QuestPond.java` | Claims ponds by a set of job IDs, adds vanilla mission importance, plants identified quest motes, and releases claims and motes. |

`FishJob` supplies complete offer/remaining deadline sentences as
`$catchreleaseDeadline` and `$catchreleaseDeadlineLeft`; unlimited jobs say so
explicitly in dialogue and intel.
Contact reopening and round changes refresh the tokens. Intel contact names use
display post, then display rank, then name alone.

Startup's skeptical acceptance rejoins `catchrelease_jobAccept`; it has no separate
mission outcome. Final replies are added through `JobSpecificOptions` after the
shared reward receipts.

TriTuber's offer uses `CatchReleaseTuberQuestions`; Startup's nested questions use
`CatchReleaseStartupDemandQuestions` and `CatchReleaseStartupOperationsQuestions`.
They rebuild through `FireAll`, with speaker-local read flags lasting for the
offer conversation. Answered topics disappear; forward, accept/decline and back
options remain. Startup's main offer keeps menu-only links to unfinished buyer
and distribution subquestions without repeating the answered introduction.
The buyer branch returns to the main offer before switching to distribution.
Fight Night's offer records its rules and house-cut questions in speaker memory
for the conversation; answered questions disappear while accept and decline remain.

`CompanionJob` owns A Client's Preference; its `catchrelease_client_*` rules and
intel describe the same private commission. The request uses `minLength`; the
bonus requires the upper two-fifths of the species' length range, ignoring weight.
`FishJobAsks` supplies the length floor.

### `campaign/fish/jobs/camp`

| File | Owner / connection |
|---|---|
| `CampedSpotJob.java` | Two independent completion conditions: camper gone + post-acceptance fish from exact rupture. Creates fleet/claim on acceptance; qualifying proof releases mark; loss restores it. Receipt requirements are set at creation and timestamped at acceptance. |
| `CampedSpot.java` | Spawns and holds the camper, forces one warning hail, allows disengagement, removes cut-link without a Continue step, and locks the R.O.D. only while the camp remains. |

### `campaign/fish/jobs/fleet`

| File | Owner / connection |
|---|---|
| `FleetQuest.java` | Fleet-backed FishJob. Saves type/details/alternate client; replaces accepted source fleet with mission-owned members; owns active map proxy, exact provenance, stage handoff and final return/despawn. External construction requires tutorial completion. |
| `FleetQuestMapIcon.java` | Accepted-job proxy, retained in fleet memory while hidden. Existing mission advance restores it at visibility `NONE`; `FleetMapVisibility` hides it on radar contact. Release clears hidden and visible tokens. Asset path matches `settings.json`'s `fleet_quest_map_icon`; its yellow `custom_entities.json` icon colour also supplies the accepted fleet-side marker tint. |
| `FleetQuestSpawner.java` | One eligible local offer/wanted quest; tutorial gate, 7% checks, 45-day cooldown (QUIET_SHIP: 120). Picks among offers that can spawn in the system, weighted (PARLEY_FISH 0.25); a dedicated-giver offer is only in the pick while one of its fleets is present. Uses scavengers except INTERMENT/MUTINY_POT/EXHIBIT trade convoys. Dedicated givers (`fitsDedicatedGiver`): FOLLOWER Hegemony and STATE_DINNER Diktat `trade`/`smallTrader` convoys; CLAIM_ASSAY Tri-Tachyon prospecting fleets or Nexerelin `exerelinMiningFleet`; MANDATE science expeditions; PARLEY_FISH patrols whose source market has `$core_pirateBase` (procgen bases only), outside `theme_core`. Every giver must be non-hostile. Low-coherence premises also gate the test path. |
| `FleetQuestEncounter.java` | Runs one fleet offer, accepts or declines after dialogue closes, resolves distress entities, restores local offer marks after load, and expires old offers. |
| `FleetQuestType.java` | 24 saved mechanical case definitions: demand shapes, reachability backoff, fleet/contact roles, reward exclusions and multipliers, alternate outcomes. No dialogue strings; inspect the case enum when changing a quest. |
| `QuestRouteManager.java` | Base for the two route managers below: a saved `EconomyTickListener` and `RouteFleetSpawner` that adds a capped route on a per-tick chance. RouteManager spawns and despawns the fleets near the player. |
| `ExpeditionRouteManager.java` | Up to 2 independent Galatia Academy science expeditions (`$catchrelease_scienceExpedition`) from `station_galatia_academy` to 2-3 non-Core systems near abyssal hyperspace and back. MANDATE givers. Skips when the Academy or Galatia's hyperspace link is missing. |
| `ProspectingRouteManager.java` | Up to 2 Tri-Tachyon prospecting fleets (`$catchrelease_prospectingFleet`) from a Tri-Tachyon spaceport to an unsettled ore world in a marketless system within 12 LY, held there 30-50 days. CLAIM_ASSAY givers. Adds no routes while Nexerelin is enabled; its Tri-Tachyon mining fleets carry the offer instead. |
| `CatchReleaseDistressProvider.java` | Adapter between the generic distress framework and `FleetQuest`. STRANDED, SCAVENGER_ENGINE, BURN_WARD and FOULED_LINE are the distress calls; each is an emergency aboard the caller. A FOULED_LINE caller is anchored at its claimed rupture (`getFleetAnchor`). |

`SpawnFleetQuest` prints the rejection passed back by `FleetQuestSpawner.spawnForTesting`
and `FleetQuest.startOn`. `FleetQuestType.getSpawnFailure` owns the location checks
used by both diagnostics and normal eligibility. Test failures distinguish missing
dedicated givers from unavailable ones, report coherence and its limit, and retain
source-market, route, fleet-factory and quest-creation failures. The optional
failure callbacks are not saved or retained by fleets. `FleetQuestSpawnCheck`
under `jars/src/catchrelease/tools` checks rejection propagation and the no-diagnostics overload.

`FleetQuest` mirrors the complete deadline sentences into entity memory
(`$catchreleaseFleetDeadline` / `$catchreleaseFleetDeadlineLeft`) and clears them
with its other rule text. The waiting comm route calls `prepareRuleText` before
selecting its Text row, so elapsed time is current for every type. Intel's next
step names the flagship, not a generic catch action.

Dialogue remains in `rules.csv`: each of the 24 cases owns its pitch, terms,
questions, reminder, delivery and thanks. Ordinary comms and distress calls share
`CatchReleaseFleetQuestOffer`: portrait, hail and story, then Continue to
`CatchReleaseFleetQuestTermsText`. Requirements, deadline, reward cards and the
offer menu appear on this second page. Questions and negotiations return directly
to the offer menu; alternate clients and stage follow-ups keep their own routes.
Qualified acceptance options explicitly say
`(Accept)`; they use the ordinary acceptance path. The Last Entry and Interment
follow-up questions use the existing extra-question routing and saved flags.
Scavenger Engine switches its acceptance labels after the technical question,
using complementary `$entity.catchrelease_fqQuestionAsked` conditions; both menus
retain the shared acceptance and decline handlers.

### `campaign/fish/colony`

| File | Owner / connection |
|---|---|
| `BreachConservatory.java` | Industry definition and aquarium state: stock, enabled state, and selected backdrop. |
| `AquariumTransfers.java` | Vanilla cargo pickers for transfers. |
| `AquariumTankScript.java` | Mounts the tank below the colony image whenever no covering visual is open, and removes it when another visual takes over. |
| `FishSpritePose.java` | Converts native image coordinates into a head-right mesh using precise `spriteDirection`; `AquariumTankPanel` bends that mesh and applies swimming turns. |
| `AquariumFishShader.java` | Aquarium-only filter around that mesh; lazy shared shader from `data/catchrelease/shaders/aquarium_fish_*`. Per-specimen coherence controls RGB separation and intermittent digital artefacts. No sprite mutation or screen capture; drawing contracts are in [UI.md](UI.md#portraits-and-sprites). |
| `Backdrops.java` | Separates campaign-wide backdrop ownership from the scene selected by each conservatory. |

`tools/AquariumShaderCheck` checks the strength curve outside the game. Add `--gl`
and the game's native library directory to `java.library.path` for off-screen
GLSL compilation and rendering checks: transparency, texture-region bounds,
mirroring, fade alpha, disabled shaders and restoration of the previous program.
Run from the mod root with the normal compile dependencies.

### `campaign/fish/fisherman`

| File | Owner / connection |
|---|---|
| `FishermanSpawner.java` | One temporary visitor sector-wide, one Fisherman per system; repairs duplicate pointers, excludes decoy, yields to tutorial posting; test path bypasses only the natural roll. |
| `CoreFisherSpawner.java` | Keeps permanent map postings in eligible inhabited systems; creates core markers on load and other inhabited-system markers on discovery. Spawns the local boat at its marker, unloads boats only outside the player's system, and reconciles weekly/on arrival. Tutorial reservations and active battles delay unloading. |
| `FishermanMapIcon.java` | Standing postings keep their token in system memory, including while hidden; unloaded fleets leave the posting at their last position. Temporary visitors and decoys retain fleet-owned markers. Uses `map/FleetMapVisibility` to yield to real radar contacts and redirect local autopilot before hiding. |
| `OuterReaches.java` | Collects real market entities, including connected/hidden/non-economy entities; chooses cleared spawn points and travel legs. Conditions-only planet markets are not settlements. |
| `FishermanBehavior.java` | Shared visitor/standing route checks and market navigation avoidance; also lamps, staged motes, pacing, visibility, visit duration, and departure. `CoreFisherBehavior` selects standing lifetime and assignment text. |
| `FishermanShelf.java` | Stores each boat's two initial habitat-data slots, duplicate prevention, and sale-based 30-day restocking. |
| `FishermanQuest.java` | Saved chart offer and exact identified catch. Selects species/system/source together and checks implement eligibility before offering the request. FishRequirement/FishCurrency govern progress, picker and spending; completion widens the shelf and starts a 90-day cooldown. Decline/reopen does not reroll. |
| `FishermanIdentity.java` | Stores the shared `PersonAPI` and selects one of five coherence portraits immediately before a hail. |
| `FishRumors.java` | Monthly leads: eight effects, four effect pairs, and one legendary-sighting slot unlocked by `FishingIntro.isComplete() && FishermanQuest.getRound() > 0`. Graduation alone grants an ordinary lead. Legendary reports snapshot an uncaught fish's actual host/residency; they neither transplant fish nor grant range data. |

`FishingMinigameDialogPlugin` takes rumor effects from the catch anchor. Size bias joins the specimen roll; `FishingMinigame` snapshots movement, bycatch chance and bycatch rarity for that retrieval. Size and movement boosts exclude legendaries, and their fixed treasure rarity is unchanged. Tuning stays in `FishermanConstants`.

Each ordinary `FishRumors.Kind` has one matching `CatchReleaseRumorText` row; legendary sightings select a species-specific row with `$catchreleaseRumorLegendary`, followed by its `_continue` option handler. The second page displays the intel receipt and returns to the Fisherman menu. `CatchReleaseCMD.setRumorTokens()` supplies the dialogue-lifetime `$catchreleaseRumorLegendaryColor` from `FishRarity.LEGENDARY.color` for shortened names; full names use `highlightFishText`. Legendary intel reads `CatchReleaseLegendaryRumorIntel_<speciesId>` from rules. Combined leads use their own passages. Intel expands the saved system/fish values with ordered highlights; ordinary dialogue and the no-lead fallback retain Continue to business.

Rumors last 60 days from `started`; publication starts the shared 30-day cooldown, including delayed bar reports. `ACTIVE_KEY` stores overlapping leads; ordinary effect rolls avoid occupied systems. Legendary rolls exclude caught fish and species with a live report; no eligible legendary falls back to the ordinary rotation. `getActive()` selects the newest published lead for dialogue; effect getters search all live leads. Legendary leads have no effects and expire on capture, host/residency change, or removed system. They offer autopilot and fishing-map navigation to the saved system without granting range data; expired leads cannot navigate. `LegendaryChases.isCurrentResidency()` only reads the ledger, avoiding rumor -> habitat -> rumor recursion. Host changes increment residency, preventing an old report from reviving after a fish moves away and back. `RumorIntel.shouldRemoveIntel()` shares expiry without reviving ended entries; vanilla calls it for queued and visible entries while unpaused. No new timer script.

### `dialogue/rules`

Use [RULES_AUTHORING.md](RULES_AUTHORING.md) when working on the command bridge or its memory/text bindings, not only when adding CSV rows.

| File | Owner / connection |
|---|---|
| `CatchReleaseCMD.java` | Single rules bridge: temporary tokens, conditions, actions, custom panels, highlights, question paging and fleet teardown. Restores prior rules plugin and options once after panels. |
| `QuestTextHighlights.java` | Splits explicit dialogue highlights into fish/rarity colours and ordinary requirement/reward emphasis. Preserves surrounding terms, displayed casing and repeated occurrences. Text rows supply values in display order before reward cards or other text. |
| `QuestDialogMap.java` | Shared temporary sidebar map for local and remote dialogue targets, matching vanilla mission icons, tags, and colours. `showIntroMap`, `showWorkMap` and FishJob's `showRemoteMap` action use `show()`; unresolved targets clear the preview. |
| `FishBuyer.java` | Immutable bulk-sale preview, revalidated before sale; protects active FishAsker and marked-gear specimens. Picker packing uses scoped reflection for input and transfer reset, copies the packed hold into the offer, and restores surviving original containers on exit. See [cargo pickers](UI.md#cargo-pickers). |

Chart offer/reminder tokens share `CatchReleaseCMD.setWorkTokens()`. `$catchreleaseWorkInstructions` reads a Text-only `CatchReleaseWorkPondInstructions` or `CatchReleaseWorkLampInstructions` row from `rules.csv`, chosen by the saved source before outer Text replacement; these private lookups do not execute scripts or add options.

`CatchReleaseFisherQuestions` keeps answered general questions grey and rereadable,
sorted after new topics before pagination. Its paired unread/read rows share the
same option and handler; saved flags select the variant. Tutorial questions remain
one-time options. See [question routing](RULES.md#project-routing).

### `campaign/fish/tutorial`

| File | Owner / connection |
|---|---|
| `FishingIntro.java` | Six-stage tutorial, grants, target selection, save repair and IntroIntel. Shared requirement/currency path; fifth valid same-rarity miss substitutes the single lesson target; invalid locations pause the count. Final multi-species lesson is excluded. |
| `TutorialWreck.java` | Creates a vanilla derelict cruiser beside the first suitable rupture. |
| `Castaway.java` | Stores planet eligibility and rescue state for the stranded crewman encounter. |

Tutorial questions rebuild shared `FireAll` menus: `CatchReleaseIntroRodQuestions`,
`CatchReleaseIntroFirstQuestions`, `ShopIntroQuestions`, `CatchReleaseIntroDeepQuestions`
and `CatchReleaseRangeDataBrief`. Persistent speaker-memory flags hide answered
questions. Each main menu keeps its forward option at order 100.
`CatchReleaseIntroFishShapeQuestions` contains the first-catch ghost-name follow-up,
not the main menu. That answer and `catchrelease_introToldBefore` return through
`catchrelease_introFirstQuestionsBack`; only `catchrelease_introFirstDone` advances
the first-catch lesson.

The `catchrelease_introCurious` and `catchrelease_introCuriousIntercepted` replies
offer rig delivery or a return to business without accepting it. Both delivery
options route to `catchrelease_intro_rod`, which owns the `giveRod` grant.

### `campaign/fish/minigame`

Lantern Jack keeps two Epic treasures active throughout its minigame, immediately replacing collected or expired ones. Other fish retain one active treasure and their finite spawn count and delay. All pickups use the existing win-only reward path; escape loses the loot. `FishingMinigamePanel` draws every active treasure and coalesces simultaneous spawn/pickup sounds.

| File | Owner / connection |
|---|---|
| `FishingMinigame.java` | Owns in-game bar/fish movement, progress/escape and treasure; advances movement -> treasure -> progress. Each target choice is the species' own move, its movement type's signature move (`specialChance`) or a move borrowed from the MIXED pool (`mixChance`). Any move of any kind that lands at least `MINIGAME_TELL_DISTANCE` from both the fish and its current target, with a speed limit of at least `MINIGAME_TELL_SPEED`, waits out a `MINIGAME_TELL_TIME` tell on the old course (`withTell`); nothing else gets one, and the opening move never does. A twitcher's signature bound lands before its next pick (`isBounding`). Uses runtime `FishConstants`, tackle, campaign inputs and live player-rate lookups. Legendary treasures are Epic. No dependency on the authoring tools. |
| `FishingMinigamePanel.java` | Draws the track, target, progress, and treasure, including the tell before a telegraphed move; handles input; records bycatch, catch intel, route progress, and legendary completion. Manta fights own a panel-bounded `ChromaticAberrationOverlay` instance, disposed by both dialog-dismiss paths; campaign aberration still excludes dialogs. |
| `MantaMinigame.java` | Manta-only model selected by the dialog. Two independent motion-only decoys call the same `FishingMinigame.advanceFish`; each has its own targets, velocity and tells, but no treasure/progress/rewards. Opening positions are shuffled across all three. The panel renders all with the real target's presentation (including sonar/chicken); only the real model controls green-bar coverage, catch progress and sound hooks. Restart resets all three; ending stops them. |
| `FishingMinigameDialogPlugin.java` | Hosts the custom visual, preserves source rupture and quest identity for drone and harpoon catches, applies tutorial catch protection, preserves campaign music, and exposes dev reopens that bypass substitution. |

### `campaign/fish/entities` and `campaign/fish/spawner`

| File | Owner / connection |
|---|---|
| `FishEntityPlugin.java` | World fish mote: per-species movement, straight-line dives, held and stunned states, glow, source rupture, and legendary behavior. |
| `BuriedMoteEntityPlugin.java` | Invisible open-water fish; drifts with its species' campaign pace and wander. |
| `PondFishSpawner.java` | Selects species by habitat, range, implement, weights, tackle, and rumor effects. |

### `campaign/fish/shop`

| File | Owner / connection |
|---|---|
| `FishShopDialog.java` | Outfitter and session undo. Automatic payment preview spends the same specimens; manual payment uses the shared picker and reopens next frame. Undo restores exact cargo/credits/gear/tiers/marks. |
| `ShopEntry.java` | Uniform wrapper for upgrades, modules, and curio switches. |
| `ShopPricing.java` | Seeded credit-and-fish prices. |
| `ShopMarks.java` | Persistent shopping list. |
| `FishAsker.java` | Interface implemented by jobs, tutorial intel, and chart-request intel so the shop, cargo, and route planner can read fish requirements uniformly. |
| `FishCurrency.java` | Counts and spends matching fish. |
| `FishRequirement.java` | Describes and evaluates count, rarity, grade, species, region, source rupture, timestamp, coherence, method, and implement. |
| `ShopSchematics.java` | Saves quest-earned permissions for stocked modules and the last two tiers of each upgrade. |

### `campaign/fish/items`

| File | Owner / connection |
|---|---|
| `FishItems.java` | Item IDs, encoding, decoding, landing, unboxing, and transaction-screen packing. |
| `FishItemPlugin.java`, `FishBundleItemPlugin.java`, `FishPileItemPlugin.java` | Specimen, crate and pile cargo items: names, prices, right-click stowing and unpacking, icons and tooltips. The three rows in `special_items.csv` carry `hide_in_codex`; species pages live in the mod's own Codex category. |
| `FishItemTooltips.java` | Shared tooltip blocks for the three items; layout contract in [UI.md](UI.md#catch-item-and-species-tooltips). |

### `campaign/fish/crab`

| File | Owner / connection |
|---|---|
| `CrabWares.java` | Stock, prices, ownership, curio switches, fallback bass and last explosive target; industry plans count as owned when held or learned. |
| `CrabBackdrops.java` | Rotates one backdrop per port from `backdrops.csv`. |

### `campaign/fish/tackle`

| File | Owner / connection |
|---|---|
| `TackleManager.java` | Separates module ownership from the module fitted to each rig. |

### `campaign/fish/map`

| File | Owner / connection |
|---|---|
| `FishMapFilterScript.java` | Map filter installation, UI mounting and deferred Codex/intel handoffs. Reuses an underlying map only on a return originating there; preserves saved range knowledge. |
| `FishMapPane.java` | Search, type filters, species list, coherence toggle, request restrictions, and no-data/reset states. |
| `FishIntelPlanetPanel.java` | Patterns column beside the selected planet in Intel; matches the detail card's height and colours, with a top-aligned, horizontally centred, non-scrolling fish grid. Layout contract in [UI.md](UI.md#intel-and-sidebar-maps). |
| `FishPresence.java` | Species/system visibility: caught or learned data in normal play; computed, non-persistent full chart in dev mode; optional request allowlists. |
| `FishRoutePlanner.java` | Builds route suggestions from every `FishAsker` and shop mark, expands broad requirements, and orders stops using stability and slipstreams. |
| `FishTooltips.java` | Species hover for map rows, route-planner rows, system pane and intel planet cells; layout contract in [UI.md](UI.md#catch-item-and-species-tooltips). |

### `campaign/fish/codex`

| File | Owner / connection |
|---|---|
| `FishCodexEntryState.java` | Central `UNKNOWN`, `RANGE_DATA`, and `CAUGHT` policy for visibility, links, art, description, records, and map access. |

### `campaign/fish/legendary`

| File | Owner / connection |
|---|---|
| `LegendaryChases.java` | Persistent host, sighting, provocation, Imposter reveal, completion, and defense state for each legendary. |
| `LegendaryStarAvoidance.java` | All non-False-Dawn legendaries avoid each local star's surface and nominal corona plus 150 units. Natural buried spawns, surfacing, console placement, explosive respawns and Imposter reveal use the same boundary. Swimming, submerged runs, travel dashes and reveal drift test complete movement segments; positions already inside are corrected. Quorum steering reserves its escort radius; shell-game centers reserve the largest decoy ring. Manta steering moves its line center with one-slot clearance, preserving spacing and blackout swaps. Held catches remain attached to their retrieval gear. Uses existing movement callbacks, with no new script, saved state or sector-wide scan. |
| `SlipDashModule.java` | Moray slipstream trail and curved travel dash. A failed catch bypasses range/intensity/cooldown gates, ends any prior growing trail, and starts a 9.75-second emergency dash (1.5 × normal maximum), keeping the fish surfaced. Uses the existing trail roll-up and haunt cleanup. |
| `QuorumShellGame.java` | Real Quorum failures spin the shell game four turns in 1.2 seconds and add up to 12 outer-ring phantoms. They share the existing decoy anchor/cleanup, cannot award catches, and repeated failures reuse them. The two ordinary shell-game decoys remain catchable and replenish separately. |
| `LegendaryHaunt.java` | Transient coordinator. The manta runs only its formation and chromatic aberration modules. Starting a Lantern Jack haunt immediately restores its base shield; later deflections retain the normal cooldown. Stored shells are still earned by eating motes. |
| `MantaBackgroundBlackout.java` | Manta-only 0.3-second background blackout every 15–40 unpaused seconds. First Luna renderer on `TERRAIN_1`, after vanilla background/starfield and before breach windows, motes, fleets and HUD. No saved background changes. `MantaFormationModule` swaps the real mote into a different stationary slot on blackout onset, preserving its target offset and shield/catch identity; swaps wait while held. Renderer expires outside its owning sector/system and is removed on haunt cleanup. |
| `MantaFormationModule.java` | Abyssal Ghost Manta haunt: one real mote and two haunt-owned phantoms, fixed world-space line at 180-unit spacing. `FishEntityPlugin.advance` synchronizes positions after movement; copies use the real mote's dive visibility and shield display. Only the real mote is catchable. `LegendaryHaunt.getMantaLampAlpha` applies location-scoped visual flicker to spot/fan breach, glow and impression rendering, never detection geometry. Coordinator cleanup removes copies and transient bindings; load-time haunt sweeping prevents duplicate formations. |
| `FalseDawnOrbit.java` | Keeps surfaced, diving and buried False Dawn movement in an annulus outside the largest non-pulsar star with a usable corona in its system. Uses the actual corona bounds; natural spawns, console spawns and explosive-hit respawns share the constraint. Dives retain their visibility timing but follow the corona instead of a straight chord through the star. |
| `FalseDawnCorona.java` | Adds short, complementary-hue spikes to the host star's vanilla `FlareManager` queue while the player is in the host system, including before provocation. Only its own flares are removed on departure, capture and before saving; star specs and natural flares are unchanged. A location listener handles departure; the existing haunt advance refills the queue because vanilla exposes no flare-finished callback. No extra frame script or renderer. |
| `../entities/HauntMineEntityPlugin.java` | Non-damaging False Dawn mines. Push and pull deliver equal, opposite one-shot impulses through the fleet movement module via `setVelocity`; the implosion ripple remains visual only, with no lingering attraction. Interdiction mines cancel interdictable abilities and stop the fleet for two seconds using vanilla's one-frame stop request; no persistent speed modifier. Timers stop while paused. |
| `MinefieldModule.java` | Keeps the 3–6-mine waves and 22-mine cap but rejects overlapping trigger zones, leaving at least 300 units of passage (more for a large player fleet). Placement checks earlier waves as well as the current one, keeps clear of the player and stellar surfaces, and skips crowded spawns after bounded attempts. Every new mine gets the full two-second arming delay; blink phase is separate. |
| `GhostFleetsModule.java` | Lantern Jack's harmless fleets: first at 5–10 seconds, then every 15–25, at most two. Spawn 1000–1600 units away with 2–15 ships and at least 8 burn; intercept, with half switching to HOLD after 3–7 seconds. Hard-remove at 250 units or 30 seconds. Transponders stay off; flags prevent clicks, comms and other-fleet attention, and `setNoEngaging` prevents INTERCEPT battles. Harpoons ignore haunt fleets. |
| `FakeWrecksModule.java` | Lantern Jack wrecks: first at 10 seconds, then every 15–35; at most 25 including fades. Spawn 600–1100 units away, expire after 120–240 seconds, and use registered ship variants except fighters/stations/modules. Click-to-approach stays enabled while interaction/salvage tags are withheld. At 100 units the first wreck approached becomes real; later wrecks have a 10% chance, otherwise fade over 0.5 seconds. Real wrecks restore vanilla interaction tags and retain default derelict salvage. All remain haunt-owned. |
| `LanternSensorGhostsModule.java` | Jack-only vanilla `BaseSensorGhost` contacts: echo player movement, intercept then depart, or pass by. First and repeat spawns take 5–10 seconds, cap four including fades. Uses the haunt's existing advance and cleanup, not the hyperspace manager or a new sector script. Behavior durations convert seconds to campaign days; original tokens remain tracked after vanilla starts fading. No fleet spawning or drive drain. `SensorGhostsModule` remains the Imposter's separate implementation. |
| `LonglinerDecoy.java` | Imposter disguise. Player lamps remove fleet and spawn mote at the same location -> 1s drift along last velocity -> alert + positional sound -> 0.3s delay -> flee. Excluded from Fisherman reconciliation. |
| `LegendaryShields.java` | Persistent defenses and render state: Imposter explosive-only shield, Quorum escort/regeneration, Lantern Jack stored shells/prey lure, regrowing shells and provocation. |

Harpoon and drone failure callbacks release real legendary motes through
`LegendaryShields.onFailedCatch` instead of fading them out. Ordinary fish,
Quorum splinters and phantoms retain their previous failure handling. The callback
path runs once per resolved minigame, not for a busy UI or a dev fish replacement.
`LegendaryHaunt.onFailedCatch` starts the matching haunt if necessary, refreshes
its sighting grace period and dispatches `HauntModule.onFailedCatch`; responses
then advance through the existing coordinator, with no additional frame script.
Manta base-shield breaks call `LegendaryHaunt.onMantaShieldPopped` synchronously:
start its haunt if needed, rebuild missing copies and switch stationary slots
through `MantaFormationModule.switchPosition`. The initial wake-up deflection
does not spend the base shield and does not trigger a switch.
Every manta slot switch shares the 0.3-second blackout clock for invulnerability
and jitter. `FishEntityPlugin.isAvailable` rejects it for harpoons and drones;
direct shield/explosive contacts and blast effects also respect this window.
All three motes draw 32 jittered glow copies over a 160-unit spread using vanilla
`JitterUtil`, without moving collision positions. The module refreshes the seed
only during unpaused advance; expiry and cleanup end both protection and jitter.
Manta failure immediately restores its base shield and invokes the formation's
existing background-only blackout/slot swap, rebuilding missing copies first and
resetting the normal blackout timer. It does not wait for the 15–40-second interval.
Other legendaries immediately restore their base shield without changing movement
or granting Lantern Jack stored shells. The Imposter keeps `shieldPopped`; its
saved `recoveryShield` deflects one ordinary harpoon and is then consumed. It uses
the normal purple shield display, not the red explosive-only hull shield.

`tools/FalseDawnCheck` runs standalone checks for corona bounds, host selection,
specimen lifetime, flare cleanup, mine forces, stun duration and minefield passages.

### `campaign/fish/constants` and `campaign/fish/intel`

| File | Owner / connection |
|---|---|
| `FishIntelMapButton.java` | Shared navigation contracts: open the fishing map for habitat targets, plot a route for known systems, or set autopilot for non-fish objectives. |
| `FishIntelNotifications.java` | Defers new intel to the first unpaused frame after dialogue; updates use a zero-day delayed script; inline cards do not consume the queue. |

### `campaign/ponds`

| File | Owner / connection |
|---|---|
| `listener/OnJumpPondSpawner.java` | Transient Map/Intel-opening and current-location listeners, registered on load. Vanilla 0.98a-RC8 `StarSystem.setEnteredByPlayer` has no event; newly charted systems are prepared at the next Map/Intel opening, not at the data grant itself. `CoreUITabListener` is dispatched before display, and the map reads current terrain entities when drawing. |
| `listener/PondCreator.java` | Populates charted or entered systems from planet count, capped at two ponds, and finds clear positions away from planets, ponds, nebulae, and rings. |
| `scripts/PondCameraFocusScript.java` | Smoothly acquires and releases camera control around an open pond. |

### `campaign/crime`

| File | Owner / connection |
|---|---|
| `LampOffence.java` | Defines the inhabited-world distance gate, consequences, and per-faction/per-system warning history. |
| `LampPatrolResponse.java` | While lamps are on and the player remains inside the inhabited-world radius, every eligible patrol that sees the player pushes an intercept ahead of its current assignments. |
| `HarpoonOffence.java` | Per-faction, per-system hit history, debts, reputation loss, witness state, and response ladders. |
| `HarpoonPatrolResponse.java` | Sends one collector at a time in the incident system. |
| `HarpoonWitness.java` | Makes a civilian seek a patrol. |
| `HarpoonHitman.java` | 30% revenge-contract roll for eligible colonial factions and eligible victims. |

### `abilities`

| File | Owner / connection |
|---|---|
| `charges/BaseChargedSkillshotAbility.java` | Shared charge pool for charged abilities. |
| `rod/ability/PondInteractionAbilityPlugin.java` | Opens ponds, launches and recalls drones, and supports lamp fishing with Breach Coupler. |
| `rod/entities/FishingDroneEntityPlugin.java` | Drone launch, orbit, timed chase, catch, and return. |
| `rod/scripts/FishingDroneSwarmScript.java` | Owns one cast, staggered launches, recall, target assignment, hit cues, and reachability checks. |
| `rod/scripts/RoamingDroneSwarmScript.java` | Pondless Breach Coupler swarm. |
| `harpoon/ability/HarpoonAbilityPlugin.java` | Charged targeting, aim assist and tow-line cutting. |
| `harpoon/entities/HarpoonEntityPlugin.java` | Flight, collision, shields, mines, hauling, fleet contact, rope, catch, and return. |
| `searchlight/ability/SearchlightAbilityPlugin.java` | Breach Lights activation, spool, slow, detection penalty, and all beam renderers. |
| `searchlight/scripts/Searchlight.java` | Beam sweep, lock-on, distortion, and ripples. |

The three ability plugins own their tooltips and read current upgrades and fitted
modules when opened. Display contracts are in [UI.md](UI.md#ability-tooltips).
`tools/AbilityTooltipCheck.java` checks their text, highlights, upgraded values,
equipment and availability notices against `UpgradeData.csv` in a separate JVM.

### `memory`, `helper`, `reflection`, and `testing`

| File | Owner / connection |
|---|---|
| `memory/upgrades/UpgradeManager.java` | Saves purchased levels. |
| `memory/charges/ChargeManager.java` | Persistent fractional charge pools. |
| `reflection/ReflectionUtils.java` | `MethodHandle`-based reflection that avoids the script classloader's direct reflection ban. |

## Contracts

Rules-engine and menu routing constraints: [RULES.md](RULES.md#project-routing).

### Engine / missions

- Terrain uses `getPlugin()`, not `getCustomPlugin()`. Read its radius through `CampaignTerrainAPI`. Override `getActiveLayers()` and `getRenderRange()`. `BaseTerrain.advance()` affects local fleets unless the terrain opts out.
- Terrain and entity scripts advance outside the player's current location. Gate rendering and sound on `isInCurrentLocation()` because LunaLib keeps one sector-wide renderer list.
- A handled `callAction()` must return true. Vanilla treats false as an unhandled action and throws.
- `BaseHubMission` assumes `getPerson()` is non-null in many intel, reward, reputation, and distance paths. Fleet and entity missions must set a person override, usually the fleet commander.
- `setTimeLimit()` is compared with total mission elapsed time, not time in the current stage. Multi-round jobs that remain in `WANTED` must call `setClock()` for each round. Intel must use `getDaysLeft()`.

### Importance, interaction, and fleet AI

- `Misc.makeImportant(entity, reason)` takes a reason without `$`. `BaseHubMission.makeImportant(entity, flag, stages...)` takes a memory key with `$`. Pair each overload with its matching removal call.
- A memory pursuit flag makes a fleet willing to pursue; an explicit `FleetAssignment.INTERCEPT` makes it change course. Use both where immediate pursuit is required.
- Vanilla has no flee assignment. Civilian flight uses `MEMORY_KEY_AVOID_PLAYER_SLOWLY` plus Emergency Burn when available.
- A hostile fleet can still hail the player. `HailPlayer` on `BeginFleetEncounter` opens comms regardless of relationship; `MakeOtherFleetGoAway` handles a negotiated departure.
- The Fisherman uses vanilla `OpenComms` on `BeginFleetEncounter` so the player never sees a combat-oriented fleet screen.
- Lamp enforcement follows vanilla transponder logic. All seeing patrols may interrupt their assignments only while the player is within 3,000 units of an inhabited market; leaving that radius cancels their temporary intercepts. The first dialogue claimant releases only the temporary intercepts of the others. Turning the lights off does not erase an already observed offence.
- Harpoon incidents are stored per faction and system. Patrol collectors are local; one system cannot escalate another.
- Civilian harpoon responses depend on fleet role, relationship, and vanilla's reciprocal 1.25× strength threshold. A convoy remains civilian even when heavily escorted.
- Repair bills and fines return their outcomes through memory. The global pending marker prevents repeated sector-wide searches when the original fleet is no longer nearby.
- Camp completion is polled because destruction, bribery, dialogue, and departure do not share a callback.
- `despawn()` reports fleet removal to managers and starts the fleet's own fade. `FleetQuest` replacements additionally clear AI, move the original away, and call `Misc.fadeAndExpire()` so the replacement can occupy the same position immediately. Other retiring fleets must not move their still-rendering token during that fade.
- A local fleet-job offer adds state and a cyan drawn marker to an existing eligible fleet; see `FleetQuestSpawner` above for fleet types and exceptions. It does not create or rename a fleet; the expedition and prospecting route managers create the only fleets that exist to carry offers. Acceptance creates fresh members in a mission-owned replacement and reports the original despawn.
- `FleetQuest.ensureMarked()` uses `FleetMarkerRenderer` with `fleet_quest_map_icon`: light cyan for local offers, yellow after acceptance, including accepted distress jobs. The existing encounter/mission callbacks restore transient renderers after loading; release expires them.
- Fleet jobs use stage-owned `setFlag` for delivery, not `makeImportant`. `markDeliverable()` registers the delivery flag once; no vanilla importance record is added. Delivery flags still follow `WANTED`; release clears the hand-in flag and both custom markers. `getMapLocation()` continues to target the giver.

### Save data, cargo, and shop state

Cross-version campaign saves are unsupported during development; see [workflow](../CLAUDE.md#make-and-record-changes). Current saves retain levels, cargo, quest state and equipment ownership. Load hooks restore transient state and current registrations, not old data formats.

- Fish encoding uses four required fields and positional optional tail fields for origin, method, implement and chart provenance. The current encoder trims absent tail fields; preserve interior placeholders and accept its short records.
- Containers are identified by contents, not stack identity. Spending part of a crate or pile removes it and creates a replacement. Always repack with the original container ID.
- `FishItems.stow()` is the only landing path and normally creates a crate. Loose fish remain valid for all counting and spending.
- `FishItems.isContainer()` is the shared crate/pile test. Do not add direct bundle-ID checks.
- Unpacking a pile restores any singleton species as a loose fish, not a one-fish crate.
- `Tackle.Fit.BOTH` describes compatibility; it is not a rig. Rig loops use `Fit.isRig()`.
- Module ownership and module fitting are separate. Charge only when `isOwned()` is false; grants must both own and fit the module.
- Stock is a third state. `TackleManager.getOptions()` contains stocked modules plus owned unstocked modules so purchased gear can be removed and refitted.
- Explosive Head is unstocked and consumable. A miss keeps it; detonation removes ownership and the fitted slot, which makes Crablobab sell it again.
- Upgrade tiers and modules granted outside the shop still go through `ShopEntry.grant()` so a running ability is stopped and restarted with its new values.
- A curio is a switch, not a purchase. Its shop price is null, it never becomes “done,” and the button toggles it.
- Celebration Charges are purchased from Crablobab and switched in the outfitter. They are not a LunaLib setting.
- Abilities read tuning values when activated. Any code that changes their upgrade or module inputs must restart the affected running ability.
- `StatIds.getAbilityId()` uses an explicit map. Do not infer the ability from a stat-name prefix.
- ROD chase duration and rarity priority are progressive stats: every purchased tier must affect runtime behavior.
- `drone_acceleration` is a steering response time in seconds: upgrades reduce it. `tools/DroneSteeringCheck` checks acceleration, turning and return approach across every tier at 30/60/144 Hz.
- Retrieval Head refunds one charge only after a confirmed player mote collision. It preserves fractional recharge progress, respects the cap, and uses the ordinary charge-ready callback.
- Explosive Head never lands a fish. Its blast state is terminal, consumes the head, and can immediately make a fleet hostile. Vanilla's explosion entity supplies fleet damage, visuals, and sound.
- An industry blueprint is `industry_bp` with the industry ID as item data. The industry must still override availability and `showWhenUnavailable` against `knowsIndustry()`; the blueprint item alone does not gate construction.

### Fish habitats, ranges, and balance

- `FishHabitat.of()` is the shared habitat snapshot. `FishRanges.matches()` is the only range decision, including pins and relaxation. UI calls `FishPresence.livesIn()`, which routes through the same logic.
- Habitat criteria treat blank as unrestricted except for the Abyss. Abyssal species must explicitly name `ABYSSAL`.
- Monthly reassessment refreshes moving habitat inputs and relaxes non-Abyssal species with fewer than three systems. Relaxation order is constellation age, ±0.25 coherence, star colour, then region. It never crosses the Abyss boundary or raises any system above fifteen species.
- Active `FishAsker` species are pinned to their previous system list during reassessment and unpin when the ask ends.
- Ordinary species use one or two adjacent regions unless a stronger star, coherence, or theme gate already provides the range. Every region retains at least two ungated Common species.
- The non-legendary roster is exactly 100 fish in a 59/23/12/6 Common/Uncommon/Rare/Epic split. The Abyss contributes 7/2/1/1 of those. Zero-weight mechanism rows, such as the Quorum splinter, are outside the hundred.
- The Abyss uses its own high-difficulty ladder. Rarity controls frequency and value there, but even Abyssal Common rows use at least main-sheet Rare difficulty.
- Catch bar size comes from `FishConstants`; no catch-stat upgrades exist in the registry or runtime. Progress and escape use species and tackle modifiers. The tuner's bar/gain/loss controls remain what-if inputs, not purchasable equipment.
- Balance every row for the base kit. `FishShopDialog` sells no catch-stat upgrades, so each species must stay catchable with the `MINIGAME_BAR_SIZE_FALLBACK` bar, neutral gain and loss, and no tackle. Tune `difficulty`, `restlessness`, `motionSpeed`, `progressRateMult`, `escapeRateMult`, `specialChance`, and `mixChance` together and simulate the result.
- Rarity must read as catch difficulty. The Player angler, fitted to one recorded normal player, is the reference: each rarity has its own band of Player catch rates with a gap to the next, and no fish may fall outside its band (`FishTunerChecks.RARITY_BANDS`, checked over 400 attempts per fish with a 4-point allowance). The bands are Common 84–95%, Uncommon 65–75%, Rare 47–57%, Epic 29–39% and Legendary 13–21%; measured on fresh seeds they are 83–96/63–76/48–58/31–40/14–22%, with means of 90/70/53/35/18%. Within a band, species keep the order they had before the bands were set, so some are harder than others. Abyss species sit at the hard end of their rarity's band instead of a band harder. The bands are set through `progressRateMult` and `escapeRateMult` alone; movement values keep each species' character. Across all species the Skilled angler then averages about 95/80/74/65/49%, and Regular about 49/27/26/19/13%. Later difficulty settings are meant to ease the base game. No species may be caught in more than half of its attempts by holding the button or by never pressing it; `FishTunerChecks` asserts this over the real sheet. Signature and random moves carry most of that: sinkers and floaters also need a steeper gain/loss ratio because their home zone is where a parked bar sits. Both shares apply per target choice, so a style that picks often needs smaller shares; twitchers pick about three times as often. An interrupted twitcher bound turns back through the middle, where a bar held still covers it, which is why the bound always lands. Slow common styles rarely show a tell; faster rarer fish and MIXED ones show one every two to four seconds.
- `motionSpeed` and `restlessness` also move the campaign mote; see [fish entities](#fish-entities-and-catch-provenance). Restlessness pulls in opposite directions: a restless mote wanders more in the world, but on the line it retargets before finishing a move and is easier to cover.
- `difficulty` is also the player-facing rating. `FishCodexEntry` labels it, and `FishermanQuest.isChartRequest()` routes a species rated 65 or more to a chart request. Keep main-sheet Commons below 65 and every other row at or above it.
- Species that share a rarity and `motion` differ in at least one of `motionSpeed`, `restlessness`, or `jitter`, so players can learn them apart.
- Weaver is not assigned above Uncommon, and Lunger is not assigned to Common. `MIXED` and a species' random moves may still roll either for one move.
- `reachedBy` uses `POND`, `BREACH_LAMP`, or blank for either. Check both the catch method and its origin; drones can also catch at Breach Lamps with the Breach Coupler. See the combinations below when rolling equipment requirements.
- A legendary has one host, one permanent catch, and no range data or job asks. All six are lamp-only. The five non-Abyssal legendaries are Lantern Jack, Slipstream Moray, Quorum, False Dawn, and The Imposter; the manta is Abyssal.
- Legendary hosts and motes remain disabled until tutorial graduation. A sighting starts the 90-day relocation timer; the fish never relocates while the player is in-system and never returns after landing.
- False Dawn selects the largest-radius usable corona star across chartable systems, regardless of coherence; its haunt supplies the coherence change. Its old regional sheet preference does not limit this selection. Equal-largest hosts can alternate; a sole largest host is retained. If no usable corona exists, no natural host is assigned. A host that becomes unsuitable is replaced only while the player is elsewhere; new natural spawns are gated immediately. `SpawnFish` still overrides the host for testing but places the fish in the largest usable local corona.
- False Dawn's repeating orbit cannot finish a generic swim crossing. The buried spawner therefore refuses a second live specimen in that system; existing specimens expire when their ledger marks capture or a different host.

| Catch | Method | Implement |
|---|---|---|
| Drones at a natural rupture | `DRONE` | `POND` |
| Harpoon catch of a fish from a pond | `HARPOON` | `POND` |
| Harpoon catch at a Breach Lamp | `HARPOON` | `BREACH_LAMP` |
| Drones at a Breach Lamp with the Breach Coupler | `DRONE` | `BREACH_LAMP` |

### Coherence model

- `Aberration` indexes static sources and colony fields instead of scanning the sector on every read. Rebuild on day change, gate activation change, or economy market-count change.
- System readings fill on arrival, map opening, or demand. `localPull` is a separate in-system distance calculation over local tagged entities and only lifts the system reading; it does not scale it down.
- The Abyss uses uncapped `Misc.getAbyssalDepth()` divided by `ABERRATION_ABYSS_SPAN`. A span of one restores the old hard cliff.
- `openSpaceReading` must include all indexed sources, not only Abyss and slipstreams, because the heat map samples bare hyperspace points.
- Each inhabited market creates a five-light-year quadratic stabilizing field. Overlapping fields do not stack; the strongest stabilizer is subtracted from the strongest destabilizer. The colony's own system is exactly zero aberration.
- Extreme-coherence rumors set one system to zero or one aberration without changing its cached ordinary reading or neighboring hyperspace. Targets must be outside the corresponding coherence band; instability excludes colonies, and a new colony overrides an ongoing event. Catch rolls, known route readings and habitat snapshots use the effective value; expiry restores the ordinary reading. Quest pins still take precedence over habitat changes.
- `CoherenceHeatField` draws temporary system rings in the shared coherence colour over the ordinary hyperspace heat field. It reads live overrides so expired rings disappear without rebuilding the map.
- Slipstreams are indexed as sampled ribbons through `SlipstreamTerrainPlugin2.getSegments()`. The old `SlipstreamTerrainPlugin` is inert in 0.98a. Foreign implementations fall back to their anchor.
- Marks verify that their source still exists so short-lived sources do not remain active until the next daily rebuild.
- In-system reach is `ABERRATION_LOCAL_BASE + ABERRATION_LOCAL_PER_LY × reachLY`. Do not add a second hand-maintained reach table.
- Hyperspace has no entity-local reading; its relevant sources are the Abyss depth field and slipstream terrain.
- Gates are individual marks because active and dormant gates have different reach and strength. Use `GateEntityPlugin.isActive()` only for vanilla gates; foreign tags fall back to sector-wide gate state.
- Foreign equivalents are identified by optional tags. Missing tags return empty results and create no dependency.
- Hidden sources use one survey test in `Mark.isFound`. Stars and slipstreams are the only always-visible exceptions. Fisherman map postings follow the lifecycle below; motes use Breach Lights instead.

### Fish entities and catch provenance

- A mote spawned at its destination expires immediately. Spawn and target positions must differ.
- `FishEntityPlugin.HOLDS_KEY` makes a tutorial mote choose another point in its pond instead of expiring. Chart and camp fish intentionally do not hold.
- Campaign motes move at `FishRarity.speedMult` and `wanderMult` scaled per species through `FishSpec.getCampaignSpeedMult()` and `getCampaignWanderMult()`, for both surfaced and buried motes. `FishSpecLoader` derives the factors from `motionSpeed` and `restlessness` relative to the median of the rarity's weighted non-Abyssal roster. It clamps them to `CAMPAIGN_PACE_MIN`/`MAX` and `CAMPAIGN_WANDER_MIN`/`MAX` and caps combined wander at `CAMPAIGN_WANDER_CAP`. Legendaries keep factor 1 because their chases are scripted. Editing a species' minigame speed or restlessness therefore changes its world movement too.
- No ordinary mote stands still for long: a lunger's freeze is capped at `LUNGER_FREEZE_MAX` and still creeps. Rare-and-above motes dive for `DIVE_TIME` about every `DIVE_INTERVAL`, shortened for more erratic species. Under the fabric they hold a straight course at cruise speed along the bearing they dived on, so the surfacing point can be anticipated.
- The method says which rig caught a fish; the implement says what exposed it. Both values must follow the mote's actual provenance.
- Pond and harpoon catches must carry the exact source rupture where applicable. Chart requests also carry target ID, target system, and earliest valid timestamp through loose fish and containers.
- Chart and tutorial completion use the same `FishRequirement`/`FishCurrency` read and spend path as other quests. Do not add a separate cargo-completion check.
- A chart request plants or replants its identified target only while the player is in the target system. Only the active specimen ID/species/system suppresses replanting; a stale quest flag does not. Open-space targets spawn away from their destinations, and chart intel routes to their saved in-system search coordinates.
- `FishermanQuest.markCatch()` assigns both aberration 1.0 and chart provenance after checking specimen ID/species/system. This happens after normal catch rolls and equipment bonuses. Ordinary catches keep their own readings and cannot satisfy the identified request; loose/container cargo, progress and hand-in use the same requirement.
- Chart offers select a valid species/system/implement together through `FishRanges.matches()`. Lamp-only species cannot use ponds; pond-only species require a free pond. Zero-weight, legendary and low-coherence-ineligible species are excluded. Occupied camp ruptures may inform species choice but are never quest destinations.
- `FishermanQuest.hasActiveRumor()` excludes every system in `FishRumors.getActiveRumors()` from chart generation, regardless of rumor kind. A saved, unaccepted offer there is withheld without replacement until its rumor expires; accepted targets remain unchanged. Expired intel cards do not gate generation.
- Harpoon aim assist and collision both call `HarpoonEntityPlugin.canTake()`. Buried motes use `catchrelease_buried_mote` and require full light, or mere detection with Fathom Head.
- `HarpoonAbilityPlugin.interceptPoint()` leads eligible motes at their sampled velocity and the upgraded shot speed. `FishEntityPlugin` samples between callbacks to include external controllers; `BuriedMoteEntityPlugin` samples its own movement. Samples are transient and retained while paused. Correction stays within the purchased angle and 1200-unit range; shots do not home or predict later turns.
- `HarpoonEntityPlugin` sweeps each outbound step and clamps the last step to range. Shields retain priority over mines; ordinary fish use the earliest contact across surfaced and buried motes. Only the selected buried mote is unearthed, and NPC shots cannot take buried motes. `tools/HarpoonAimCheck` checks lead, assist tiers, eligibility, external movement, frame rates, shield/mine ordering and range from a standalone JVM with the mod root as its working directory.

### Rendering, UI, reflection, and audio

Java custom-panel behavior, sprite state, drawing gotchas and minigame UI timing are in [UI.md](UI.md). Shared player-facing text guidelines are in [DIALOGUE.md](DIALOGUE.md#shared-text-presentation). Campaign VFX and non-UI engine constraints remain here.

- Fan light and fan breach window share `STEPS_ACROSS`, `STEPS_ALONG`, and both alpha curves. Change their geometry together.
- Glow, fan, and impression renderers share the same resting alpha formula. Module changes should affect light shape, not total intensity.
- Camera-centered objects have no camera parallax term. Account for this in effects such as `PondDepthField`.
- `ReflectionUtils` uses `MethodHandle` because the Starsector script classloader rejects direct references to `java.lang.reflect.Field` and `Method`.
- Sound IDs are unchecked strings until playback. Validate them against merged sound data. Starsector JSON supports `#` comments and trailing commas, and sound entries may be arrays or objects.
- `playUISound` expects stereo; positional `playSound` requires mono; loops should be mono.

### Fisherman and tutorial lifecycle

- The Fisherman is one saved `PersonAPI` shared by every boat. Apply the hailed boat's portrait immediately before vanilla builds the person panel; background boats must not mutate it.
- Fisherman portraits are registered `graphics.characters` sprite IDs in `settings.json`. Rank and post remain blank so vanilla shows the rankless person card once.
- All Fishermen use one checked `GO_TO_LOCATION` leg at a time, not `PATROL_SYSTEM`. `OuterReaches` excludes 5,000 units beyond each market entity's radius plus a 1,000-unit route buffer. Deterministic fallback legs are checked too; no valid leg means hold and retry. A boat already inside an exclusion may only take a leg that increases its distance from every enclosing market throughout the escape.
- `FishermanBehavior.keepWorking()` rechecks destinations, current movement and moving markets every 0.25 campaign seconds, refreshes vanilla navigation avoidance and replaces unsafe assignments. Battles are not interrupted; the transient check timer starts immediately after loading.
- `FishermanInterception.cutOff()` places an offscreen boat on the viewport boundary plus 50 screen pixels, converted through `ViewportEdge` for zoom and aspect ratio. A boat already onscreen approaches from its current position. Candidates must have a clear approach; blocked edges or an invalid/displaced viewport defer the encounter without moving the boat. Cancellation clears pursuit, never the fleet's relocation latch. Intercept safety checks the player segment, not the previous travel destination.
- The existing 0.5-second intercept and 0.25-second route checks stay unpaused: public listeners do not report entry into an arbitrary rupture radius or moving-market route clearance. Interception scans only the current system; route checks inspect their owning boat.
- Fisherman visibility requires both a flat detected-range bonus and a per-frame sensor-fader override.
- Visiting Fisherman time advances only while the player is elsewhere. Rendering and sound also stop when the player is outside the location.
- Standing Fisherman markers are map-only and have no sensor profile. Eligible core-system postings are known from the start; other inhabited-system postings remain known after discovery. The fleet stays loaded throughout the player's stay in-system, regardless of viewport or sensor visibility. It unloads only after the player leaves; tutorial reservations and battles delay that unloading. It returns at the saved marker position. Shared identity, stock, restock timers and chart requests live outside the unloaded fleet.
- `FleetMapVisibility` hides local fleet proxies at every visibility level above `NONE`, including sensor contacts, and restores them at `NONE`. Standing postings retain the same token off-map and restore it when the boat detaches. The attached boat's existing callback maintains its posting even for a visiting/tutorial-temporary boat; temporary marker owners also restore hidden tokens. See [UI map lifetime](UI.md#fleet-map-markers).
- Visiting/procgen and Imposter markers remain temporary: departure removes the marker, and the existing fleet departure/expiry rules still apply. Reconciliation preserves standing markers and removes duplicate markers.
- Tutorial and chart-request return navigation can target a standing marker when its boat is unloaded; local marker autopilot redirects to the fleet after spawning.
- The visitor shelf restocks from each sale date, not a global monthly tick. Chart-request completion is the only way to increase shelf width.
- `FishingIntro.point()` is idempotent and can be reached from the wreck, stranded crewman, bar referral, Fisherman interception, or a direct hail. Recovered property takes origin precedence, then rescued crew, then recorded market.
- `FishingIntro.getAsks()` accepts the first lesson's drone catch from any pond in the assigned system after assignment. One `FishRequirement.anyOf` combines source pond IDs, including the saved marked pond. Catch storage, cargo counts and hand-in share this requirement, and a qualifying catch releases the original pond mark. Later lessons keep their own requirements.
- `CatchReleaseRatingQuestions` offers trawler directions, a fishing question and a return to the bar. Both answers rebuild the menu; the return option is always available.
- `FishingIntro.giveOutfitter()` grants the Spool Governor schematic with the second tutorial hand-in. `giveOutfitterSchematic()` also supplies the skip path, reuses `FishReward` receipts, and skips known/owned equipment. The schematic exposes the Equipment tab's drone-core shelf; purchasing and fitting remain separate.
- The returning-player skip is available only before the R.O.D. lesson begins. The saved Luna setting controls future campaigns and remains disabled when the player turns it off.
- Tutorial single-target protection advances only when the requested species could naturally spawn at the current location with the required implement. The count carries between valid locations and pauses elsewhere.
- No bar, local fleet, or distress fleet job may appear before `FishingIntro.isOpenForWork()` or tutorial completion as appropriate. Equipment requirements are limited to gear the player owns.

### Distress and reusable framework boundaries

- The distress framework follows the live vanilla `NearbyEventsEvent` timer. If the bridge cannot read that state, it fails closed instead of starting an independent scheduler.
- The framework owns entity, route, breadcrumb intel, reservations, and dialogue trigger only. Providers own eligibility, content, quest creation, expiry, and resolution.
- Distress CSV IDs must be namespaced so several mods can merge rows and providers without collisions.
- The framework yields whenever vanilla creates a distress call and shares system reservations to avoid concurrent duplicate events.
- Skillshot and distress registration must remain idempotent and save-safe. Transient listeners are rebuilt on load and skillshot targeting resets before save.


### Shared cross-file constraints

- `FishingTaboo` is the only Church/Path exclusion list used by fishing jobs, buyers and Fishermen.
- Custom entity `init()` calls `super.init()`; do not shadow the inherited entity field.
- `CampaignDistortionRenderer` owns campaign storage, screen copy and viewport conversion; GraphicsLib's combat-engine storage and viewport helpers are unavailable here.
- Quest range-data rewards never stand alone: add credits, or a second chart on no-credit jobs. Unknown range values are 5/10/15/20k by rarity. Fixed rewards consume the budget first.
- Parley Fish claims an exact free local rupture; qualifying proof releases its mark, losing proof restores it; map targets rupture until proof, client afterward. Claim Assay and Parley timestamp requirements at acceptance.
- Alternate fleet clients preserve the demand but replace reward/contact/hand-in/intel/thanks state. Mutiny crew success transfers the flagship to a bosun-led fleet; captain success leaves ten harvested organs.
- Every job's post-acceptance requirement is rolled once and applies across rounds. Pass selected specimens to payout before generating the next request.
- `QuestPond.sweep` repairs stale claims; no new camp fleet or claim before acceptance.
- Tutorial skip is allowed only in UNSTARTED/POINTED. It shares dev-skip grants, but only normal completion enables and saves the setting. Mirror its saved object into LunaLib's cache without a global backend reload.
- Crablobab backdrop offers persist per market until sold, then wait 60 days; exclude owned scenes and gate rotation on conservatory-plan ownership.

## Dead or dormant

| Component | State |
|---|---|
| `campaign/ponds/entities/StenciledFishingPondEntityPlugin` | Dead. Ponds are terrain now. |
| `testing/DevShortcut` | Registered, but active only in dev mode. |
| `testing/TestStencilRenderer` | Not registered. |
| `rendering/spiral/BlackHoleSpiralWarp` | Deprecated test effect. Not installed by `ModPlugin`; its settings do not enable it. |
| `campaign/ponds/renderer/PondHoleRenderer` | Dormant while `PondConstants.POND_HOLE_LOOK` selects the shader version. |
