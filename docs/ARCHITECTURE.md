# Architecture

Technical routing for the current implementation. Java paths below are relative to `jars/src/catchrelease/`; data paths are repository-relative. Search the named owner before adding another implementation.

| Reference | Scope |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | Workflow, build gate and document upkeep |
| [DIALOGUE.md](DIALOGUE.md) | All text work and presentation checks |
| [RULES.md](RULES.md) | Rules syntax, execution and project routing contracts |
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
| Rules menu, panel return, highlights | `rules.csv -> CatchReleaseCMD`; [project contracts](RULES.md#project-routing) |
| Harpoon, drones, Breach Lights | `abilities/*/ability -> entities/scripts -> renderers`; shared targeting in `skillshot/` |
| Camera, pond opening | `PondInteractionAbilityPlugin -> RodMoteEntityPlugin -> MaskedFishingPondTerrainPlugin -> PondCameraFocusScript` |
| Charge count / regeneration | `BaseChargedSkillshotAbility -> ChargeManager -> ability callback` |
| Fleet offence / pursuit | `LampOffence/HarpoonOffence -> patrol response -> CatchReleaseCampaignPlugin/HarpoonedFleetFID` |
| Legendary reveal / cleanup | `LegendaryChases -> LegendaryHaunt/LonglinerDecoy -> HauntModule/LegendaryShields` |
| Map/Codex/intel handoff | `FishIntelMapButton/FishCodex -> FishMapFilterScript -> FishMapPane/FishPresence` |
| Stale campaign effect | Owner location/ability validity -> cleanup; shared renderer registration in `rendering/` |
| Shared UI widgets / fish icons | `ui/PaneWidgets`, `ShopUi`, `ListRow`, `FishIcons`; minigame rendering is separate |
| Campaign distortion / masking | `rendering/distortion/CampaignDistortionRenderer`, `rendering/helper/Stencil`, `rendering/plugins/*`; black-hole pass in `rendering/spiral/` |
| Aquarium | `BreachConservatory -> AquariumTransfers/Backdrops -> AquariumTankScript/Panel` |

## Registration and lifecycle

| Registry / hook | Owners |
|---|---|
| `ModPlugin.onCodexDataGenerated()` | `FishCodex`, only while Codex data is generated |
| `ModPlugin.onGameLoad()` | Idempotent script/listener registration and save repair; order below |
| `ModPlugin.beforeGameSave()` | Reset transient skillshot targeting |
| `data/campaign/fish.csv` | Species; `FishSpecLoader` |
| `data/campaign/abilities.csv` | catchrelease_searchlights, catchrelease_rod, catchrelease_harpoon, skillshot_example |
| `data/config/settings.json` | `catchrelease.dialogue.rules` command package, sprites, black-hole warp range |
| `data/config/sounds.json` | Sound registry; callers in abilities and `FishConstants` |
| `data/config/LunaSettings.csv` | Charge-ready sound policy, camera snap, returning-player tutorial skip |
| `data/campaign/bar_events.csv` | 11 ordinary FishJob subclasses + 3 camp jobs; Crablobab/rating use AddBarEvents rules |
| `data/campaign/distress_calls.csv` | Merged, namespaced specs; # IDs disabled; `CatchReleaseDistressProvider` |
| `data/campaign/rules.csv` | Dialogue and type-selected fleet quest/intel text; Java supplies mechanics/state |
| `data/world/factions/default_ranks.json` | Contact roles |
| `data/campaign/terrain.json` | Pond/coherence terrain; ponds are not custom entities |
| `data/config/custom_entities.json` | Motes, drones, harpoons, legendary props and map proxies |
| `data/campaign/special_items.csv` | Fish cargo |
| `data/campaign/industries.csv` | Breach Conservatory |
| `data/campaign/backdrops.csv` | Aquarium scenes and ownership source |
| `data/config/UpgradeData.csv` -> `memory/upgrades/` | Stat IDs, loader aliases, saved levels and runtime values |

Load order in `ModPlugin`: pond-on-jump -> buried motes -> charges -> harpooned FID selector -> offence responses -> local fleet offers -> visiting Fishermen -> standing Fishermen -> chart upkeep -> tutorial/wreck/rating/interception -> colony options -> aquarium -> coherence cache -> monthly ranges (including initial assessment) -> legendary cleanup -> Imposter cleanup -> upgrade base refresh -> distress provider/framework -> skillshot -> map filter -> intel planet panel -> coherence overlay -> black-hole warp -> stale pond claims/range relock -> dev shortcut.

IntelliJ classes: `out/production/catchrelease`; artifact: `jars/catchrelease.jar`. Keep compiler output outside `jars/`. Build procedure: [CLAUDE.md](../CLAUDE.md#building).

Optional Console Commands entry points: `AllFish`, `AddFish`, `SpawnFish`, `HauntStatus`, `SpawnFisherman`, `SpawnFleetQuest`, `SpawnDistressCall`. `lw_Console.jar` is needed for compilation even when the runtime mod is absent.

## Save identity

| Stable ID | Display name |
|---|---|
| `mackerel` | Moiré Mackerel |
| `cutout` | Volley Dolphin |
| `hull_grazer` | Hull Grazer |
| `longliner` | The Imposter |
| `miscount` | Relic Crab |

Display renames do not migrate IDs. `LonglinerDecoy` and Longliner-named memory, sound and option keys remain compatible with older saves. Asset status is recorded beside each species row (`placeholder art`); descriptions do not imply new campaign mechanics.

## Source owners

Folders contain related renderers, constants, widgets and helpers; use `rg --files jars/src/catchrelease/<folder>` for their complete inventory. The entries below identify state and integration owners, not every class.

### `campaign/fish` and `campaign/fish/data`

| File | Owner / connection |
|---|---|
| `FishingTaboo.java` | Central list of factions that reject fishing: the Church and the Path. |
| `FishSpec.java` | Species row, stable save ID and display fields, minigame tuning, value/size, habitat and implements; display renames do not rename saved IDs. |
| `FishCatch.java` | One specimen: size, weight, aberration, region, source rupture, timestamp, method, and optional chart-request provenance. |
| `FishLog.java` | Persistent per-species discovery and record data. |
| `Aberration.java` | Computes and caches aberration from the strongest destabilizer minus the strongest colony field. |
| `FishRanges.java` | Authoritative current range test. |

### `campaign/fish/jobs`

| File | Owner / connection |
|---|---|
| `FishJob.java` | Mission base -> FishRequirement/FishCurrency -> picker -> QuestRewards -> intel. Shared acceptance-time restriction; progress compares capped displayed counts; selected specimens reach rewards before the next round rolls. |
| `DemandScore.java` | Scores actual requirements (unmodified Common = 10), including diminishing extra specimens and cheapest anyOf branch; supplies ambition/count helpers and EASY/MEDIUM/HARD/SEVERE reward tiers. |
| `QuestRewards.java` | Shared reward budget: score × 600 credits × 0.75–1.35; fixed rewards, tier-gated extras, remaining guaranteed credits, saved hand-in value multiplier. Later cash stages guarantee ≥20% total and ≥25% base-credit growth, each ≥2,000; multiplier cannot decrease. |
| `QuestDuration.java` | Satisfiability gate and deadline: nearest valid range + round-trip fleet travel + work, rounded to 30/60/90/120/180 days or unlimited; +30 for required Rare/Epic and +30 for post-acceptance catch. |
| `FishHandoffPicker.java` | Validates non-overlapping specimen assignments; autoSelect takes the minimum valid set, worst first. Invalid confirmation reopens next frame after modal release; preserves container ID when repacking. |
| `FishReward.java` | Reward values, grants and actual grant results; quest credits use the exact selected specimens. Known range grants convert to stored credit fallback; legacy commodity rewards convert to fixed credits. |
| `FishRewardRoller.java` | Tier/ownership/active-job exclusions; distinct chart reservations; 3–10× fish-value multiplier (10× = 0.5%). Backdrop rolls require conservatory plans; compatibility conversions retain fixed values. |
| `QuestPond.java` | Claims ponds by a set of job IDs, adds vanilla mission importance, plants identified quest motes, and releases claims and motes. |

### `campaign/fish/jobs/camp`

| File | Owner / connection |
|---|---|
| `CampedSpotJob.java` | Two independent completion conditions: camper gone + post-acceptance fish from exact rupture. Creates fleet/claim on acceptance; qualifying proof releases mark; loss restores it; repairs legacy named-species requests. |
| `CampedSpot.java` | Spawns and holds the camper, forces one warning hail, allows disengagement, removes cut-link without a Continue step, and locks the R.O.D. only while the camp remains. |

### `campaign/fish/jobs/fleet`

| File | Owner / connection |
|---|---|
| `FleetQuest.java` | Fleet-backed FishJob. Saves type/details/alternate client; replaces accepted source fleet with mission-owned members; owns active map proxy, exact provenance, stage handoff and final return/despawn. External construction requires tutorial completion. |
| `FleetQuestSpawner.java` | One eligible local offer/wanted quest; tutorial gate, 7% checks, 45-day cooldown (QUIET_SHIP: 120). Uses scavengers except INTERMENT/MUTINY_POT/EXHIBIT trade convoys; low-coherence premises also gate the test path. |
| `FleetQuestEncounter.java` | Runs one fleet offer, accepts or declines after dialogue closes, resolves distress entities, restores local offer marks after load, and expires old offers. |
| `FleetQuestType.java` | 22 saved mechanical case definitions: demand shapes, reachability backoff, fleet/contact roles, reward exclusions and multipliers, alternate outcomes. No dialogue strings; inspect the case enum when changing a quest. |
| `CatchReleaseDistressProvider.java` | Adapter between the generic distress framework and `FleetQuest`. |

### `campaign/fish/colony`

| File | Owner / connection |
|---|---|
| `BreachConservatory.java` | Industry definition and aquarium state: stock, enabled state, and selected backdrop. |
| `AquariumTransfers.java` | Vanilla cargo pickers for transfers. |
| `AquariumTankScript.java` | Mounts the tank below the colony image whenever no covering visual is open, and removes it when another visual takes over. |
| `Backdrops.java` | Separates campaign-wide backdrop ownership from the scene selected by each conservatory. |

### `campaign/fish/fisherman`

| File | Owner / connection |
|---|---|
| `FishermanSpawner.java` | One temporary visitor sector-wide, one Fisherman per system; repairs duplicate pointers, excludes decoy, yields to tutorial posting; test path bypasses only the natural roll. |
| `CoreFisherSpawner.java` | Maintains standing boats in eligible inhabited systems; reconciles weekly/on arrival and reuses the canonical local Fisherman. |
| `OuterReaches.java` | Chooses destinations and straight-line legs that avoid inhabited inner orbits. |
| `FishermanBehavior.java` | Controls lamps, staged motes, pacing, visibility, visit duration, and departure. |
| `FishermanShelf.java` | Stores each boat's two initial habitat-data slots, duplicate prevention, and sale-based 30-day restocking. |
| `FishermanQuest.java` | Saved chart offer and exact identified catch. FishRequirement/FishCurrency govern progress, picker and spending; completion widens the shelf and starts a 90-day cooldown. Decline/reopen does not reroll. |
| `FishermanIdentity.java` | Stores the shared `PersonAPI` and selects one of five coherence portraits immediately before a hail. |
| `FishRumors.java` | Monthly rarity/treasure/non-legendary-stranger rumor state, expiry and intel; graduation grants a separate immediate lead. |

### `dialogue/rules`

| File | Owner / connection |
|---|---|
| `CatchReleaseCMD.java` | Single rules bridge: temporary tokens, conditions, actions, custom panels, highlights, question paging and fleet teardown. Restores prior rules plugin and options once after panels. |
| `QuestDialogMap.java` | Shared temporary sidebar map for remote dialogue targets, matching vanilla mission icons, tags, and colours. |
| `FishBuyer.java` | Immutable bulk-sale preview, revalidated before sale; protects active FishAsker and marked-gear specimens, preserves cargo cells and unboxes temporary crates on exit. |

### `campaign/fish/tutorial`

| File | Owner / connection |
|---|---|
| `FishingIntro.java` | Six-stage tutorial, grants, target selection, save repair and IntroIntel. Shared requirement/currency path; fifth valid same-rarity miss substitutes the single lesson target; invalid locations pause the count. Final multi-species lesson is excluded. |
| `TutorialWreck.java` | Creates a vanilla derelict cruiser beside the first suitable rupture. |
| `Castaway.java` | Stores planet eligibility and rescue state for the rating encounter. |

### `campaign/fish/minigame`

| File | Owner / connection |
|---|---|
| `FishingMinigame.java` | Catch physics, progress, escape and treasure. Square-root difficulty and compressRate preserve the sheet ordering with flat player power; hooked legendaries receive at least three Epic rewards. |
| `FishingMinigamePanel.java` | Draws the track, target, progress, and treasure; handles input; records bycatch, catch intel, route progress, and legendary completion. |
| `FishingMinigameDialogPlugin.java` | Hosts the custom visual, preserves source rupture and quest identity for drone and harpoon catches, applies tutorial catch protection, preserves campaign music, and exposes dev reopens that bypass substitution. |

### `campaign/fish/entities` and `campaign/fish/spawner`

| File | Owner / connection |
|---|---|
| `FishEntityPlugin.java` | World fish mote: movement, depth, held and stunned states, glow, source rupture, and legendary behavior. |
| `BuriedMoteEntityPlugin.java` | Invisible open-water fish. |
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
| `FishPresence.java` | Species/system visibility: caught or learned data in normal play; computed, non-persistent full chart in dev mode; optional request allowlists. |
| `FishRoutePlanner.java` | Builds route suggestions from every `FishAsker` and shop mark, expands broad requirements, and orders stops using stability and slipstreams. |

### `campaign/fish/codex`

| File | Owner / connection |
|---|---|
| `FishCodexEntryState.java` | Central `UNKNOWN`, `RANGE_DATA`, and `CAUGHT` policy for visibility, links, art, description, records, and map access. |

### `campaign/fish/legendary`

| File | Owner / connection |
|---|---|
| `LegendaryChases.java` | Persistent host, sighting, provocation, Imposter reveal, completion, and defense state for each legendary. |
| `LegendaryHaunt.java` | Transient coordinator. |
| `LonglinerDecoy.java` | Imposter disguise. Player lamps remove fleet and spawn mote at the same location -> 1s drift along last velocity -> alert + positional sound -> 0.3s delay -> flee. Excluded from Fisherman reconciliation. |
| `LegendaryShields.java` | Persistent defenses and render state: Imposter explosive-only shield, Quorum escort/regeneration, Lantern Jack stored shells/prey lure, regrowing shells and provocation. |

### `campaign/fish/constants` and `campaign/fish/intel`

| File | Owner / connection |
|---|---|
| `FishIntelMapButton.java` | Shared navigation contracts: open the fishing map for habitat targets, plot a route for known systems, or set autopilot for non-fish objectives. |
| `FishIntelNotifications.java` | Defers new intel to the first unpaused frame after dialogue; updates use a zero-day delayed script; inline cards do not consume the queue. |

### `campaign/ponds`

| File | Owner / connection |
|---|---|
| `listener/PondCreator.java` | Populates entered systems from planet count, capped at two ponds, and finds clear positions away from planets, ponds, nebulae, and rings. |
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
| `harpoon/entities/HarpoonEntityPlugin.java` | Flight, collision, shields, mines, hauling, fleet contact, rope, catch, and return. |
| `searchlight/ability/SearchlightAbilityPlugin.java` | Breach Lights activation, spool, slow, detection penalty, and all beam renderers. |
| `searchlight/scripts/Searchlight.java` | Beam sweep, lock-on, distortion, and ripples. |

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
- A legendary has one host, one permanent catch, and no range data or job asks. All six are lamp-only. The five non-Abyssal legendaries are Lantern Jack, Slipstream Moray, Quorum, False Dawn, and The Imposter; the manta is Abyssal.
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
- Backdrop source art: 388×170, visible 386×168; 2× visible assets: 772×336.

## Dead or dormant

| Component | State |
|---|---|
| `campaign/ponds/entities/StenciledFishingPondEntityPlugin` | Dead. Ponds are terrain now. |
| `campaign/fish/intel/FishMapIntel` | Save-compatibility shell. Old saves remove it after loading. |
| `campaign/fish/shop/ShopStorage` | Migration only. Returns fish left in the removed storage UI. |
| `testing/DevShortcut` | Registered, but active only in dev mode. |
| `testing/TestStencilRenderer` | Not registered. |
| `campaign/ponds/renderer/PondHoleRenderer` | Dormant while `PondConstants.POND_HOLE_LOOK` selects the shader version. |
