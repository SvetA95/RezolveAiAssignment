# Findings

The assignment note says no issues were deliberately planted in the Controller or in
`ms.reindexBatchSize`, and that mostly held up. Testing against the live `task-11`
environment turned up one genuine functional bug, a couple of smaller inconsistencies in how
errors are reported, and a few small slips in the assignment's own documentation that aren't
worth a full ticket each.

## Documentation minor issues

- The properties endpoints are documented under `fdpqa.fredhopper.com`, while every other
  endpoint in the spec uses `fdp-qa.fredhopper.com` (with the hyphen). Looks like a typo;
  copy-pasting those two URLs as written gives a DNS failure rather than a clean error.
- The set-property endpoint lists `Content-type: text-plain`, which isn't a real MIME type
  (should be `text/plain`). Turns out it doesn't matter in practice, since the server ignores
  `Content-Type` entirely, but worth fixing in the docs regardless.
- `GET /deployments` returns a flat list of IPs with no indication of which one is the main
  node and which are children. Since `ms.reindexBatchSize` only applies to child nodes, there's
  no way to check via the API alone whether a given batch size makes sense relative to how many
  child nodes actually exist.
- The spec never states what values are actually valid for `ms.reindexBatchSize`: no range, no
  statement on whether zero, negatives, or decimals are allowed. That gap is exactly what the
  edge-case testing below was for.

## Bugs

### BUG-1 — Zero and negative values are accepted instead of rejected

This is the one I'd actually call a bug. Posting `0` or `-1` to `ms.reindexBatchSize` returns
`202 Accepted`, and a follow-up `GET` confirms the value is stored as-is. No validation
rejects it, even though the API clearly does have validation in place for other bad input
(`abc`, `1.5`, and oversized numbers all correctly come back as `400` with a message like
`Batch reindex size 'X' is invalid.`).

The property exists to say "update this many child nodes at once." A batch of zero servers
isn't an update at all, and a negative one doesn't mean anything, so whatever process reads
this value downstream is being handed a number the underlying feature was never designed to
handle. Best case it's a silent no-op; worst case it stalls or breaks the update job
entirely, and nothing in the API would warn you beforehand.

While testing this I also happened to find the property already sitting at `-1234` on one
occasion, with no test of mine having set it. Somebody or something else on the same shared
environment landed it on a negative value too, which is a small real-world illustration of
exactly this bug.

This is covered by two automated test cases (`ReindexBatchSizePropertyTest.settingInvalidValueIsRejected`,
the `"0"` and `"-1"` inputs) that currently fail on purpose. The failure itself is the proof.

**Suggested fix:** reject `value <= 0` the same way other invalid input is already rejected.

### BUG-2 — Empty input returns a different error code than other bad input

Posting an empty body returns `406 Not Acceptable` with the message `No value for property:
ms.reindexBatchSize`, while every other kind of bad input (non-numeric, decimal, oversized)
returns `400 Bad Request`. `406` usually means content negotiation failed (an `Accept` header
mismatch), not "you sent nothing," so it's both a slightly wrong status code on its own and
inconsistent with how the rest of this endpoint reports bad input.

**Suggested fix:** return `400` for the empty-body case as well.

### BUG-3 — An unknown customer leaks an internal error message

Hitting the API with a customer name that doesn't exist returns `404`, which is correct, but
the body reads `Could not complete request : Cannot operate life cycle object: state=STOPPED`.
That sounds like an unhandled internal exception getting stringified rather than a deliberate
"customer not found" response. An unknown *service* name, by contrast, returns a generic Jetty
404 HTML page, so the same class of error (an unknown path segment) produces two different
response shapes depending on which segment was wrong.

Not a serious issue on its own, since the status code is still right, but worth fixing both
because it leaks implementation details and because consistent error shapes make a client's
error handling much simpler to write.

**Suggested fix:** normalize all "unknown path segment" cases to the same plain-text 404 shape,
and make sure the customer lookup doesn't let an internal exception's message reach the client.

## Things I checked that turned out fine

Worth noting so it's clear these were actually tested, not just assumed:

- The property is scoped per customer/service, not global. Changing it for one customer's
  `fas:live1` left another customer's `fas:live1` untouched (now an automated test:
  `doesNotLeakAcrossCustomers`).
- `202 Accepted` reads as if the change might apply asynchronously, but in this environment a
  `GET` right after a `POST` already reflects the new value, with no propagation delay observed.
- Decimals, scientific notation, leading/trailing whitespace, and oversized numbers are all
  rejected consistently with `400`.
- A batch size larger than the number of servers actually deployed (tried `100` against a
  4-server deployment) is accepted with no clamping or warning. Not a bug exactly, since the
  spec never said it would be capped, but worth knowing if you're relying on this property to
  protect you from over-committing capacity.
