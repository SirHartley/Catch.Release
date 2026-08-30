# Catch.Release architecture

This document maps features to files and records the contracts that are easy to break. Update it in the same commit as any architectural change.

- `CLAUDE.md` defines repository workflow, documentation style, and source-layout policy.
- `LORE.md` defines the setting, character voices, and when the player may learn information.
- `lib/` contains read-only game and dependency archives.
- Only the active task checkout is writable unless the user explicitly says otherwise. Use the connected GitHub app for pull requests and merges.

## Build layout

IntelliJ compiles `jars/src` to `out/production/catchrelease`. The artifact build packages those classes as `jars/catchrelease.jar`.

Keep compiler output outside `jars/`. Otherwise stale classes or an old jar can be included in the artifact. Runtime changes require a clean Java 17 build from the exact remote branch. Documentation-only changes are exempt.

## Feature lookup

| Feature | Main files |
|---|---|
| Pond terrain and interaction | `campaign/ponds/terrain/MaskedFishingPondTerrainPlugin.java` |
| Pond placement | `campaign/ponds/listener/PondCreator.java` |
| Fishing rules | `campaign/fish/minigame/FishingMinigame.java` |
| Fishing rendering and input | `campaign/fish/minigame/FishingMinigamePanel.java` |
| Species definitions | `campaign/fish/data/FishSpec.java` and `data/campaign/fish.csv` |
| Habitat and current ranges | `campaign/fish/data/FishHabitat.java` and `FishRanges.java` |
| Individual catch data | `campaign/fish/data/FishCatch.java` |
| Bar jobs | `campaign/fish/jobs/FishJob.java` and subclasses |
| Fleet jobs | `campaign/fish/jobs/fleet` |
| Distress calls | `distress` and `campaign/fish/jobs/fleet/CatchReleaseDistressProvider.java` |
| Fisherman dialogue and services | `data/campaign/rules.csv`, `dialogue/rules/CatchReleaseCMD.java`, and `campaign/fish/fisherman` |
| Tutorial | `campaign/fish/tutorial` and `data/campaign/rules.csv` |
| Outfitter | `campaign/fish/shop` |
| Cargo items | `campaign/fish/items` |
| Crablobab | `campaign/fish/crab` and `data/campaign/rules.csv` |
| Abilities | `abilities` |
| Reusable skillshots | `skillshot` |
| Fishing map and routes | `campaign/fish/map` |
| Codex | `campaign/fish/codex` |
| Legendary encounters | `campaign/fish/legendary` |
| Coherence | `campaign/fish/data/Aberration.java` and `campaign/fish/coherence` |
| Aquarium and conservatory | `campaign/fish/colony` |
| Lamp and harpoon offences | `campaign/crime` |
| Shared UI | `ui` |
| Campaign rendering | `rendering` |
| Save-backed upgrades and charges | `memory` |

## Load order

`ModPlugin.java` installs the fish Codex from `onCodexDataGenerated()` only while Codex data is being generated.

`ModPlugin.onGameLoad()` registers or restores systems in this order:

1. Pond creation on jump.
2. Buried mote spawning.
3. Charge storage and regeneration.
4. The campaign plugin for harpooned-fleet encounters.
5. Harpoon and lamp patrol responses.
6. Local fleet-job spawning. Distress calls own the stranded and dead-engine variants.
7. Visiting Fisherman fleets.
8. Standing Fisherman fleets.
9. Fisherman chart-request upkeep.
10. Tutorial wreck, rating, interception, and tutorial upkeep. Castaway rules attach to planets.
11. Conservatory interaction options.
12. Aquarium management and rendering.
13. Aberration cache updates on arrival and map opening.
14. Monthly fish-range reassessment, including an immediate first-save assessment.
15. Legendary haunt state and stale-haunt cleanup.
16. Longliner decoy state and stale-decoy cleanup.
17. Upgrade base-value refresh from the data sheet.
18. The Catch.Release distress provider, followed by the generic distress framework on vanilla's nearby-event cadence.
19. The skillshot framework.
20. The fishing-map filter.
21. Fish information in the intel planet view.
22. The coherence overlay.
23. Black-hole spiral warping.
24. Stale pond-claim cleanup and legendary range relocking.
25. The three-step `J` developer shortcut, in dev mode only.

`beforeGameSave()` resets the transient skillshot session. Registration must be idempotent because transient listeners and scripts are rebuilt after every load.

## Data registration

### Abilities

`data/characters/abilities/abilities.csv` registers:

| ID | Purpose |
|---|---|
| `catchrelease_searchlights` | Breach Lights |
| `catchrelease_rod` | R.O.D. |
| `catchrelease_harpoon` | Harpoon |
| `skillshot_example` | Framework example |

### Settings

`data/config/settings.json`:

- adds `catchrelease.dialogue.rules` to `ruleCommandPackages`;
- registers portraits, icons, and UI sprites;
- sets `catchreleaseBlackHoleSpiralWarpRange`, which defaults to 6000 and is disabled at zero.

Minigame sound IDs are compile-time constants in `FishConstants.java`. Ability sound IDs are registered in `abilities.csv`. `data/config/sounds.json` contains 31 sound IDs: 28 mod sounds, the skillshot-denied sound, and two low-volume vanilla character sounds. Minigame success and failure use dedicated mod cues.

LunaLib exposes the harpoon charge-ready sound policy, the pond camera-snap setting, and the returning-player tutorial skip. Tutorial skipping defaults off. Completing the tutorial enables and saves it for later campaigns.

### Bar events

`data/campaign/bar_events.csv` registers fourteen `FishJob` missions: eleven standard jobs and three camp jobs. Crablobab and the tutorial rating are added by `AddBarEvents` rules instead.

| Mission ID | Class |
|---|---|
| `catchrelease_standingOrder` | `StandingOrderJob` |
| `catchrelease_chef` | `ChefJob` |
| `catchrelease_startup` | `StartupJob` |
| `catchrelease_butler` | `ButlerJob` |
| `catchrelease_curator` | `CuratorJob` |
| `catchrelease_duel` | `KidsJob` |
| `catchrelease_ring` | `MafiaJob` |
| `catchrelease_client` | `CompanionJob` |
| Remaining standard IDs | `AcademyJob`, `CultJob`, and `TuberJob` |
| Three camp IDs | `PirateCampJob`, `MercCampJob`, and `PatherCampJob` |

Bar offers use the private `JobSpecificOptions` trigger so vanilla's cut-comm option and unrelated person options cannot enter the job menu. Accepted contacts use the mission-owned greeting wrapper and clean up their visual state on exit.

### Distress calls

`data/campaign/distress_calls.csv` is a merged spreadsheet. Each uncommented, namespaced row is active; a row whose ID begins with `#` is disabled.

The framework creates the distress entity, vanilla-style breadcrumb intel, and rules trigger. Providers own all event and quest content and may replace the breadcrumb's descriptive copy or the default jump-point orbit anchor. Distress quest rules select the detailed route when a type supplies a hail, then enter the same complete, looping offer menu as local fleet quests; legacy cases without a hail retain the compact route. Catch.Release supplies the stranded fleet, dead-engine scavenger, Hegemony Follower, Diktat State Dinner, Tri-Tachyon Claim Assay, League Mandate, and pirate Parley Fish events through `FleetQuest`.

### Other registrations

- `data/world/factions/default_ranks.json` registers the Sub-Butler, Crab Merchant, Fight Night
  Commissioner and House display ranks.
- Terrain CSVs register the pond and coherence terrain. `BaseTerrain` does not add the terrain ID as an entity tag.
- `data/campaign/special_items.csv` registers fish cargo items.
- `data/campaign/industries.csv` registers the Breach Conservatory.
- `data/campaign/backdrops.csv` defines aquarium scenes and their ownership sources. Source art is 388×170 with a 386×168 visible area; 2× assets are 772×336.
- `data/config/custom_entities.json` registers buried motes, harpoons, drones, legendary props, and the Fisherman map icon. The castaway is planet memory, the tutorial wreck is vanilla salvage, and ponds are terrain.
- `data/campaign/rules.csv` owns all dialogue. The Fisherman encounter uses `BeginFleetEncounter`, clears ignore state, and calls vanilla `OpenComms`; it does not use a custom interaction plugin.

### Console commands

When Console Commands is installed, the jar exposes:

- `AllFish.java`
- `AddFish.java`, using the shared species resolver
- `SpawnFish.java <species>`, including legendary reset and Longliner safeguards
- `HauntStatus.java`
- `SpawnFisherman.java`
- `SpawnFleetQuest.java`
- `SpawnDistressCall.java`

The console mod is optional at runtime but remains a compile dependency. Command classes are included in the jar.

## `rules.csv` contract

`data/campaign/rules.csv` contains the mod's spoken text and option labels. Java supplies mechanics, state, and custom panels.

### Writing and review

- Option labels are unquoted actions.
- Dialogue supplied by the user stays verbatim unless the user asks for a rewrite.
- `LORE.md` is the authority for voice, knowledge, slang, and faction behavior.
- Every new or edited player-facing line must pass through the Starsector Editor at High thinking with the exact display context. Review the result against `LORE.md` and reject generic AI phrasing or unnecessary em dashes.
- A prose pass may change quoted text, option labels, and `AddText` content. It must not change IDs, triggers, conditions, commands, tokens, row order, facts, or routing.
- The Fisherman and Crablobab have distinct voices and must remain distinct.

### Java boundary

`CatchReleaseCMD <verb> [arg]` is the only rules command supplied by the mod. Condition verbs write temporary branch tokens and return true. Script verbs perform actions. Custom panels such as the outfitter, survey counter, map, and cargo picker remain in Java.

The rules engine replaces text tokens before running the row's script. A row therefore cannot print a value that its own script creates. Roll or prepare the value on an earlier row, then render it.

Harpooned-fleet comm greetings keep their primary prose in the `text` column. Their scripts only switch the fleet menu and populate its options.

### Job routing

Each job follows this trigger family:

| Trigger or option | Purpose |
|---|---|
| `<missionId>_blurbBar` | Bar description |
| `<missionId>_optionBar` | Bar option |
| `DialogOptionSelected` on `<missionId>_ask` | Open the offer |
| `catchreleaseJobAccepted` / `…Declined` / `…Remind` / `…Paid` | Shared lifecycle |

Important job tokens:

| Token | Meaning |
|---|---|
| `$catchrelease_jobRef` | Active `FishJob` |
| `$catchrelease_jobDeliver` | Delivery route is valid |
| `$catchreleaseHasFish` | Cargo can satisfy the request |
| `$catchreleaseAsk` / `…AskCap` | Requirement text |
| `$catchreleaseReward` / `…RewardCap` | Reward text |
| `$catchreleaseDays` / `…DaysCap` | Total deadline text |
| `$catchreleaseDaysLeft` | Remaining deadline text |
| `$catchreleasePaid` | Payout completed |
| `$catchreleaseBonus` | Bonus condition |
| `$catchreleaseMore` | Multi-round job continues |

