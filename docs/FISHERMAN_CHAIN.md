# Fisherman follow-up chain

**Status: approved design, not yet implemented.** This document records the author's decisions for the quest chain that follows the tutorial, plus the open questions and the work each step needs. Read it before implementing or revising any of these quests.

It does not govern lore, prose or rules structure. [LORE.md](LORE.md) remains the authority on facts, knowledge limits and voice. [DIALOGUE.md](DIALOGUE.md) owns the text workflow, and [RULES_WRITING.md](RULES_WRITING.md) owns conversation structure. Text described here is a brief, not approved dialogue. The deferred [Needle quest concept](NEEDLE_QUEST_CONCEPT.md) follows after this chain; see [Ships](#ships).

## Framing

- The tutorial still ends where it does today. `FishingIntro.isComplete()` keeps its current meaning, and everything gated on it (legendary spawns and the shop) is unchanged.
- The Fisherman offers T1 straight after the tutorial's last hand-in. The chain is not presented as more tutorial: the tutorial should feel short, and this reads as ordinary work.
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
  - No module takes up two slots, and one module cannot fill both slots of a rig.
  - A rig's modules never block another rig's slots. A module that fits both the ROD and the Harpoon can still be fitted to both, as today.
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
| Harpoon | Explosive Head + Barbed Head, Sonar Head, Calibrated Grader or Shoring Head | The blast destroys the catch those tips improve. |
| Lens Array | Needle Sensor + Neutrino Lens | Both point out hidden rare, epic and legendary patterns. |

Every other pair is compatible. Two of them were checked specifically:
- **Retrieval Head + Explosive Head:** the Retrieval Head returns harpoon charges and does nothing to the catch.
- **Fanned Array + Tracking Gimbal:** one sets the beam's shape and sweep speed, the other pauses the sweep on a pattern. `Searchlight.acquire` already picks the best-lit pattern under a fan.

Because no ship module is in a pair, the reset when a ship rejoins never meets a conflicting slot 1. The disabled Breach Coupler is not assessed; check it if it returns.

## T1: The treasure rumour

**Premise.** Word goes round that retrievals in a nearby system are bringing up far more bycatch than usual. The Fisherman knows of one particular find there and wants it.

**Steps**
1. The Fisherman names the system and asks for one specific treasure: a golden chest.
2. The player fishes there under Breach Lights. Each **lamp catch** may produce the golden chest in the minigame. Pond catches never do.
3. The chest holds a compass. The player brings it to the Fisherman, who takes it.
4. Rewards are granted (below), and the follow-up quest starts immediately.

**Golden chest**
- A quest-only minigame treasure, separate from the Salvage, Wreckage, Cache and Relic tiers.
- Chance: 30% on each lamp-method catch in the target system while the quest is active.
- Frustration gate: guaranteed on the 4th lamp catch, then on every 2nd catch after that until the player pulls it.
- The player still has to take it in the minigame like any treasure. A missed chest counts as not pulled.

**Compass**
- Special item using the icon `graphics/catchrelease/icon/compass.png`, base value 1 credit, cosmetic.
- Plugin: vanilla's plain `com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin`, as used by its Topographic Data and Planetkiller rows. There is no `DefaultSpecialItemPlugin` class.
- It must never be cleared from markets. Nothing extra is needed: vanilla restocks never remove special items, and `SoldFishRestock` only removes catch items.
- The Fisherman takes it at hand-in, possibly as a keepsake of his own.

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
- Failure: watch the quest fish's `EpicHunt` reset.
- Needle Sensor follow-up: grant the schematic through `FishReward.tackleSchematic`. Advance the intel to its return step from the purchase (`ShopEntry.grant`) rather than by polling ownership.

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
- Bar rumours about legendary fish are generally worth following, once the player has the gear for it, and the gear level needed is very high.
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
- Catching a legendary changes its entry to the species' real name.
- The conclusion of this intel is not decided.

**Work**
- Count the entries from the legendary rows in `fish.csv` (six today), not a constant.
- Record each species' told name whenever a legendary rumour is heard, including before the list appears. There are two sources:
  - the Fisherman's monthly lead (`FishRumors`);
  - the bar spacer's report (`RatingBarEvent`, `CatchReleaseRatingLegendaryReport` rows).
- Today's spacer tellings name some legendaries ("Lantern Jack", "The Moray", "The Imposter") and give no name for the Quorum, False Dawn and Ghost Manta. None gives a deliberately wrong name. Each rumour therefore needs an explicit told name.
- Catches: `LegendaryChases.isCaught`. A legendary caught before T4 starts completes it at once.

## Ships

| Ship | Rig | Given by | Ship module ([second slot](#shared-mechanic-ship-bound-equipment)) |
|---|---|---|---|
| Harpoon fishing ship | Harpoon | T2, either outcome | Retrieval Head: a hit restores the spent harpoon charge |
| Breach Light ship | Breach Lights | T3 blueprint; the player builds it | Fanned Array: each Breach Light becomes a wide fan |
| ROD ship (drone carrier) | ROD drones | T4, first legendary | Reployment Bays (new Drone Core module): once every drone has returned, the drones deploy again to the same area |

None of the hulls exist yet. Until they do, each ship is a placeholder built on the vanilla Nebula hull (`nebula`, a civilian transport). Give each placeholder its own hull id, for example a `data/hulls/skins/*.skin` of `nebula` with its own `skinHullId` and name. Otherwise three Nebulas cannot be told apart, and the fleet check would not know which rig gets the second slot. With all three ships allocated, the [Needle quest concept](NEEDLE_QUEST_CONCEPT.md) can be revisited.

## Open questions

1. **Unnamed rumours.** What a legendary's entry shows after a rumour that gives no name.
2. **The conclusion of "Hunting the legendary fish".**

## Suggested order

1. Two-stage epics (done).
2. Compass item and golden chest, then T1 and its follow-up. The Glitter Lens is done.
3. Second equipment slots in the Outfitter, the Nebula placeholder hulls, and moving the Retrieval Head and Fanned Array out of the shop.
4. T2 with the harpoon ship, then the Fisherman debrief, lore questions and the Needle Sensor follow-up.
5. T3 and the Breach Light ship blueprint.
6. The Reployment Bays, then T4, the ROD ship and the legendary-hunt intel.
