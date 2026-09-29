---
name: dialogue-check
description: Validate Catch.Release dialogue after any change to data/campaign/rules.csv Text or to a rules.csv line quoted in docs/LORE.md, including every batch of a rewrite pass. Runs DialogueCheck (paragraph pacing against vanilla, stage-note fragments, LORE excerpts, tokens, welded sentences, Church and Path rows) and RulesCheck, then the manual prose review the tools cannot do. Use it before committing dialogue, and when asked to audit or compare dialogue with vanilla.
---

# Dialogue check

This skill runs the checks that keep `rules.csv` dialogue at vanilla's paragraph pacing and protect the lines the lore reference depends on. It does not replace the writing guidance: follow the player-facing text route in `CLAUDE.md` (`docs/DIALOGUE.md`, the full `docs/LORE.md`) before drafting. The tool's checks, bands and exemptions are defined in [DIALOGUE.md, Dialogue check](../../../docs/DIALOGUE.md#dialogue-check); do not restate or change them here.

## Run the tools

Work in a temporary directory outside the repository for the base file and build output.

1. Save the base Text for the comparison, normally current remote `master`:

   ```sh
   git fetch origin master
   git show origin/master:data/campaign/rules.csv > "$TMP/base_rules.csv"
   ```

   For one batch of a longer pass, use the last committed state instead (`git show HEAD:data/campaign/rules.csv`), so the findings cover only that batch.

2. Compile the rules tools. `DialogueCheck` needs no dependency jars:

   ```sh
   javac --release 17 -encoding UTF-8 -d "$TMP/rules-tools" jars/src/catchrelease/tools/rules/*.java
   ```

3. Check the changed rows, then the whole file:

   ```sh
   java -cp "$TMP/rules-tools" catchrelease.tools.rules.DialogueCheck . --changed "$TMP/base_rules.csv"
   java -cp "$TMP/rules-tools" catchrelease.tools.rules.DialogueCheck .
   ```

   Both must exit 0. The changed-rows run prints its measures for reference only, because a pass edits a biased set of rows; the whole-file run gates them.

4. Run `RulesCheck` as described in [RULES.md, Rules check tool](../../../docs/RULES.md#rules-check-tool). It looks up command classes, so it needs the full module build and the compile jars from `CLAUDE.md` Building, not the rules package alone. It must report 0 errors, with no more warnings than the base.

## Act on the findings

- **`stage-note`:** rewrite the fragment as a sentence with a subject, and a reason where there is one ("He takes a drink before answering."), or cut it.
- **`excerpt`:** decide which side is right. If the row improved, update the `LORE.md` quotation in the same commit, and check that the surrounding explanation still fits it. If the quoted line was the better one, restore the row. Never leave a quotation that no row contains.
- **`reference`:** undo the change unless the user explicitly asked to change that Church or Path row. If they did, say so in the pull request.
- **`token`:** every removed or added token needs a reason you can state in the pull request. An added pronoun token needs a person in that dialogue context. Otherwise restore the token.
- **`welded`:** split the joined clauses back into sentences inside the merged paragraph, as in [LORE.md, Rhythm and length](../../../docs/LORE.md#rhythm-and-length). Leave it only when the longer sentence is a speech tag or reads naturally aloud.
- **`alternation`, `crowded`, `wall`:** apply [LORE.md, Rhythm and length](../../../docs/LORE.md#rhythm-and-length):
  - merge gestures that only fill the space between lines;
  - break a paragraph at scene-setting, at a closing event and where the speaker changes subject.
- **A measure outside its band:** read the rows it comes from and fix them there.
  - Too many short or speech-only paragraphs means fragmentation.
  - Too few paragraphs per variant, or too few narration-only paragraphs, means dense blocks.
  - Never widen a band, edit `vanilla-pacing-baseline.txt` by hand or add an exemption to get a pass. A band change is a style decision for the user, recorded in `DIALOGUE.md`.

## Review what the tools cannot see

Read every changed row in full, in context with the rows before and after it. Check it against the [common mistakes](../../../docs/LORE.md#common-mistakes-and-corrections) and the speaker's voice profile in `LORE.md`, in particular:

- prop tics and stock replies;
- echo beats;
- rhetorical contrasts;
- narrator asides;
- a speaker who has started to sound like every other speaker.

Keep the characters colourful. A clean tool run says nothing about voice.

Also confirm, for a prose-only pass:

- only the Text column changed;
- row count and order are unchanged;
- the CSV still round-trips as described in [RULES.md](../../../docs/RULES.md);
- straight quotes only.

## Report

In the pull request, record:

- the commands and results of both tools;
- the whole-file measure table;
- each token or excerpt change with its reason.

For a rewrite pass, show the user two or three complete before/after rows in the final reply, not single lines. Say plainly that the text has not been tested in game unless it has.