Bar option IDs must begin with the mission ID. `BarCMD` uses that prefix to retain the active wrapper and aborts missions whose prefix does not match.

### Fleet-job routing

Fleet-job prose, option labels, titles, assignment text, distress breadcrumbs, and intel purpose and special-terms copy live in `rules.csv`. `FleetQuestType` owns mechanics only. `FleetQuest` writes `$catchrelease_fleetQuestType`, the current ask and reward descriptions, the round, the deadline, and saved case-detail tokens to entity memory; the sheet selects the matching type row from that state.

The initial, distress, follow-up, and counteroffer pitches show the case terms once, then call the shared reward-detail-card path. Question and negotiation responses return to the complete type-specific offer menu. `CatchReleaseCMD highlightJobText` colours matching fish names by rarity while ordinary ask, reward, credit, and deadline phrases retain the vanilla highlight colour. Highlight arguments follow their order in the displayed paragraph, and a repeated fish mention repeats its argument because `TextPanelAPI` consumes one occurrence per argument. Non-dialogue consumers ask the rules engine for the same type-selected title, action, breadcrumb, purpose, or terms row using entity memory, so the sheet remains the only owner of player-facing fleet-job copy. Completed cases keep only the tokens needed for the rules-authored thank-you until the player opens comms; lower-scored legacy rows still consume string-backed thank-you state from older saves.

### Fisherman dialogue

The encounter enters `$menuState == catchreleaseFisher`. The sheet owns the business menu, tutorial, outfitter questions, fish sales, rumors, chart requests, and all spoken text.

- The business menu uses the dedicated `CatchReleaseFisherOptions` trigger instead of vanilla's broad `PopulateOptions` bucket. Entry and ordinary return rows fire it directly; cancelled picker paths rebuild through `CatchReleaseFisherResume`.
- A highlighted business option is added by its option row, then coloured by a later condition-matched row in the same trigger. This keeps `SetOptionColor` after the option it mutates.
- Question topics are paged. Unasked topics appear first; answered topics appear afterward in the Common rarity colour. Previous, Next, and Back keep the menu within the engine's nine-option limit.
- A terminal answer returns through `Something else`. `I've heard enough.` is the only route from the question menu to business.
- The name question introduces “Baha” in the answer; the option does not assume the player already knows it.
- The tutorial uses one reminder and hand-in route. Fish sales unlock only after the first hand-in.
- Stage-three continuity questions remain at the root until their persistent flags are set.
- `I just left you in another system.` takes precedence over `Are you okay?` when both are eligible. If the former never became available, `Are you okay?` does not require it.
- Rumors unlock after tutorial graduation. Graduation creates one immediate lead; later rumors use the monthly gate.
- The first landed bycatch records a question topic for the next Fisherman conversation.
- Fish names use rarity colours. Places and non-fish rewards use quest yellow. Final hand-in options use quest yellow.
- Stage two speaks about the R.O.D.; stage three introduces Breach Lights and the Harpoon.
- The range-data hand-in is the only progression route for that stage. Each unlocked species gets its own vanilla-style receipt with its rarity colour.

The first chart-request offer is stored before it is described. Declining, leaving, saving, or reopening reuses the same fish and system until acceptance consumes it. An exhausted roller follows a separate no-work row.

### Dialogue maps and exits

`QuestDialogMap` provides the temporary person-info map used by remote offers and reminders. It uses the mission's stored target, refuses local or unresolved destinations, and removes only its own temporary marker.

Peaceful fleet resolutions use `CatchReleaseFleetResolutionOptions` and one Escape-bound `Leave` option. `leaveEncounter` closes the fleet encounter cleanly. Explicit cut-link and flee outcomes exit immediately. Hostile lamp escalation uses vanilla `EndConversation` so the fleet interaction can rebuild into combat options.

## Source map

### `campaign/fish` and `campaign/fish/data`

| File | Responsibility |
|---|---|
| `FishingTaboo.java` | Central list of factions that reject fishing: the Church and the Path. Bar jobs, fleet jobs, and standing Fishermen use it. |
| `FishSpec.java` | One species row: stable save ID, current display name and description, minigame values, size and price ranges, habitat, and valid catch implements. Display copy may change without renaming the ID used by existing catches and logs. |
| `FishCatch.java` | One specimen: size, weight, aberration, region, source rupture, timestamp, method, and optional chart-request provenance. Its encoded tail is backward compatible. |
| `FishGrade.java` | Five quality grades, size-to-value multipliers, and colours. Comparisons use explicit `rank` values, never enum ordinals. |
| `FishRarity.java` | Canonical rarity colours, movement values, and explicit ranks. Common uses its own beige colour rather than standard text or disabled grey. |
| `FishMotion.java` | Species movement archetypes. |
| `FishLog.java` | Persistent per-species discovery and record data. Legendary range data is always refused and repaired out of old saves. |
| `FishLogEntry.java` | Counts, records, locations, dates, and capture methods for one species. |
| `AberrationSource.java` | Registry of coherence sources, tags, reach, strength, and survey requirements. In-system reach is derived from light-year reach. |
| `Aberration.java` | Computes and caches aberration from the strongest destabilizer minus the strongest colony field. Inhabited systems are exactly Stable; colonies create five-light-year quadratic stability fields. |
| `SectorRegion.java` | Eight sector bands plus the Abyss. |
| `StarColour.java` | Reduces a system's primary star to the colour used by habitat rules. |
| `FishHabitat.java` | Cached system habitat: star, tags, region, constellation age, and coherence. |
| `FishRanges.java` | Authoritative current range test. It handles legendary hosts, frozen quest ranges, relaxation levels, system caps, and monthly reassessment. |
| `CatchImplement.java` | Whether a fish was exposed by a pond or Breach Lights. |
| `FishLocationSummary.java` | Builds a readable habitat summary from a species' criteria. |

### `campaign/fish/jobs`

All bar jobs share `FishJob` for requirements, rewards, hand-in, intel, routing, and save behavior.

| File | Responsibility |
|---|---|
| `FishJob.java` | Shared mission base. Handles person and fleet givers, contact visuals, reward cards, exact-specimen hand-in, active and completed intel, player-faction title colours, fishing-map navigation, vanilla acceptance cards, post-dialogue reward receipts, and the `rules.csv` token contract. Every generated job has one shared chance to require catches made after acceptance; the result applies to every round, is stamped at acceptance, and is carried by `FishRequirement` into dialogue and intel. Intel always leads with subclass-provided purpose prose; special terms that do not fit `FishRequirement` appear as natural prose before the exact requirement and reward lists. Intel progress notifications compare the capped counts actually displayed for every request: filling or emptying a slot sends an update, while extra matching fish beyond a filled slot do not. Exact selected specimens are passed to rewards before the next round is generated. `setDurationForAsks` sizes the deadline through `QuestDuration`, vetoes the offer when an ask cannot be filled within sensible reach, and the tokens `$catchreleaseDays`, `$catchreleaseDaysCap`, and `$catchreleaseDaysLeft` carry it into dialogue. It implements `FishAsker`. |
| `DemandScore.java` | Prices a set of `FishRequirement`s as a difficulty score: rarity bases (one unmodified common scores 10) times multipliers for specificity, grade, coherence, origin, method, implement, weight and length floors, same-species, and a post-acceptance catch; extra specimens add diminishing fractions, and `anyOf` alternatives score as the cheapest branch. Maps scores to the EASY/MEDIUM/HARD/SEVERE tiers that gate rewards. Also carries the score-targeted generation helpers: the skewed ambition roll and the count-curve inversion any demand generator can shape against. |
| `QuestRewards.java` | Central reward provider for every quest. A `Request` carries the asks, fixed always-fire rewards, exclusions, a score override, a budget multiplier, a no-credits flag, and a tier floor. Rolling converts the score into a credit budget (600 credits per point, rolled 0.75–1.35 so quests can over- or under-reward), subtracts fixed rewards, picks one tier-gated non-credit reward that fits - a second is a one-in-four lucky day - and always closes with quest credits: the remainder as the guaranteed sum plus a saved 3–10x hand-in value multiplier on top, so a prize specimen is worth money even when the rolled sum is nothing. A chart never comes alone: it brings money, or on a no-credit quest a second chart. Every reward kind has a credit valuation; habitat data is priced by rarity (5/10/15/20k), so expensive charts gate themselves naturally and an early-learned chart converts to its full worth. Cash-enabled later-stage rolls compare against the immediately preceding package and guarantee at least 20% more total value and 25% more guaranteed credits, with a 2,000-credit minimum increase for each; the saved fish-value multiplier never decreases. |
| `QuestDuration.java` | Deadline rungs of 30/60/90/120/180 days or no limit. Picks the smallest rung covering working days plus a round trip at the player fleet's vanilla base-burn travel estimate, then gives requests that specifically require a Rare or Epic catch another 30 days and requests that require a post-acceptance catch another 30 days. Also the satisfiability gate: one prefiltered pass per ask finds the nearest system with matching water, both sizing the clock and vetoing offers whose asks cannot be filled within sensible reach - species ranges move monthly, so a rolled demand can point at water that no longer exists. |
| `FishHandoffPicker.java` | Shows eligible loose fish and validates a non-overlapping assignment on confirm. Invalid selections reopen on the next frame after Starsector releases the picker modal. `autoSelect` chooses the minimum valid set, worst specimens first, across loose fish, crates, and piles. Partial containers are repacked under their original ID. |
| `FishJobAsks.java` | Rolls species, rarity, grade, quantity, weight, method, and implement requirements. It excludes legendaries and impossible method/implement combinations. |
| `FishReward.java` | Reward types: quest credits, fixed compatibility credits, upgrade and tackle schematics, habitat data, backdrops, and blueprints. Reward descriptions are lowercase modular phrases shared by dialogue and intel. Quest credits pay their guaranteed base plus their saved difficulty multiplier times the value of the exact fish handed in. Each grant captures its actual result before state changes, then supplies a small-font receipt after the hand-in scene; the normal label is `Gained:`, while fleet contracts use `Received:`. Location data therefore reports either the newly learned range or its stored fixed-credit fallback correctly, with a rarity-coloured note below the receipt block when known data was converted to credits. The commodity type remains only to convert old saves to fixed credits. |
| `FishRewardRoller.java` | Roll helpers used by `QuestRewards`: individual upgrade, tackle, schematic, backdrop, blueprint, and habitat-data rolls, distinct-data reservation, credit merging, and the fish-value multiplier distribution. That multiplier heavily favours 3x and falls through 10x, with 10x at a 0.5% chance. Schematics exclude owned plans, active-job plans, invalid rigs, and unavailable upgrade tiers. Backdrops require conservatory-plan ownership. Old saves and compatibility conversions keep their fixed credit values. |
| `QuestPond.java` | Claims ponds by a set of job IDs, adds vanilla mission importance, plants identified quest motes, and releases claims and motes. `sweep` repairs stale save data. |
| `StandingOrderJob.java` | Quantity, rarity, and grade order. |
| `AcademyJob.java` | Low-coherence specimens for Galatia or large Independent markets. Uses the shared fishing-work gate. |
| `ButlerJob.java` | One specimen above a weight floor, with an offer submenu that reveals the full terms before acceptance. |
| `ChefJob.java` | Three distinct ingredient requirements, sometimes Fine grade. Every offer fixes up to two distinct, currently unknown habitat-data rewards, so either converts to its stored credit fallback if learned before hand-in; when fewer than two remain, the freed budget rolls extra rewards instead. |
| `CompanionJob.java` | Hegemony-only private order with a weight floor and an upper-size bonus. |
| `CultJob.java` | One named species for a non-credit reward. It does not create an offer when its prize roll comes up empty. |
| `CuratorJob.java` | One to three Uncommon-or-better specimens that must be Fine or from low coherence. |
| `KidsJob.java` | Two unrestricted fish for a tournament. Rewards and bonuses cannot be credits or charts; a tier floor keeps the prize pool open. Hand-in compares two selected specimens, then spends only after assignment. Bar entry restores the normal bar image; its comm anchor uses the generic portrait. |
| `MafiaJob.java` | Supplies the main event for a bar's Fight Night. It asks for exactly two unrestricted fish, maintains the local Commissioner and House, gives both entries size-based fight names, publishes relative-size odds and the house share, and runs the same public bout after either a fixed-fee or wager choice. The paid hand-in stops on a rules-owned Continue beat before the result, settlement text, reward receipts, and exit option are shown. Successful deliveries increment a sector-wide supplier record used by later offers. Older active jobs discard the retired rarity and catch-method clauses, while legacy Salvatore and Enzo contacts are recast into the new local roles. |
| `StartupJob.java` | Three deliveries of increasing quantity. Each round resets its clock, uses a round token in dialogue, and rolls its reward above the preceding package. |
| `TuberJob.java` | Fine Uncommon/Rare specimen followed by a low-coherence specimen whose reward is rolled above the first package. |

