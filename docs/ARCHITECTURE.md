# Catch.Release — file and feature map

What is where, and which file to open first. 179 Java files across eight top-level packages, plus
the data tables that register them.

Kept by hand. When a package gains or loses a file, the table below is the thing to update — a map
that is wrong is worse than no map, because it is believed.

---

## Where a feature lives

| If you are changing… | Start in |
|---|---|
| The pond itself — look, open/close, mote spawning | `campaign/ponds/terrain/MaskedFishingPondTerrainPlugin.java` |
| Where ponds appear in a system | `campaign/ponds/listener/PondCreator.java` |
| The catch minigame's rules | `campaign/fish/minigame/FishingMinigame.java` (no rendering in it) |
| The catch minigame's look or input | `campaign/fish/minigame/FishingMinigamePanel.java` |
| What a fish *is* | `campaign/fish/data/FishSpec.java` + `data/campaign/fish.csv` |
| A caught specimen's stats and grading | `campaign/fish/data/FishCatch.java` |
| Bar jobs | `campaign/fish/jobs/` — `FishJob.java` is the spine |
| Fleet-given jobs | `campaign/fish/jobs/fleet/` |
| The shop | `campaign/fish/shop/FishShopDialog.java` |
| Upgrades | `memory/upgrades/` + `data/config/UpgradeData.csv` |
| Tackle modules | `campaign/fish/tackle/Tackle.java` |
| An ability's behaviour | `abilities/<name>/ability/` |
| An ability's tuning | `abilities/<name>/constants/` |
| Aiming and reticules | `skillshot/` (has its own README) |
| Shaders and GL helpers | `rendering/` + `data/catchrelease/shaders/` |
| The sector-map fish filter | `campaign/fish/map/` |
| The wandering Fisherman fleet | `campaign/fish/fisherman/` |
| The colony structure and its aquarium | `campaign/fish/colony/` |
| Consequences of harpooning a fleet | `campaign/crime/` |
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
4. `CatchReleaseCampaignPlugin.register()` — hands harpooned fleets and the Fisherman their
   custom encounter dialogs
5. `HarpoonPatrolResponse.register()` — sends a patrol after an outstanding harpooning
6. `FleetQuestSpawner.register()` — fleets out in the world that want fish
7. `FishermanSpawner.register()` — the daily roll for the wandering Fisherman
8. `ConservatoryOptionProvider.register()` — the conservatory's options on the colony screen
9. `AquariumTankScript.register()` — the aquarium on the colony's main menu
10. `UpgradeManager.getInstance().updateBaseValues()` — re-reads the upgrade sheet into the save
11. `SkillshotFramework.register()` — the aiming framework
12. `FishMapFilterScript` as a transient script — the sector-map filter
13. `FishIntelPlanetPanel` as a transient script — the intel Planets view's fish panel

`beforeGameSave()` — `SkillshotFramework.reset()`.

Registration is idempotent: the `register()` methods unregister by id first, and transient scripts
are rebuilt every load because their state lives in sector memory rather than in fields.

---

## Registered by data, not by code

Classes the game instantiates by name. Grep the data file, not the call sites — there aren't any.

**`data/campaign/abilities.csv`** — 5 abilities

| Id | Class |
|---|---|
| `catchrelease_searchlights` | `abilities/searchlight/ability/SearchlightAbilityPlugin` |
| `catchrelease_rod` | `abilities/rod/ability/PondInteractionAbilityPlugin` |
| `catchrelease_harpoon` | `abilities/harpoon/ability/HarpoonAbilityPlugin` |
| `catchrelease_shop` | `campaign/fish/shop/FishShopAbilityPlugin` |
| `skillshot_example` | `skillshot/example/ExampleSkillshotAbility` |

**`data/campaign/bar_events.csv`** — 11 jobs, plus `catchrelease_crablobab` →
`campaign/fish/crab/CrabSalesman`, which is a vendor rather than a job and the one row here that is
not a `FishJob`. **Three of the job ids do not match their class name:**

| Id | Class | | Id | Class |
|---|---|---|---|---|
| `catchrelease_standingOrder` | `StandingOrderJob` | | `catchrelease_duel` | **`KidsJob`** |
| `catchrelease_chef` | `ChefJob` | | `catchrelease_ring` | **`MafiaJob`** |
| `catchrelease_startup` | `StartupJob` | | `catchrelease_client` | **`CompanionJob`** |
| `catchrelease_butler` | `ButlerJob` | | `catchrelease_academy` | `AcademyJob` |
| `catchrelease_curator` | `CuratorJob` | | `catchrelease_tuber` | `TuberJob` |
| `catchrelease_cult` | `CultJob` | | | |

