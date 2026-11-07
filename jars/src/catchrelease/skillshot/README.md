# Skillshot Framework

Aimed campaign abilities for Starsector. Hold an ability hotkey to aim and release it to fire.
Clicking the ability instead starts aiming; the next map click fires.

The framework was extracted from Industrial.Evolution's consumable missiles. Its Java source is
in this package; assets and registration entries are in the Catch.Release mod root. Industrial
Evolution keeps a copy under `indevo.skillshot` with the same file layout, so fixes port by renaming
the package. The two copies share one [cross-mod lock](#cross-mod-lock).

Shared drawing gotchas are in [UI.md](../../../../docs/UI.md#drawing-gotchas).
Reticule hooks and targeting lifecycle stay here.

Read this guide before implementing an ability or changing targeting hooks, registration
or lifecycle. For Catch.Release's tooltip wording and other player-facing text, also use
[DIALOGUE.md](../../../../docs/DIALOGUE.md); this guide owns the framework's technical
integration, not a separate text standard.

## Requirements

- Starsector 0.98a API
- [LunaLib](https://github.com/Lukas22041/LunaLib) — used for campaign rendering
  (`LunaCampaignRenderer`). It is the only third-party dependency.

## Install

1. Copy the Java package and its referenced assets into your mod, preserving the package name or
   updating Java imports and plugin registrations together. In this repository they are located at:

   ```
   jars/src/catchrelease/skillshot/
   data/campaign/abilities.csv
   data/config/settings.json
   data/config/sounds.json
   graphics/catchrelease/fx/skillshot_*.png
   sounds/catchrelease/skillshot/skillshot_denied.ogg
   ```

   Merge only the framework's ability, sprite and sound entries into your mod's registries.
   Do not copy the whole Catch.Release registries over another mod's files.

2. Add LunaLib to `mod_info.json` dependencies if it isn't there already.

3. Call the framework from your `ModPlugin`:

   ```java
   @Override
   public void onGameLoad(boolean newGame) {
       SkillshotFramework.register();
   }

   @Override
   public void beforeGameSave() {
       SkillshotFramework.reset();
   }
   ```

   `register()` installs the hotkey listener and is idempotent. It also clears the cross-mod lock.
   `reset()` cancels any half-aimed shot so it never ends up in the save, and releases the lock.

Never ship another mod's copy under its package name: all mod jars share one classloader, so a
second `catchrelease.skillshot` would collide with Catch.Release's.

## Writing an ability

Extend `BaseSkillshotAbility` and implement two methods:

```java
public class MyMissileAbility extends BaseSkillshotAbility {

    @Override
    public SkillshotRenderer createReticule() {
        return new DirectionReticuleRenderer();
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        // spawn your missile, drop your mine, call your artillery
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("Does something at the place you point at.", 10f);
    }
}
```

Then add a row to `abilities.csv` with **`skillshot` in the tags column** and your class in the
plugin column. The tag is how the hotkey listener recognises the ability in the ability bar; without
it only the click path works.

Useful hooks on `BaseSkillshotAbility`:

| Method | Purpose |
| --- | --- |
| `createReticule()` | New reticule per targeting session |
| `onSkillshotFired(target, angle)` | Action after a targeted shot |
| `getActivationVolume()` | Activation sound volume; default 1. Consumers may apply their own settings. |
| `onConsume()` | Runs once per successful shot — remove the item, spend a charge |
| `isTargetingBlocked()` | Why aiming can't start right now; override to add your own conditions |
| `addTooltip(tooltip)` | Ability tooltip body; the framework appends its own blocked-reason lines |
| `showReticuleOnActivation()` | Whether this press should be aimed at all; default true |
| `onActivatedWithoutReticule()` | Action for an unaimed press |

## Abilities that are only sometimes aimed

Override `showReticuleOnActivation()`. It is asked on every press, so it can depend on game state:

```java
@Override
public boolean showReticuleOnActivation() {
    return closestPondActive();
}
```

While it returns **false** the framework stays out of the way completely — the hotkey is left
unconsumed, `pressButton()` falls through to the vanilla `BaseDurationAbility` path, no reticule is
created, and `isTargetingBlocked()` stops restricting the ability (so it works from the core UI tabs
like any vanilla ability, and its blocked-reason tooltip lines disappear). The activation lands in
`onActivatedWithoutReticule()` instead of `onSkillshotFired(...)`, because there is no aim point —
firing at the cursor would just shoot wherever the mouse happened to sit.

While it returns **true** everything behaves as described above.

The `deactivationCooldown` column in `abilities.csv` is the rearm time — the framework applies it on
fire, not on button press.

## Reticules

| Class | Looks like | For |
| --- | --- | --- |
| `DirectionReticuleRenderer` | Arrow at the cursor | Things fired in a direction |
| `AreaReticuleRenderer(size)` | Circle at the cursor, sized to the effect | Things that land on a spot |
| `ValidatedAreaReticuleRenderer(size, validator)` | Same, but turns red and refuses to fire on rejected positions | Restricted targeting |

### Guide lines

Any reticule can draw lines out from the fleet along the aim direction — they live on
`BaseReticuleRenderer`, so the arrow, the circle, and anything you subclass yourself all take them.
All three are opt-in; without them a reticule renders exactly as before:

```java
new DirectionReticuleRenderer().withTrajectory()   // one line: where the shot goes
new AreaReticuleRenderer(400f).withBounds(30f)     // two lines: the edges of a 30 degree spread
new DirectionReticuleRenderer().withTrajectory().withBounds(30f).withLength(2000f)
```

`withLength(range)` stops the lines at the ability's range; without it they run out to the cursor.
Lines always start outside the fleet ring, and stop short of whatever the reticule draws at the
cursor — the area circle stops them at its edge, the direction arrow lets them run in. Override
`getGuideLineEndPadding()` to change that. They pick up the reticule's valid/invalid tint.

#### Styling

`withLineStyle(GuideLineStyle.DASHED)` — `SOLID`, `DASHED` or `DOTTED`:

```java
new AreaReticuleRenderer(400f).withTrajectory().withLineStyle(GuideLineStyle.DOTTED)
```

Everything about how they look sits in `SkillshotSettings`, so a mod can retune it once at startup
rather than at every call site:

| Setting | Default | Applies to |
| --- | --- | --- |
| `GUIDE_LINE_STYLE` | `SOLID` | Reticules that don't call `withLineStyle()` |
| `GUIDE_LINE_WIDTH` | `2f` | Every style — line thickness in screen pixels |
| `GUIDE_LINE_ALPHA_MULT` | `0.45f` | Every style — multiplier on `RETICULE_ALPHA` |
| `GUIDE_LINE_DASH_PX` / `GUIDE_LINE_DASH_GAP_PX` | `24f` / `24f` | `DASHED` — stroke and gap length |
| `GUIDE_LINE_DOT_PX` / `GUIDE_LINE_DOT_GAP_PX` | `4f` / `28f` | `DOTTED` — same two lengths |

Lengths are in screen pixels. `SkillshotUtils.cutDashes()` converts them through the current
viewport and constructs dash geometry; it does not use GL stippling. Connected segments preserve
phase, and a break in the path resets it.

A `PositionValidator` is one method — `boolean isValid(Vector2f worldPos)`. `MarketProximityValidator`
ships with the framework and rejects aim points near inhabited worlds:

```java
return new ValidatedAreaReticuleRenderer(400f, new MarketProximityValidator(500f));
```

For anything else, subclass `BaseReticuleRenderer` and implement `renderCursorBoundObject(...)`. The
fleet ring, the cursor tracking, and the valid/invalid tint are handled for you; `cursorPos` holds
the current aim point and is safe to read from `isValidPosition()`.

## Configuration

`SkillshotSettings` holds every sprite id, sound id, size and colour, plus the ability tag name.
All fields except `LOCK_KEY` are writable. Rewrite them once during startup to point at your own assets:

```java
SkillshotSettings.SPRITE_DIRECTION_ARROW = "mymod_arrow";
SkillshotSettings.LOG_DEBUG = true; // state transitions to starsector.log
```

## How it works

Two input paths lead to the same place.

**Hotkey** — `OnKeyPressSkillshotListener` is a single long-lived `CampaignInputListener` at priority
0. It intercepts the 1-9 keys before the UI can turn them into a button press (a button press would
fire the ability instantly, leaving nothing to aim), checks the slotted ability for the skillshot tag,
and opens a session. Releasing the same key fires.

Both paths ask `showReticuleOnActivation()` first and do nothing themselves if it says no.

**Click** — `BaseSkillshotAbility.pressButton()` starts a per-session `OnClickSkillshotListener`.
Registration is deferred by one frame via `DelayedActionScriptRunWhilePaused`, because the UI outranks
priority-0 listeners and the new listener would otherwise eat the very click that created it. The next
left click on the map fires. The listener is transient and removes itself on every exit (fire, abort,
dialog, reset). Input listeners run from a copy of the listener list
(`ListenerUtil.getSortedInputListeners`, `ListenerManager.getListeners`), so it can remove itself
during input.

`SkillshotActivationManager` is an `EveryFrameScript` (running while paused) that allows exactly one
live session, clears it when it goes inactive and holds the cross-mod lock while it is live.
`hasActiveSession()` (an own live session, or the lock held by another mod) backs
`isTargetingBlocked()` and the "Conflicting ability in use!" tooltip line across abilities and mods.

Either path cancels cleanly on an opened dialog, on ctrl (the vanilla slot-reassign modifier), or on
any stray keypress. Firing at a position the reticule rejects plays `skillshot_denied` instead.

## Cross-mod lock

Industrial Evolution ships the same framework under `indevo.skillshot` and also listens to keys 1
to 9. One sector memory key stops the two from aiming at once. Plain values only, so neither mod
references the other's classes. Keep the key name and meaning identical in every copy.

| Key | Value | Set | Read |
| --- | --- | --- | --- |
| `$sirhartley_skillshotActive` | Owner mod id: `catchrelease` or `IndEvo` | `SkillshotActivationManager.setCurrentListener` (session start) | `hasActiveSession()`: a value that is not the own id blocks targeting and shows "Conflicting ability in use!" |

- A mod unsets the key only when the value is its own id: on the manager `advance` after its
  session goes inactive, and in `reset()` before a save.
- `register()` clears the key on load whatever its value; nothing aims during a load, so a key found
  then is stale.
- No memory expiry: it does not tick while the game is paused, and aiming is mostly done paused.
- The tags differ (`skillshot`, Industrial Evolution `IndEvo_skillshot`) and both check
  `instanceof SkillshotAbility` of their own package, so each framework only handles its own
  abilities.
- Owner: `SkillshotLock`. `SkillshotSettings.LOCK_KEY` is final; `LOCK_OWNER` is the id a copy writes.

## Extension boundaries

The framework chooses reticules through `SkillshotAbility.createReticule()` and target restrictions
through `PositionValidator`. Validation uses the current frame's cursor position. Both hotkey edges
resolve the same slotted ability, including in hyperspace. Consumers implement cargo or charge
spending through `onConsume()`; the framework does not own inventory.

Update this README with changes to those hooks, registration, assets, the cross-mod lock or
lifecycle. Follow
[CLAUDE.md](../../../../CLAUDE.md#documentation-upkeep) for repository maintenance.
