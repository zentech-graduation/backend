# Support Module — Data Rules

Scope: `com.app.modules.support` and the `support_tickets` table.

One request, one response.
There is no thread, so there is no message table.

---

## Section 1: Canonical Data

| Table | Owner | Notes |
|-------|-------|-------|
| `support_tickets` | support | One request and the single staff response to it (V97). |
| `support_category_configs` | support | Display metadata for the category enum (V97). |

### Why this module exists at all

`TokenPrincipalResolverImpl` is the single account-state enforcement point in the application and it admits only `ACTIVE`.
A banned, suspended or deactivated account can therefore reach no authenticated endpoint.
Before this module the only user-facing moderation surface was a read-only warning list, so the population most affected by a moderation decision had no route to contest it.

That is why the module carries its own entry mechanisms rather than a carve-out in the account-state check.
Weakening that check would have handed a blocked account a general-purpose session.

### Column mutability

Everything the user wrote is `updatable = false` on the entity and is never rewritten: `user_id`, `contact_email`, `category`, `subject`, `body`, `source`, `admin_action_id`.
Only the staff workflow columns move: `status`, `assigned_to`, `assigned_at`, `staff_response`, `internal_note`, `responded_by`, `responded_at`, `escalated_by`, `escalated_at`, `escalation_reason`.

This mirrors how `Report` and `AdminAction` split theirs, and it means no workflow step can rewrite the request it is answering.

### `contact_email` is captured even for an authenticated ticket

The account may be banned by the time staff answer.
The address is then the only channel that reaches the person, so it is stored on the ticket rather than resolved from `users` at send time.

### `internal_note`

Staff-only, and it never leaves the system.

The guarantee is structural rather than procedural.
`SupportTicketResponse`, the owner-facing record, has no component for it, and `SupportTicketMapper` is hand-written rather than generated precisely so that a field added to the entity cannot appear in it by name matching.
`internal_note` is also never placed into the `admin_actions` metadata map, so it cannot reach a mail template either.

---

## Section 2: Status Lifecycle

| From | Permitted targets |
|------|-------------------|
| `pending_confirmation` | `open`, and only by redeeming the confirmation token |
| `open` | `in_progress` (by claiming), `answered`, `rejected`, `escalated` |
| `in_progress` | `answered`, `rejected`, `escalated` |
| `escalated` | `answered`, `rejected` |
| `answered` | none — terminal |
| `rejected` | none — terminal |

`SupportTicketServiceImpl.validateTransition` is the enforcement point, in the shape `ReportServiceImpl.validateTransition` uses: one explicit switch, one error code (`SUPPORT_TICKET_INVALID_TRANSITION`) for every disallowed pair.

An escalated ticket does not return to the moderator queue, for the same reason an escalated report does not: a case handed up to an administrator must not be pulled back down.

`pending_confirmation` is not reachable by any staff action.
It exists only for the public form and is left by redeeming the confirmation token, never by a status write.

---

## Section 3: The One-Open-Ticket Invariant

A user may hold exactly one ticket that is not terminal, across every category.

Enforced twice, deliberately:

1. `uq_support_tickets_one_open_per_user` (V100), a partial unique index over `user_id` where the status is `open`, `in_progress` or `escalated`. This is what actually holds under a concurrent double submit.
2. `SupportTicketRepository.hasOpenTicket`, checked in the service. This exists so the ordinary case answers `SUPPORT_TICKET_ALREADY_OPEN` rather than surfacing a constraint violation as a 500.

`pending_confirmation` is outside the guard in both places.
An unconfirmed public submission is not yet a real ticket and must not block the account's genuine one.

The index also carries `user_id IS NOT NULL`.
Without it a partial unique index over a nullable column would admit unlimited public tickets, because `NULL` never equals `NULL`.

This narrowing to active statuses follows V89, which corrected the report duplicate guard for exactly the same reason: a guard that counts closed rows stops a user from ever asking again.

For a public ticket with a null `user_id` the index cannot apply.
The IP rate limit and the email-confirmation step are the controls there.

---

## Section 4: Authorization Matrix

Enforced in `SupportAuthorizationService`, which follows `AdminAuthorizationService`: a pure evaluator returning an outcome, and assert methods mapping outcomes to error codes.
Controller annotations are the first gate and this is the second; neither is load-bearing alone.

