# Catch.Release

Catch.Release is a Starsector fishing mod with unstable-fabric ponds, fishing rigs, collectible species, jobs, and a catch minigame.

## Required workflow

Follow these rules for every Catch.Release task.

### Read the current documentation

1. Read the current remote `CLAUDE.md` in full at the start of every task. A read from an earlier task does not count.
2. Use the `starsector-knowledge` skill before answering or changing anything related to vanilla Starsector. It contains the official API, decompiled internals, and vanilla data for one exact game version. Do not rely on memory.
3. If the skill is incomplete, inspect the read-only archives under `lib/`. Extract them to a temporary directory, never into the repository.
   - `starfarer.api.zip` contains the official game API source.
   - `GraphicsLib.zip`, `Lazylib_lunalib.zip`, and `MagicLib.zip` contain dependency sources and jars.
   - For GraphicsLib, LazyLib, LunaLib, and MagicLib, `lib/` is the primary source because the Starsector skill covers vanilla only.
4. Read [`docs/LORE.md`](docs/LORE.md) before editing or adding any player-facing text. It defines terminology, character knowledge, voice, and the setting's unanswered questions. Do not invent an answer to an unresolved lore question in dialogue or UI text.
5. Read [`docs/RULES.md`](docs/RULES.md) before working on `rules.csv`. Read the relevant parts of [`docs/rules/engine_workflow.md`](docs/rules/engine_workflow.md) and [`docs/rules/command_table.md`](docs/rules/command_table.md) before writing a rule.
6. Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) before tracing features or files.

### Make and record changes

- Use one task branch for the user's message.
- Make one commit per requested change. If one message contains several changes, commit them separately in the requested order.
- Keep each commit message to one short, plain-English summary. Do not include testing notes, agent or model names, co-author trailers, session metadata, links, formatting, or session URLs.
- Update `docs/ARCHITECTURE.md` in the same commit when a change affects file ownership, registration, lifecycle, contracts, or how systems connect. Replace stale statements; do not append a contradictory correction.
- Work only in the current task checkout. A live mod installation, including `C:\Modding\mods\Catch.Release`, and every unrelated checkout are read-only unless the user explicitly asks you to change them.
- Do not deploy or synchronize the mod after merge unless the user explicitly requests it.

### Validate and merge

