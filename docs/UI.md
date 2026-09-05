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
