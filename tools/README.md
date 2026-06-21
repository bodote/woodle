# Tools

Operational/observability scripts for Woodle. Run from the repo root.

## WoodleLogStats.java — access-log analyzer

A self-contained [JBang](https://www.jbang.dev/) script that reads the API Gateway
access logs from CloudWatch Logs and reports traffic and error patterns for a stage.
It also pulls the CloudFront frontend request count from CloudWatch metrics.

### Prerequisites

- `jbang` installed (`brew install jbang`) — pulls Java 21 and the AWS SDK on first run.
- AWS credentials in the environment / default profile with read access to CloudWatch
  Logs (`logs:FilterLogEvents`), CloudWatch metrics (`cloudwatch:GetMetricStatistics`),
  in the relevant regions.

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
