#!/bin/sh
# Validates a branch name against docs/CONTRIBUTING.md: <type>/<issue-number>-<kebab-case>.
#
#   validate-branch.sh <branch-name>

branch=$1
BRANCH_RE='^(feature|bugfix|hotfix|docs|refactor|perf|test|chore)/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$'

case "$branch" in
  main | development | HEAD | dependabot/*) exit 0 ;;
esac

if ! printf '%s\n' "$branch" | grep -Eq "$BRANCH_RE"; then
  printf "ERROR: Invalid branch name '%s'.\n" "$branch" >&2
  printf '  %s\n' "Use <type>/<issue-number>-<kebab-case-description>, e.g. feature/42-add-equal-split" \
    "Types: feature, bugfix, hotfix, docs, refactor, perf, test, chore" >&2
  exit 1
fi
