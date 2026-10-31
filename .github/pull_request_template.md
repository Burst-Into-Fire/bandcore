<!-- Title: Conventional Commit header, e.g. "feat(expense): add equal split" -->

## What and why

<!-- What changes, and why. Link the roadmap issue. -->

## How it was tested

<!-- Commands run, new tests, manual checks. Mention the CI duration if it changed noticeably. -->

## Definition of Done

<!-- Tick what applies. Explain any unchecked item that applies but is not done. -->

- [ ] Integration tests for every new or changed endpoint: happy path, validation errors, an entry in the authorization matrix and an entry in the cross-tenant isolation harness
- [ ] Every new tenant table has `band_id NOT NULL`, RLS enabled and forced with policies, composite foreign keys `(band_id, …)` and an index starting with `band_id` (the schema guard enforces it)
- [ ] Money handled as integer cents end to end; no floating point
- [ ] Relevant actions recorded in the audit log
- [ ] OpenAPI updated and the frontend client regenerated (`npm run api:generate`)
- [ ] UI text in pt-BR via i18n keys; screens usable from 360 px wide
- [ ] No secrets, tokens, passwords or unnecessary personal data in logs or error responses
- [ ] CI green; pull request reviewed

Closes #
