# Combat ships

Read this before implementing or revising the combat side of the three fishing ships: their hulls, hullmods, weapons, ship systems, wings, combat rendering and combat AI. It records the approved concept, the decisions taken, the engine facts the design depends on, open questions and the build plan. It does not govern the ships' campaign role or quest flow ([FISHERMAN_CHAIN.md](FISHERMAN_CHAIN.md)), player-facing text ([DIALOGUE.md](DIALOGUE.md), [LORE.md](LORE.md)) or Java UI contracts ([UI.md](UI.md)). Move implemented technical detail to [ARCHITECTURE.md](ARCHITECTURE.md) and tick the plan here.

Engine facts below are from Starsector 0.98a-RC8 (the `starsector-knowledge` skill: `api/`, decompiled `obf/`, `data/`) unless marked otherwise. Line references are to that build.

## Ships

| Ship | Hull id | Class | Designation | Art |
|---|---|---|---|---|
| Stigmata | `catchrelease_harpoon_ship` | Destroyer | Harpoon Tug | `graphics/catchrelease/ships/harpoon_boat.png`; harpoon weapon `graphics/catchrelease/weapons/harpoon_harpoon.png` |
| Reliquary | `catchrelease_rod_ship` | Cruiser | LYNE Tender | `graphics/catchrelease/ships/done_boat.png` |
| Monstrance | `catchrelease_lamp_ship` | Cruiser | Breach Trawler | none yet; the hull is still a Nebula copy |

All three are midline. Stats for the Stigmata and Reliquary are set in `ship_data.csv`. The Stigmata has its Haul Drive; the Reliquary's system is a vanilla placeholder (`targetingfeed`) until its launch system exists.

## Stigmata: harpoon tug

A fast destroyer with classic midline stats, one modular launch bay, three small ballistic turrets and a built-in medium mount for the harpoon (`WS0001`: angle −59.5, arc 190). The mount's default angle lets the rope run past the hull while the tug burns straight away from its target.

### Tug hullmod

Built in as the Tug Drive Field (`catchrelease_tug_field`), placeholder icon (vanilla Augmented Drive Field).

- +1 maximum burn for the fleet per tug; several tugs stack.
- Campaign acceleration +20% for the first tug, each further tug adding half the previous one's bonus (+20%, +10%, +5%, …).
- No drawback.
- Inactive while the fleet's Sustained Burn is on. The point is to make a fleet that does not sustain-burn better at catching things.

Vanilla reference: the Ox tug's burn comes from the hidden Drive Field Stabilizer (+1 fleet burn and +200 sensor profile, `api/impl/hullmods/DriveFieldStabilizer.java:10-16`). Augmented Drive Field is a different hullmod (+2 to the ship's own burn). No vanilla hullmod changes campaign acceleration.

Implementation route: `HullModFleetEffect.advanceInCampaign(fleet)` writes `getFleetwideMaxBurnMod().modifyFlat` and `getAccelerationMult().modifyMult` under one key, or removes both, each frame. Those fleet stats take effect in the same frame (`obf/campaign/fleet/FleetData.java:798-823`, `CampaignFleet.java:794-795`). A ship-stat bonus would only reach the fleet at the next fleet sync, so it could not follow Sustained Burn. The hook runs for every fleet every frame, so it must exit at once for fleets without a tug. Sustained Burn: `fleet.getAbility(Abilities.SUSTAINED_BURN)`, `isActive()`.

### Harpoon

A built-in weapon on `WS0001` with regenerating charges (`ammo`, `ammo/sec`, `reload size` in `weapon_data.csv`). Prefix its ids: vanilla already uses `harpoon` for a missile. The projectile sprite is still to come; until then it uses `graphics/catchrelease/weapons/harpoon_projectile.png`, a renamed copy of the vanilla Harpoon missile's sprite.

