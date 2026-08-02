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
| `jars/src/catchrelease/helper/reflection/` | `ReflectionUtils` - the game blocks `java.lang.reflect` for mod code, so it goes through `MethodHandles`. |
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
- **`showCustomDialog` always builds a confirm button.** The delegate can drop the cancel
  button but not that one; it has to be taken back out of the panel by reflection, and enter
  and space stay wired to it after it is gone.