| Capability | `user` | `moderator` | `admin` |
|---|---|---|---|
| Create a ticket about themselves | Yes | Yes | Yes |
| Read their own ticket | Yes | Yes | Yes |
| List all tickets | No | Yes | Yes |
| Claim a ticket | No | Yes | Yes |
| Respond to or close a non-appeal ticket | No | Yes | Yes |
| Respond to or close an `appeal_*` ticket | No | **No** | Yes |
| Read an `appeal_*` ticket | No | Yes, read only | Yes |
| Escalate any ticket | No | Yes | Yes |
| Read `internal_note` | No | Yes | Yes |

### Why a moderator cannot decide an appeal

Unban, unsuspend, revoke-warning and revoke-strike are all administrator-only actions.
A moderator who could close an appeal would be recording a verdict they have no capability to execute, which would leave the ticket answered and the account still banned.

A moderator may still read the appeal and escalate it.
Escalation is how an appeal reaches an administrator, so removing it would strand the queue.

Refused with `SUPPORT_APPEAL_REQUIRES_ADMIN`, a distinct code, so a client can explain the refusal rather than showing a generic 403.

The narrowing is not expressible as a per-method `@PreAuthorize`, because it depends on the ticket's category, which an annotation cannot see.
That is why the service gate is the real one here and the annotation is only the outer perimeter.

### Conflict of interest

A staff member may not claim, respond to, close or escalate a ticket whose `admin_action_id` names an audit row they wrote.

The acting staff member is resolved from `admin_actions.admin_id`.
That column is nullable — it is null for the automatic strike the discipline ladder writes and for the automatic unsuspend the expiry sweep writes — and **a null actor blocks nobody**.

Reading is exempt.
Reading is not a decision, and hiding the ticket from the person who made the original call would make the queue harder to reason about without protecting anyone.

Refused with `SUPPORT_CONFLICT_OF_INTEREST`, again distinct from a plain 403.

Conflict of interest is evaluated **before** the appeal rule, so an administrator who wrote the decision is refused for the conflict — the accurate reason — rather than being admitted because their role is sufficient.

### Claiming

`assigned_to` and `assigned_at` are set by `SupportTicketRepository.claimIfUnassigned`, whose predicate re-checks `assigned_to IS NULL AND status = 'open'`.
Two moderators claiming at once produce one update of 1 and one of 0, and the loser is told with `SUPPORT_TICKET_ALREADY_CLAIMED` rather than silently overwriting the winner.

Shaped on `AdminUserRepository.reinstateExpiredSuspension`, which resolves the same race the same way.

Deciding and escalating both require holding the claim.
Escalated reports have no ownership model at all today, and that gap is why this one is guarded from the start.

### Audit

Every staff decision writes an `admin_actions` row through `AdminActionRecorder`: `respond_support_ticket`, `reject_support_ticket`, `escalate_support_ticket` (V98, with their config rows in V99).

Claiming is deliberately **not** audited.
It is a queue mechanic rather than a decision about a person, and a row for every claim would bury the rows that record verdicts.

---

## Section 5: The Three Entry Paths

### Path A — authenticated

`POST /api/v1/support/tickets`.
Ordinary authenticated request; the standard filter chain resolves the principal.

### Path B — signed link

Every punitive moderation notice carries a single-use appeal link.

The token follows `TokenServiceImpl` exactly: `SecureRandom` 32 bytes, Base64-URL encoded, only the SHA-256 hex stored in Redis, a forward key and a reverse key, atomic issue by Lua so a new token invalidates the previous, atomic consume by Lua GET-then-DEL so it can be redeemed exactly once.

TTL is **30 days**, against 24 hours for email verification and 15 minutes for a password reset.
Those two bound a window the user opened seconds earlier and is waiting on.
An appeal window is the opposite: the notice arrives unannounced, is bad news, and is routinely read late.
A shorter window would expire the link for exactly the people least able to act quickly, and the link authorises nothing but writing one ticket.
It is not indefinite, because a token that never expires is a credential.

The reverse key is keyed on the **audit row**, not the account, so a second appealable decision does not invalidate the link for the first.

The token binds to the `admin_actions` row, so the resulting ticket lands with `admin_action_id` populated and the appeal category taken from the token rather than from the request body.
A client-supplied category would let the submitter appeal something the token never authorised.

