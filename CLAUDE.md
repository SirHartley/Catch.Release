# Catch.Release

Starsector mod: fishing. Ponds, drones, fish, and a catch minigame.

## Workflow

- **Ask the `starsector-knowledge` skill first, and for anything vanilla ask it only.** It is
  the full API, the decompiled internals and the whole stock `data/` folder for one exact game
  build, indexed - and it covers the obfuscated code and the csv formats, which nothing else
  does. Never answer from memory: the API changes between versions and a remembered signature
  is the expensive kind of wrong.
- **Go to the real sources when the skill runs short.** They live in the repo under `lib/`,
  zipped: `starfarer.api.zip` is the game's own API source, and `GraphicsLib.zip`,
  `Lazylib_lunalib.zip` and `MagicLib.zip` are the dependency mods, source and jars both. Unzip
  to a temp directory to read them - do not unpack them into the repo. Two cases earn it: the
  skill answered but not fully, or the change is knotty enough to want a whole subsystem read
  end to end. The dependency mods are the exception to the rule above - the skill is vanilla
  only, so for GraphicsLib, LazyLib, LunaLib and MagicLib `lib/` is not the fallback, it is the
  only source there is.
- **Any player-facing line starts in [`docs/LORE.md`](docs/LORE.md).** What the fabric, a breach,
  a pattern, the ROD and the Fisherman are, who in-universe calls them what, and the one tone rule -
  whimsy on the surface, nothing good underneath, and never say so. Dialogue, species descriptions,
  tackle blurbs and intel notes all answer to it. It also lists what the mod does that the fiction
  has not explained yet; do not invent an answer in a row, settle it there first.
- **Any work on `rules.csv` starts in [`docs/RULES.md`](docs/RULES.md).** The language as
  verified against decompiled engine source - triggers, memory scopes, the operator table, the
  truthiness gate, how scoring actually works - plus an appendix of the traps this repo has
  already paid for. Two deeper references sit beside it and are quoted verbatim from the modder
  who wrote them: [`docs/rules/engine_workflow.md`](docs/rules/engine_workflow.md) for the
  engine end to end, and [`docs/rules/command_table.md`](docs/rules/command_table.md) for the
  command vocabulary. Read the part you are about to use before writing a row, not after the
  row misbehaves.
- **One commit per change.** Several changes, or several things asked for at once, get split
  into separate commits rather than piled into one.
- **Every change updates [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).** Not only when a
  package gains or loses a file - any change that moves what a file does, what registers it,
  or how the pieces fit lands in the map in the same commit. A change is not done while the
  map still describes the old shape.
- **Always open a pull request and merge it** once work is done. Do not leave finished work
  sitting on the branch.
- **Use the connected GitHub app for pull requests and merges.** The `gh` CLI is only a fallback;
  a missing or invalid CLI login must never block PR creation, inspection, or merging when the app
  is connected.

## Model assignments

Opus 5, in the main session, writes the code. Planning and code are the same job here - the
reasons live in the javadoc, and a class handed to somebody else comes back explaining what it
does instead of why it is built that way.

Subagents are for legwork that does not produce shipped code:

| Task | Who does it |
|---|---|
| Planning and writing code - architecture, sequencing, every Java file | Opus 5, in the main session |
| Crawling and scoping - finding files, tracing call sites, reading the game sources | Sonnet 5 subagents (`model: sonnet`) |
| Anything UI or UI-adjacent - panels, dialogs, tooltips, renderers, shaders, sprites | Fable 5 subagents (`model: fable`) |

The UI rule still wins where it applies: if a change touches something the player looks at, it
goes to Fable 5.

## Building

Java 17 (`.idea/misc.xml` sets the language level; the source uses switch expressions).

Compiles against the game and library jars:

- `starfarer.api.jar` - in `lib/`; `starfarer_obf.jar` is not, and still comes from the game
  install's `starsector-core`
- `Graphics.jar` (GraphicsLib), `LazyLib.jar`, `LunaLib.jar`, `MagicLib.jar` - the declared
  dependencies in `mod_info.json`, each inside its zip in `lib/`
- `lwjgl-2.9.3.jar`, `lwjgl_util-2.9.3.jar`, `json-20140107.jar`, `log4j-1.2.17.jar` - needed
  for `org.lwjgl.util.vector`, `org.lwjgl.opengl`, `org.json` and `Global.getLogger`

```
javac --release 17 -cp "<those jars>" -d <out> $(find jars/src -name '*.java')
```

## Layout

**[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) is the full map** - every package, every file, one
line each, plus the boot order and the index of what is registered by data rather than by code. Read
it before going looking for something, and update it with every change - see the workflow rule
above.

The short version:

| Path | What lives there |
|---|---|
| `jars/src/catchrelease/campaign/ponds/` | The pond. **Terrain**, not a custom entity - registered in `data/campaign/terrain.json`, plugin under `terrain/`. |
| `jars/src/catchrelease/campaign/fish/` | Most of the mod: specs, motes, spawning, the catch minigame, jobs, the shop, the map filter, the codex. |
| `jars/src/catchrelease/campaign/crime/` | What harpooning a fleet costs you, and what running the breach lamps over an inhabited world costs you. |
| `jars/src/catchrelease/abilities/` | The three rigs - searchlight, R.O.D., harpoon. |
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
