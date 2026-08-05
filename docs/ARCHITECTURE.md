# Catch.Release — file and feature map

What is where, and which file to open first. 163 Java files across eight top-level packages, plus
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
4. `CatchReleaseCampaignPlugin.register()` — hands harpooned fleets a custom encounter dialog
5. `HarpoonPatrolResponse.register()` — sends a patrol after an outstanding harpooning
6. `FleetQuestSpawner.register()` — fleets out in the world that want fish
7. `UpgradeManager.getInstance().updateBaseValues()` — re-reads the upgrade sheet into the save
8. `SkillshotFramework.register()` — the aiming framework
9. `FishMapFilterScript` as a transient script — the sector-map filter

`beforeGameSave()` — `SkillshotFramework.reset()`.

Registration is idempotent: the `register()` methods unregister by id first, and transient scripts
are rebuilt every load because their state lives in sector memory rather than in fields.

---

## Registered by data, not by code

Classes the game instantiates by name. Grep the data file, not the call sites — there aren't any.

**`data/campaign/abilities.csv`** — 6 abilities

| Id | Class |
|---|---|
| `catchrelease_searchlights` | `abilities/searchlight/ability/SearchlightAbilityPlugin` |
| `catchrelease_rod` | `abilities/rod/ability/PondInteractionAbilityPlugin` |
| `catchrelease_harpoon` | `abilities/harpoon/ability/HarpoonAbilityPlugin` |
| `catchrelease_depthbomb` | `abilities/depthbomb/ability/DepthBombAbilityPlugin` |
| `catchrelease_shop` | `campaign/fish/shop/FishShopAbilityPlugin` |
| `skillshot_example` | `skillshot/example/ExampleSkillshotAbility` |

**`data/campaign/bar_events.csv`** — 11 jobs. **Three ids do not match their class name:**

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

**`data/config/custom_entities.json`** — the motes, harpoon, drone and depth bomb. The pond is
**not** here any more.

**`data/campaign/rules.csv`** — all dialogue. Jobs reach their Java through
`Call $catchrelease_jobRef <action>`.

---

## The tree

### `campaign/fish/data`
The data model: species, individual catches, the player's log, and the enums everything reads off.

| File | What it does |
|---|---|
| `FishSpec.java` | One row of the fish table: identity, minigame stats, value/size range, where it lives |
| `FishCatch.java` | One rolled specimen — length, weight, aberration, origin; grades, values, encodes to a string |
| `FishGrade.java` | Five-step quality ladder, size fraction → value multiplier and colour |
| `FishRarity.java` | Rarity ladder with mote colour, speed and wander multipliers |
| `FishMotion.java` | Minigame movement archetypes (SMOOTH, DARTER, SINKER, FLOATER, MIXED) |
| `FishLog.java` | Sector-persistent per-species record; unlocks location data for codex and map |
| `FishLogEntry.java` | Per-species log data: counts, records, first/record location and time, capture method |
| `Aberration.java` | 0–1 "reality coherence" for a location, from abyss depth, hypershunt and slipstream |
| `SectorRegion.java` | Nine-way sector location enum (8 quadrant bands + ABYSSAL) |
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
| `FleetQuest.java` | A `FishJob` whose giver is a fleet; pins it in place, releases it when the job ends |
| `FleetQuestSpawner.java` | Rarely adopts a passing fleet or places a stranded one |
| `FleetQuestType.java` | Seven flavours of trouble, with pitch text, ask rolling and base worth |

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
| `CatchCelebration.java` | Flash, backlight, confetti and flourish on a landed fish |

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
| `PondFishSpawner.java` | Weighted selection filtered by star type, tags and region; biased by drone tackle |
| `BuriedMoteSpawner.java` | Keeps a target buried-mote population around the player |
| `StarSystemFishSpawner.java` | **Empty stub.** No fields, no methods |

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
| `ShopStorage.java` | A hold the shop keeps separate from the player's cargo |
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

### `campaign/fish/tackle`
Modules bolted to a rig.

| File | What it does |
|---|---|
| `Tackle.java` | The modules, which rig each fits, and the multipliers each applies |
| `TackleManager.java` | The fitted slot per rig; always returns non-null, possibly `NONE` |

### `campaign/fish/map`
The sector-map fish filter.

| File | What it does |
|---|---|
| `FishMapFilterScript.java` | Inserts the filter button, resizes the map, mounts pane and overlay each frame |
| `FishMapPane.java` | The side panel: search, type chips, species list |
| `FishPresence.java` | What the player is allowed to see, and where |
| `FishPresenceField.java` | Builds merged organic blobs — metaball field, marching triangles, smoothing |
| `FishPresenceOverlay.java` | Draws the blobs through a stencil, striped where they overlap |
| `FishType.java` | Filter categories with colour and icon |
| `CoreUiCrawler.java` | Reflection into the obfuscated core UI to find the filter row |

### `campaign/fish/codex`
Codex pages for species.

| File | What it does |
|---|---|
| `FishCodex.java` | Installs the category and per-species entries; opens the codex on a species |
| `FishCodexEntry.java` | One page: description, catch data, record, location, art |
| `FishLocationMap.java` | A small drawn sector map marking where a record was taken |

