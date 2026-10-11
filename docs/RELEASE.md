# Releases

Read this when changing the build, preparing a release, or rebuilding the committed
release jar. This covers packaging and version numbers, not gameplay or prose.

For change notes, follow [CHANGELOG.md](CHANGELOG.md).

## Versions

The first release is `1.0.a`. The three parts are:

| Part | Change | Example |
|---|---|---|
| First number | Major release | `1.4.c -> 2.0.a` |
| Second number | Gameplay additions or other changes players will notice | `1.0.c -> 1.1.a` |
| Final letter | Bug fixes, balance changes and small patches | `1.0.a -> 1.0.b` |

Reset the lower parts when a higher one changes. Use lowercase letters and no
`v` prefix. Do not bump a version or publish a tag for every task. Work goes into
the newest, unreleased changelog section until a release is requested.

The tag is the released version. Packaging copies it into `mod_info.json` and
the major/minor/patch fields of `catchrelease.version` inside the ZIP. It does
not edit the working files or commit version changes back to master.

## Build

Use JDK 17 from the task checkout. The game and Console Commands paths are
read-only inputs; nothing is written to the installed mod.

```text
java jars/src/catchrelease/tools/ReleaseBuild.java build "<starsector-core>" "<lw_Console.jar>"
```

The game path may also point to the installation directory containing
`starsector-core`. The tool takes the API and mod dependencies from `lib/`,
extracts only the needed jars into a new directory under `out/`, and compiles
all Java sources there. It checks that the game JSON library has the expected
checked exception type. Release builds require Console Commands so its optional
in-game commands are included.

Commit the generated `jars/release/catchrelease.jar` with changes to runtime Java,
the build tool or bundled compile dependencies. Its manifest records a hash of
those inputs. Packaging rejects a missing or stale jar. Other authoring-tool,
data, artwork and documentation changes do not require a new jar; they still
need the compile gate in [CLAUDE.md](../CLAUDE.md#building) where applicable.

The release jar is separate from IntelliJ's ignored `jars/catchrelease.jar`.
An ordinary IDE build cannot accidentally replace it with tool classes.
Rebuilding the release jar uses an empty class directory, so removed classes
cannot survive from an earlier build.

## Package and publish

```text
java jars/src/catchrelease/tools/ReleaseBuild.java package
java jars/src/catchrelease/tools/ReleaseBuild.java package 1.0.a
```

Without a version, packaging previews the newest changelog section, including
one marked `- UNRELEASED`. With a version, it applies the release checks: an
exact tag such as `1.0.a`, a matching newest changelog section, at least one
change, no duplicate version headings, and no `UNRELEASED` label.

Outputs go to `out/release/`: `Catch.Release-<version>.zip`, release notes and a
version file. The ZIP contains one `Catch.Release/` folder, ready for `mods/`.

- Included: runtime data, graphics, sounds, the runtime jar, mod/version
  metadata, Changelog, and the existing asset-credit notices. Root LICENSE and
  CREDITS files are also accepted if added later.
- Excluded: `lib/`, source, standalone tools/checks, IDE files, Git/agent files,
  guides, backups, source artwork, and `unused/` or `unedited/` assets.
- The jar includes only the mod's runtime classes. Existing dormant classes
  and in-game dev hooks are left alone; `catchrelease/tools/` is excluded.
- The resource allowlist currently accepts CSV, JSON and shaders under data;
  PNG/JPG/JPEG under graphics; OGG/WAV under sounds. Add a format deliberately
  if a new runtime resource needs it. Literal mod asset references in packaged
  data must resolve inside the archive.

Only Git-tracked files are candidates for packaging. Stage new assets and the
release jar before a local preview; commit them before a remote build.

The [Release action](../.github/workflows/release.yml) checks and packages on
branch pushes, pull requests and manual runs. These runs only keep a preview
artifact. A pushed version tag publishes the checked ZIP as a GitHub release.
Its body is that version's Changelog text, in a plain-text block to retain line
breaks and headings. It never generates notes from commits.

Before tagging, finish the changelog, remove `UNRELEASED`, merge the tested
revision, and verify its package check. Push a tag only when the user requests
a release. No deployment or separate download-host upload is part of this
workflow. An existing release is never overwritten; a rerun stops with an
error instead.

This follows [Foilers' release workflow](https://github.com/SirHartley/Foilers-Backgrounds/blob/main/.github/workflows/release.yml)
and its source-checked, committed jar. GitHub needs only Java and the repository
to package; it does not download or distribute game libraries.
