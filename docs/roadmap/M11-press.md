# M11 · Press

**Goal:** Define the press module (sending material to media outlets) from the README roadmap, so it can be broken into implementation issues.

**Exit criteria:** the scope, data model and risks are agreed, and a follow-up set of issues exists for this milestone.

**Depends on milestones:** M10

---

## M11-01 · Discovery: press module

**Labels:** type:spike, priority:normal
**Depends on:** M10-07

### Context

Bands send press kits and releases to blogs, radio stations and journalists. The module should fit the existing architecture: a new Spring Modulith module with tenant tables under RLS, audit events and the existing email infrastructure.

### Scope

- [ ] Interview bands about how they send material today
- [ ] Define the scope:
  - press kit: bio, photos, releases, tech rider
  - media contacts: outlets and journalists
  - campaigns that send material
  - tracking of opens and replies
- [ ] LGPD: journalists' contacts are personal data; define the legal basis, opt-out and retention
- [ ] Email deliverability: provider, sending domain per band or shared, SPF/DKIM/DMARC, bounce handling, unsubscribe
- [ ] Storage of files (photos, PDFs): limits per band, scanning, private links
- [ ] Output: data model sketch and the implementation issues for this milestone

### Acceptance criteria

- Issues for the press module are written in this file and reviewed

### Tests required

- None (discovery).