### `campaign/fish/jobs/camp`

| File | Responsibility |
|---|---|
| `CampedSpotJob.java` | Requires the camper to be gone and a post-acceptance catch from the exact rupture. It tracks both conditions separately, updates intel, releases the pond once proof is aboard, and repairs older named-species jobs. The fleet and pond claim are created only on acceptance. The camp's credit value converts into reward score on top of the receipt ask, and the deadline covers the trip twice over. |
| `CampType.java` | Pirate, mercenary, and Pather behavior and labels. |
| `CampSize.java` | Small, medium, and large fleet estimates. |
| `CampedSpot.java` | Spawns and holds the camper, forces one warning hail, allows disengagement, removes cut-link without a Continue step, and locks the R.O.D. only while the camp remains. Peaceful clearance releases the permanent camp flags and sends the fleet back to its vanilla source before despawning. |
| `PirateCampJob.java` | Pirate protection-racket version of the camp offer. |
| `MercCampJob.java` | Mercenary boundary-dispute version of the camp offer. |
| `PatherCampJob.java` | Pather sabotage version of the camp offer. |

All three offers state the camp size, system, clear-then-catch sequence, deadline, and exact reward.

### `campaign/fish/jobs/fleet`

| File | Responsibility |
|---|---|
| `FleetQuest.java` | A `FishJob` attached to a fleet. Every construction path requires tutorial completion. Offers keep the source fleet from avoiding the player; accepted jobs replace it with a mission-owned copy and maintain a permanent map-only marker until resolution. It saves case mechanics and generated details, publishes separate original-fleet and flagship-name tokens, and exposes shared offer, counteroffer, and follow-up actions; player-facing quest copy uses the flagship name while the original fleet name is retained so completion can restore it. `rules.csv` owns the type-selected dialogue, options, titles, assignment text, distress breadcrumbs, and intel prose. Non-dialogue surfaces resolve those rows through the rules engine. Case-specific contacts replace the captain where the story calls for another speaker, including the Stranded ship's engineer. Alternate-client selection preserves the ask while replacing the reward and all later contact, hand-in, intel, and thanks state. Completed jobs resolve and store their rules-authored thank-you before mission teardown so the next comm opening does not depend on a live mission reference. Rewards come from `QuestRewards` at the type's multiplier and every pitch uses the shared reward preview cards. Accepted intel uses the common `FishJob` purpose, special-terms, exact requirement/progress, deadline, and reward-list layout. Local and distress deadlines are sized from the asks' nearest satisfiable water using the shared player-travel estimate. Claim Assay and Parley Fish acceptance stamp their catch requirements with the acceptance time before accepted dialogue and intel are rewritten. Parley Fish reserves and marks its exact in-system rupture, releases that mark while a qualifying catch is aboard, restores it if the catch is lost, and routes its intel map button to the rupture until proof is aboard and to the client afterward. Multi-stage hand-ins can pin the next request to the delivered species and reroll a strictly larger, more cash-heavy reward; first-round hand-in and receipts stop at a Continue handoff before presenting the next stage and its accept-or-decline options. Hand-in uses the shared picker, reports `Received:` receipts, then restores the fleet's original name, returns it to its source, and despawns it after the final round. |
| `FleetQuestMapIcon.java` | Map-only proxy that follows an accepted quest fleet, keeps its position known at any distance, and redirects map autopilot to the live fleet. |
| `FleetQuestSpawner.java` | After tutorial completion, gives one local offer to an eligible fleet in the player's system. Most offers use existing scavengers; `INTERMENT`, `MUTINY_POT`, and `EXHIBIT` use legitimate small Independent trade convoys. Natural checks are 7% with one live offer or wanted quest and a 45-day cooldown; completed intel does not occupy the slot. `QUIET_SHIP` extends its own cooldown to 120 days. Quest types whose premise depends on worn or thin fabric are excluded outside low-coherence systems. The test path creates a matching route-backed scavenger or convoy and observes the same geography gate. |
| `FleetQuestEncounter.java` | Runs one fleet offer, accepts or declines after dialogue closes, resolves distress entities, restores local offer marks after load, and expires old offers. An explicit decline stores the shared rules-authored follow-up for the next comm opening; silent expiry does not. |
| `FleetQuestType.java` | Mechanics-only definitions for twenty-two saved quest types. Fifteen are local fleet offers; `STRANDED`, `SCAVENGER_ENGINE`, `FOLLOWER`, `STATE_DINNER`, `CLAIM_ASSAY`, `MANDATE`, and `PARLEY_FISH` are selected by the distress provider. Demand generation runs off a skewed target score: each type pushes its own demand shape toward the rolled ambition, and the reward is then priced from the ask that actually came out. `LAST_ENTRY` uses a small Independent scavenger, asks for one specific Average-or-better Uncommon, Rare, or Epic specimen according to ambition, allows a longer 75-light-year search with its deadline sized from the player fleet's round trip, and carries one unknown range chart into the reward roll when available. `ESCROW` also uses an Independent scavenger, asks for one exact Uncommon, Rare, or Epic species, adds a Fine-grade floor at target 30, drops that floor before rarity during reachability backoff, excludes range data and backdrops from its licensed-asset reward roll, and supports the original, raised, closed, and one-story-point reclaimed negotiation states. Renegotiation preserves the rolled package and adds credits rather than rolling a replacement. `INTERMENT` uses a small Independent trade convoy and asks for one exact Uncommon, Rare, or Epic species without a grade floor. `CALIBRATION_PAIR` uses an Independent survey scavenger and a scientist contact: its first round asks for a matching pair, adds low coherence at target 30, fixes one range chart into the reward, then pins a smaller second-round request to the species actually handed in, calculates a fresh deadline, and guarantees a larger reward than the first package. `MUTINY_POT` uses an Independent trade convoy and a distinct, Bosun-ranked crew contact, requests one exact species with an ambition-scaled weight floor, drops weight before rarity during backoff, and offers mutually exclusive crew and captain reward routes; the captain pays less and cannot award a blueprint. Crew completion transfers the flagship into a one-ship fleet commanded by the bosun and sends it to a random core market. Captain completion leaves cargo pods containing ten harvested organs at the fleet's position. `TRIBUTE` uses an Independent scavenger and supply officer contact, asks for one exact Uncommon, Rare, or Epic species with a Fine-grade floor at target 22, and pays 1.25 times the generated request value from the salvage hold. `REFERENCE_SPECIMEN` uses an Independent utility-style scavenger and business contact, asks for one exact pond-taken Uncommon or Rare species, and excludes backdrops and blueprints from its League service-fee reward. `QUIET_SHIP` uses an Independent maintenance scavenger and chief contact, asks for a nearby fish, crab, or mollusc body type with an Average-grade floor at target 24, fixes one available tackle or upgrade schematic into its modest reward, and uses a 120-day offer cooldown. `EXHIBIT` uses an Independent bonded trade convoy, requests one exact Uncommon species with an ambition-scaled length floor from target 26, and excludes backdrop rewards. `HEADLINER` uses an Independent scavenger-class show boat, requests a Rare-or-better specimen with an optional Fine-grade floor, lowers the rarity to Uncommon during reachability backoff, and fixes one available backdrop into a hard-tier reward. `FOLLOWER` uses a Hegemony supply fleet, requests one nearby exact species with an ambition-scaled weight floor, and excludes backdrop rewards. `STATE_DINNER` uses a Diktat liner fleet, requests two to six specimens of one nearby common species with an optional Fine-grade floor, and excludes blueprints. `CLAIM_ASSAY` uses a Tri-Tachyon prospecting fleet, verifies one satisfiable current-system catch with an optional Uncommon floor, adds the acceptance timestamp only when the contract is taken, and excludes range data and backdrops. `MANDATE` uses a League science convoy, is eligible only within sensible travel distance of abyssal water, requests one abyssal-provenance specimen with an optional Uncommon floor, and fixes one available equipment or upgrade schematic into its reward. `PARLEY_FISH` uses a pirate combat formation orbiting a free in-system rupture, requires a post-acceptance specimen from that exact rupture with an optional Average-grade floor, and pays 1.2 times the generated request value without exclusions. `STRANDED` keeps its nearby one-or-two-specimen demand and 1.15 reward multiplier and replaces the captain with a practical engineer. Distress requests source species from their own or the single nearest system unless a type requires the current system, exact rupture, or abyssal provenance, prefer home, and size their deadlines through the shared travel estimate. |
| `CatchReleaseDistressProvider.java` | Adapter between the generic distress framework and `FleetQuest`. Applies tutorial and one-job gates, maps CSV cases to quest types, enforces case-local geography such as the Mandate's abyssal proximity and the Parley Fish's free-rupture requirement, asks the live quest for its rules-authored breadcrumb copy, chooses the Parley Fish rupture as its fleet orbit anchor, prepares the quest, and abandons it if the distress fleet expires. |

