# Fisherman follow-up chain

**Status: approved design, implemented in Stages 0–5.** The player-facing text of Stages 1–5 still awaits the Editor pass (subtasks 1.9, 3.10, 4.7, 5.7), and one open question remains. This document records the author's decisions for the quest chain that follows the tutorial, plus the open questions and the work each step needed. Read it before revising any of these quests.

It does not govern lore, prose or rules structure. [LORE.md](LORE.md) remains the authority on facts, knowledge limits and voice. [DIALOGUE.md](DIALOGUE.md) owns the text workflow, and [RULES_WRITING.md](RULES_WRITING.md) owns conversation structure. Text described here is a brief, not approved dialogue. The deferred [Needle quest concept](NEEDLE_QUEST_CONCEPT.md) follows after this chain; see [Ships](#ships).

## Framing

- The tutorial still ends where it does today. `FishingIntro.isComplete()` keeps its current meaning, and everything gated on it (legendary spawns and the shop) is unchanged.
- The Fisherman offers T1 straight after the tutorial's last hand-in. After a skipped tutorial, which has no last hand-in, he offers it on the next visit. T1 replaces the free rumour the tutorial gives today on both paths. The chain is not presented as more tutorial: the tutorial should feel short, and this reads as ordinary work.
- Each step teaches one thing:
  - T1: lamp catches pay better than pond catches.
  - T2: fleets can have work for you, and epics are hunted.
  - T3: equipment is how you win hunts.
  - T4: there is more out there, and rumours lead to it.

## Shared mechanic: two-stage epics

Implemented in `EpicHunt` (see [ARCHITECTURE.md](ARCHITECTURE.md)).

- Every epic is a Breach Light catch, never a rupture catch, and only the harpoon lands one: ROD drones never take an epic.
- It drifts slowly until a harpoon hit wakes it. That hit is deflected and raises a single purple shield charge that never regenerates.
- The awake epic swims at full speed and more erratically. A failed catch loses it, like any other fish.
- More than 3,000 units from the player resets it to drifting. It drifts a short way and fades, like any fish at the end of its swim. The shield charge is never restored: once spent, every later hit lands.
- Awake epics are removed when the player leaves the system.

T2 and T3 build on this; T2's failure condition is the reset.

## Shared mechanic: ship-bound equipment

Implemented in Stage 2; see [ARCHITECTURE.md](ARCHITECTURE.md) (`FishingShips`, the Outfitter). Each fishing ship belongs to one rig and carries one module for it (see [Ships](#ships)). The harpoon ship goes with the Harpoon (Harpoon Tips), the Breach Light ship with the Breach Lights (Lens Arrays) and the drone carrier with the ROD (Drone Cores).

- **Second slot.** While a ship of that kind is in the fleet, its rig gets a second equipment slot, and the ship's module becomes available.
  - This happens once per rig. Two harpoon ships still give one extra harpoon slot.
- **Fitting.** The second slot works like the first. The player can swap the ship's module out for any other module they own.
  - While the ship is in the fleet, its module can be fitted in either slot.
- **Ship leaves the fleet.** When the last ship of that kind leaves, the second slot is emptied at once. The ship's module is unfitted wherever it sits, because it is only available with the ship. A copy bought before the module moved onto the ship is the player's own and stays.
- **Ship rejoins.** The second slot resets to the ship's module. It does not remember what was fitted before.
- **Two modules on one rig.**
  - Effects apply in slot order: the first slot, then the second.
  - No module takes up two slots.
  - A module cannot fill both slots of one rig. In the Outfitter it is greyed out in the rig's other slot, and its hover says it is already fitted there.
  - Rigs do not block each other. The three modules that fit both the ROD and the Harpoon (Calibrated Grader, Barbed Head, Sonar Head) can still be fitted to both rigs at once, as today.
  - Modules with similar or opposing effects cannot share a rig. Two modules that change the same value count as similar. The pairs are listed under [Incompatible pairs](#incompatible-pairs).
  - In the Outfitter, a module that conflicts with the one in the rig's other slot is greyed out for that slot and cannot be fitted there. Its hover names the conflicting module.
- **Supply.** A module that moves onto a ship leaves the shop and the reward pools; the ship is the only way to get it. This applies to the Retrieval Head and the Fanned Array. The drone carrier's module, the Reployment Bays, is new.

**Work**
- Fitted state is a two-slot `RigLoadout` per rig, and every effect check reads the combined `TackleManager.effects(rig)` (Stage 0). Ship slots only need to fill and empty the second slot (`RigLoadout` index 1).
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

Because no ship module is in a pair, the reset when a ship rejoins never meets a conflicting first slot. The disabled Breach Coupler is not assessed; check it if it returns.

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

- While the quest is active, a `ColonyInteractionListener` (`reportPlayerMarketTransaction`) watches for the compass leaving the player's cargo. It records the market where it was last sold; a free-transfer submarket counts as storage, not a sale. The quest removes the listener when it concludes.
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
- The rumour can reuse the `FishRumors` `LOOT` kind pinned to the target system. It replaces the graduation lead: `FishingIntro.finish` and `FishingIntro.skip` stop calling `FishRumors.ensureTutorialLead()`, and the graduation option "Heard of anywhere worth fishing?" (`catchrelease_introGetFreeRumor`) leads into T1 instead of a random rumour. Extending it by 60 days after the purchase needs a quest-owned duration, so the normal rumour rotation does not replace it.
- New: the golden chest treasure, its pity counter and the compass item row. The Glitter Lens already exists.
- The reward specimens must be generated from the cached payment at grant time, so they always match.

## T2: The captain's fleet

**Premise.** "A former worker of mine, on another fleet now, asked me to help out with something." This introduces fleets that offer work.

**The fleet**
- Spawned in a nearby system with **zero markets and no colonies**, preferably adjacent. The system must have a star; nebulae are never chosen.
- It is following a rumour of an epic seen in that system. The target is an **existing epic species** whose range includes that system. It is not Lantern Jack or any other legendary.
- Their Breach Lights have malfunctioned, so they cannot find it.

**The captain**
- An old, grizzled independent skipper, portrait `graphics/portraits/portrait30.png` (vanilla; listed in `independent.faction`).
- Speaks with Scottish-style speech patterns: accent, dialect and slang. That is only how he talks. Scotland, Earth and their places do not exist in the setting, which stays fully vanilla ([LORE.md](LORE.md#canon-sources)). He never names them, and nothing explains his speech by an origin outside the vanilla Sector. He needs a voice entry in `LORE.md` before any lines are drafted.
- Tells a personal encounter with the epic that fits any species, for example that it took his leg. The tale must not establish species-specific mechanics.

**Steps**
1. The captain asks the player to circle within sight of the system's star with lamps on.
2. After 15 continuous seconds with Breach Lights on within 6,000 units of the star's surface, the epic's buried mote is placed in the path of the player's lamp beam. Leaving that range or turning the lamps off restarts the count.
3. The player is told to harpoon it if found, and the two-stage epic hunt begins.
4. The hunt fails if the epic resets (more than 3,000 units from the player), a catch attempt fails, or an Explosive Head blast destroys it once the shield is down. A notification sends the player back to the captain.

**Outcomes**
- **Caught (unexpected).** The captain is overjoyed; he has hunted it for years and can retire now.
  - Extra rewards: one harpoon module schematic, or any module schematic if all harpoon ones are unlocked, plus three range-data entries. Ship modules are never offered.
- **Not caught (expected).** The captain talks about his years-long chase and says it got away again, but he'll get it next time. He is grateful for the data the player gathered.
- **Both outcomes:** the harpoon fishing ship. It brings a second Harpoon slot and the Retrieval Head (see [ship-bound equipment](#shared-mechanic-ship-bound-equipment)).

**Return to the Fisherman.** He thanks the player and explains how epics and legendaries behave when hunted. Optional questions add some background lore, which the mod is currently light on. His answers stay within his knowledge limits and his habit of not over-explaining (`LORE.md`). He gives the player the **Needle Sensor** schematic and tells them to build it.

**Follow-up: build the Needle Sensor.** A tracked intel quest. Its first step is to buy the Needle Sensor at the Outfitter with the schematic. Once the player owns it, the intel tells them to go back and talk to the Fisherman, and T3 starts in that conversation. If the player already owns one, the intel starts at the return step.

**Work.** Implemented in Stage 3 (`CaptainsFleet`, `SensorBuild`); see [ARCHITECTURE.md](ARCHITECTURE.md). The Needle Sensor's return step hands over to T3 (Stage 4).

## T3: The wounded epic

**Premise.** The Fisherman points the player at a nearby system with epics. One has been wounded and is recovering, circling a planet at about 1,000 units.

**Steps**
1. The player uses the Needle Sensor built after T2 to locate it.
2. Asleep, it behaves like any epic: buried, drifting at the normal buried speed, and visible under the Breach Lights. The difference is that its drift circles the planet at about 1,000 units.
3. A harpoon hit wakes it as usual: the hit is deflected, the fish surfaces and its shield charge goes up. It stops circling and runs the normal epic hunt, at half the speed of a normal awake epic.
4. If the hunt resets (more than 3,000 units from the player), it despawns and respawns at the planet, buried and circling again, with a campaign message along the lines of: "The pattern returns to the planet to recuperate once more."
5. The player is expected to hunt it until caught, then return to the Fisherman.

**Rewards**
- The blueprint for the second fishing ship, the Breach Light ship. It brings a second Lens Array slot and the Fanned Array.
- Range data on an epic.

**Follow-up: have the ship built.** The player works out how; the Fisherman does not build it. Completes when the player owns a ship of that hull.

**Closing conversation**
- Gist: "Rumours about the stranger fish are worth following once your gear is very good." He does not name legendaries, so T4 stays vague.
- Epics are not unique, but they are rare enough that they might as well be.

**Work.** Implemented in Stage 4 (`WoundedEpic`, `BuriedMoteEntityPlugin` circling, `EpicHunt.AWAKE_SPEED_KEY`); see [ARCHITECTURE.md](ARCHITECTURE.md). Any hunt that does not land the epic returns it to the planet, so T3 cannot fail.

## T4: Listen for rumours

**Decided**
- The Fisherman tells the player to improve their gear and listen for rumours. He stays vague about *what* they are hunting.
- The intel's title is along the lines of "Listen for rumours". It is a permanent reminder that there is more to find. Neither it nor the Fisherman mentions a reward or names legendaries.
- It is a long-term quest: no deadline. It completes on the first legendary catch, whenever that happens.
- It starts in T3's closing conversation, which already gives the Fisherman's advice; there is no separate offer.
- There is no gear checkpoint. Player skill is part of how hard a legendary is.
- The ROD ship (drone carrier) is still the reward on completion. Nothing announces it beforehand. The Fisherman hands it over at the next visit after the catch, not at the catch itself.

**After the first legendary: hunting the legendary fish**
- When T4 completes, the player learns there is more than one legendary. The intel changes to "Hunting the legendary fish".
- It lists one entry per legendary species, each shown as question marks until known, so the list shows how many there are.
- Each rumour the player hears about a legendary turns its question marks into the last known name: the name the teller used. A bar teller can give a folk name or the wrong one, so an entry can stay wrong until the catch.
- Every rumour fills in a name. A telling that names nothing gives either a wrong name or one taken from its story, for example "World Serpent" for an eel that lies curled with its tail in its mouth.
- Told names: the Fisherman's leads use the real names. The bar spacer's tellings use "Lantern Jack", "The Moray" and "The Imposter"; the three that name nothing go by "The Many" (the Quorum), "The Burning Lie" (False Dawn) and "The Abyss" (the Abyssal Ghost Manta). The tellings' dialogue still says no name was given.
- Names are recorded from the first rumour heard, before the list appears, at the single path every rumour is published through.
- Catching a legendary changes its entry to the species' real name.
- The existing legendary rumour intel shows the told name too, not the species' real name (`FishRumors.getStrangerDisplayName` today).
- The intel concludes once every legendary has been caught (six today). What happens then is decided later.

**Work.** Implemented in Stage 5 (`LegendaryHunt`, `FishRumors.Saved.toldName`, Reployment Bays); see [ARCHITECTURE.md](ARCHITECTURE.md).

## Ships

| Ship | Rig | Given by | Ship module ([second slot](#shared-mechanic-ship-bound-equipment)) |
|---|---|---|---|
| Harpoon fishing ship | Harpoon | T2, either outcome | Retrieval Head: a hit restores the spent harpoon charge |
| Breach Light ship | Breach Lights | T3 blueprint; the player builds it | Fanned Array: each Breach Light becomes a wide fan |
| ROD ship (drone carrier) | ROD drones | T4, first legendary | Reployment Bays (new Drone Core module): each time every drone has returned, the drones deploy again, until the player recalls them or they can no longer fish there (both the pond swarm and the roaming swarm) |

None of the final hulls exist yet. Until they do, each ship is a placeholder hull: a full copy of the vanilla Nebula (`nebula`, a civilian transport) under its own hull id and name, so the fleet check can tell which rig gets the second slot. The placeholders are `catchrelease_harpoon_ship` (Harpoon Nebula), `catchrelease_lamp_ship` (Breach Light Nebula) and `catchrelease_rod_ship` (ROD Nebula); see [ARCHITECTURE.md](ARCHITECTURE.md) for their data. Each real hull replaces its placeholder under the same id. With all three ships allocated, the [Needle quest concept](NEEDLE_QUEST_CONCEPT.md) can be revisited.

## Open questions

1. **What concluding "Hunting the legendary fish" brings.** It ends once every legendary is caught; the payoff is decided later.

## Implementation plan

Six stages, done in order (see [Stages](#stages)). Stage 0 changes no visible behaviour; it removes the obstacles the research found. Write all player-facing text through [DIALOGUE.md](DIALOGUE.md) and [LORE.md](LORE.md). Route UI scoping to the UI subagent ([CLAUDE.md](../CLAUDE.md#claude-model-assignments)).

### Architecture

| Area | Owner (new or changed) | Role |
|---|---|---|
| Chain state | New `campaign/fish/fisherman/chain/FishermanChain` | Saved stage per quest (T1–T4 and follow-ups) in sector persistent data, as `FishermanQuest` stores its `STATE_KEY`. Entry points are `CatchReleaseCMD` verbs. A `tokens` Boolean per offer gates the `CatchReleaseFisherOptions` rows. Registers its transient listeners on load. |
| Chain intel | New `BaseIntelPlugin` subclasses in the same package | One intel per quest: lost compass, Needle Sensor build, wounded epic, and the rumour hunt (title and body switch after the first legendary, as `FishingIntro.IntroIntel` switches by stage). Created through `FishIntelNotifications.queue`. |
| T2 mission | `chain/CaptainsFleet` | Implemented in Stage 3; see [ARCHITECTURE.md](ARCHITECTURE.md). A chain-owned class with its own intel, like T1, not a `BaseHubMission`: the chain already saves the stage, and the fleet's commander is the contact. |
| Rig slots | `TackleManager`, `RigLoadout`, `TackleEffects` | Implemented in Stage 0; see [ARCHITECTURE.md](ARCHITECTURE.md). |
| Incompatibility | `Tackle.Exclusion` | Implemented in Stage 0; see [ARCHITECTURE.md](ARCHITECTURE.md). |
| Ships | `FishingShips` | Implemented in Stage 2; see [ARCHITECTURE.md](ARCHITECTURE.md). |
| Fleet presence | `FishingShips.Listener`, `FishingShips.Watcher` | Implemented in Stage 2; see [ARCHITECTURE.md](ARCHITECTURE.md). |
| Hull ids | Placeholder hulls `catchrelease_harpoon_ship`, `catchrelease_lamp_ship`, `catchrelease_rod_ship` | Added; see [Ships](#ships). A D-modded ship becomes `<id>_default_D`, so match `spec.isDefaultDHull() ? spec.getDParentHullId() : spec.getHullId()`. |
| Outfitter | `FishShopDialog`, `ShopSlotPlugin`, `ShopRowPlugin`, `ShopEntry`, `ShopDetailHeaderPlugin` | Implemented in Stage 2; see [ARCHITECTURE.md](ARCHITECTURE.md) and [UI.md](UI.md#outfitter-second-slot). |
| Quest motes | `BuriedMoteEntityPlugin`, `BuriedMoteSpawner` | Implemented in Stage 0; see [ARCHITECTURE.md](ARCHITECTURE.md). |
| Epic hooks | `QuestEpicListener`, `EpicHunt` | Implemented in Stage 0; see [ARCHITECTURE.md](ARCHITECTURE.md). |
| Ship rewards | `FishReward.Ship` | Implemented in Stage 0; see [ARCHITECTURE.md](ARCHITECTURE.md). |
| Golden chest | `FishingMinigameDialogPlugin`, `FishingMinigame`, `TreasureRoller` | `init` decides "chest due" from chain state, the system and `CatchImplement.BREACH_LAMP`, as `FishingIntro.applyCatchTargetProtection` does. The chest is a flagged `MinigameTreasure`, not a new `TreasureRarity`, so `rollRarity` never draws it. It is granted on a win and counted in `resolve()`. |
| Rumours | `FishRumors` | T1's quest-owned `LOOT` rumour takes the graduation lead's place, including starting the shared 30-day cooldown as that lead does. The chain extends its `duration`. `publish` is the single choke point where T4 records told names. |
| Legendary list | `LegendaryChases`, `FishSpecLoader` | Rows from `getAllFishSpecs()` with `rarity == LEGENDARY` and `spawnWeight > 0`. Caught state from `isCaught`; completion from `noteCaught`. |

### Stages

Each stage is one pull request and depends on the stages before it. Within a stage, do the subtasks in order, one commit each. Tick a subtask when it is merged. Every stage finishes with the compile gate, the checks named in it, its Changelog entries, and its `ARCHITECTURE.md` routes. A finished stage's technical detail then moves from this document to `ARCHITECTURE.md`.

**Stage 0: groundwork.** No visible change.
- [x] 0.1 Two-slot loadout: `RigLoadout` and `TackleEffects`, with `TackleManager` storing two slots per rig and the second slot always empty.
- [x] 0.2 Move every single-module read to `TackleEffects`: the minigame constructors, `FishingMinigameDialogPlugin`, `NeedleSensor`, `NeutrinoLens`, `Searchlight`, `HarpoonEntityPlugin`, the ability tooltips, `PondFishSpawner` and the shop. Done when behaviour is unchanged.
- [x] 0.3 Fit rules in `TackleManager.fit`: no repeat within a rig, and the exclusion groups declared on `Tackle` for the [incompatible pairs](#incompatible-pairs). They only take effect once the second slot exists.
- [x] 0.4 Quest motes: `unearth()` copies the quest claim and any per-fish quest fields to the surfaced fish. `BuriedMoteSpawner` neither counts nor culls quest motes.
- [x] 0.5 Epic quest hooks: a listener for a quest-owned epic's reset, failed catch, blast and catch.
- [x] 0.6 `FishReward.ship(hullId)`, which adds a ship to the player fleet.
- [x] 0.7 Move the checks that fit single modules to the new API: `AbilityTooltipCheck`, `HarpoonAimCheck`, `PondProximityChecks`, `LegendaryTrailChecks`, `NeedleSensorChecks`, `DevShortcutChecks`.

**Stage 1: T1, the treasure rumour.**
- [x] 1.1 `FishermanChain`: saved chain state, its `CatchReleaseCMD` verbs, and a `tokens` Boolean that gates each offer row. Every later quest builds on it.
- [x] 1.2 The compass `special_items.csv` row (`BaseSpecialItemPlugin`).
- [x] 1.3 The quest-owned `LOOT` rumour in a nearby system. It replaces the graduation lead: `FishingIntro.finish` and `skip` stop calling `ensureTutorialLead()`. It starts the shared cooldown as that lead did.
- [x] 1.4 The offer: "Heard of anywhere worth fishing?" after the last hand-in leads into T1, and after a skipped tutorial the Fisherman offers it on the next visit. Then the lost-compass intel.
- [x] 1.5 The golden chest: a flagged `MinigameTreasure` whose "chest due" is decided in `FishingMinigameDialogPlugin.init`, with the pity counter updated in `resolve()` and the compass granted on a win.
- [x] 1.6 The lost-compass listener (`ColonyInteractionListener`, registered as `RatingBarEvent.VisitCounter` does) and the "I lost it" branches, including the storage search.
- [x] 1.7 Hand-in and rewards: the Glitter Lens schematic, credits, and the reward specimens from a new helper that builds a `FishCatch` satisfying a `FishRequirement`. If no payment can be generated, only credits are given.
- [x] 1.8 The follow-up: buy the Glitter Lens, checked when the Outfitter closes; then the rumour is extended by 60 days.
- [ ] 1.9 Text through the Editor process, then the dialogue and rules checks.

**Stage 2: ship slots.**
- [x] 2.1 Three placeholder hulls, each a full copy of the vanilla `nebula` under its own hull id and name.
- [x] 2.2 `FishingShips`: the hull → rig → module registry and `reconcile()`, covering once per rig, emptying on leave and resetting on rejoin.
- [x] 2.3 Fleet presence: market, battle and recovery listeners, `onGameLoad`, direct calls from quest grants, and the documented fallback check in the rig abilities' `advance`.
- [x] 2.4 Ship modules: Retrieval Head and Fanned Array set to `stocked = false`. Availability comes from ship presence, never ownership, and has no price.
- [x] 2.5 Outfitter: second slot per rig, a disabled row state, hovers naming the conflict or where a module is fitted, undo per slot, and the "one slot" text. UI scoping goes to the UI subagent.
- [x] 2.6 A check covering the reconcile rules and the incompatibility matrix.

**Stage 3: T2, the captain's fleet.**
- [x] 3.1 The captain's voice entry in `LORE.md`.
- [x] 3.2 System and species selection: zero markets and colonies, a star, nearby, and an epic species in range.
- [x] 3.3 The T2 hub mission: spawn and hold the fleet, the captain with his portrait, the map marker and the comms rows.
- [x] 3.4 The lamp timer in `SearchlightAbilityPlugin.advance`: 15 continuous seconds within 6,000 units of the star's surface.
- [x] 3.5 A `Searchlight` accessor for a point ahead of the sweep, then placing the quest's buried epic there.
- [x] 3.6 The hunt through the Stage 0 hooks: a catch, or a failure on reset, failed catch or blast with a notification back to the captain.
- [x] 3.7 Outcomes: the harpoon ship through `FishReward.ship` for both, plus the extra rewards on a catch.
- [x] 3.8 The Fisherman debrief, its lore questions and the Needle Sensor schematic.
- [x] 3.9 The Needle Sensor intel: buy it (checked when the Outfitter closes), then return to the Fisherman.
- [ ] 3.10 Text through the Editor process, then the dialogue and rules checks.

**Stage 4: T3, the wounded epic.**
- [x] 4.1 The circling heading rule on the quest's buried mote (`BuriedMoteEntityPlugin.advanceFish`).
- [x] 4.2 The awake 0.5 multiplier in `EpicHunt.getSpeedMult`, carried to the surfaced fish by `unearth()`.
- [x] 4.3 Spawning at the planet, plus respawn on reset and on arrival in the system, with the campaign message.
- [x] 4.4 The T3 offer in the Needle Sensor return conversation, and its intel.
- [x] 4.5 Hand-in: the Breach Light ship blueprint and range data on an epic.
- [x] 4.6 The build follow-up, completed through `FishingShips.reconcile()`.
- [ ] 4.7 The closing conversation; text through the Editor process, then the dialogue and rules checks. The conversation and checks are done; the Editor pass is pending.

**Stage 5: T4 and the legendary hunt.**
- [x] 5.1 Reployment Bays:
  - an unstocked `Tackle` constant;
  - a `ShopPricing.getCredits` case, because the switch is exhaustive;
  - redeploying at the swarm's "every drone returned" branch in `FishingDroneSwarmScript.advance`, after re-checking `shouldRecall()`, and never after a manual recall.
- [x] 5.2 Told names: one per rumour, including the story-drawn names, recorded in `FishRumors.publish`.
- [x] 5.3 The existing legendary rumour intel shows the told name.
- [x] 5.4 The "Listen for rumours" intel, completed by `LegendaryChases.noteCaught` or by an earlier catch.
- [x] 5.5 The ROD ship grant.
- [x] 5.6 "Hunting the legendary fish": the question-mark list, told and real names, and concluding once every legendary is caught. The payoff is still open.
- [ ] 5.7 Text through the Editor process, then the dialogue and rules checks. The checks pass; the Editor pass is pending.

**Behaviour to keep in mind.** With the Retrieval Head and the Explosive Head both fitted, a blast does not refund the harpoon charge, and the head is consumed. The Retrieval Head's description says so. The Tracking Gimbal locks onto quest motes like any buried pattern.