**Redeeming the token mints no session, no token pair and no refresh token row.**
It authorises exactly one write.
`AuthServiceImpl.verifyEmail` is the counter-example in this codebase — it consumes a token and calls `issueSession` with no account-state check — and this path deliberately does not repeat that shape.

Only the six punitive actions carry a link.
The two reinstating actions do not, because there is nothing to contest, and the two support ticket replies do not, because a rejected appeal could otherwise be appealed in an unbounded loop.

The token is minted at send time, not when the action is recorded, so a notice that is throttled or skipped leaves no live token behind.

### Path C — public form

For someone who no longer has the mail, or never received one.

Two independent controls, both required:

1. **Cloudflare Turnstile**, verified server-side before anything is written. Configured under `app.support.turnstile.*`.
2. **Email confirmation.** The ticket is written in `pending_confirmation` and is invisible to every staff query until the confirmation link is followed. Turnstile proves the submitter is probably not a bot; only this proves they can read the mailbox they named, which is what stops the form opening tickets in someone else's name.

Appeal categories are refused on this path with `SUPPORT_CATEGORY_NOT_PUBLIC`.
An appeal needs an audit row to appeal against, which only a signed link supplies.

**Turnstile fails closed.**
When Cloudflare is unreachable, times out, or answers anything other than a clear success, the submission is refused.
Failing open would keep appeals flowing during an outage, but it would also mean anyone able to cause a timeout can switch the bot control off at will, and a control an attacker can disable by making one request slow is not a control.
Failing closed makes an outage visible and temporary: the public form stops, while the two authenticated paths and every appeal link already in existing moderation mail keep working, so nobody who was actually mailed a decision loses their route to contest it.

An unconfigured secret is treated the same way, and refuses, so a deployment that forgets `TURNSTILE_SECRET_KEY` fails the form closed rather than silently removing the control.

### Rate limits on path C

Three per hour keyed on the caller's IP, in `app.rate-limit.endpoint-rules` in all three profiles.

Ten per day keyed on the submitted **address**, enforced in `SupportTicketServiceImpl` on the same Redis sliding-window primitive the filter uses.
`AuthRateLimitFilter` carries one window per endpoint rule, so a second window cannot be expressed there.
The two bounds are deliberately keyed differently: the filter stops one machine bursting, and the service stops one address being used all day from many machines.

---

## Section 6: Deployment Prerequisite — Trusted Proxy

`IpExtractor` trusts `X-Forwarded-For` only when the direct peer matches a CIDR in `app.security.trusted-proxy-cidrs`.

**That list is empty in the production profile.**

Behind an unconfigured reverse proxy every caller presents the proxy's address, so the IP-keyed hourly limit on the public form buckets every caller together and becomes a global cap of three submissions per hour.

Configure `app.security.trusted-proxy-cidrs` with the ingress CIDRs before relying on the public form in production.
The daily per-address bound is unaffected, because it is keyed on the submitted address rather than the IP.

---

## Section 7: Notifications and Mail

A terminal status change notifies the user twice.

In-product: a `support_ticket_update` notification (V98, config row in V99).
It is not user-toggleable, for the reason V61 gives for the warning row: an account that could switch it off would ask a question and never be told it had been answered.

By mail: through `moderation.mail.queue`, the path built for moderation notices.
That path applies no `ACTIVE`-only gate, which is exactly what a reply to a banned account requires.

The mail is raised by the audit row rather than by the service: `AdminActionRecorder` maps `respond_support_ticket` and `reject_support_ticket` to notice templates and enqueues the event inside the same transaction as the ticket write.

The mail carries `staff_response`.
It never carries `internal_note`, which is never placed in the metadata map at all.

A public ticket that never resolved to an account gets no in-product notification — there is nobody to notify — but still gets the mail, which is the whole point of that path.

---

## Section 8: Inter-Module Dependencies

| Depends on | For |
|------------|-----|
| `users` | The account, its role and its address |
| `admin` | `admin_actions` for the appealed decision and for writing audit rows |
| `mail` | The notice mail and the confirmation mail |
| `notification` | The in-product notification |
| `common/security` | The Redis sliding window and `IpExtractor` |

Nothing depends on this module in return.
