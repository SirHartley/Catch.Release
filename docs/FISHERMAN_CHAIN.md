# Fisherman follow-up chain

**Status: approved design, not yet implemented.** This document records the author's decisions for the quest chain that follows the tutorial, plus the open questions and the work each step needs. Read it before implementing or revising any of these quests.

It does not govern lore, prose or rules structure. [LORE.md](LORE.md) remains the authority on facts, knowledge limits and voice. [DIALOGUE.md](DIALOGUE.md) owns the text workflow, and [RULES_WRITING.md](RULES_WRITING.md) owns conversation structure. Text described here is a brief, not approved dialogue. The deferred [Needle quest concept](NEEDLE_QUEST_CONCEPT.md) follows after this chain; see [Ships](#ships).

## Framing

- The tutorial still ends where it does today. `FishingIntro.isComplete()` keeps its current meaning, and everything gated on it (legendary spawns and the shop) is unchanged.
- The Fisherman offers T1 straight after the tutorial's last hand-in. After a skipped tutorial, which has no last hand-in, he offers it on the next visit. The chain is not presented as more tutorial: the tutorial should feel short, and this reads as ordinary work.
- Each step teaches one thing:
  - T1: lamp catches pay better than pond catches.
  - T2: fleets can have work for you, and epics are hunted.
  - T3: equipment is how you win hunts.
  - T4: there is more out there, and rumours lead to it.

## Shared mechanic: two-stage epics

Implemented in `EpicHunt` (see [ARCHITECTURE.md](ARCHITECTURE.md)).

- Every epic is a Breach Light catch, never a rupture catch.
- It drifts slowly until a harpoon hit wakes it. That hit is deflected and raises a single purple shield charge that never regenerates.
- The awake epic swims at full speed and more erratically. A failed catch loses it, like any other fish.
- More than 3,000 units from the player resets it to drifting. It drifts a short way and fades, like any fish at the end of its swim. The shield charge is never restored: once spent, every later hit lands.
- Awake epics are removed when the player leaves the system.

T2 and T3 build on this; T2's failure condition is the reset.

## Shared mechanic: ship-bound equipment

Not implemented. Each fishing ship belongs to one rig and carries one module for it (see [Ships](#ships)). The harpoon ship goes with the Harpoon (Harpoon Tips), the Breach Light ship with the Breach Lights (Lens Arrays) and the drone carrier with the ROD (Drone Cores).

- **Second slot.** While a ship of that kind is in the fleet, its rig gets a second equipment slot, and the ship's module becomes available.
  - This happens once per rig. Two harpoon ships still give one extra harpoon slot.
- **Fitting.** The second slot works like the first. The player can swap the ship's module out for any other module they own.
  - While the ship is in the fleet, its module can be fitted in either slot.
- **Ship leaves the fleet.** When the last ship of that kind leaves, the second slot is emptied at once. The ship's module is unfitted wherever it sits, because it is only available with the ship.
- **Ship rejoins.** The second slot resets to the ship's module. It does not remember what was fitted before.
- **Two modules on one rig.**
  - Effects apply in slot order: slot 1, then slot 2.
  - No module takes up two slots.
  - A module cannot fill both slots of one rig. In the Outfitter it is greyed out in the rig's other slot, and its hover says it is already fitted there.
  - Rigs do not block each other. The three modules that fit both the ROD and the Harpoon (Calibrated Grader, Barbed Head, Sonar Head) can still be fitted to both rigs at once, as today.
  - Modules with similar or opposing effects cannot share a rig. Two modules that change the same value count as similar. The pairs are listed under [Incompatible pairs](#incompatible-pairs).
  - In the Outfitter, a module that conflicts with the one in the rig's other slot is greyed out for that slot and cannot be fitted there. Its hover names the conflicting module.
- **Supply.** A module that moves onto a ship leaves the shop and the reward pools; the ship is the only way to get it. This applies to the Retrieval Head and the Fanned Array. The drone carrier's module, the Reployment Bays, is new.

**Work**
- Fitted state is one module per rig today (`TackleManager.getFitted()`, read through `TackleManager.get(Fit)`). It needs a second slot per rig, and every effect check must read both slots.
- Fleet presence: look for a fleet or cargo callback before adding a fleet scan; see [CLAUDE.md](../CLAUDE.md#make-and-record-changes) on polling. A save loaded without the ship must still empty the second slot.
- Removing a module from the shop: `stocked = false`, keeping the `Tackle` constant, as with the Breach Coupler.
- Declare incompatibility on `Tackle`, for example as a shared exclusion group per effect, rather than as pairs in the Outfitter code, so a new module is checked against every other.

### Incompatible pairs

Every module was checked against the others that fit the same rig.

| Rig | Pair | Why |
|---|---|---|
| ROD | Spool Governor + Inertial Damper | Both change how fast the catch window rises (`barLiftMult`). |
| ROD | Salvage Trawl + Lifting Rig | Both raise the bycatch chance (`treasureChanceMult`). |
| ROD | Holdfast Clamp + Barbed Head | Both work on catch progress: one slows its loss, the other speeds its gain. |

Every other pair is compatible. These were checked specifically:
- **Explosive Head with any tip:** the blast destroys the pattern anyway, so pairing it changes nothing.
- **Needle Sensor + Neutrino Lens:** the Needle Sensor only runs with the Breach Lights off and the Neutrino Lens only with them on, so they never act together.
- **Fanned Array + Tracking Gimbal:** one sets the beam's shape and sweep speed, the other pauses the sweep on a pattern. `Searchlight.acquire` already picks the best-lit pattern under a fan.

Because no ship module is in a pair, the reset when a ship rejoins never meets a conflicting slot 1. The disabled Breach Coupler is not assessed; check it if it returns.

## T1: The treasure rumour

**Premise.** Word goes round that retrievals in a nearby system are bringing up far more bycatch than usual. The Fisherman appears to have lost something and is sure it is there. That is all he says about it.

**Steps**
1. The Fisherman names the system and asks for one specific treasure: a golden chest.
2. The player fishes there under Breach Lights. Each **lamp catch** may produce the golden chest in the minigame. Pond catches never do.
3. The chest holds his compass. The player brings it back to him.
4. Rewards are granted (below), and the follow-up quest starts immediately.

**Golden chest**
- A quest-only minigame treasure, separate from the Salvage, Wreckage, Cache and Relic tiers.
- Chance: 30% on each lamp-method catch in the target system while the quest is active.
- Frustration gate: guaranteed on the 4th lamp catch, then on every 2nd catch after that until the player pulls it.
- What counts: every minigame opened on a lamp-method, non-legendary fish in the target system. The chest only pays out on a won minigame, like all treasure.
- The player still has to take it in the minigame like any treasure. A missed chest counts as not pulled.

**Compass**
- Special item using the icon `graphics/catchrelease/icon/compass.png`, base value 1 credit, cosmetic.
- Plugin: vanilla's plain `com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin`, as used by its Topographic Data and Planetkiller rows. There is no `DefaultSpecialItemPlugin` class.
- It must never be cleared from markets. Nothing extra is needed: vanilla restocks never remove special items, and `SoldFishRestock` only removes catch items.
- It is the Fisherman's own. He takes it back at hand-in and does not explain how it got there or how he knew where it was.
- Nobody remarks on its needle, and it does nothing unusual. The needle stays a writer's metaphor ([LORE.md](LORE.md#origin-and-nature), [Needle quest concept](NEEDLE_QUEST_CONCEPT.md)).

**A lost compass.** The quest is about getting the compass, not about handing it in, so losing it must not block the player.

- While the quest is active, a `CampaignEventListener` (`reportPlayerMarketTransaction`) watches for the compass leaving the player's cargo. It records the market where it was last sold; a free-transfer submarket counts as storage, not a sale. The quest removes the listener when it concludes.
- If the compass is not in the player's cargo at hand-in, the player can tell the Fisherman "I lost it". He asks "Are you sure?" When the player confirms:
  - **Sold:** he's pretty sure he saw it on sale at that market, and the player had better go and get it. The quest continues.
  - **Not sold:** check, once, every unlocked storage and colony submarket.
    - Found in storage: he tells the player to fetch it from there. The quest continues.
    - Not found anywhere: the quest concludes and the player still gets the rewards.

**Rewards**
- Equipment: the **Glitter Lens** schematic. It is a Lens Array module that raises the treasure chance for catches made under Breach Lights only, using the same catch-method distinction as the rest of the shop.
  - Implemented: `Tackle.GLITTER_LENS`, treasure chance ×2 for lamp catches (the Salvage Trawl gives ×3 on the ROD).
  - Price: the lowest module tier in `ShopPricing`, plus its generated fish payment.
- The means to buy it: the exact fish specimens that satisfy the Glitter Lens's saved fish payment (`ShopPricing` caches each module's payment), plus its credit price.

**Follow-up: buy the Glitter Lens.** Completes when the module is owned. The Fisherman then says the system's raised treasure chance will hold for another two months.

**Work**
- The rumour can reuse the `FishRumors` `LOOT` kind pinned to the target system. Extending it by 60 days after the purchase needs a quest-owned duration, so the normal rumour rotation does not replace it.
- New: the golden chest treasure, its pity counter and the compass item row. The Glitter Lens already exists.
- The reward specimens must be generated from the cached payment at grant time, so they always match.

## T2: The captain's fleet

**Premise.** "A former worker of mine, on another fleet now, asked me to help out with something." This introduces fleets that offer work.

**The fleet**
- Spawned in a nearby system with **zero markets and no colonies**, preferably adjacent.
- It is following a rumour of an epic seen in that system. The target is an **existing epic species** whose range includes that system. It is not Lantern Jack or any other legendary.
- Their Breach Lights have malfunctioned, so they cannot find it.

**The captain**
- An old, grizzled independent skipper, portrait `portrait30.png` (a vanilla portrait; confirm the path in the target game install).
- Speaks with Scottish-style speech patterns: accent, dialect and slang. That is only how he talks. Scotland, Earth and their places do not exist in the setting, which stays fully vanilla ([LORE.md](LORE.md#canon-sources)). He never names them, and nothing explains his speech by an origin outside the vanilla Sector. He needs a voice entry in `LORE.md` before any lines are drafted.
- Tells a personal encounter with the epic that fits any species, for example that it took his leg. The tale must not establish species-specific mechanics.

**Steps**
1. The captain asks the player to circle within sight of the system's star with lamps on.
2. After 15 continuous seconds with Breach Lights on within 6,000 units of the star, the epic's buried mote is placed in the path of the player's lamp beam. Leaving that range or turning the lamps off restarts the count.
3. The player is told to harpoon it if found, and the two-stage epic hunt begins.
4. If the epic resets (more than 3,000 units from the player), the hunt fails. A notification sends the player back to the captain.

**Outcomes**
- **Caught (unexpected).** The captain is overjoyed; he has hunted it for years and can retire now.
  - Extra rewards: one harpoon module schematic, or any module schematic if all harpoon ones are unlocked, plus three range-data entries. Ship modules are never offered.
- **Not caught (expected).** The captain talks about his years-long chase and says it got away again, but he'll get it next time. He is grateful for the data the player gathered.
- **Both outcomes:** the harpoon fishing ship. It brings a second Harpoon slot and the Retrieval Head (see [ship-bound equipment](#shared-mechanic-ship-bound-equipment)).

**Return to the Fisherman.** He thanks the player and explains how epics and legendaries behave when hunted. Optional questions add some background lore, which the mod is currently light on. His answers stay within his knowledge limits and his habit of not over-explaining (`LORE.md`). He gives the player the **Needle Sensor** schematic and tells them to build it.

**Follow-up: build the Needle Sensor.** A tracked intel quest. Its first step is to buy the Needle Sensor at the Outfitter with the schematic. Once the player owns it, the intel tells them to go back and talk to the Fisherman, and T3 starts in that conversation. If the player already owns one, the intel starts at the return step.

**Work**
- Quest fleet: an independent fleet with a contact person, using the existing fleet-quest spawning and contact patterns where possible.
- Choosing the target: an epic species whose range matches the zero-market system. If none qualifies, choose another system rather than a species from elsewhere.
- Placing the mote:
  - count continuous lamp-on time within 6,000 units of the star;
  - then put a buried mote of the target species ahead of the beam sweep;
  - one quest-owned fish, claimed so other spawners and Lantern Jack's feeding leave it alone.
- Failure: a hook in `EpicHunt`'s reset branch for quest-owned fish (see [Implementation plan](#implementation-plan)).
- Needle Sensor follow-up: grant the schematic through `FishReward.tackleSchematic`. Advance the intel to its return step when the Outfitter closes and the module is owned, not from `ShopEntry.grant`: the Outfitter's undo refunds a purchase through `TackleManager.consume` without passing through `ShopEntry`.

## T3: The wounded epic

**Premise.** The Fisherman points the player at a nearby system with epics. One has been wounded and is recovering, circling a planet at about 1,000 units.

**Steps**
1. The player uses the Needle Sensor built after T2 to locate it.
2. It has the usual epic shield and stages. Asleep, it drifts at the normal epic drift speed; awake, it moves 50% slower than a normal awake epic.
3. If it gets too far away (the hunt resets), it despawns and respawns at the planet with a campaign message along the lines of: "The pattern returns to the planet to recuperate once more."
4. The player is expected to hunt it until caught, then return to the Fisherman.

**Rewards**
- The blueprint for the second fishing ship, the Breach Light ship. It brings a second Lens Array slot and the Fanned Array.
- Range data on an epic.

**Follow-up: have the ship built.** The player works out how; the Fisherman does not build it. Completes when the player owns a ship of that hull.

**Closing conversation**
- Gist: "Rumours about the stranger fish are worth following once your gear is very good." He does not name legendaries, so T4 stays vague.
- Epics are not unique, but they are rare enough that they might as well be.

**Work**
- **The circling must not use `orbitAnchor`.** `NeedleSensor.isEligible` and `EpicHunt.applies` both exclude orbit-anchored fish (that field marks shield escorts), so the sensor would never see it and it would never wake. Drive the circling with swim targets around the planet, or add a quest flag both checks respect.
- Slower movement: a per-fish multiplier of 0.5, applied only while awake, on top of `EpicHunt`'s speed.
- Respawn: on the quest fish's reset, expire it and spawn a fresh one at the planet.

## T4: Listen for rumours

**Decided**
- The Fisherman tells the player to improve their gear and listen for rumours. He stays vague about *what* they are hunting.
- The intel's title is along the lines of "Listen for rumours". It is a permanent reminder that there is more to find. Neither it nor the Fisherman mentions a reward or names legendaries.
- It is a long-term quest: no deadline. It completes on the first legendary catch, whenever that happens.
- There is no gear checkpoint. Player skill is part of how hard a legendary is.
- The ROD ship (drone carrier) is still the reward on completion. Nothing announces it beforehand.

**After the first legendary: hunting the legendary fish**
- When T4 completes, the player learns there is more than one legendary. The intel changes to "Hunting the legendary fish".
- It lists one entry per legendary species, each shown as question marks until known, so the list shows how many there are.
- Each rumour the player hears about a legendary turns its question marks into the last known name: the name the teller used. A bar teller can give a folk name or the wrong one, so an entry can stay wrong until the catch.
- Every rumour fills in a name. A telling that names nothing gives either a wrong name or one taken from its story, for example "World Serpent" for an eel that lies curled with its tail in its mouth.
- Catching a legendary changes its entry to the species' real name.
- The intel concludes once every legendary has been caught (six today). What happens then is decided later.

**Work**
- Count the entries from the legendary rows in `fish.csv` (six today), not a constant.
- Record each species' told name whenever a legendary rumour is heard, including before the list appears. There are two sources:
  - the Fisherman's monthly lead (`FishRumors`);
  - the bar spacer's report (`RatingBarEvent`, `CatchReleaseRatingLegendaryReport` rows).
- Today's spacer tellings name some legendaries ("Lantern Jack", "The Moray", "The Imposter") and give no name for the Quorum, False Dawn and Ghost Manta. None gives a deliberately wrong name. Each rumour therefore needs an explicit told name, following the rule above. The tellings' dialogue can keep saying no name was given; the intel's name comes from the story.
- Catches: `LegendaryChases.isCaught`. A legendary caught before T4 starts completes it at once.

## Ships

| Ship | Rig | Given by | Ship module ([second slot](#shared-mechanic-ship-bound-equipment)) |
|---|---|---|---|
| Harpoon fishing ship | Harpoon | T2, either outcome | Retrieval Head: a hit restores the spent harpoon charge |
| Breach Light ship | Breach Lights | T3 blueprint; the player builds it | Fanned Array: each Breach Light becomes a wide fan |
| ROD ship (drone carrier) | ROD drones | T4, first legendary | Reployment Bays (new Drone Core module): once every drone has returned, the drones deploy again to the same area |

None of the hulls exist yet. Until they do, each ship is a placeholder built on the vanilla Nebula hull (`nebula`, a civilian transport). Give each placeholder its own hull id, for example a `data/hulls/skins/*.skin` of `nebula` with its own `skinHullId` and name. Otherwise three Nebulas cannot be told apart, and the fleet check would not know which rig gets the second slot. With all three ships allocated, the [Needle quest concept](NEEDLE_QUEST_CONCEPT.md) can be revisited.

## Open questions

1. **What concluding "Hunting the legendary fish" brings.** It ends once every legendary is caught; the payoff is decided later.

The research for the implementation plan turned up these gaps in the design. Each has a suggested default that the plan assumes until decided:

4. **T2 failures other than the reset.** A failed catch, or an Explosive Head blast once the shield is down, also removes the epic. Suggested: both fail the hunt like a reset.
5. **T2's 6,000 units.** Suggested: measured from the star's surface, as the existing legendary code measures; systems without a star (nebulae) are never chosen.
6. **T3's sleeping fish.** Today a sleeping epic is either a buried mote (no swim target) or a surfaced fish that fades at its target. Suggested: the wounded epic is surfaced from the start and circles on its own route, asleep or awake.
7. **Legendary rumour intel names.** The existing legendary rumour intel shows the species' real name (`FishRumors.getStrangerDisplayName`), which contradicts T4's told names. Suggested: it shows the told name too.

## Implementation plan

Six phases, each its own pull request. Phase 0 changes no visible behaviour; it removes the obstacles the research found. Write all player-facing text through [DIALOGUE.md](DIALOGUE.md) and [LORE.md](LORE.md). Route UI scoping to the UI subagent ([CLAUDE.md](../CLAUDE.md#claude-model-assignments)).

### Architecture

| Area | Owner (new or changed) | Role |
|---|---|---|
| Chain state | New `campaign/fish/fisherman/chain/FishermanChain` | Saved stage per quest (T1–T4 and follow-ups) in sector persistent data, as `FishermanQuest` stores its `STATE_KEY`. Entry points are `CatchReleaseCMD` verbs. A `tokens` Boolean per offer gates the `CatchReleaseFisherOptions` rows. Registers its transient listeners on load. |
| Chain intel | New `BaseIntelPlugin` subclasses in the same package | One intel per quest: lost compass, Needle Sensor build, wounded epic, and the rumour hunt (title and body switch after the first legendary, as `FishingIntro.IntroIntel` switches by stage). Created through `FishIntelNotifications.queue`. |
| T2 mission | New `BaseHubMission` subclass | Needs a contact person and a fleet, and `BaseHubMission` supplies both and ticks `advanceImpl`. Fleet spawning follows `CampedSpot.spawn`; holding and friendliness reuse `FleetQuest.hold`/`keepStanding`; comms reuse the `catchrelease_fleetQuestHail` pattern. Not a `FishJob`: no fish are delivered. |
| Rig slots | `TackleManager`, new `RigLoadout` and `TackleEffects` | Two slots per rig. `TackleEffects` combines slot 1 then slot 2 (multipliers multiply, flags OR). It replaces every single-`Tackle` read: the minigame constructors, `NeedleSensor`, `NeutrinoLens`, `Searchlight`, `HarpoonEntityPlugin`, the ability tooltips and `PondFishSpawner`. The no-repeat rule within a rig and incompatibility are enforced in `TackleManager.fit`. |
| Incompatibility | `Tackle` | An exclusion group per effect (catch-window rise, bycatch chance, catch progress), declared on the constant. |
| Ships | New `campaign/fish/ships/FishingShips` | Hull id → rig → ship module. `reconcile()` compares fleet presence with slot 2 and applies the leave/rejoin rules. Ship modules are available while their ship is present, never added to the owned set, and have no price. |
| Fleet presence | `FishingShips` listeners | Vanilla has no fleet-membership callback (`FleetData.addFleetMember` reports nothing). Covered: `reportPlayerMarketTransaction` (buy, sell, storage), `FleetEventListener.reportBattleOccurred` (losses), `ShipRecoveryListener`, `onGameLoad`, and quest grants calling `reconcile()` directly. Not covered: scuttling and changes made by other code. A member-count comparison in the rig abilities' existing `advance` covers those. It is a documented polling exception, with `reconcile()` also run when the Outfitter opens or an ability activates. |
| Hull ids | Skins of `nebula` | `FleetMemberAPI.getHullId()` returns the skin id. A D-modded ship becomes `<id>_default_D`, so match `spec.isDefaultDHull() ? spec.getDParentHullId() : spec.getHullId()`. No `ship_data.csv` rows are needed. |
| Outfitter | `FishShopDialog`, `ShopRowPlugin`, `ShopEntry`, `ShopDetailHeaderPlugin` | Second slot per rig, a disabled row state (rows have none today), and row hovers naming the conflict or where a module is fitted. Undo restores per slot. The "one slot" text changes. |
| Quest motes | `BuriedMoteEntityPlugin`, `BuriedMoteSpawner`, `QuestPond` | `unearth()` copies the quest claim (`QuestPond.markPlanted` memory). The spawner's population count and `cullDistant` skip quest motes. |
| Epic hooks | `EpicHunt`, `HarpoonEntityPlugin` | A listener interface for quest-owned epics: reset (the branch in `EpicHunt.advance`), failed catch (the harpoon's `onCatchResolved` path, where epics currently fade unreported), blast and catch (`FishItems.addToPlayerCargo` hooks with `questTargetId`). Saved per-fish fields for an awake speed multiplier and a circle route. |
| Ship rewards | `FishReward` | New `ship(hullId)` reward that adds a fleet member to the player fleet; there is none today. `shipBlueprint` already exists. |
| Golden chest | `FishingMinigameDialogPlugin`, `FishingMinigame`, `TreasureRoller` | `init` decides "chest due" from chain state, the system and `CatchImplement.BREACH_LAMP`, as `FishingIntro.applyCatchTargetProtection` does. The chest is a flagged `MinigameTreasure`, not a new `TreasureRarity`, so `rollRarity` never draws it. It is granted on a win and counted in `resolve()`. |
| Rumours | `FishRumors` | T1 publishes a quest-owned `LOOT` rumour without starting the shared cooldown. The chain extends its `duration`. `publish` is the single choke point where T4 records told names. |
| Legendary list | `LegendaryChases`, `FishSpecLoader` | Rows from `getAllFishSpecs()` with `rarity == LEGENDARY` and `spawnWeight > 0`. Caught state from `isCaught`; completion from `noteCaught`. |

### Phases

**Phase 0: groundwork.**
1. `TackleEffects` and the two-slot `TackleManager` API with slot 2 always empty. Move every single-module read to it.
2. Enforce the no-repeat rule within a rig. Fitting a module to one rig never affects another rig.
3. Quest-mote fixes: the unearth claim copy and the spawner skips.
4. Epic quest hooks.
5. `FishReward.ship`.
6. Update the checks that fit single modules: `AbilityTooltipCheck`, `HarpoonAbilityChecks`, `HarpoonAimCheck`, `PondProximityChecks`, `LegendaryTrailChecks`, `NeedleSensorChecks`, `DevShortcutChecks`.

**Phase 1: T1.**
- The compass `special_items.csv` row (`BaseSpecialItemPlugin`).
- The golden chest and its pity counter.
- The quest-owned rumour.
- The lost-compass listener: a `ColonyInteractionListener`, registered as `RatingBarEvent.VisitCounter` does, removing the old copy first.
- The reward specimens, built from `ShopPricing.getPrice(Tackle.GLITTER_LENS)` with a new helper that creates a `FishCatch` satisfying a `FishRequirement` (none exists). If no payment can be generated, only credits are given.
- The Glitter Lens follow-up, checked when the Outfitter closes.
- The T1 offer after the last hand-in, and its route for skipped tutorials.

**Phase 2: ship slots.**
- Skins and descriptions.
- `FishingShips` with its listeners and the documented fallback check.
- The Outfitter's second slot, greyed rows and hovers.
- Retrieval Head and Fanned Array set to `stocked = false`.
- A check for the reconcile rules and the incompatibility matrix.

**Phase 3: T2.**
- The captain's voice entry in `LORE.md` first.
- The hunt mission and fleet.
- The lamp timer in `SearchlightAbilityPlugin.advance`, which already ticks every frame (the Needle Sensor runs there).
- A `Searchlight` accessor for a point ahead of the sweep; `arc`, `baseArcAngle` and `travelDirection` are private today.
- The outcomes, the harpoon ship grant, the Fisherman debrief and the Needle Sensor intel.

**Phase 4: T3.**
- The circle route as a saved per-fish swim rule next to the `holdsStation()` branch in `FishEntityPlugin`.
- The awake 0.5 multiplier in `EpicHunt.getSpeedMult`.
- Respawn on reset and on arrival in the system.
- The blueprint.
- The build follow-up through `FishingShips.reconcile()`.

**Phase 5: T4.**
- Reployment Bays at the swarm's "every drone returned" branch in `FishingDroneSwarmScript.advance`, after re-checking `shouldRecall()`; never after a manual recall. It needs a `ShopPricing.getCredits` case: the switch is exhaustive, but the module is never sold.
- The rumour hunt intel, told names recorded in `FishRumors.publish`, and the T4 completion hook.
- The ROD ship grant.

**Behaviour to keep in mind.** With the Retrieval Head and the Explosive Head both fitted, a blast refunds the harpoon charge; the head itself is still consumed. The Tracking Gimbal locks onto quest motes like any buried pattern.