`STARVING` uses an Independent galley-chief contact, requests three to eight specimens of any species, and pays 1.1 times the generated request value from the full reward pool. Its dialogue and intel update the printed-protein day count throughout the offer, waiting, and accepted states.

`COLLECTOR` uses an Independent business contact, requests one exact Uncommon, Rare, or Epic species with a Fine-grade floor at target 70, and pays 1.3 times the generated request value from the full reward pool. Its two looping questions expose the saved broker record and prepared display tank, while accepted intel records the direct-catch commission.

`WAGER` keeps its two-of-one-species request, adding a Fine-grade floor at target 25 and an Uncommon floor at target 40, and pays 1.15 times the generated request value from the full reward pool. Its captain presents the competing watch logs and exposes two independent looping questions before the player chooses whether to take the job.

`SCAVENGER_ENGINE` keeps its nearby one-or-two-specimen demand, pays 1.2 from the full salvage reward pool, replaces the captain with a coil technician, and carries a looping repair-procedure question plus complete distress dialogue and intel. Its manual revision and live coil-condition readout are saved case details rendered consistently across repeat contact.

`SEEKER` now rolls one exact Uncommon, Rare, or Epic species according to ambition, adds a Fine-grade floor at target 60, and pays 1.25 from the full reward pool. Its guarded captain, missing-collection question, and all offer/intel states use Editor-authored copy while the existing single-stage fleet workflow remains unchanged.

`QUOTA` remains the real fishing-crew offer with its Fine-grade two-to-six-specimen demand. It now uses a purser contact, a 1.0 operating-margin reward multiplier with blueprints excluded, saved contract/filing details, and Editor-authored occupational dialogue, looping range question, and accepted intel.

### `campaign/fish/colony`

| File | Responsibility |
|---|---|
| `BreachConservatory.java` | Industry definition and aquarium state: stock, enabled state, and selected backdrop. It gates construction on learned industry plans. |
| `ConservatoryOptionProvider.java` | Adds outfitter and aquarium options to the colony screen. |
| `AquariumManageDialog.java` | Stocks, empties, enables, disables, and changes the aquarium. Backdrops are paged six at a time, rarity-coloured, and previewed with a live tank. |
| `AquariumTransfers.java` | Vanilla cargo pickers for transfers. Depositing unboxes fish first; aquarium fish are already loose. |
| `AquariumTankScript.java` | Mounts the tank below the colony image whenever no covering visual is open, and removes it when another visual takes over. |
| `AquariumTankPanel.java` | Draws water, caustics, light shafts, scenery, and specimens. Body type controls posture: fish pitch within limits, molluscs list, and crabs stay on the bottom. |
| `Backdrop.java` | One `backdrops.csv` row: name, art, rarity, Crablobab stock, and starting ownership. |
| `Backdrops.java` | Separates campaign-wide backdrop ownership from the scene selected by each conservatory. |

### `campaign/fish/fisherman`

The same persistent Fisherman appears on every boat. Inhabited systems have a standing boat with one shared chart shelf. Uninhabited systems may receive one temporary visitor with its own shelf. His portrait follows local coherence.

| File | Responsibility |
|---|---|
| `FishermanSpawner.java` | Rolls and reconciles the temporary visitor. It allows one visitor sector-wide and one Fisherman per system, repairs old pointers and duplicates, excludes the Longliner decoy, and yields to tutorial postings. Its test entry bypasses only the natural roll. |
| `CoreFisherSpawner.java` | Maintains one standing boat in each eligible inhabited system and re-creates missing boats weekly or when the player arrives. It reuses any canonical local Fisherman. |
| `CoreFisherBehavior.java` | Permanent outer-reaches schedule for a standing boat. |
| `OuterReaches.java` | Chooses destinations and straight-line legs that avoid inhabited inner orbits. Placement is clamped only in inhabited systems. |
| `FishermanBehavior.java` | Controls lamps, staged motes, pacing, visibility, visit duration, and departure. It keeps the Fisherman non-hostile, removes mutual fleet interference, and prevents player-avoidance routing. |
| `FishermanShelf.java` | Stores each boat's two initial habitat-data slots, duplicate prevention, and sale-based 30-day restocking. Legendaries are never stocked. |
| `FishermanQuest.java` | Chart requests for one identified specimen from one system. A pending offer survives declines and saves. Accepted catches carry a unique target ID, system, timestamp, and method through cargo containers. The quest uses `FishRequirement` and `FishCurrency` for progress, picker eligibility, and spending. Completion pays credits, widens the shelf, prints both results through the shared post-dialogue receipt format, removes duplicate notes safely, and starts a 90-day cooldown. Its intel uses player-faction title colours, the Fisherman portrait and Independent crest, live progress, method, destination, and shared navigation. |
| `FishermanSurveyDialog.java` | Custom chart shelf with silhouette cards. It clears host options before opening and restores the rules menu exactly once on close. |
| `FishermanMapIcon.java` | Map-only marker for an undetected local Fisherman. It disappears when the fleet itself is detected, removes stale duplicates, preserves the authored name, and redirects autopilot to the moving fleet once per second. |
| `FishermanIdentity.java` | Stores the shared `PersonAPI` and selects one of five coherence portraits immediately before a hail. It clears rank and post and repairs old saves. |
| `FishermanBycatch.java` | Persists the first unexplained bycatch until the player asks about it. |
| `FishRumors.java` | Stores one monthly rumor about rarity, treasure, or a non-legendary stranger species. Dialogue owns the spoken rumor. Intel uses the Fisherman icon, player-faction title colours, an expiry timer, and correct navigation. Graduation creates one immediate rumor without consuming the monthly gate. |
| `FishermanConstants.java` | Fisherman tuning values. |

The encounter enters vanilla comms through `catchrelease_fisherEncounter`. Range data, the outfitter, sales, rumors, and chart work are rules-sheet menus, not separate interaction plugins.

### `dialogue/rules`

| File | Responsibility |
|---|---|
| `CatchReleaseCMD.java` | Rules-to-Java bridge. Supplies tokens, conditions, panels, tutorial and job actions, fish/reward highlights, paged Fisherman questions, Crablobab options and prices, bulk-sale previews, map handoffs, clean fleet exits, and inline tutorial intel. Panel returns restore the previous rules plugin and rebuild options once. |
| `QuestDialogMap.java` | Shared temporary sidebar map for remote dialogue targets, matching vanilla mission icons, tags, and colours. |
| `FishBuyer.java` | Fish-sale picker and Common-to-Epic bulk sales. The preview is immutable and is revalidated before sale. It can crate selected fish, refreshes both cargo views through the stable UI capability, unboxes temporary crates on exit, and protects every specimen required by active `FishAsker` entries or marked gear. |

### `campaign/fish/tutorial`

The tutorial has six lessons, a returning-player skip, and a developer shortcut. It gates equipment and downstream work, not the existence of the Fisherman trade.

| File | Responsibility |
|---|---|
| `FishingIntro.java` | Owns stages, targets, grants, save repair, and `IntroIntel`. Intel uses player-faction titles, lesson-specific icons, deferred notifications, shared map navigation, live per-species progress, and Fisherman visuals where appropriate. The first R.O.D. lesson accepts any drone catch made at the marked rupture after assignment; later pond lessons plant their named targets. All completion and spending use `FishRequirement` and `FishCurrency`, including catches inside crates and piles. Single-target lessons count valid same-rarity misses across locations where the target can naturally spawn; the fifth qualifying catch is replaced with the target. Invalid locations neither advance nor reset the count. The final multi-species habitat-data lesson is excluded. Target selection uses real `FishHabitat`, `FishRanges`, and method constraints and refuses impossible targets. The stage-two Fisherman posting is reserved and repaired across loads. Habitat-data grants produce one rarity-coloured vanilla receipt per species. The returning-player skip is a gold Fisherman option at `UNSTARTED` or `POINTED` only, gated by the Luna setting and `$catchreleaseCanSkip`. It calls the same grant path as the dev shortcut, but only normal tutorial completion enables and saves the setting. The saved object is mirrored directly into LunaLib's cache without reloading the settings backend or firing its global notification lifecycle. |
| `TutorialWreck.java` | Creates a vanilla derelict cruiser beside the first suitable rupture. It carries the damaged LYNE assembly, uses normal salvage behavior, and removes its marker after recovery. |
| `Castaway.java` | Stores planet eligibility and rescue state for the rating encounter. A high-score rules row temporarily takes over an eligible planet interaction. No entity or listener is spawned. |
| `RatingBarEvent.java` | Counts eligible port visits for the rules-based rating event. |
| `FishermanInterception.java` | Moves the Fisherman into position when an unequipped player nears a rupture, then lets him close at more than burn 4. Placement remains in the outer reaches. |
| `TutorialConstants.java` | Tutorial ranges, timing, persistent IDs, the five-catch protection threshold, and the tutorial-skip Luna field. |

### `campaign/fish/minigame`

| File | Responsibility |
|---|---|
| `FishingMinigame.java` | Physics, progress, escape, and treasure rules. Tuned for flat player power - there are no minigame upgrades - so the difficulty response is a square root, not a line, and per-fish progress/escape extremes are compressed toward baseline (`compressRate`): the sheet's ordering survives while the top of the ladder stays chaseable by a bare bar. Hooked legendaries skip the normal treasure roll and receive at least three Epic rewards. |
| `FishingMinigamePanel.java` | Draws the track, target, progress, and treasure; handles input; records bycatch, catch intel, route progress, and legendary completion. Unidentified targets use a rarity glow or the Chicken Profile icon; Sonar shows the species. Owns caught, failed, click, line-loop, treasure-hover, and treasure-pickup sound hooks. |
| `FishingMinigameDialogPlugin.java` | Hosts the custom visual, preserves source rupture and quest identity for drone and harpoon catches, applies tutorial catch protection, preserves campaign music, and exposes dev reopens that bypass substitution. |
| `FishingMinigameLayout.java` | Per-frame layout for the track, progress meter, and result cards. |
| `CatchResultPanel.java` | Reveals catch statistics. New species use a gold heading and aquarium-style light shafts; new records stay green and use bubbles only. |
| `LootResultPanel.java` | Reveals treasure after the fish tally, switches from closed to open chest, plays the opening cue, and wraps long item names without widening the panel. |
| `CatchCelebration.java` | Optional purchased celebration: flash, backlight, flourish, confetti, and shared UI-sound helpers. |

### `campaign/fish/treasure`

| File | Responsibility |
|---|---|
| `MinigameTreasure.java` | Timed stationary pickup that requires the indicator to remain over it. |
| `TreasureRoller.java` | Rolls whether treasure appears and selects its contents. |
| `TreasureAward.java` | Immutable reward data for the result panel. |
| `TreasureRarity.java` | Four reward tiers with weights, colours, and explicit ranks. |

### `campaign/fish/entities` and `campaign/fish/spawner`

