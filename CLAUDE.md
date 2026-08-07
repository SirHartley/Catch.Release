# Catch.Release

Starsector mod: fishing. Ponds, drones, fish, and a catch minigame.

## Workflow

- **Always use the `starsector-knowledge` skill** for anything touching the game: API
  signatures, data file formats, game internals, obfuscated code. Never answer from memory -
  the API changes between versions.
- **One commit per change.** Several changes, or several things asked for at once, get split
  into separate commits rather than piled into one.
- **Always open a pull request and merge it** once work is done. Do not leave finished work
  sitting on the branch.

## Model assignments

Split the work by kind of task rather than doing all of it in one place. The main session
plans; subagents do the legwork.

| Task | Who does it |
|---|---|
| Planning - architecture, sequencing, deciding what changes | Opus 5, in the main session |
| Crawling and scoping - finding files, tracing call sites, reading the game sources | Sonnet 5 subagents (`model: sonnet`) |
| Writing and editing code | Opus 4.8 subagents (`model: opus`) |
| Anything UI or UI-adjacent - panels, dialogs, tooltips, renderers, shaders, sprites | Fable 5 subagents (`model: fable`) |

The UI rule wins over the code rule: if a change touches something the player looks at, it
goes to Fable 5 even though it is also code.

## Building

Java 17 (`.idea/misc.xml` sets the language level; the source uses switch expressions).

Compiles against the game and library jars, none of which live in this repo:

- `starfarer.api.jar`, `starfarer_obf.jar` - from the game install's `starsector-core`
- `Graphics.jar` (GraphicsLib), `LazyLib.jar`, `LunaLib.jar`, `MagicLib.jar` - the declared
  dependencies in `mod_info.json`
- `lwjgl-2.9.3.jar`, `lwjgl_util-2.9.3.jar`, `json-20140107.jar`, `log4j-1.2.17.jar` - needed
  for `org.lwjgl.util.vector`, `org.lwjgl.opengl`, `org.json` and `Global.getLogger`

```
javac --release 17 -cp "<those jars>" -d <out> $(find jars/src -name '*.java')
```

## Layout

**[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) is the full map** - every package, every file, one
line each, plus the boot order and the index of what is registered by data rather than by code. Read
it before going looking for something. Update it when a package gains or loses a file.

The short version:

| Path | What lives there |
|---|---|
| `jars/src/catchrelease/campaign/ponds/` | The pond. **Terrain**, not a custom entity - registered in `data/campaign/terrain.json`, plugin under `terrain/`. |
| `jars/src/catchrelease/campaign/fish/` | Most of the mod: specs, motes, spawning, the catch minigame, jobs, the shop, the map filter, the codex. |
| `jars/src/catchrelease/campaign/crime/` | What harpooning a fleet costs you, and what running the breach lamps over an inhabited world costs you. |
| `jars/src/catchrelease/abilities/` | The four rigs - searchlight, R.O.D., harpoon, depth bomb. |
| `jars/src/catchrelease/skillshot/` | Reusable aim-and-fire ability framework; see its README. |
| `jars/src/catchrelease/rendering/` | Shader and sprite renderers, stencils, warp grids. |
| `jars/src/catchrelease/memory/` | Anything that has to survive a save - upgrades, charges, caches. |
| `data/config/custom_entities.json` | Motes and drones. The pond is **not** here any more. |

## Gotchas

- **The Church and the Path never like fishing.** A rupture is a hole into hyperspace and the
  catch comes from the wrong side of it, so neither flag has fishers, buyers or bar jobs - only
  patrols that stop you and cells that sit on the water. `campaign/fish/FishingTaboo.java` is the
  one list; do not hardcode the faction ids anywhere else.
- **Terrain differs from custom entities.** `getPlugin()` rather than `getCustomPlugin()`,
  radius only settable through `CampaignTerrainAPI`, `getActiveLayers()` and
  `getRenderRange()` throw unless overridden, and `BaseTerrain.advance` sweeps local fleets
  for terrain effects unless the plugin opts out.
- **`GL_LINE_STIPPLE` is useless for short segments.** GL restarts the pattern at every
  segment of a `GL_LINES` batch, so anything shorter than one dash draws solid. Dashes are cut
  as geometry in `SkillshotUtils` instead.
- **GraphicsLib's distortion works in the campaign.** The shaders were never the problem - only
  the plumbing: `DistortionShader` keeps its list in `Global.getCombatEngine().getCustomData()`,
  which does not exist outside a battle. `CampaignDistortionRenderer` rebuilds that plumbing
  against the campaign and drives GraphicsLib's own `.vert`/`.frag` files directly. Only two
  ShaderLib helpers are unusable out there - `unitsToUV` and `isOnScreen`, both because they
  read the zoom off the combat viewport - and both are two lines against the campaign viewport.
  The screen has to be copied by hand (`ShaderLib.copyScreen`); in combat something else already
  has.
- **A camera snapped to a thing kills that thing's parallax.** `ParallaxUtil`'s camera term is
  computed from the distance to the middle of the screen, which is zero for whatever the camera
  is centred on. Anything that has to read as deep while centred needs motion of its own - see
  `PondDepthField` and `computeDriftUvOffsetPx`.
- **`showCustomDialog` always builds a confirm button.** The delegate can rename it and can
  drop the cancel button beside it, but cannot ask for neither, and enter and space are wired
  straight to it. For a panel that wants no buttons use `showCustomVisualDialog` with a
  `CustomVisualDialogDelegate` - it hands the panel the whole frame and leaves the keyboard
  alone. Vanilla hosts its own minigame that way (`DuelDialogDelegate`).
