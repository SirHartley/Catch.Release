# Catch.Release — file and feature map

What is where, and which file to open first. 218 Java files across eight top-level packages, plus
the data tables that register them.

Kept by hand, and updated by every change — not only when a package gains or loses a file, but
whenever what a file does, what registers it, or how the pieces fit moves. The update belongs in
the same commit as the change. A map that is wrong is worse than no map, because it is believed.

Not mapped below, because none of it is ours: `lib/` holds the game's API source and the three
dependency mods, zipped, to be read rather than edited.

---

## Where a feature lives

| If you are changing… | Start in |
|---|---|
| The pond itself — look, open/close, mote spawning | `campaign/ponds/terrain/MaskedFishingPondTerrainPlugin.java` |
| Where ponds appear in a system | `campaign/ponds/listener/PondCreator.java` |
| The catch minigame's rules | `campaign/fish/minigame/FishingMinigame.java` (no rendering in it) |
| The catch minigame's look or input | `campaign/fish/minigame/FishingMinigamePanel.java` |
| What a fish *is* | `campaign/fish/data/FishSpec.java` + `data/campaign/fish.csv` |
| Where a fish lives | `campaign/fish/data/FishHabitat.java` — one question, one answer |
| A caught specimen's stats and grading | `campaign/fish/data/FishCatch.java` |
| Bar jobs | `campaign/fish/jobs/` — `FishJob.java` is the spine |
| Fleet-given jobs | `campaign/fish/jobs/fleet/` |
| The shop | `campaign/fish/shop/FishShopDialog.java` |
| Anything anybody **says** | `data/campaign/rules.csv` — all of it, without exception |
| What the sheet can *do* | `dialogue/rules/CatchReleaseCMD.java` |
| Upgrades | `memory/upgrades/` + `data/config/UpgradeData.csv` |
| Tackle modules | `campaign/fish/tackle/Tackle.java` |
| An ability's behaviour | `abilities/<name>/ability/` |
| An ability's tuning | `abilities/<name>/constants/` |
| Aiming and reticules | `skillshot/` (has its own README) |
| Shaders and GL helpers | `rendering/` + `data/catchrelease/shaders/` |
| The sector-map fish filter | `campaign/fish/map/` |
| The low-coherence screen overlay | `campaign/fish/coherence/CoherenceOverlayScript.java` |
| The fishing boats, standing and visiting | `campaign/fish/fisherman/` |
| How anybody starts fishing | `campaign/fish/tutorial/` |
| The colony structure and its aquarium | `campaign/fish/colony/` |
| Consequences of harpooning a fleet, or of running the lamps over one | `campaign/crime/` |
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
4. `CatchReleaseCampaignPlugin.register()` — hands harpooned fleets their custom encounter screen;
   the fishing boats' conversations are rules rows and need no plugin
5. `HarpoonPatrolResponse.register()` and `LampPatrolResponse.register()` — the crime responses: a
   patrol after an outstanding harpooning, and the stop over lit lamps
6. `FleetQuestSpawner.register()` — fleets out in the world that want fish
7. `FishermanSpawner.register()` — the daily roll for the visiting fishing boat
8. `CoreFisherSpawner.register()` — one standing boat to every inhabited system
9. `FishermanQuest.Keeper.register()` — keeps a chart request's specimen in the water
10. `TutorialWreck.Watcher.register()`, `Castaway.Watcher.register()`,
   `RatingBarEvent.VisitCounter.register()`, `FishermanInterception.register()`,
   `FishingIntro.Keeper.register()` — the introduction's hooks and its errand keeper
11. `ConservatoryOptionProvider.register()` — the conservatory's options on the colony screen
12. `AquariumTankScript.register()` — the aquarium on the colony's main menu
13. `UpgradeManager.getInstance().updateBaseValues()` — re-reads the upgrade sheet into the save
14. `SkillshotFramework.register()` — the aiming framework
15. `FishMapFilterScript` as a transient script — the sector-map filter
16. `FishIntelPlanetPanel` as a transient script — the intel Planets view's fish panel
17. `CoherenceOverlayScript` as a transient script — the low-coherence screen overlay
18. `DevShortcut.register()` — the Ü key, as a transient `CampaignInputListener`; inert unless dev mode is on

`beforeGameSave()` — `SkillshotFramework.reset()`.

Registration is idempotent: the `register()` methods unregister by id first, and transient scripts
are rebuilt every load because their state lives in sector memory rather than in fields.

---

## Registered by data, not by code

Classes the game instantiates by name. Grep the data file, not the call sites — there aren't any.

**`data/campaign/abilities.csv`** — 5 abilities. The four of ours are `unlockedAtStart=FALSE`:
they are granted by `FishingIntro`, not by character creation.

| Id | Class |
|---|---|
| `catchrelease_searchlights` | `abilities/searchlight/ability/SearchlightAbilityPlugin` |
| `catchrelease_rod` | `abilities/rod/ability/PondInteractionAbilityPlugin` |
| `catchrelease_harpoon` | `abilities/harpoon/ability/HarpoonAbilityPlugin` |
| `catchrelease_shop` | `campaign/fish/shop/FishShopAbilityPlugin` |
| `skillshot_example` | `skillshot/example/ExampleSkillshotAbility` |

