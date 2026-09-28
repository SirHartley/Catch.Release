#!/usr/bin/env bash
# PostToolUse hook: runs DialogueCheck when data/campaign/rules.csv or docs/LORE.md changed, whether through Edit or
# Write or through a Bash command such as a script, sed or git. Failures go to stderr with exit 2, which Claude Code
# feeds back to the model; warnings go back as context. The change itself is not undone.
# Checks, bands and exemptions are in docs/DIALOGUE.md, "Dialogue check".

cat >/dev/null
root=${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}
cd "$root" 2>/dev/null || exit 0
[ -f data/campaign/rules.csv ] && [ -f docs/LORE.md ] || exit 0

# Only a change to either file since the last run triggers a check, so ordinary tool calls cost one checksum.
cache="${TMPDIR:-/tmp}/catchrelease-dialogue-check"
mkdir -p "$cache"
state=$(cat data/campaign/rules.csv docs/LORE.md | cksum | cut -d ' ' -f 1)
stamp="$cache/state-$(printf '%s' "$root" | cksum | cut -d ' ' -f 1)"
[ "$(cat "$stamp" 2>/dev/null)" = "$state" ] && exit 0
printf '%s' "$state" >"$stamp"

if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    echo "dialogue-check hook: javac or java is not on PATH; run the dialogue-check skill by hand." >&2
    exit 1
fi

# The rules tool package has no dependencies. Its compiled classes are cached by source content.
sources=(jars/src/catchrelease/tools/rules/*.java)
key=$(cat "${sources[@]}" | cksum | cut -d ' ' -f 1)
out="$cache/$key"
if [ ! -f "$out/catchrelease/tools/rules/DialogueCheck.class" ]; then
    rm -rf "$out" && mkdir -p "$out"
    if ! javac --release 17 -encoding UTF-8 -nowarn -d "$out" "${sources[@]}" >"$out.log" 2>&1; then
        echo "dialogue-check hook: the rules tools did not compile:" >&2
        cat "$out.log" >&2
        rm -rf "$out"
        exit 1
    fi
fi

base=$(mktemp)
trap 'rm -f "$base"' EXIT
git show HEAD:data/campaign/rules.csv >"$base" 2>/dev/null || cp data/campaign/rules.csv "$base"

changed=$(java -cp "$out" catchrelease.tools.rules.DialogueCheck . --changed "$base" 2>&1)
changed_status=$?
whole=$(java -cp "$out" catchrelease.tools.rules.DialogueCheck . 2>&1)
whole_status=$?
clean() { grep -v '^Picked up JAVA_TOOL_OPTIONS'; }

if [ "$changed_status" -eq 0 ] && [ "$whole_status" -eq 0 ]; then
    warnings=$(printf '%s\n' "$changed" | clean | grep '^WARN ' | head -n 40)
    [ -z "$warnings" ] && exit 0
    # Warnings do not fail the check; they reach Claude as context with a reason required in the pull request.
    text="DialogueCheck warnings for rows changed since HEAD. Give each a reason in the pull request or fix it (dialogue-check skill):
$warnings"
    escaped=$(printf '%s' "$text" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/ /g' | awk '{printf "%s\\n", $0}')
    printf '{"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":"%s"}}\n' "$escaped"
    exit 0
fi

{
    echo "DialogueCheck failed after this edit (docs/DIALOGUE.md#dialogue-check). Fix it as the dialogue-check skill describes:"
    echo "--- rows changed since HEAD"
    printf '%s\n' "$changed" | clean | head -n 60
    if [ "$whole_status" -ne 0 ]; then
        echo "--- whole file"
        printf '%s\n' "$whole" | clean | grep -v '^\(ERROR\|WARN\) ' | head -n 20
    fi
} >&2
exit 2
