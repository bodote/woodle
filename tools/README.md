# Tools

Operational/observability scripts for Woodle. Run from the repo root.

## WoodleLogStats.java — access-log analyzer

A self-contained [JBang](https://www.jbang.dev/) script that reads the API Gateway
access logs from CloudWatch Logs and reports traffic and error patterns for a stage.
It also pulls the CloudFront frontend request count from CloudWatch metrics.

### Prerequisites

- `jbang` installed (`brew install jbang`) — pulls Java 21 and the AWS SDK on first run.
- AWS credentials in the environment / default profile with read access to CloudWatch
  Logs (`logs:FilterLogEvents`, `logs:DescribeLogGroups`), CloudWatch metrics
  (`cloudwatch:GetMetricStatistics`), in the relevant regions.

### Usage

```bash
jbang tools/WoodleLogStats.java [--env prod] [--days 14] [--region eu-central-1] \
                                [--cf-dist <distributionId>] [--show-errors 30]
```

| Flag | Default | Meaning |
|------|---------|---------|
| `--env` | `prod` | Stage; selects log group `/aws/apigateway/woodle-<env>` (`prod`, `qs`, `dev`). |
| `--days` | `14` | Look-back window. Capped by the log group's 14-day retention. |
| `--region` | `eu-central-1` | Region of the API Gateway log group. |
| `--cf-dist` | _(none)_ | CloudFront distribution id; adds the frontend daily request totals (metric is read from `us-east-1`). woodle.click = `ECIPVF7FI5V4B`. |
| `--show-errors` | `30` | Max number of `status >= 400` events to print. |
| `--lambda-log-group` | _(auto)_ | App Lambda log group for the cleanup section. Auto-discovered from the prefix `/aws/lambda/woodle-<env>-AppFunction`; override if your stack/function name differs. |

Examples:

```bash
# Production, last 14 days, with woodle.click frontend counts
jbang tools/WoodleLogStats.java --env prod --days 14 --cf-dist ECIPVF7FI5V4B

# QS stage, last 7 days
jbang tools/WoodleLogStats.java --env qs --days 7
```

### What it reports

- **Distinct source IPs** — total, split into `/v1` API callers vs frontend/other paths.
- **Requests per day** — total · `/v1` API · other · errors, with a TOTAL row.
- **HTTP status distribution** and **top 15 paths**.
- **Error events** (`status >= 400`) with timestamp, status, method, path, source IP.
- **Poll cleanup job** — `POLL_CLEANUP` events from the app Lambda log (read from
  `/aws/lambda/woodle-<env>-AppFunction…`): each run's *started / found N / deleted N of M*
  lines, plus a summary of how many runs fired and how many polls were deleted in the window.
  The weekly cleanup is invoked directly by EventBridge Scheduler (it does **not** traverse
  API Gateway), so it does not appear in the access-log sections above — only here.
- **CloudFront frontend daily request counts** (with `--cf-dist`).

### Important caveats (what these logs can and cannot tell you)

- **No Host header in the API Gateway access log**, so `woodle.click` vs `api.woodle.click`
  cannot be separated here. The script splits by path instead: `/v1*` = API, everything
  else = frontend/other. Much of "other" is bot/scanner noise (`/.git/config`, `/actuator/*`).
- **Frontend distinct-IP counts are not available.** CloudFront access logging is disabled
  on the distributions, so `--cf-dist` yields aggregate request counts only — no per-IP data.
  To get per-IP frontend data you'd need to enable CloudFront standard logging (v2 → CloudWatch).
- The `/aws/apigateway/woodle-*` log groups have **14-day retention**, so `--days` beyond 14
  returns nothing older.

### Related

- Used in the [503 Throttling Runbook](../deploy-on-aws.md) to confirm throttle-driven `503`s
  disappear after the Lambda account concurrency limit is raised.

## PostDeploySmokeTest.java — post-deploy native smoke test

A self-contained [JBang](https://www.jbang.dev/) script that verifies a live Woodle
deployment behaves correctly. It targets the failure modes that pass on the JVM but can
break only in a real GraalVM **native** image — and which the build's AOT step does not
catch: Thymeleaf/SpEL rendering of the step-2 option fragments, and Jackson
`@RequestBody` deserialization of `CleanupEventDTO` on `/events`. It hits the **API
Gateway** URL directly (not CloudFront) so caching cannot mask backend errors.

`aws-deploy.sh` runs it automatically at the end of a native deploy — both qs and prod
(`DEPLOY_RUNTIME=native ./aws-deploy.sh [-prod]`); a smoke failure aborts before the
success message. It can also be run by hand.

### Prerequisites

- `jbang` installed (`brew install jbang`). No AWS SDK or credentials needed — it only
  makes HTTP requests, using the JDK's built-in `HttpClient` (no `//DEPS`).

### Usage

```bash
jbang tools/PostDeploySmokeTest.java <apiBaseUrl> [publicBaseUrl]
```

| Arg | Required | Meaning |
|-----|----------|---------|
| `apiBaseUrl` | yes | API Gateway base URL, e.g. `https://xxxx.execute-api.eu-central-1.amazonaws.com` (stack output `ApiBaseUrl`). |
| `publicBaseUrl` | no | Public/CloudFront origin, e.g. `https://qs.woodle.click`; adds the static `new-step1.html` check. |

Exit code `0` = all checks passed, `1` = at least one failed, `2` = usage error.

### Checks (all non-destructive)

- `GET /poll/active-count` → `200` (backend is up).
- `POST /poll/step-2` → `200` and the body contains a `dateOption1` input (native
  Thymeleaf/SpEL render of the option fragment works).
- `POST /events` with a deliberately **invalid** token → `403` (the cleanup controller and
  `CleanupEventDTO` deserialization loaded in native; the bad token means no real poll
  deletion is triggered).
- `GET <publicBaseUrl>/poll/new-step1.html` → `200` (static entrypoint, when given).

It waits out Lambda cold start (polls `active-count` until `200`) before asserting.

### Coverage caveat

This is a **fast confidence check on critical paths, not exhaustive** native verification.
A missing reflection hint or bad SpEL is only caught if the corresponding code path is
actually executed. It currently does **not** exercise the intraday `step2-datetime-options`
fragment, the full poll lifecycle (`poll/view` for participant/admin, voting, admin option
editing — each with their own template model types), the `/events` success path, or legacy
wizard-state deserialization. The only way to detect a *missing* hint across all paths is to
build/run a real native image — the `nativeTest` Gradle task (reachability analysis over the
JUnit suite) or the deploy itself.
