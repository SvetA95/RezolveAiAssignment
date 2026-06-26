# CI Integration (Task 4)

**Trigger:** I'd run this on a schedule (nightly, say) plus on-demand, not on every commit or
PR. Every run mutates a live property on a shared QA Controller, and other people (other
candidates, other automated checks) might be relying on whatever state it's in — running this
on every push risks fighting over that state, which is exactly the kind of thing that happened
once during testing (see the `-1234` note in the findings doc). If any pure unit-level tests
get added later that don't touch the live API, those could still run on every PR.

**Credentials:** pulled from CI secrets, never committed — the suite already reads them from
environment variables for this reason. Ideally a dedicated test customer/service rather than
shared one.

**Failure behavior:** a red build should mean something actionable. The two `0`/`-1` cases
that fail (see `docs/findings-ticket.md`, BUG-1) would get tagged — `@Disabled` with
a reason, or a JUnit tag excluded from the default run — so the pipeline stays meaningfully
green/red for new regressions, with those two tracked separately until the underlying bug is
actually fixed, instead of either permanently failing the build or quietly deleting the
evidence.

**Reporting:** the standard Surefire XML output is enough for most CI dashboards. Linking a
failure back to the relevant findings-doc entry, where one exists, saves whoever's on call from
re-discovering something already written down.
