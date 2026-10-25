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

In the outfitter, a null module price can mean an empty slot, an already-owned
module or a ship module that comes with a fishing ship. UI text must distinguish them.

### Outfitter second slot

While a rig has its second slot (`FishingShips.hasSecondSlot`), the Equipment shelf
shows two `ShopSlotPlugin` cells above the module list, drawn like category tabs:
half the list width each, `SLOT_HEIGHT` tall, the slot's module or "Empty" beneath a
small "SLOT n" label. The selected cell is the target slot; FIT reads "FIT IN SLOT n"
and every row and the detail pane are judged against it. Without a second slot the
shelf looks as it did with one slot. A module fitted in the other slot, or one that
shares an exclusion group with it, is blocked for the target slot: its row name is
grey at 0.6 alpha with an "IN SLOT n" or "CONFLICT" mark, FIT and SELECT are not
live, and a hotspot past the mark ring carries a hover naming the slot or the
conflicting module. A blocked row stays selectable so the detail pane can say why; for an owned or ship
module that line replaces the "fitting it is free" price line. The detail header's
accent, like the row, is judged against the target slot (`ShopEntry.isDone(slot)`).
A ship module shows "SHIP" where an owned module shows "OWNED".

Treasure result rows normally use the chest rarity colour, read from
`TreasureAward.color`; a quest chest such as T1's golden chest sets its own label and
colour there, and `MinigameTreasure.getColor` draws it on the track. Range-data bonuses
carry `TreasureAward.Item.color` from the species rarity and use the vanilla
remote-survey-data icon, not an uncaught fish portrait. `LootResultPanel` keeps
the chest colour as the default for existing item rewards.

## Intel and sidebar maps

- Offers and location reminders use `QuestDialogMap` below the portrait, including
  targets in the current system. Use the stored target; omit unresolved destinations.
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

In Intel's Planets view, Patterns keeps the selected planet detail card's full
height even with few fish. Its width fills the remaining right-hand column.
Top-align the non-scrolling grid below the heading with ten units of padding.
Centre each row horizontally, including an incomplete last row. Scale cells and
gaps down together only if needed to fit the available height. Clear
`addCustom`'s first-row text inset. Match the planet
card's full-alpha dark player colour for the heading and border; its interior
uses black at 0.67 alpha, without a black underlay beneath the heading.

Sources: planet detail card rendering in `sources-obf/campaign.ui.java`, heading
rendering in `sources-obf/ui.java`, and `StandardTooltipV2Expandable.addCustom`
in `sources-obf/ui.impl.java` (0.98a-RC8).

### Fleet map markers

Fleet jobs use `fleet_quest_map_icon` beside the fleet and for the accepted map
proxy. Offers stay light cyan and pulse; accepted markers hold steady at full alpha
in the yellow colour from the
`catchrelease_FleetQuestMapIcon` custom-entity spec on both surfaces. Keep its
asset path and the settings sprite alias aligned. `FleetQuest` owns marker state
and registers its delivery flag without adding vanilla importance. The Fisherman
chain's captain (`CaptainsFleet`) reuses the proxy through `FleetQuestMapIcon.PINNED_FLAG` and the fleet-side
marker in both colours (`CaptainsFleet.syncMarker`).

`FleetMapVisibility` uses the fleet's logical visibility in the player's location,
not sprite-fader brightness. Even `SENSOR_CONTACT` has a real radar icon. Remove
the proxy entity from its location while detected; restore it at `NONE` or when
a standing boat unloads. Redirect an active local course to the fleet before
hiding its proxy. Retained tokens must stay with their saved owner.

In 0.98a-RC8, `BaseLocation.removeEntity` leaves the token's containing location
intact; `isAlive()` checks membership. The map has its own fader and uses the
shared custom-entity spec, so neither sensor-fader changes nor mutating that spec
is a per-marker visibility switch. `DetectedEntityListener` omits some initial,
off-location and no-profile transitions. Existing fleet/mission advance callbacks
therefore reconcile these markers; no separate sector-wide polling script is needed.

## Portraits and sprites

- `SpriteLoader` and `FishIcons` use fresh sprite wrappers. Never retain mutable
  sprite state across screens. `FishIcons.draw` owns the fish/silhouette rendering;
  `drawBacklit` adds the rarity backlight. Both use the shared Codex unlock state.
- Cargo crate labels use pixel coordinates in the native 80×80 `fishbarrel.png`,
  centred in the supplied item cell. Do not scale those coordinates by cell width
  or height: vanilla's `CargoStackView.renderAtCenter` draws the base sprite at
  native size and applies any outer scale to both passes (0.98a-RC8,
  `sources-obf/campaign.ui.java`). `tools/FishCargoIconChecks` checks the rendered
  corners across cell sizes, translations, alpha and hover passes without OpenGL.
