# Fredhopper Controller API — `ms.reindexBatchSize` Test Suite

API test suite for the Fredhopper Controller, focused on the `ms.reindexBatchSize` service
property (Technical QA Assignment #11).

## Layout

```
docs/
  manual-test-cases.md        Task 2 — manual test cases
  findings-ticket.md          Task 3 — bugs and other findings
  ci-pipeline.md              Task 4 hint — CI integration write-up
  bonus-aws-zone-strategy.md  Bonus Task 5 — write-up, no code
src/test/java/.../client/    Request builders (RestAssured)
src/test/java/.../config/    Environment configuration
src/test/java/.../model/     Plain-text response parsing
src/test/java/.../data/      Test data, kept separate from test logic
src/test/java/.../base/      Shared setup/teardown + a polling helper
src/test/java/.../tests/     Task 4 — the test classes
```

Everything lives under `src/test/java` rather than `src/main/java`, since this is
test-automation tooling (request builders, config, parsing), not a reusable production
library, so there's no `src/main` here.

## Requirements

- JDK 17+
- Maven 3.9+

## Configuration

Nothing is hardcoded. Everything below can be overridden with a system property
(`-Dcontroller.xxx`) or an environment variable, and the defaults point at the live `task-11`
environment used for this assignment.

| Setting | System property | Env var | Default |
|---|---|---|---|
| Base URI | `controller.baseUri` | `CONTROLLER_BASE_URI` | `https://task-11-ctl.eu-west-1.fdp-qa.fredhopper.com/control` |
| Customer | `controller.customer` | `CONTROLLER_CUSTOMER` | `jdplc_09_etl2_ctl8-61dbe4da-20260610123310` (the spec's example customer, `demo01`, doesn't actually exist here) |
| Service | `controller.service` | `CONTROLLER_SERVICE` | `fas:live1` |
| Secondary customer (used only by the scope-isolation test) | `controller.secondaryCustomer` | `CONTROLLER_SECONDARY_CUSTOMER` | `new_look_test_etl2_ctl8-6aa33dc7-20260611085344` |
| Basic-auth username | `controller.username` | `CONTROLLER_USERNAME` | none |
| Basic-auth password | `controller.password` | `CONTROLLER_PASSWORD` | none |

This environment uses HTTP Basic auth. Set credentials as environment variables before
running, e.g. in PowerShell:

```powershell
$env:CONTROLLER_USERNAME = "..."
$env:CONTROLLER_PASSWORD = "..."
mvn test
```

## Running

```
mvn test
```

Tests that change `ms.reindexBatchSize` capture its value beforehand and restore it
afterward (see `BaseApiTest`), since this is a shared environment other runs may also be
using. Parallel execution is disabled (`src/test/resources/junit-platform.properties`) so
that restoration is reliable.

Last run against `task-11`: 24 tests, 2 failures. Both failures are expected, the `"0"` and
`"-1"` cases of `settingInvalidValueIsRejected`, which encode the correct behavior
(non-positive values should be rejected) and currently fail because the live API accepts
them instead. See `docs/findings-ticket.md` (BUG-1) for the full writeup.
