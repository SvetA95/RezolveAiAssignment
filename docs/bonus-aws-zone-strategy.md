# Bonus Task 5 — `ms.reindexBatchSize` Across Two AWS Zones

Scenario: child nodes for a service are split evenly across two AWS zones, `eu-1a` and
`eu-1b`. This is a design/test-strategy write-up, no code — the Controller API has no
zone-awareness to actually exercise (see `docs/findings-ticket.md` for that gap).

## 1. How batching should behave across zones

`ms.reindexBatchSize` is just a flat integer — it says nothing about which nodes a batch
draws from. That's the real question here: with `k` as the batch size and two zones, does
the system pick `k` nodes off some flat list regardless of zone (risking an uneven or
single-zone batch by accident), or does it deliberately cap how many nodes per zone can be
in-flight at once?

I'd want the latter, with one rule: never reduce both zones' capacity at the same time if it
can be avoided. Drain one zone's nodes fully before touching the other, rather than splitting
each batch proportionally across both. The reason: the whole point of running two zones is
that an unrelated failure in one shouldn't compound with planned maintenance in the other. If
a batch is taking nodes from both zones at once and either zone has a real outage during that
window, you're left with both zones degraded simultaneously — strictly worse than one zone at
100% and the other mid-update. "Balanced," to me, means no zone ever has more than its fair
share of nodes in-flight at once — and if `k` is large enough to require spilling into a
second zone, that should only happen after the first zone's portion is exhausted.

## 2. Zone becomes unavailable mid-update

If `eu-1a` drops out while a batch is running, here's what I'd expect, and how I'd check it:

- **No new batches get drawn from the dead zone**, and the in-flight batch against it
  fails or times out within a bounded window rather than hanging — verified by confirming no
  further batches target `eu-1a` while it's down, and that nothing blocks waiting on it
  indefinitely.
- **`eu-1b` isn't pushed past safe capacity** — if it was also mid-batch when `eu-1a` failed,
  it should pause or throttle rather than stack a planned capacity cut on top of the
  unplanned one. Verified with synthetic/canary traffic against the service, confirming it
  keeps succeeding throughout (served by `eu-1b` alone).
- **Recovery is explicit, not assumed** — when `eu-1a` comes back, its nodes should pass
  health checks before rejoining rotation, and the rollout should resume rather than silently
  treat them as already done.

Testing this needs infrastructure-level fault injection, since the Controller API has no
"simulate a zone outage" endpoint — in a non-prod copy of the environment, cut network
reachability to `eu-1a` (security group change or instance stop) while a batch is actively
running, and check the three things above.

## 3. Test strategy for a regulated environment (audit trail / rollback evidence)

The API itself doesn't give you any of this, so it has to come from whatever wraps it:

- **No per-identity attribution** — every call in this assignment used one shared set of
  credentials, so "who made this change" can't be answered by the API alone.
  Fix: route changes through tooling that issues attributable, scoped credentials (or at
  least logs the human/ticket behind each call).
- **No change history** — `GET /properties` only shows current state, so rollback evidence
  has to be captured *before* the fact, not reconstructed after. Fix: record the full
  properties dump (not just the one property being changed) before and after every change,
  tagged with a change-ticket ID and timestamp — `BaseApiTest` already does this for test
  cleanup; in a regulated context it would write to durable, append-only storage instead of
  just holding it in memory for the test's own teardown.
- **A `202` isn't proof anything happened** — pair every change record with a confirmed
  post-change `GET`, so the evidence shows "requested X, confirmed X applied," not just
  "requested X." Rollback should go through the same capture-and-confirm pipeline, producing
  its own linked record.
- **No approval gate** — a single `POST` is immediately effective, with no two-person rule.
  Worth pairing with a negative-control test (an unauthorized credential is rejected and that
  rejection is logged), since proving access control works is itself part of the audit story.

And the zone-failure scenario in section 2 should be run as a repeatable, scripted procedure
with timestamps for "failure injected / degradation observed / recovery completed" — evidence
that resilience claims were actually tested, not just asserted.
