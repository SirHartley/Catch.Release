# Custom Java UI

This guide covers Java-bound custom panels, widgets and renderers, including
intel, shops, maps, portraits and tooltips. It is not the rules-dialogue guide.
All player-facing wording and shared text-display requirements, including those
used here, belong in [DIALOGUE.md](DIALOGUE.md#shared-text-presentation).

Engine details are checked against Starsector 0.98a-RC8; recheck them when updating
the game.

## Before editing

- Find the owning code and existing widgets in [ARCHITECTURE.md](ARCHITECTURE.md).
  Read the relevant sections here before changing a screen or its presentation.
- Use the Starsector knowledge skill to check vanilla patterns and uncertain API
  behavior. Framework-specific hooks stay in their README files.
- Follow [DIALOGUE.md](DIALOGUE.md) for text production, presentation, Editor review
  and dialogue flow, and [LORE.md](LORE.md) for prose and character constraints.
- When a Java custom panel is opened from rules, follow [RULES.md](RULES.md) and its
  required references for the caller's routing and teardown. Rules-authored text
  and options are governed by `DIALOGUE.md`, not this guide.
- Update the affected contract here in the same commit. Workflow and build
  requirements remain in [CLAUDE.md](../CLAUDE.md#documentation-upkeep).

## Text in custom controls

Use the [shared text presentation](DIALOGUE.md#shared-text-presentation),
[surface requirements](DIALOGUE.md#surface-requirements) and
[Catch.Release display conventions](DIALOGUE.md#colours-rewards-and-sidebars).
Do not maintain separate wording or formatting standards for Java strings.

In the outfitter, a null module price can mean an empty slot or an already-owned
module. UI text must distinguish them.

## Intel and sidebar maps

- Remote offers and location reminders use `QuestDialogMap` below the portrait.
  Use the stored target; do not display a map for a local or unresolved destination.
  Remove only the temporary map/marker owned by that preview after acceptance,
  leaving, or switching surfaces.
- Navigation follows the current objective: Open fishing map for fish requests
  without a fixed destination, Plot route for a specified fishing location, and Set autopilot
  for non-fishing destinations such as a rumor location or tutorial hand-in.
  Completed intel must not retain active-objective navigation.
- Navigation and request hovers follow the shared
  [surface requirements](DIALOGUE.md#surface-requirements).
- Intel icons distinguish rupture-only, lamp-only and mixed requirements; POINT
  tutorial entries use the tutorial icon. Check the shared icon resolver instead
  of copying a portrait.
- Unknown range data must remain visibly unknown in a forced map/planner handoff.
  Check the no-data state and reset path, not just a fully unlocked dev view.

Navigation owners: `FishIntelMapButton`, `FishJob`, `FishingIntro`, `FishermanQuest`
and `FishRumors`. Quest-specific destinations come from their saved state.

## Portraits and sprites

- `SpriteLoader` and `FishIcons` use fresh sprite wrappers. Never retain mutable
  sprite state across screens. `FishIcons.draw` owns the fish/silhouette rendering;
  `drawBacklit` adds the rarity backlight. Both use the shared Codex unlock state.
- Aquarium backdrop source art: 388×170, visible 386×168; 2× visible assets: 772×336.
- Aquarium fish use `spriteDirection` in degrees counter-clockwise from image
  right, measured from the full image centre. `FishSpritePose` aligns the mesh
  to that exact bearing, with no quarter-turn snapping. It preserves the image
  proportions and the old mirroring convention for left-painted fish. Tail sway
  follows the aligned body axis; UVs stay within the sprite's texture region.
  The IDE-facing picker is described under [registration and lifecycle](ARCHITECTURE.md#registration-and-lifecycle).

Fisherman identity and vanilla person-panel setup are covered by
[ARCHITECTURE.md](ARCHITECTURE.md#fisherman-and-tutorial-lifecycle).

## Custom-dialog hosts

- Clear host options before custom panels and restore the prior menu once on
  close. Check both confirm and cancel paths.
- `showCustomDialog()` always includes a confirm button. Use
  `showCustomVisualDialog()` when the panel must have none.
- `FishShopDialog` takes an optional close callback. Colony use may dismiss the
  interaction; Fisherman use must restore the conversation.

Use the [project routing](RULES.md#project-routing) for Fisherman panel returns and
the [fleet and bar exit paths](RULES.md#fleet-and-bar-exits) for teardown.

## Cargo pickers

The description panel passed to `CargoPickerListener.recreateTextPanel` is a
tooltip, not an interactive UI element. Its outer `setForceProcessInput(true)`
must be called through `ReflectionUtils` before nested buttons can receive input.
The nested custom panel's normal `buttonPressed` callback then works. Set its
button to quick mode: vanilla rebuilds this tooltip every frame, so a button
waiting for mouse-up can be replaced between press and release.

`FishBuyer` queues packing for the button panel's `advance`, outside input
dispatch. It finds only the ancestor picker holding its exact offer cargo, then
that picker's cargo panel by `updateCargoViews`; no obfuscated names or global
UI search. Packing waits until no stack is held by the cursor and clears the
transfer handler's ledger with `resetTransaction` before replacing either grid.
Otherwise, cancel would replay transfers using obsolete loose-fish identities.
Copy the packed player cargo into the offer instead of packing both independently:
crate contents must have identical ordering for item removal on sale.

Sources (0.98a-RC8): cargo picker and custom-panel implementations in
`sources-obf/ui.newui.java`; `StandardTooltipV2.processInputImpl` in
`sources-obf/ui.impl.java`; cargo panel `updateCargoViews` and transfer handler
`resetTransaction/cancelTransaction` in `sources-obf/campaign.ui.java`.

## Hover tooltips

Use transparent custom-panel hotspots to attach stock tooltips to hand-drawn
controls. Vanilla then owns tooltip timing, placement, and clipping.

`addTooltipTo` and `addTooltipToPrevious` default to rebuilding every frame.
Pass `false` as the last argument for fixed help text. Vanilla still creates the
content when the tooltip is shown; this only stops rebuilding it throughout the
hover.

Map and planner help, category descriptions, chart-shop help, outfitter help and
intel navigation tips use this non-rebuilding mode. Fish-row selection hints,
chart affordability, shop marks, tier status and payment details keep live updates.
Do not cache a tooltip that reads changing state unless its owner refreshes it.

Source: `StandardTooltipV2Expandable.addTooltipTo/addTooltipToPrevious`,
`beforeShown` and `advanceImpl` in the knowledge base's `sources-obf/ui.impl.java`.

## Rebuilding lists

Create a UI element and call `addUIElement` on the same `CustomPanelAPI`.
The creator stores its requested height and scrollbar flag in maps keyed by the
tooltip. Removing the element or clearing children does not remove those entries.

For a list rebuilt repeatedly, reuse its content or give each rebuild a new child
custom panel. Remove the old child panel, not just its tooltip or scroller. Keep
the list's `createUIElement` and `addUIElement` calls on that disposable owner.
Do not keep old owners in another cache.

`FishMapPane` and `FishRoutePopup` use this pattern. After creating each replacement
panel, set `listViewport` to the position returned by that panel's `addUIElement`
call. Use this position for drawing and mouse checks. The old panel's position is
no longer valid, and the full scrollable content is larger than the visible viewport.

Source: the `CustomPanelAPI` implementation's `createUIElement/addUIElement` in
`sources-obf/ui.newui.java`, and `UIPanel` removal in `sources-obf/ui.java`.

## Layout

- Positions use logical screen units with a bottom-left origin, not normalized
  0-to-1 coordinates. Use the panel's position when drawing; a custom plugin does
  not get an automatic translation to its panel's origin.
- A scrollable `createUIElement(width, height, true)` reserves five units of the
  requested width for its scrollbar. The supplied height is the viewport height.
  For a non-scrolling element it is a minimum content height.
- `addUIElement` returns the scroller's position when scrolling is enabled.
  Use that for row clipping and hit tests. `getExternalScroller()` gives access
  to the scroll offsets.
- `addCustom` normally places content below the previous element and follows its
  left edge. Repositioning one element can therefore shift later content too.
  `addTitle` anchors at the top-left; use a paragraph or a separate header panel
  for a heading inside the content flow.
- Relative anchors must refer to siblings and must not form a cycle.

Sources: `PositionAPI`, `CustomPanelAPI` and `TooltipMakerAPI` in
`sources-api/ui.java`; position recomputation in `sources-obf/ui.java`;
`createUIElement/addUIElement` in `sources-obf/ui.newui.java`; and
`StandardTooltipV2Expandable.addCustom/addTitle` in `sources-obf/ui.impl.java`.

## Rendering and input

For a custom panel, the order is:

| Pass | Order |
|---|---|
| Render | Plugin `renderBelow`, children in panel order, plugin `render` |
| Input | Children in reverse panel order, then plugin `processInput` |
| Advance | Children, then plugin `advance` |

Put backgrounds in `renderBelow`. Apply the supplied alpha to custom drawing.
Respect consumed input: a parent plugin may receive events already handled by a
child. Use `isLMBDownEvent()` or `isLMBUpEvent()` for a single activation;
`isLMBEvent()` matches both. `setQuickMode(true)` changes vanilla buttons to
activate on mouse-down; it does not disable their checked-state toggle.

If a click needs to rebuild a subtree, prefer queuing it for the owning panel's
`advance` rather than changing the hierarchy midway through input dispatch.

Sources: custom-panel callbacks in `sources-obf/ui.newui.java`; child dispatch
and button handling in `sources-obf/ui.java`; mouse-event predicates in
`sources-obf/util.A.java`.

### Drawing gotchas

- `Stencil.startStencil()` is deprecated because it breaks campaign radar. Use
  the depth-mask pair in `rendering/helper/Stencil`.
- `GL_LINE_STIPPLE` restarts on each `GL_LINES` segment and is unusable for short
  campaign lines. `SkillshotUtils` builds dash geometry explicitly.

Campaign VFX, reflection restrictions and sound formats remain in
[ARCHITECTURE.md](ARCHITECTURE.md#rendering-ui-reflection-and-audio).

The coherence map keeps temporary system conditions separate from hyperspace heat.
`CoherenceHeatField` draws their rings with `Disc` and the shared coherence colour;
`Aberration.temporaryAt` supplies the live value, including colony precedence and expiry.

### Minigame timing

- The line sound uses one continuously refreshed UI loop with changing volume.
- The loot result has a backdrop clock that starts when the panel is created and
  a list clock that starts after the catch tally. Coin rain uses the backdrop clock.

### Fish difficulty tuner

`catchrelease.tools.FishDifficultyTuner.main` in the normal `catchrelease` module
opens a plain Swing authoring window outside the game. Its tool-only
`FishingSimulation` copies the catch model and `FishingMinigamePanel` jitter,
using the existing game constants and types read-only. The game does not call
the simulator. Gear/player/rumor controls are test conditions, not fish
CSV fields. Treasure pursuit is not simulated. Saving and launch arguments are
under [authoring tools](ARCHITECTURE.md#registration-and-lifecycle).

Beginner, Regular and Skilled use delayed screen observations with different
decision intervals, tracking error and anticipation. They do not read future
fish targets. These are repeatable test profiles, not calibrated human skill.
Manual mode takes left-mouse or Space only in the focused preview. Losing focus
releases input and pauses manual play; window focus loss pauses all modes.

The tool advances at fixed 1/60-second steps. Both Caught and Lost are logged,
held on screen for 0.8 seconds, then followed by a new simulation and seed with
settings retained. The last result stays visible. Disable losing is off by
default and independent of game devmode; it uses the existing practice floor.
An attempt remains unscored if protection was enabled or tuning changed during
it. Tuning changes clear the current statistics; switching input/gear starts a
fresh session. Help appears only in the bottom bar on hover or keyboard focus;
do not register floating Swing tooltips. The preview adds its live coordinates
and progress there. The last selected help stays visible, including while paused;
validation notices stay until the next hover or focus event.

Run `Fish Tuner Checks` from IntelliJ for CSV saves, resets, determinism, profiles,
input and off-screen panel rendering at two sizes. Run `Fishing Parity Checks`
for game/model comparison. Both check classes live beside the tools under
`jars/src/catchrelease/tools` and run headless with Java 17 and the normal module
classpath; see [model ownership](ARCHITECTURE.md#registration-and-lifecycle).
Neither check replaces the full mod build or live in-game QA.

Balance results runs all three anglers with normal losing at fixed 60 Hz, without
drawing or real-time waits. Up to four workers run fish snapshots independently.
The default is 30 attempts per angler and a 120-second limit. Timeouts stay
separate from losses and remain in catch-rate denominators. Catch-time
percentiles and sample deviation use successes only; the 95% catch-rate interval
uses Wilson's method. Flags are screening heuristics, not balance verdicts.
Tables retain stale results visibly; charts and movement groups exclude them.
Movement groups use declared modes (MIXED is separate), with equal per-fish
weight for mean catch rates. Filtering and sorting also control the charts.
Edits debounce a retest of only previously tested fish; an active batch finishes
before queued edits run. Cancelling keeps completed fish, discards incomplete
ones and stops queued retests. Closing the tuner cancels all background work.
Before / after compares saved CSV values or a named in-session checkpoint with
current edits. Both use the same setup, sample count, first seed and time limit.
The panel retains both snapshots, reports percentage-point changes and counts
seeds that became or ceased being catches. Each side's time statistics use its
own successful attempts. Catch-rate intervals are not a significance test of
the difference. Selection or input changes mark the retained comparison stale.
Selected results explain the metrics in plain language and suggest one-field
experiments. Required coverage and drain times use the tested fish and setup,
not the current controls. Advice labels uncertainty and possible causes; it
does not treat bot catch rates as human success rates or promise a particular
fix. Every movement type has a description, including its main tuning tradeoff.

## Keep optimizations local

Reuse existing widgets and text where practical. `TooltipMakerAPI` is a supported
UI builder, not just a floating tooltip. `SettingsAPI.createLabel/createTextField/
createCheckbox` can also create standalone widgets without an extra builder.
Do not share a static tooltip builder between screens: it carries layout and
listener state.

Keep off-screen row culling and rebuild only when displayed data changes.
Profile a slow panel before imposing list-size limits or replacing its renderer.
OpenGL draw submission costs CPU time, but that does not make the rendering
CPU-only.

## Review the affected screen

Check the changed states, not just the fully unlocked dev view: unknown data,
partial progress, locked or owned gear, repeated names, and completed objectives
where applicable. Open and close custom panels through confirm, cancel and Escape;
check the restored options and sidebar. Rebuild scrollable lists repeatedly and
check clipping, hit tests and hover placement at the edges.

Apply the [shared text checks](DIALOGUE.md#shared-text-presentation) to the final
display. Report source checks separately from in-game QA. A successful compile
does not verify layout or text presentation.
