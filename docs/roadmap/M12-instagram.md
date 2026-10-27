# M12 · Instagram

**Goal:** Define the Instagram integration from the README roadmap, so it can be broken into implementation issues.

**Exit criteria:** the use cases, API constraints and token handling are agreed, and a follow-up set of issues exists for this milestone.

**Depends on milestones:** M10

---

## M12-01 · Discovery: Instagram integration

**Labels:** type:spike, priority:normal
**Depends on:** M10-07

### Context

Bands promote shows and merch on Instagram. An integration needs each band to connect its own account, so it involves third-party OAuth tokens stored per tenant.

### Scope

- [ ] Use cases to validate with bands: scheduling posts, insights, announcing merch drops and shows
- [ ] Instagram Graph API constraints: business or creator accounts only, Meta app review, permissions, rate limits
- [ ] Token handling: OAuth per band, tokens encrypted at rest (envelope encryption with a key outside the database), refresh and revocation, disconnect flow
- [ ] LGPD and Meta platform terms
- [ ] Output: architecture notes and the implementation issues for this milestone

### Acceptance criteria

- Issues for the Instagram integration are written in this file and reviewed

### Tests required

- None (discovery).
