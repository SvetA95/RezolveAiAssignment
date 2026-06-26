# Manual Test Cases — `ms.reindexBatchSize`

These are the manual test cases for `ms.reindexBatchSize`, covering the GET-list, GET-single,
and POST-set endpoints. I ran each of these by hand in Postman against the live `task-11`
environment before deciding which ones were worth promoting to the automated suite — rows
below say "verified in Postman" where I actually did that, with what came back.

Cases marked **[Automated]** ended up in `ReindexBatchSizePropertyTest`. Unless stated
otherwise, the target for every row is
`customers/{customer}/services/{service}/properties/ms.reindexBatchSize`.

## A. Functional / happy path

| # | Test | Steps | Expected |
|---|------|-------|----------|
| A1 | **[Automated]** Property is listed | GET `/properties` | 200; response includes a line `ms.reindexBatchSize=<n>` |
| A2 | **[Automated]** Single property fetch matches the list | GET `/properties`, then GET `/properties/ms.reindexBatchSize` | Both return the same value |
| A3 | **[Automated]** Set a valid value | POST a positive integer (e.g. `5`) | 202 Accepted; a follow-up GET returns `5` |
| A4 | **[Automated]** Set the documented default | POST `1` | 202; GET returns `1` |
| A5 | Set the value to what it already is | POST the current value again | 202; nothing breaks on a no-op update |
| A6 | Two updates in quick succession | POST `2`, then immediately POST `7` | Final value is `7` — confirms updates apply in order, not out of order |

## B. Boundary values

| # | Test | Expected | Verified in Postman |
|---|------|------|------|
| B1 | **[Automated]** Minimum sane value, `1` | Accepted | Yes — 202, persists |
| B2 | **[Automated]** Zero | Rejected — a batch of 0 servers can't update anything | Accepted (202) and stored as `0`. **This is a bug — see BUG-1.** |
| B3 | **[Automated]** Negative, `-1` | Rejected | Accepted (202) and stored as `-1`. **Same bug, BUG-1.** |
| B4 | A value larger than the number of child nodes actually deployed | Unclear from the spec — I'd want it rejected, clamped, or at least flagged, since exceeding the real node count makes "batching" meaningless | Tried `100` against a deployment with only 4 servers total — accepted, no clamping or warning. Worth flagging even if not strictly a bug, since the spec never promises a cap |
| B5 | **[Automated]** A number too large to be a real int (`99999999999999999999`) | Rejected | Yes — 400, `Batch reindex size '...' is invalid.` |

## C. Invalid input

| # | Test | Expected | Verified in Postman |
|---|------|------|------|
| C1 | **[Automated]** Non-numeric (`abc`) | Rejected | Yes — 400 |
| C2 | **[Automated]** Empty body | Rejected | Yes, but with `406` instead of `400` — inconsistent with the rest of this list, see BUG-2 |
| C3 | **[Automated]** Decimal (`1.5`) | Rejected — a batch size has to be a whole number of servers | Yes — 400 |
| C4 | **[Automated]** Leading/trailing whitespace | Rejected, or trimmed and accepted — either is fine as long as it's consistent | Both rejected with 400 — consistent |
| C5 | **[Automated]** Scientific notation (`1e3`) | Rejected | Yes — 400 |
| C6 | **[Automated]** Explicit plus sign (`+5`) | Either rejected, or accepted and normalized to `5` | Accepted, normalized to `5`. Reasonable, not a bug |
| C7 | Wrong `Content-Type` (`application/json` with body `"5"`) | The spec says `text/plain` is required — worth checking whether that's actually enforced | Not enforced at all; same result as `text/plain` |
| C8 | Multi-line body (`"5\n6"`) | Rejected | Yes — 400 |

## D. Resource not found

| # | Test | Expected | Verified in Postman |
|---|------|------|------|
| D1 | **[Automated]** GET an unknown property name | 404 | Yes |
| D2 | **[Automated]** GET/POST against an unknown customer | 404 | Yes, but the error body looks like a leaked internal exception — see BUG-3 |
| D3 | **[Automated]** GET/POST against an unknown service | 404 | Yes, and with a different (generic) error body than D2 for what should be the same kind of error |

## E. Consistency / state

| # | Test | Steps | Expected | Verified in Postman |
|---|------|-------|----------|------|
| E1 | Read-after-write timing | POST a new value, then GET right away | `202` implies the change *could* be async, so I wasn't going to assume an immediate read is safe | No observable delay in this environment — GET reflects the new value straight away. The automated test still polls for a few seconds rather than relying on that, since nothing guarantees it |
| E2 | **[Automated]** Scope isolation | Set the property for one customer's `fas:live1`; check another customer's `fas:live1` | Untouched — this property should be scoped per customer+service, not shared globally | Yes — confirmed, and now an automated test (`doesNotLeakAcrossCustomers`) |

## Why these particular edge cases

- **Zero, negative, decimal (B2, B3, C3):** this property directly controls how many
  production-serving nodes get pulled out of rotation at once. A bad value here doesn't just
  fail to validate — it can actually break the update process it's meant to configure. That's
  why I treated this as worth a real test rather than a "nice to have."
- **Read-after-write timing (E1):** the spec documents `202 Accepted`, which is normally a hint
  that something happens asynchronously. Even though it turned out instant here, I didn't want
  the automated check to quietly depend on that.
- **Larger than the actual node count (B4):** this is the one edge case the assignment itself
  points at — batches only apply to child nodes, default size 1. If the API lets you set a
  batch size bigger than the fleet without so much as a warning, "batching" stops doing
  anything useful.
- **Scope isolation (E2):** the spec doesn't say whether this property is per (customer,
  service) or shared more broadly. With several customers living on the same Controller, a
  leak between them would be a serious correctness problem, so it was worth confirming
  directly rather than assuming.
