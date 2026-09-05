# Dialogue and player-facing text

Start here for dialogue, options, intel, tooltips, UI labels, messages, species and item descriptions, mission copy, and console output.

- [LORE.md](LORE.md): setting, terminology, character voices, knowledge limits, and information-release order.
- [RULES.md](RULES.md): CSV format, rule execution, commands, memory, and technical validation. Read its linked references and the required rules skills for rules work.
- [ARCHITECTURE.md](ARCHITECTURE.md): code and data owners, registrations, and lifecycle connections.
- [CLAUDE.md](../CLAUDE.md#documentation-upkeep): task workflow and automatic documentation upkeep.

This document governs text production and presentation. It does not override lore or define rules-engine syntax. Preserve user-supplied dialogue verbatim unless the user asks for a rewrite.

## Starsector Editor

Every new or revised player-facing line must be drafted and audited with the pinned Starsector Editor GPT. This includes small labels and console messages, not just spoken dialogue. Reading or quoting existing text, editing project documentation, and changing technical routing without changing prose do not require an Editor request.

Use the pinned chat directly through the app; do not reach it through a browser. The coding agent owns implementation, state, and technical validation. The Editor reviews prose only.

### Prepare the request

1. Confirm manually that the Editor is set to High thinking. Do not use Auto, Standard, Fast, or a lower setting. If High is unavailable, stop the prose work and report it; continue independent technical work where possible.
2. Supply the full current `LORE.md`, not a summary. Identify its revision or content hash. Within the same Editor session, reuse an unchanged supplied version; resend it if the file changed or the session no longer has it. Recheck High after a session or model-setting change, or when its state is uncertain.
3. Name the surface, speaker, audience, current quest stage, and information the player already has. Include the exact lines and surrounding exchange.
4. Give the mechanics, required facts, tokens, highlight phrases, layout limits, option order, and intended return or exit. Separate these constraints from prose the Editor may change.
5. For a regression, include the prior and current wording. If the prior version cannot be recovered, say so rather than inventing it.
6. Use focused passes for different speakers or surfaces, followed by one integrated review of the final exchange.

Require concrete language, distinct voices, and natural sentence variety. Reject canned contrasts, repeated templates, gratuitous triplets, vague abstractions, explanations of subtext, false ominousness, filler, and habitual em dashes.

### Integrate and review

The Editor may revise only the requested prose. It may not change rule IDs, triggers, conditions, commands, tokens, row order, mechanics, facts, or layout contracts. Return weak or inaccurate lines for another High-thinking pass; do not silently replace its text with a new coding-agent draft.

Independently check the result against the full current lore and the actual display context. Check the complete final exchange once. Reopen a passed review only when an integration change or a concrete issue affects it. Technical rules and UI checks remain the coding agent's responsibility.

## Writing

Write people doing their jobs. An officer thinks about orders and liability; a cook thinks about prep and service. Their vocabulary and choices should fit that work. Supporting characters are usually competent.

Describe observable actions before interpreting them. “Your ops chief checks the mass figure twice” gives the player more room than an explanation of how disturbed the chief feels. Use specific objects when they matter; avoid lists of invented technical nouns.

Characters need different rhythms. Mix sentence lengths without repeating a fixed paragraph pattern. Let dialogue sound spoken, with enough room to finish a thought. Character-specific voice in `LORE.md` and information the player needs take precedence over general advice to shorten a passage. Crablobab should remain expansive; the Fisherman should have warmth and patience.

Choose a useful detail for each beat. One is often enough; this is not a numerical limit on descriptions or conversations. Do not explain the detail's emotional meaning afterward. Use dashes for real interruptions, not every aside.

### Dialogue and options

- Break teaching into related questions or short exchanges rather than a long lecture.
- Let choices express the player's intent. Avoid paraphrasing the preceding paragraph or forcing a theatrical personality onto the captain.
- Offer different plausible responses where they matter. Use Continue when there is no meaningful decision.
- Repetition can belong to a character: a product name, a regulation, or the plain-coated buyers' exact requested species. Do not have every speaker repeat the player's question.
- Humor should come from the situation and the people. Avoid explaining a joke after it lands.
- Keep the narrator's voice consistent when something strange happens. The lore defines what observers can notice; do not announce horror or tell the player how to feel.

### Surface requirements

| Surface | Required information |
|---|---|
| Tooltip | What it does first; flavor afterward. State restrictions and costs plainly. |
| Schematic reward card | Exact upgrade tier or compatible module, its effect, purchase-permission status, and later credit-and-fish price. A schematic is not the equipment itself. Resolve the same icon as the shop, including category fallback. |
| Quest offer | Complete demand, destination, deadline, reward preview, and unusual conditions before acceptance. Do not repeat the whole reward section in both character speech and the shared pitch. |
| Accepted intel | Purpose, special terms, exact requirements and live progress, deadline, rewards, and navigation. Match the shared bar-job layout. |
| Fish description | Observable form, precise deviations, and a useful handling or culinary consequence. Do not invent evolutionary history or explain every pattern. |
| Colony description | A functioning facility: visitors, staff, tanks, access and maintenance. Follow the lore's setting constraints. |
| Receipt | Actual items, credits or knowledge granted, after the hand-in prose. Use the shared receipt path rather than a second invented payment sentence. |

The Outfitter tabs are Upgrades, Equipment, and Extras. Upgrade levels are tiers. Module categories are Harpoon Tips, Drone Cores, and Lens Arrays; use “rig module” across categories. `Tackle` remains an internal/save identifier. Canonical terminology belongs in `LORE.md`; obtain labels from the owning UI/data definitions rather than maintaining another vocabulary table here.

### Review questions

Does the line fit the speaker's job and knowledge? Does it tell the player what they need to do? Does its voice still belong in Starsector without the anomaly? Does narration explain a joke, prescribe an emotion, or reveal a mystery the lore leaves unresolved?

Remove needless emphasis, vague adjectives and repetitive comparisons such as “with the expression of someone who…”. Watch for theatrical reactions, constant ellipses, ominous smiles, and a clever closing line on every exchange. Cut a sentence only if it adds no information, character, pacing, or useful context.

Examples illustrate one technique. Do not turn them into templates for every speaker or screen.

## Dialogue behavior and presentation checks

These are project acceptance criteria. Use `RULES.md` for how to implement and validate them.

### Navigation

- Every reachable state needs an intentional next step or exit, including insufficient cargo, unavailable stock, decline, cancellation, and completed hand-in.
- Reading one informational option must return to the appropriate question menu so the others remain available. It must not accept, decline, pay, or end the exchange.
- Accepted-job comms must show the active task's hand-in, reminder, not-yet, and exit choices, not the original offer's accept/decline menu.
- Keep generated targets, prices, rewards and case details stable when leaving and reopening an unaccepted offer. Display saved values; prose must not reroll them.
- Show hand-in prose and actual reward receipts before moving to a new offer or ending the interaction. Multi-stage hand-ins use a Continue handoff before the next stage's accept/decline choices.
- Peaceful fleet resolutions leave a visible Leave option. Explicit flee and cut-link choices may exit directly. Technical teardown must use the proper fleet path in `RULES.md`.
- Clear host options before custom panels and restore the prior menu once on close. Check both confirm and cancel paths.

### Fisherman questions

- Business uses the dedicated `CatchReleaseFisherOptions` trigger; custom-panel cancellations resume through `CatchReleaseFisherResume`.
- Unasked questions precede answered ones. Answered questions remain selectable at the end, coloured with vanilla `Misc.getGrayColor()`, not Common fish-rarity beige.
- Keep paging and navigation within the nine-option limit. Terminal answers return to questions through Something else; the question-menu exit returns to business.
- “Baha” is introduced by its answer, not assumed in the preceding question label.
- Bycatch becomes a question topic after the first relevant catch. Tutorial disclosure and special-topic precedence follow the saved progression and `LORE.md`.

### Colours, rewards and sidebars

- Fish names use their rarity colours. Non-fish rewards, places and final hand-in choices use the established quest highlight. Do not substitute the positive-gain colour for the last fish in a list.
- Highlight arguments follow displayed occurrence order. Repeat the argument when the same fish name appears again. Check first, middle and last items, including token-expanded names.
- Colour a menu option after it has been added. Use the existing later, condition-matched colour row rather than relying on an earlier script.
- Use shared reward cards for initial offers, counteroffers and follow-ups. Receipts report actual grant results, including a learned-range reward converted to its saved credit fallback.
- Standard `FishReward` receipts use Gained; fleet contracts use Received. Do not change an established receipt label as incidental prose cleanup.
- Remote offers and location reminders use `QuestDialogMap` below the portrait. Use the stored target; do not display a map for a local or unresolved destination. Remove only the temporary map/marker owned by that preview after acceptance, leaving, or switching surfaces.
- Restore Crablobab's person card after merchandise options; do not leave the bar image active.
- Every live fishing quest, task and rumor offers Open fishing map. The hover explains its destination/filter. Request hovers retain specimen restrictions: a geographic range alone does not satisfy size, grade, origin, time or method requirements.
- Intel icons distinguish rupture-only, lamp-only and mixed requirements; POINT tutorial entries use the tutorial icon. Check the shared icon resolver instead of copying a portrait.
- Unknown range data must remain visibly unknown in a forced map/planner handoff. Check the no-data state and reset path, not just a fully unlocked dev view.

### Camp proof

The offer must say that the player needs the camper gone and any qualifying fish caught from that exact rupture after acceptance. Existing cargo cannot prove later work. When the camper leaves, intel names the remaining catch requirement. When proof is aboard, update progress and remove the rupture mark; restore the requirement and mark if proof is lost. Hand-in must validate the same origin and timestamp restrictions as intel and the picker, then show the actual rewards.

## Technical handoff

Before a rules edit, read `RULES.md` and the required rules references. Keep prose review separate from CSV round-trip checks, column counts, token/command validation, state tracing, and runtime tests. The Starsector Editor is never a technical rules validator.

Trace each affected route through initial contact, questions, acceptance, return, insufficient cargo, hand-in, next round, cancellation and exit as applicable. Include overlapping flags and save/load state. Do not invent a dependency on an unavailable visualizer. Report what static checks establish and what still requires in-game QA.

## Maintenance

Update the relevant section in the same commit when a text workflow, presentation contract or reusable dialogue convention changes. Keep lore facts in `LORE.md`, engine semantics in `RULES.md`, and owner/connection information in `ARCHITECTURE.md`. Repair cross-links after moving a section. Do not append a task history or copy complete dialogue trees into this guide.