### `campaign/fish/constants` · `campaign/fish/intel`

| File | What it does |
|---|---|
| `FishConstants.java` | Every magic number for minigame, result cards, celebration, treasure, codex |
| `FishMapIntel.java` | **Dead.** A husk kept so old saves load and delete it themselves |

### `campaign/ponds`
The pond, as terrain.

| File | What it does |
|---|---|
| `terrain/MaskedFishingPondTerrainPlugin.java` | The live pond: activation, motes, depth field, hole rendering, temporary ponds |
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
| `CatchReleaseCampaignPlugin.java` | Hands harpooned fleets that dialog, at the narrowest priority |

### `abilities`
Four rigs. Each is `ability/` (the plugin), `constants/` (tuning), and usually `entities/`.

| File | What it does |
|---|---|
| `charges/BaseChargedSkillshotAbility.java` | Shared charge-pool rearm for the charged abilities |
| `rod/ability/PondInteractionAbilityPlugin.java` | Unlocks the nearest pond, then casts and recalls the swarm |
| `rod/entities/RodMoteEntityPlugin.java` | The mote flown at a pond to open it |
| `rod/entities/FishingDroneEntityPlugin.java` | One drone: launch, orbit, chase, return — steering, not pathing |
| `rod/scripts/FishingDroneSwarmScript.java` | Owns one cast: spawns drones, assigns chasers, handles recall |
| `rod/rendering/FishingRingRenderer.java` | The dashed ring showing the fishing radius |
| `rod/rendering/FishingDroneDebugRenderer.java` | Dev only: ring and per-drone spokes |
| `rod/animation/Flash.java` | Short additive glow burst |
| `rod/constants/RodConstants.java` | Drone speed, steering, orbit, return acceleration, ring look |
| `harpoon/ability/HarpoonAbilityPlugin.java` | Fires the line; aim assist; press again to cut while hauling |
| `harpoon/entities/HarpoonEntityPlugin.java` | The whole cast: flight, strike, hauling, catch, return, rope rendering |
| `harpoon/constants/HarpoonConstants.java` | Flight, catch radius, haul physics, rope spring and wave params |
| `depthbomb/ability/DepthBombAbilityPlugin.java` | Throws a bomb at a clamped-range point |
| `depthbomb/entities/DepthBombEntityPlugin.java` | Falls, arms, detonates; opens a pond, stuns motes, unearths buried ones |
| `depthbomb/constants/DepthBombConstants.java` | Range, blast, glass look, shockwave, and the `SPAWN_POND` toggle |
| `searchlight/ability/SearchlightAbilityPlugin.java` | Spools lights up; exposes `isLit(mote)`; detectability penalty |
| `searchlight/scripts/Searchlight.java` | One beam: sweep, lock-on, picks its face, drives distortion and ripples |
| `searchlight/rendering/SearchlightGlowRenderer.java` | The default circular beam |
| `searchlight/rendering/SearchlightFanRenderer.java` | The wedge beam, for the fan-beam tackle |
| `searchlight/rendering/SearchlightBurnRenderer.java` | The hyperspace burn-through look |
| `searchlight/rendering/SearchlightImpressionRenderer.java` | Every buried mote's dent, drawn once for all beams together |

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
| `plugins/FractureRenderer.java` | Procedural shattered-glass break |
| `plugins/GlassShardBurst.java` | Spinning triangle debris off a fracture |
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

**Every memory key starts with `$`.** `Memory.set` throws on one that does not, and it throws
whenever the write happens rather than where the key was written down - which can be a stage change
minutes later.

**The two `makeImportant` overloads do not take the same kind of string,** and the compiler cannot
tell them apart. `Misc.makeImportant(entity, reason)` takes a *reason*, held alongside the flag, and
must **not** start with `$`. `BaseHubMission.makeImportant(entity, flag, stages...)` takes a memory
*key* it writes on a stage change, and must. Handing a reason to the mission's version compiles
cleanly and throws in the campaign. Whichever is used, pair it with the matching `makeUnimportant`.

**`Tackle.Fit.BOTH` is not a rig.** It is a declaration of what a module fits. Code walking rigs must
use `Fit.isRig()` or it will offer a shelf for a slot nobody owns.

**Fish encode format is save-critical.** `FishCatch.encode()` appends origin as an optional fifth
field so four-field saves still parse. Changing the format breaks fish already in saves.

**Bundles are identity-by-contents.** Spending part of one removes it and adds a new one with the
remainder. Never mutate in place.

**`GL_LINE_STIPPLE` is useless here.** GL restarts the pattern at every segment of a `GL_LINES`
batch, so anything shorter than one dash draws solid. Dashes are cut as geometry in `SkillshotUtils`.

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
| `campaign/fish/spawner/StarSystemFishSpawner` | Empty stub |
| `testing/TestStencilRenderer` | Commented out of `ModPlugin` |
| Depth bomb's glass fracture and shards | Fully implemented but dormant — `DepthBombConstants.SPAWN_POND` routes detonation to a temporary pond instead |
| The pond's shader swirl | Dormant behind `PondConstants.POND_HOLE_LOOK`, which currently selects the stencil hole renderer |
