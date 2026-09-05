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
