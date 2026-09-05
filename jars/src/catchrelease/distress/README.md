# Distress Call Framework

This framework schedules campaign distress calls alongside vanilla nearby events. The consuming
mod supplies the quest, dialogue and rewards.

For Catch.Release's player-facing text, follow [DIALOGUE.md](../../../../docs/DIALOGUE.md).
Java intel controls and sidebar maps are covered by
[UI.md](../../../../docs/UI.md#intel-and-sidebar-maps). Provider integration stays here.

## Install

Keep the package under the mod's ordinary `jars/src` tree, merge its two generic rules into
`data/campaign/rules.csv`, add `data/campaign/distress_calls.csv`, then register it on game load:

```java
DistressCallFramework.registerProvider("mymod", new MyDistressCallProvider());
DistressCallFramework.register();
```

Registration is idempotent. Providers are runtime adapters and are registered again on every load;
saved instances retain only their provider and specification ids.

## Data

The framework loads `data/campaign/distress_calls.csv` with Starsector's merged-spreadsheet API.
Every live row is one event. Prefix an id with `#` to disable the row. IDs must be namespaced because
the game's order for duplicate non-master rows is undefined.

`providerId` resolves the registered adapter. `weight` selects among eligible rows and
`probability` is the selected row's final 0-to-1 spawn roll. `factionId`, `fleetType`, `minFP` and
`maxFP` describe the entity supplied by the framework. `cooldownDays` and `maxActive` limit that row.
`dialogTrigger` is fired when the comm link opens. `tags` are opaque to the framework and available
to the provider.

The provider decides whether a row is eligible, attaches its own quest state to the supplied
fleet, and may replace the default jump-point orbit anchor with another token in the same system.
It owns all dialogue, rewards, acceptance and completion. The generic comm entry requires the
framework flag, and the saved instance verifies the exact fleet and instance reference before it
fires the provider trigger. Call `DistressCallFramework.resolve(fleet)` when the encounter has
finished.

## Vanilla integration

`NearbyEventsBridge` binds to the live 0.98a `NearbyEventsEvent`. The framework has no independent
timer: vanilla gets the first chance on its normal interval, and a framework row is considered only
when that check created no vanilla distress call. Both selectors use vanilla's system reservation
tracker. While a framework call is live, short reservations keep vanilla from opening a concurrent
call; they expire naturally when the framework stops refreshing them.

The bridge does not replace `nearby_events`, call its event picker, or claim a vanilla-created
entity. If the expected vanilla fields are unavailable, the framework fails closed and spawns
nothing.

## Maintenance

Update this README when provider hooks, registration, CSV fields or reservation behavior change.
Keep quest-specific rules and copy in the consuming mod. See
[CLAUDE.md](../../../../CLAUDE.md#documentation-upkeep) for repository maintenance.
