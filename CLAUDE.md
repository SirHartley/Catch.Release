# Catch.Release

Starsector mod: fishing. Ponds, drones, fish, and a catch minigame.

## Workflow

- **Read this file in full before every Catch.Release task.** Refresh the current remote
  `CLAUDE.md` at the start of each task; a read from an earlier task does not carry over.
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
- **Never sync or modify a live/local mod copy or another checkout unless the user explicitly
  requests it.** Work is written only in the current task repository. External installs and
  checkouts - including `C:\Modding\mods\Catch.Release` - may be inspected read-only for
  pre-merge auditing, but never pulled, copied into, checked out, cleaned, or otherwise changed
  as an automatic post-merge step. After a successful merge, leave every external copy untouched;
  the user owns deployment and synchronization unless they explicitly delegate it.
- **Always open a pull request and merge it** once work is done. Do not leave finished work
  sitting on the branch.
- **Use the connected GitHub app for pull requests and merges.** The `gh` CLI is only a fallback;
  a missing or invalid CLI login must never block PR creation, inspection, or merging when the app
  is connected.
- **A merged branch does not stay standing.** GitHub does this by itself: the repository has
  *Automatically delete head branches* switched on, so merging a pull request retires its branch
  without anybody asking. Nothing to do in the ordinary case - merge and it is gone. The rule is
  written down because it is worth knowing why the repository looks tidy, and because the setting
  covers exactly one thing: a branch that was the head of a pull request, at the moment that
  request is merged. It does not touch a branch pushed and never opened as a request, one whose
  request was closed rather than merged, or anything merged before the setting was turned on.
  Those are the cases an agent still has to see to, and the one that made them worth a rule: the
  repository reached a hundred and fifty leftovers, and from the outside a finished branch and one
  still being worked on look the same, so the pile could only be sorted out by auditing every
  branch individually. Whoever leaves such a branch behind clears it up in the same session -
  tidying up after somebody else is nobody's job in particular. The session's own assigned task
  branch is the standing exception: it is reset onto the new master and reused under the same name.
  If a deletion is refused, say so in the reply and name the branch. Some sessions hold credentials
  that can create and update refs but not delete them; the refusal is an HTTP 403 on the delete
  push, from GitHub rather than the proxy, while an ordinary push still succeeds. That is a
  permission boundary, not something to route around - report it and leave the branch, so it is a
  known leftover rather than a silent one.

## Comments and documentation

- **Comment hidden constraints, not visible operations.** Keep the reason that code cannot express:
  engine or API quirks, required ordering and lifecycle, save or serialization compatibility,
  reflection and obfuscation, units and invariants, non-obvious math, or cross-file coupling.
  Delete comments that merely translate the next statement into English.
- **Let names carry ordinary intent.** Do not add Javadoc to self-explanatory classes, methods,
  fields, parameters, or return values. Keep necessary comments short, usually one line, in a
  direct working-programmer voice. Source comments are not essays, tutorials, release notes, or
  a record of how the implementation was developed.
- **Do not preserve dead code in comments.** Delete commented-out code. Leave a TODO only when it
  names a concrete remaining action; include the constraint or blocker when that is not obvious.
- **Documentation describes the current truth.** Record architecture, contracts, entry points,
  and live gotchas. Do not turn documentation into a development diary, PR history, bug
  postmortem, or implementation narrative. Replace stale prose in the same commit instead of
  appending a correction that contradicts it.
- **Give each fact one home.** Keep `docs/ARCHITECTURE.md` as the compact file and feature map,
  and put wider stable context in the relevant focused document. Do not repeat the same
  explanation across documentation and source comments unless a local warning is needed to keep
  code safe.
- **Remove or update stale words with the code.** A changed behavior does not leave behind an old
  comment or document claim. Player-facing prose and rules still follow the lore and rules
  workflows above.

## Java class layout

- **Order members for the reader, not for a sorter.** The default is nested enums, static fields
  and initializers, instance fields and initializers, nested types, constructors, lifecycle or
  public entry points in the order they run or are used, then supporting helpers near their
  callers. Keep the relative order of initializers when changing it could alter behavior.
- **Functional flow may beat the default.** A cohesive subsystem can stay together when splitting
  it across declaration categories would make the class harder to follow. The result should read
  naturally from setup through operation to details.
- **Keep spacing deliberate.** Use one blank line between sections and methods, none between
  simple related fields or enum constants, and never stack empty lines. Do not leave an empty line
  just inside an opening or closing brace.
- **Use section headers sparingly.** A short header such as `// Rendering` is useful when a long
  class contains distinct subsystems. Do not add one where member names and order already make the
  grouping obvious.

## ChatGPT app workflow (ChatGPT only)

This section applies only when ChatGPT is working on Catch.Release in the ChatGPT app. It is
**not an instruction for Claude**.

- **Use local and pinned GPTs directly through the ChatGPT app.** ChatGPT can switch to GPTs
  such as the Starsector Editor inside the app. Do not open `chatgpt.com` or use the browser
  to reach them.
- **Keep every PC checkout read-only when ChatGPT is in the app.** Local files may be inspected,
  but never edit, patch, create, delete, move, or format repository files there. Do not perform
  local Git operations either: no local branch, checkout, staging, commit, pull, push, merge, or
  post-merge sync. A working filesystem sandbox does not change this boundary: earlier local
  writes corrupted files, so the connected GitHub app is the only repository write path.
- **Use the connected GitHub app for the entire repository workflow.** Read the current remote
  files and refs there, create the task branch there, make each file update there, inspect the
  remote diff there, and open and merge the pull request there. Do not fall back to PowerShell,
  a local checkout, the `gh` CLI, the browser, or another local write path.
- **Preserve the repository's task/commit discipline through the GitHub app.** One task is one
  commit. If a message contains several tasks, commit them separately on the same task branch in
  the requested order, and open and merge the pull request only after the whole message is done.
- **Work from, and finish against, remote state.** Refresh remote `master` before branching and
  check it again before merging, account for intervening remote changes, verify the merged remote
  commit, and leave every local checkout and live mod install untouched.
- **Draft and audit dialogue with the Starsector Editor GPT in the ChatGPT app.** Give the editor
  the complete current remote `docs/LORE.md`, not a summary, before asking it to revise a line.
  For corrective passes, also supply both the pre-regression and current dialogue so stronger
  characterization is restored rather than flattened by generic tightening. Use focused faction,
  Fisherman and Crablobab passes, then give the editor the final sheet for voice QA. Its prose
  still has to pass the rules safety checks below; the editor does not get to alter routes, tokens
  or mechanics. **Never treat the Editor's draft or QA as the lore check:** independently compare
  every returned line against the complete current remote `docs/LORE.md` before integration, and
  send any terminology or mechanics conflict back to the Editor for correction. Apply the
  approved result through the GitHub app only.

## Model assignments

Opus 5, in the main session, writes the code. Planning and code are the same job here. Clear
names and structure carry ordinary intent; comments are reserved for the hidden constraints
listed above.

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
- `lw_Console.jar` - Console Commands, in `lib/` directly. A compile dependency only since the
  console commands moved into `jars/src`; the mod itself still runs without the console installed,
  because nothing but the console's own loader ever loads those classes
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
