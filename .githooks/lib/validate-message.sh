#!/bin/sh
# Validates a commit message (or only its header) against docs/CONTRIBUTING.md.
#
#   validate-message.sh <file>                  full message: header, blank line, issue footer
#   validate-message.sh --header-only <text>    header only (pull request titles)
#
# Shared by the commit-msg and pre-push hooks and the validate-standards workflow.

TYPES='feat|fix|docs|style|refactor|perf|test|chore|ci|revert'
HEADER_RE="^(${TYPES})(\([a-z0-9]+(-[a-z0-9]+)*\))?!?: [a-z0-9][^[:cntrl:]]*[^.]$"
FOOTER_RE='^(Closes #[0-9]+|Ref: #[0-9]+)[[:space:]]*$'
MAX_HEADER=72

error() {
  printf 'ERROR: %s\n' "$1" >&2
  printf '  %s\n' "Example: feat(expense): add equal split" "See docs/CONTRIBUTING.md#commit-standards" >&2
  exit 1
}

check_header() {
  header=$1
  if ! printf '%s\n' "$header" | grep -Eq "$HEADER_RE"; then
    error "Invalid header: '$header'. Use a valid type, a kebab-case scope (optional) and a lowercase description without a trailing period."
  fi
  if [ "${#header}" -gt "$MAX_HEADER" ]; then
    error "Header has ${#header} characters; the limit is ${MAX_HEADER}."
  fi
}

if [ "$1" = "--header-only" ]; then
  check_header "$2"
  exit 0
fi

file=$1
[ -f "$file" ] || error "Message file not found: $file"

# Ignore comment lines, as git does when it writes the commit.
message=$(grep -v '^#' "$file")
header=$(printf '%s\n' "$message" | sed -n '1p')

# Messages written by git itself are not validated.
case "$header" in
  "Merge "* | "Revert \""* | "fixup! "* | "squash! "* | "amend! "*) exit 0 ;;
esac

check_header "$header"

second=$(printf '%s\n' "$message" | sed -n '2p')
if [ -n "$second" ]; then
  error "Leave a blank line after the header."
fi

if ! printf '%s\n' "$message" | grep -Eq "$FOOTER_RE"; then
  error "Missing issue reference. Add a footer line 'Closes #N' or 'Ref: #N'."
fi
