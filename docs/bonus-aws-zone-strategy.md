# Bonus Task 5 — `ms.reindexBatchSize` Across Two AWS Zones

Scenario: child nodes for a service are split evenly across two AWS zones, `eu-1a` and
`eu-1b`. This is a design/test-strategy write-up, no code. The Controller API has no
zone-awareness to actually exercise (see `docs/findings-ticket.md` for that gap).

## 1. How batching should behave across zones

`ms.reindexBatchSize` is just a flat integer, so it says nothing about which nodes a batch
draws from. That's the real question here: with `k` as the batch size and two zones, does
the system pick `k` nodes off some flat list regardless of zone (risking an uneven or
single-zone batch by accident), or does it deliberately cap how many nodes per zone can be
in-flight at once?

I'd want the latter, with one rule: never reduce both zones' capacity at the same time if it
can be avoided. Drain one zone's nodes fully before touching the other, rather than splitting
each batch proportionally across both. The reason is that the whole point of running two
zones is so an unrelated failure in one doesn't compound with planned maintenance in the
other. If a batch is taking nodes from both zones at once and either zone has a real outage
during that window, you end up with both zones degraded at the same time, which is strictly
worse than one zone at 100% and the other mid-update. "Balanced," to me, just means no zone
ever carries more than its fair share of in-flight nodes, and if `k` is large enough to spill
into a second zone, that should only happen once the first zone's portion is used up.

## 2. Zone becomes unavailable mid-update

Say `eu-1a` drops out while a batch is running. A few things I'd want to be true, and how I'd
check each one:

No new batches should get drawn from the dead zone, and whatever's already in flight against
it should fail or time out within a bounded window rather than hang forever. I'd verify that
by confirming no further batches target `eu-1a` while it's down, and that nothing's left
waiting on it indefinitely.

`eu-1b` shouldn't get pushed past safe capacity either. If it was also mid-batch when `eu-1a`
failed, it should pause or throttle rather than stack a planned capacity cut on top of an
unplanned one. Synthetic or canary traffic against the service should keep succeeding
throughout, served by `eu-1b` alone, which is how I'd confirm it.

And recovery should be explicit rather than assumed: when `eu-1a` comes back, its nodes
should pass health checks before rejoining rotation, and the rollout should pick back up
rather than silently treat those nodes as already done.

Actually testing any of this needs infrastructure-level fault injection, since the Controller
API has no "simulate a zone outage" endpoint. In a non-prod copy of the environment, I'd cut
network reachability to `eu-1a` (a security group change or stopping the instances) while a
batch is actively running, and check the three things above.

## 3. Test strategy for a regulated environment (audit trail / rollback evidence)

The API doesn't give you any of this on its own, so it has to come from whatever wraps it.

Every call in this assignment used one shared set of credentials, so on its own the API can't
answer "who made this change." That needs to come from tooling that issues attributable,
scoped credentials, or at least logs the human and ticket behind each call.

`GET /properties` only shows current state, with no history, so rollback evidence has to be
captured before the fact rather than reconstructed afterward. In practice that means
recording the full properties dump, not just the one property being changed, before and
after every change, tagged with a change-ticket ID and timestamp. `BaseApiTest` already does
something like this for test cleanup, just in memory rather than to durable storage. In a
regulated context it would need to write to an append-only log instead.

A `202` on its own isn't proof anything actually happened, so every change record should be
paired with a confirmed follow-up `GET`, so the evidence trail shows "requested X, confirmed
X applied," not just "requested X." Rollback should go through that same capture-and-confirm
step rather than being an untracked side effect of cleanup.

There's also no approval gate today; a single `POST` takes effect immediately, with no
two-person rule. I'd want a negative-control test alongside everything else, confirming an
unauthorized credential gets rejected and that the rejection is logged, since proving access
control works is as much a part of the audit story as proving the happy path.

Finally, the zone-failure scenario from section 2 should be run as a repeatable, scripted
procedure with timestamps for when the failure was injected, when degradation was observed,
and when recovery completed, so it stands as evidence that resilience claims were actually
tested rather than just asserted.