**`data/config/settings.json`** — `ruleCommandPackages`, listing vanilla's five packages **plus**
`catchrelease.dialogue.rules`. The key is read once from merged settings and **replaces** rather than
merges, so vanilla's have to be re-listed; dropping any of them breaks every rule in the game.

**`data/campaign/bar_events.csv`** — 14 jobs, all `FishJob`s: 11 in `campaign/fish/jobs`, plus the
three camp events in `campaign/fish/jobs/camp`, whose shared base `CampedSpotJob` extends `FishJob`
like the rest. The two bar encounters that are *not* jobs — Crablobab and the rating — are
`AddBarEvents` rows in `rules.csv` instead, with no Java at all. **Three of the job ids do not match
their class name:**

| Id | Class | | Id | Class |
|---|---|---|---|---|
| `catchrelease_standingOrder` | `StandingOrderJob` | | `catchrelease_academy` | `AcademyJob` |
| `catchrelease_chef` | `ChefJob` | | `catchrelease_tuber` | `TuberJob` |
| `catchrelease_startup` | `StartupJob` | | `catchrelease_cult` | `CultJob` |
| `catchrelease_butler` | `ButlerJob` | | `catchrelease_campPirate` | `PirateCampJob` |
| `catchrelease_curator` | `CuratorJob` | | `catchrelease_campMerc` | `MercCampJob` |
| `catchrelease_duel` | **`KidsJob`** | | `catchrelease_campPath` | `PatherCampJob` |
| `catchrelease_ring` | **`MafiaJob`** | | | |
| `catchrelease_client` | **`CompanionJob`** | | | |

**`data/campaign/terrain.json`** — `catchrelease_StaticPond` → `MaskedFishingPondTerrainPlugin`,
`catchrelease_coherence_field` → `CoherenceTerrain`. Carries the plugin class only; name, radius,
layers and tags all come from the plugin — including the terrain id as a tag, which `BaseTerrain`
does **not** add for you and which anything looking a terrain up by tag depends on.

**`data/campaign/special_items.csv`** — `catchrelease_fish` → `FishItemPlugin`,
`catchrelease_fish_bundle` → `FishBundleItemPlugin`, `catchrelease_fish_pile` → `FishPileItemPlugin`.

**`data/campaign/industries.csv`** — `catchrelease_conservatory` → `BreachConservatory`,
the colony structure that opens the fishing trade and keeps the aquarium.

**`data/config/custom_entities.json`** — the motes, harpoon, drone, the fishing boats' map mark
(`catchrelease_FisherMapIcon` → `FishermanMapIcon`) and the introduction's two props
(`catchrelease_TutorialWreck` → `TutorialWreck`, `catchrelease_Castaway` → `Castaway`). The pond is
**not** here any more.

**`data/campaign/rules.csv`** — all dialogue. See the contract below. One row registers *behaviour*
rather than words and is easy to miss when hunting for it in Java: `catchrelease_fisherEncounter`,
on `BeginFleetEncounter`, does `unset $ignorePlayerCommRequests` then `OpenComms`, which is the whole
reason walking up to a fishing boat opens the conversation instead of the engage/disengage screen.
There is no plugin behind it — see the gotcha below.

**`data/config/sounds.json`** — 6 ids of our own, merged into vanilla's ~600. Ability sounds are
named in `abilities.csv` (`uiOn`/`uiOff`/`uiLoop`/`world*`), not in code.

---

## The rules.csv contract

**Every word anybody speaks is in the sheet — jobs, the Fisherman, the introduction, the props.**
Java is reached from a row in exactly one way, `CatchReleaseCMD <verb> [arg]`: in *conditions*,
`tokens` writes the booleans and strings the rows branch on and always returns true, so it never
changes whether a row matches; in *script*, a verb does the thing and returns whether it worked. The
machinery panels — shop, chart counter, cargo picker — stay Java, because a shelf of cards is not
something a sheet has anything to say about. Java owns only what a sheet cannot do — counting the hold,
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

### `campaign/fish`
One file sits at the root, because it is a fact about the setting rather than about any subsystem.

| File | What it does |
|---|---|
| `FishingTaboo.java` | Who will not touch the water at all — the Church and the Path — and the three questions asked about them: is this flag one of them, is this port one of theirs, do they run this system. Read by the bar jobs, the fleet-quest spawner, the core trawler spawner and the lamp response, so the two faction ids appear exactly once in the mod |

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
| `Aberration.java` | 0–1 aberration for a location — the inverse of coherence — from abyss depth, black hole, hypershunt and slipstream, strongest wins. Names the source via `dominantSourceAt` |
| `SectorRegion.java` | Nine-way sector location enum (8 quadrant bands + ABYSSAL) |
| `StarColour.java` | What a system's sun looks like, from its star's planet type |
| `FishHabitat.java` | Everything a place says about itself — sun, tags, region, constellation age, coherence — read once and cached |
| `CatchImplement.java` | What made a fish reachable — a pond or a breach lamp — read off the mote's own provenance |
| `FishLocationSummary.java` | Builds the "where this swims" sentence from every habitat criterion a spec sets |

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

### `campaign/fish/jobs/camp`
A fisher whose one good rupture has somebody parked on it. Three bar events, three ways through.

