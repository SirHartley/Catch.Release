# Writing the changelog

Read this for every implementation task. Update the root `Changelog` in the
same commit as the change; do not leave it for the user to reconstruct from PRs.
This guide covers change notes, not dialogue or character writing. Changelog
entries do not need a Starsector Editor pass.

## When to write an entry

- Every implementation task adds or amends its entries in the current
  unreleased section before merge. Read-only investigations add nothing.
- Check first whether the feature has been released: look for it in the
  published sections (every version heading without `UNRELEASED`).
  - Not released yet: later changes to it get no entry of their own. Edit its
    `Added -` entry so it describes the feature as it now stands, or leave it
    alone if it is still accurate.
  - Released: the change gets its own entry.
- Check the actual diff. Record only completed changes, preserve earlier
  entries, and never list plans or unfinished work as shipped.
- When a change needs no entry, say why in the PR ("no changelog entry: the
  second slot is new in this version").

## Format

Follow [Industrial Evolution's Changelog](https://github.com/SirHartley/Industrial.Evolution/blob/master/Changelog):
the fixed Known issues and Notes sections, then versions, newest first.

```text
1.1.a - UNRELEASED

General
Fixed - Crash when a system has no star

ROD
Added - Upgrade to use drones with breach lights
Adjusted - Drone launch delay 1s -> 0.5s

Outfitter
Fixed - Selling unmarked commons no longer sells marked fish

1.0.b
Fixed - Ponds no longer crash in systems without a star
```

These are format examples, not changes to copy into the actual changelog.

- **Version heading:** the version alone on its line, optionally followed by
  ` - UNRELEASED`. The published version carries ` - CURRENT PUBLIC VERSION`
  and the author's caps save line (`SAVE COMPATIBLE WITH 1.0 - NOT SAVE
  COMPATIBLE WITH 0.9`); never write or change that line unasked.
- **Category headings:** in-game feature names (Fisherman, ROD, Harpoon,
  Outfitter, Aquarium), General first. A small patch with only a few entries
  needs none. Use Development for useful changes to authoring tools or the
  build; do not sell a refactor or documentation edit as a gameplay feature.
- **Entries:** one change per line, starting `Added - `, `Adjusted - `,
  `Fixed - ` or `Removed - `, in that order inside a category.
- **Notes:** a `Note - ` line directly below an entry gives a limit or
  something the player must do. A console command the player has to run goes on
  its own line under it.
- Plain text only: no Markdown bullets, code fences, PR summaries or commit
  dumps. A blank line between categories and between versions.

## Every line

- Short: about 60 characters is typical. Stay under 80; go past 100 only for a
  new feature that needs its numbers.
- Capital letter after the dash, no full stop on a one-clause line. A second
  sentence is fine for a new feature; then both sentences end in a full stop.
- In-game names and capitals: `Breach Lights`, `Glitter Lens`, `Tug Harpoon`.
- Parentheses carry the short extra: `(was 3)`, `(vanilla game limitation)`,
  `(new game)`, credits.
- No semicolons, no curly quotes and no hyphen used as a dash. Straight quotes
  for in-game strings.
- What the player sees, never the call chain or class names.
- No claims about seamless experiences, robust frameworks or how thoroughly
  anything was audited. No agent or model names, session links, validation
  logs or commit hashes.

## Entry forms

**Added.** Name, colon, what it does: `Added - Glitter Lens: Lens Array module
that doubles bycatch under Breach Lights`. Settings: `Added - Setting to adjust
surfaced lamp-fish speed`. A new ability of an existing feature: `Added - The
Neutrino Lens shows quest fish as gold spikes`.

**Adjusted.** Numbers as old `->` new, with the unit: `Adjusted - Tug Harpoon
charges 2 -> 3`. Otherwise a direction word (Reduced, Increased, Halved,
Doubled): `Adjusted - Halved the chest chance on rupture catches`. Say buff or
nerf when the number hides it: `(this is a buff)`. Sub-features in one category
get a prefix: `Adjusted - Breach Lights: Reduced flux cost`.

**Fixed.** What the player saw, in these forms, most used first:

1. `Fixed - The Outfitter no longer opens on a locked shelf with an empty list`
2. `Fixed - Distance is now properly calculated into job rewards`
3. `Fixed - Crash when a planet has no storage`
4. A short noun phrase for small things: `Fixed - Typos`.

Exploits: `Fixed - Can no longer duplicate fish by opening the crate twice`.
Bugs caused by another mod name that mod. Use `again` only when an older
section lists the same fix. A partial fix says so: `(won't crash anymore, but
source of error not known)`. A bug report link fits at the end when the user
gave one.

**Removed.** The thing, then the reason or replacement in parentheses:
`Removed - Breach Coupler (withdrawn for now)`.

**Credits.** Real people only, in the author's forms: `(thanks, J2Greene)`,
`(PR'd by abel1502, thank you!)`, `courtesy of Pyrophage`,
`(reported by RoadTrain)`. Never an agent.

**The author's own lines** carry commentary: admissions, hedges, jokes, caps,
mixed spellings. Keep them verbatim; never tidy, shorten or merge them unasked.
Never imitate that voice: agent lines have no first person, jokes, swearing,
caps, exclamation marks outside a credit, or hedges about whether a fix works
(untested status goes in the PR).

**Old forms not to copy:** `Changed - `, lines without the dash
(`Fixed tooltip - again`), `Code adjustment - `, `Effect - `, `Cont. - `,
`From dev release:`, indented sub-lines, `NOTE` in caps, `Known issue - `
inside a version, reporter tags (`[Wisp] - `), combined headings (`2.2.a/b`).

## Amending versus adding

Combine entries when several edits fix the same player-visible problem. Amend
an existing unreleased entry if the task changes that same feature again. A
feature that did not exist in the last release gets one `Added -` entry that
describes it as it now stands; later work on it in the same version edits that
entry and never gets its own `Fixed -` or `Adjusted -` line. An unreleased
feature removed again before release loses its `Added -` entry and gets no
`Removed -` line. Changes to features that shipped in an earlier release get
their own entries. Do not repeat a change once per file, implementation step
or PR.

The Stigmata is new in `1.1.a`, which is unreleased. Two later tasks fix its
harpoon and raise its charges.

Wrong:

```text
1.1.a - UNRELEASED
Added - Tug Harpoon: 2 charges, 400 kinetic damage
Fixed - The Tug Harpoon no longer damages friendly ships
Adjusted - Tug Harpoon charges 2 -> 3
```

Right: both changes are folded into the one `Added -` entry.

```text
1.1.a - UNRELEASED
Added - Tug Harpoon: 3 charges, 400 kinetic damage, none to friendly ships
```

After `1.1.a` is released, the next change gets its own line:

```text
1.1.b - UNRELEASED
Adjusted - Tug Harpoon charges 3 -> 4
```

## Versions and upkeep

The newest section holds work for the next release and is marked
`- UNRELEASED`. Keep adding to it across tasks. Do not invent a new version
for every commit. See [RELEASE.md](RELEASE.md#versions) for the major/minor/letter
scheme and ask if the next version has not been chosen after a published release.

For release, remove `UNRELEASED`, check any compatibility notes, and use exactly
that version as the tag. Never claim save compatibility without evidence. Leave
older release sections unchanged.

The action uses only the newest matching version section as the release body,
preserving its text and layout. The full Changelog remains inside the ZIP.
There is no separate release summary to keep in sync.
