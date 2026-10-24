# Architecture

Technical routing for the current implementation. Java paths below are relative to `jars/src/catchrelease/`; data paths are repository-relative. Search the named owner before adding another implementation.

| Reference | Scope |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | Workflow, build gate and document upkeep |
| [RELEASE.md](RELEASE.md) | Version numbers, release jar, package contents and publishing |
| [CHANGELOG.md](CHANGELOG.md) | Change-note format and upkeep for every implementation task |
| [DIALOGUE.md](DIALOGUE.md) | All player-facing text, rules or Java: workflow, shared text presentation and dialogue flow; prose constraints in LORE.md |
| [LORE.md](LORE.md) | Setting facts, knowledge limits, character voices and source-labelled prose examples |
| [Fisherman follow-up chain](FISHERMAN_CHAIN.md) | Approved design for the post-tutorial quest chain and ship allocation; not yet implemented |
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
| `ModPlugin.onApplicationLoad()` | Player settings; `combat/salvage/FighterStoreRefitButton` registered once with LunaLib's static `LunaRefitManager` |
| `ModPlugin.pickShipAI()` | `combat/salvage/LyneDroneAI` for fighters tagged `catchrelease_lyne_drone`, at `MOD_SPECIFIC` |
| `ModPlugin.pickWeaponAutofireAI()` | `combat/tug/HarpoonAutofireAI` for `catchrelease_harpoon`, at `MOD_SPECIFIC` |
| `ModPlugin.onCodexDataGenerated()` | `FishCodex`, only while Codex data is generated |
| `ModPlugin.onGameLoad()` | Idempotent script/listener registration and current-state cleanup; order below |
| `ModPlugin.beforeGameSave()` | Reset transient skillshot targeting; remove False Dawn's owned coronal flares before serialization |
| `data/campaign/fish.csv` | Species; `FishSpecLoader`. The `icon` column is a literal asset path loaded by `SpriteLoader`, shared by cargo, Codex, map, shop and aquarium; no `settings.json` sprite registration. Fish PNG filenames use lowercase with underscores; paths must match case exactly. Match `spriteDirection` to replacement art for aquarium swimming. |
| `data/campaign/abilities.csv` | catchrelease_searchlights, catchrelease_rod, catchrelease_harpoon |
| `data/config/settings.json` | `catchrelease.dialogue.rules` command package and sprites; black-hole warp settings belong to the deprecated test below |
| `data/config/sounds.json` | Sound registry; callers in abilities and `FishConstants` |
| `data/config/LunaSettings.csv` | Nine feature tabs; defaults and bounds mirror `config/Setting`. Runtime values and preset actions belong to `config/PlayerSettings`. |
| `data/config/LunaSettingsConfig.json` | LunaLib mod-list icon; `catchrelease.iconPath` uses the existing `graphics/catchrelease/icon/small_icon_catchrelease.png` hook. |
| `data/campaign/bar_events.csv` | 11 ordinary FishJob subclasses + 3 camp jobs; Crablobab and the tutorial spacer use AddBarEvents rules |
| `data/campaign/distress_calls.csv` | Merged, namespaced specs; # IDs disabled; `CatchReleaseDistressProvider` |
| `data/campaign/rules.csv` | Dialogue and type-selected fleet quest/intel text; Java supplies mechanics/state |
| `data/world/factions/default_ranks.json` | Contact roles |
| `data/campaign/terrain.json` | Pond/coherence terrain; ponds are not custom entities |
| `data/config/custom_entities.json` | Motes, drones, harpoons, legendary props and map proxies |
| `data/campaign/special_items.csv` | Fish cargo; the T1 compass (`catchrelease_compass`, plain `BaseSpecialItemPlugin`, `TreasureRumour.COMPASS`) |
| `data/campaign/industries.csv` | Breach Conservatory |
| `data/hulls/ship_data.csv`, `data/hulls/catchrelease_*_ship.ship`, `data/hulls/skins/catchrelease_rod_ship_early.skin`, `data/hullmods/hull_mods.csv`, `data/strings/descriptions.csv` | Fishing-ship hulls `catchrelease_harpoon_ship` (Stigmata, midline destroyer: built-in Tug Harpoon on `WS0001` and Tug Drive Field hullmod (see [`combat/tug`](#combattug)), one bay, Haul Drive system `catchrelease_tug_drive`), `catchrelease_rod_ship` (Reliquary, midline carrier cruiser: two bays holding its two built-in LYNE wings (see [`combat/salvage`](#combatsalvage)), Salvage Launch system `catchrelease_salvage_launch`; its skin `catchrelease_rod_ship_early` is the early Reliquary, identical in combat, with its own tags and the base hull's description; nothing grants it yet) and `catchrelease_lamp_ship` (Monstrance, still a full copy of vanilla `nebula` until its art exists). Sprites are under `graphics/catchrelease/ships`; the harpoon weapon sprite is `graphics/catchrelease/weapons/harpoon_harpoon.png`. The combat design is in [COMBAT_SHIPS.md](COMBAT_SHIPS.md). Vanilla's blueprint-package tags (`base_bp`, `merc`, `ind`) are left out so no faction builds or sells them; `no_drop`, `no_sell`, `no_bp_drop` and the `HIDE_IN_CODEX` hint keep them out of loot, markets and the Codex. Only quests grant them (`FishReward.ship`). The real hulls replace them under the same ids; see [FISHERMAN_CHAIN.md](FISHERMAN_CHAIN.md#ships). |
| `data/campaign/backdrops.csv` | Aquarium scenes and ownership source |
| `data/config/UpgradeData.csv` -> `memory/upgrades/` | Current stat definitions, saved levels and runtime values |

Load order in `ModPlugin`: player settings -> pond listeners -> buried motes -> charges -> harpooned FID selector -> offence responses -> local fleet offers -> expedition/prospecting routes -> visiting Fishermen -> standing Fishermen -> chart upkeep -> tutorial/wreck/bar referral/interception -> Fisherman chain, fishing ships and the refit fighter store (`RefitStoreView`) -> sold-fish restock -> colony options -> aquarium -> coherence cache -> monthly ranges (including initial assessment) -> legendary cleanup -> Imposter cleanup -> distress provider/framework -> skillshot -> map filter -> intel planet panel -> coherence overlay -> stale pond claims -> dev shortcut.

### Player settings

`config/PlayerSettings` installs a LunaLib listener on application start and reloads on game load.
`Setting` owns IDs, defaults, bounds and typed access. `data/config/LunaSettings.csv` supplies the
nine tabs; `fieldDescription` owns their setting help, separate from IDs, defaults and bounds.
Runtime callers read the cached values, not the CSV or LunaLib each frame.
`reload()` handles checked JSON errors from both the normalized-value writes and persistence,
plus save I/O errors; failures are logged without skipping runtime preferences or Luna UI synchronization.

Overall and hunt presets are one-shot actions. Applying one sets its sliders, saves them and
returns the action to Keep current. Custom is a computed status, not a preset. Overall presets
cover catching and hunts; they leave equipment, prices, rewards, input mode and presentation alone.
Marker presets set the four marker switches. [LunaLib UI synchronization](UI.md#lunalib-settings)
prevents a retained action from overwriting later manual edits.
The same [LunaLib UI contract](UI.md#lunalib-settings) defines CSV paragraph escaping;
`tools/PlayerSettingsChecks` validates displayed formats as well as defaults and presets.

| Area | Runtime owners |
|---|---|
| Catching | `FishingMinigame` snapshots physics per attempt; `MinigameTreasure` snapshots chest timing. Zero-progress failure delay and opening grace use real, unpaused seconds, independent of game speed. Recovery resets the failure timer; no-loss overrides it. |
| Equipment | `UpgradeManager.getValue()` scales the upgraded value without changing owned levels. Drone return speed, launch spacing, lamp activation and Needle hints use their existing owners. `HARPOON_ROPE_HULL_BREAK` is read by each combat `Tether` per frame; `LYNE_WRECK_CHANCE` by `WreckTracker` as each enemy fighter is first damaged; `LYNE_STORE_CAP` by `FighterStore` on each add and room check, keeping fighters already aboard. |
| Hunts | `FishSpec`, haunt modules and `LegendaryShields` consume movement, hazard, recovery and species-specific settings. Easier hunting does not reduce rewards. |
| Quests | `QuestDuration` and `QuestRewards` scale newly generated offers; accepted terms stay fixed. The listener scales this mod's bar weights from captured base weights. Fleet and distress providers scale only their own natural rolls. |
| Markers | `FleetQuest.ensureMarked()`, `FleetMapVisibility` and `QuestPond.refreshMarkers()` hide presentation only. Quest memory, claims, routing and completion stay active. The settings callback refreshes loaded owners without a polling script. |
| Exploration | `PondCreator` stores each system's target count on first initialization; existing systems are not reseeded by slider edits. Ordinary mote population and replenishment exclude legendary residency. `LAMP_SURFACING` is read by each buried mote's own `advance`, and `LAMP_FISH_SPEED` and `LAMP_STRAIGHT_SWIM` by each lamp-surfaced fish's movement step, so all three apply at once. |
| Outfitter | `ShopPricing`, `ShopTabPlugin` and `FishermanShelf` scale prices, retaining fish minima and saved purchase receipts. Stock is permanent; there is no restock setting. Upgrade fish-count scaling uses a copy of the saved base requirement, so refreshes cannot compound it. Quest valuation keeps base credit prices. |
| Presentation | Aquarium and campaign renderers scale their own effects; shared fish icons are untouched. `FishingSounds` applies mod/group volumes to existing sound calls. Music ducking restores the previous music setting on dialogue close. |

Defaults preserve normal gameplay. Active minigames keep their initial physics; cached equipment
stats refresh on their normal activation. Renderer/audio preferences apply on their next update.
New quest terms, rumors and uninitialized systems use the values at creation. Important-only
notifications suppress optional rumor notices, not required quest grants or completion notices.
The standalone difficulty tuner remains independent of player settings.

### Build and authoring tools

IntelliJ classes: `out/production/catchrelease`; artifact: `jars/catchrelease.jar`. Keep compiler output outside `jars/`. Build procedure: [CLAUDE.md](../CLAUDE.md#building).

`tools/ReleaseBuild` compiles into a fresh `out/release-build-*/classes` directory
and writes the tracked runtime-only `jars/release/catchrelease.jar`. Its source
hash covers runtime Java, the builder and the bundled compile libraries.
`tools/ReleaseBuildChecks` tests version/changelog parsing, metadata stamping,
archive filtering, freshness and reproducibility without game dependencies.
`.github/workflows/release.yml` verifies that jar and packages allowlisted
tracked resources into `out/release/`; only tag pushes publish.
Packaging maps the jar to `Catch.Release/jars/catchrelease.jar` and stamps
both version files from the tag. [Release procedure and contents](RELEASE.md).

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

`tools/MantaHauntChecks` verifies formation spacing, stationary slots at jitter onset,
instant reorientation at jitter end, blackout duration/interval, held-target safety,
per-lamp flicker, overlapping impressions and cleanup. `tools/MantaMinigameChecks`
compares decoy motion/tells against the real model at 30/60/144 Hz and checks
real-only scoring, treasure isolation and restart/end behavior. These are standalone
checks with API proxies; they do not test OpenGL output or engine callback ordering.
`tools/LegendaryEscapeChecks` exercises the production Moray haunt without projectile
spawns or player ability changes, immediate haunt-start escapes,
repeat-dash gates, dive lock/release in both callback orders, Moray swim-speed bounds,
ordinary-fish movement isolation, failure dispatch,
four-turn shell-game bursts at 30/60/144 Hz,
repeated-loss decoy caps, emergency dash duration,
slipstream roll-up/fade lifetime and cleanup, immediate manta swaps, fixed hit-position
labels and shield colours. It also checks corona-safe movement segments,
invalid-position recovery, overlapping and companion stars, long-frame dashes,
buried movement, formation clearance, and the False Dawn/ordinary-fish exclusions.
`tools/LonglinerHauntChecks` covers segmented-shield
geometry, swept harpoon hits through gaps, explosive adjacency, shield persistence,
decoy start/stop at section hits, lamp-bound shield visibility through dives,
independent swirling movement, separation/replenishment, opposite rotation and encounter cleanup.
`tools/LonglinerCleanupChecks` covers immediate relocation on failure or a missing
revealed mote, held-catch deferral, no-alternative suppression, boat/light teardown,
stale bookings and exclusion of real Fishermen.
`tools/LegendaryLifetimeChecks` checks repeated swim arrivals, dash expiry exclusions,
distance-based sightings, departure cleanup and preservation of the host/relocation ledger.

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

`AddFish <fish id or name> [quantity] [quality] [coherence]` adds one bundle containing the requested number of separately rolled specimens. Exact IDs or full display names are case-insensitive; multi-word names may be quoted or unquoted. The longest exact prefix identifies the species, followed by a positive integer quantity (default 1) and optional finite quality/coherence values in `[0,1]`. Quality interpolates both length and weight directly; coherence is stored as `1 - coherence`. Omitted quality uses a separate normal size roll per fish; omitted coherence uses the species' aberration midpoint. ID/name autocomplete is shared with `SpawnFish`; only `SpawnFish` uses the fuzzy matcher.

## Data identity

| Stable ID | Display name |
|---|---|
| `mackerel` | Moiré Mackerel |
| `cutout` | Volley Dolphin |
| `hull_grazer` | Hull Grazer |
| `longliner` | The Imposter |
| `miscount` | Relic Crab |

Display names and internal IDs are separate. `LonglinerDecoy` and Longliner-named memory, sound and option keys still identify The Imposter. Asset status is recorded beside each species row (`placeholder art`). The `desc` column loads into `FishSpec.desc`, shared by `FishItemTooltips` and `FishCodexEntry`. It contains quoted sailors' yarns; their events and hazards do not add campaign mechanics. Description edits leave species identifiers, balance, assets and facing values unchanged. Prose guidance belongs in [LORE.md](LORE.md#fish-descriptions).

Both description displays pass the text to `addPara` verbatim. Spoken quotation marks belong in the `desc` value, escaped as doubled quotes in the CSV.

The Imposter's active `longliner` fish row and nine matching rules supply its spawn, Fisherman questions, rumor intel and bar reports. It retains the boat disguise and has no range-data entry.

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
| `FishLog.java` | Persistent per-species discovery and record data. `record` also stores the latest catch's `lastLength`; 0 means none logged yet. |
| `Aberration.java` | Caches ordinary aberration from the strongest destabilizer minus the strongest colony field, then applies temporary system rumors. `naturalAt` bypasses rumors for target selection. |
| `FishRanges.java` | Authoritative current range test, including the fifteen-species system cap. |

### `campaign/fish/jobs`

| File | Owner / connection |
|---|---|
| `FishJob.java` | Mission base -> FishRequirement/FishCurrency -> picker -> QuestRewards -> intel. Shared acceptance-time restriction; progress compares capped displayed counts; selected specimens reach rewards before the next round rolls. The `rewardTooltip <option id>` action lists the current rewards in an option tooltip, in the intel's reward-line format; Fight Night's take-the-fee option uses it. |
| `ChefJob.java` | Three ingredient types and a saved dish; supplies `$catchreleaseDish` to the chef's rules dialogue. |
| `TuberJob.java` | Two paid deliveries: a Fine Uncommon/Rare specimen, then a low-coherence catch. Rules disclose both before acceptance; follow-up and camera questions return to the offer without changing mission state. |
| `DemandScore.java` | Scores actual requirements (unmodified Common = 10), including diminishing extra specimens and cheapest anyOf branch; supplies ambition/count helpers and EASY/MEDIUM/HARD/SEVERE reward tiers. |
| `QuestRewards.java` | Shared reward budget: score × 600 credits × 0.75–1.35; fixed rewards, tier-gated extras, remaining guaranteed credits, saved hand-in value multiplier. Later cash stages guarantee ≥20% total and ≥25% base-credit growth, each ≥2,000; multiplier cannot decrease. |
| `QuestDuration.java` | Satisfiability gate and deadline: nearest valid range + round-trip fleet travel + work, rounded to 30/60/90/120/180 days or unlimited; +30 for required Rare/Epic and +30 for post-acceptance catch. |
| `FishHandoffPicker.java` | Validates non-overlapping specimen assignments; autoSelect takes the minimum valid set, worst first. Invalid confirmation reopens next frame after modal release; preserves container ID when repacking. |
| `FishReward.java` | Reward values, grants and actual grant results; quest credits use the exact selected specimens. Known range grants convert to stored credit fallback. Equipment rewards unlock schematics, not purchased tiers or modules. `ship(hullId)` adds the hull's empty `_Hull` variant to the player fleet, named by `pickShipName` and at maximum CR; its receipt is the vanilla full designation. It has no `QuestRewards.valueOf` case because only quests grant it. |
| `FishRewardRoller.java` | Tier/ownership/active-job exclusions; `Tackle.questOnly` modules never roll; distinct chart reservations; 3–10× fish-value multiplier (10× = 0.5%). Backdrop rolls require conservatory plans. |
| `QuestPond.java` | Claims ponds by a set of job IDs, adds vanilla mission importance, plants identified quest motes, and releases claims and motes. `getPlanted` finds a planter's surfaced and buried motes in one location; `sweep` and `clearMotes` only reach surfaced ones. |

`FishJob` supplies complete offer/remaining deadline sentences as
`$catchreleaseDeadline` and `$catchreleaseDeadlineLeft`; unlimited jobs say so
explicitly in dialogue and intel.
Intel names the giver's market and star system in the description, the list bullets and the next step; the bullet reads "Return to" once the asks are satisfied. A camp job's map target is the camper, then the rupture until the proof is aboard after clearing, then the giver.
Camp jobs register as a `FleetEventListener` on their camper: when the player's side wins a battle with it, `CampedSpot.CLEARED_FLAG` is set and the next advance sends the fleet home, the same as a bribe or scare. A beaten camp no longer returns to its orbit.
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
| `FleetQuestMapIcon.java` | Accepted-job proxy, retained in fleet memory while hidden. `PINNED_FLAG` keeps it for a fleet no `FleetQuest` owns (`CaptainsFleet`), whose owner adds, syncs and removes it. Existing mission advance restores it at visibility `NONE`; `FleetMapVisibility` hides it on radar contact. Release clears hidden and visible tokens. Asset path matches `settings.json`'s `fleet_quest_map_icon`; its yellow `custom_entities.json` icon colour also supplies the accepted fleet-side marker tint. |
| `FleetQuestSpawner.java` | One eligible local offer/wanted quest; tutorial gate, 7% checks, 45-day cooldown (QUIET_SHIP: 120). Picks among offers that can spawn in the system, weighted (PARLEY_FISH 0.25); a dedicated-giver offer is only in the pick while one of its fleets is present. Uses scavengers except INTERMENT/MUTINY_POT/EXHIBIT trade convoys. Dedicated givers (`fitsDedicatedGiver`): FOLLOWER Hegemony and STATE_DINNER Diktat `trade`/`smallTrader` convoys; CLAIM_ASSAY Tri-Tachyon prospecting fleets or Nexerelin `exerelinMiningFleet`; MANDATE science expeditions; PARLEY_FISH patrols whose source market has `$core_pirateBase` (procgen bases only), outside `theme_core`. Every giver must be non-hostile and must not already offer a quest: never a fleet or commander marked important (`MemFlags.ENTITY_MISSION_IMPORTANT`, set by `Misc.makeImportant`), and never the Fisherman chain's captain (`CaptainsFleet.isCaptain`), whose fishing-job marker replaces vanilla importance. Low-coherence premises also gate the test path. |
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
| `AquariumFishShader.java` | Aquarium-only filter around that mesh; lazy shared shader from `data/catchrelease/shaders/aquarium_fish_*`. Per-specimen coherence controls digital displacement, pixel breakup and dropouts, without chromatic separation. No sprite mutation or screen capture; drawing contracts are in [UI.md](UI.md#portraits-and-sprites). |
| `Backdrops.java` | Separates campaign-wide backdrop ownership from the scene selected by each conservatory. |

`tools/AquariumShaderCheck` checks the strength curve outside the game. Add `--gl`
and the game's native library directory to `java.library.path` for off-screen
GLSL compilation and rendering checks: digital strength, neutral colour channels,
transparency, texture-region bounds, mirroring, fade alpha, disabled effects,
successive fish draws and restoration of the previous program.
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
| `FishermanSurveyDialog.java` | Range-data shop. Each purchase pushes a session receipt; undo pops them newest first, restoring the hold's fish, relocking the chart and returning it to its shelf slot and restock booking, like the Outfitter's session undo. |
| `FishermanQuest.java` | Saved chart offer and exact identified catch. Selects species/system/source together and checks implement eligibility before offering the request. FishRequirement/FishCurrency govern progress, picker and spending; completion widens the shelf and starts the configured work cooldown (90 days by default). Decline/reopen does not reroll. |
| `chain/TreasureRumour.java` | T1. `prepareOffer` saves a target system 2–10 LY from the player (else the nearest) that hosts a non-legendary lamp species and no rumour, keeping it while it still qualifies; `start` publishes the held `LOOT` rumour there, moves the chain to `T1_ACTIVE` and queues `CompassIntel`, one intel for the compass errand whose title, lines and map target follow whether the compass is aboard; it ends itself outside `T1_ACTIVE`. `CompassWatcher`, a transient `ColonyInteractionListener` registered through `FishermanChain.register` on load and at `start` only while T1 is active, records the market of the compass's last sale; a free-transfer submarket counts as storage and a purchase clears the record. `checkLost` answers "I lost it" once: the recorded sale (`SOLD`), else a search of every free-transfer submarket on player-owned markets or markets with storage access (`STORED`), else `GONE`. `complete` takes the compass from cargo if aboard, unregisters the watcher, moves the chain to `T1_LENS` and shows the reward receipts: the Glitter Lens schematic unless known or owned, `PaymentSpecimens` for its saved fish payment (stowed, not landed), and its credit price after `Setting.EQUIPMENT_COST`; credits alone when no specimens can be made, and only credits when the lens is already owned. The Fisherman's follow-up row runs `t1LensIntel` (`showLensIntel`), which adds `LensIntel` (buy the lens; ends outside `T1_LENS`) at once through `FishIntelNotifications.addNow`, so its card follows his line. `checkLensBought`, run by `FishShopDialog.close` (both hosts; ownership is final once a session's undo is gone) and at the end of `complete`, releases the rumour for `EXTENSION_DAYS` (60) and moves the chain to `T1_BOUGHT`; the Fisherman's remark (`t1Done`) closes T1 at `T1_DONE`. Owns the compass id. |
| `chain/CaptainsFleet.java` | T2. `prepareOffer` saves the nearest system with no markets or colonies (`OuterReaches.isPopulated`), a primary star that is not a pulsar or black hole, no nebula, no rumour and no `THEME_UNSAFE` tag (Remnant bases and similar), plus an `EPIC` lamp species in range there (`FishRanges.matches`, weighted by `spawnWeight`; never a legendary); it keeps both while they still qualify, and clears them when nothing qualifies, so the offer falls back to its "nobody's asking" row. `start` (from `T1_DONE`) spawns the captain's independent `SCAVENGER_SMALL` fleet 3,000 units past the star's surface, clear of every vanilla corona. It circles a token on a fixed orbit there (`Saved.t2Hold`) on `ORBIT_PASSIVE`: orbiting the star itself would park it just above the surface. The token is removed with the fleet. `start` moves the chain to `T2_MEET` and queues `CaptainIntel`. The fleet carries `$catchrelease_t2Captain`, the vanilla non-hostile, no-jump, not-sidetracked and ignore/ignored-by-other-fleets flags and no vanilla importance: `syncMarker` gives it the fishing-job marker (`FleetMarkerRenderer` with `fleet_quest_map_icon`), light cyan at `T2_MEET` and the accepted yellow once met, under the same marker settings as fishing fleets, re-added on load and cleared at release; its commander is a fresh independent captain with portrait `portrait30`, also set as the flagship's captain. `Saved.t2Fleet` holds it. `Movement`, a transient `CurrentLocationChangedListener` registered through `FishermanChain.register` while the captain is waiting (`T2_MEET` to `T2_REPORT`), respawns a lost fleet when the player enters the system; a load outside the system does the same. Leaving the system at `T2_HUNT` with no awake epic re-arms (`rearm`: the epic fades, back to `T2_LAMPS`); an awake epic is left to `EpicHunt.DepartureCleanup`, whose reset ends the hunt. `releaseFleet` clears the flags and sends it to despawn at the nearest jump point (vanilla return-to-source orders when there is none); it never despawns the fleet inside the open comm dialog. `CaptainIntel` covers `T2_MEET` to `T2_DEBRIEF`; its lines and map target follow the stage. Once met (`T2_LAMPS` to `T2_REPORT`), `syncMapIcon` pins the fleet with `FleetQuestMapIcon.PINNED_FLAG` and keeps the shared accepted-job proxy; it runs at `beginSearch`, on load, on a location change, on a respawn, on a settings change and, in his system, from `LampTimer.advance`, since nothing restores a proxy once radar contact is lost. `releaseFleet` unpins it. `t2Search` moves the chain to `T2_LAMPS`. `LampTimer`, held transiently by the player's `SearchlightAbilityPlugin` and advanced from its `advance` (no callback reports lamp time; the ability already runs every unpaused frame), counts while the fleet is in the target system within `LAMP_RANGE` (6,000 units) of the star's surface with a runtime-current light; any break, a load or another stage resets it. After `LAMP_SECONDS` (15) `placeEpic` adds a buried mote of the saved species at `Searchlight.getPointAhead(LEAD_SECONDS)` of the first lit beam (2 seconds of unlocked sweep and sine offset plus the fleet's current velocity, ignoring the turn at the arc's end), moved clear of stars by `LegendaryStarAvoidance.place`, marks it with `QuestPond.markPlanted(mote, PLANTER)` and moves the chain to `T2_HUNT`. In `T2_HUNT` the timer instead re-arms after `VANISH_GRACE` (5) seconds in the system with no mote of that planter, which covers a drone-surfaced epic that fades unwoken. `Hunt`, a transient `QuestEpicListener` registered at `T2_LAMPS` and `T2_HUNT`, takes the first report for `PLANTER`: a catch, or a reset, lost minigame or blast; `resolve` saves `t2Caught`, fades what is left of the epic (buried motes too, which `QuestPond.sweep` never reaches) and moves the chain to `T2_REPORT`. `ModPlugin.sweepPondClaims` keeps `PLANTER` live while searching. Planter ids starting `catchrelease_chain` (`FishermanChain.isPlanter`) are not chart requests, so `CatchLogIntel` skips its "Landed for a chart request" line for them. `complete` (`t2Complete`, run by the captain's report row after its text) grants the harpoon ship through `FishReward.ship` for either outcome; a catch adds a harpoon module schematic (`pickSchematic`: stocked, unlocked, unknown and not reserved by a job; any such module when no harpoon one is left; never the Needle Sensor) and `RANGE_DATA` (3) range-data entries with `VALUE_PER_FISH` fallbacks. It releases the fleet, moves the chain to `T2_DEBRIEF` and shows the receipts. |
| `chain/WoundedEpic.java` | T3. `prepareOffer` saves the nearest system with no markets or colonies and nothing unsafe (cut off, abyssal, special, hidden, `THEME_UNSAFE`, a rumour, any pulsar), a planet (never a moon) whose 1,000-unit circle stays 2,500 units clear of every star's surface at the closest the two orbits allow, since the epic does not steer round stars, and an `EPIC` lamp species (`CaptainsFleet.isTarget`); never T2's system or species. It keeps them while they still qualify. `spawnEpic` adds a buried mote of that species circling the planet (`BuriedMoteEntityPlugin.setCircle`, `CIRCLE_RADIUS` 1,000) with `EpicHunt.AWAKE_SPEED_KEY` 0.5, marked with `PLANTER`. `Hunt`, a transient `QuestEpicListener` registered at `T3_HUNT`, sends the epic back to the planet on a reset, lost minigame or blast (`returnToPlanet`: fade, respawn, campaign message), so T3 cannot fail; a catch moves the chain to `T3_CAUGHT`. `LampsOff`, a transient `BaseCampaignEventListener` registered at `T3_HUNT`, does the same when the player's Breach Lights go off (`reportPlayerDeactivatedAbility`) while an awake, unheld epic is in the player's location; a deactivation caused by leaving the system finds none there and leaves the reset to `EpicHunt`. `Arrival` (`CurrentLocationChangedListener`) and `register` on load respawn a missing epic and clear wild buried motes of its species from the system (`clearWild`); while hunting, `isReserved` keeps `BuriedMoteSpawner.spawn` from adding more, because a wild one wakes at full speed and its sensor ripples match the quest epic's; `register` clears leftovers outside `T3_HUNT`. `ModPlugin.sweepPondClaims` keeps `PLANTER` live while hunting. `start` (`t3Accept`, from `T2_SENSOR_BOUGHT`) re-checks the saved target, moves the chain to `T3_HUNT`, registers (which spawns the epic) and queues `WoundedIntel`, one intel for T3 whose lines and map target (the planet) follow the stage. `complete` (`t3Complete`, run by the hand-in row after its text) grants the `ship_bp` item for `FishingShips.Ship.LAMP` through `FishReward.shipBlueprint` and range data on an `EPIC` the player has neither caught nor charted (`pickRangeData`); with none left, `RANGE_DATA_CREDITS` (10,000) instead, then moves the chain to `T3_BUILD`. `checkBuilt`, run at the end of every `FishingShips.reconcile` and of `complete`, moves on to `T3_BUILT` once the lamp ship's rig has its second slot, whatever brought the ship into the fleet; custom production delivers to storage, and taking the ship out is a storage transfer `FishingShips.Listener` already reports. `WoundedIntel` covers the build and report steps too. The closing conversation runs `t3Done` (`finish`), which starts T4 (`LegendaryHunt.start`). |
| `chain/SensorBuild.java` | T2's follow-up. `give` (`t2Debrief`, run after the Fisherman's debrief text) grants the Needle Sensor schematic unless `ShopSchematics.has` already covers it, moves the chain from `T2_DEBRIEF` to `T2_SENSOR`, shows the receipt and, while the sensor is still to be bought, adds it to the Outfitter shopping list (`FishermanChain.listWantedPurchase`); `checkBought`, run by `FishShopDialog.close` beside `TreasureRumour.checkLensBought` and at the end of `give`, moves on to `T2_SENSOR_BOUGHT` once the sensor is owned, at once when it already is. `SensorIntel`, added at once by `give` (`FishIntelNotifications.addNow`, so its card follows the Fisherman's line), asks for the purchase at `T2_SENSOR` and sends the player back to the nearest boat at `T2_SENSOR_BOUGHT`; it ends at any other stage. T3 starts from `T2_SENSOR_BOUGHT`. |
| `chain/LegendaryHunt.java` | T4 and the legendary list. `SPACER_NAMES` gives each bar telling's name; the three tellings that name nothing use a name from their story (The Many, The Burning Lie, The Abyss). `recordTold` keeps the latest told name per legendary in `FishermanChain.Saved.toldNames`, from the first rumour on. `start` (from T3's closing conversation) moves the chain to `T4_LISTEN` and queues `HuntIntel`, one intel from T4 to the last legendary whose title and body follow the stage. `noteCaught`, called by `LegendaryChases.noteCaught` on every legendary catch, moves `T4_LISTEN` to `T4_LANDED`; `start` runs the same check, so a legendary landed earlier completes T4 at once. `getLegendaries` counts the `LEGENDARY` rows with `spawnWeight > 0` in `fish.csv` order. `complete` (`t4Complete`, run by the Fisherman's row at `T4_LANDED` after its text) grants the ROD ship through `FishReward.ship`, unannounced until then, and moves the chain to `HUNTING`. There `HuntIntel` lists every legendary: the real name in the legendary colour once caught, else the latest told name, else question marks, with a caught count. When the last one is caught (`checkAllCaught`, on each catch and after `complete`) the chain moves to `HUNT_DONE` and the intel shows its final state for its end delay; it is added as a sector script then, because `BaseIntelPlugin` only counts the delay down in `advance`, which the IntelManager never calls. What concluding brings is still open. |
| `chain/FishermanChain.java` | Post-tutorial chain ([FISHERMAN_CHAIN.md](FISHERMAN_CHAIN.md)). One saved `Saved` (stage plus each quest's targets and counters) under sector persistent data `$catchrelease_fisherChain`. `writeTokens`, called from `CatchReleaseCMD tokens`, writes one Boolean per offerable or active stage and the display strings; rows never read the saved object directly. `getWantedPurchase` names the module a step waits for the player to buy (the Glitter Lens at `T1_LENS`, the Needle Sensor at `T2_SENSOR`): `$catchreleaseChainOutfitterWanted` highlights the Outfitter option, and `listWantedPurchase`, run when T1 and T2 reach those steps, puts it on the Outfitter shopping list (`ShopMarks.mark`; `ShopEntry.grant` removes it on purchase). T1 to T3 save their system as vanilla names it, by `getNameWithLowercaseType` ("Foo star system", written with "the"); the bare name reads as the star's. |
| `FishermanIdentity.java` | Stores the shared `PersonAPI` and selects one of five coherence portraits immediately before a hail. |
| `FishRumors.java` | Monthly leads: eight effects, four effect pairs, and one legendary-sighting slot unlocked by `FishingIntro.isComplete() && FishermanQuest.getRound() > 0`. Graduation grants no lead of its own: finishing or skipping the tutorial opens T1 (`FishermanChain.offerFirst`), whose `LOOT` rumour is published held (`publishHeld`: no lifetime runs, no stale bullet) until `release` gives it a normal lifetime from that day. Legendary reports snapshot an uncaught fish's actual host/residency; they neither transplant fish nor grant range data. A legendary rumour carries `Saved.toldName`, the name its teller used: the Fisherman's leads (`create`) use the real name, the bar spacer's reports (`RatingBarEvent.prepareReport`) `LegendaryHunt.getSpacerName`. `publish`, the single path every rumour takes, records it through `LegendaryHunt.recordTold`. `getStrangerDisplayName` shows a legendary rumour under its told name until that legendary is caught, so its intel title and text use the teller's name; the description colours it in the legendary colour itself, since a told name is not a species name. |

`FishingMinigameDialogPlugin` takes rumor effects from the catch anchor. Size bias joins the specimen roll; `FishingMinigame` snapshots movement, bycatch chance and bycatch rarity for that retrieval. Size, movement and treasure-rarity boosts exclude legendaries. Tuning stays in `FishermanConstants`.

Each ordinary `FishRumors.Kind` has one matching `CatchReleaseRumorText` row; legendary sightings select a species-specific row with `$catchreleaseRumorLegendary`, followed by its `_continue` option handler. The second page displays the intel receipt and returns to the Fisherman menu. `CatchReleaseCMD.setRumorTokens()` supplies the dialogue-lifetime `$catchreleaseRumorLegendaryColor` from `FishRarity.LEGENDARY.color` for shortened names; full names use `highlightFishText`. Legendary intel reads `CatchReleaseLegendaryRumorIntel_<speciesId>` from rules. Combined leads use their own passages. Intel expands the saved system/fish values with ordered highlights; ordinary dialogue and the no-lead fallback retain Continue to business.

Rumors snapshot the configured lifetime (default 60 days) from `started`; publication starts the shared 30-day cooldown, including delayed bar reports. `ACTIVE_KEY` stores overlapping leads; ordinary effect rolls avoid occupied systems. Legendary rolls exclude caught fish and species with a live report; no eligible legendary falls back to the ordinary rotation. `getActive()` selects the newest published lead for dialogue; effect getters search all live leads. Legendary leads have no effects and expire on capture, host/residency change, or removed system. They offer autopilot and fishing-map navigation to the saved system without granting range data; expired leads cannot navigate. `LegendaryChases.isCurrentResidency()` only reads the ledger, avoiding rumor -> habitat -> rumor recursion. Host changes increment residency, preventing an old report from reviving after a fish moves away and back. `RumorIntel.shouldRemoveIntel()` shares expiry without reviving ended entries; vanilla calls it for queued and visible entries while unpaused. No new timer script.

### `dialogue/rules`

Use [RULES_AUTHORING.md](RULES_AUTHORING.md) when working on the command bridge or its memory/text bindings, not only when adding CSV rows.

| File | Owner / connection |
|---|---|
| `CatchReleaseCMD.java` | Single rules bridge: temporary tokens, conditions, actions, custom panels, highlights, question paging and fleet teardown. Restores prior rules plugin and options once after panels. |
| `QuestTextHighlights.java` | Splits explicit dialogue highlights into fish/rarity colours and ordinary requirement/reward emphasis. Preserves surrounding terms, displayed casing and repeated occurrences. Text rows supply values in display order before reward cards or other text. |
| `QuestDialogMap.java` | Shared temporary sidebar map for local and remote dialogue targets, matching vanilla mission icons, tags, and colours. `showIntroMap`, `showWorkMap`, `showChainMap` (a Fisherman chain offer's target before its intel exists: `t1`, `t2`, `t3`) and FishJob's `showRemoteMap` action use `show()`; unresolved targets clear the preview. |
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
not the main menu. After that answer, the menu offers `catchrelease_introToldBefore`
directly while it is unread, so no empty return step precedes it; once both are read,
its Continue returns through `catchrelease_introFirstQuestionsBack`, as
`catchrelease_introToldBefore` does. Only `catchrelease_introFirstDone` advances the
first-catch lesson.

The `catchrelease_introCurious` and `catchrelease_introCuriousIntercepted` replies
offer rig delivery or a return to business without accepting it. Both delivery
options route to `catchrelease_intro_rod`, which owns the `giveRod` grant.

### `campaign/fish/minigame`

Lantern Jack keeps two treasures active throughout its minigame, immediately replacing collected or expired ones. Each uses `TreasureRoller`'s normal Common–Epic rarity weights, without rumor rarity boosts. Other fish retain one active treasure and their finite spawn count and delay; other legendaries get three Uncommon–Epic rolls, retaining those tiers' relative weights without rumor rarity boosts. All pickups use the existing win-only reward path; escape loses the loot. `FishingMinigamePanel` draws every active treasure and coalesces simultaneous spawn/pickup sounds. `tools/LanternTreasureChecks` covers rarity sampling, collection, replenishment and restart/end behavior.

T1's golden chest is a `MinigameTreasure` flagged `golden`, not a `TreasureRarity`, so `rollRarity` never draws it. `FishingMinigameDialogPlugin.init` asks `TreasureRumour.isChestQualifying` (chain at `T1_ACTIVE`, compass not yet taken, non-legendary `BREACH_LAMP` anchor in the target system) and stores the answer; when `isChestDue` (30%, certain on the 4th qualifying minigame and every 2nd after) it calls `FishingMinigame.addGoldenChest`, which puts the chest on the track at once and returns any rolled treasure to the queue. `resolveTreasure` grants its compass through `TreasureRumour.awardCompass` instead of a roll; `resolve` counts every qualifying minigame, win or loss, and records a taken chest only on a win. `MinigameTreasure.getColor` and `TreasureAward.label`/`color` carry the quest highlight and the "Golden chest" name to the track, the loot card accent and the catch log.

`TreasureRoller.awardCommon` selects commodities, weapons or fighter LPCs at 60/20/20. Uncommon treasure without a Lifting Rig and empty vanilla drop-group results use the same pool. `tools/TreasureLootChecks` samples actual cargo awards to check the split and quantities.

Uncommon treasure also rolls a 25% bonus range-data entry (`FishConstants.TREASURE_RANGE_DATA_CHANCE`), with or without a Lifting Rig. `TreasureRoller.awardRangeData -> FishRewardRoller.rollLocationData -> FishReward.LocationData.grant -> FishLog` uses the existing unlock path. The shared selector excludes known/caught, legendary, zero-weight and habitatless species. No eligible entry means normal loot only; successive chests cannot duplicate an unlock. The win-only `FishingMinigamePanel.resolveTreasure` owns all grants. `TreasureAward.Item.color` lets the loot result show the species rarity colour; its data icon does not reveal an uncaught portrait. `tools/TreasureRangeDataChecks` covers bonus rates, both tackle routes, eligibility, duplicate prevention and the map unlock.

| File | Owner / connection |
|---|---|
| `FishingMinigame.java` | Owns in-game bar/fish movement, progress/escape and treasure; advances movement -> treasure -> progress. Each target choice is the species' own move, its movement type's signature move (`specialChance`) or a move borrowed from the MIXED pool (`mixChance`). Any move of any kind that lands at least `MINIGAME_TELL_DISTANCE` from both the fish and its current target, with a speed limit of at least `MINIGAME_TELL_SPEED`, waits out a `MINIGAME_TELL_TIME` tell on the old course (`withTell`); nothing else gets one, and the opening move never does. A twitcher's signature bound lands before its next pick (`isBounding`). Uses runtime `FishConstants`, tackle, campaign inputs and live player-rate lookups. No dependency on the authoring tools. |
| `FishingMinigamePanel.java` | Draws the track, target, progress, and treasure, including the tell before a telegraphed move; handles input; records bycatch, catch intel, route progress, and legendary completion. Manta fights own a panel-bounded `ChromaticAberrationOverlay` instance, disposed by both dialog-dismiss paths; campaign aberration still excludes dialogs. |
| `MantaMinigame.java` | Manta-only model selected by the dialog. Two independent motion-only decoys call the same `FishingMinigame.advanceFish`; each has its own targets, velocity and tells, but no treasure/progress/rewards. Opening positions are shuffled across all three. The panel renders all with the real target's presentation (including sonar/chicken); only the real model controls green-bar coverage, catch progress and sound hooks. Restart resets all three; ending stops them. |
| `FishingMinigameDialogPlugin.java` | Hosts the custom visual, preserves source rupture and quest identity for drone and harpoon catches, applies tutorial catch protection, preserves campaign music, and exposes dev reopens that bypass substitution. |
| `LegendaryScarDisplay.java` | Minigame-owned scar icons, failure stamp and postgame explanation. `showNotice` positions the first-scar box using the icon centre and measured box size. Presentation and input timing are in [UI.md](UI.md#minigame-timing). |

Legendary minigames snapshot `Chase.scars` at opening. `FishingMinigame.applyScars`
blends movement speed, turn frequency and catch/drain rates toward a Common profile
by Poor quality; the fourth scar eases them further. `FishConstants.SCAR_*` owns
that tuning. Species motion, rarity, treasure and tackle effects remain intact;
Manta decoys receive the same modifiers. No shared `FishSpec` is changed.
`tools/LegendaryScarChecks` covers retries, failure callbacks, quality and cargo,
saved records, per-species easing, and postgame UI lifetime; normal movement parity
remains covered by `FishingParityChecks` and `MantaMinigameChecks`.

### `campaign/fish/entities` and `campaign/fish/spawner`

| File | Owner / connection |
|---|---|
| `FishEntityPlugin.java` | World fish mote: per-species movement, straight-line dives, held and stunned states, glow, source rupture, and legendary behavior. A saved `lampSurfaced` flag marks fish the player's lamps surfaced: they swim at `MOVE_SPEED` 90 times `LAMP_FISH_SPEED` (a percentage, default 100: their normal speed), with `LAMP_STRAIGHT_SWIM` on they keep the buried drift's straight course and `BURIED_WEAVE` weave with no motion mode or dives (`isStraightLampSwim`), they are lamp-bound whatever their `reachedBy`, and their lamp visibility decays like a buried mote's lamp mark, so `searchlight_track_time` holds them in view and in harpoon reach. The wander swing runs on a clock, so `getWander` scales it by the fish's pace (base speed against the 90 rupture speed, times the beam slow, capped at 1): lamp-surfaced and beam-slowed fish weave less instead of looping in place. |
| `BuriedMoteEntityPlugin.java` | Invisible open-water fish; drifts with its species' campaign pace and wander. While `Setting.LAMP_SURFACING` is on, a non-epic, non-legendary mote in the player's location surfaces through `unearth(true)` the moment any player beam reaches it (`SearchlightAbilityPlugin.getBeamStrengthAt` > 0), checked in its own `advance`. It then swims its 900-unit surface run with full species movement and fades at the end. Epics and legendaries stay buried until harpooned. `unearth()` copies every `$catchrelease_` memory key, with its expiry, to the surfaced fish, so quest claims (`QuestPond.markPlanted`) and per-fish quest fields survive surfacing; vanilla keys stay behind. `BuriedMoteSpawner` counts lamp-surfaced fish toward the population but neither counts nor culls quest motes, and the Tracking Gimbal (`Searchlight.acquire`) locks onto them as well as buried motes. A mote given `setCircle(mote, anchor, radius)` (`$catchrelease_circleAnchor`, `$catchrelease_circleRadius`) circles the anchor at its buried speed instead of wandering, easing back to the radius and weaving 60 units in and out, with its heading on the tangent; `unearth` copies the keys, but the surfaced fish ignores them. |
| `PondFishSpawner.java` | Selects species by habitat, range, implement, weights, tackle, and rumor effects. |

### `campaign/fish/shop`

| File | Owner / connection |
|---|---|
| `FishShopDialog.java` | Outfitter and session undo. Automatic payment preview spends the same specimens; manual payment uses the shared picker and reopens next frame. Undo restores exact cargo/credits/gear/tiers/marks. On the Equipment tab a target slot (`ShopSlotPlugin`, shown while `FishingShips.hasSecondSlot`) decides where FIT goes; `ShopEntry.buy(slot)` checks `canFitIn` before taking payment, receipts record the rig, module and slot, and undo restores that slot, dropping a ship module whose ship has gone. `ShopEntry.grant()` from outside the Outfitter fits the first slot, or the second when a module there blocks the first. `init` reconciles fishing ships first. |
| `ShopEntry.java` | Uniform wrapper for upgrades, modules, and curio switches. |
| `PaymentSpecimens.java` | Builds the specimens one saved `FishRequirement` asks for, for a reward that hands over the fish to buy something: one ordinary species (named, or weighted from the species that could satisfy it), 95% of its size range, low coherence when asked, the required method and implement, no catch time or provenance. Every specimen is checked with `FishCurrency.isAutoPayable`; null means the caller pays credits instead. `tools/ShopPricingChecks` builds them for every generated recipe. |
| `ShopPricing.java` | Credit prices and saved fish payments. Upgrade credits use the 5k/10k/15k/20k ladder, end-aligned for shorter lines (two tiers: 15k/20k). Modules cost 20k–40k; Sonar is in the 40k bracket. Quest schematic valuation reads credits without generating a fish payment. |
| `ShopFishPayments.java` | Generates ordinary `FishRequirement` payments: 50% named pattern, 30% species with two modifiers, 20% player-chosen matching batch with a modifier. Checks current ranges and owned fishing gear; no origin restriction. Higher bands favor rarer patterns, larger catches and Fine grade. |
| `ShopMarks.java` | Persistent shopping list. |
| `FishAsker.java` | Interface implemented by jobs, tutorial intel, and chart-request intel so the shop, cargo, and route planner can read fish requirements uniformly. |
| `FishCurrency.java` | Counts and spends matching fish; `countSpecies` feeds the outfitter's rarity-chip tooltips. |
| `FishRequirement.java` | Describes and evaluates count, rarity, grade, species, region, source rupture, timestamp, coherence, method, and implement. |
| `ShopSchematics.java` | Saves quest-earned permissions for stocked modules and the last two tiers of each upgrade. |

### `campaign/fish/items`

| File | Owner / connection |
|---|---|
| `FishItems.java` | Item IDs, encoding, decoding, landing, unboxing, and transaction-screen packing. |
| `FishItemPlugin.java`, `FishBundleItemPlugin.java`, `FishPileItemPlugin.java` | Specimen, crate and pile cargo items: names, prices, right-click stowing and unpacking, icons and tooltips. The three rows in `special_items.csv` carry `hide_in_codex`; species pages live in the mod's own Codex category. |
| `SoldFishRestock.java` | Transient `SubmarketUpdateListener` that removes sold fish, crates and piles when a submarket restocks. |
| `FishItemTooltips.java` | Shared tooltip blocks for the three items; layout contract in [UI.md](UI.md#catch-item-and-species-tooltips). |
| `FishBundleItemPlugin.renderBoxIcon()` | Species art fitted to the crate label's four native-image corners; [cargo sprite coordinates](UI.md#portraits-and-sprites). |

### `campaign/fish/crab`

| File | Owner / connection |
|---|---|
| `CrabWares.java` | Stock, prices, prerequisites (`isOffered`), ownership, curio switches, the catch sound, fallback bass and last explosive target; industry plans count as owned when held or learned. |
| `CrabBackdrops.java` | Rotates one backdrop per port from `backdrops.csv`. |

### `campaign/fish/tackle`

| File | Owner / connection |
|---|---|
| `TackleManager.java` | Separates module ownership from the modules fitted to each rig. Each rig is a saved `RigLoadout` of two slots under `$catchrelease_tackle`; the first slot (index 0) is the rig's own and the second (index 1) the fishing ship's, empty until ship slots exist. `effects(rig)` returns the rig's combined `TackleEffects`; `getSlot` reads one slot for the Outfitter. `fit` refuses a slot through `canFit`: a module that does not fit the rig, the same module in the rig's other slot, or one sharing its `Tackle.Exclusion` group. Rigs are checked separately, so a module that fits both the ROD and the Harpoon can sit on each. The second slot takes a module only while `FishingShips.hasSecondSlot(rig)`; emptying it is always allowed. |
| `TackleEffects.java` | Combined effect of one rig's fitted modules, first slot then second: multipliers multiply, quality and coherence bonuses add, flags combine and the longest lock wins. `has(Tackle)` replaces identity checks against a single fitted module. |

### `campaign/fish/ships`

| File | Owner / connection |
|---|---|
| `FishingShips.java` | Fishing ship registry: `Ship` maps each hull (`catchrelease_harpoon_ship` Stigmata, `catchrelease_lamp_ship` Monstrance, `catchrelease_rod_ship` Reliquary, and its early skin `catchrelease_rod_ship_early` as `ROD_EARLY` with no module) to its rig and module (Retrieval Head, Fanned Array, Reployment Bays). Two ships can serve the ROD rig: `hasSecondSlot` and `getShipModule` look at every present ship of the rig, `forRig` prefers a present one, and a ship joining or leaving while the other is aboard keeps the slot (`joinSibling` fills an empty slot with its module; `leaveSibling` removes only its own unowned module). `FishingDroneSwarmScript.getDroneCount` adds one drone while `ROD_EARLY` is present. `PRESENT_KEY` saves which ships were aboard at the last `reconcile()`, so `hasSecondSlot` and `isShipModuleAvailable` are lookups. `reconcile()` scans the fleet once (a D-modded copy counts through `getDParentHullId`) and acts only on changes: a joining ship resets the rig's second slot to its module, or empty when the first slot already holds it; a leaving ship empties the second slot and unfits its module from the first slot unless the player owns a copy. Several ships of one kind give one slot; a mothballed ship still counts, as it is still a member. `register()` (on load) adds the transient `Listener` (ship purchases, sales and storage transfers through `reportPlayerMarketTransaction`, battles through `FleetEventListener.reportBattleOccurred`, after losses are removed and recoveries added, and `ShipRecoveryListener`) and the transient `Watcher`, then reconciles. `FishReward.Ship` and `FishShopDialog.init` reconcile directly. **Polling exception:** `FleetData` reports nothing for scuttling, the `AddShip`/`RemoveShip` rule commands or other mods' changes, so `Watcher` compares the O(1) `getNumMembers()` each unpaused frame and reconciles on a change; two changes that cancel within one frame wait for the next callback or count change. `tools/FishingShipChecks` covers join, leave, rejoin, several ships of one kind, D-hulls, owned copies and the full incompatibility matrix. `register` also adds each hull's empty `_Hull` variant to `getHullIdToVariantListMap` when its list is empty: vanilla colony custom production otherwise charges the first order of a hull without listed variants and builds nothing (`CoreScript.doCustomProduction`). Every `reconcile` also drops the `FighterStore`s of ships no longer in the fleet, and ends with `WoundedEpic.checkBuilt`. |

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
| `FishCodexEntry.java`, `FishSizeChart.java` | Species page: left-column boxes, right-column icon card and, for caught species, the size-distribution box below it. A range-data-only species has no icon: the list row gets the fully transparent `FishConstants.CODEX_BLANK_ICON` (preloaded as `codex_blank` in `settings.json`; vanilla's row needs a real sprite), and the page builds no icon card until the species is caught. `getIcon` loads a caught species' icon through `SpriteLoader.loadSprite` before returning it: vanilla's row only looks up an already-loaded texture, and fish icons load on first use, so a row could otherwise draw nothing. [Chart contract](UI.md#codex-size-chart). |

### `campaign/fish/legendary`

| File | Owner / connection |
|---|---|
| `LegendaryChases.java` | Persistent host, sighting, provocation, active-hunt (`roaming`), scars, Imposter reveal, completion, and defense state for each legendary. `noteCaught` also tells `LegendaryHunt` about the catch. |
| `LegendarySpawns.java` | Spawns each uncaught resident on system entry and load through `LegendaryHaunt`'s location listener, after tutorial graduation. One buried or surfaced mote per species; no random-population rolls or distance culling. Placement and inactive patrols stay within 6000 units of the primary sun, otherwise the largest planet, otherwise 0/0, and clear stellar hazards. False Dawn uses its corona without the 6000-unit limit; Longliner retains its boat spawn and movement. |
| `LegendaryStarAvoidance.java` | All non-False-Dawn legendaries avoid each local star's surface and nominal corona plus 150 units. Natural buried spawns, surfacing, console placement, explosive respawns and Imposter reveal use the same stellar boundary. Swimming, submerged runs, travel dashes and reveal drift test complete movement segments; positions already inside are corrected. Quorum steering reserves its escort radius; shell-game centers reserve the largest decoy ring. Manta steering moves its line center with one-slot clearance, preserving spacing and blackout swaps. Held catches remain attached to their retrieval gear. Uses existing movement callbacks, with no new script or sector-wide scan. |
| `SlipDashModule.java` | Moray slipstream trail and curved travel dash. The first haunt update starts a surfaced escape and stream without waiting for cooldown, full intensity or the repeat-dash range; held/diving fish wait, and lamps must remain on. Repeat dashes wait 11–18 unpaused seconds after the previous dash ends and retain their range/intensity gates. A failed-catch escape consumes the opening so it cannot spawn twice. Streams are 620 units wide. `STREAM_LIFETIME_MULT` is 5: tail roll-up is 0.5 segments per second and segment fade-out is 15 seconds, independent of dash timing. With the 14-segment standing window, fully lit trails finish fading about 43 seconds after a dash ends. A failed catch bypasses range/intensity/cooldown gates, ends any prior growing trail, and starts a 9.75-second emergency dash (1.5 × normal maximum), keeping the fish surfaced. Haunt cleanup removes all owned terrain immediately. |
| `QuorumShellGame.java` | Active-haunt movement for a Quorum with no escorts. Real Quorum failures spin the shell game four turns in 1.2 seconds and add no bodies. The two shell-game decoys remain catchable and replenish. Abandoning the haunt removes shell-game bodies and restores all three escorts. |
| `LegendaryHaunt.java` | Transient coordinator. The Moray runs only `SlipDashModule`: escape dashes and slipstreams, with no projectile motes, player slowdown or ability lockout. The manta runs only its formation and chromatic aberration modules. Lantern Jack's stored shells remain in the chase ledger and are not reset by haunt activation. A sighting or `activateFor` start plays the stereo `catchrelease_haunt_foghorn` UI sound (volume 0.45, legendary slider); `stop()` plays it at `FOGHORN_END_PITCH` 0.8 when a haunt was active, including leaving the system and switching species. `restoreHunt` after loading is silent. |
| `MantaBackgroundBlackout.java` | Manta-only 0.3-second background blackout every 15–40 unpaused seconds. First Luna renderer on `TERRAIN_1`, after vanilla background/starfield and before breach windows, motes, fleets and HUD. No saved background changes. `MantaFormationModule` swaps the real mote into a different stationary slot on blackout onset, preserving its target offset and shield/catch identity; swaps wait while held. Renderer expires outside its owning sector/system and is removed on haunt cleanup. |
| `MantaFormationModule.java` | Abyssal Ghost Manta haunt: one real mote and two haunt-owned phantoms in a straight line at 180-unit spacing. When timed, shield-break or failed-catch jitter ends, the line teleports to a new orientation about its center; no animated rotation. The real fish keeps its slot identity and target offset. Held catches cannot be teleported. `FishEntityPlugin.advance` synchronizes positions after movement; copies use the real mote's dive visibility and shield display. Only the real mote is catchable. Coordinator cleanup removes copies and transient bindings; load-time haunt sweeping prevents duplicate formations. |
| `FalseDawnOrbit.java` | Keeps surfaced, diving and buried False Dawn movement in an annulus outside the largest non-pulsar star with a usable corona in its system. Uses the actual corona bounds; natural spawns, console spawns and explosive-hit respawns share the constraint. Dives retain their visibility timing but follow the corona instead of a straight chord through the star. |
| `FalseDawnCorona.java` | Adds short, complementary-hue spikes to the host star's vanilla `FlareManager` queue while the player is in the host system, including before provocation. Only its own flares are removed on departure, capture and before saving; star specs and natural flares are unchanged. A location listener handles departure; the existing haunt advance refills the queue because vanilla exposes no flare-finished callback. No extra frame script or renderer. |
| `../entities/HauntMineEntityPlugin.java` | Non-damaging False Dawn mines. Red push and yellow pull deliver equal, opposite one-shot impulses through the fleet movement module via `setVelocity`; the implosion ripple remains visual only. Green mines launch `DawnShieldTransfer` only on player collision, not natural expiry or harpoon hits. They do not interdict or slow the fleet. The spent mine advances/renders its courier and stays at the gain-notice origin until the notice finishes. |
| `DawnShieldTransfer.java` | Mine-owned green courier: homes on the live False Dawn at 4000 units/second, adds one stored charge on arrival, and expires after eight seconds if it cannot arrive. Ignores lamp visibility, rejects phantoms and vanished/caught targets, and follows a replacement real mote after an explosive relocation. No fish entity, loot, collision target or separate frame script. Minefield cleanup removes its owner; load-time haunt sweeping removes saved effects. |
| `MinefieldModule.java` | Keeps the 3–6-mine waves and 22-mine cap but rejects overlapping trigger zones, leaving at least 300 units of passage (more for a large player fleet). Placement checks earlier waves as well as the current one, keeps clear of the player and stellar surfaces, and skips crowded spawns after bounded attempts. Mines arm after two seconds and detonate at ten seconds from spawn; both timers pause with the campaign. Blink phase is separate. Spent mines lose their harpoon/placement tag immediately. |
| `GhostFleetsModule.java` | Lantern Jack's harmless fleets: first at 5–10 seconds, then every 15–25, at most two. Spawn 1000–1600 units away with 2–15 ships and at least 8 burn; intercept, with half switching to HOLD after 3–7 seconds. Hard-remove at 250 units or 30 seconds. Transponders stay off; flags prevent clicks, comms and other-fleet attention, and `setNoEngaging` prevents INTERCEPT battles. Harpoons ignore haunt fleets. |
| `FakeWrecksModule.java` | Lantern Jack wrecks: groups of three, first at 5 seconds then every 10–18; at most 25 including fades, with smaller groups when nearly full. Spawn 600–1100 units away, expire after 120–240 seconds, and use registered ship variants except fighters/stations/modules. Click-to-approach stays enabled while interaction/salvage tags are withheld. At 100 units the first wreck approached becomes real; later wrecks have a 10% chance, otherwise fade over 0.5 seconds. Real wrecks restore vanilla interaction tags and retain default derelict salvage. All remain haunt-owned. |
| `LanternSensorGhostsModule.java` | Jack-only vanilla `BaseSensorGhost` contacts: echo player movement, intercept then depart, or pass by. First and repeat spawns take 5–10 seconds, cap four including fades. Uses the haunt's existing advance and cleanup, not the hyperspace manager or a new sector script. Behavior durations convert seconds to campaign days; original tokens remain tracked after vanilla starts fading. No fleet spawning or drive drain. |
| `LonglinerDecoy.java` | Imposter disguise. Fleet centre must enter the inner 80% of a current player lamp's radius (`REVEAL_RADIUS_SHARE`); fan beams inset both reach and half-angle. The existing frame callback checks geometry, not brightness, via `SearchlightAbilityPlugin.isInsideBeam` / `Searchlight.contains`. Reveal removes fleet and spawns mote at the same location -> 3s drift along last velocity -> alert + positional sound -> 0.3s delay -> flee without a shield. Excluded from Fisherman reconciliation. |
| `LonglinerShield.java` | Six rotating, independently breakable sections; swept harpoon collision closes the decorative separators and guards intact sectors from inside-ring contacts. First body hit raises the shield and starts the haunt. Normal hits break one section; explosive hits also break its neighbours. `Chase.longlinerBroken/longlinerRotation` persist until encounter reset. Rendering is covered in [UI.md](UI.md#drawing-gotchas). |
| `LonglinerSchoolModule.java` | The real fish and eighteen phantoms circle a drifting center with individual velocities, changing radii and neighbour avoidance. Movement runs from the real mote's callback, as Quorum's shell game does; the haunt callback owns replenishment. Shared dive visibility, reversed shield rotation and shared section damage remain. Cleanup removes copies on replacement, failure, capture or departure. |
| `LegendaryShields.java` | Defense dispatch and render state: Longliner sections, Quorum escort/regeneration, Lantern Jack stored shells/prey lure, False Dawn mine-fed charges, Moray's one-off shield, regenerating base shields and provocation. `say` resolves shield colours for fish anchors; explicit colour arguments preserve shield/mote colours on mine and fleet anchors. See [floating notices](UI.md#drawing-gotchas). |
| `EpicHunt.java` | Two-stage epic hunts, routed through `LegendaryShields`' harpoon-contact, `isShielded`, colour and speed entry points. Epics drift at `LAZY_SPEED_MULT`, with wander scaled down by the same factor so the slow path does not curl into loops, until a harpoon hit wakes them; `HarpoonEntityPlugin.canTake` lets the harpoon reach a buried epic only inside a lit beam (`getBeamStrengthAt`), never through a lingering lamp mark or a Fathom Head dent; that hit is deflected and raises a single purple shield charge that never regenerates. Awake epics swim at full speed with 1.2x wander and take new legs instead of fading at their target. Every epic is lamp-bound (`reachedBy` is `BREACH_LAMP` alone), so `FishEntityPlugin.advanceLampFade` holds an awake one at full lamp visibility: it stays drawn, shielded and harpoonable with the lamps off or outside the beam. A failed catch fades the epic like any other fish. More than 3,000 units from the player resets the fish to drifting with its shield as it was; a spent shield never returns, and any later hit lands. `announceLost` posts one campaign message naming the species; it skips Fisherman chain epics (`FishermanChain.isPlanter`), whose quests post their own when they hear the reset (T2's intel update, T3's return to the planet). The reset cuts its swim to `EpicHunt.DRIFT_OFF_DISTANCE` (500 units) through `FishEntityPlugin.shortenSwim`, so it fades at the end like other fish unless a hit wakes it first. State is per fish (`FishEntityPlugin.epicActive`, `epicShieldSpent`), not the species ledger. A transient `CurrentLocationChangedListener` (`EpicHunt.DepartureCleanup`, registered on game load) removes every awake epic from the system the player leaves, because awake epics never fade at their target. Phantoms, decoys and escorts are excluded. Drone catches never wake an epic. A fish whose mote memory holds `AWAKE_SPEED_KEY` (`$catchrelease_epicAwakeSpeed`, set on the buried mote and copied by `unearth`) multiplies its awake speed by it; the drift speed is unchanged. |
| `QuestEpicListener.java` | Sector `ListenerManager` interface for the end of a quest-owned epic's hunt: reset (both `EpicHunt.advance` branches and `DepartureCleanup`; both skip an expired mote, so a departure is reported once), escaped and caught (`FishingMinigameDialogPlugin.resolve`, after the rig's callback, so ROD and Harpoon catches both report), and blasted (`HarpoonEntityPlugin.blastMote`). `EpicHunt.report*` dispatch only for motes that `EpicHunt.applies` to and `QuestPond.isQuestMote` marks, passing the `QuestPond.getPlanter` id. |
| `KantaTribute.java` | Dormant legendary tribute support. All 23 `catchrelease_kp*` rules are commented out, including gift options, hand-in handlers, reactions, verdicts and the market trophy description. Their dialogue is placeholder pending replacement; keep the whole block disabled until replaced. The unused `CatchReleaseCMD kantaHasLegendary`/`kantaGiftTokens`/`kantaGiveLegendary` verbs remain for reactivation. Vanilla gifts and legendary automatic-payment safeguards are unaffected. |

Quorum shield escorts use a 70-unit orbit with at least 60 units between centers.
`LegendaryShields.maintainSatellites` samples the available angular arcs randomly,
weighted by their length, and places new/regrown splinters directly on the orbit.
Survivors keep their angles. The real mote's post-movement callback advances all
escort angles together; satellite callbacks only synchronize positions. Held
escorts reserve their moving slots without being pulled off the line. Angles
are saved on `FishEntityPlugin`; no transient reroll on load. Shell-game decoys
are separate. `tools/QuorumEscortChecks` covers placement, replenishment, wraparound,
callback order, held release and random gap variation.

False Dawn starts with one stored shield charge. The wake-up hit provokes it
without spending that charge, so the haunt begins with one. Green mine couriers
add one on arrival, capped at three. Each subsequent deflection consumes
one; elapsed time, failed catches and mote replacement within a hunt never refill
the ledger. Abandoning the haunt restores the single starting charge, even if three
were stored before the reset. Shield collision, green rendering and charge rings use the same count.
Minefield's 22-mine cap counts only live mines, not spent notice/courier anchors.

False Dawn's chase alternates 2.5 seconds at 2.2 times base pace with 3.5 seconds
at 1.5 times base pace. `FishEntityPlugin` caps the additional movement-mode
multiplier at 1.2 while provoked, so mixed-mode lunges cannot multiply the sprint
into a second large burst. Speed tops out at 564.3 units/second at the maximum
normalized species pace. Passive movement, corona paths, dive timing and minigame stats
are unchanged. `tools/LegendaryEscapeChecks` checks the chase across movement
modes and 30/60/144 Hz.

Lantern Jack has only stored hunt shells: three initially, leaving two after the
wake-up hit starts the chase. Feeding can refill up to three. Every deflection
spends one. Empty shells do not regenerate with time, haunt activation or a failed
catch. Abandoning the haunt restores the initial count; collision and display both use the
stored count in `LegendaryShields.isShielded`. It hunts surfaced
prey and lamp-revealed buried prey, surfacing the latter before pursuit. Pursuit
uses a direct heading after hit evasion ends. Inside `FishEntityPlugin.LUNGE_RANGE` (300 units)
the approach becomes a two-stage lunge: a `LUNGE_COIL_SECONDS` 0.3-second coil at 0.6 pace, then a
dash at 5 pace along the heading taken when the dash starts. The dash commits to that mote and to a
fixed exit `LUNGE_OVERRUN` (180 units) past it, and steers beyond the exit so arrival never brakes
it. The mote is swallowed when the Jack's centre crosses it within `LUNGE_SWALLOW_WIDTH` of the
line. The check runs before the frame's move, so it is at most one frame late. The dash then runs on
to the exit, ending there or at the `LUNGE_DASH_SECONDS` 1.2-second cap. It is handled before the
stack and prey checks, so a swallow that fills the stack or leaves no prey cannot stop it on the
mote, and it counts as pursuit (no wander or mode multiplier). Only the dash swallows, so every meal
has a direction. A mote that dodges the line is missed. Losing the mote during the coil, a full
stack, or evasion clears the lunge; the phases scale with stored shells like every other Jack pace.
The phase is transient and a loaded save leaves it null, not `NONE`; read it only through
`getLungePhase()`. `LanternFeedingChecks.loadedLunge` covers a restored Jack.
Each swallowed mote expires immediately
and supplies one stored shell, up to three. At three it stops hunting, eating and
calling. Pond stock, quest targets, held fish, legendaries and shield/phantom bodies
are excluded.
`FishEntityPlugin` scales its surfaced prowl, pursuit and evasive speed to
100/85/70/55% with zero/one/two/three stored shells. Wandering and turn angles,
plus the patrol and evasive retarget clocks, scale to 100/80/60/40%. Shell loss
or feeding changes these factors immediately. Zero shells retain the original
movement; dive timing, movement-mode timing and the five-second evasion do not change.
Each call draws 1–3 fish total: eligible nearby motes within 3000 units are picked
in random order, surfacing buried picks; shortages come from the normal local lamp
pool, 800–1400 units away. Calls retain their 30-second cooldown. A call in the
player's location plays `LegendaryShields.SOUND_LURE` at Jack: vanilla
`ui_intel_distress_call` audio under the mod's legendary volume. Called fish stay
surfaced and swim toward Jack for at most 20 seconds, breaking off immediately at
50 units. They then resume their normal destination, movement and dive schedule;
they do not wait or follow. New call-spawned fish have a destination beyond Jack,
so release does not leave them aimed at its center. `tools/LanternFeedingChecks`
covers shield counts, shield-dependent movement, the lunge's stages and paces at 30/60/144 Hz,
call limits, exclusions, release and ordinary movement.

`LegendaryHaunt.getMantaLampAlpha(lamp)` gives each lamp an independent visual
flicker phase using the identity of its stable aim vector, not its coordinates.
Spot/fan glow and breach renderers share that vector, so each lamp's layers agree
even when it moves or changes beam shape. Impressions combine the visible
contributions of overlapping lamps; marks outside all lamps remain unaffected.
The effect is location-scoped and follows the haunt's unpaused clock and intensity.
Detection, tracking marks and targeting do not use the visual flicker.

Real legendary motes continue swimming after reaching a destination and do not expire
when a dash ends or they leave a pond boundary. Before a haunt starts,
`LegendaryStarAvoidance` steers buried and surfaced patrols inside the spawn disk,
checking that outer boundary alongside stellar clearance. Actual haunt start sets
`Chase.roaming` and releases the outer limit, including through the fade; provocation
alone does not. False Dawn stays in its corona throughout, and Longliner movement is
unchanged. `tools/LegendaryPatrolChecks` covers both movement paths, encounter
transitions, attached bodies, full encounter resets and these exceptions.
`LegendaryHaunt` also registers a
transient `CurrentLocationChangedListener`: departure removes surfaced and buried
legendaries, their attached Quorum bodies and active haunt modules. The existing
load sweep removes off-system legendary motes, preserves current residents, and fills
missing residents through `LegendarySpawns`. Departure resets uncaught residents'
encounter state without changing host selection or relocation timing. Longliner's
reveal flag remains set on departure so its existing relocation still runs.
False Dawn's active haunt ignores lost contact. With lamps on it remains at full
strength within 3500 SU of its corona star's surface, including while the fish
dives or crosses the far side of the star. `LegendaryHaunt.DAWN_SURFACE_RANGE`
controls that distance; `FalseDawnOrbit.findCorona` selects the same star as its
movement, including secondary stars. Leaving that range or turning lamps off
starts the normal 12-second fade. Returning in range with lamps on reverses it
without another sighting. Other legendary activations cannot replace this hunt
while the player remains in range with lamps on. Initial activation still needs
provocation and a sighting; capture and departure still clean up immediately.
The existing haunt advance checks distance because vanilla's location listener
only reports system changes, not crossings within a system. No new script.

For other legendaries, with lamps on, unseen haunt effects linger for
60 seconds, then fade over 12 seconds. Turning the player's lamps off starts that
fade immediately and blocks new haunts, including shield-break and failed-catch
activation. Switching them back on only reverses a fade after a fresh sighting.
Existing module callbacks handle the fade and cleanup; no new polling script.
Spawners and timed Manta blackouts stop escalating below full intensity, except
for the Moray's opening escape and Longliner's school. The school forms when the
first shield section breaks, not on the wake-up hit, and maintains its size while
any section remains intact. The final section hit removes every decoy and detaches
school movement immediately, even if the lamps went off while the harpoon was in flight.
An empty shield cannot rebuild the school after a failed catch or load. Screen effects scale all the way
to zero, including their minimum-strength component.
The full-screen chromatic pass also fades its pixel displacement to zero; the
minigame's region-only pass keeps its existing displacement range.
False Dawn's ambient host-star flares remain independent of haunt intensity.
When the fade finishes, the real fish teleports to a safe `LegendarySpawns.position`
and resumes patrol. `Chase.resetEncounter()` clears provocation, roaming and saved
defense progress. `FishEntityPlugin.resumePatrol()` restores the timed base shield
and clears combat/movement timers, flare cooldown, stun, slowdown and velocity
sampling. Jack's called prey are released. Quorum decoys are removed; surviving
escorts return with the fish and missing escorts are immediately replaced.
The reset waits for any held fish or attached body to be released. Moray's shield,
Jack's three starting shells and False Dawn's starting charge return. Longliner's broken-section mask and rotation reset.
Longliner instead removes its revealed mote, school and any remaining disguise,
then `LegendaryChases.relocateAfterFailure` immediately selects a different host,
even while the player remains in the failed system. Encounter progress and sighting
time reset; recognition survives. The new residency expires old rumors. If no other
host is eligible, saved `Chase.relocationPending` blocks habitat/boat/rumor availability
until host selection succeeds; it never falls back to the failed system.
The active haunt also fails when its real mote disappears. `LonglinerDecoy.reconcile`
handles missing revealed motes before haunt activation through its existing callback.
The 0.98a-RC8 custom-entity API has no removal callback; these checks use local mote
tags in the existing haunt/reconciliation callbacks, not a new sector scan.
All disguise retirement paths call `FishermanBehavior.unload()` before despawn,
expiring lights and stopping their owner immediately, then remove the map marker and
fleet. Registration and stale-booking cleanup use the same path; removing an unbooked
duplicate cannot unset another boat's booking. No new polling script.
Other species preserve host/residency and sighting history on haunt failure.
Recognition and completed catches are always preserved.
Leaving the system resets defenses and scars but removes motes instead of teleporting them.
Catching the fish uses cleanup only and cannot reset or respawn it.
The saved `Chase.roaming` flag restores an unfinished haunt
at full intensity with a fresh sighting grace period on load, even with lamps off;
normal fading then applies; False Dawn resumes its sun-distance check instead
of a sighting grace period. Ordinary fish, phantoms and splinters retain their
movement expiry. `tools/HauntWindDownChecks` covers lamp toggles, dark activation,
lost contact, False Dawn's surface-range boundary, held catches, return placement,
shield resets and load restoration.

Harpoon and drone failure callbacks release real legendary motes through
`LegendaryShields.onFailedCatch` instead of fading them out. Ordinary fish,
Quorum splinters and phantoms retain their previous failure handling. The callback
path runs once per resolved minigame, not for a busy UI or a dev fish replacement.

`FishingMinigameDialogPlugin.recordFailure` adds one scar per failed real-legendary
minigame, including abandonment with Escape, before the retained failure display.
Final dismissal also checks this path, with a once-only guard. Decoys, phantoms and
dev substitutions for a different source species cannot scar a legendary. Scars
survive retries, mote recreation and save/load within the hunt. `resetEncounter`
clears them on full haunt fade, departure or host relocation; completing a catch
preserves its landed state. There are four scars: Exceptional, Fine, Average, Poor,
then Terrible. `FishCatch.getGrade` uses this sequence for legendaries instead of
rolled size; length and weight still roll normally. Ordinary grading is unchanged.
`FishLog` saves the record's scar count, and both codex grade displays use
`FishLogEntry.getRecordGrade` so they agree with the landed specimen.

`LegendaryHaunt.onFailedCatch` starts the matching haunt if necessary, refreshes
its sighting grace period and dispatches `HauntModule.onFailedCatch`; responses
then advance through the existing coordinator, with no additional frame script.
Manta base-shield breaks call `LegendaryHaunt.onMantaShieldPopped` synchronously:
start its haunt if needed, rebuild missing copies and switch stationary slots
through `MantaFormationModule.switchPosition`. The initial wake-up deflection
does not spend the base shield and does not trigger a switch.
Manta deflection labels are created before the swap and stay at the hit position.
`LegendaryShields.sayDeflection` keeps vanilla floating-text timing and rendering,
but uses `ReflectionUtils` to set each new label's stable `entity` field to a fixed
location token. The token is not added to the system; the mote still owns the label's
lifetime and visibility. This follows `CampaignFloatingText` in 0.98a-RC8, which
reads its entity's location every render. Other species retain entity-following text.
All legendary notices share the overlap spacing in [UI.md](UI.md#drawing-gotchas).
Every manta slot switch shares the 0.3-second blackout clock for invulnerability
and jitter. `FishEntityPlugin.isAvailable` rejects it for harpoons and drones;
direct shield/explosive contacts and blast effects also respect this window.
All three motes draw 32 jittered glow copies over a 160-unit spread using vanilla
`JitterUtil`, without moving collision positions. The module refreshes the seed
only during unpaused advance; expiry and cleanup end both protection and jitter.
Manta failure immediately restores its base shield and invokes the formation's
existing background-only blackout/slot swap, rebuilding missing copies first and
resetting the normal blackout timer. It does not wait for the 15–40-second interval.
Lantern Jack and False Dawn escape without restoring shields or changing stored charges.
The Moray's shield uses the existing saved `Chase.shieldPopped` flag instead of
the timed base shield. Once broken, it stays down through failed catches, elapsed
time and mote recreation within a hunt. Abandoning the haunt restores it. Its failed-catch slipstream dash is unchanged.
The Imposter has no timed base shield. Its wake-up hit uses the shared legendary
provocation, flash and deflection notice without breaking a section, spawning decoys
or consuming an Explosive Head. The first section hit forms the school immediately;
restoring an intact shield on load leaves the fish alone. Section hits use the same flash and notice.
Subsequent swept hits use the section collision mesh, independent of visual line
width; a shot through a broken section can reach the body before the
far side of the ring. Explosive section hits use the existing detonation/consumption
path. Failed catches, elapsed time, mote replacement and save/load retain damage.
Full haunt failure moves its boat disguise to a different system; the next wake-up raises six intact
sections. Its school steers toward 180-unit spacing, with bounded substeps and
pairwise separation to prevent overlap. Each fish chooses its own changing orbit
radius and speed; they do not occupy fixed formation slots. The centre drifts away
from the player with stellar clearance, and each swim segment avoids coronae.
Held catches cannot be moved by the school; copies keep swimming without teleporting
when the harpoon drags the real fish. Phantoms have no targetable body or shield, and their dive
visibility comes from the real fish. Lamp visibility remains local to each mote.
Shields stay visible through dives while lit; their drawing contract is in
[UI.md](UI.md#drawing-gotchas).

The Moray keeps its mixed campaign movement and fleeing weave. `FishEntityPlugin`
caps its movement-mode multiplier at 1.25; the 12-second escape cycle uses 0.5 for
two seconds, 1.4 for six, then 1.8 for four. With legendary base pace, normal swimming
peaks at 384.75 units/second. Slipstream dashes still use 900 units/second and bypass
those multipliers. Other species and the catch minigame are unchanged.

Every Moray slipstream dash starts movement immediately. `FishEntityPlugin.startTravelDash`
surfaces the fish on entry; `advanceDive` pauses its dive timer while the dash is active.
Expiry, interception and haunt cleanup release the lock through the existing dash state.
The normal dive interval resumes afterward; no separate lock or timer needs cleanup.

`SlipDashModule` checks entire new trail segments against its existing ribbons,
reserving their width and vanilla's 5% edge wobble until both ends have rolled
up and fully faded. Pending invisible segments also reserve space. A blocked
section or sharp fold ends that ribbon; the fish keeps escaping and starts a
separate ribbon once clear. It never connects across the blocked gap. Nearby
cross-sections of the same ribbon stay joined. `tools/MoraySlipstreamChecks`
covers crossings, parallel clearance, endpoint clearance, fades and sharp folds.
Each new dash stays within 80 degrees of the previous dash's actual exit heading;
its curve also stays within 80 degrees of its own starting heading. This prevents
both an immediate return and a mid-dash U-turn. Held retrieval does not update the
heading; position jumps beyond the dash's frame distance break the trail without
changing its remembered direction. Corona avoidance retains movement control;
an out-of-cone detour cannot lay a backwards ribbon. The direction survives trail
fading for the life of the haunt. The same checks cover consecutive escapes at
30/60/144 Hz, angle wrap, cooldowns and corona detours, including long frames.

`tools/FalseDawnCheck` runs standalone checks for corona bounds, host selection,
specimen lifetime, flare cleanup, mine forces/expiry, minefield passages, shield
charges, moving couriers, notice placement, pause and cleanup.

### `campaign/fish/constants` and `campaign/fish/intel`

| File | Owner / connection |
|---|---|
| `FishIntelMapButton.java` | Shared navigation contracts: open the fishing map for habitat targets, plot a route for known systems, or set autopilot for non-fish objectives. |
| `FishIntelNotifications.java` | `queue` defers new intel to the first unpaused frame after dialogue; `addNow` adds it at once inside the dialogue, with its card in the text panel and no later popup; updates use a zero-day delayed script; inline cards do not consume the queue. |

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
| `HarpoonOffence.java` | Per-faction, per-system hit history, debts, reputation loss, witness state, and response ladders. The T2 captain's fleet (`CaptainsFleet.isCaptain`) is never recorded or turned hostile, so the harpoon comm rows (score 1200–1300) never take over his conversation. |
| `HarpoonPatrolResponse.java` | Sends one collector at a time in the incident system; after a reported refusal, every eligible patrol there that sees the player. |
| `HarpoonWitness.java` | Makes a civilian seek a patrol, after a first hit or a refused repair bill. |
| `HarpoonHitman.java` | Revenge-contract roll for eligible colonial factions and eligible victims; the `CHANCE_*` constants set each trigger's odds. |

### `combat/tug`

Stigmata combat and towing code. Design and build plan: [COMBAT_SHIPS.md](COMBAT_SHIPS.md#stigmata-harpoon-tug).

| File | Owner / connection |
|---|---|
| `data/weapons/weapon_data.csv`, `catchrelease_harpoon.wpn`, `proj/catchrelease_harpoon_shot.proj` | Tug Harpoon `catchrelease_harpoon`, built into the Stigmata's `WS0001` (`builtInWeapons`): BALLISTIC MEDIUM turret, 400 kinetic, range 900, 2 charges regained one per 12 seconds (`ammo`, `ammo/sec` 0.0833, `reload size` 1), 1.5 s refire (`chargedown`). Tagged `restricted`, `no_drop`, `no_sell`; hint `NO_MANUAL_FIRE` so the ship AI rarely selects it to fire by hand. Its shot is a `PROJECTILE_FF` ballistic, so it hits friendly ships and wrecks (owner 100) too, never its own source. The shot's colours are transparent and it has no `bulletSprite`, which is always drawn additively; `TetherRenderer` draws the head (`harpoon_head` in `settings.json`). Description in `descriptions.csv`. |
| `HarpoonWeaponEffect.java` | The harpoon's `everyFrameEffect` (one instance per weapon). The eased return below stops while its group autofires at a target, since the tracker aims then. The engine also calls `onFire` on it, as it implements `OnFireEffectPlugin`, so the `.proj` has no `onFireEffect`; `onFire` hands the shot to `TugTethers.fire`. Turret return: 0.4 s after a shot, or at once when a line ends any other way (`Tether.release` calls `onReleased`), `advance` eases the mount back to its slot angle with `setCurrAngle` (8× the remaining angle per second, 30–360°/s), which replaces vanilla's turning for that frame because `advance` runs first; vanilla would hold the angle for 15 s. Skipped while the player's own ship aims the weapon's selected, non-autofire group. `tools/TetherChecks` covers the step. |
| `HarpoonOnHit.java` | The shot's `onHitEffect` (one instance per projectile, so stateless). Ships and wrecks only. A shield hit (`shieldHit`, true because the shot always deals shield damage) spawns a deflected copy through `engine.spawnProjectile`: the velocity reflected about the shield normal at 60% speed, spawned 12 units outside the shield so it cannot hit the same shield at once, at most two deflections per shot (custom data); the line follows the copy (`TugTethers.replaceShot`). A hull or armour hit attaches the line (`TugTethers.attach`). A spawned copy gets its own `onHit` instance but no `onFire`. |
| `TugTethers.java` | `BaseEveryFrameCombatPlugin` owning every harpoon line in one battle, kept in the engine's custom data and added by the first shot (`get`), so battles without a Stigmata never run it. `fire` releases the weapon's current line and starts a new one; `attach`, `replaceShot` and `missed` find the line by its shot. `getTether(tug)` returns a tug's attached line, `getLine(weapon)` a weapon's line in flight or attached, and `release` lets go without firing. `peek` returns the battle's instance without creating it. Advances lines only while unpaused. |
| `Tether.java` | One line. `FLIGHT` from the mount (`weapon.getFirePoint(0)`) to the shot, released when the shot expires or damages something that is not a ship; `ATTACHED` at a point stored in the target's local frame, with the mount-to-hit distance as its length (at least `MIN_LENGTH` 60). Released when the tug leaves play or dies, or the target leaves play, expires or changes between ship and wreck, after `DURATION` (20 s), or, with `Setting.HARPOON_ROPE_HULL_BREAK` on, when the taut rope runs through the tug's hull (LazyLib `CollisionUtils.getCollisionPoint` from the target end, after updating the bounds, entering more than `HULL_MARGIN` 40 from the mount, which sits inside the bounds). Each frame while attached, `solve` (mount to target point, centres' velocities) removes the separating speed of a taut rope plus a stretch correction (`CORRECTION_RATE` 4/s per unit, at most 200), as an impulse split by inverse mass; slack never pulls. `applyImpulse` also turns each ship by the off-centre pull (uniform disc, at most 30°/s per frame). The engine brakes any ship above its top speed unless it is accelerating, decelerating or strafing, and those clamp too, so each end gets a `getMaxSpeed` flat allowance (`nextAllowance`) that tracks its excess speed while the rope pulls and decays at 200/s after, removed on release. `tools/TetherChecks` covers slack, closing, mass split, correction and the allowance. |
| `TetherRenderer.java`, `RopeCurve.java` | The line's look, one renderer per line, added by `Tether`'s constructor and expiring 0.3 s after release; contract in [UI.md](UI.md#drawing-gotchas). It reads the line's state and ends (mount, shot or target point; the head's angle at impact is kept relative to the target) and keeps the last ends once they leave play. `RopeCurve` is engine-free: the campaign rope's spring-driven quadratic curve and wobble (`HarpoonEntityPlugin.advanceSlack`/`getLinePath`) plus a sag from the parabola length estimate, with the belly's side held until the rope has been taut for 0.25 s. `tools/TetherRopeChecks` covers sag, ends, settling and side hold. |
| `data/shipsystems/ship_systems.csv`, `catchrelease_tug_drive.system`, `TugDriveStats.java` | Haul Drive, the Stigmata's system: 2 charges regained one per 20 seconds (`max uses`, `regen` 0.05 per second), 0.25 s charge-up, 4 s active, 0.5 s down; `ENGINE_MOD` with a custom system AI (`TugDriveAI`), and `clampMaxSpeedAfter` off so it does not cut a tethered tug's speed. The stats script (one instance per ship system, so it keeps the ship's own mass in a field) scales mass by up to 2.5× (`setMass`, restored in `unapply`), acceleration +150%, deceleration +100%, turn acceleration +150% and turn rate +50% by effect level; it lengthens and recolours the flames (`extendFlame`, `fadeToOtherColor`) and blows dark `addNebulaSmokeParticle` smoke out of each working nozzle every 0.05 s (0.15 s under `Setting.REDUCED_EFFECTS`). Description in `descriptions.csv`. |
| `HarpoonAutofireAI.java` | The harpoon's `AutofireAIPlugin` (one per weapon), asked only while its group is on autofire. Every 0.3 s, or when its target stops qualifying, it scores enemy ships the side is aware of, in range and arc, not fighters, stations, modules or phased: shield down 2, turned away 1.5, facing the mount 0.25 (a deflection); ×1.5 overloaded or venting; ×1.5 retreating, `BACKING_OFF` or `RUN_QUICKLY`; up to ×1.5 for lost hull; ×0.3–1.5 by mass against the Stigmata's 600; ÷(1 + friendly ships targeting it within 2,000); ×0.75 if hit in the last second; nearer is better. Below `MIN_SCORE` 0.6 it holds fire. It aims with `getAimPointWithLeadForAutofire` and fires only when aimed (3°, or half the target's radius), no line of this weapon is out, and no friendly ship or wreck is within 80% of its radius of the line of fire (the weapon has no friendly-fire check of its own). `tools/TugAIChecks` covers the scoring. |
| `TugPilot.java` | AI control for an AI-flown Stigmata (`getShipAI() != null`, so also under autopilot), an `AdvanceableListener` added by the Tug Drive Field's `applyEffectsAfterShipCreation`. It runs at the start of `Ship.advance`, after the ship AI and before commands and weapons, so its orders act in the same frame; the default AI cannot be wrapped, because `BasicShipAI.advance` throws when the ship's AI is not itself. It sets `setForceNoFireOneFrame` on the harpoon unless its group is on autofire and the plugin wants to fire, since the ship AI can also fire a group by hand. It releases a line without firing (`TugTethers.release`) when the target is a wreck or friendly, the tug's hull is below 35% or flux above 85%, or the target is over three times its mass with no drive charge left after 4 s. While a line holds an enemy, `tow` overrides the AI's movement: `dragDirection` heads away from the catch (weight 1.5), bent toward friendly and away from enemy ships within 3,000 (`Misc.getShipWeight` over distance, enemies only if the side is aware of them), so the rope stays taut; `steer` turns with an angular-speed target (2× the angle, capped at the ship's turn rate), thrusts within 60° of the heading, and blocks every other movement command, since the engine drops a blocked command type even when it is also given. |
| `TugDriveAI.java` | The Haul Drive's `ShipSystemAIScript` (`.system` `aiType` `CUSTOM`). It runs inside the default ship AI each frame, also under autopilot, and is rebuilt with that AI, so it keeps only a timer. Every 0.2 s, when the system is usable (not active, charges left, no cooldown, not overloaded or venting), it gives `USE_SYSTEM` to haul when a line on an enemy is taut (99% of its length) and the catch is at least 0.8× the tug's mass or pulling away faster than 20, or, with no line, to escape below 40% hull while backing off, needing help or under missile threat. `tools/TugAIChecks` covers both rules. |
| `TugDriveHullmod.java` | Tug Drive Field (`catchrelease_tug_field`, `data/hullmods/hull_mods.csv`), built into the Stigmata. A `HullModFleetEffect`: `onFleetSync` counts non-mothballed members with the hullmod into fleet memory `$catchrelease_tugFieldCount` (unset at zero), so `advanceInCampaign`, which the engine calls for every fleet every frame, is one memory read for fleets without one. Fleets with tugs get `getFleetwideMaxBurnMod().modifyFlat` (+1 per tug) and `getAccelerationMult().modifyMult` (+20% × (2 − ½^(n−1))) under `catchrelease_tug_field`, both removed while `Abilities.SUSTAINED_BURN` is active. These fleet stats apply in the same frame (`FleetData.getBurnLevel`, `CampaignFleet` movement), which ship stats would not until the next sync. `tools/TugDriveChecks` covers the falloff, reapplying and Sustained Burn. |

### `combat/salvage`

Reliquary salvage code. Design and build plan: [COMBAT_SHIPS.md](COMBAT_SHIPS.md#reliquary-lyne-tender).

| File | Owner / connection |
|---|---|
| `data/hulls/catchrelease_lyne_drone.ship`, `ship_data.csv`, `data/variants/fighters/catchrelease_lyne_drone_wing.variant`, `data/hulls/wing_data.csv` | LYNE drone `catchrelease_lyne_drone`: unarmed FIGHTER, sprite `graphics/catchrelease/ships/lyne_drone.png` (32×30, a copy of the drone art the campaign borrows from vanilla `munitions_drone.png`), 150 hull, 10 armour, speed 450, acceleration 600, turn 240, mass 5, no shield, hull tag `catchrelease_lyne_drone`. Wing `catchrelease_lyne_wing`: 5 drones, refit 3 s, 0 OP, `no_drop`, `no_sell`, `hide_in_codex`. Two of them are the Reliquary's `builtInWings`; bay *i* launches from the *i*-th launch-bay slot, so `LB 1` and `LB 2`. Description in `descriptions.csv`. |
| `SalvageBaysHullmod.java` | Salvage Bays (`catchrelease_salvage_bays`, `hull_mods.csv`), built into the Reliquary. In combat, `applyEffectsAfterShipCreation` registers the ship as a tender with the battle's `WreckTracker`; `applyEffectsToFighterSpawnedByShip` sets `setHulkChanceOverride(0)` on every fighter its bays spawn, so its own fighters never leave a wreck. `applyEffectsBeforeShipCreation` adds one fighter bay per built-in wing beyond the hull's bays, only for stats built without an entity (the refit's), so `RefitStoreView`'s extra built-in wings are not trimmed; combat ships have their entity set first, so stored wings never get a bay. `advanceInCombat` shows the store on the player's ship: one `maintainStatusForPlayerShip` line per stored fighter type (fighter hull name; ready and restoring counts), plus a "BAYS FULL" line at the cap; contract in [UI.md](UI.md#drawing-gotchas). `addPostDescriptionSection` lists the store in the refit tooltip (the refit `ShipAPI` carries the member id; nothing in the Codex): a count against the cap, then per fighter type the hull sprite (`beginImageWithText`, 32 px, texture loaded once per sprite), name and count, and ready/restoring state. It is the fallback whenever the refit bays are not shown. |
| `WreckTracker.java` | One per battle in the engine's custom data, created by the first tender; an `EveryFrameCombatPlugin` and an engine-level `HullDamageAboutToBeTakenListener`. Just before an eligible fighter of a side opposing a live tender takes hull damage, it sets `setHulkChanceOverride` to `Setting.LYNE_WRECK_CHANCE` (default 75%; vanilla fighters leave a wreck 50% of the time) unless an override is already set, and records it; each unpaused frame a recorded fighter that became a hulk is tracked as a wreck. A fighter wreck counts the private `timeBeforeFade` down from 10 s and fades over 0.5 s (`Ship.java`, 0.98a-RC8; no API holds it), so once a second the tracker tops it back up to 10 through `ReflectionUtils` until the wreck is 20 s old, which fades it at `HOLD_TIME` 30 s; a claimed wreck is held regardless. If the field is missing it logs once and wrecks fade normally. Blacklist (`isSalvageable`): non-fighters, station modules, system drones, shuttle pods; hull tags `threat`, `swarm_fighter`, `fragment`, `dweller`, `monster` and the LYNE drone's; wing tags `restricted`, `independent_of_carrier`. Claims: `claim(drone, range)` takes the nearest free wreck; `attach` sets collision `NONE` (a fighter with disabled engines still reports `SHIP`, logged), `release` restores it, and a claim whose drone dies is released; `salvaged` removes the wreck from the battle and adds its fighter, by the wing id read while it was alive, to the tender's `FighterStore`; `hasRoom` counts the store plus wrecks the tender's drones hold against the cap. `tools/SalvageChecks` covers the blacklist and hold rule. |
| `LyneDroneAI.java` | A LYNE drone's whole AI, picked in `ModPlugin.pickShipAI` for fighters with the hull tag `catchrelease_lyne_drone` (bays pick an AI for every fighter they spawn). `IDLE`: holds station behind its tender, spread by wing index, and every 0.5 s, while `WreckTracker.hasRoom(tender)`, claims the nearest free wreck within 4,000. `FETCH`: flies to it and attaches within both radii plus 10. `TOW`: pins the wreck behind itself (location, velocity, facing easing round) and flies at 70% speed to one of `LB 1`'s launch points by wing index. `LAND`: within 40 of the point, `beginLandingAnimation(tender)` on drone and wreck; both ride the moving point with the bay's work sparks (MIDLINE's `fighterRepairFlickerColor`) until `isFinishedLanding`, then `WreckTracker.salvaged` removes the wreck and `getWing().getSource().land` takes the drone back, counted as returned. Movement is `headInDirectionWithoutTurning` plus `Misc.turnTowardsFacingV2`. `needsRefit` stays false, so the wing never recalls it, and wing orders reach only vanilla `FighterAI`. With its tender gone it drops its wreck and stops. |
| `FighterStore.java` | A Reliquary's recovered fighters, per fleet member id in sector persistent data (`catchrelease_fighterStores`; variant ids are shared by stock ships). Each fighter has a wing id and restoration progress; `launched` counts fighters out on a launch per wing id. The cap (`Setting.LYNE_STORE_CAP`, 10) counts aboard, restoring and launched fighters. `advance` restores the oldest restoring fighter by its refit time. `launchReady` takes every restored fighter and groups each wing type into wings no larger than its wing size (`group`); `returned` puts a landed fighter back restored, `destroyed` drops it; fighters still out at the next access come back (`recallLaunched`). `forBattle(engine, memberId)` keeps the battle's stores in the engine's custom data: the saved store for player-fleet ships in a real campaign battle (`engine.isInCampaign()`), otherwise a copy, so simulator salvage never reaches the save. `get` drops fighters whose wing no longer exists. `purge` drops stores of ships that left the player's fleet, called from `FishingShips.reconcile`. `tools/FighterStoreChecks` covers cap, restoration, grouping, return and loss. |
| `TenderBays.java` | Restores a Reliquary's stored fighters in combat, an `AdvanceableListener` added by `SalvageBaysHullmod`: like a bay replacement, the wing's refit time × `getFighterRefitTimeMult`, advanced at `getSharedFighterReplacementRate`, paused while overloaded, venting or at no CR. Progress outside combat does not move. |
| `SalvageFlight.java` | Flight and bay positions shared by the drones and returning fighters: `moveTo` (`headInDirectionWithoutTurning` plus `Misc.turnTowardsFacingV2` toward a moving point, slowing on arrival) and `bayPoint` (a bay slot's launch point by index, rotated into the world). |
| `data/shipsystems/ship_systems.csv`, `catchrelease_salvage_launch.system`, `SalvageLaunchStats.java` | Salvage Launch, the Reliquary's system (replacing `targetingfeed`): `STAT_MOD`, 0.5 s charge-up, 15 s cooldown, system AI `SalvageLaunchAI` (`aiType` `CUSTOM`). Once per activation (a ready flag, as `ConstructionSwarmSystemScript` uses) it launches every restored fighter: `FighterStore.launchReady` groups them, each group spawns at an `LB 2`/`LB 3` launch point (`spawnShipOrWing` for ids ending `_wing`, otherwise a `FIGHTER_WING` member and `spawnFleetMember`) with deployment messages suppressed; surplus members are removed, the wing gets the Reliquary as source ship and no source bay, and `removeDeployed(wing, false)`. Each fighter gets the ship's CR through the CR plugin, every hullmod's `applyEffectsToFighterSpawnedByShip` (Salvage Bays' sets no wreck), a launch animation and a `RecoveredWings` entry. `isUsable` needs a restored fighter and CR; `getInfoText` shows the ready count. Description in `descriptions.csv`. |
| `RecoveredWings.java` | Launched recovered fighters, one per battle in the engine's custom data. A bay builds whatever wing asks it, so recovered wings have no source bay and vanilla never brings them home. When one needs a refit (its AI's `needsRefit`) or the Reliquary pulls fighters back (`isPullBackFighters`), it gets `ReturnAI`: it flies to an `LB 1` point, lands, goes back into the store restored (`FighterStore.returned`) and leaves the battle. One that dies or leaves play is gone (`destroyed`). |
| `SalvageLaunchAI.java` | Salvage Launch's `ShipSystemAIScript`, run by the default ship AI and rebuilt with it, so it keeps only a timer. Every 0.5 s, when the system is free and the ship is not overloaded, venting or out of CR, it launches when an enemy target (the AI's, else `CARRIER_FIGHTER_TARGET`) is within 3,000 and two fighters are ready, or every fighter aboard is ready (`shouldLaunch`, covered by `tools/FighterStoreChecks`). |
| `RefitStoreView.java` | Shows each Reliquary's stored fighters in the refit as filled, locked built-in bays, only while the refit is open. `Opener`, a transient `CoreUITabListener` registered on load, swaps every player tender with stored fighters on `REFIT`: a copy of its hull spec (the concrete class's public `clone()`, called through `ReflectionUtils` with a return-type filter that skips the `Object` bridge) with up to `MAX_EXTRA_BAYS` (4) stored wing types appended to `getBuiltInWings()`, on a cloned `REFIT` variant set with `member.setVariant(v, false, true)`. The open callback fires after the refit already shows a clone of the first ship's variant, so `Watcher`, a transient script that runs while paused, finds the refit panel by method names as LunaLib's `RefitButtonAdder` does (`getCore`/`getEncounterDialog().getCoreUI()`, then children with `setBorderInsetLeft`, `goBackToParentIfNeeded`, `syncWithCurrentVariant`), swaps the displayed variant and resyncs (`syncWithCurrentVariant`, `saveCurrentVariant`, `setEditedSinceSave`, `syncWithCurrentVariant`). If that fails the swap is reverted, since a member variant with wings the display lacks is charged as removed wings on the next field save. Each frame `Watcher` also re-applies a copy a hull restore replaced, and when the tab is no longer `REFIT` reverts every swap (normal spec, `stripStoredWings`, `setVariant`) and ends. `register` strips stored wings from tenders' saved variants; `rebuild` redoes a member's swap after a discard and sets the shown variant to match (`refreshDisplay`), including back to the normal spec when the store is empty. `tools/FighterStoreChecks` covers the revert. |
| `FighterStoreRefitButton.java` | LunaLib refit option (`BaseRefitButton`, "Additional Options") on each Salvage Bays ship: lists its stored fighters with sprite, name, state and a restoration bar, and discards them one at a time or all at once, each with a second press to confirm. A discarded fighter is gone and gives no LPC; the store is outside the variant, so the refit's undo does not bring it back. Luna's background panel ignores button presses, so the content sits on its own panel whose plugin queues presses and applies them in `advance`; rows route their buttons to it. After a discard it calls `RefitStoreView.rebuild`, which redoes the swap and resyncs the display with the same sequence as Luna's `refreshVariant`. Contract in [UI.md](UI.md#drawing-gotchas). |

### `abilities`

| File | Owner / connection |
|---|---|
| `charges/BaseChargedSkillshotAbility.java` | Shared charge pool for charged abilities. |
| `rod/ability/PondInteractionAbilityPlugin.java` | Opens ponds, launches and recalls drones, and supports lamp fishing with the Breach Coupler (currently disabled; see below). |
| `rod/entities/FishingDroneEntityPlugin.java` | Drone launch, orbit, timed chase, catch, and return. |
| `rod/scripts/FishingDroneSwarmScript.java` | Owns one cast, staggered launches, recall, target assignment, hit cues, and reachability checks. Never targets an epic, surfaced (`EpicHunt.applies`) or buried (the roaming swarm's search): epics are harpoon catches. With `TackleEffects.redeploys` (Reployment Bays), the branch where every drone has returned and none is queued relaunches the whole swarm after re-checking `shouldRecall()`, until the player recalls or the swarm can no longer fish; `RoamingDroneSwarmScript` inherits the same branch with its own `shouldRecall`; a manual recall ends the cast in the `recalling` branch and never reaches it. |
| `rod/scripts/RoamingDroneSwarmScript.java` | Pondless Breach Coupler swarm; dormant while the Coupler is disabled. |
| `harpoon/ability/HarpoonAbilityPlugin.java` | Charged manual targeting and tow-line cutting. |
| `harpoon/entities/HarpoonEntityPlugin.java` | Flight, collision, shields, mines, hauling, fleet contact, rope, catch, and return. |
| `searchlight/ability/SearchlightAbilityPlugin.java` | Breach Lights activation, spool, slow, detection penalty, and all beam renderers. |
| `searchlight/scripts/Searchlight.java` | Beam sweep, lock-on, distortion, and ripples. `getPointAhead` predicts the beam centre a few seconds into the unlocked sweep, moved with the fleet's velocity (T2's epic placement). |
| `searchlight/scripts/NeedleSensor.java` | Passive Lens Array module. The existing lamp ability's `advance` checks local buried/surfaced mote tags every 0.5 seconds while fitted and switched off. Eligible rare/epic fish and unprovoked, uncaught legendaries emit position-only hints every 6–12 seconds, or every 1.5–2.5 seconds for a mote circling an anchor (`BuriedMoteEntityPlugin.CIRCLE_ANCHOR_KEY`, the T3 epic), whose static rings would otherwise land at scattered points of its circle; Longliner, pond stock, held catches and illusion/shield bodies are excluded. No sightings, targeting permissions or fish state changes. `tools/NeedleSensorChecks` covers equipment, eligibility, cadence and teardown. |
| `searchlight/scripts/NeutrinoLens.java` | Lens Array module. One LunaLib transient renderer, created by the lamp ability's existing `advance` while fitted, draws rare/epic/legendary contacts in `FishRarity.color` on vanilla's Neutrino Detector ring (`Abilities.GRAVITIC_SCAN`) while both that ability and the Breach Lights are on; its level is the product of their progress fractions. Vanilla `GSPing` has no colour and the ring colour is a local in `GraviticScanAbility.render`, so the lens keeps its own pings per rarity and copies vanilla's ping model (`GraviticScanData`: special-ping cadence, fades, squared arc falloff, range multiplier, ping sound) and band drawing (`GraviticScanAbility.render`: texture, two bands, pulse, extrusion). Alpha is zero where there is no signal, so only the coloured spikes are drawn over the vanilla ring. No PatchLib, reflection or ability replacement is used. Taking ownership through `ShopEntry.grant` also grants the detector via `FishingIntro.grant`. Exclusions match the Needle Sensor's, without its lamp conditions. |

Lamp and ROD pond lookups use `LocationAPI.getTerrainCopy()` and terrain `getPlugin()`;
the pond tag excludes visual-only ruptures. `SearchlightAbilityPlugin.advance()`
refreshes a transient proximity result before applying effects. Availability,
beam validity and trail vertices reuse it. Location changes, first use after load,
and `MaskedFishingPondTerrainPlugin.getStateVersion()` changes force a refresh;
pond creation/load and opening/closing bump that version, including while paused.
Fleet movement has no proximity callback in 0.98a-RC8, so the existing ability update
handles distance changes; no new polling script or pond registry is used.
`tools/PondProximityChecks` covers scan counts, boundaries and invalidation,
plus the Imposter reveal margin for upgraded circular and fan beams, inactive lamps
and location changes.

The three ability plugins own their tooltips and read current upgrades and fitted
modules when opened. Display contracts are in [UI.md](UI.md#ability-tooltips).
`tools/AbilityTooltipCheck.java` checks their text, highlights, upgraded values,
equipment in both slots, the rig fit rules and availability notices against
`UpgradeData.csv` in a separate JVM.

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
- Refusing a holed crew's repair bill stands the crew down (`HarpoonOffence.stopChasing` also drops the demand intercept) and sends them to the nearest patrol that may collect for their faction (`HarpoonPatrolResponse.findReportTarget`; sight of the player is not required). Their report raises a per-faction, per-system alert in sector memory for `ALERT_DAYS`: every eligible patrol in that system that sees the player is sent with the first-offence fine offer, not the repeat-offence attack. Settling the claim with any one of them clears the alert and stands the others down. With no patrol to report to, the refusal costs standing after `EVASION_DELAY_DAYS` as before.
- Contract rolls, transponder on / off: an ordinary first hit 15% / none (only where it already rolled: no patrol to report to, or the report fails); an explosive hit 30% / 15%; refusing the second-hit repair bill 30% / 15%; paying it none. A refusal contract is called off if a patrol later collects the fine (`HarpoonOffence.callOffContract`). A dark hit files the victim's name under `UNSEEN_KEY`; if the player is later seen with the transponder on by the demanding crew or a collecting patrol (`SEEN_ON_KEY`) and the demand lapses or the chase ends with the claim still open, `noteEscapeWhileSeen` rolls once more at 15%. The roll can be made in hyperspace; the hunters still spawn only once the player is in a system.
- Civilian harpoon responses depend on fleet role, relationship, and vanilla's reciprocal 1.25× strength threshold. A convoy remains civilian even when heavily escorted.
- Repair bills and fines return their outcomes through memory. The global pending marker prevents repeated sector-wide searches when the original fleet is no longer nearby.
- Camp completion is polled because destruction, bribery, dialogue, and departure do not share a callback.
- `despawn()` reports fleet removal to managers and starts the fleet's own fade. `FleetQuest` replacements additionally clear AI, move the original away, and call `Misc.fadeAndExpire()` so the replacement can occupy the same position immediately. Other retiring fleets must not move their still-rendering token during that fade.
- A local fleet-job offer adds state and a cyan drawn marker to an existing eligible fleet; see `FleetQuestSpawner` above for fleet types and exceptions. It does not create or rename a fleet; the expedition and prospecting route managers create the only fleets that exist to carry offers. Acceptance creates fresh members in a mission-owned replacement and reports the original despawn.
- `FleetQuest.ensureMarked()` uses `FleetMarkerRenderer` with `fleet_quest_map_icon`: pulsing light cyan for local offers, static yellow at full alpha after acceptance (`pulsing` false), including accepted distress jobs. The existing encounter/mission callbacks restore transient renderers after loading; release expires them.
- Fleet jobs use stage-owned `setFlag` for delivery, not `makeImportant`. `markDeliverable()` registers the delivery flag once; no vanilla importance record is added. Delivery flags still follow `WANTED`; release clears the hand-in flag and both custom markers. `getMapLocation()` continues to target the giver.

### Save data, cargo, and shop state

Cross-version campaign saves are unsupported during development; see [workflow](../CLAUDE.md#make-and-record-changes). Current saves retain levels, cargo, quest state and equipment ownership. Load hooks restore transient state and current registrations, not old data formats.

- Fish encoding uses four required fields and positional optional tail fields for origin, method, implement, chart provenance and legendary scars (index 11). The current encoder trims absent tail fields; preserve interior placeholders and accept its short records. All fish/crate/pile paths retain the landed scar count independently of the hunt ledger.
- Containers are identified by contents, not stack identity. Spending part of a crate or pile removes it and creates a replacement. Always repack with the original container ID.
- `FishItems.stow()` is the only landing path and normally creates a crate. Loose fish remain valid for all counting and spending.
- `FishItems.isContainer()` is the shared crate/pile test. Do not add direct bundle-ID checks.
- Unpacking a pile restores any singleton species as a loose fish, not a one-fish crate.
- Vanilla restocks remove sold weapons and ships but never special items. `SoldFishRestock` removes every `FishItems.isCatch()` stack from a submarket in the listener call that follows its restock: `BaseSubmarketPlugin.getSinceSWUpdate()` is 0 there because the engine reports right after `updateCargoPrePlayerInteraction()`. Free-transfer submarkets (player storage) and submarkets that do not extend `BaseSubmarketPlugin` are skipped. A per-submarket market-memory timestamp (`$catchrelease_soldFishPurgedAt_<submarket id>`) stops a second purge when the market reopens while time is paused. Restocks that vanilla runs without the listener call, such as `FoodShortageEvent`, leave fish until the next reported restock.
- `Tackle.Fit.BOTH` describes compatibility; it is not a rig. Rig loops use `Fit.isRig()`.
- Module ownership and module fitting are separate. Charge only when `isOwned()` is false; grants must both own and fit the module.
- `ShopPricing` saves each generated `FishRequirement` in sector persistent data under `$catchrelease_shop_payments`, keyed by stat ID/target tier or module ID (shared across rigs). Shop refresh, schematic unlock, session undo and save/load must not reroll it. Named patterns prefer those used least by previous payments. If no catch is obtainable, the uncached null fish payment disables purchase; it does not waive the fish cost.
- Automatic payment never spends a legendary unless the ask names that species (`FishCurrency.isAutoPayable`). That covers `FishCurrency.spend`/`findBest`, `spend(rarity, n)` and `FishHandoffPicker.autoSelect`. Counts still include legendaries, so a broader ask a legendary alone would satisfy falls through to the picker. Jobs already do that when `autoSelect` returns null; the Outfitter's buy button opens its select-payment picker. Only the player's own pick in a picker, or a deliberate sale, removes one. Lamp seizure is confiscation and still takes everything.
- Shop payments use ordinary positive-weight patterns, never legendary or special rows. `ShopFishPayments` checks `FishRanges.matches()` in accessible systems and the player's existing ROD/harpoon/lamps/coupler. Low-coherence asks also need a suitable non-colony habitat without relying on a temporary rumor. Payments accept existing cargo and reuse `FishRequirement`, `FishCurrency` and `FishHandoffPicker`; grade, length and weight do not stack, nor do method and implement. Aim tolerance and lamp slowing use a higher fish-payment band without changing their credit ladder or effects.
- A catch's method and implement are independent. `FishRequirement.couldBeSatisfiedBy()` may constrain an explicit implement, but must not infer it from the rig: harpoons can take pond fish and coupled drones can take lamp fish.
- Stock is a third state. `TackleManager.getOptions()` contains stocked modules plus owned unstocked modules so purchased gear can be removed and refitted. Ship modules (Retrieval Head, Fanned Array, Reployment Bays) are unstocked and join the options while `FishingShips.isShipModuleAvailable`: `ShopSchematics.has` treats them as known, `ShopEntry.isShipModule` gives them no price and `grant` fits them without adding them to the owned set; shopping marks skip them. An owned copy, from a save that bought one before, stays an ordinary owned module.
- Explosive Head is unstocked and consumable. A miss keeps it; detonation removes ownership and the fitted slot, which makes Crablobab sell it again.
- Upgrade tiers and modules granted outside the shop still go through `ShopEntry.grant()` so a running ability is stopped and restarted with its new values.
- A curio is a switch, not a purchase. Its shop price is null, it never becomes “done,” and the button toggles it.
- Celebration Charges are purchased from Crablobab and switched in the outfitter. Player settings scale the celebration's visuals; they do not grant or equip it.
- Party Horn is offered once Celebration Charges are owned, and Crab Fanfare once the horn is owned; both are curios. `CrabWares.getActiveCatchSound()` picks the minigame catch sound: Fanfare if switched on, then Horn, then `SOUND_CAUGHT`. The offer screens' sample options run `CatchReleaseCMD crabPlay`, whose row has no options, so `FireBest` leaves the current menu in place.
- Abilities read tuning values when activated. Any code that changes their upgrade or module inputs must restart the affected running ability.
- Glitter Lens is a stocked, schematic-gated Lens Array with `Tackle.lampTreasureChanceMult` = 2. It is `questOnly`: `FishRewardRoller.rollTackle` and `CaptainsFleet.pickSchematic` skip it, so its schematic comes only from T1 (`TreasureRumour`). `FishingMinigameDialogPlugin` passes the fitted lamp module's multiplier into the minigame's treasure roll only when the catch's implement is `BREACH_LAMP`, whichever rig landed it; it multiplies with the rig tackle's `treasureChanceMult` and the rumour bonus.
- Needle Sensor is a stocked, schematic-gated Lens Array. It uses the normal module reward, fitting and category-icon paths. Its controller and rings are transient. Vanilla registers abilities as entity scripts even when off; the existing callback supplies the passive timer, with no new campaign script or sector scan.
- `StatIds.getAbilityId()` uses an explicit map. Do not infer the ability from a stat-name prefix.
- `harpoon_reach` has three additive 10% tiers: 1200 base range, then 1320/1440/1560 units. It uses normal upgrade prices, Harpoon category/icon fallback and schematics for tiers 2–3. `HarpoonAbilityPlugin.getReach()` supplies the player shot limit, trajectory guide and tooltip. NPC shots retain the base range.
- ROD chase duration and rarity priority are progressive stats: every purchased tier must affect runtime behavior.
- `drone_catch_area` is commented out in `UpgradeData.csv`. New campaigns omit it from shops, schematic rewards and dev grants; `FishingDroneSwarmScript.getRingRadius()` uses the 150-unit fallback. Its stat ID and runtime hook remain. Existing saves retain their saved upgrade registry; this CSV change does not migrate them.
- `drone_acceleration` is a steering response time in seconds: upgrades reduce it. `tools/DroneSteeringCheck` checks acceleration, turning and return approach across every tier at 30/60/144 Hz.
- Retrieval Head refunds one charge only after a confirmed player mote collision that hooks the pattern; an Explosive Head blast and a shield deflection refund nothing. It preserves fractional recharge progress, respects the cap, and uses the ordinary charge-ready callback.
- Explosive Head never lands a fish. Its blast state is terminal, consumes the head, and can immediately make a fleet hostile. Vanilla's explosion entity supplies fleet damage, visuals, and sound.
- An industry blueprint is `industry_bp` with the industry ID as item data. The industry must still override availability and `showWhenUnavailable` against `knowsIndustry()`; the blueprint item alone does not gate construction.

### Fish habitats, ranges, and balance

- `FishHabitat.of()` is the shared habitat snapshot. `FishRanges.matches()` is the only range decision, including pins and relaxation. UI calls `FishPresence.livesIn()`, which routes through the same logic but excludes zero-weight mechanism rows, as the system cap does; otherwise the Quorum splinter would count in every non-Abyssal system.
- Habitat criteria treat blank as unrestricted except for the Abyss. Abyssal species must explicitly name `ABYSSAL`.
- Monthly reassessment refreshes moving habitat inputs and relaxes non-Abyssal species with fewer than three systems. Relaxation order is constellation age, ±0.25 coherence, star colour, then region. It never crosses the Abyss boundary or widens a species into a system that already has fifteen.
- The system cap is strict. After relaxation, `FishRanges.recomputeAdmissions()` ranks each chartable system's matching species under `$catchrelease_range_admit`: pinned quest species first, then species with the fewest homes, then by ID. `matches()` admits only the first fifteen, minus legendaries currently hosted there (`LegendaryChases.countHostedIn()`, which reads the ledger without moving hosts), so a legendary temporarily pushes out the lowest-ranked species until it leaves. A species that starts matching between reassessments, through habitat drift or a rumor, stays unranked and does not spawn there until the next reassessment. Pinned species bypass the cap through their frozen lists, which are snapshotted through the capped `matches()`. Systems without a ranking (the Abyss and systems cut off from hyperspace) are uncapped; a missing ranking table triggers a reassessment on load.
- Active `FishAsker` species are pinned to their previous system list during reassessment and unpin when the ask ends.
- Ordinary species use one or two adjacent regions unless a stronger star, coherence, or theme gate already provides the range. Every region retains at least two ungated Common species.
- The non-legendary roster is exactly 100 fish in a 59/23/12/6 Common/Uncommon/Rare/Epic split. The Abyss contributes 7/2/1/1 of those. Zero-weight mechanism rows, such as the Quorum splinter, are outside the hundred.
- The Abyss uses its own high-difficulty ladder. Rarity controls frequency and value there, but even Abyssal Common rows use at least main-sheet Rare difficulty.
- Catch bar size comes from `FishConstants`; no catch-stat upgrades exist in the registry or runtime. Progress and escape use species and tackle modifiers. The tuner's bar/gain/loss controls remain what-if inputs, not purchasable equipment.
- Balance every row for the base kit. `FishShopDialog` sells no catch-stat upgrades, so each species must stay catchable with the `MINIGAME_BAR_SIZE_FALLBACK` bar, neutral gain and loss, and no tackle. Tune `difficulty`, `restlessness`, `motionSpeed`, `progressRateMult`, `escapeRateMult`, `specialChance`, and `mixChance` together and simulate the result.
- Rarity must read as catch difficulty. The Player angler, fitted to one recorded normal player, is the reference: each rarity has its own band of Player catch rates with a gap to the next, and no fish may fall outside its band (`FishTunerChecks.RARITY_BANDS`, checked over 400 attempts per fish with a 4-point allowance). The bands are Common 84–95%, Uncommon 65–75%, Rare 47–57%, Epic 29–39% and Legendary 13–21%; measured on fresh seeds they are 83–96/63–76/48–58/31–40/14–22%, with means of 90/70/53/35/18%. Within a band, species keep the order they had before the bands were set, so some are harder than others. Abyss species sit at the hard end of their rarity's band instead of a band harder. The Dopefish (`alfonzo_dopefish`) is a deliberate exception in `FishTunerChecks.BAND_EXEMPT`: an Epic that barely moves and is caught almost every time, so its rarity sets only frequency and value. The bands are set through `progressRateMult` and `escapeRateMult` alone; movement values keep each species' character. Across all species the Skilled angler then averages about 95/80/74/65/49%, and Regular about 49/27/26/19/13%. Later difficulty settings are meant to ease the base game. No species may be caught in more than half of its attempts by holding the button or by never pressing it; `FishTunerChecks` asserts this over the real sheet. Signature and random moves carry most of that: sinkers and floaters also need a steeper gain/loss ratio because their home zone is where a parked bar sits. Both shares apply per target choice, so a style that picks often needs smaller shares; twitchers pick about three times as often. An interrupted twitcher bound turns back through the middle, where a bar held still covers it, which is why the bound always lands. Slow common styles rarely show a tell; faster rarer fish and MIXED ones show one every two to four seconds.
- `motionSpeed` and `restlessness` also move the campaign mote; see [fish entities](#fish-entities-and-catch-provenance). Restlessness pulls in opposite directions: a restless mote wanders more in the world, but on the line it retargets before finishing a move and is easier to cover.
- `difficulty` is also the player-facing rating. `FishCodexEntry` labels it, and `FishermanQuest.isChartRequest()` routes a species rated 65 or more to a chart request. Keep main-sheet Commons below 65 and every other row at or above it.
- Species that share a rarity and `motion` differ in at least one of `motionSpeed`, `restlessness`, or `jitter`, so players can learn them apart.
- Weaver is not assigned above Uncommon, and Lunger is not assigned to Common. `MIXED` and a species' random moves may still roll either for one move.
- `reachedBy` uses `POND`, `BREACH_LAMP`, or blank for either. Every EPIC row is `BREACH_LAMP`, so epics are lamp hunts only. Check both the catch method and its origin; drones can also catch at Breach Lamps with the Breach Coupler. See the combinations below when rolling equipment requirements.
- The Breach Coupler is disabled for now: `Tackle.BREACH_COUPLER` has its `breachCoupling = true` line commented out and is unstocked, so it is not sold, rolled as a reward or unlocked by dev grants. Its dependents (`ShopFishPayments`' coupled payments, the ROD tooltip hint, the roaming swarm) read `breachCoupling` and stay off. The constant remains because saves store fitted tackle by value; restoring that line and its stocking re-enables everything.
- A legendary has one host, one permanent catch, and no range data or job asks. All six legendaries are lamp-only. Lantern Jack, Slipstream Moray, Quorum, False Dawn and the Imposter are non-Abyssal; the manta is Abyssal.
- Legendary hosts and motes remain disabled until tutorial graduation. A sighting starts the 90-day relocation timer; the fish never relocates while the player is in-system and never returns after landing.
- False Dawn selects the largest-radius usable corona star across chartable systems, regardless of coherence; its haunt supplies the coherence change. Its old regional sheet preference does not limit this selection. Equal-largest hosts can alternate; a sole largest host is retained. If no usable corona exists, no natural host is assigned. A host that becomes unsuitable is replaced only while the player is elsewhere; new natural spawns are gated immediately. `SpawnFish` still overrides the host for testing but places the fish in the largest usable local corona.
- `LegendarySpawns` excludes resident legendaries from the ordinary population. False Dawn's existing specimens expire when their ledger marks capture or a different host; other legendary lifetimes and departure cleanup are described above.

| Catch | Method | Implement |
|---|---|---|
| Drones at a natural rupture | `DRONE` | `POND` |
| Harpoon catch of a fish from a pond | `HARPOON` | `POND` |
| Harpoon catch at a Breach Lamp | `HARPOON` | `BREACH_LAMP` |
| Drones at a Breach Lamp with the Breach Coupler (disabled for now) | `DRONE` | `BREACH_LAMP` |

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
- Campaign motes move at `FishRarity.speedMult` and `wanderMult` scaled per species through `FishSpec.getCampaignSpeedMult()` and `getCampaignWanderMult()`, for both surfaced and buried motes. `FishSpecLoader` derives the factors from `motionSpeed` and `restlessness` relative to the median of the rarity's weighted non-Abyssal roster. It clamps them to `CAMPAIGN_PACE_MIN`/`MAX` and `CAMPAIGN_WANDER_MIN`/`MAX` and caps combined wander at `CAMPAIGN_WANDER_CAP`. Legendaries skip species-derived pacing and use the player's hunt-speed multiplier because their chases are scripted. Editing a species' minigame speed or restlessness therefore changes its world movement too.
- No ordinary mote stands still for long: a lunger's freeze is capped at `LUNGER_FREEZE_MAX` and still creeps. Rare-and-above motes dive for `DIVE_TIME` about every `DIVE_INTERVAL`, shortened for more erratic species. Under the fabric they hold a straight course at cruise speed along the bearing they dived on, so the surfacing point can be anticipated.
- The method says which rig caught a fish; the implement says what exposed it. Both values must follow the mote's actual provenance.
- Pond and harpoon catches must carry the exact source rupture where applicable. Chart requests also carry target ID, target system, and earliest valid timestamp through loose fish and containers.
- Chart and tutorial completion use the same `FishRequirement`/`FishCurrency` read and spend path as other quests. Do not add a separate cargo-completion check.
- A chart request plants or replants its identified target only while the player is in the target system. Only the active specimen ID/species/system suppresses replanting; a stale quest flag does not. Open-space targets spawn away from their destinations, and chart intel routes to their saved in-system search coordinates.
- `FishermanQuest.markCatch()` assigns both aberration 1.0 and chart provenance after checking specimen ID/species/system. This happens after normal catch rolls and equipment bonuses. Ordinary catches keep their own readings and cannot satisfy the identified request; loose/container cargo, progress and hand-in use the same requirement.
- Chart offers select a valid species/system/implement together through `FishRanges.matches()`. Lamp-only species cannot use ponds; pond-only species require a free pond. Zero-weight, legendary and low-coherence-ineligible species are excluded. Occupied camp ruptures may inform species choice but are never quest destinations.
- `FishermanQuest.hasActiveRumor()` excludes every system in `FishRumors.getActiveRumors()` from chart generation, regardless of rumor kind. A saved, unaccepted offer there is withheld without replacement until its rumor expires; accepted targets remain unchanged. Expired intel cards do not gate generation.
- Harpoon collision calls `HarpoonEntityPlugin.canTake()`. Buried motes use `catchrelease_buried_mote` and require full light, or mere detection with Fathom Head.
- Harpoons launch toward the clicked point without leading or steering. `harpoon_aim_assist` instead has two tiers that add 2/4 screen pixels to the player's existing mote catch radius. `HarpoonEntityPlugin.getCatchRadius()` converts the margin through the current viewport before the swept contact check. Shield, mine and fleet collision shapes are unchanged; an active Longliner shield caps its body catch radius inside the sections, even at distant zoom. NPC shots retain the base radius.
- `HarpoonEntityPlugin` sweeps each outbound step and clamps the last step to range. Shields retain priority over mines; ordinary fish use the earliest contact across surfaced and buried motes. Only the selected buried mote is unearthed, and NPC shots cannot take buried motes. Every `DEFLECTED` shield result returns the harpoon and plays `catchrelease_harpoon_shield_deflect` at the head when it is in the player's location: vanilla's solid and heavy shield-hit files with baked pitch steps, picked at random by the sound set, at a random 0.9–1.1 call pitch. `tools/HarpoonAimCheck` checks unchanged launch direction, catch margins across zoom levels, eligibility, external movement, frame rates, shield/mine ordering and range from a standalone JVM with the mod root as its working directory.

### Rendering, UI, reflection, and audio

Java custom-panel behavior, sprite state, drawing gotchas and minigame UI timing are in [UI.md](UI.md). Shared player-facing text guidelines are in [DIALOGUE.md](DIALOGUE.md#shared-text-presentation). Campaign VFX and non-UI engine constraints remain here.

- Fan light and fan breach window share `STEPS_ACROSS`, `STEPS_ALONG`, and both alpha curves. Change their geometry together.
- Glow, fan, and impression renderers share the same resting alpha formula. Module changes should affect light shape, not total intensity.
- `LegendaryTrail` records legendary and epic movement independently of provocation or haunt state. Surfaced motes record regardless of lamp or sensor visibility. It stops while a dive has faded the mote out (`getVisibility() <= 0`), leaving a gap instead of a strip across the dive. Existing mote callbacks sample after movement; a transient Luna renderer ages and draws the short history using live player-beam coverage from one `SearchlightAbilityPlugin.snapshotBeams()` per render pass (`getBeamVisibilityAt` builds one for single queries). Epic motes draw it in the epic rarity colour at `LegendaryTrail.EPIC_ALPHA_MULT` (half) opacity. Buried legendary and epic motes record only while the lamps light them (`getBeamVisibilityAt` > 0), so dormant legendaries and lamp-hunted epics, which stay buried until harpooned, show a trail under the beam but reveal no unlit path. Other buried motes record none. `BuriedMoteSpawner.cullDistant` expires a mote before removing it, so its trail renderer retires. Shell-game decoys and legendary phantoms share the full-opacity trail; ordinary Quorum escorts have none. Held/expired motes stop sampling, departure retires the renderer, and teleports start a separate strip. Render details: [UI.md](UI.md#drawing-gotchas). `tools/LegendaryTrailChecks` exercises hidden history, spot/fan coverage, pause, capture, departure and frame rates. Cargo, Codex and aquarium sprites are unchanged.
- Camera-centered objects have no camera parallax term. Account for this in effects such as `PondDepthField`.
- `ReflectionUtils` uses `MethodHandle` because the Starsector script classloader rejects direct references to `java.lang.reflect.Field` and `Method`.
- Sound IDs are unchecked strings until playback. Validate them against merged sound data. Starsector JSON supports `#` comments and trailing commas, and sound entries may be arrays or objects.
- `playUISound` expects stereo; positional `playSound` requires mono; loops should be mono.

### Fisherman and tutorial lifecycle

- The Fisherman is one saved `PersonAPI` shared by every boat. Apply the hailed boat's portrait immediately before vanilla builds the person panel; background boats must not mutate it.
- Fisherman portraits are registered `graphics.characters` sprite IDs in `settings.json`. Rank and post remain blank so vanilla shows the rankless person card once.
- All Fishermen use one checked `GO_TO_LOCATION` leg at a time, not `PATROL_SYSTEM`. `OuterReaches` excludes 5,000 units beyond each market entity's radius plus a 1,000-unit route buffer. Deterministic fallback legs are checked too; no valid leg means hold and retry. A boat already inside an exclusion may only take a leg that increases its distance from every enclosing market throughout the escape.
- `FishermanBehavior.keepWorking()` rechecks destinations, current movement and moving markets every 0.25 campaign seconds, refreshes vanilla navigation avoidance and replaces unsafe assignments. An active pre-ROD tutorial interception of the local player bypasses market clearance and removes settlement entries from the navigation avoid list; ordinary routing restores them when the approach ends. Battles are not interrupted; the transient check timer starts immediately after loading.
- `FishermanInterception.cutOff()` places an offscreen boat on the viewport boundary plus `TutorialConstants.INTERCEPT_VIEWPORT_MARGIN_PX` (200 screen pixels), converted through `ViewportEdge` for zoom and aspect ratio. A boat already onscreen approaches from its current position. Settlement distance never gates this approach; stale settlement navigation avoidance is removed before pursuit starts. Battle, dialogue and invalid/displaced viewport guards remain. Cancellation clears pursuit, never the fleet's relocation latch. `tools/FishermanInterceptCheck` covers placement, settlement exemption and restoration, unrelated navigation hazards and cancellation.
- The existing 0.5-second intercept and 0.25-second route checks stay unpaused: public listeners do not report entry into an arbitrary rupture radius or moving-market route clearance. Interception scans only the current system; route checks inspect their owning boat.
- Fisherman visibility requires both a flat detected-range bonus and a per-frame sensor-fader override.
- Visiting Fisherman time advances only while the player is elsewhere. Rendering and sound also stop when the player is outside the location.
- Standing Fisherman markers are map-only and have no sensor profile. Eligible core-system postings are known from the start; other inhabited-system postings remain known after discovery. The fleet stays loaded throughout the player's stay in-system, regardless of viewport or sensor visibility. It unloads only after the player leaves; tutorial reservations and battles delay that unloading. It returns at the saved marker position. Shared identity, stock, restock timers and chart requests live outside the unloaded fleet.
- `FleetMapVisibility` hides local fleet proxies at every visibility level above `NONE`, including sensor contacts, and restores them at `NONE`. Standing postings retain the same token off-map and restore it when the boat detaches. The attached boat's existing callback maintains its posting even for a visiting/tutorial-temporary boat; temporary marker owners also restore hidden tokens. See [UI map lifetime](UI.md#fleet-map-markers).
- Visiting/procgen and Imposter markers remain temporary: departure removes the marker, and the existing fleet departure/expiry rules still apply. Reconciliation preserves standing markers and removes duplicate markers.
- Tutorial and chart-request return navigation (`FishingIntro.getNearestBoat()`) first takes a boat in the player's current system through `CoreFisherSpawner.getAnyBoat()`, including a visiting boat, which has no map marker once detected. Otherwise it targets the nearest system's non-visiting boat, or its standing marker when that boat is unloaded; local marker autopilot redirects to the fleet after spawning.
- The visitor shelf restocks from each sale date, not a global monthly tick. Chart-request completion is the only way to increase shelf width.
- `FishingIntro.point()` is idempotent and can be reached from the wreck, stranded crewman, bar referral, Fisherman interception, or a direct hail. Recovered property takes origin precedence, then rescued crew, then recorded market.
- Every tutorial target system and chart-scoring system passes `FishingIntro.isLessonSystem()`: reachable through hyperspace, not special or hidden, and without a neutron star (`StarSystemAPI.hasPulsar()`, which also checks secondary and tertiary stars). The first lesson uses the Fisherman's system; if that system fails the check, `pickRodSystem()` takes the nearest passing system with a common pond species. Its marked pond is the free pond nearest the hailed Fisherman (`QuestPond.findNearestFreePond`, passed from `CatchReleaseCMD giveRod`), or nearest the player when he is elsewhere; later lessons keep `findFreePond`.
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
| `testing/DevShortcut` | Dev-mode J presses: skip tutorial and grant gear/charts; unlock backdrops; unlock schematics; then unlock and apply every remaining upgrade rung for free. The fourth press uses `ShopEntry.grant()` for mark cleanup and ability refresh, without changing fitted modules, then posts a campaign confirmation. Progress is saved in sector memory; later presses do nothing. |
| `testing/TestStencilRenderer` | Not registered. |
| `rendering/spiral/BlackHoleSpiralWarp` | Deprecated test effect. Not installed by `ModPlugin`; its settings do not enable it. |
| `campaign/ponds/renderer/PondHoleRenderer` | Dormant while `PondConstants.POND_HOLE_LOOK` selects the shader version. |