| File | Responsibility |
|---|---|
| `FishEntityPlugin.java` | World fish mote: movement, depth, held and stunned states, glow, source rupture, and legendary behavior. Lamp-only patterns and legendary constructs fade with the Breach Lights and cannot be targeted in darkness. Phantom motes remain unavailable to ordinary gear. Legendary defenses delegate to `LegendaryShields`. |
| `GhostAsteroidEntityPlugin.java` | Non-interactive drifting haunt asteroid. |
| `HauntMineEntityPlugin.java` | False Dawn mine. Red shoves, blue interdicts and drags, yellow pulls. Mines trigger by proximity or harpoon and clean themselves up after firing. |
| `BuriedMoteEntityPlugin.java` | Invisible open-water fish. `unearth()` atomically replaces it with a normal mote. |
| `PondFishSpawner.java` | Selects species by habitat, range, implement, weights, tackle, and rumor effects. Stranger rumors bypass only the range gate. The Longliner never spawns here. |
| `BuriedMoteSpawner.java` | Maintains a buried-mote population around the player. |

### `campaign/fish/shop`

The top-level Outfitter tabs are Upgrades, Equipment, and Extras. Player-facing text calls upgrade levels “tiers” and calls modules Harpoon Tips, Drone Cores, or Lens Arrays. `Tackle` is an internal and serialized name only.

| File | Responsibility |
|---|---|
| `FishShopDialog.java` | Outfitter tabs, list, detail panel, automatic or player-selected fish payment, and session undo. The automatic upgrade button previews the exact specimens selected for payment and spends that same selection. Upgrade and equipment purchases with a fish price place `Select...` beside the primary action; it hands the modal to the shared specimen picker and reopens the Outfitter on the next frame. Upgrade and module details show their mechanical descriptions. A top-right help mark explains tier stacking, equipment refitting, and automatic catch selection. Undo restores exact cargo, credits, gear, tiers, and marks. Unknown modules are hidden; gated upgrade tiers remain visible and explain their schematic requirement. Every tier pip has its own tooltip hotspot. Host options are cleared before opening and restored exactly once. |
| `ShopEntry.java` | Uniform wrapper for upgrades, modules, and curio switches. Exposes their mechanical descriptions, enforces schematic permissions, and supports automatic or caller-selected fish payment through the same purchase validation. Resolves item or category icons and keeps unknown modules out of the visible list. |
| `ShopGroup.java` | Defines shelves, related stats and rigs, tab art, fallback icons, and the player-facing module noun for each rig. |
| `ShopPricing.java` | Seeded credit-and-fish prices. Breach Coupler is the unique highest module tier; Retrieval Head is one tier below. Exact-tier lookup supports reward previews. |
| `ShopMarks.java` | Persistent shopping list. Upgrade marks identify an exact tier; module marks identify the module and rig. Learned plans and active `FishAsker` entries drive yellow wanted dots and tooltip reasons. Old whole-ladder keys migrate to the next tier. |
| `FishAsker.java` | Interface implemented by jobs, tutorial intel, and chart-request intel so the shop, cargo, and route planner can read fish requirements uniformly. |
| `FishCurrency.java` | Counts and spends matching fish. Purchases take loose specimens before opening bundled catch; unnamed same-species requirements use the eligible species with the largest count aboard. |
| `FishRequirement.java` | Describes and evaluates count, rarity, grade, species, region, source rupture, timestamp, coherence, method, and implement. Formats live progress and applies canonical rarity colours. Also tests whether any species could satisfy the requirement. |
| `ShopStorage.java` | Migration shell for the removed storage counter. |
| `ShopSchematics.java` | Saves quest-earned permissions for stocked modules and the last two tiers of each upgrade. Tracks fresh plans for `New!` labels, unlocks gated tiers in order, migrates owned gear, and provides a dev-only bulk grant. |
| `ShopRowPlugin.java` | Shared list-row rendering, purchase state, wanted ring, tier pips, `New!` labels, and transparent tooltip hotspots. |
| `ShopTabPlugin.java` | One outfitter tab. |
| `ShopHeaderPlugin.java` | Title, credits, and fish balances by rarity. |
| `ShopDetailHeaderPlugin.java` | Selected item/category image, name, and tier state. |

Shared drawing helpers are in the top-level `ui` package.

### `campaign/fish/items`

| File | Responsibility |
|---|---|
| `FishItems.java` | Item IDs, encoding, decoding, landing, unboxing, and transaction-screen packing. Its post-stow hook updates chart requests, tutorial tasks, jobs, routes, and intel. |
| `FishItemPlugin.java` | One loose specimen. Right-click packs it while preserving the clicked cargo cell. Tooltips show coherence and every job or purchase that requires it. Also owns the canonical five-band coherence labels, beginning with Unsettled after Stable. |
| `FishBundleItemPlugin.java` | One-species crate. Unpacking preserves the cargo cell; Ctrl-packing creates a pile. The fish icon is perspective-fitted onto the box label. |
| `FishPileItemPlugin.java` | Mixed-species pile. Unpacking keeps existing cargo cells stable and restores singleton contents as loose fish rather than one-fish boxes. |
| `FishItemRenderer.java` | Draws fish art plus rarity and grade pips, including the blueprint-style four-corner box-label pass. |

### `campaign/fish/crab`

Crablobab is a market-local bar roll with one persistent identity. Dialogue and option order live in `rules.csv`; Java owns stock, prices, ownership, and cards. His portrait is loaded from the `graphics.characters` registry. Merchandise screens always restore his person card rather than leaving their visuals in the bar.

The five regular wares use credits-and-crabs prices. Switchable curios route through the one-time “Baha?” explanation and are toggled later in the Fisherman's outfitter. Celebration Charges disappear after purchase. Explosive Heads return to stock after detonation. Backdrops and conservatory plans are unavailable until their progression gates are satisfied. When regular stock and the local backdrop slot are empty, he sells an overpriced Terrible Green Bass.

| File | Responsibility |
|---|---|
| `CrablobabBarPresence.java` | Uses vanilla bar seeding and `barEventProbOneMore` to roll independently per eligible market, caches until reroll, caps the cache at 60 days, and always succeeds in dev mode. |
| `CrablobabIdentity.java` | Persistent Crab Merchant person and registered portrait. Repairs rank data without adding him to the market or comm directory. |
| `CrabWares.java` | Defines regular wares, prices, ownership, switches, the fallback bass, repeatable Explosive Head, and latest explosive target. Conservatory plans are a vanilla `industry_bp` item and count as owned if the faction already knows the industry. |
| `CrabBackdrops.java` | Rotates one backdrop per port from `backdrops.csv`. The market keeps its offer until sale, then waits 60 days. Owned scenes are excluded. Rotation remains disabled until conservatory plans are owned. |

### `campaign/fish/tackle`

| File | Responsibility |
|---|---|
| `Tackle.java` | Defines modules, rig compatibility, mechanical descriptions, optional icons, modifiers, and stock state. `BREACH_COUPLER` enables lamp openings for drones; `RETRIEVAL_HEAD` refunds a capped harpoon charge after a confirmed mote hit. |
| `TackleManager.java` | Separates module ownership from the module fitted to each rig. `get()` never returns null. Consumables are removed from both ownership and the slot. Prerequisite modules stay out of shop and rewards until valid. |

### `campaign/fish/map`

| File | Responsibility |
|---|---|
| `FishMapFilterScript.java` | Installs the hyperspace-map filter, resizes the map, mounts map UI, draws routes, and stages external handoffs from Codex and intel until the map is ready. It preserves an already-open map on Codex return and handles no-data states without changing saved range knowledge. |
| `FishMapPane.java` | Search, type filters, species list, coherence toggle, request restrictions, and no-data/reset states. Intel handoffs replace stale pane state and can show a restricted union for broad requirements. |
| `FishPresence.java` | Authoritative visibility of species and systems. Normal play uses caught or learned range data; dev mode computes the full chart without changing the save. Optional allowlists constrain intel requests. |
| `FishPresenceField.java` | Builds smoothed metaball range meshes. |
| `FishPresenceOverlay.java` | Draws range meshes, overlap stripes, route badges, saved and current routes, clear/track controls, and the coherence heat map. Clearing the live plot leaves tracked route intel intact; a transparent hotspot gives the hand-drawn clear control a stock tooltip. Uncharted focused species show a centered red `NO DATA` state. |
| `CoherenceHeatField.java` | Samples `Aberration` on a light-year grid over the exact sector bounds. Uses uncapped abyss depth and the same colony stability fields as gameplay. |
| `FishSystemPane.java` | System-view fish list and handoff to the main fishing map. |
| `FishHolderPlugin.java` | Reusable circular species holder with rarity ring, art or silhouette, and wanted mark. |
| `FishListRow.java` | Shared species row with caught state, wanted tooltip, and F2 Codex link. |
| `FishRoute.java` | Ordered live route and saved stop representation. |
| `FishRoutePlanner.java` | Builds route suggestions from every `FishAsker` and shop mark, expands broad requirements, and orders stops using stability and slipstreams. All suggestions require visible range data, except computed dev data. Route stops use the same chartable-system policy as the sidebar, so reachable hand-authored systems remain valid parts of a known range. |
| `FishRoutePopup.java` | Sidebar route builder with search, filters, up to five species, and plot action. |
| `FishRouteSaveDialog.java` | Names and optionally annotates a route before creating `FishRouteIntel`. |
| `FishTooltips.java` | Shared species tooltip. |
| `FishIntelPlanetPanel.java` | Adds fish information beside the selected intel planet card. |
| `FishType.java` | Map categories, colours, and registered widget sprite IDs. |
| `CoreUiCrawler.java` | Narrow reflection helper that locates the obfuscated map filter row. |

### `campaign/fish/codex`

| File | Responsibility |
|---|---|
| `FishCodex.java` | Registers the category and species entries and owns guarded F2/custom links. |
| `FishCodexEntryState.java` | Central `UNKNOWN`, `RANGE_DATA`, and `CAUGHT` policy for visibility, links, art, description, records, and map access. |
| `FishCodexEntry.java` | Renders one entry. Range-only entries use live silhouettes; caught entries use full art and description. Known ranges open the pre-filtered map. Legendary pages explain uniqueness and the absence of range data. |

### `campaign/fish/legendary`

Legendary fish are unique, lamp-only targets with one host system and no purchasable or quest-granted range data. Their normal and decoy state remains disabled until the tutorial is complete.

