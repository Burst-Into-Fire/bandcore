# M4 · Band Workspace

**Goal:** Give each band its workspace: members (integrantes) with or without login, invitations, role management, settings, secure switching between bands, and the application shell from the mockup.

**Exit criteria:** an admin can register members, invite people by email and manage their roles; a user in several bands can switch between them without logging in again; the shell (sidebar, header, mobile navigation) is in place for the business modules.

**Depends on milestones:** M3

---

## M4-01 · Manage band members

**Labels:** type:feature, area:backend, module:tenancy, priority:high
**Depends on:** M2-11

### Context

A member (integrante) is who participates in splits, sales and balances. Not every member has a login, and not every user with access is a member (for example a read-only manager). Members are kept separate from memberships (access) for that reason.

### Scope

- [ ] `members` tenant table: `band_id`, `id`, `display_name`, `instrument` (free label such as "Vocal"), `user_id` (nullable, unique per band), `status` (`ACTIVE`, `INACTIVE`), `joined_at`, `left_at`; RLS and composite keys
- [ ] CRUD endpoints: admins write, every role reads
- [ ] Deactivate instead of delete when the member has history (expenses, splits, sales); hard delete only when there is none
- [ ] Inactive members are excluded from new splits but stay in balances and history
- [ ] Link or unlink a member to a user who has a membership in the same band

### Acceptance criteria

- Members can be managed by admins, and history is never lost when someone leaves the band

### Tests required

- Integration: CRUD and validation; linking a user who is not a member of the band → rejected; hard delete with history → 409; audit events recorded; entries in the authorization matrix and isolation harness

---

## M4-02 · Invite users by email

**Labels:** type:feature, area:backend, module:tenancy, priority:high
**Depends on:** M4-01, M3-02, M3-03

### Context

Invitations are how new people get access to a band. The token in the email grants access, so it must be short-lived, single use and tied to the invited email.

### Scope

- [ ] Admins invite an email with a role and, optionally, the member record to link
- [ ] Token: 256-bit random, stored hashed, single use, expires in 7 days; resend issues a new token and invalidates the previous one; revoke
- [ ] Accept flow: the link opens the central host → log in or sign up → the verified email must equal the invited email → membership created and member linked → redirect to the band host
- [ ] Invitation list with status (pending, accepted, expired, revoked)
- [ ] Rate limit per band per day
- [ ] pt-BR email template with the band name (user-supplied text escaped)

### Acceptance criteria

- Existing and new users can accept an invitation and land in the band with the invited role

### Tests required

- Integration: accept flow for an existing user and for a new user; an account with a different email → rejected; expired, revoked or used token → rejected; inviting an existing member → 409; band B's admin cannot list or revoke band A's invitations; audit events; rate limit

---

## M4-03 · Manage memberships and roles

**Labels:** type:security, area:backend, module:tenancy, priority:high
**Depends on:** M4-02

### Context

Removing someone from a band must cut their access immediately. A band must also never end up without an admin.

### Scope

- [ ] List memberships; change role; remove access; leave the band
- [ ] Removing access revokes the user's refresh tokens for that band and evicts the membership cache, so the current access token stops working on the next request (M2-09)
- [ ] Invariant: every band always has at least one active `ADMIN`, enforced in the service with a row lock
- [ ] Every change audited

### Acceptance criteria

- A removed user loses access at once, and the last admin cannot be removed or demoted

### Tests required

- Integration: the removed user's next request fails; demoting or removing the last admin → 409; two admins demoting each other concurrently leave at least one admin; `MEMBER` cannot change roles

---

## M4-04 · Implement band settings

**Labels:** type:feature, area:backend, area:frontend, module:tenancy, priority:normal
**Depends on:** M4-03

### Context

Admins need to edit basic band information. The timezone matters because months on the dashboard are computed in the band's timezone.

### Scope

- [ ] Admins edit name and timezone (validated against the IANA list)
- [ ] Currency fixed to BRL in v1
- [ ] Slug is immutable in v1 (documented); changing it, with redirects, is a later feature
- [ ] Settings page in the frontend

### Acceptance criteria

- Settings changes are applied and audited

### Tests required

- Integration: validation, authorization matrix and isolation entries
- The effect of the timezone on month boundaries is tested in M6-04

---

## M4-05 · Implement central "my bands" page with secure subdomain handoff

**Labels:** type:security, area:backend, area:frontend, module:identity, priority:high, needs-discussion
**Depends on:** M2-07, M4-03

### Context

Sessions are host-only, so moving from the central host to a band (or between bands) needs a safe handoff that does not share cookies across subdomains.

### Open question

The requirements and architecture docs do not mention one subdomain per band; there the tenant comes from the token and a selected band (`docs/ARCHTECTURE.md`). This issue assumes subdomain routing. To be decided before this issue starts.

Multiple bands per user is *Next* in the requirements (RF06). This roadmap assumes global accounts with memberships in several bands from the start.

### Scope

- [ ] The central session lists the user's active memberships (allowed by the `app.user_id` RLS policy)
- [ ] Choosing a band issues a handoff code: 256-bit random, stored hashed, 60-second lifetime, single use, bound to user, band and target host
- [ ] The browser is redirected to `https://{slug}.{BASE_DOMAIN}/auth/handoff#code=…` (fragment, so the code never reaches server logs); the SPA posts the code and receives a band session with the refresh cookie set on the band host
- [ ] "Switch band" entry in the shell (M4-06)

### Acceptance criteria

- A user in two bands switches between them without typing the password again, and the code cannot be reused or used on another host

### Tests required

- Integration: successful exchange; reuse → 401; expired → 401; a code for band A exchanged on band B's host → 401; membership removed between issue and exchange → 401; exchange on the central host → 404

---

## M4-06 · Build the application shell

**Labels:** type:feature, area:frontend, priority:high
**Depends on:** M2-12

### Context

The shell reproduces the mockup and is shared by every module, so it must be responsive and role-aware from the start.

### Scope

- [ ] Sidebar with the band name and navigation: *Resumo*, *Gastos*, *Merch*, *Integrantes*, plus *Configurações* for admins
- [ ] Page header with title, subtitle and a primary action slot (for example *Novo gasto*)
- [ ] Shared components matching the mockup: cards, KPI tiles, lists with right-aligned values, status badges, coral accent for primary buttons and warnings
- [ ] Responsive: bottom navigation and compact header up to 768 px; usable at 360 px
- [ ] Role-aware UI: hide actions the user cannot perform (the backend still enforces)
- [ ] Loading, empty and error states
- [ ] Keyboard navigation, visible focus, WCAG AA contrast
- [ ] Band switcher using M4-05

### Acceptance criteria

- The shell matches the mockup on desktop and works on a 360 px phone

### Tests required

- Component tests: navigation and actions by role
- Playwright screenshots at 360, 768 and 1280 px (visual review in the pull request)

---

## M4-07 · Build members and invitations pages

**Labels:** type:feature, area:frontend, module:tenancy, priority:normal
**Depends on:** M4-01, M4-02, M4-03, M4-06

### Context

The *Integrantes* page is where admins manage who is in the band and who has access.

### Scope

- [ ] Member list: name, instrument, linked account, role, status
- [ ] Add, edit and deactivate members
- [ ] Invite by email with role; pending invitations with resend and revoke
- [ ] Change role and remove access, with in-page confirmation
- [ ] Invitation acceptance page

### Acceptance criteria

- Every member and invitation operation from M4-01 to M4-03 is available in the UI

### Tests required

- Vitest + MSW: forms and role-based visibility
- E2E: an admin invites someone → the invitee signs up from the Mailpit link → lands in the band as `MEMBER`
