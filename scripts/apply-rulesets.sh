#!/usr/bin/env bash
# Applies the branch rulesets in .github/rulesets/ to GitHub and sets the repository merge
# options. Needs `gh` logged in as a repository admin. Safe to run again: each ruleset is
# updated by name, or created when it does not exist yet.
#
#   scripts/apply-rulesets.sh           # back up, apply, show the result
#   DRY_RUN=1 scripts/apply-rulesets.sh # only show what would change
set -euo pipefail

cd "$(dirname "$0")/.."

REPO=${REPO:-Burst-Into-Fire/bandcore}
# The main ruleset was first created by hand with this name; it is renamed to "main".
LEGACY_MAIN_NAME="Protecao da main"

run() {
  if [ "${DRY_RUN:-0}" = "1" ]; then
    echo "would run: $*"
  else
    "$@"
  fi
}

id_of() {
  gh api "repos/${REPO}/rulesets" --jq ".[] | select(.name == \"$1\") | .id" | head -n 1
}

backup="rulesets-backup-$(date +%Y%m%d-%H%M%S).json"
{
  echo "["
  first=1
  for id in $(gh api "repos/${REPO}/rulesets" --jq '.[].id'); do
    [ "$first" = "1" ] || echo ","
    gh api "repos/${REPO}/rulesets/${id}"
    first=0
  done
  echo "]"
} > "$backup"
echo "Backed up the current rulesets to ${backup} (not committed; keep it until you are happy)."

for file in .github/rulesets/*.json; do
  name=$(sed -n 's/^  "name": "\(.*\)",$/\1/p' "$file")
  id=$(id_of "$name")
  if [ -z "$id" ] && [ "$name" = "main" ]; then
    id=$(id_of "$LEGACY_MAIN_NAME")
  fi
  if [ -n "$id" ]; then
    echo "Updating ruleset '${name}' (${id}) from ${file}"
    run gh api --silent -X PUT "repos/${REPO}/rulesets/${id}" --input "$file"
  else
    echo "Creating ruleset '${name}' from ${file}"
    run gh api --silent -X POST "repos/${REPO}/rulesets" --input "$file"
  fi
done

# Merge commits are needed to promote development to main; feature branches are rebased into
# development (the rulesets enforce which method each branch accepts). Squash is not used.
echo "Setting merge options: merge commit and rebase on, squash off"
run gh api --silent -X PATCH "repos/${REPO}" -F allow_merge_commit=true -F allow_rebase_merge=true \
  -F allow_squash_merge=false

echo
echo "Rules now in force:"
for branch in main development; do
  echo "  ${branch}: $(gh api "repos/${REPO}/rules/branches/${branch}" --jq '[.[].type] | join(", ")')"
done
