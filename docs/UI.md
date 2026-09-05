# Custom UI

Engine details here are checked against Starsector 0.98a-RC8. Recheck them when
updating the game. See [ARCHITECTURE.md](ARCHITECTURE.md) for UI owners and shared
rendering constraints, and [DIALOGUE.md](DIALOGUE.md) for text presentation.

## Hover tooltips

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

`FishMapPane` and `FishRoutePopup` use this pattern. Their `listViewport` is the
position returned by the owner's `addUIElement`, so row clipping and hit tests
still use the visible viewport, not the full scrollable content.

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
