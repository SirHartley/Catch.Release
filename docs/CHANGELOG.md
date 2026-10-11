# Writing the changelog

Read this for every implementation task. Update the root `Changelog` in the
same commit as the change; do not leave it for the user to reconstruct from PRs.
This guide covers change notes, not dialogue or character writing. Changelog
entries do not need a Starsector Editor pass.

## Format

Follow [Industrial Evolution's Changelog](https://github.com/SirHartley/Industrial.Evolution/blob/master/Changelog):
newest version first, short feature headings, then one change per line.
Use `Added -`, `Adjusted -`, `Fixed -` or `Removed -`. A `Note -` directly
below an entry can explain a limit or something the player needs to do.

Group a long update by the affected feature: Fisherman, ROD, Harpoon, Outfitter,
Aquarium, and so on. Use General for broad changes. A small patch with only a
few entries does not need category headings.

```text
1.1.a - UNRELEASED

ROD
Added - Upgrade to use drones with breach lights
Adjusted - Drones launch 0.5 seconds apart

Outfitter
Fixed - Selling unmarked commons no longer sells marked fish

1.0.b
Fixed - Ponds no longer crash in systems without a star
```

These are format examples, not new changes to copy into the actual changelog.
Use plain text, not Markdown bullets, PR summaries or commit dumps. Leave a
blank line between categories and versions.

## Wording

- Say what changed and what it does. Name the feature so a player can find it.
- Include important numbers and units. For balance changes, old and new values
  are useful: `Adjusted - Drone launch delay reduced from 1 to 0.5 seconds`.
- For a fix, describe the broken behavior that is now fixed. Do not explain
  the internal call chain.
- Keep it short and ordinary. No claims about seamless experiences, robust
  frameworks, polished workflows, or how thoroughly the agent audited things.
- An occasional aside or a genuine contributor credit fits the Industrial
  Evolution format. Do not invent jokes, credits or testing claims.
- No agent/model names, session links, validation logs or commit hashes.
- Use Development for useful changes to authoring tools or the build. Do not
  sell a refactor or documentation edit as a gameplay feature.

Combine entries when several edits fix the same player-visible problem.
Amend an existing unreleased entry if the task changes that same feature again.
Do not repeat a change once per file, implementation step or PR.

## Versions and upkeep

The newest section holds work for the next release and is marked
`- UNRELEASED`. Keep adding to it across tasks. Do not invent a new version
for every commit. See [RELEASE.md](RELEASE.md#versions) for the major/minor/letter
scheme and ask if the next version has not been chosen after a published release.

Every agent implementation task must add or amend its change notes before
merge. Read-only investigations do not add entries. Check the actual diff:
record only completed changes, preserve earlier entries, and don't list plans
or unfinished work as shipped.

For release, remove `UNRELEASED`, check any compatibility notes, and use exactly
that version as the tag. `- CURRENT PUBLIC VERSION` is optional. Never claim
save compatibility without evidence. Leave older release sections unchanged.

The action uses only the newest matching version section as the release body,
preserving its text and layout. The full Changelog remains inside the ZIP.
There is no separate release summary to keep in sync.