| File | Responsibility |
|---|---|
| `LegendaryChases.java` | Persistent host, sighting, provocation, Longliner reveal, completion, and defense state for each legendary. Uncaught fish relocate after 90 unseen days, never while the player is present. Longliner relocates after a revealed departure. Host selection prefers uninhabited systems and repairs null-host saves. |
| `LegendaryHaunt.java` | Transient coordinator. Starts only after a provoked legendary has been seen, eases modules in and out around visibility, and removes all haunt state on departure, completion, relocation, load cleanup, or test reset. |
| `HauntModule.java` | Advance-and-cleanup contract for a haunt effect. |
| `BaseHauntModule.java` | Tracks spawned props, chooses positions near the player, and removes fleets and entities immediately. |
| `DistractionMotesModule.java` | Spawns untouchable phantom motes in the legendary's colours. |
| `InterdictionPulse.java` | Shared contact-triggered burn-ability cooldown and release logic. |
| `MinefieldModule.java` | Places False Dawn mines across the player's course and removes them on cleanup. |
| `SensorGhostsModule.java` | Sends vanilla sensor ghosts through the sensor bubble, including some that track the player. |
| `GhostFleetsModule.java` | Creates up to two unclickable, comm-dead, dark-transponder intercept contacts that vanish on arrival. Uses vanilla-style member creation and detection bonuses. |
| `FakeWrecksModule.java` | Creates visible but non-interactable wreck bait with manually added sensor profile and range. |
| `ChromaticAberrationModule.java` | Drives the full-screen chromatic effect used by the manta. |
| `CoherenceSurgeModule.java` | Holds the coherence overlay at full strength for the False Dawn. |
| `SlipDashModule.java` | Gives the moray frequent curved escape dashes and builds a moving vanilla slipstream behind it. Segments fade rather than being removed so texture offsets remain valid. |
| `QuorumShellGame.java` | Rebuildable three-body endgame after the escort is gone. One body is real; decoys use splinter catches and presented Quorum colours. Results reveal the body only after landing. |
| `LonglinerDecoy.java` | Full Fisherman-like fleet used as the Longliner's disguise. It is excluded from Fisherman reconciliation and reveals only under the player's Breach Lights. Revelation reports and immediately removes the boat, then replaces it at the same position with a mote. The mote drifts for one second along the boat's last travel direction, shows the alert floaty with the positional discovery sting, waits another 0.3 seconds, and then begins its existing escape. |
| `GhostAsteroidsModule.java` | Moving field of harmless ghost asteroids that reseeds near the chase. |
| `LegendaryShields.java` | Shared shield radius, state, visuals, and per-species defense rules. Handles the Longliner explosive-only shield, Quorum escort and regeneration, Lantern Jack stored shells and prey lure, ordinary regrowing shells, first-hit provocation, flee/prowl exceptions, status text, and persistent defense state. |
| `MoteDashModule.java` | Moray countermeasure that throws nearby ordinary motes at the fleet. Contact interdicts; missing ammunition is replaced by temporary common props. Quest and shell-game motes are excluded. |

The associated `entities/HauntMineEntityPlugin.java` implements mine behavior. `ChromaticAberrationModule` uses `rendering/plugins/ChromaticAberrationOverlay.java`; `CoherenceSurgeModule` uses the coherence overlay.

### `campaign/fish/coherence`

| File | Responsibility |
|---|---|
| `CoherenceOverlayScript.java` | Chooses the strongest active source among a running rig, nearby open pond, Fisherman, and legendary haunt floor; eases the effect and manages its whisper loop. |
| `CoherenceTerrain.java` | Invisible whole-location terrain used to add the terrain-bar line while the overlay is active. |

### `campaign/fish/constants` and `campaign/fish/intel`

| File | Responsibility |
|---|---|
| `FishConstants.java` | Minigame, result, celebration, treasure, Codex, input-sound, click-edge, and campaign-music constants. |
| `FishIntelIcon.java` | Resolves lamp-only, rupture-only, or mixed/open intel icons from all requirement branches. Drones and exact ponds are rupture-only; an unconstrained Harpoon remains open. |
| `FishIntelMapButton.java` | Shared navigation contracts: open the fishing map for habitat targets, plot a route for known systems, or set autopilot for non-fish objectives. |
| `FishIntelNotifications.java` | Queues new tutorial, chart, rumor, and custom fish intel for the first unpaused frame after dialogue. Updates use a zero-day delayed script. Inline rendering shows the same vanilla card without consuming the queue. |
| `CatchLogIntel.java` | One silent entry per landed specimen under `Catch log`. Uses player-faction title colours and records grade, dimensions, coherence, gear, date, system, value, chart provenance, and bycatch. |
| `FishRouteIntel.java` | Persistent, independent copy of a tracked route with name, note, saved stops, caught counts, methods, live relevant requests, map arrows, replot, and stop-tracking actions. Clearing or replacing the live planner route does not remove the copy. Catch progress uses shared delayed intel updates. |
| `FishMapIntel.java` | Dead save-compatibility shell. |

### `campaign/ponds`

| File | Responsibility |
|---|---|
| `terrain/MaskedFishingPondTerrainPlugin.java` | Pond activation, motes, depth, rendering, temporary ponds, and visual-only ponds. Discoverable ponds use the registered unstable-fabric map icon. |
| `listener/PondCreator.java` | Populates entered systems from planet count, capped at two ponds, and finds clear positions away from planets, ponds, nebulae, and rings. |
| `listener/OnJumpPondSpawner.java` | Runs pond creation when the player enters a system. |
| `scripts/PondCameraFocusScript.java` | Smoothly acquires and releases camera control around an open pond. The live Luna setting can disable snapping without disabling lifecycle cleanup. It snapshots the current viewport on every acquisition. |
| `renderer/PondDepthField.java` | Draws motes of light spiraling below the surface. |
| `renderer/PondHoleRenderer.java` | Dormant stencil-and-gradient pond renderer. |
| `renderer/RippleData.java` | One ripple emitter. |
| `renderer/UnstableFabricRippleTerrainRenderer.java` | Adds randomized secondary ripples around the main pond ripple. |
| `constants/PondConstants.java` | Placement, camera, timing, depth, hole, and opening-effect constants. |
| `entities/StenciledFishingPondEntityPlugin.java` | Dead custom-entity implementation retained for compatibility. |

### `campaign/crime`

| File | Responsibility |
|---|---|
| `LampOffence.java` | Defines the inhabited-world distance gate, consequences, and per-faction/per-system warning history. Every faction enforces only while the player is within 3,000 units of an inhabited market. The four-step ladder is warning, fine, inspection, and guns. |
| `LampPatrolResponse.java` | While lamps are on and the player remains inside the inhabited-world radius, every eligible patrol that sees the player pushes an intercept ahead of its current assignments. Leaving that radius cancels the temporary intercept without delaying a response on re-entry. The first patrol to open dialogue claims the incident; all others remove only the temporary intercept and resume. Turning lamps off ends the burn but does not cancel committed stops. |
| `HarpoonOffence.java` | Per-faction, per-system hit history, debts, reputation loss, witness state, and response ladders. Combat fleets turn hostile on the second hit. Outmatched civilized crews report immediately; other civilians progress through warning, repair demand, and reporting. Once hostile, strong fleets intercept and weak fleets flee. |
| `HarpoonPatrolResponse.java` | Sends one collector at a time in the incident system. The offended faction or a faction with at least favorable relations may collect, except Path and pirates collect only their own claims. |
| `HarpoonWitness.java` | Makes a civilian seek a patrol. The report occurs only on arrival and keeps the original revenge-contract eligibility. Replacement responses cancel the witness task without clearing new assignments. |
| `HarpoonHitman.java` | 30% revenge-contract roll for eligible colonial factions and eligible victims. Bounties and low/no-reputation targets are excluded. Dispatch waits one month and survives the client's final-colony loss as `Call From The Grave`. The first hail offers an 80,000–120,000 credit bribe; later comms are denied. The fleet has no reputation impact. |
| `HarpoonedFleetFID.java` | Vanilla fleet interaction plus the owed-repair line and one-shot auto-open comm request. Comm highlighting follows outstanding debt, not the longer incident-memory window. |
| `CatchReleaseCampaignPlugin.java` | Selects `HarpoonedFleetFID` at narrow priority for recorded offences and one-shot hauled-fleet contact requests. |

### `abilities`

| File | Responsibility |
|---|---|
| `FishingRigs.java` | Reports whether any fishing rig is active. |
| `charges/BaseChargedSkillshotAbility.java` | Shared charge pool for charged abilities. Disables them in hyperspace and keeps partial-charge UI accurate. |
| `rod/ability/PondInteractionAbilityPlugin.java` | Opens ponds, launches and recalls drones, and supports lamp fishing with Breach Coupler. Camps block new pond deployments, but never recall. An opening mote blocks duplicate casts. |
| `rod/entities/RodMoteEntityPlugin.java` | Flies to and opens a pond and provides the authoritative in-flight opener state. |
| `rod/entities/FishingDroneEntityPlugin.java` | Drone launch, orbit, timed chase, catch, and return. The chase-duration upgrade controls when an unsuccessful chase is abandoned. A target-lock sound plays only when a drone acquires a new target directly from its resting orbit; target switches and reacquisitions before it rejoins the ring stay silent. Held catches are unavailable to every other rig. |
| `rod/scripts/FishingDroneSwarmScript.java` | Owns one cast, staggered launches, recall, target assignment, hit cues, and reachability checks. The rarity-priority ladder progressively raises the chance that an idle drone selects rarer available patterns first. |
| `rod/scripts/RoamingDroneSwarmScript.java` | Pondless Breach Coupler swarm. It catches only buried motes fully lit by Breach Lights and recalls if the opening or coupler is lost. |
| `rod/rendering/FishingRingRenderer.java` | Fishing-radius ring. |
| `rod/rendering/FishingDroneDebugRenderer.java` | Dev-only drone geometry. |
| `rod/animation/Flash.java` | Short additive flash. |
| `rod/constants/RodConstants.java` | Drone movement, ring, timing, and sound values. |
| `harpoon/ability/HarpoonAbilityPlugin.java` | Fires or cuts the line, applies aim assist, uses the explosive icon while that head is fitted, owns charge-ready policy, and receives Retrieval Head refunds. |
| `harpoon/entities/HarpoonEntityPlugin.java` | Flight, collision, shields, mines, hauling, fleet contact, rope, catch, and return. Preserves pond and chart provenance. Explosives consume the head and never land fish; unshielded legendaries dive instead of dying. When the player pulls a weaker fleet, touching circles cuts the line and opens comms. Pulling toward a stronger anchor never does. |
| `harpoon/constants/HarpoonConstants.java` | Flight, collision, hauling, rope, sounds, alternate icon, and explosive visual tuning. |
| `searchlight/ability/SearchlightAbilityPlugin.java` | Breach Lights activation, spool, slow, detection penalty, and all beam renderers. Distinguishes lit, detected, and breaching states and exposes raw beam strength. Cleans up every location-bound transient effect on shutdown, replacement, load repair, and location change. |
| `searchlight/scripts/Searchlight.java` | Beam sweep, lock-on, distortion, and ripples. Persistent beams verify same-location ability ownership each frame. |
| `searchlight/rendering/SearchlightGlowRenderer.java` | Circular light beam and synchronous zero-duration cleanup. |
| `searchlight/rendering/SearchlightFanRenderer.java` | Fan light beam. Player beams use current area upgrades; fixed beams, including the Fisherman's, do not. |
| `searchlight/rendering/SearchlightBreachRenderer.java` | Circular world-anchored hyperspace window with parallax. |
| `searchlight/rendering/SearchlightFanBreachRenderer.java` | Fan-shaped window using exactly the fan light's geometry and alpha falloff. |
| `searchlight/rendering/SearchlightBurnRenderer.java` | Dormant older burn effect. |
| `searchlight/rendering/SearchlightImpressionRenderer.java` | Combines passive dents and beam-revealed fish for all lights. Tracking fades the mote and impression together and validates ability/location ownership. |