**`data/campaign/terrain.json`** — `catchrelease_StaticPond` → `MaskedFishingPondTerrainPlugin`.
Carries the plugin class only; name, radius, layers and tags all come from the plugin.

**`data/campaign/special_items.csv`** — `catchrelease_fish` → `FishItemPlugin`,
`catchrelease_fish_bundle` → `FishBundleItemPlugin`.

**`data/campaign/industries.csv`** — `catchrelease_conservatory` → `BreachConservatory`,
the colony structure that opens the fishing trade and keeps the aquarium.

**`data/config/custom_entities.json`** — the motes, harpoon and drone. The pond is
**not** here any more.

**`data/campaign/rules.csv`** — all dialogue. See the contract below.

**`data/config/sounds.json`** — 5 ids of our own, merged into vanilla's ~600. Ability sounds are
named in `abilities.csv` (`uiOn`/`uiOff`/`uiLoop`/`world*`), not in code.

---

## The rules.csv contract

Every word a job speaks is in the sheet. Java owns only what a sheet cannot do — counting the hold,
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

A job with its own hand-over decision overrides `getDeliverFlag()` so the shared "hand over the
catch" row does not appear beside its own — `KidsJob` and `MafiaJob` both do this.

---

## The tree

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
| `Aberration.java` | 0–1 "reality coherence" for a location, from abyss depth, hypershunt and slipstream |
| `SectorRegion.java` | Nine-way sector location enum (8 quadrant bands + ABYSSAL) |
| `CatchImplement.java` | What made a fish reachable — a pond or a breach lamp — read off the mote's own provenance |
| `FishLocationSummary.java` | Builds the "where this swims" sentence from a spec's regions and tags |

### `campaign/fish/jobs`
Bar-given jobs on a shared spine, plus the ask/reward rollers they share.

| File | What it does |
|---|---|
| `FishJob.java` | The spine: asks, rewards, hand-over, intel, and the `rules.csv` token contract |
| `FishJobAsks.java` | Rolls ask parameters — weight floors, species, type variety — off the fish table |
| `FishReward.java` | Reward base plus Credits, Upgrade, Tackle, LocationData, Blueprint, Commodity |
| `FishRewardRoller.java` | Rolls a payment scaled to a job's worth |
| `QuestPond.java` | Claims and releases a pond for a job, and seeds a flagged quest mote into it |
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

### `campaign/fish/jobs/fleet`
Jobs given by a hull in space, which then has to still be there when you return.

| File | What it does |
|---|---|
| `FleetQuest.java` | A `FishJob` whose giver is a fleet. `mark()` puts the offer on the hull, `hold()` sits it down; accepted on `take()`, not at spawn, so an offer nobody agreed to raises no intel |
| `FleetQuestSpawner.java` | Rolls the offer and routes it: able to fly → arrives from beyond sensor range, cannot → distress call |
| `FleetDistressCall.java` | The immobile half: picks a nearby empty system vanilla's own way, spawns at its distress jump point, raises vanilla `DistressCallIntel` |
| `FleetQuestEncounter.java` | Runs one offer — intercepts the player, reads the answer once the dialogue closes, sends a refused fleet home, times the offer out |
| `FleetQuestType.java` | Seven flavours of trouble, with pitch text, ask rolling and base worth. `wandering` is read off the complaint: a seized drive cannot come looking for you |

### `campaign/fish/colony`
The Breach Conservatory: the structure that brings the fishing trade to the player's own colony.

| File | What it does |
|---|---|
| `BreachConservatory.java` | The structure itself; also holds the aquarium's stock and its on/off switch |
| `ConservatoryOptionProvider.java` | The two colony-screen options: the fish outfitter and the aquarium office |
| `AquariumManageDialog.java` | The office: stock the tank, empty it, or shut the display off |
| `AquariumTransfers.java` | Hold-to-tank and back, both through the vanilla cargo picker |
| `AquariumTankScript.java` | Hangs the tank on the colony main menu, below the planet's image |
| `AquariumTankPanel.java` | The tank: GL water, bubbles, and every specimen swimming its own way |

### `campaign/fish/fisherman`
The wandering Fisherman: an independent fleet that fishes the player's system for two weeks and
trades while it does.