- Every runtime-affecting final branch must pass the full clean Java 17 build described in [Building](#building).
- Build the exact final remote task-branch revision. Earlier builds, partial builds, IDE analysis, and static checks do not satisfy the gate.
- Missing compilers or dependencies are merge blockers. Documentation-only changes do not require a Java build.
- Open a pull request and merge it when the work is complete. Do not leave finished work on an unmerged branch.
- Use the connected GitHub app to create, inspect, and merge pull requests. Use `gh` only if the app is unavailable. A broken `gh` login is not a blocker when the app works.
- Fetch current remote `master` before branching and again before merging. Integrate intervening changes, push the exact final commit, and verify the merged remote revision.

### Branch cleanup

GitHub normally deletes a pull request's head branch after merge because automatic branch deletion is enabled.

Verify that the branch is gone. Manually remove it when it was pushed without a pull request, its pull request was closed without merging, or it predates the repository setting. If deletion returns HTTP 403, report the branch name and leave it in place; do not work around a credential boundary.

After merge, fast-forward the local task branch to the new `master`. The local task branch may keep its assigned name.

## Comments and documentation

- Comment constraints that code cannot express: engine quirks, required ordering, lifecycle rules, save compatibility, serialization, reflection, obfuscation, units, invariants, non-obvious math, and cross-file coupling.
- Do not comment visible operations. Prefer clear names and structure.
- Do not add Javadoc to self-explanatory classes, methods, fields, parameters, or return values.
- Keep necessary comments short and direct. Comments are not tutorials, release notes, bug histories, or implementation narratives.
- Delete commented-out code.
- Keep a TODO only when it names a concrete unfinished action. Include the blocker when it is not obvious.
- Documentation records current architecture, contracts, entry points, and live hazards. It must not become a development diary, pull-request history, or postmortem.
- Give each fact one home. `docs/ARCHITECTURE.md` is the compact file and feature map; broader stable explanations belong in focused documents.
- Update or remove stale comments and documentation in the same commit as the behavior change.

## Java class layout

- Organize members for reading, not alphabetical sorting.
- Default order:
  1. nested enums;
  2. static fields and initializers;
  3. instance fields and initializers;
  4. other nested types;
  5. constructors;
  6. lifecycle and public entry points in execution or usage order;
  7. supporting helpers near their callers.
- Preserve initializer order when moving code could change behavior.
- Keep a cohesive subsystem together when strict declaration grouping would make the class harder to follow.
- Group fields by responsibility. Put one blank line between groups and no blank lines inside one group.
- Leave one blank line after a class, enum, interface, or record opening brace.
- Use one blank line between methods and major sections. Do not stack empty lines.
- Use short section headers such as `// Rendering` only when names and ordering do not make a long class's groups clear.

## ChatGPT app workflow

This section applies only to ChatGPT working in the ChatGPT app. It is not an instruction for Claude.

### Local and GitHub work

- Use local and pinned GPTs directly through the ChatGPT app. Do not open `chatgpt.com` or use the browser to reach them.
- Use one dedicated local task checkout for clone, fetch, branch, edit, build, commit, integration, and push operations.
- Use local Git for source work and history. Use the connected GitHub app for pull requests, merges, and remote-only fallback when the local filesystem or Git state is unreliable.
- Do not use the browser for repository work.
- If one user message contains several tasks, commit each task separately on the same branch and open one pull request after all of them are complete.
- Compile from a clean output directory in the task checkout. Never compile from or write into a live mod installation.

### Filesystem validation

Before trusting a new or suspect filesystem:

1. Clone current remote `master` into a disposable directory.
2. Confirm its HEAD matches the remote commit.
3. Require a clean `git status`.
4. Run `git fsck --full`.
5. Compare one repository file with its remote blob.
6. Test write, read, and delete in the clone.
7. Create and switch a local branch.
8. Resolve and verify the disposable path before removing it.

If any check fails, keep local PC checkouts read-only and use the GitHub-app-only workflow until the cause is fixed.

### Player-facing text

Every player-facing line must be drafted and audited with the pinned Starsector Editor GPT. This includes dialogue, options, UI labels, messages, tooltips, intel, species and item text, station, weapon, skill and hullmod descriptions, mission text, console output, and any other text visible to the player.

Before each Editor request:

1. Manually confirm that the pinned Editor chat uses High thinking. Do not use Auto, Standard, Fast, or any lower setting. Stop if High is unavailable.
2. Provide the full current `docs/LORE.md`, not a summary.
3. State the exact display context: button, tooltip, intel entry, fish description, weapon description, campaign message, or another named surface.
4. Include all mechanical, layout, and space constraints.
5. For corrective work, provide both the pre-regression and current text.
6. Use separate focused passes for different speakers or surfaces, then request a final integrated voice and context check.

Every Editor prompt must reject hallmark AI prose. Require focused, concrete information, strong character voice, and natural sentence variety. Reject:

- canned contrasts;
- repeated sentence templates;
- excessive triplets;
- vague abstractions;
- polished exposition that explains the subtext;
- false ominous or poetic language;
- filler such as “somehow” and “quietly”;
- routine use of em dashes.

Keep each pass limited to the specified lines and purpose. The Editor may not change routes, tokens, mechanics, layout contracts, or code behavior. Send weak lines back for another High-thinking pass instead of rewriting around the Editor during integration.

The Editor does not replace the lore and context review. Independently compare every returned line with the full current `docs/LORE.md` and the named display context. Return terminology, mechanics, usability, or context problems to the Editor before integration.

## Claude model assignments

This section applies to Claude sessions, not ChatGPT.

Opus 5 in the main session owns planning and shipped code. Clear names and structure carry ordinary intent; comments remain limited to hidden constraints.

| Work | Model |
|---|---|
| Planning, architecture, sequencing, and all Java changes | Opus 5 in the main session |
| Repository search, call tracing, and reading game sources | Sonnet 5 subagents using `model: sonnet` |
| UI and UI-adjacent work, including panels, dialogs, tooltips, renderers, shaders, and sprites | Fable 5 subagents using `model: fable` |

Subagents perform research and scoping, not shipped code. The UI assignment takes precedence whenever the player can see the result.

## Building

### Required compile gate

After the final runtime-affecting commit and before merge:

1. Fetch and build the exact remote task-branch revision.
2. Use a clean, empty output directory.
3. Compile every `.java` file under `jars/src` with Java 17 and the complete dependency set below.
4. Require a successful exit with no compile errors.
5. Record the command, branch commit, Java version, and result in the pull request and final reply.
6. Stop before merge if the build or any dependency is unavailable.

Documentation-only changes are exempt.

The project uses Java 17; `.idea/misc.xml` sets the language level and the source uses switch expressions.

Required compile dependencies:

- `lib/starfarer.api.jar`;
- `starfarer_obf.jar` from the game's `starsector-core` directory;
- `Graphics.jar` from `lib/GraphicsLib.zip`;
- `LazyLib.jar` and `LunaLib.jar` from `lib/Lazylib_lunalib.zip`;
- `MagicLib.jar` from `lib/MagicLib.zip`;
- `lib/lw_Console.jar`, used only to compile optional Console Commands classes;
- `lwjgl-2.9.3.jar`;
- `lwjgl_util-2.9.3.jar`;
- `json-20140107.jar`;
- `log4j-1.2.17.jar`.

These dependencies provide `org.lwjgl.util.vector`, `org.lwjgl.opengl`, `org.json`, and `Global.getLogger()`. GraphicsLib, LazyLib, LunaLib, and MagicLib are also the declared runtime dependencies in `mod_info.json`. `lw_Console.jar` is compile-only because only Console Commands loads those optional classes.

Reference command:

```sh
javac --release 17 -cp "<all jars above>" -d <empty-output> $(find jars/src -name '*.java')
```

## Repository layout

`docs/ARCHITECTURE.md` contains the complete file and feature map, boot order, data registrations, lifecycle contracts, and known engine constraints.

| Path | Contents |
|---|---|
| `jars/src/catchrelease/campaign/ponds/` | Pond terrain and interaction. Ponds are registered in `data/campaign/terrain.json`, with the live plugin under `terrain/`. They are not custom entities. |
| `jars/src/catchrelease/campaign/fish/` | Species, motes, spawning, minigame, jobs, outfitter, map, Codex, Fisherman, tutorial, and aquarium. |
| `jars/src/catchrelease/campaign/crime/` | Harpoon and Breach Lights offences and responses. |
| `jars/src/catchrelease/abilities/` | Breach Lights, R.O.D., and Harpoon rigs. |
| `jars/src/catchrelease/distress/` | Reusable distress-call framework. Read its README first. |
| `jars/src/catchrelease/skillshot/` | Reusable aim-and-fire framework. Read its README first. |
| `jars/src/catchrelease/rendering/` | Campaign shaders, sprite renderers, stencils, and warp grids. |
| `jars/src/catchrelease/memory/` | Persistent upgrades and charges plus session caches. |
| `data/config/custom_entities.json` | Motes, drones, harpoons, legendary props, and the Fisherman map icon. Ponds are not registered here. |

## Core engine constraints

- Church and Path factions do not provide fishers, buyers, or fishing jobs. They provide enforcement and rupture camps. `campaign/fish/FishingTaboo.java` is the only faction list; do not hardcode those IDs elsewhere.
- A custom entity plugin's `init()` must call `super.init()`. `BaseCustomEntityPlugin` stores the inherited `entity` reference used by `getRenderRange()`. Do not shadow it.
- Terrain uses `getPlugin()`, not `getCustomPlugin()`. Set radius through `CampaignTerrainAPI`. Override `getActiveLayers()` and `getRenderRange()`. `BaseTerrain.advance()` affects local fleets unless the plugin opts out.
- `GL_LINE_STIPPLE` restarts for every `GL_LINES` segment, so short segments render solid. `SkillshotUtils` creates dash geometry instead.
- Campaign distortion uses GraphicsLib's `.vert` and `.frag` shaders with campaign-specific plumbing. `DistortionShader` normally stores its list in `Global.getCombatEngine().getCustomData()`, which is unavailable outside combat. `CampaignDistortionRenderer` replaces that storage, calls `ShaderLib.copyScreen` itself, and supplies campaign versions of `unitsToUV` and `isOnScreen` because the originals read the combat viewport.
- A camera-centered object has no camera-distance parallax. Effects that must show depth while centered need independent motion; see `PondDepthField` and `computeDriftUvOffsetPx`.
- `showCustomDialog()` always creates a confirm button and binds Enter and Space to it. Use `showCustomVisualDialog()` with `CustomVisualDialogDelegate` when a panel must have no buttons. Vanilla's `DuelDialogDelegate` uses this pattern.