| File | What it does |
|---|---|
| `CampedSpotJob.java` | The shared job. Two conditions rather than one — clearing the camp is the work, the specimen is only the receipt. Asks `CampedSpot.isGone` and nothing more specific, so it never has an opinion about how the player did it |
| `CampType.java` | Who is out there: pirates (there for money, will take money), mercenaries (paid to be there, and say so), pathers (not selling anything, and the bribe does the least good). Mercenary rather than independent deliberately — see the note in the file |
| `CampSize.java` | Small, medium, large, and the words the fisher uses for each. The estimate is honest; it is the only warning the player gets |
| `CampedSpot.java` | Spawns the camper on the rupture and holds it there. Spawned rather than borrowed, because the job is about one specific pond and there is no fleet already parked on it |
| `PirateCampJob.java` · `MercCampJob.java` · `PatherCampJob.java` | One per bar event, so each fisher gets their own pitch |

### `campaign/fish/jobs/fleet`
Jobs hung on a hull that was already out there, which then has to still be there when you return.

| File | What it does |
|---|---|
| `FleetQuest.java` | A `FishJob` whose giver is a fleet. `offer()` hangs it and touches nothing else; `take()` supplants the hull with a copy, then `mark()` and `hold()` |
| `FleetQuestSpawner.java` | Picks a civilian hull already in the player's system and hangs an offer on it. Spawns nothing |
| `FleetQuestEncounter.java` | Runs one offer — reads the answer once the dialogue closes, re-hangs the mark after a load, times the offer out |
| `FleetQuestType.java` | Seven flavours of trouble, with pitch text, ask rolling and base worth. `fleetType` is a preference between candidates, not a recipe |

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
The fishing trade. **One man, many boats** — a standing trawler in every inhabited system working the
outer reaches off one shared shelf, and a visiting one that turns up in uninhabited water for a
fortnight with a shelf of its own. Every one of them answers with the same face, and none of them
explains how.

| File | What it does |
|---|---|
| `FishermanSpawner.java` | The daily roll for the visiting boat: where it may turn up, and what leans the odds |
| `CoreFisherSpawner.java` | One boat to every inhabited system, re-posted if it is lost |
| `CoreFisherBehavior.java` | The standing boat: the same rig and the same man, no visit clock, and the outer-reaches route |
| `OuterReaches.java` | Where a boat is willing to be, and which legs clear the inhabited worlds |
| `FishermanBehavior.java` | The stay: yellow fan lamps, staged motes, NPC harpoon throws, the leaving — and `keepStanding()`, which pins the boat non-hostile and un-fleeing every frame |
| — | Talking to the boat is not a file. The encounter goes straight to comms (`catchrelease_fisherEncounter`), and the survey counter, outfitter, buyer, rumours and chart requests are all rows under `$menuState == catchreleaseFisher` |
| `FishermanShelf.java` | What survey data is on sale and on which boat — two slots to start, the pool that stops duplicates, and the restock dated off each sale |
| `FishermanQuest.java` | Chart requests: one named specimen from one named place, kept in the water until it is landed |
| `FishermanSurveyDialog.java` | The chart counter: the shelf as silhouette cards, component-built in the sidebar's language |
| `FishermanMapIcon.java` | The boat's mark on the system map — drawn there and nowhere else, riding the fleet |
| `FishermanIdentity.java` | The one person, kept for the campaign — and how far gone he reads where the fabric is thin |
| `FishRumors.java` | One rumor a month — rarer rolls, richer treasure, or a stranger species |
| `FishermanConstants.java` | Every number the above read |

### `dialogue/rules`
The one rule command the mod ships, and the only place the sheet reaches into Java.

| File | What it does |
|---|---|
| `CatchReleaseCMD.java` | `CatchReleaseCMD <verb> [arg]` — writes the branch tokens, opens the panels, walks the ladder |
| `FishBuyer.java` | Selling the catch: the picker, the batch rungs, the arithmetic |

### `campaign/fish/tutorial`
Learning to fish, in six lessons and one shortcut. **Entirely detached from the ordinary loop** — the
trade runs whether or not any of it has happened. What it gates is *equipment*, and through that
everything downstream. Not a word of what it says is in Java.

| File | What it does |
|---|---|
| `FishingIntro.java` | The seven stages, the errand targets, the grants, the shortcut, the intel note |
| `TutorialWreck.java` | A holed cruiser beside the first rupture seen out where nobody lives |
| `Castaway.java` | The rating put off a boat for looking, found on a survey |
| `RatingBarEvent.java` | The port counter the sheet's bar version is gated on, and nothing else |
| `FishermanInterception.java` | The boat that is simply *there* when somebody nears a rupture unequipped |
| `TutorialConstants.java` | Every number the above read |

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
| `FishShopDialog.java` | The dialog: tabs, list, detail pane, buy - the store/retrieve counter is gone |
| `ShopEntry.java` | Wraps one shelf item — upgrade, tackle or curio — behind uniform price/state/buy |
| `ShopGroup.java` | The shelves, and which stat ids and rigs belong to which |
| `ShopPricing.java` | Per-campaign seeded prices in credits and fish |
| `ShopMarks.java` | The shopping list: marked upgrades feed the route planner and hang the quest-yellow dot on every fish that would pay for them. `isMarked` is the marks alone, which is what the map screens ask; `isWanted` counts the open jobs too, which is what a cargo icon asks, and is cached because it is asked per cell per frame |
| `FishCurrency.java` | Counts and spends fish as payment, worst specimens first |
| `FishRequirement.java` | An ask: count, rarity, grade, species, origin, coherence — and how to describe it |
| `ShopStorage.java` | Migration only — returns fish left in the removed store/retrieve counter. See Dead or dormant |
| `FishShopAbilityPlugin.java` | The ability-bar button that opens it. Temporary until it lives on a market |
| `ShopRowPlugin.java` | One clickable row, plus the shopping-list ring. Reports the ring's hover upwards rather than drawing its own card |
| `ShopTabPlugin.java` | One tab button |
| `ShopHeaderPlugin.java` | Title, credits and the per-rarity fish purse |
| `ShopDetailHeaderPlugin.java` | The detail pane's portrait, name and ladder readout |
| `ShopUi.java` | Shared drawing helpers: fonts, quads, clipping, card placement, and `drawPanel` - the sidebar dressing every panel wears |