### `distress`

Read `distress/README.md` before changing this reusable framework.

| File | Responsibility |
|---|---|
| `DistressCallFramework.java` | Idempotent entry point, provider registry, resolution API, logging, merged IDs, and test hooks. |
| `DistressCallSettings.java` | Mutable paths, memory keys, route ID, concurrency, and reservation tuning. |
| `DistressCallSpec.java` | Validated merged-CSV row: provider, weight, fleet shape, limits, trigger, and opaque tags. |
| `DistressCallRegistry.java` | Loads merged `distress_calls.csv` and rejects malformed rows without spawning anything. |
| `DistressCallProvider.java` | Third-party seam for eligibility, fleet preparation, optional same-system orbit anchor and breadcrumb copy, expiry, and resolution. |
| `DistressCallInstance.java` | Save-safe IDs and live entity/intel handles; also the rules `Call` target. |
| `DistressCallManager.java` | Persistent coordinator. Watches vanilla's real distress interval, yields when vanilla creates an event, shares system reservations, creates the fleet and vanilla-style breadcrumb, applies optional provider-authored orbit anchors and breadcrumb copy, and contains no quest logic. Tests use the same gates and route creation. |
| `vanilla/NearbyEventsBridge.java` | Only protected-state seam. Reads `NearbyEventsEvent` interval and timeout through `ReflectionUtils` and fails closed instead of running a parallel scheduler. |
| `vanilla/VanillaDistressCallSpawner.java` | Test bridge that invokes one of vanilla's four distress generators and adds the normal breadcrumb intel. |

### `skillshot`

Read `skillshot/README.md` before changing this reusable framework.

| File | Responsibility |
|---|---|
| `SkillshotFramework.java` | Register, reset, and log entry point. |
| `SkillshotSettings.java` | Mutable sprites, sounds, dimensions, colours, and ability tag. |
| `GuideLineStyle.java` | Solid, dashed, and dotted line styles. |
| `ability/BaseSkillshotAbility.java` | Shared activation, tooltip, and block-reason behavior. |
| `ability/SkillshotAbility.java` | Interface consumed by the input layer. |
| `input/OnKeyPressSkillshotListener.java` | Ability keys 1–9: hold to aim and release to fire. |
| `input/OnClickSkillshotListener.java` | Waits for the next campaign-map click. |
| `input/SkillshotActivationManager.java` | Keeps one targeting session active. |
| `input/SkillshotInputListener.java` | Active/reset contract. |
| `render/SkillshotRenderer.java` | LunaLib renderer contract with completion, validity, and cursor hooks. |
| `render/BaseReticuleRenderer.java` | Fleet ring and guide lines. |
| `render/AreaReticuleRenderer.java` | Effect-radius circle at the cursor. |
| `render/DirectionReticuleRenderer.java` | Direction arrow at the cursor. |
| `render/ValidatedAreaReticuleRenderer.java` | Area reticule that turns red when its validator rejects the position. |
| `render/PositionValidator.java` | Position-validation interface. |
| `render/validators/PondProximityValidator.java` | Allows positions only inside a pond. |
| `render/validators/MarketProximityValidator.java` | Rejects positions near a market. |
| `util/SkillshotUtils.java` | Cursor-to-world conversion and geometry-built solid, dashed, or dotted lines. |
| `util/DelayedActionScriptRunWhilePaused.java` | Delayed action that advances during pause. |
| `example/ExampleSkillshotAbility.java` | Working integration example. |

### `ui`

The shared UI language is used by the fishing map, outfitter, survey counter, and aquarium. The minigame remains separate.

| File | Responsibility |
|---|---|
| `ShopUi.java` | Fonts, quads, gradients, clipping, pips, card placement, and the shared panel background. |
| `PaneWidgets.java` | Type chips, standard and muted secondary buttons, titles, list headers, help marks, empty states, and text-field placeholders. Each widget owns a fresh sprite instance. |
| `ListRow.java` | Clipped, scroll-aware row base used by fish and shop lists. |
| `FishIcons.java` | Full species art after a catch and live rimmed silhouettes after habitat-data unlock. Uses fresh sprites so colour state cannot leak between screens. |

### `rendering`

| File | Responsibility |
|---|---|
| `distortion/CampaignDistortionRenderer.java` | GraphicsLib distortion pass adapted to the campaign layer. |
| `plugins/MaskedWarpedSpriteRenderer.java` | Fill, alpha mask, swirl, and radial-warp renderer. |
| `plugins/CoherenceOverlayRenderer.java` | Full-screen warp and purple tint under a rectangular edge mask. |
| `plugins/ChromaticAberrationOverlay.java` | Fixed-function RGB separation above the UI. Registers only above zero intensity and pauses for dialogs and core UI. |
| `plugins/MaskGlowRenderer.java` | Additive glow shaped by sprite alpha. |
| `plugins/NoiseMappedCircularRingRenderer.java` | Animated noise-shaped ring. |
| `plugins/WarpGrid.java` | Shared animated vertex grid with pinned borders. |
| `plugins/WarpedRectRenderer.java` | Shader-free per-vertex sprite warp. |
| `spiral/CircularSpiralWarpRenderer.java` | Reusable circular campaign post-process with cached world sources and configurable shader inputs. |
| `spiral/BlackHoleSpiralWarp.java` | Finds all local black-hole stars, manages the spiral renderer, and reads `catchreleaseBlackHoleSpiralWarpRange`. |
| `renderers/FleetMarkerRenderer.java` | Vanilla-geometry corner icon used for cyan fleet-job offers. |
| `renderers/RippleRingRenderer.java` | One location-bound expanding ring with synchronous retirement. |
| `renderers/SimpleRippleDataRunner.java` | Advances and expires `RippleData`. |
| `helper/Stencil.java` | Depth-mask sprite masking. Stencil-buffer methods are deprecated. |
| `helper/ParallaxUtil.java` | Camera-relative parallax offsets. |
| `helper/Disc.java` | Filled and outlined circles. |
| `helper/RoundedBorder.java` | Rounded rectangle border. |

### `memory`, `helper`, `reflection`, and `testing`

| File | Responsibility |
|---|---|
| `memory/upgrades/UpgradeManager.java` | Saves purchased levels. `getValue` is the single read path; `updateBaseValues` refreshes sheet-owned values and seeds missing stats on load. |
| `memory/upgrades/StatIds.java` | Shared upgrade IDs and explicit stat-to-ability mapping. |
| `memory/upgrades/UpgradeStat.java` | One upgrade row: base, flat/multiplier progression, category, mechanical description, optional icon, and current value. |
| `memory/charges/ChargeManager.java` | Persistent fractional charge pools. Regenerates only while unpaused, preserves partial progress, caps explicit gains, and fires one callback when a whole-charge boundary is crossed. |
| `memory/TransientMemory.java` | Session-only key/value store. Keys still begin with `$`. |
| `memory/RandomMemoryHelper.java` | Persistent per-system random source. |
| `helper/loading/FishSpecLoader.java` | Cached `fish.csv` loader. |
| `helper/loading/UpgradeStatLoader.java` | Cached `UpgradeData.csv` loader with old and new column-name aliases plus optional icons. |
| `helper/loading/BackdropLoader.java` | Cached `backdrops.csv` loader. |
| `helper/loading/SpriteLoader.java` | Returns a fresh sprite wrapper for an ID or path and caches only success/failure, preventing cross-screen render-state leaks. |
| `helper/CampaignHelper.java` | Shared “is the player in this location?” check used by Fishermen, tutorial postings, and camps. |
| `helper/cache/TimedValue.java` | Caller-clock TTL cache with an optional invalidation key. |
| `helper/math/TrigHelper.java` | Circle intersection, fitting, smoothing, and normal distributions. |
| `helper/math/Circle.java` | Circle geometry helpers. |
| `helper/math/CircularArc.java` | Arc traversal helpers. |
| `helper/animation/BaseCircleTrajectoryFollowingParticle.java` | Position and facing along a circular arc. |
| `helper/animation/ArchedTrajectoryFollowingMote.java` | Glowing mote animated along an arc. |
| `reflection/ReflectionUtils.java` | `MethodHandle`-based reflection that avoids the script classloader's direct reflection ban. |
| `testing/DevShortcut.java` | Three-step `J` shortcut: tutorial and gear, all backdrops, then all schematics. Dev mode only. |
| `testing/TestStencilRenderer.java` | Unregistered development renderer. |

## Engineering constraints

### Rules and mission engine

- Terrain uses `getPlugin()`, not `getCustomPlugin()`. Read its radius through `CampaignTerrainAPI`. Override `getActiveLayers()` and `getRenderRange()`. `BaseTerrain.advance()` affects local fleets unless the terrain opts out.
- Terrain and entity scripts advance outside the player's current location. Gate rendering and sound on `isInCurrentLocation()` because LunaLib keeps one sector-wide renderer list.
- A handled `callAction()` must return true. Vanilla treats false as an unhandled action and throws.
- `BaseHubMission` assumes `getPerson()` is non-null in many intel, reward, reputation, and distance paths. Fleet and entity missions must set a person override, usually the fleet commander.
- `setTimeLimit()` is compared with total mission elapsed time, not time in the current stage. Multi-round jobs that remain in `WANTED` must call `setClock()` for each round. Intel must use `getDaysLeft()`.
- A bar option ID must begin with its mission ID. `BarCMD` aborts wrappers whose prefix does not match. Mission IDs must not prefix one another.
- `BarCMD` closes with `returnFromEvent`, not `close`.
- A bare `score:` condition crashes while loading because it leaves an empty condition. Add scores to a real condition.
- Matching rule scores are summed. Specific conditions receive no implicit priority; distinguish a special case with an explicit score.
- A condition containing only a memory variable expects a Boolean. Do not test a stored name or number as truthy; maintain a separate Boolean flag.
- Token replacement uses longest keys first.
- Every memory key begins with `$`. `Memory.set()` throws when the write occurs if it does not.
- `PopulateOptions` does not fire automatically after `OpenCommLink` or `DialogOptionSelected`. A menu switch must set `$menuState` and explicitly fire `PopulateOptions` or `JobSpecificOptions`. Rows with options rebuild the panel; rows without options leave the old panel in place.
- `EndConversation` returns to the fleet interaction screen. Use `DismissDialog` or the shared `leaveEncounter` path when the encounter itself should close.
- `$hailing` and `$highlightComms` are one-shot flags. Vanilla clears both while building the fleet interaction.
- The rules engine formats a row before its script runs. Prepare generated tokens on an earlier row.

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
- A local fleet-job offer adds state and a cyan drawn marker to an existing scavenger; it does not create or rename a fleet. Acceptance creates fresh members in a mission-owned replacement and reports the original despawn.
- `$missionImportant` is not used for fleet-job offer markers because it changes both colour and story behavior. `FleetQuestMarker` copies vanilla placement and changes only tint.

