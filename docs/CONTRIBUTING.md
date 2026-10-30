# Commit and Branch Standards

> Official commit, branch and pull request guide for **bandcore**.

**Version:** 1.0 | **Status:** Proposed | **Last updated:** October 2026

[Back to README](../README.md)

---

## Table of Contents

1. [Overview](#overview)
2. [Commit Standards](#commit-standards)
3. [Branch Standards](#branch-standards)
4. [Pull Requests](#pull-requests)
5. [Testing Expectations](#testing-expectations)
6. [Full Flow](#full-flow)
7. [Automations](#automations)
8. [Practical Examples](#practical-examples)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)
11. [Initial Setup](#initial-setup)

---

## Overview

bandcore uses **Conventional Commits** with short-lived branches that always trace back to an issue.

### Objectives

- Readable and organized commit history
- Clear traceability: issue, branch, commits, pull request, deploy
- Easier code review and changelog generation
- Standards enforced by tools, not by memory

### Differences from the API_6S standard

bandcore reuses the API_6S standard with three deliberate changes:

| Topic | bandcore | Why |
|---|---|---|
| Long-lived branches | `main` and `sprint-*` | `main` only | Every push to `main` is deployed (RNF26), so `main` must always be deployable. Sprints are tracked with milestones and issues. |
| Issue reference | `Closes #N`, `Ref: #N` or a bare `#N` | `Closes #N` or `Ref: #N` | A line that starts with `#` is a comment for Git when the message is written in an editor, so a bare `#N` can be silently stripped. |
| Remote validation | Rulesets only | GitHub Action `validate-standards` | Local hooks can be skipped with `--no-verify`. The Action checks the branch, the PR title and every commit in the PR. |

---

## Commit Standards

### Format

```
<type>(<scope>): <description>     <- REQUIRED

[optional body]

Closes #<issue-number>             <- REQUIRED (or: Ref: #<issue-number>)
```

Write commit messages in English, like the rest of the repository.

### Accepted types

| Type | Emoji | Usage | Example |
|---|:---:|---|---|
| `feat` | ✨ | New feature | `feat(expense): add equal split` |
| `fix` | 🐛 | Bug fix | `fix(sale): reject sale above stock` |
| `docs` | 📚 | Documentation | `docs: update getting started` |
| `style` | 🎨 | Formatting only, no logic | `style(web): fix indentation` |
| `refactor` | ♻️ | Code change that neither fixes a bug nor adds a feature | `refactor(auth): extract token service` |
| `perf` | ⚡ | Performance improvement | `perf(expense): index list filters` |
| `test` | 🧪 | Tests | `test(settlement): cover multi-currency balances` |
| `chore` | 🔧 | Build, dependencies, housekeeping | `chore(deps): bump spring boot` |
| `ci` | 🔄 | Continuous integration | `ci: filter workflows by folder` |
| `revert` | ↩️ | Revert a commit | `revert: revert "feat(sale): add refund"` |

The emoji column is only a visual aid. Do not put emojis in commit messages.

### Structure

#### 1. Type (required)

Must be one of the types above.

```
OK   feat(auth): add sign in
NO   feature(auth): add sign in
NO   new(auth): add sign in
```

#### 2. Scope (optional, recommended)

The affected area, in lowercase kebab-case. Suggested scopes:

| Scope | Area |
|---|---|
| `api`, `web` | Whole application, when no feature fits |
| `auth`, `band` | Sign in, tokens, bands, members, roles |
| `expense`, `settlement` | Expenses, splits, balances, settlement payments |
| `merch`, `sale` | Products, stock, sales, refunds, event closing |
| `report` | Summaries, reports, exports |
| `db` | Migrations and database structure |
| `docker`, `ci`, `deps`, `docs` | Infrastructure, pipeline, dependencies, documentation |

```
OK   feat(expense): add custom split
OK   fix(db): add stock check constraint
NO   feat(Expense_Split): add custom split
```

#### 3. Description (required)

- Starts with a lowercase letter or digit
- Imperative mood ("add", not "added" or "adds")
- No trailing period
- The whole header has at most 72 characters

```
OK   feat(auth): add sign in
NO   feat(auth): Add sign in
NO   feat(auth): added sign in
NO   feat(auth): add sign in.
```

#### 4. Body (optional)

Separated from the header by a blank line. Explain the **why**, not the what.

#### 5. Footer (required)

Every commit references an issue. The hook rejects commits without it.

```
Closes #42     closes the issue when the pull request is merged
Ref: #42       references the issue without closing it
```

Use `Ref:` for commits in the middle of a larger issue and `Closes` on the last one.

#### Breaking changes

Add `!` after the type or scope and explain it in the body:

```
feat(api)!: require currency on every amount

BREAKING CHANGE: amounts without a currency code are rejected.

Closes #88
```

### Automatic validation

The `commit-msg` hook rejects commits that:

- Have an invalid type, scope, description or header length
- Have no blank line after the header
- Have no `Closes #N` or `Ref: #N` line

Merge commits and the automatic message of `git revert` are not validated.

---

## Branch Standards

### Format

```
<type>/<issue-number>-<kebab-case-description>
```

### Branch types

| Type | Usage | Example |
|---|---|---|
| `feature` | New feature | `feature/42-add-equal-split` |
| `bugfix` | Bug fix | `bugfix/57-fix-negative-stock` |
| `hotfix` | Critical fix in production | `hotfix/63-api-down` |
| `docs` | Documentation | `docs/71-update-architecture` |
| `refactor` | Refactoring | `refactor/75-extract-money-type` |
| `perf` | Performance | `perf/80-optimize-expense-queries` |
| `test` | Tests | `test/84-add-isolation-tests` |
| `chore` | Build, CI, dependencies | `chore/90-update-dependencies` |

### Rules

A valid branch has a valid type, the issue number and a kebab-case description.

```
OK   feature/42-add-equal-split
OK   bugfix/57-fix-negative-stock
NO   feature/add-equal-split        (no issue number)
NO   feature/42-AddEqualSplit       (CamelCase)
NO   feature/42-add_equal_split     (underscore)
NO   my-feature                     (no type, no number)
```

### Lifecycle

- `main` is the only long-lived branch and is always deployable.
- Every other branch starts from an up-to-date `main`, lives for a few days and is deleted after the merge.
- One issue, one branch. Do not mix unrelated work.
- The branch type follows the main purpose of the work, even when it contains commits of several types.

---

## Pull Requests

- **Target:** `main`.
- **Title:** follows the commit header format, for example `feat(expense): add equal split`. With squash merge, the title becomes the commit on `main`.
- **Body:** use the template and end it with `Closes #<issue>`.
- **Size:** small and focused. If the diff is hard to review, split the work.
- **Checks:** the `validate-standards` workflow and the CI tests must pass before merging.
- **Merge strategy:** **squash and merge**. This keeps `main` linear, one pull request equals one Conventional Commit, and the deploy history is easy to read. Edit the squash message so it keeps the Conventional header and the `Closes #N` line.
- **After merging:** delete the branch.

---

## Testing Expectations

Every pull request proves it keeps the rules intact. Details and examples in [Testing](TESTING.md).

- **Integration tests first.** Every new or changed endpoint has an `*IT` test through HTTP against PostgreSQL (extend `IntegrationTest`): happy path, validation errors, authorization per role and an attempt from another band (from M2, through the isolation harness).
- **Unit and property-based tests** for pure rules (splits, balances, allocation, average cost), with jqwik where an invariant applies ("parts always add up to the total").
- **Never mock the database** and never use an in-memory database.
- **Money is integer cents** in Java, SQL and TypeScript.
- **Run locally before pushing:** `./mvnw verify` in `api/`; `npm run lint && npm run typecheck && npm test` in `web/`. If you changed the API, run `npm run api:generate` in `web/` and commit `src/api/schema.ts`.
- **Required checks** (see `ruleset.json`): `validate`, `backend`, `frontend`, `api-contract`, `docker`, `gitleaks` and `dependency-review`. A pull request with a failing check cannot be merged.
- The pull request template contains the [Definition of Done](roadmap.md#definition-of-done). Tick what applies and explain anything left unchecked.

---

## Full Flow

1. **Create the issue** on GitHub and note its number, for example `#42`. Assign it to the sprint milestone.
2. **Create the branch** from an updated `main`:

   ```bash
   git checkout main
   git pull
   git checkout -b feature/42-add-equal-split
   ```

3. **Commit** following the standard. The `commit-msg` hook validates each one:

   ```bash
   git commit -m "feat(expense): add equal split" -m "Closes #42"
   ```

4. **Push.** The `pre-push` hook validates the branch name and every commit that is not on the remote yet:

   ```bash
   git push -u origin feature/42-add-equal-split
   ```

5. **Open the pull request** to `main`. Title in the Conventional format, body with `Closes #42`. The `validate-standards` workflow and the CI run.
6. **Review and fix.** Push more commits to the same branch.
7. **Squash and merge.** The issue closes automatically and the deploy from `main` starts.
8. **Delete the branch.**

---

## Automations

### Validation layers

| Layer | Where | When | Can be skipped? |
|---|---|---|---|
| `commit-msg` hook | Local | `git commit` | Yes, with `--no-verify` |
| `pre-push` hook | Local | `git push` | Yes, with `--no-verify` |
| `post-checkout` hook | Local | `git checkout` | Warning only |
| `validate-standards` workflow | GitHub | Pull request | No, when set as a required check |
| Branch ruleset | GitHub | Merge into `main` | No |

### Files

```text
.githooks/
├── commit-msg            validates the commit message
├── pre-push              validates the branch name and unpushed commits
├── post-checkout         warns about an invalid branch name
└── lib/
    ├── validate-message.sh
    └── validate-branch.sh
.github/
├── pull_request_template.md
└── workflows/validate-standards.yml
```

The hooks and the workflow share the same two scripts in `.githooks/lib/`, so the local and remote rules can never drift apart.

### Branch ruleset

Configure it in GitHub, Settings, Rules, Rulesets (the repository keeps its export in `ruleset.json`). Recommended rules for `main`:

- Require a pull request before merging (no direct push)
- Require status checks to pass: `Validate standards / validate` and the CI tests
- Block force pushes and branch deletion
- Allow only squash merge
- Do not allow bypass, not even for admins

Check that `ruleset.json` matches this list. The `validate-standards` check only appears in the list of required checks after it has run once.

### Dependabot

Pull requests opened by Dependabot are skipped by `validate-standards` because their branch names do not follow the standard.

---

## Practical Examples

### Example 1: new feature

Issue `#42`: "Split an expense equally".

```bash
git checkout -b feature/42-add-equal-split

git commit -m "feat(expense): add equal split" -m "Closes #42"
```

A larger feature with a body, in several commits:

```bash
git commit -m "feat(settlement): calculate balance per currency" -m "Ref: #42"
git commit -m "test(settlement): cover remainder on equal split" -m "Closes #42"
```

Pull request:

```
Title: feat(expense): add equal split
Body:  Splits an expense equally among the selected members and
       assigns the remainder deterministically.

       Closes #42
```

### Example 2: bug fix

Issue `#57`: "Stock goes negative on concurrent sales".

```bash
git checkout -b bugfix/57-fix-negative-stock

git commit -m "fix(sale): lock stock row during sale" \
           -m "Two concurrent sales could both pass the stock check." \
           -m "Closes #57"
```

The multi-line example uses `\` at the end of the line in bash. In Windows cmd, write the whole command on one line, or use `^` as the line continuation.

### Example 3: production hotfix

Issue `#63`: "API does not start after the last deploy".

```bash
git checkout -b hotfix/63-api-down
git commit -m "fix(db): correct migration order" -m "Closes #63"
```

Maximum priority: open the pull request, wait for the checks and merge as soon as they pass.

---

## Best Practices

### Do

```bash
# Small, focused commit with the footer
git commit -m "feat(merch): add product variations" -m "Closes #31"

# One issue per branch, created from an updated main
git checkout main && git pull && git checkout -b feature/31-add-product-variations

# Keep your branch up to date with main before opening the pull request
git fetch origin && git merge origin/main
```

- Commit often, with one logical change per commit.
- Write the body when the reason is not obvious from the header.
- Add tests for the business rules you touch (see [Testing](TESTING.md)).
- Update the documentation when behavior or setup changes.

### Don't

```bash
# Generic messages
git commit -m "fix stuff"

# No issue reference (the hook rejects it)
git commit -m "feat(auth): add sign in"

# Wrong branch names
git checkout -b my-feature
git checkout -b Feature/42-x

# Skipping the hooks (the pull request check will still block you)
git commit --no-verify -m "bad message"
git push --no-verify
```

- Do not commit secrets, `.env` files or credentials.
- Do not edit a Flyway migration that was already applied. Create a new one.
- Do not force-push a branch that is under review, except your own branch with `--force-with-lease`.
- Do not mix unrelated changes in the same branch.

---

## Troubleshooting

### Commit rejected: invalid header

```
ERROR: Invalid header. Use a valid type, a kebab-case scope (optional) ...
```

Fix the last commit:

```bash
git commit --amend -m "feat(auth): add sign in" -m "Closes #42"
```

### Commit rejected: missing issue reference

Add `Closes #N` or `Ref: #N` as a footer line, as in the command above. A bare `#N` line is not accepted.

### Push rejected: invalid branch name

```bash
git branch -m feature/42-my-feature
git push -u origin feature/42-my-feature
```

### Push rejected: invalid commit in the history

The error shows the commit and a hint. Reword it with an interactive rebase:

```bash
git rebase -i <sha>^
# mark the commit as "reword", save and fix the message
```

### Hooks do not run

Hooks only run after you point Git to the folder, once per clone:

```bash
git config core.hooksPath .githooks
```

On macOS and Linux the files also need the executable bit. Commit it once so everyone gets it:

```bash
git update-index --chmod=+x .githooks/commit-msg .githooks/pre-push .githooks/post-checkout .githooks/lib/validate-message.sh .githooks/lib/validate-branch.sh
```

### Hook fails with `\r: command not found` (Windows)

The shell scripts were saved with Windows line endings (CRLF). Force LF for them by adding this to `.gitattributes`, then re-checkout the files:

```
.githooks/** text eol=lf
*.sh text eol=lf
```

### Commits made before the hooks existed

`pre-push` validates every commit that is not on a remote yet. If old local commits do not follow the standard, reword them with `git rebase -i`, or push once with `--no-verify` and let the pull request check decide.

### The issue did not close

The pull request body is missing `Closes #N`, or the squash message was edited and lost it. Add the line and merge again, or close the issue manually.

### I forgot the issue number

It is in the branch name:

```bash
git branch --show-current
# feature/42-add-equal-split  ->  issue #42
```

---

## Initial Setup

Run once after cloning the repository:

```bash
# 1. Enable the Git hooks
git config core.hooksPath .githooks

# 2. Check that they work: this commit must be rejected
git commit --allow-empty -m "test"
```

If the second command creates a commit, the hooks are not active. Undo it with `git reset --soft HEAD~1` and check the [Hooks do not run](#hooks-do-not-run) section.

### Optional: secret scanning before each commit

CI runs [gitleaks](https://github.com/gitleaks/gitleaks) on every pull request. To catch a secret before it ever leaves your machine, install gitleaks and add a `pre-commit` hook that chains it:

```bash
cat > .githooks/pre-commit.local <<'HOOK'
#!/bin/sh
exec gitleaks git --pre-commit --staged --redact --no-banner
HOOK
chmod +x .githooks/pre-commit.local
ln -sf pre-commit.local .githooks/pre-commit
```

`.githooks/pre-commit*` files stay local (they are ignored by Git).

---

## Related Documentation

- [Getting Started](GETTING-STARTED.md): how to run the project
- [Database](DATABASE.md): roles, migrations and the tenant table checklist
- [Testing](TESTING.md): which rules need test coverage
- [Deployment](DEPLOYMENT.md): CI/CD and hosting
- [Conventional Commits](https://www.conventionalcommits.org/): official specification