### `campaign/fish/items`
Fish in cargo.

| File | What it does |
|---|---|
| `FishItems.java` | Ids and the encode/decode used by all three item kinds, plus `stow` — where a landed fish actually goes |
| `FishItemPlugin.java` | One landed specimen; right-click stows it into a bundle |
| `FishBundleItemPlugin.java` | A crate of one species; right-click unpacks, ctrl sweeps the hold into the pile |
| `FishPileItemPlugin.java` | Every fish aboard on one line; right-click breaks it back into one crate per species |
| `FishItemRenderer.java` | Icon plus rarity and grade pips over the cargo cell |

### `campaign/fish/crab`
`rules.csv` (`catchrelease_crabBarAdd` and the rows under it); only the wares are Java.
Crablobab's two wares. The stall itself is `AddBarEvents` rows in `rules.csv` — no Java; only the

| File | What it does |
|---|---|
| `CrabWares.java` | The two wares, what each costs in credits and crabs, where each one's ownership lives, and which of them has a switch |

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
| `FishPresenceOverlay.java` | Draws the blobs through a stencil, striped where they overlap; route badges and the close-route label |
| `FishSystemPane.java` | The system view's sidebar: the viewed system's catch as holder cells, same map hand-over as the big pane |
| `FishHolderPlugin.java` | One round fish holder - rarity ring, art/mark/question - shared by every screen that lines fish up in circles |
| `FishIcons.java` | A species' face by knowledge: the art once landed, its rimmed black silhouette while only surveyed |
| `FishRoute.java` | The saved route: ordered stops in the save, until closed by hand |
| `FishRoutePlanner.java` | Suggestions from open asks; cover + exact ordering, stability- and slipstream-aware |
| `FishRoutePopup.java` | The planner in the sidebar's slot, built from the sidebar's own parts: search, chips, pick up to five, plot |
| `PaneWidgets.java` | The panes' shared widgets - type chip, text button, ghost-text tending - one face for sidebar and planner |
| `FishTooltips.java` | The one species tooltip every fish icon answers a hover with |
| `FishIntelPlanetPanel.java` | The intel Planets view's fish panel, beside the planet card |
| `FishType.java` | Filter categories with colour and icon |
| `CoreUiCrawler.java` | Reflection into the obfuscated core UI to find the filter row |

### `campaign/fish/codex`
Codex pages for species.

| File | What it does |
|---|---|
| `FishCodex.java` | Installs the category and per-species entries; opens the codex on a species |
| `FishCodexEntry.java` | One page: description, catch data, record, location, art, and the jump to the sector map |

### `campaign/fish/coherence`
The low-coherence overlay: the screen warps purple at its edges while a rig runs, an open pond
is close, or one of the trade's boats is - whichever of the three pulls hardest, weighted by
distance for the last two.

| File | What it does |
|---|---|
| `CoherenceOverlayScript.java` | The rules: which of the three sources is loudest, how hard, the ease in and out, the whisper loop. Drawing is `rendering/plugins/CoherenceOverlayRenderer` |
| `CoherenceTerrain.java` | The terrain-bar line. Invisible terrain covering a whole location whose `containsEntity` is "is the overlay up" rather than a distance — IndEvo's trick, so nothing has to be moved under the fleet |

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
What harpooning a fleet costs, and what running the breach lamps over somebody's head costs.

