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

| Path | What lives there |
|---|---|
| `jars/src/catchrelease/campaign/ponds/` | The pond. **Terrain**, not a custom entity - registered in `data/campaign/terrain.json`, plugin under `terrain/`. |
| `jars/src/catchrelease/campaign/fish/` | Fish specs, mote entities, spawning, and the catch minigame. |
| `jars/src/catchrelease/abilities/` | The searchlight and the R.O.D. - drones, motes, the ring. |
| `jars/src/catchrelease/skillshot/` | Reusable aim-and-fire ability framework; see its README. |
| `jars/src/catchrelease/rendering/` | Shader and sprite renderers, stencils, warp grids. |
| `data/config/custom_entities.json` | Motes and drones. The pond is **not** here any more. |

## Gotchas

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