- **Damage:** it does real damage, as a harpoon should. Non-zero damage also matters technically: `OnHitEffectPlugin` reports `shieldHit` only when shield damage was dealt (`obf/combat/entities/BallisticProjectile.java:255-257`).
- **Hits:** a shield hit glances off; only a hull or armour hit attaches. A deflected harpoon is a new projectile spawned at the hit point (`engine.spawnProjectile`); vanilla has no ricochet.
- **Targets:** anything with a hull: enemies, friendly ships and wrecks.
- **While attached:** the rope keeps its length. When taut it pulls both ships along the rope, split by mass, so lighter hulls are dragged more easily. Mass is a plain field (`CombatEntityAPI.getMass/setMass`), not a stat. LazyLib `CombatUtils.applyForce` does not scale by frame time. A ship above its top speed brakes on its own unless it is accelerating, decelerating or strafing (`obf/combat/entities/Ship.java:2031-2039`), and those commands clamp to top speed too (`obf/combat/entities/ship/null.java:740-815`); `blockCommandForOneFrame` cannot stop the brake. So a dragged ship needs a temporary top-speed allowance. The rope can go slack; slack must never pull.
- **Release:** firing again releases the current rope. The rope also breaks when its duration runs out, when the target is destroyed or removed, and, probably, when the rope crosses the tug's own hull while it turns (LazyLib `CollisionUtils`; start the test outside the hull, since a line starting inside the bounds reports a hit at its start, whatever its javadoc says).
- **Turret return:** after firing or release the mount turns back fast but smoothly, never snapping.
- **Rope look:** about 2 px wide, a beige-white centre with brown edges, using half-pixel widths where that helps. Drawn as a feathered quad strip from a `CombatLayeredRenderingPlugin`, with its entity kept at the rope's midpoint and a render radius covering the rope (vanilla `NeuralTransferVisual` draws between two ships this way). The campaign harpoon's curve, spring slack and wobble maths in `HarpoonEntityPlugin` are portable; its classes are not.
- **Connection:** the line flies out at low alpha and "materialises" when the harpoon bites, possibly with EMP arcs racing along it.

### Drive system

Replaces the placeholder system. It uses the tug drive's field: while active it raises the tug's mass (restored when it ends), acceleration, turn acceleration and turn rate, so a hooked target can be hauled. Visuals: longer engine plumes (`ShipEngineControllerAPI.extendFlame`, `fadeToOtherColor`) spewing dirty black smoke (`engine.addNebulaSmokeParticle`). Charges and regeneration come from the system's `max uses` and `regen` (charges per second).

### AI

