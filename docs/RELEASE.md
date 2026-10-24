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
not edit the working files. The only change committed back to master is the
version file update described under [Version Checker](#version-checker).

### Version Checker

`catchrelease.version` (registered in `data/config/version/version_files.csv`) is read by
Version Checker, LunaLib's bundled checker and mod managers such as triOS. It follows
[Industrial Evolution's setup](https://github.com/SirHartley/Industrial.Evolution/blob/master/IndEvo.version):
**master's copy is the online version file**, and it must always name the newest release.

- `masterVersionFile`: the raw master file, `raw.githubusercontent.com/SirHartley/Catch.Release/master/catchrelease.version`;
- `modVersion`: the newest released version;
- `directDownloadURL`: that release's ZIP, `releases/download/<version>/Catch.Release-<version>.zip`;
- `changelogURL`: the raw master `Changelog`.

Packaging stamps the version numbers and `directDownloadURL` into the ZIP's copy, and
writes the same stamped file to `out/release/catchrelease.version`. After the release
action creates the GitHub release for a tag, it commits that stamped file to master as
"Update the version file to <version>". Master therefore advertises a version only once
its download exists. Do not bump this file by hand for unreleased work. If the commit
step fails, for example because branch protection blocks the action, commit the stamped
file to master manually; until then, installed copies are not told about the release.

There is no forum thread yet; add `modThreadId` when one exists.

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

Rebuild and commit `jars/release/catchrelease.jar` only when the user explicitly
asks, normally before a release. Ordinary changes to runtime Java, the build tool
or dependencies leave the committed jar stale; they still need the compile gate in
[CLAUDE.md](../CLAUDE.md#building). The jar's manifest records a hash of those
inputs. Packaging a tagged release rejects a missing or stale jar. A preview
(branch push, pull request or local run without a version) accepts a stale jar
and prints a warning, so it packages the last rebuilt runtime.

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
- The resource allowlist currently accepts CSV, JSON, shaders, `.ship` hulls, `.wpn` weapons, `.proj` projectiles, `.system` ship systems and `.variant` variants under data;
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
