# Catch.Release

Catch.Release is a Starsector fishing mod with unstable-fabric ponds, fishing rigs, collectible species, jobs, and a catch minigame.

## Required workflow

These instructions apply to all coding agents. App-specific tools and Claude model assignments are scoped below.

Reviews, explanations, audits, and proposals are read-only unless the user requests changes. For implementation, finish the authorized edit, commit, validation, pull request, and merge steps. Use reasonable assumptions for routine details; ask when a missing decision would materially change the scope or behavior. A request to stop or propose only overrides the implementation workflow.

### Read the current documentation

1. Read the current remote `CLAUDE.md` in full at the start of each new task. Within the same task, reuse the version already read unless the instructions change. Read new or changed instruction files before following them.
2. Use the `starsector-knowledge` skill before answering or changing anything related to vanilla Starsector. It contains the official API, decompiled internals, and vanilla data for one exact game version. Do not rely on memory.
3. If the skill is incomplete, inspect the read-only archives under `lib/`. Extract them to a temporary directory, never into the repository.
   - `starfarer.api.zip` contains the official game API source.
   - `GraphicsLib.zip`, `Lazylib_lunalib.zip`, and `MagicLib.zip` contain dependency sources and jars.
   - For GraphicsLib, LazyLib, LunaLib, and MagicLib, `lib/` is the primary source because the Starsector skill covers vanilla only.
4. For any player-facing text work, start with [`docs/DIALOGUE.md`](docs/DIALOGUE.md). It owns the Editor procedure, presentation checks, and links to lore and technical rules. `LORE.md` owns setting definitions, absolute knowledge limits, prose style, character instructions and explanatory examples; read it before drafting. Documentation editing and technical-only routing changes do not require an Editor prose pass.
5. Read [`docs/RULES.md`](docs/RULES.md) before working on `rules.csv`. Read the relevant parts of [`docs/rules/engine_workflow.md`](docs/rules/engine_workflow.md) and [`docs/rules/command_table.md`](docs/rules/command_table.md) before writing a rule.
6. Use [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) to locate the relevant owner and connections before tracing code. Follow only the references needed for the task.

### Make and record changes

- Use one task branch for the user's message.
- Make one commit per requested change. If one message contains several changes, commit them separately in the requested order.
- Keep each commit message to one short, plain-English summary. Do not include testing notes, agent or model names, co-author trailers, session metadata, links, formatting, or session URLs.
- Update the affected documentation in the same commit, following [Documentation upkeep](#documentation-upkeep). Do this without a separate user request.
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

## Documentation upkeep

Update documents automatically as part of each relevant change, not by a background job. Before committing, check which entries the diff makes stale and update those entries in that commit.

| Document | Owns | Update when / how |
|---|---|---|
| `AGENTS.md` | Codex discovery | Keep it a short pointer to this file. Do not copy workflow policy into it. |
| `CLAUDE.md` | Task scope, tools, commits, PRs, builds, comments, document maintenance | Replace changed policies here and update links. Keep provider-specific instructions explicitly scoped. |
| `docs/ARCHITECTURE.md` | Technical routing: owners, registrations, data flow, lifecycle and cross-system constraints | Update the affected route or contract; remove obsolete owners. Use exact paths and symbols. No lore, writing advice, release history, or duplicate policy. |
| `docs/DIALOGUE.md` | Master for text work: Editor procedure, prose review, UI/intel conventions, dialogue usability and presentation hazards | Update the relevant convention when a text feature or workflow changes. Link to lore and technical rules instead of copying their explanations. |
| `docs/LORE.md` | Setting definitions, absolute knowledge limits, prose style, character instructions, examples, terminology and information-release order | Update only for approved fiction/characterization changes or requested meaning-preserving edits. Define concepts before using them, or link to their definitions. Preserve the force of facts and prohibitions, their reasons, and useful correct/incorrect examples. Do not turn invented dialogue into canon. |
| `docs/RULES.md` | Project rules-engine guidance | Update verified technical contracts and local links. Keep command syntax, examples and evidence accurate. |
| `docs/rules/*.md` | Vendored technical references | Preserve the upstream text and provenance. Record project-specific corrections in `docs/RULES.md`, with source evidence, rather than silently altering the reference. |
| Framework `README.md` files | Integration and extension instructions for that framework | Update when its API, registration, dependencies, paths or lifecycle changes. |

Give each fact one home. Move useful detail to its proper owner and link to it; do not keep a second copy in architecture. After moving a section, repair inbound links and check that no requirement or technical constraint was lost. Keep architecture compact and readable; do not create a parallel human version to maintain.

Write documentation in ordinary English. Use direct verbs and concrete nouns; remove slogans, inflated claims, repeated contrasts, and commentary about how important the document is. Keep identifiers exact. Tables and short technical fragments are appropriate in architecture. Examples demonstrate a technique, not a sentence pattern to repeat everywhere.

`LORE.md` is an explicit author reference, not player-facing fiction. Never apply the mod's ambiguity, understatement or omission of explanations to the document itself. Distinguish established facts, character knowledge, narration restrictions and deliberately unanswered questions. State absolutes directly; do not replace “cannot” or “never” with a preference to “avoid.” Explain what to write, what not to write, why the distinction exists and how to apply it. Shortening must not remove those instructions or examples. After reorganizing lore, compare the old and new versions for facts, prohibition strength, exceptions, examples and definition order; word retention alone is not a sufficient check. Keep text-production workflow in `DIALOGUE.md` and link to lore for prose and characterization rules.

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

## Codex and ChatGPT app tools

These tool choices apply to Codex and ChatGPT app sessions. They do not select a model or alter Claude's assignments.

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

## Agent behavior

- Keep the user's selected model. In Codex/Astra sessions, the main agent owns planning, integration, and shipped code. Delegate bounded research only when the user or applicable instructions authorize it and the app supports it. Do not copy Claude-specific model names into Codex calls.
- Treat repository instructions as project requirements and skill guidance as scoped reference material. Follow explicit user changes within the app's safety and permission limits. If a skill blocks work or changes its scope, identify the exact instruction and explain the effect.
- Give brief progress updates and concise final results. Put detailed evidence in the pull request; avoid repeating the whole implementation narrative in chat.

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
5. Record the command, branch commit, Java version, and result in the pull request. The final reply links the PR and states the result and any untested behavior.
6. Stop before merge if the build or any dependency is unavailable.

Documentation-only changes are exempt. Changes to runtime data, including text-only CSV changes, still require this gate unless the user explicitly grants an exemption.

Run the checks appropriate to the affected behavior as well as the required build. After they pass, repeat or broaden checks only for changed inputs, failures, or a concrete unresolved concern. Do not add tests that merely restate the implementation.

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

## Repository navigation

Start at [ARCHITECTURE.md](docs/ARCHITECTURE.md). It maps code and data owners, startup order, lifecycle constraints, and framework entry points. Start text work at [DIALOGUE.md](docs/DIALOGUE.md).