- **Harpoon targets:** prefer vulnerable targets (hull exposed, shields down or flanked) that are not already being hammered by friendly ships, or that are fleeing.
- **Towing:** no vanilla AI tows. Wrap the default ship AI (call it, then override or block its movement commands with `giveCommand` / `blockCommandForOneFrame`) and choose where to drag the target. A first version can pull toward friendly ships and away from the strongest enemy group. A later one makes a quick firepower estimate of the surroundings and drags the target into friendly firing arcs. This needs workshopping.
- **Drive system:** fire it when the rope is taut and the target resists, or to escape.
- References: Ed Shipyards (https://bitbucket.org/NickWWest/edshipyard/src/develop/) and Knights of Ludd (https://github.com/Dal-041/Knights_of_Ludd) have some of the best combat code to learn from.

## Reliquary: LYNE Tender

A classic midline carrier cruiser: slow and cumbersome, with high crew capacity. It is parasitic: two built-in wings of LYNE drones and no modular bays, so every other fighter it fields is one it salvaged. The hull has two fighter bays; `LB 1` is the landing bay for towed wrecks, and `LB 2`/`LB 3` stay as launch points. Until the LYNE wing exists, the two bays are empty modular bays.

### LYNE drones

- Five per wing, unarmed, nimble, low hull, very fast, with a very short replacement time. They need to be fast enough to reach wrecks before they fade.
- Sprite: vanilla `graphics/ships/munitions_drone.png` for now, as in the campaign.
- Low target priority. A drone counts for 10% of a normal threat (`obf/combat/ai/OO0O.java:2981-2983`); whether autofire also ignores drones is untested.
- Custom fighter AI, chosen by a hull tag in `ModPlugin.pickShipAI`, which also covers bay-launched fighters.

### Wreck salvage

1. A drone picks an unclaimed eligible fighter wreck, attaches to it (one wreck per drone) and tows it back to the area of bay 1.
2. At the bay, drone and wreck shrink to look as if they land (`ShipAPI.beginLandingAnimation` shrinks and fades a ship; on a wreck it is untested). The bay shows the vanilla hit-particle work effect.
3. The recovered fighter goes into the ship's store.

Engine limits:

- **Wreck chance.** A dying fighter leaves a wreck only 50% of the time (`Ship.java:4821-4828`). `setHulkChanceOverride` raises it; use an adjustable setting, default 75%, for enemy fighters while a Reliquary is deployed.
- **Wreck lifetime.** Every fighter wreck fades about 10.5 seconds after it appears (`Ship.java:215-216`, `1962-1972`), whatever `setAlphaMult` says. While a Reliquary is deployed, a combat script extends every eligible fighter wreck to 30 seconds with the same once-a-second top-up of `timeBeforeFade`, so the drones have time to reach them.
- **Damage overlay.** The towed wreck should keep its damage decals. Keeping the real wreck does that; a drawn copy would lose them. Keeping it means topping up its private `timeBeforeFade` by reflection once a second (the class and field names are stable; the countdown starts at 10 seconds, so a one-second interval never lets it run out) and setting its collision class to `NONE` while it is towed.
- **Blacklist.**
  - Skip anything that is not a fighter, station or armour modules, drones and shuttle pods.
  - Skip hulls tagged `threat`, `swarm_fighter`, `fragment`, `dweller` or `monster`. Threat swarms and Dweller ships never leave wrecks anyway.
  - Omega "Aspect" fighters have no hull tag. Their wings are tagged `restricted` and `independent_of_carrier`, so exclude by wing tags as well.

### Recovered fighters

- **Store size:** a Reliquary holds at most 10 recovered fighters, counting those still being restored. The limit is a setting (LunaLib, Ruptures & Exploration or a new combat section). While the store is full, the drones ignore wrecks.
- **Restoration:** a recovered fighter is restored like a normal replacement (its wing's refit time and the ship's refit modifiers, with the usual CR rules), then waits in the store.
- **Launch system:** launches every stored fighter at the target for an attack run. Fighters of one type form wings up to their wing size, and never more instances than were recovered: one recovered Broadsword launches as one, two as two.
- **After launch:** they behave like normal fighters, return and land. A destroyed one is gone and leaves no wreck (`setHulkChanceOverride(0)` on every fighter the Reliquary launches), so no fighter is salvaged twice.
- **Bays cannot rebuild them.** A launch bay only builds its own wing's type (`obf/combat/entities/ship/A/C.java:47, 63, 346-410`) and deletes a fighter that lands. So the system spawns them itself:
  - spawn with `CombatFleetManagerAPI.spawnShipOrWing`;
  - set the Reliquary as source ship;
  - remove surplus members;
  - set their CR;
  - count landed fighters back into the store.
  - Vanilla `SwarmLauncherEffect.java:148-180` does the same, including `removeDeployed` cleanup.
- **Status display:** the store shows on the combat screen where terrain effects live. `engine.maintainStatusForPlayerShip` gives one line per entry. A richer display can be drawn in `renderInUICoords`, or MagicLib's `MagicSubsystem` can host the launch ability with its own HUD bars and AI hook (`shouldActivateAI`).
- **Refit display:** while the refit screen is open, each Reliquary with stored fighters shows them as filled, locked built-in bays. The swap exists only while the refit is open, so saves, D-mod changes and carrier skill counts never see it. Engine facts below are from the 0.98a-RC8 refit, variant and hull-spec code (`obf/coreui/refit/`, `obf/loading/specs/`).
  - **Why a hull spec copy:** a fighter bay is locked only when the hull spec says it is built-in (`isBuiltInWing`). No variant flag is consulted. A copy of the spec with the stored wings appended to its built-in list shows them filled, locked and free of OP. Being built-in also keeps them out of the loot, scuttle and sell paths, which only take non-built-in wings.
  - **Making the copy:** the API has no clone, but the concrete spec class has a public `clone()` that copies the built-in wing list. Call it through `MethodHandles` (mods may not use `java.lang.reflect`) and set it with `ShipVariantAPI.setHullSpecAPI`. The variant must belong to this ship alone, since the refit reloads a stock variant from the registry. Clone it, set its source to `REFIT` and call `member.setVariant(v, false, true)`, as vanilla `PKDefenderPluginImpl` does.
  - **Open:** `CoreUITabListener.reportAboutToOpenCoreTab(REFIT, …)` builds the copy from the current store, with the LYNE wings first, then the stored wings. It calls `refreshBuiltInWings()`, and the built-in Reliquary hullmod adds one bay per stored wing while the swap is active.
  - **Close:** no callback reports the refit closing. A script added on open checks each frame whether the refit is still the current tab (`CampaignUIAPI.getCurrentCoreTab`) and removes itself on revert, so this polling exists only while the swap does. On close it reverts whatever variant the member holds at that moment: the refit's undo history clones variants that share the copy. It restores the normal spec and removes the stored wing ids from the variant's wing list.
  - **Restore guard:** restoring a hull (removing D-mods) happens inside the refit and calls `setHullSpecAPI` with the normal spec. The same script re-applies the copy whenever the variant's spec is no longer the copy. Otherwise the extra bays would become modular and a stored fighter could be stripped into a free LPC.
  - **Combat:** the hullmod adds the extra bays only outside combat. Bays are created from the variant's wings at ship creation, after `applyEffectsBeforeShipCreation` has run with `stats.getEntity()` set (`Ship.java:841-850`, `obf/loading/specs/_.java:107-133`). So the stored wings never get bays, and only the launch system spawns them.
  - **Safety net:** on game load and when the ship leaves the fleet, strip any stored wing ids left in the variant.
  - **Refitting is otherwise unaffected:** weapons, hullmods, S-mods and OP are identical on the copy. Autofit fills only modular bays. Saving a variant skips built-in wings. The stats panel shows the extra bays while the refit is open.
  - **Fallback:** the built-in hullmod's tooltip (`HullModEffect.addPostDescriptionSection`, with the refit `ShipAPI` and its `getFleetMemberId()`) also lists the store, with sprites, counts and restoration progress. It is the fallback whenever the swap is not in place.
  - **Rejected:** drawing on the refit screen has no API hook. A PatchLib redirect of `isBuiltInWing` would need many fragile call-site patches.
- **Discarding stored fighters:** the player removes fighters they do not want through a LunaLib refit button on each Reliquary. LunaLib 2.0.1 (`lib/Lazylib_lunalib.zip`) has `lunalib.lunaRefit.BaseRefitButton`, registered with `LunaRefitManager.addRefitButton`. Its `shouldShow(member, variant, market)` limits it to Reliquaries, and `hasPanel`/`initPanel(CustomPanelAPI, …)` opens a custom panel listing the store with a discard control per fighter. After a change, `refreshVariant()` updates the ship, so the refit swap is rebuilt from the new store. A discarded fighter is gone; it gives no LPC. The panel is player-facing UI, so `UI.md` and the text route apply. The button's `advance` and `onPanelClose` may also be usable to detect the refit closing in place of the polling script; that is unverified.
- **Persistence:** the store carries over between battles, kept per ship. Variant ids are shared by every ship of a stock variant, so key it by fleet member id. Purge it when the ship leaves the player's fleet, for example into storage.
- **AI:** fire when enough fighters are stored and a target in range is worth it.

### Early Reliquary

The player gets a version of the Reliquary early, with no blueprint. In combat it is the same ship: same hull, wings, salvage, store, launch system and refit display. It differs only in the campaign, where it does not unlock the Reployment Bays (see [FISHERMAN_CHAIN.md](FISHERMAN_CHAIN.md#ships)).

- **Form:** a hull skin (`.skin`) of `catchrelease_rod_ship` that changes nothing for combat. Its own hull id is what lets the campaign tell it apart.
- **Combat code must treat it as a Reliquary:** match the Reliquary by base hull id (`ShipHullSpecAPI.getBaseHullId`, which a skin points at its base hull), not by the exact hull id. That covers the built-in hullmod, the salvage and store, the launch system, the refit swap and the store's fleet-member key.

## Monstrance: Breach Trawler (later)

Lower priority; the hull art does not exist yet. Real breach-light projectors on the ship. The system spools the reactor and turns on the lights for a time, with cone visuals and sounds exactly as in the campaign. The number of cones depends on the projectors fitted. The cones sweep in a circle as in the campaign, with some targeting movement.

- **Effects:** ships in the light suffer soft flux build-up, a lower vent rate, faster CR loss and a higher malfunction chance.
  - Flux: `FluxTrackerAPI.increaseFlux(amount, false)`.
  - Vent rate: `getVentRateMult`.
  - Malfunctions: the weapon, engine and critical malfunction chance stats.
  - CR loss: the stat only takes effect after peak time runs out (`Ship.java:2374-2376`), so lower CR directly.
- **AI avoidance:** an invisible missile flagged as a primed mine with an inert AI, so ship AI steers around the lights (vanilla precedent `RealityDisruptorEffect.java:54-58`). Untested: whether point defence shoots it, and whether collision class `NONE` hides it from the AI.
- **System AI:** angle the lights away from friendly ships and toward hostile ones.
- **Rendering:** the campaign light renderers' GL bodies are portable; their LunaLib campaign base, `CampaignEngineLayers` and sector calls are not.
- **Open:** it is meant as a dodgeable debuff, and whether it is fun needs more design work.

## Dependencies

- `lib/MagicLib.zip` is MagicLib 1.5.6 (for 0.98a-RC7), which has `MagicSubsystem`. `mod_info.json` pins no MagicLib version; pin one if the mod comes to depend on `MagicSubsystem`.
- No part of this design needs PatchLib.

## Open questions

- Does `beginLandingAnimation` work on a wreck, and does the reflection reset of `timeBeforeFade` hold up in play?
- Does autofire ignore drones?
- Harpoon: rope length, duration, charge count, break-on-own-hull or not, and how mass ratios map to pull strength.
- Does the harpoon fire as a plain weapon, or through a `MagicSubsystem` with charges, a key binding and an AI hook?
- Do spawned recovered wings count toward deployment points?
- Refit swap: does the spec `clone()` work through `MethodHandles` at runtime, does `reportAboutToOpenCoreTab` fire when the core UI opens straight into the refit (if not, the close script also has to notice the refit opening), and do fighter wings change the crew figures shown while the swap is active?

## Implementation plan

### How the work is delivered

- One stage is one pull request, with one commit per numbered step, as in [FISHERMAN_CHAIN.md](FISHERMAN_CHAIN.md). Each stage ends with the compile gate, the checks below, the docs and the changelog.
- Every step that changes what the player sees in combat ends with an in-game test by the author. The PR lists what to try.
- Steps marked **spike** prove an untested engine assumption first, in a throwaway branch or behind a dev flag. If it fails, the step falls back as described, and this document is updated before the stage goes on.
- Player-facing text (descriptions, tooltips, settings, HUD labels) goes through [DIALOGUE.md](DIALOGUE.md); panels, renderers and HUD work through [UI.md](UI.md). UI design research goes to a Fable subagent (see `CLAUDE.md`); the main session writes the code.
- Ids use the `catchrelease_` prefix. Vanilla already owns `harpoon`.
- Combat code goes under `jars/src/catchrelease/combat/`, one package per ship: `tug`, `salvage` (Reliquary) and later `lights`. Shared helpers go in `combat/` itself. Each owner gets an `ARCHITECTURE.md` row when it lands.
- Checks go under `jars/src/catchrelease/tools/`, headless, in the style of the existing `*Checks` classes. Check the maths and rules, not restated code: rope constraint, acceleration falloff, store cap and wing grouping, blacklist, and refit-swap revert.

### Data files this introduces

| File | Contents |
|---|---|
| `data/hullmods/hull_mods.csv` | tug hullmod, Reliquary salvage hullmod |
| `data/weapons/weapon_data.csv`, `data/weapons/catchrelease_harpoon.wpn`, `data/weapons/proj/catchrelease_harpoon_shot.proj` | harpoon weapon and projectile |
| `data/shipsystems/ship_systems.csv`, `data/shipsystems/catchrelease_tug_drive.system`, `catchrelease_salvage_launch.system` | Stigmata drive, Reliquary launch |
| `data/hulls/wing_data.csv`, `data/hulls/catchrelease_lyne_drone.ship`, a `ship_data.csv` row, `data/variants/fighters/catchrelease_lyne_drone_wing.variant` | LYNE drone wing |
| `data/hulls/skins/catchrelease_rod_ship_early.skin` | early Reliquary |
| `data/hulls/catchrelease_harpoon_ship.ship`, `catchrelease_rod_ship.ship` | `builtInWeapons`, `builtInMods`, `builtInWings`; these are the author's files, edited only for those keys |
| `data/strings/descriptions.csv`, `data/config/settings.json`, `sounds.json`, `LunaSettings.csv` | descriptions, sprites, sounds, settings |

### Stage 0: data and dependencies

- [x] 0.1 New designations: LYNE Tender, Breach Trawler.
- [x] 0.2 Stigmata and Reliquary stats in `ship_data.csv`; placeholder systems.
- [x] 0.3 Update `lib/MagicLib.zip`.
- [x] 0.4 Placeholder harpoon projectile sprite: `graphics/catchrelease/weapons/harpoon_projectile.png` (12×26), a copy of the vanilla Harpoon missile's sprite.

### Stage 1: tug hullmod

- [x] 1.1 `combat/tug/TugDriveHullmod` (Tug Drive Field, `catchrelease_tug_field`): a `BaseHullMod` that also implements `HullModFleetEffect`, built into the Stigmata. Implemented; see [ARCHITECTURE.md](ARCHITECTURE.md#combattug).
  - `advanceInCampaign(fleet)` exits at once when the fleet has no non-mothballed tug (counted on fleet sync). Each frame:
    - count the tugs: +1 burn per tug through `getFleetwideMaxBurnMod().modifyFlat`;
    - acceleration through `getAccelerationMult().modifyMult` with 20% × (1 + ½ + ¼ …);
    - remove both under the same key while `Abilities.SUSTAINED_BURN` is active.
  - The tooltip states the numbers and the Sustained Burn rule.
  - Check: the falloff sum for 1–5 tugs, and that the bonus is gone with Sustained Burn on.
  - Done when: a fleet with tugs gains burn and acceleration, and loses them the moment Sustained Burn starts.

### Stage 2: harpoon

- [x] 2.1 Weapon data. Implemented; see [ARCHITECTURE.md](ARCHITECTURE.md#combattug).
  - `catchrelease_harpoon`: built-in MEDIUM ballistic-styled weapon on `WS0001`, real kinetic damage, `ammo`, `ammo/sec`, `reload size`.
  - The projectile uses the placeholder sprite.
  - The turret's return to its default angle is fast but smooth: a turn rate high enough to feel quick, no snap.
  - Done when: the Stigmata fires harpoons from its built-in mount in the simulator.
- [x] 2.2 Hit rule: `combat/tug/HarpoonOnHit` (`OnHitEffectPlugin`). Implemented; see [ARCHITECTURE.md](ARCHITECTURE.md#combattug). The `onFire` hook is the weapon's `everyFrameEffect` (`HarpoonWeaponEffect`); `onFireEffect` is a `.proj` key.
  - A shield hit (`shieldHit`, reliable because the shot does damage) spawns a deflected copy.
  - A hull or armour hit on any ship or wreck creates a `Tether`: target, attachment point in the target's local frame, rope length = distance at impact.
  - Firing again releases the current tether.
- [x] 2.3 Tether physics: `combat/tug/Tether`, advanced by one `EveryFrameCombatPlugin` that owns every live tether. Implemented; see [ARCHITECTURE.md](ARCHITECTURE.md#combattug). Duration 20 s; the hull-break setting is `catchrelease_harpoon_rope_hull_break`, on by default.
  - **Pull:** when taut, remove the outward velocity along the rope and add an impulse split by mass, so the lighter ship moves more. Slack never pulls.
  - **Top speed:** a temporary max-speed allowance on a dragged target so its auto-brake does not fight the rope.
  - **Breaks** on:
    - duration;
    - target destroyed or removed;
    - the rope crossing the tug's hull (LazyLib `CollisionUtils`, tested from outside the hull) — behind a setting, off if it plays badly.
  - Check: the constraint maths, i.e. mass split, slack, and no pull while slack.
  - Done when: the tug can hook and drag a frigate, struggles with a cruiser, and the rope behaves when slack.
- [x] 2.4 Rope rendering: `combat/tug/TetherRenderer`, a `BaseCombatLayeredRenderingPlugin` per tether, its entity kept at the rope's midpoint with a render radius covering the rope.
  - **Look:** about 2 px wide, beige-white centre, brown edges, as a feathered quad strip along the campaign rope's curve and slack maths, ported from `HarpoonEntityPlugin`.
  - **Flight and bite:** low alpha in flight, then it "materialises" on the bite, with EMP arcs racing along the rope.
  - Done when: the rope reads clearly at all zooms and looks right slack and taut.
  - Implemented; see [ARCHITECTURE.md](ARCHITECTURE.md#combattug) and [UI.md](UI.md#drawing-gotchas). The renderer also draws the harpoon head, since a bullet sprite is always additive.
- [x] 2.5 Turret return polish and any feel tuning found in 2.3–2.4. The return is implemented ([ARCHITECTURE.md](ARCHITECTURE.md#combattug)); feel tuning waits for the author's in-game test.

### Stage 3: drive system

- [x] 3.1 `catchrelease_tug_drive` (Haul Drive) with `combat/tug/TugDriveStats` (`BaseShipSystemScript`). Implemented; see [ARCHITECTURE.md](ARCHITECTURE.md#combattug). It uses vanilla's maneuvering-jets system AI until 4.3.
  - **Stats while active:** raised mass (`setMass`, restored in `unapply`), acceleration, turn acceleration and turn rate.
  - **Visuals:** `extendFlame` and `fadeToOtherColor` on the engines, dark `addNebulaSmokeParticle` smoke from the nozzles.
  - **Uses:** charges through `max uses`/`regen`.
  - It replaces `maneuveringjets` in `ship_data.csv`.
  - Done when: a hooked cruiser can be hauled with the drive on, and the smoke and plumes look right.

### Stage 4: Stigmata AI

- [ ] 4.1 Harpoon targeting: a weapon autofire AI from `ModPlugin.pickWeaponAutofireAI`.
  - Prefer vulnerable targets (exposed hull, shields down or flanked) that friendly ships are not already hammering, or that are fleeing.
  - Fire again to release when the tether is no longer useful.
- [ ] 4.2 Towing movement: while a tether is live, a wrapper around the ship's default AI (`setShipAI`) calls it, then overrides movement with `giveCommand` and `blockCommandForOneFrame`.
  - v1 drags the target toward friendly ships and away from the strongest enemy group.
  - Study Ed Shipyards and Knights of Ludd for movement-override patterns first.
- [ ] 4.3 `ShipSystemAIScript` for the drive: use it when the rope is taut and the target resists, or to escape.
- Done when: an AI Stigmata in the simulator hooks sensible targets and drags them somewhere useful.

### Stage 5: LYNE wing

- [ ] 5.1 Data.
  - LYNE drone fighter hull: unarmed, fast, nimble, low hull, `munitions_drone.png`.
  - Its variant and a `wing_data.csv` row: 5 drones, very short refit.
  - Two `builtInWings` on the Reliquary.
- [ ] 5.2 **Spike**, all three in the simulator:
  - `ShipAPI.setHulkChanceOverride` on enemy fighters;
  - topping up `timeBeforeFade` by reflection (`ReflectionUtils`) once a second;
  - `beginLandingAnimation` on a wreck.

  Fallbacks: if the timer cannot be held, the drone carries a drawn copy (losing the damage overlay); if the landing shrink fails on a wreck, fade it out with the drone.
- [ ] 5.3 `combat/salvage/WreckTracker`: an `EveryFrameCombatPlugin` active only while a Reliquary is deployed.
  - Sets the wreck chance (setting, default 75%) on eligible enemy fighters.
  - Keeps their wrecks for 30 seconds.
  - Hands out claims, one wreck per drone.
  - Applies the blacklist: non-fighters, station modules, drones, shuttle pods; hull tags `threat`, `swarm_fighter`, `fragment`, `dweller`, `monster`; wing tags `restricted`, `independent_of_carrier`.
  - Check: the blacklist rules.
- [ ] 5.4 `combat/salvage/LyneDroneAI` (`ShipAIPlugin`), picked in `ModPlugin.pickShipAI` by a hull tag.
  - Cycle: claim → fly → attach → tow (wreck collision class `NONE`) → reach `LB 1` → landing shrink, with hit particles at the bay.
  - Drones ignore wrecks while the store is full.
- Done when: the drones visibly salvage fighter wrecks in a simulator fight.

### Stage 6: recovered fighters

- [ ] 6.1 `combat/salvage/FighterStore`, saved per fleet member id, on the sector's persistent data.
  - Wing id, count and restoration progress per entry.
  - Cap 10 (setting), counting fighters still restoring.
  - Restoration at the wing's refit time with the ship's refit modifiers.
  - Purged when the member leaves the player's fleet: a fleet callback, as `FishingShips` uses.
  - Check: cap, restoration and grouping into wings no larger than the wing size.
- [ ] 6.2 Launch system: `catchrelease_salvage_launch` with `combat/salvage/SalvageLaunchStats`. It replaces `targetingfeed`.
  - Spawns every restored fighter with `spawnShipOrWing`, groups them into wings, removes surplus members, sets the source ship and CR, and calls `removeDeployed` on the spawned wing, as `SwarmLauncherEffect` does.
  - Every launched fighter gets `setHulkChanceOverride(0)`.
  - Landed fighters go back into the store; destroyed ones are gone.
- [ ] 6.3 `ShipSystemAIScript` for the launch: fire when enough fighters are restored and a target in range is worth it.
- [ ] 6.4 Combat status display: one `maintainStatusForPlayerShip` line per stored type, or a richer `renderInUICoords` panel. Fable research first.
- [ ] 6.5 Refit display, in this order:
  1. **Spike** the spec `clone()` through `MethodHandles` and whether `reportAboutToOpenCoreTab` fires when the core UI opens straight into the refit.
  2. `combat/salvage/RefitStoreView`:
     - takes the variant for this member alone;
     - swaps in the spec copy on open;
     - extra bays from the salvage hullmod only outside combat;
     - reverts on close;
     - the restore guard;
     - strips stray wings on load and on leaving the fleet.
  3. Check: revert leaves no stored wing ids in the variant.
- [ ] 6.6 Salvage hullmod tooltip listing the store, the fallback display.
- [ ] 6.7 LunaLib refit button and panel to discard stored fighters (`BaseRefitButton`). Afterwards, `refreshVariant()`.
- [ ] 6.8 Early Reliquary skin. Combat code matches it through `getBaseHullId`. Campaign:
  - a `FishingShips` entry with the ROD rig's second slot and no module;
  - +1 drone in `FishingDroneSwarmScript`.

  Where the player gets it is still open, so no grant yet.
- Done when: fighters are salvaged in one battle, show in the refit, can be discarded, launch in the next battle and return.

### Stage 7: Monstrance

- [ ] 7.1 Hull art and data, cruiser.
- [ ] 7.2 Light projectors and the light system: cone rendering ported from the campaign renderers, effects, mine-flagged invisible missiles for AI avoidance, and system AI. Revisit the design first; it is a dodgeable debuff whose fun is unproven.