| File | What it does |
|---|---|
| `LampOffence.java` | Where the lamps may be run and what a stop costs, plus the burn counter — a stop settles the burn it was about, so putting the lamps out and lighting them again is a fresh offence rather than a continuation of the settled one. System-bound like the transponder law — a flag polices systems it holds something in; inside those, everybody objects near an inhabited world and the Church and the Path object anywhere. Four rungs: warning, fine, inspection, guns, the last only reached by doing it again inside a month |
| `LampPatrolResponse.java` | Patrols coming over about the lit lamps. Nothing is booked and nothing is dispatched — the sweep asks only whether they are burning, whether this is somebody's space, and whether anybody is looking, so putting them out before the patrol arrives ends it |
| `HarpoonOffence.java` | Incident history, outstanding debts, evasions, rep loss, and the escalation ladders. Armed crews turn on you at the second hit; unarmed ones are split by strength — a crew that is plainly outmatched (`isOutmatched`, vanilla's own 1.25× engage threshold) and has somebody to tell (`isCivilised`) runs on the *first* hole with an emergency burn and fetches a patrol, and everyone else works ignore → run you down for the bill → run and tell. `isPlayerIdentified()` is the transponder, and is what decides whether anybody can name you |
| `HarpoonPatrolResponse.java` | Sends one patrol at a time after the player. Any faction **not hostile to the offended one** will take it — the infraction belongs to the space, not to a flag |
| `HarpoonWitness.java` | An unarmed crew flying to a patrol to report it. The report lands on arrival, so it can be outrun, jumped away from, or shot down |
| `HarpoonHitman.java` | Mercenaries, when there was nobody to report to. One at a time; guaranteed for a charge fired under a live transponder |
| `HarpoonedFleetFID.java` | Vanilla's encounter dialog plus one line, and a comm link highlighted only while the crew is actually owed something — `wasHarpooned` stays true for a month and colouring on it alone left a settled bill looking unsettled for weeks |
| `CatchReleaseCampaignPlugin.java` | Hands harpooned fleets that dialog at the narrowest priority - the one custom encounter screen left |

### `abilities`
Four rigs. Each is `ability/` (the plugin), `constants/` (tuning), and usually `entities/`.

| File | What it does |
|---|---|
| `FishingRigs.java` | One answer to "is any rig running" - lamps lit, swarm out, or a line in the water |
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
| `plugins/CoherenceOverlayRenderer.java` | Full-screen post-process: the screen redrawn warped and leaned purple, at a level set from outside. Both warp and tint sit under a radial mask that leaves the middle of the screen clear |
| `plugins/MaskGlowRenderer.java` | Additive glow shaped by a sprite's alpha |
| `plugins/NoiseMappedCircularRingRenderer.java` | Ring shaped and animated by scrolling noise |
| `plugins/WarpGrid.java` | The animated vertex grid the warp renderers share; borders pinned |
| `plugins/WarpedRectRenderer.java` | A sprite warped per-vertex by a grid, no shader |
| `renderers/FleetMarkerRenderer.java` | A small icon off a fleet's corner, in vanilla's own geometry and whoever's colour — the quest offer's cyan `!` |
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
| `helper/loading/SpriteLoader.java` | Sprites by id or path, cached, misses logged once. One object per path is shared by every caller, so it is handed back neutral - native size, white, full alpha, normal blend - and a caller that wants it otherwise says so |
| `helper/math/TrigHelper.java` | Circle intersection and fitting, smoothing, normal distribution |
| `helper/math/Circle.java` · `CircularArc.java` | Point/angle helpers and arc traversal |
| `helper/animation/BaseCircleTrajectoryFollowingParticle.java` | Position and facing along a circular arc between two points |
| `helper/animation/ArchedTrajectoryFollowingMote.java` | A glowing mote drawn along that arc |
| `reflection/ReflectionUtils.java` | Reflection via `MethodHandle`, to dodge the classloader ban |
| `testing/DevShortcut.java` | The Ü key: skips the introduction, grants every rig and the shop, issues charts of every rung. A `CampaignInputListener` on the post-core pass, so anything with a text field has already eaten the key; matched on the typed character rather than a scancode. Dev mode only, read per press |
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

**The shop has three kinds of thing on a shelf, and the third one is not for sale.** A `ShopEntry`
is an upgrade (a ladder), a tackle (a slot), or a **curio** (a switch). A curio was bought from
Crablobab; all the shop offers is the only thing left to do with it, which is turn it off. So its
price is null, `isDone()` is never true — a switch is never finished with — and the buy button reads
SWITCH ON/OFF. Both the main tab row and the shelves under it are derived from the entries that
exist, so the Extras tab appears the moment the first curio is bought and not before.

**The celebration is bought, not configured.** It had a LunaLib toggle; it now has no setting at all,
and `CrabWares.CELEBRATION` answers three separate questions — bought (his business, never undone),
owned (where it is kept), and switched on (the player's, through the shop). `FishingMinigamePanel`
asks `isOn()` and simply does not build a `CatchCelebration`; nothing inside that class asks again.
The mod ships no `LunaSettings.csv` any more, so it has no page in LunaLib's menu.

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

**Where a fish lives is one question, and everything asks it the same way.** `FishHabitat.of()`
reads a place once — sun colour, system tags, region, constellation age, how well reality is holding
— and `FishSpec.matches()` is the only thing that tests a species against it. They used to be
several: the spawner tested star type, tags and region; the map, the route planner and the intel
panel tested the region alone, so the map shaded systems under the wrong sun and said so beside a
spawner that would never have offered the fish there. `FishPresence.livesIn()` is what every screen
calls. Habitats are cached for the session because none of their inputs change during a game, and
because `Aberration` walks every slipstream in hyperspace each time it is asked.

**Blank means "anywhere" on every habitat criterion except the abyss.** `ABYSSAL` has to be named,
because a species that says nothing about where it lives is one somebody could describe, and nothing
describable lives down there — without the exception the deepest water in the game offered the same
roach as a core world. It is the one asymmetry in the table and it is deliberate.

**A species can be reachable by only one kind of gear.** `reachedBy` names `POND`, `BREACH_LAMP`, or
neither for both — the same `CatchImplement` a buyer asks about, so "only ever out of a rupture" and
"wanted pond-caught" are one vocabulary rather than two that happen to agree. It is also a second
way to write an unfillable ask: a named species that only comes up out of a rupture cannot be asked
for through a breach lamp, which is why `FishJobAsks.pickImplement()` reads the species first.

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

**Being harpooned escalates two different ways, and which one depends on what the crew is for.**
`HarpoonOffence.isCombatCrew()` reads vanilla's own `$isPatrol` / `$isWarFleet` / `$isPirate`. A crew
that fights for a living turns hostile on the second hit, which is the old rule. Everybody else gets
a ladder of their own: once is a complaint to your face and nothing more, twice brings them after you
for the repair bill — pursuit without hostility, which is a state vanilla supports and uses for its
own hasslers — and three times and they stop talking, set `AVOID_PLAYER_SLOWLY`, and put it on the
channel through `HarpoonPatrolResponse.callForHelp()`. Asked rather than inferred from strength: a
heavily escorted convoy is still a convoy.

**A rules row can take credits but cannot close a faction's books.** Both the patrol's fine and a
holed crew's repair bill hand the outcome back through memory flags that the crime script reads on
its next tick, rather than through `CatchReleaseCMD` — the handoff predates the command and there is
no reason to move it. The bill adds a **global** marker beside the per-fleet flags, because the crew
that was talked to need not still be near the player when anything reads it back; the marker is what
keeps the sector-wide search off every other tick.

**Walking up to a fishing boat must not show the fleet screen.** Everything the trawler is for is behind a comm link, and vanilla's encounter renders that link as one unlabelled line under engage and disengage - a combat prompt with a shop hidden in it. `OpenComms` on a `BeginFleetEncounter` row is vanilla's own answer: it sets the flag the encounter reads at the end of its own `init`, which takes the `OPEN_COMM` branch instead of building the fleet screen at all. No plugin needed. The mod briefly had one that lit the comm option up instead - `$hailing` and `$highlightComms`, written before `super.init`, the same trick `HarpoonedFleetFID` still uses and which does work. It was the wrong question: a lit option on a screen that should never have opened is still that screen.

**`PopulateOptions` is never fired for you - not after `OpenCommLink`, and not after
`DialogOptionSelected` either.** The engine's whole contribution is what `FireBest`/`FireAll` do
with the fired rows' own options columns: the panel is cleared and rebuilt *only when a fired row
carries options*, and left standing untouched when none does. So a handler that only sets
`$menuState` visibly does nothing - the old menu stays up and the new one never appears - and a
handler whose options column holds a submenu works without any firing at all, because `FireBest`
rebuilds the panel from that column. Every row that *switches* menus must do both halves itself:
`$menuState = <state> 0` and `FireAll PopulateOptions`. This map used to claim the engine fires it
after option selections; it does not, and the fisherman's whole menu tree was broken by rows
written to that claim.

**The Church and the Path are against the water, not against fish.** A rupture is a hole opened between here and hyperspace and everything pulled through it came from the wrong side of that hole, so the whole trade is people making a living off a wound in creation. Neither flag therefore produces a fisher, a buyer, a broker, or anybody in a bar with a favour to ask - no bar job at a Church or Path port, no trawler working a system either of them runs, no fishing offer on one of their hulls. What they do produce is everything in `campaign/crime`, plus the cells in `jobs/camp` that sit on ruptures so nobody can work them, and that is the only shape a Luddic interaction with this mod takes. `campaign/fish/FishingTaboo.java` is the one list; nothing hardcodes the two faction ids anywhere else.

**A hostile fleet can still be talked to, and the hail is what makes it possible.** The camp job needs a conversation with a pirate pack that is hostile by default, which no memory flag softens. Vanilla's answer is in the Galatia arc's gate-sitting pirates: `HailPlayer` on `BeginFleetEncounter` opens the link regardless of the relationship, and `MakeOtherFleetGoAway` is what ends it when they agree to leave. Nothing about the fleet is made friendly — the conversation is a thing the player gets to have, not a promise about how it ends.

**The camp job polls rather than being told.** Being killed, bought off, talked off and quietly wandering away do not share a hook, and only two of the four happen inside a conversation. So `CampedSpotJob.advanceImpl` asks one question on the mission's own tick instead of four rules rows each reporting a different way.

**Two flags are the only things that colour a comm link, and both erase themselves.** `$hailing` and `$highlightComms` are read and `unset` by `FleetInteractionDialogPluginImpl` as it builds the option, so a highlight is a one-shot that has to be re-set for every encounter it should appear in. Which means the bug is never a highlight that fails to clear - it is something re-setting the flag on a condition that outlived the reason for it.

**A memory flag makes a fleet willing to chase; an assignment makes it chase.** `MEMORY_KEY_PURSUE_PLAYER` and friends are read when a fleet already has the player as a target, so a freighter told to come and collect a repair bill went on flying its trade route instead. `MEMORY_KEY_MAKE_ALWAYS_PURSUE` plus an explicit `FleetAssignment.INTERCEPT` is what turns the willingness into a course change - see `HarpoonOffence.demand`.

**There is no `FleetAssignment` for running away.** `MEMORY_KEY_AVOID_PLAYER_SLOWLY` is the whole of vanilla's civilian evasion and it does what it says - it biases steering and shortens committed headings, which against anything faster is dawdling in the right direction. A crew that is meant to read as fleeing needs the emergency burn (`Abilities.EMERGENCY_BURN`, asked for with `isUsable()` and never forced) on top of it.

**The lamp response is the transponder's shape, not the harpoon patrol's.** A harpooning is an
incident on a faction's books that a patrol is sent about days later; lit lamps are something the
player is doing right now that anybody in line of sight can see. So `LampPatrolResponse` books
nothing and remembers nothing — it re-asks the three questions every tick and calls the stop off the
moment any of them turns false, which is why turning the lamps out makes the whole thing go away.
The two things that do persist are the sector-wide rung count and a 30-day marker on each crew that
has already had the conversation, so one system cannot produce a queue of identical stops.

**The loot card has two clocks, and only one of them is the readout's.** Its list is held back
until the specimen has finished being tallied, so `elapsed` is zero for the whole of the first
readout — a backdrop driven off it hung motionless over an open card. `advanceBackdrop()` runs from
the moment the card exists and is what the coin rain reads; `advance()` stays the list's own.

**A landed fish goes into a crate, not into the hold.** `FishItems.stow()` is the only landing
path — a good night produced forty single-fish stacks and a hold nobody could read. Loose specimens
still exist and every buyer and job still spends them; nothing *makes* one by default any more.

**A crate and a pile are the same shape, and that is the whole reason the pile was cheap.**
`FishItems.isContainer()` is the question everything spending, selling or counting fish is really
asking — it used to be spelled `BUNDLE.equals(...)` at a dozen call sites, which is a line that has
to be found again every time a container is added. Anything taking fish out of one must put the
remainder back with `FishItems.repack(id, …)` rather than `toBundle`: a part-spent pile rebuilt as a
crate would file every species in it under whichever happened to be first.

**A hand-drawn control has no tooltip, and cannot grow one where it lives.** The shopping-list ring
is painted by `ShopRowPlugin` inside the list's scissor box, so a card drawn there would be sliced
off at the edge of the list — and it is not a `ButtonAPI`, so there is nothing to hang a stock
tooltip on. The row reports the hover to the pane, which draws the card from its own
`CustomUIPanelPlugin.render()`; that runs *after* the panel's children, which is what puts it on top
of everything. The card's `DrawableString`s are rebuilt only when the hovered ring or its marked
state changes — each one is a display list.

**A fleet quest never spawns a fleet, and until it is accepted it never touches one either.** The
offer is two memory keys and a `FleetQuestMarker` hung on a civilian hull already in the player's
system — no rename, no orders, no `$missionImportant`. Turning one down costs nothing because there
is nothing to tidy away.

**Accepting supplants the hull.** `FleetQuest.supplant()` builds a copy (fresh members off the same
variants, so nothing is owned by two fleets at once; only the source market carried over from the
old memory) and despawns the original *with a report*, so whatever was running it — a trade route, a
scavenger sweep — hears that it is gone rather than waiting forever on a fleet parked in a system
for two months. What is left answers to nobody but the job, which is what makes it safe to pin. When
the job ends or its clock runs out, `release()` sends it home to despawn.

**The mark's colour is the message, and it is drawn rather than flagged.** `$missionImportant` is one
boolean with one colour, and setting it on somebody's trade fleet would also make the game treat it
as story furniture. `FleetQuestMarker` copies vanilla's sprite, corner and zoom arithmetic exactly
and changes only the tint. Once the offer is accepted the cyan comes off and vanilla's own takes
over — at that point it is no longer passive.

**The Fisherman is pinned visible while anybody is in the system to see him.** His lamps are drawn
wherever the boat is, whether or not the hull carrying them can be made out — so a Fisherman at the
edge of a sweep was two searchlights working the dark on their own. `keepVisible()` does both halves:
a flat `getDetectedRangeMod()` so he is never a blip, and `forceSensorFaderBrightness(1f)` every tick,
which is a per-frame override rather than a setting and is how vanilla drives its own faders. It is
re-applied each tick rather than set at spawn, so it heals a boat that predates it.

**The Fisherman is one person, made once — and he is on every boat.** Every other fleet in the game
is fresh hulls under a fresh officer with a fresh name; he deliberately is not, because the same face
turning up four jumps and eight months later is the point of him. Standing boat or visiting one, the
encounter shows the same portrait and says nothing about it. The flag that separates the two is
`$catchrelease_fisherman_visiting`, and it is about the *schedule* — nothing about who the player is
talking to hangs off it. `FishermanIdentity` keeps the `PersonAPI` in sector
memory and hands the same object back at every spawn, and the encounter shows the portrait rather
than a fleet readout — a thing a hull list cannot say. What changes is how well he is holding:
`getDrift()` reads the system's own instability through `Aberration.baseAt` (the deterministic
figure, not a specimen's jittered one), and the boat's name, the greeting, and the line under it all
come apart by degrees as it climbs. Letters are taken out by position, so the same system spells him
wrong the same way every time — the degradation is a fact about the water, not an animation.

**All dialogue is in the sheet — all of it.** The Fisherman's whole conversation, the introduction's
six lessons, the hulk, the castaway and the bar rating are rows in `rules.csv`. Java is reached only
through `CatchReleaseCMD <verb>`: in a row's *conditions* `tokens` writes the dozen booleans the rows
branch on and always returns true, and in a row's *script* a verb does the thing and returns whether
it worked. The panels — shop, chart counter, cargo picker — stay Java, because a shelf of cards is
machinery and there is nothing for a sheet to say about it.

**The ladder gates equipment, and equipment gates the world.** `unlockedAtStart` is off for all four
abilities. A shelf needs two things before it is on the floor (`ShopGroup.isUnlocked`): the rig in
your hands, **and** the introduction's blessing. The lamp and harpoon shelves stay shut for one rung
*after* that gear arrives, because the gear and the errand to use it in are handed over in the same
breath and the upgrades for it are what finishing that errand buys — opening them together would make
the errand a formality. No bar job and no fleet job is offered at
all until the first errand is behind you (`FishingIntro.isOpenForWork`), and while the ladder runs an
ask never names gear you have not got — `FishJobAsks.rollCatchTerms` narrows to what is in your
hands, because an order you cannot act on is not an order.

**Three ways into the introduction, none required, and each fitted to where the player already is.**
Out where nobody lives, the first rupture they come within sight of gets a holed cruiser next to it —
sited *then*, not at sector generation, so it is always this rupture and never a thing already on the
map. Surveying a world out there instead turns up the one crewman who was put off a boat for looking
at the catch. In an inhabited system there is already a boat posted, so it simply *is* somewhere
else the next time anybody looks — `FishermanInterception` teleports it in outside the viewport and
nobody aboard remarks on it, which is the cheapest way to say what the Fisherman is. And hailing a
boat works on its own. All of them call `FishingIntro.point()`, which is idempotent.

**The shelf restocks off the sale, not off a calendar.** A monthly tick pays out to whoever happens
to ask just after it, which rewards standing still. Every purchase books a replacement due 30 days
later and asking is what redeems the ones that have come due, so the wait is the same wait for
everybody and starts when the player caused it. The shelf is two charts wide to begin with;
`FishermanShelf.widen()` is what a chart request pays out in, on top of the money, and it is the only
thing that ever raises it.

**A chart request puts the fish there and keeps it there.** A quest that names a system and then
leaves it to the spawn tables can be arrived at correctly and fail for an hour. `FishermanQuest.Keeper`
replants the specimen whenever the player is in the target system and it is missing — only then,
because a mote is an entity with a plugin on it and keeping one alive in a system nobody is standing
in is upkeep bought for nothing. In open water it is spawned on one side of the marked patch aiming
at the other, because a mote swims to its target and expires there; one spawned on top of its own
destination blinks out immediately. It always reads **barely holding**, forced in the minigame's
roll — the request is a question about the water, not about the animal.

**A patrol assignment could not be told to stay out of the way.** `PATROL_SYSTEM` wanders the whole
system and will cut straight across an inhabited orbit getting anywhere, so the standing boats
route themselves: `OuterReaches` defines a band from past the outermost inhabited world out to a
little beyond the furthest thing in orbit — the far-flung planets are on the route, the populated
inner system is not — and `pick()` rejects a destination *and* the leg to it, since a fleet flies its
assignment in a straight line and a good destination reached by cutting through Jangala is the thing
being avoided. Legs are issued one at a time from where the boat actually is; a queue of them would
be a queue planned from a position it has since left. A system too crowded for any leg falls back to
the far edge, which is clear of everything by construction.

**Finding a fishing boat is a map problem, not a space problem.** Nothing is painted over the hull —
out in space the boat is already lit, and a mark there says nothing the lamps did not. The map is the
screen where it is one blip among forty, so `FishermanMapIcon` is a custom entity that rides the
fleet with `showInCampaign` false and `showIconOnMap` true — the pair vanilla's own `base_intel_icon`
is built from. It carries no sensor profile, so it is never a contact to be found; it is simply
drawn, which is what makes the boat locatable while it is out of sight.

**The Fisherman's visit is counted in days the player was not there for.** He cannot despawn in
front of anybody: the clock in `FishermanBehavior` only advances while the player is elsewhere, a
wind-down interrupted by the player turning up is cancelled outright, and the patrol assignment is
topped up rather than cut to fit a stay that no longer has a fixed length. The same check silences
him while nobody is watching — his lamps go into LunaLib's one sector-wide renderer list and his
sounds play wherever the player is standing, not where they were asked for.

**Closing the outfitter is not the same as closing the dialog.** `FishShopDialog` takes an optional
`OnClose`; without one, escape closes the whole interaction, which is what the ability bar wants.
The Fisherman passes one, because the shop runs inside his conversation and dropping the encounter
would read as the shop having hung up on somebody the player was mid-sentence with. The way back is
the opener's to write: the shop hides the text and visual panels and dims the background going in,
and `InteractionDialogAPI` has no getter for what any of that was before.

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
| `testing/DevShortcut` | Registered from `ModPlugin` as a transient listener; does nothing unless dev mode is on |
| `testing/TestStencilRenderer` | Commented out of `ModPlugin` |
| The pond's shader swirl | Dormant behind `PondConstants.POND_HOLE_LOOK`, which currently selects the stencil hole renderer |
