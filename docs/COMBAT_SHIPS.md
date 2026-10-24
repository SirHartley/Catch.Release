# Combat ships

Read this before implementing or revising the combat side of the three fishing ships: their hulls, hullmods, weapons, ship systems, wings, combat rendering and combat AI. It records the approved concept, the decisions taken, the engine facts the design depends on, open questions and the build plan. It does not govern the ships' campaign role or quest flow ([FISHERMAN_CHAIN.md](FISHERMAN_CHAIN.md)), player-facing text ([DIALOGUE.md](DIALOGUE.md), [LORE.md](LORE.md)) or Java UI contracts ([UI.md](UI.md)). Move implemented technical detail to [ARCHITECTURE.md](ARCHITECTURE.md) and tick the plan here.

Engine facts below are from Starsector 0.98a-RC8 (the `starsector-knowledge` skill: `api/`, decompiled `obf/`, `data/`) unless marked otherwise. Line references are to that build.

## Ships

| Ship | Hull id | Class | Designation | Art |
|---|---|---|---|---|
| Stigmata | `catchrelease_harpoon_ship` | Destroyer | Harpoon Tug | `graphics/catchrelease/ships/harpoon_boat.png`; harpoon weapon `graphics/catchrelease/weapons/harpoon_harpoon.png` |
| Reliquary | `catchrelease_rod_ship` | Cruiser | LYNE Tender | `graphics/catchrelease/ships/done_boat.png` |
| Monstrance | `catchrelease_lamp_ship` | Cruiser | Breach Trawler | none yet; the hull is still a Nebula copy |

All three are midline. Stats for the Stigmata and Reliquary are set in `ship_data.csv`; their systems are vanilla placeholders (`maneuveringjets`, `targetingfeed`) until the custom systems exist.

## Stigmata: harpoon tug

A fast destroyer with classic midline stats, one modular launch bay, three small ballistic turrets and a built-in medium mount for the harpoon (`WS0001`: angle −59.5, arc 190). The mount's default angle lets the rope run past the hull while the tug burns straight away from its target.

### Tug hullmod

Built in, placeholder icon.

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
- **While attached:** the rope keeps its length. When taut it pulls both ships along the rope, split by mass, so lighter hulls are dragged more easily. Mass is a plain field (`CombatEntityAPI.getMass/setMass`), not a stat. LazyLib `CombatUtils.applyForce` does not scale by frame time. A ship idle and above its top speed brakes on its own (`obf/combat/entities/Ship.java:2031-2039`), so a dragged target may need a temporary top-speed allowance. The rope can go slack; slack must never pull.
- **Release:** firing again releases the current rope. The rope also breaks when its duration runs out, when the target is destroyed or removed, and, probably, when the rope crosses the tug's own hull while it turns (LazyLib `CollisionUtils`; start the test outside the hull, since a line starting inside the bounds is missed).
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

A classic midline carrier cruiser: slow and cumbersome, with high crew capacity. One built-in wing of LYNE drones and two modular bays (`LB 1` is the built-in wing's bay).

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
- **Refit display:** a built-in Reliquary hullmod lists the store in its tooltip: fighter sprites, counts and restoration progress. `HullModEffect.addPostDescriptionSection` receives the refit `ShipAPI`, whose `getFleetMemberId()` finds the store. Rejected alternatives:
  - drawing on the refit screen directly has no API hook; it would need guessed screen positions or a PatchLib patch;
  - per-ship wings through `ShipVariantAPI.setWingId` need bays, and a bay would build those fighters itself, which breaks the store.
- **Persistence:** the store carries over between battles, kept per ship. Variant ids are shared by every ship of a stock variant, so key it by fleet member id. Purge it when the ship leaves the player's fleet, for example into storage.
- **AI:** fire when enough fighters are stored and a target in range is worth it.

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

## Plan

**Stage 0: data and dependencies**
- [x] 0.1 New designations: LYNE Tender, Breach Trawler.
- [x] 0.2 Stigmata and Reliquary stats in `ship_data.csv`; placeholder systems.
- [x] 0.3 Update `lib/MagicLib.zip`.
- [x] 0.4 Placeholder harpoon projectile sprite: `graphics/catchrelease/weapons/harpoon_projectile.png` (12×26), a copy of the vanilla Harpoon missile's sprite.

**Stage 1: tug hullmod**
- [ ] 1.1 Hullmod with the fleet effect, Sustained Burn check and diminishing acceleration.

**Stage 2: harpoon**
- [ ] 2.1 Weapon and projectile data, built into `WS0001`.
- [ ] 2.2 Hit rule: glance off shields, attach to hull or armour, any ship or wreck.
- [ ] 2.3 Rope constraint and mass-weighted pull, slack, release and break rules.
- [ ] 2.4 Rope rendering and the connection effect.
- [ ] 2.5 Smooth turret return.

**Stage 3: drive system**
- [ ] 3.1 Mass, acceleration and turn boost; plumes and smoke.

**Stage 4: Stigmata AI**
- [ ] 4.1 Harpoon target choice.
- [ ] 4.2 Towing movement.
- [ ] 4.3 Drive system AI.

**Stage 5: LYNE wing**
- [ ] 5.1 Wing and drone hull data; built-in wing on the Reliquary.
- [ ] 5.2 Drone AI: claim, attach, tow, land.
- [ ] 5.3 Wreck chance setting, wreck lifetime, blacklist.

**Stage 6: recovered fighters**
- [ ] 6.1 Store, restoration and persistence per fleet member.
- [ ] 6.2 Combat status display and the refit hullmod tooltip.
- [ ] 6.3 Launch system and its AI.

**Stage 7: Monstrance**
- [ ] 7.1 Hull art and data.
- [ ] 7.2 Light system, effects, AI avoidance and system AI.