- The crate label shrinks the 80×80 fish art about four times. Game textures up to
  1024 px are trilinear with generated mipmaps (`TextureLoader`, 0.98a-RC8), so the
  label samples a soft box-filtered level. `FishBundleItemPlugin.render` adds
  `BOX_ICON_LOD_BIAS` (−0.5) to the texture-environment LOD bias around
  `renderBoxIcon` only and restores it in `finally`: three GL calls per crate, no
  texture memory. Loose fish draw at native size and are not biased. Keep the GL
  calls out of `renderBoxIcon`, which the check tool calls without a GL context.
- Aquarium backdrop source art: 388×170, visible 386×168; 2× visible assets: 772×336.
- Aquarium fish use `spriteDirection` in degrees counter-clockwise from image
  right, measured from the full image centre. `FishSpritePose` aligns the mesh
  to that exact bearing, with no quarter-turn snapping. It preserves the image
  proportions and the old mirroring convention for left-painted fish. Tail sway
  follows the aligned body axis; UVs stay within the sprite's texture region.
  The IDE-facing picker is described under [registration and lifecycle](ARCHITECTURE.md#registration-and-lifecycle).
- Aquarium distortion reads each specimen's saved `FishCatch.aberration`. Stable
  fish bypass it; digital strip displacement, pixel breakup and partial dropouts
  grow above 0.12 aberration. At maximum strength, bursts occupy about half the
  animation cycle, strips shift up to six pixels and pixel blocks reach five
  pixels. Each fish has its own phase. All colour channels use the same sample;
  there is no chromatic separation or RGB setting. The Digital artifacts toggle,
  distortion slider and reduced-effects setting still control the filter.
  `AquariumFishShader` samples only the fish texture inside its UV
  region. It never samples the screen or changes sprite properties. The tank,
  cargo icons, shops and Codex are unaffected. Keep the previous shader, texture
  unit, binding, blend and colour state intact after drawing. Disabled or failed
  shaders leave the ordinary swimming mesh visible.

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
waiting for mouse-up can be replaced between press and release. Clear its
mouse-over sound with `setMouseOverSound(null)`: each new button sees the cursor
enter, so the hover click would replay every frame. The button skips a null
sound (0.98a-RC8 `ui.newui` button class).

`FishBuyer`'s one button toggles between "Pack into crates" and "Unpack from
crates"; the picker opens unpacked. It queues the toggle for the button panel's
`advance`, outside input dispatch. It finds only the ancestor picker holding its
exact offer cargo, then that picker's cargo panel by `updateCargoViews`; no
obfuscated names or global UI search. The toggle waits until no stack is held by
the cursor and clears the transfer handler's ledger with `resetTransaction`
before replacing either grid. Otherwise, cancel would replay transfers using
obsolete fish or crate identities. Copy the packed or unpacked player cargo into
the offer instead of changing both independently: crate contents must have
identical ordering for item removal on sale. Confirm and cancel both restore the
crates the hold had before the picker opened.

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

Map and planner help, category descriptions, chart-shop help, outfitter help,
the outfitter's rarity-count chips and intel navigation tips use this
non-rebuilding mode. Fish-row selection hints,
chart affordability, shop marks, tier status and payment details keep live updates.
Do not cache a tooltip that reads changing state unless its owner refreshes it.

The outfitter header draws its rarity chips in `ShopHeaderPlugin.render`;
`FishShopDialog.buildHeader` places hotspots from the same
`ShopHeaderPlugin.getChipLeft/getChipTop` offsets. Each chip's tooltip lists the
species aboard from `FishCurrency.countSpecies`, most-held first, as up to two
rows of `FishIcons` with an "xN" count for duplicates. Species past those rows
are summarized as "... and N other species." so the grid never outgrows the
320-pixel tooltip.

Source: `StandardTooltipV2Expandable.addTooltipTo/addTooltipToPrevious`,
`beforeShown` and `advanceImpl` in the knowledge base's `sources-obf/ui.impl.java`.

## Ability tooltips

The ROD, Harpoon and Breach Lamps use ordinary body text at vanilla's 350-unit
width. Keep the purpose paragraph short, group live stats with pad 3, and separate
blocks with pad 10. Highlight values in the normal highlight colour; reserve red
for current failures or explosive-head danger. Do not append module descriptions
from the shop. Show the fitted modules in one `Fitted:` line, in slot order, and each
module's change to targeting or operation on its own line, read from the rig's
combined `TackleEffects`.
Codex mode omits the repeated title and transient availability notices.

Read current values through `UpgradeManager` and the runtime getters. ROD pursuit
radius includes its ring and chase margin; lamp mode adds `Searchlight.getMaxReach()`.
The lamp spot radius is not its distance from the fleet. Fanned Array changes
coverage to an angle and reduces sweep speed. Retained visibility is an upper
limit, since partially illuminated fish fade sooner. Equipment rarity weighting
combines the fitted drone module and lamp upgrade; it is not a probability and
does not include temporary local rumor effects. Harpoon recharge is seconds per
charge, with fractional seconds retained. Its extra catch margin is measured in
screen pixels, matching the collision upgrade; it is not an angular correction.

Sources (0.98a-RC8): `BaseAbilityPlugin`, `SensorBurstAbility` and
`EmergencyBurnAbility` in `sources-api/impl.campaign.java`; the ability-slot
tooltip builder in `sources-obf/ui.newui.java`. The engine appends neither the
CSV description nor cooldown stats after the plugin tooltip.

## Catch item and species tooltips

The specimen, crate and pile tooltips follow vanilla's special-item order through
`FishItemTooltips`:

1. title, in the rarity colour for one species;
2. classification (Rarity, Type);
3. data: the stats grid, the crate's grade list or the pile's species list;
4. provenance and description;
5. "Wanted for";
6. vanilla's `addCostLabel`;
7. action hints.

Use pad 10 between blocks and 3 inside one. The primary right-click hint is
positive and a Control-click tip is gray. Hints are omitted in Codex mode,
which also replaces the title with `addSpacer(-10)`, as
`BaseSpecialItemPlugin` does. They are also omitted unless the stack's cargo is
the player fleet's cargo (`FishItemTooltips.isInFleetCargo`). Grid stacks are
copies, but `CargoData.addStack` points each copy at its source cargo, so the
player side of the cargo, trade and loot screens passes. Market and storage
sides, salvage and every cargo picker fail: picker offers are separate cargo,
and vanilla disables right-click actions in pickers.

Specimen provenance uses the same phrases as request text, `FishLogEntry.Method.phrase`,
`CatchImplement.name` and `SectorRegion.describe()`, so a specimen can be
checked against an ask by eye. Keep the three in step with `FishRequirement`.

The species hover (`FishTooltips`) puts the icon beside the name in a custom
header panel, because `FishIcons.draw` must paint the silhouette of an uncaught
species and `beginImageWithText` only takes a sprite. Its range prose is body
text under a gray "Range:" label, never a whole-paragraph highlight.

Sources: `BaseSpecialItemPlugin.createTooltip/addCostLabel` and the vanilla item
plugins in the knowledge base's `sources-api/campaign.impl.java`; `Misc.addDesignTypePara`
in `sources-api/util.java`.

## Codex size chart

Caught species get a "Size distribution" box below the Codex icon card, as wide
as the right column. `FishSizeChart` draws a normal curve from `lengthMin` to
`lengthMax` with the roll's mean and spread (σ = range/√24). It is lowered so both
ends touch the axis. The actual roll in `FishCatch.roll` is triangular, so the
curve is an approximation, and quality bias is not shown. Gray axis labels give
the range ends. The best catch (`recordLength`) is a highlight-colour stem and dot
labelled on the top row. The latest catch (`lastLength`) is a bright-player stem
labelled on the row below. When they match, or no latest length is logged yet,
one combined "Best and last" label is drawn. The curve, fill, and markers use
`GL_TRIANGLE_STRIP`/`GL_LINE_STRIP` inside pushed attributes. The LazyFont labels
are created on first render, because fonts load on the render thread.

## LunaLib settings

`data/config/LunaSettingsConfig.json` registers this mod's existing 40×40 hook icon
under `catchrelease.iconPath`. LunaSettingsConfigLoader reads the merged file;
LunaSettingsUIModsPanel loads that texture for the mod list. No extra preload is needed.

`LunaSettings.csv` and `config/Setting` must agree on IDs, types, defaults and bounds.
Preset radios are one-shot actions, reset to Keep current after Save. The difficulty Text row
describes saved values, and is rebuilt when its tab is reopened.

Luna's coloured `addPara` calls format `fieldName`, `fieldDescription` and Text-row
`defaultValue` with no arguments. Encode literal percent signs as `%%` in those CSV
cells; they display as one `%`. Headers, stored values and radio choices are not
paragraph formats. `PlayerSettingsChecks` exercises every formatted cell across all tabs.
Sources: LunaSettingsUISettingsPanel.recreatePanel (bundled LunaLib) and
StandardTooltipV2Expandable.addPara (`sources-obf/ui.impl.java`, 0.98a-RC8).

LunaLib retains `LunaSettingsUISettingsPanel.Companion.changedSettings` and mounted controls
after Save. `config/LunaSettingsBridge` synchronizes this mod's pending values and radio,
boolean and numeric controls after applying a preset. Updating only the JSON would let a later
Save restore the old preset or slider values. Do not clear another mod's pending settings.

LunaLib suppresses settings listeners during new-game setup. `PlayerSettings.install()` reloads
on game load as well as application start, so those choices are applied. Dependency source:
`lib/Lazylib_lunalib.zip`, LunaSettingsUISettingsPanel and LunaSettingsLoader.

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

- Shields use red for timed regeneration and blue for Quorum's escort-dependent
  immunity. Purple (`LegendaryShields.SHIELD_ORANGE`, an older name) marks shields
  that do not regenerate on a timer: Moray's one-off shield, Lantern Jack's and
  False Dawn's charges, the Imposter's breakable sections and the awake epic's
  single charge. Moray's shield returns only on encounter reset.
  `LegendaryShields.getShieldColor` supplies the bubble, outline, stored rings and
  deflection flash through the shared mote renderer.
  The Imposter uses `LonglinerShield` instead of the bubble/lens renderer: six purple
  arcs at radius 68, drawn as single 1.5-pixel antialiased lines with normal alpha
  blending and 0.4 opacity, matching the other shields' outer rims. There is no
  filled band, inner edge or end cap. The 4-degree decorative separators remain.
  The six-unit collision depth is independent of line width. Harpoon collision
  closes those separators; only a broken section is passable.
  An intact sector also blocks a head starting inside the ring, including when
  the fish moves over it between callbacks. Broken arcs disappear, including
  from all copies. The real shield turns clockwise at 36 degrees/second; phantom
  shields turn counterclockwise using the same saved angle and damage mask.
  Rotation advances only from the real mote's existing callback.
  `FishEntityPlugin.getShieldAlpha` applies each mote's local lamp fade and viewport
  alpha to all shields and deflection flashes. Diving hides the body, not the shield;
  a lit copy keeps its shield even when the real fish is outside the lamps.
  GL attributes and matrices are restored.
  The bubble lens uses `WaveDistortion.setSize(SHIELD_RADIUS)`: GraphicsLib treats
  size as a radius and draws a sprite twice that wide. Keep the lens inside the
  shield rather than refracting the exposed trail beyond it. Update its position
  after movement and confinement so it stays centered on the current mote.
  Wake-up and section hits use the shared expanding deflection flash at the section
  radius, alongside the same floating notices as the other legendaries.
  No sprite mutation or extra frame script is involved.
  The engine advances custom entities through the unpaused location update
  (`CampaignEngine.advance` / `CustomCampaignEntity.advance`, 0.98a-RC8).
  Full encounter resets are covered in [ARCHITECTURE.md](ARCHITECTURE.md#campaignfishlegendary).
- False Dawn's shield mines and couriers share `LegendaryShields.SHIELD_ORANGE`.
  `DawnShieldTransfer` draws a white-centred glow, a trail in that colour and an
  arrival ring using `Disc`, which preserves GL state. It remains visible outside
  lamp beams; the expanding render range follows it away from the stationary mine.
  Viewport alpha/culling still apply, and pause freezes movement and arrival fade.
  Successful recharge prints "The False Dawn brightens" at the mine, never at the
  fish. Keep that otherwise invisible mine for two seconds after the grant so
  vanilla's one-second notice plus half-second fade can finish. Full shields do
  not print a gain notice. Spent anchors are not harpoon targets.
- Legendary trails retain the red foggy texture, 10-to-1-unit taper, 0.85 opacity,
  normal blending and five-second fade. Epic trails use `FishRarity.EPIC.color` at
  half that opacity (`LegendaryTrail.EPIC_ALPHA_MULT`); each `sample` call passes
  the rarity, and decoys pass legendary. Their transient Luna renderer runs independently of the mote's render
  callback, so an unlit or offscreen head does not hide its illuminated tail. A
  fully faded dive stops sampling, so the strip breaks across the dive.
  Quads are subdivided at most four world units apart; each vertex uses current
  spot/fan coverage and that lamp's Manta flicker, not stored mote visibility or
  retained contact marks. `render` takes `SearchlightAbilityPlugin.snapshotBeams()`
  once per pass. Each `Searchlight.Beam` freezes that frame's position, area, fan
  state and Manta alpha, so vertices only do distance and angle maths. Neighbouring
  quads reuse their shared edge. Keep upgrade, tackle, ownership and haunt lookups
  out of the per-vertex path: profiling showed them taking over 90% of trail
  render time. Lamps off means no visible trail, and an empty snapshot skips the
  geometry entirely. Viewport alpha and culling still apply. Pause freezes sampling and ageing; GL and shared sprite
  state are preserved. No MagicLib trail registration remains for these motes.
  Beam validity reuses the lamp ability's cached pond proximity; vertex sampling
  must not rescan terrain. Cache lifetime is documented under
  [abilities](ARCHITECTURE.md#abilities).
- A mote swallowed by Lantern Jack leaves a burst drawn by `FishEntityPlugin.renderPop`: its own
  `fusion_lamp_glow` sprite in its own rarity colour, additively blended, thrown out to both sides
  of the dash heading and dragged to a halt, with one `Disc` flash at the origin. It is owned by
  the Jack, not the mote, because the mote is expired on the same frame and an effect owned by it
  would never draw. Sub-mote positions are derived from one clock rather than integrated, so there
  is no per-frame state to drift or leak and a pause holds the burst in place. The sub-motes are
  visual only; a real entity here would be edible and harpoonable. Eighteen pieces spread up to
  about 280 units over 0.9 seconds, each with a 0.35× bright core, fading linearly. Its brightness is
  the lamp at the burst (`getBeamStrengthAt(popOrigin)` × 3, clamped) times viewport alpha, not the
  Jack's lamp fade, because the Jack has run on. `getRenderRange` covers the burst while it lasts,
  since the engine culls by the Jack's position. It sets colour, size, alpha and blend on the shared
  sprite before every draw.
- Legendary floating notices use `LegendaryShields.say` and the fish's shield
  colour, never the generic UI highlight. Mine-anchored
  recharge passes the mine/shield colour explicitly; Quorum escort updates use its
  blue shield colour even at the player fleet. Other non-fish anchors and the
  Imposter alert use the legendary mote colour.
  It keeps vanilla's renderer and lifetime, but stacks older labels above newer ones by the font height plus four
  screen units, converted through the current viewport. Older labels drift upward
  first, away from new notices. This
  covers shield hits, the Lantern call, feeding, Quorum feedback and the Imposter alert.
  The stable `CampaignFloatingText.offset` and `label` fields are accessed through
  `ReflectionUtils`; Manta notices still detach before a slot swap.
- Needle Sensor reuses `RippleRingRenderer`: a fixed emission point, 180-unit outer
  radius, 5-second growth/fade and opaque pale blue (`RIPPLE_COLOR`). Its 4-unit
  starting width is multiplied by the viewport's view mult when zoomed out, because
  buried motes spawn beyond the default view and a fixed world width goes sub-pixel.
  It shows no species icon or rarity colour. Render-time checks retire
  rings immediately on lamp activation, refitting, departure, capture or provocation;
  retained lamp reveals suppress them too. Pause freezes both cadence and rings.
  Each ring respects viewport alpha and culling. Load restores the passive controller
  through the lamp ability's existing advance; it does not save timers or renderers.
- Neutrino Lens spikes reuse vanilla's detector drawing (`abilities/neutrino_detector` texture, two bands, pulse and outward extrusion), one pass per rarity tinted with `FishRarity.color`; rare is lifted 35% toward white (`RARE_LIFT`) so it shows against space and the cyan ring. Per-vertex alpha follows that rarity's signal and is zero elsewhere. Constants copied from `GraviticScanAbility.render` (0.98a-RC8) must track vanilla if it changes.
- The Stigmata's combat harpoon rope (`combat/tug/TetherRenderer`) is the mod's only combat-layer
  renderer: one `BaseCombatLayeredRenderingPlugin` per line on `ABOVE_SHIPS_LAYER`, added with
  `engine.addLayeredRenderingPlugin`. The engine culls on the returned entity's location and the render
  radius alone, so the entity is kept at the rope's midpoint with a radius covering the rope; the layer set
  is read once when added. The rope is two feathered `GL_TRIANGLE_STRIP` bands per side (brown edge from
  0.35 to 1.25 px, beige-white core to 0.75 px), sized in screen pixels through
  `convertScreenWidthToWorldWidth(1f)` and widened by a quarter per view mult above 1, with normal blending
  and GL attributes pushed and popped. It flies out at 35%/25% alpha, materialises over 0.25 s on the bite
  with a white flash and five `spawnEmpArcVisual` arcs along the rope (both skipped under
  `Setting.REDUCED_EFFECTS`), and fades over 0.3 s after release. The harpoon head is drawn here with
  normal blending, because a projectile's `bulletSprite` is always additive; the `.proj` colours are
  transparent. The shape is `RopeCurve`, the campaign line's spring and wobble: its middle springs toward the
  chord's midpoint, so it bends only while the ends move and slack shows as wobble, never as a sideways belly.
  Viewport alpha applies, and pause freezes it (the engine does not advance entities while paused).
- Exhaust smoke belongs below the ships. The engine draws every `CombatEngineAPI` particle (`addSmokeParticle`,
  the `addNebula*` calls) after the layered renderer, above every hull and flame; only the engines' own `SMOKE`
  contrails draw underneath (`obf/combat/CombatEngine.java:921-953`). Dark smoke also needs normal blending: of
  the nebula calls only `addNebulaSmokeParticle` has it, the other positive ones are additive and draw nothing in
  dark colours, and the negative ones subtract. The Haul Drive (`combat/tug/TugDriveStats`) lays one MagicLib
  trail per nozzle on `BELOW_SHIPS_LAYER` with MagicLib's `base_trail_heavySmoke` texture and `additive` false,
  starting inside the flame so the hull and flame cover its root. Its pieces slow from 70-100 to 15 and widen
  from 1.2 to 5 times the slot width over 2.45 s, dark soot cooling to grey, at 55% opacity scaled by the effect
  level; a quarter of the ship's velocity carries over. The engines' `LOW_TECH` contrail is recoloured toward
  soot with `fadeToOtherColor`, under its own key so the flame keeps its blend. MagicLib stores a trail's
  `offsetVelocity` by reference, so each piece gets a new vector; a piece added under the id of a trail that is
  still fading joins it, so each activation and each restarted nozzle takes a new id
  (`MagicTrailPlugin.addTrailMemberAdvanced`, `MagicTrailTracker`).
- The Reliquary's recovered-fighter store shows in combat through `maintainStatusForPlayerShip`, the list above
  the player ship's info widget where terrain effects show (`SalvageBaysHullmod.advanceInCombat`, player ship only).
  The list draws each icon as a 25×25 tinted schematic, so the line uses a fixed glyph (vanilla's
  `icon_tactical_bdeck` for now), never the fighter's sprite. Title: the fighter hull's name; data: upper-case
  ready and restoring counts, as vanilla status lines are; a type with nothing ready is drawn as a debuff. A line
  fades 0.25 s after the first unpaused frame it is not maintained, so nothing is removed by hand. One key per
  wing type keeps lines from reshuffling. The lines are maintained every frame but rebuilt from the store every
  0.25 s, kept in the ship's custom data.
- The refit shows a Reliquary's stored fighters as locked built-in bays by swapping a hull-spec copy into the
  member's and the displayed variant while the refit is open (`combat/salvage/RefitStoreView`). The displayed
  variant is reached as LunaLib reaches it, through the refit panel's stable method names, and resynced after
  each change; [ARCHITECTURE.md](ARCHITECTURE.md#combatsalvage) owns the sequence and its safeguards.
  The Salvage Bays tooltip lists the same store with a 32-px fighter sprite per type (`beginImageWithText`),
  values in the highlight colour and no red, since restoring is not a failure.
  The discard panel is a LunaLib refit option (`FighterStoreRefitButton`). Luna hosts it on a background panel
  whose plugin ignores button presses, so the content is a child panel with its own plugin; rows and the footer
  route presses to it and it applies them in `advance`, outside input dispatch. The list and footer are disposable
  panels rebuilt after every change ([Rebuilding lists](#rebuilding-lists)). Destructive actions take a second press
  ("Confirm") on the same button. Luna closes the panel on Esc or a click outside; its `advance` and `onPanelClose`
  do not run when the refit itself closes, so they cannot replace `RefitStoreView`'s tab polling.
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

`LegendaryScarDisplay` draws up to four quiet 24-unit harpoon circles in a horizontal
row, spaced 32 units and centered over the full minigame dialog. The row recenters
when its count changes. Its height reserves the stamp's maximum 2.4-times radius
plus an eight-unit gap above the dialog. Hover regions follow each circle; the
automatic first-scar explanation is centered horizontally above the icon, with an
eight-unit gap between the circle and the bottom of the box. Hover tooltips remain
to the right of their icons. It uses the existing
`shop_harpoon` sprite and `Disc` primitives after the Manta distortion pass. There
are no labels or hover regions during play. The circles, rims and harpoons use
neutral greys at 35% of their result opacity while playing. Failure immediately
uses vanilla's negative-highlight colour for the rims, harpoons and explanation
border, with darker tile fills and normal result opacity, including at the scar
cap. This is red by default and follows vanilla's colourblind setting.
The dialog stays open: each new icon fades in and shrinks over 0.6 seconds,
then plays vanilla's `ui_cargo_supplies_drop`. Only the first scar of a hunt shows
the automatic boxed explanation; after `NOTICE_TIME` (ten seconds) it becomes a hover tooltip.
Later scars enable hover tooltips for every icon as soon as the stamp finishes,
without an automatic box or an extra wait.
A click or fresh keypress during the stamp skips to its completed state. The next
advance plays its sound once and enables the appropriate explanation; that input does not close
the result. After the stamp finishes naturally or is skipped, the next click or
fresh keypress closes it, with the existing "Press any key" prompt beneath the
track. Releases and key repeats cannot skip or dismiss. At four scars no further
stamp, sound or quality-loss notice is added. Ordinary failures retain their short automatic
close. Both dialog-dismiss paths dispose all owned tooltip components.
On a win, scar drawing stops immediately and the display is disposed before the
catch-information panel is created. No scar icons, hover regions or explanations
remain above the minigame; the landed specimen keeps its scar-based quality unless the player disables that penalty.
Scar persistence and quality are covered in
[ARCHITECTURE.md](ARCHITECTURE.md#campaignfishlegendary).

Manta minigames apply `ChromaticAberrationOverlay.renderRegion` after drawing the
running panel. Reduced visual effects lowers its strength. It shifts RGB samples only inside that panel, with pixel/UI scaling,
and never registers a campaign listener. Both custom-dialog dismissal paths dispose
its framebuffer texture, including dev reopens. Result readouts remain undistorted.
The two minigame decoys reuse the real fish's marker style and the same jitter/tell
renderer with their own motion state. Only the real model drives the bar colour and
coverage sounds; decoys do not capture treasure or produce catches. The parity check
reads the model-parameterized `getJitter` body; keep its math aligned with the tools.

The manta haunt's `MantaBackgroundBlackout` draws a normally opaque field at the start of
Luna's `TERRAIN_1` renderer list. Vanilla draws background and stars before campaign
layers (`CampaignState.render` in 0.98a-RC8); Luna draws plugins in registration order.
Keep this field before breach windows and the later entity/UI layers. It must not be
an above-world or above-UI overlay, nor change the location's saved background. Reduced flashes lowers opacity without skipping the formation swap.

- A telegraphed move shows a tell for `MINIGAME_TELL_TIME` before it starts: the drawn marker leans toward the coming move and bobs twice, eased in and out so the path has no corners, peaking at about 5 px. Ordinary jitter calms to 15% over the same envelope (`MINIGAME_TELL_CALM`), the fish's glow flares over the same envelope, and from 35% of the tell a small detached glow mote slips about 34 px toward the move and fades out before the fish follows. The lean and calm are drawn position and are mirrored by the tools' simulation; the flare and mote are overlays, which the tools' `FishPreview` draws in the same colours but the simulation does not model. None of them moves the hit position. `getJitter` also reads `getTellProgress()`, which the parity check maps to the tool's `tellProgress` parameter. `getTell` depends only on its arguments, and `FishingParityChecks` compares its body with `FishingSimulation.tell` as text, so keep the body brace-free. Its size grows with the species' jitter so shaky fish still show it.
- The line sound uses one continuously refreshed UI loop with changing volume. Player settings control its held/released levels and group volume; they do not restart the loop.
- Lantern Jack has two active treasure icons; each keeps its own timer and capture ring. The panel reads `FishingMinigame.getTreasures()` and plays each kind of treasure sound at most once per frame when several spawn or are taken together.
- The loot result has a backdrop clock that starts when the panel is created and
  a list clock that starts after the catch tally. Coin rain uses the backdrop clock.
  Long loot lists are clipped below the chest and title, follow new reward lines,
  and support mouse-wheel scrolling and a draggable scrollbar without dismissing
  the result. Manual scrolling stops automatic following; rewards remain uncapped.
- Every loot row has one layout: icon and name left-aligned, quantity right-aligned as `Nx`, including `1x`.
  The name wraps inside the column left after the quantity. The quantity and icon sit on the name's first
  line, so a wrapped name continues below them. Range-data rows read `Range data: <species>`.

### Fish difficulty tuner

`catchrelease.tools.FishDifficultyTuner.main` in the normal `catchrelease` module
opens a plain Swing authoring window outside the game. Its tool-only
`FishingSimulation` copies the catch model and `FishingMinigamePanel` jitter and tell,
using the existing game constants and types read-only. The game does not call
the simulator. Gear/player/rumor controls are test conditions, not fish
CSV fields. Treasure pursuit is not simulated. Saving and launch arguments are
under [authoring tools](ARCHITECTURE.md#registration-and-lifecycle).

Beginner, Regular and Skilled use delayed screen observations with different
decision intervals, tracking error and anticipation. They do not read future
fish targets. These are repeatable test profiles, not calibrated human skill.
Player (`PlayerModel`) is fitted to the Record play sessions committed in
`fish-recordings/`. On each 1/32-second tick, the display rate of the machine
that recorded them, it compares the fish as seen `fishDelay` steps earlier, led
by the fish's seen speed, with where the bar would stop. It adds aim noise, and
it switches only past separate press and release thresholds and after a minimum
press or pause. `FishAnglerCalibration` (run configuration `Fish Angler
Calibration`, from the mod root, about seven minutes) fits the profile: every candidate plays
the recorded fish with their recorded seeds. For each delay, Nelder-Mead matches
the player's catch rate per rarity, coverage, held share, switches per second,
median press and pause, miss spread and mean catch time. The delay is then
chosen on seeds the search did not use, because the search overfits its own
seeds. The tool prints the `RECORDED` line to paste and compares every angler
with the recording, including a per-fish log score. `FishTunerChecks` requires
Player to stay within 6 points of the recorded catch rate, 2 points of its
coverage and 0.4 switches per second, and to predict the recorded catches
better than every other profile. Refit after new recordings or catch-physics
changes; recordings whose physics changed no longer replay, and both tools warn.
Player is the default Live tuning input and the default Balance results detail
profile, which drives the time, coverage, gap and flag columns, the time chart,
the movement summaries and the advice; the table first sorts by Player catch rate.
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
input, recording save/read/replay and off-screen panel rendering at two sizes. Run `Fishing Parity Checks`
for game/model comparison. Both check classes live beside the tools under
`jars/src/catchrelease/tools` and run headless with Java 17 and the normal module
classpath; see [model ownership](ARCHITECTURE.md#registration-and-lifecycle).
Neither check replaces the full mod build or live in-game QA.

Balance results runs every angler in `FishBalance.SKILLS` with normal losing at fixed 60 Hz, without
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
Experiments run an inclusive, evenly spaced range for one field with identical
seeds and setup. Candidate tables and charts retain completed snapshots.
Applying a completed candidate changes only its tested field and adds a normal
Undo entry; it does not save the CSV. A changed source fish or test setup blocks
applying old candidates. Results keep their tested settings visible.
Reference fish are fixed snapshots grouped by their recorded movement, with
editable notes. Comparing a reference may cross species; it never copies
reference stats onto the selected fish. Named setups store tackle, bar size,
player gain/loss and rumor speed, not fish values. Loading a setup marks
incompatible results stale; out-of-range presets leave the controls unchanged.
References and setups persist in the notebook
described in architecture; checkpoints and cached run results last only for
the current tuner session. New panels use bottom-bar hover/focus help, with no
floating tooltips.

Record play (`FishRecordingPanel`) records a person playing a random run of fish
for balancing against real skill. A session copies the fish values and the Live
tuning test setup when it starts; later edits do not reach it. Each fish comes
from a ticked rarity chosen uniformly, then from that rarity's shuffled species,
so no species repeats before the rarity is used up. By default the marker is the
game's unidentified mote in the rarity colour, as without sonar tackle; the
species appears after the result. `FishPreview` also greys the bar when it
misses the fish and colours the tell overlays, as the game does; the game's
sounds are absent. Input is manual only, with normal losing, and as in game a
press held over from the previous fish does not reel until released and pressed
again. Each fish waits `READY_TIME` before it starts and shows its result, with
the bots' outcomes, for `RESULT_TIME`.
Frames advance only while the preview has focus, its tab is shown and the window
is active. Stop drops the fish in progress, so a session holds finished attempts only.

`FishRecording` writes each finished attempt at once to
`fish-recordings/<start time>/` under the mod root. The folder is not ignored,
so a session can be committed for balancing. `session.properties` holds the
format, plan and setup. `attempts.csv` has one row per fish: its values at full
precision, the setup, seed, bar height, preview track pixels, blind flag and
outcome. `frames.csv` has one row per 1/60-second step: the state on screen when
input was sampled (true, drawn and target fish position, fish velocity, bar,
bar velocity, progress, coverage, active movement, tell progress and direction),
wall-clock milliseconds, and the input applied during that step. Columns are
read by name. The fish never reacts to the bar, so `FishBalance.game` with the
attempt's seed gives every angler the same fish path: `FishRecording.replays`
reproduces an attempt from its seed and inputs, and the summary shows every
angler's results on the same fish and seeds. `summary.txt` keeps that table.

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