| File | What it does |
|---|---|
| `FishermanSpawner.java` | The daily roll: where the boat may spawn, and what leans the odds |
| `FishermanBehavior.java` | The stay: yellow fan lamps, staged motes, NPC harpoon throws, the leaving |
| `FishermanDialog.java` | Talking to it: survey ladder, outfitter hand-off, fish buyer, rumors |
| `FishRumors.java` | One rumor a month — rarer rolls, richer treasure, or a stranger species |
| `FishermanConstants.java` | Every number the above read |

### `campaign/fish/minigame`
The catch itself. Rules are separated from rendering on purpose.

| File | What it does |
|---|---|
| `FishingMinigame.java` | Rules only: bar/fish physics, progress meter, treasure rolls. No GL, no input |
| `FishingMinigamePanel.java` | Draws the track, bar, fish and meter; handles mouse and keyboard |
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
| `FishEntityPlugin.java` | The swimming mote: motion archetypes, diving, held/stunned states, glow |
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
| `FishShopDialog.java` | The dialog: tabs, list, detail pane, buy/store/retrieve/sell |
| `ShopEntry.java` | Wraps one purchasable — upgrade or tackle — behind uniform price/state/buy |
| `ShopGroup.java` | The shelves, and which stat ids and rigs belong to which |
| `ShopPricing.java` | Per-campaign seeded prices in credits and fish |
| `FishCurrency.java` | Counts and spends fish as payment, worst specimens first |
| `FishRequirement.java` | An ask: count, rarity, grade, species, origin, coherence — and how to describe it |
| `ShopStorage.java` | Migration only — returns fish left in the removed store/retrieve counter. See Dead or dormant |
| `FishShopAbilityPlugin.java` | The ability-bar button that opens it. Temporary until it lives on a market |
| `ShopRowPlugin.java` | One clickable row |
| `ShopTabPlugin.java` | One tab button |
| `ShopHeaderPlugin.java` | Title, credits and the per-rarity fish purse |
| `ShopDetailHeaderPlugin.java` | The detail pane's portrait, name and ladder readout |
| `ShopUi.java` | Shared drawing helpers for the custom-drawn look |

### `campaign/fish/items`
Fish in cargo.

| File | What it does |
|---|---|
| `FishItems.java` | Ids and the encode/decode used by both item kinds |
| `FishItemPlugin.java` | One landed specimen; right-click stows it into a bundle |
| `FishBundleItemPlugin.java` | A crate of one species; right-click unpacks |
| `FishItemRenderer.java` | Icon plus rarity and grade pips over the cargo cell |

### `campaign/fish/crab`
Crablobab, and the two things he sells. Not shop stock — see the note below.

| File | What it does |
|---|---|
| `CrabSalesman.java` | The bar event: the stall, the prices, the exchange. Stops appearing once both are sold |
| `CrabWares.java` | The two wares, what each costs in credits and crabs, and where each one's ownership lives |

### `campaign/fish/tackle`
Modules bolted to a rig.

| File | What it does |
|---|---|
| `Tackle.java` | The modules, which rig each fits, and the multipliers each applies |
| `TackleManager.java` | Two facts: which modules are **owned**, and which is in each rig's slot. `get()` always returns non-null, possibly `NONE` |

### `campaign/fish/map`
The sector-map fish filter.

| File | What it does |
|---|---|
| `FishMapFilterScript.java` | Inserts the filter button, resizes the map, mounts pane, overlay, planner popup; feeds the route's arrows to the map's own arrow list |
| `FishMapPane.java` | The side panel: planner button, search, type chips, species list |
| `FishPresence.java` | What the player is allowed to see, and where |
| `FishPresenceField.java` | Builds merged organic blobs — metaball field, marching triangles, smoothing |
| `FishPresenceOverlay.java` | Draws the blobs through a stencil, striped where they overlap; route badges, the close-route label, the system view's fish strip (filter-gated, right edge) |
| `FishRoute.java` | The saved route: ordered stops in the save, until closed by hand |
| `FishRoutePlanner.java` | Suggestions from open asks; cover + exact ordering, stability- and slipstream-aware |
| `FishRoutePopup.java` | The planner in the sidebar's slot: pick up to five, plot |
| `FishIntelPlanetPanel.java` | The intel Planets view's fish panel, beside the planet card |
| `FishType.java` | Filter categories with colour and icon |
| `CoreUiCrawler.java` | Reflection into the obfuscated core UI to find the filter row |

### `campaign/fish/codex`
Codex pages for species.

| File | What it does |
|---|---|
| `FishCodex.java` | Installs the category and per-species entries; opens the codex on a species |
| `FishCodexEntry.java` | One page: description, catch data, record, location, art, and the jump to the sector map |

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
What harpooning a fleet costs.