### Save data, cargo, and shop state

- Fish encoding is save-critical. The first four fields are always present; origin, method, implement, chart target ID, and target system are positional optional tail fields. Preserve empty placeholders and continue accepting older four- and five-field records.
- Containers are identified by contents, not stack identity. Spending part of a crate or pile removes it and creates a replacement. Always repack with the original container ID.
- `FishItems.stow()` is the only landing path and normally creates a crate. Loose fish remain valid for all counting and spending.
- `FishItems.isContainer()` is the shared crate/pile test. Do not add direct bundle-ID checks.
- Unpacking a pile restores any singleton species as a loose fish, not a one-fish crate.
- `Tackle.Fit.BOTH` describes compatibility; it is not a rig. Rig loops use `Fit.isRig()`.
- Module ownership and module fitting are separate. Charge only when `isOwned()` is false; grants must both own and fit the module. Older saves seed ownership from fitted slots.
- Stock is a third state. `TackleManager.getOptions()` contains stocked modules plus owned unstocked modules so purchased gear can be removed and refitted.
- Explosive Head is unstocked and consumable. A miss keeps it; detonation removes ownership and the fitted slot, which makes Crablobab sell it again.
- Upgrade tiers and modules granted outside the shop still go through `ShopEntry.grant()` so a running ability is stopped and restarted with its new values.
- A curio is a switch, not a purchase. Its shop price is null, it never becomes “done,” and the button toggles it.
- Celebration Charges are purchased from Crablobab and switched in the outfitter. They are not a LunaLib setting.
- A null module price can mean an empty slot or an already-owned module. UI text must distinguish them.
- Abilities read tuning values when activated. Any code that changes their upgrade or module inputs must restart the affected running ability.
- `StatIds.getAbilityId()` uses an explicit map. Do not infer the ability from a stat-name prefix.
- ROD chase duration and rarity priority are progressive stats: every purchased tier must affect runtime behavior.
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
- When changing a rarity band, tune `difficulty`, `restlessness`, `motionSpeed`, `progressRateMult`, and `escapeRateMult` together and simulate the result. Difficulty alone stops carrying the late game once bar-size upgrades are large.
- Weaver is not assigned above Uncommon, and Lunger is not assigned to Common. `MIXED` may still roll either for one behavior interval.
- `reachedBy` uses `POND`, `BREACH_LAMP`, or blank for either. Requirement rolling must combine that with method: drone catches are always pond catches, while Harpoon can use either implement.
- A legendary has one host, one permanent catch, and no range data or job asks. All six are lamp-only. The five non-Abyssal legendaries are Lantern Jack, Slipstream Moray, Quorum, False Dawn, and Longliner; the manta is Abyssal.
- Legendary hosts and motes remain disabled until tutorial graduation. A sighting starts the 90-day relocation timer; the fish never relocates while the player is in-system and never returns after landing.

### Coherence model

- `Aberration` indexes static sources and colony fields instead of scanning the sector on every read. Rebuild on day change, gate activation change, or economy market-count change.
- System readings fill on arrival, map opening, or demand. `localPull` is a separate in-system distance calculation over local tagged entities and only lifts the system reading; it does not scale it down.
- The Abyss uses uncapped `Misc.getAbyssalDepth()` divided by `ABERRATION_ABYSS_SPAN`. A span of one restores the old hard cliff.
- `openSpaceReading` must include all indexed sources, not only Abyss and slipstreams, because the heat map samples bare hyperspace points.
- Each inhabited market creates a five-light-year quadratic stabilizing field. Overlapping fields do not stack; the strongest stabilizer is subtracted from the strongest destabilizer. The colony's own system is exactly zero aberration.
- Slipstreams are indexed as sampled ribbons through `SlipstreamTerrainPlugin2.getSegments()`. The old `SlipstreamTerrainPlugin` is inert in 0.98a. Foreign implementations fall back to their anchor.
- Marks verify that their source still exists so short-lived sources do not remain active until the next daily rebuild.
- In-system reach is `ABERRATION_LOCAL_BASE + ABERRATION_LOCAL_PER_LY × reachLY`. Do not add a second hand-maintained reach table.
- Hyperspace has no entity-local reading; its relevant sources are the Abyss depth field and slipstream terrain.
- Gates are individual marks because active and dormant gates have different reach and strength. Use `GateEntityPlugin.isActive()` only for vanilla gates; foreign tags fall back to sector-wide gate state.
- Foreign equivalents are identified by optional tags. Missing tags return empty results and create no dependency.
- Hidden sources use one survey test in `Mark.isFound`. Stars and slipstreams are the only always-visible exceptions. Fisherman icons follow fleet visibility; motes use Breach Lights instead.

### Fish entities and catch provenance

- A mote spawned at its destination expires immediately. Spawn and target positions must differ.
- `FishEntityPlugin.HOLDS_KEY` makes a tutorial mote choose another point in its pond instead of expiring. Chart and camp fish intentionally do not hold.
- The method says which rig caught a fish; the implement says what exposed it. Both values must follow the mote's actual provenance.
- Pond and harpoon catches must carry the exact source rupture where applicable. Chart requests also carry target ID, target system, and earliest valid timestamp through loose fish and containers.
- Chart and tutorial completion use the same `FishRequirement`/`FishCurrency` read and spend path as other quests. Do not add a separate cargo-completion check.
- A chart request plants or replants its identified target only while the player is in the target system. Open-water targets spawn away from their destinations and always use the required low-coherence roll.
- Chart offers select a valid fish-and-system pair, not a species pasted onto a destination. Occupied camp ruptures may inform the species choice but are never selected as the quest destination.
- Harpoon aim assist and collision both call `HarpoonEntityPlugin.canTake()`. Buried motes use `catchrelease_buried_mote` and require full light, or mere detection with Fathom Head.

### Rendering, UI, reflection, and audio

- `GL_LINE_STIPPLE` restarts on each `GL_LINES` segment and is unusable for short campaign lines. `SkillshotUtils` builds dash geometry explicitly.
- Fan light and fan breach window share `STEPS_ACROSS`, `STEPS_ALONG`, and both alpha curves. Change their geometry together.
- Glow, fan, and impression renderers share the same resting alpha formula. Module changes should affect light shape, not total intensity.
- Use transparent custom-panel hotspots to attach stock tooltips to hand-drawn controls. Vanilla then owns tooltip timing, placement, and clipping.
- `showCustomDialog()` always includes a confirm button. Use `showCustomVisualDialog()` when the panel must have none.
- `Stencil.startStencil()` is deprecated because it breaks campaign radar. Use the depth-mask pair.
- Camera-centered objects have no camera parallax term. Account for this in effects such as `PondDepthField`.
- `ReflectionUtils` uses `MethodHandle` because the Starsector script classloader rejects direct references to `java.lang.reflect.Field` and `Method`.
- Sound IDs are unchecked strings until playback. Validate them against merged sound data. Starsector JSON supports `#` comments and trailing commas, and sound entries may be arrays or objects.
- `playUISound` expects stereo; positional `playSound` requires mono; loops should be mono. The minigame line sound uses one continuously refreshed UI loop with changing volume.
- The loot result has a backdrop clock that starts when the panel is created and a list clock that starts after the catch tally. Coin rain uses the backdrop clock.
- `SpriteLoader` and `FishIcons` use fresh sprite wrappers. Never retain mutable sprite state across screens.

### Fisherman and tutorial lifecycle

- The Fisherman is one saved `PersonAPI` shared by every boat. Apply the hailed boat's portrait immediately before vanilla builds the person panel; background boats must not mutate it.
- Fisherman portraits are registered `graphics.characters` sprite IDs. Rank and post remain blank so vanilla shows the rankless person card once.
- Standing boats plan one outer-reaches leg at a time and validate both the destination and the straight path. `PATROL_SYSTEM` is not suitable because it crosses inhabited inner orbits.
- Fisherman visibility requires both a flat detected-range bonus and a per-frame sensor-fader override.
- Visiting Fisherman time advances only while the player is elsewhere. Rendering and sound also stop when the player is outside the location.
- The Fisherman map marker exists only in the player's current location, has no sensor profile, and is map-only. Reconciliation removes old duplicates and marks from departed systems.
- The visitor shelf restocks from each sale date, not a global monthly tick. Chart-request completion is the only way to increase shelf width.
- `FishShopDialog` takes an optional close callback. Colony use may dismiss the interaction; Fisherman use must restore the conversation.
- `FishingIntro.point()` is idempotent and can be reached from the wreck, castaway/rating, Fisherman interception, or a direct hail. Recovered property takes origin precedence, then rescued crew, then recorded market.
- The returning-player skip is available only before the R.O.D. lesson begins. Manually disabling the new Luna setting stays disabled after the one-time legacy-file migration.
- Tutorial single-target protection advances only when the requested species could naturally spawn at the current location with the required implement. The count carries between valid locations and pauses elsewhere.
- No bar, local scavenger, or distress fleet job may appear before `FishingIntro.isOpenForWork()` or tutorial completion as appropriate. Equipment requirements are limited to gear the player owns.

### Distress and reusable framework boundaries

- The distress framework follows the live vanilla `NearbyEventsEvent` timer. If the bridge cannot read that state, it fails closed instead of starting an independent scheduler.
- The framework owns entity, route, breadcrumb intel, reservations, and dialogue trigger only. Providers own eligibility, content, quest creation, expiry, and resolution.
- Distress CSV IDs must be namespaced so several mods can merge rows and providers without collisions.
- The framework yields whenever vanilla creates a distress call and shares system reservations to avoid concurrent duplicate events.
- Skillshot and distress registration must remain idempotent and save-safe. Transient listeners are rebuilt on load and skillshot targeting resets before save.

## Dead or dormant

| Component | State |
|---|---|
| `campaign/ponds/entities/StenciledFishingPondEntityPlugin` | Dead. Ponds are terrain now. |
| `campaign/fish/intel/FishMapIntel` | Save-compatibility shell. Old saves remove it after loading. |
| `campaign/fish/shop/ShopStorage` | Migration only. Returns fish left in the removed storage UI. |
| `testing/DevShortcut` | Registered, but active only in dev mode. |
| `testing/TestStencilRenderer` | Not registered. |
| `campaign/ponds/renderer/PondHoleRenderer` | Dormant while `PondConstants.POND_HOLE_LOOK` selects the shader version. |