| File | What it does |
|---|---|
| `HarpoonOffence.java` | Incident history, outstanding debts, evasions, hostility escalation, rep loss |
| `HarpoonPatrolResponse.java` | Sends one faction patrol at a time after the player |
| `HarpoonedFleetFID.java` | Vanilla's encounter dialog plus one line, and a highlighted comm link |
| `CatchReleaseCampaignPlugin.java` | Hands harpooned fleets that dialog - and the Fisherman its own - at the narrowest priority |

### `abilities`
Four rigs. Each is `ability/` (the plugin), `constants/` (tuning), and usually `entities/`.

| File | What it does |
|---|---|
| `charges/BaseChargedSkillshotAbility.java` | Shared charge-pool rearm for the charged abilities; bans them all from hyperspace |
| `rod/ability/PondInteractionAbilityPlugin.java` | Unlocks the nearest pond, then casts and recalls the swarm; away from any pond with the breach lamps lit, sends a roaming one instead |
| `rod/entities/RodMoteEntityPlugin.java` | The mote flown at a pond to open it |
| `rod/entities/FishingDroneEntityPlugin.java` | One drone: launch, orbit, chase, return — steering, not pathing. Its circle's centre is asked for per frame, so a roaming drone flies the same circle around the fleet |
| `rod/scripts/FishingDroneSwarmScript.java` | Owns one cast: spawns drones, assigns chasers, handles recall. Four hooks — search centre, search area, what counts as fish, when it is over — are what the roaming variant replaces |
| `rod/scripts/RoamingDroneSwarmScript.java` | The pondless swarm: a screen flying with the fleet, going after buried motes the breach lamps have lit and unearthing them on contact |
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
| `plugins/MaskGlowRenderer.java` | Additive glow shaped by a sprite's alpha |
| `plugins/NoiseMappedCircularRingRenderer.java` | Ring shaped and animated by scrolling noise |
| `plugins/WarpGrid.java` | The animated vertex grid the warp renderers share; borders pinned |
| `plugins/WarpedRectRenderer.java` | A sprite warped per-vertex by a grid, no shader |
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
| `helper/loading/SpriteLoader.java` | Sprites by id or path, cached, misses logged once |
| `helper/CatchReleaseSettings.java` | LunaLib menu toggles, with fallbacks |
| `helper/math/TrigHelper.java` | Circle intersection and fitting, smoothing, normal distribution |
| `helper/math/Circle.java` · `CircularArc.java` | Point/angle helpers and arc traversal |
| `helper/animation/BaseCircleTrajectoryFollowingParticle.java` | Position and facing along a circular arc between two points |
| `helper/animation/ArchedTrajectoryFollowingMote.java` | A glowing mote drawn along that arc |
| `reflection/ReflectionUtils.java` | Reflection via `MethodHandle`, to dodge the classloader ban |
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
first, `get(rig)` the second. A module is bought once and can be moved between slots for nothing
after that, so anything that charges for tackle must ask `isOwned()` first — `ShopEntry.getPrice()`
returns null for one already owned, which is what makes fitting it free. Anything that *grants* a
module must `own()` it as well as `fit()` it, or the player pays for their own gift the first time
they take it off. Saves predating ownership seed the owned set from whatever is in a slot.

**Stocking a module and owning one are a third question.** `Tackle.stocked` says whether the
outfitter carries it, and `TackleManager.getOptions()` lists what it stocks *plus anything already
owned* — without the second half a module bought anywhere else could never be taken off and put back
on. `Tackle.EXPLOSIVE_HEAD` is the only unstocked one; it comes out of Crablobab's coat.

**Anything granted from outside the shop still goes through `ShopEntry.grant()`.** It is the only
place that knows a running rig has to be stopped so it comes back up reading what it now has — see
the note on abilities reading their numbers once. `CrabWares.EXPLOSIVE_HEAD` grants through it for
exactly that reason, rather than calling `own()` and `fit()` itself.

**An explosive head is a different ability, not a better harpoon.** It cannot land anything: the
strike blows the mote up and throws the head off its own line, and `HarpoonEntityPlugin.BLASTED` is a
terminal state that is not an arrival — nothing is reeled in and `land()` never runs. Against a hull
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
| `testing/TestStencilRenderer` | Commented out of `ModPlugin` |
| The pond's shader swirl | Dormant behind `PondConstants.POND_HOLE_LOOK`, which currently selects the stencil hole renderer |
