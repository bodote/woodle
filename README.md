# Woodle

## Overview

Woodle is a date‑poll application. Users create a poll, share a participant link, and collect votes in a tabular view with inline editing. Admins manage options via a secret admin link.

## Tech Stack

*   Java + Spring Boot
*   Thymeleaf templates
*   HTMX for partial updates
*   Gradle build
*   Amazon S3 persistence (no database)
*   JaCoCo for coverage, Pitest for mutation testing

## AWS Resources and Running Costs

The app uses these AWS resources in production:

1.  Amazon S3 (static frontend bucket: `woodle-web-<env>`)
2.  Amazon S3 (poll data bucket: `woodle-polls-<env>`)
3.  Amazon CloudFront (CDN for frontend)
4.  Amazon API Gateway HTTP API (public API entry point)
5.  AWS Lambda (Spring backend runtime)
6.  Amazon CloudWatch Logs (Lambda + API logs)
7.  AWS Route 53 (DNS for `woodle.click` and `api.woodle.click`)
8.  AWS Certificate Manager (TLS certificates for domains)
9.  AWS Budgets + Cost Anomaly Detection (cost monitoring)

Resources that generate the primary running costs:

*   `Amazon S3`: storage, PUT/GET requests, and data transfer out
*   `CloudFront`: requests and data transfer out
*   `API Gateway HTTP API`: requests (and payload processing)
*   `AWS Lambda`: invocations and execution duration (plus memory size)
*   `CloudWatch Logs`: log ingestion and retention storage
*   `Route 53`: hosted zone and DNS query volume

Cost behavior at idle:

*   No always-on compute (no EC2/ECS/RDS), so idle cost is mostly S3/CloudFront/API baseline traffic, DNS, and retained logs.

## Architecture

Hexagonal structure with clear boundaries:

```
io.github.bodote.woodle
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
├── domain
│   ├── model
│   ├── event
│   └── exception
├── adapter
│   ├── in
│   │   └── web
│   └── out
│       ├── persistence
│       └── integration
└── config
```

Rules:

*   `domain` depends on nothing else.
*   `application` depends only on `domain`.
*   `adapter` depends on `application` + `domain` (+ `config` if needed).
*   `config` can depend on all; nothing depends on `config`.

## URL Patterns

*   Create poll: `/poll/new`
*   Participant link: `/poll/<UUID>`
*   Admin link: `/poll/<UUID>-<admin-secret>`

## Persistence & Data Lifecycle

*   No user accounts.
*   Each poll has a UUID and a secret admin token.
*   Polls are stored as a single JSON per UUID in S3.
*   Polls are deleted after the expiry date.

## Product Spec (Date Poll)

The full, evolving product spec lives in:

*   `/Users/bodo.te/dev/woodle/woodle-create-poll-date-spec.md`

It includes:

*   Step‑1/2/3 creation flow
*   Event type logic (all‑day vs. intraday)
*   Admin view requirements
*   Participant table with inline edit + add row

## Testing Strategy (Summary)

*   Exactly one `@SpringBootTest` class (`*IT.java`) contains integration + Playwright E2E.
*   Most tests are `@WebMvcTest` and focus on behavior/spec, not implementation details.
*   HTML tests avoid layout details (colors, typography); they assert required elements, ordering, and behavior.
*   Unit tests only when coverage cannot be achieved via `@WebMvcTest`.
*   Coverage target: **95% instruction**, **90% branch**.

Full strategy:

*   `/Users/bodo.te/dev/woodle/test-strategie.md`

## Running Tests

```
# Unit tests (fast)
./gradlew test

# Integration tests only
./gradlew test --tests '*IT'

# Coverage
./gradlew jacocoTestReport

# Mutation testing
./gradlew pitest
```

## Local E2E (Playwright + LocalStack)

Start LocalStack (S3):

Start the app (S3 enabled):

Run E2E:

Cleanup:

## Quality & Workflow

*   Test‑first, incremental development.
*   Qodana check:
*   Nullability: use JSpecify with `@NullMarked` in the base package via `package-info.java`.
*   `DTO` suffix is reserved for public API transfer types only.
*   Do not use `Optional` as a method parameter.

Operational note:

*   If Playwright/Chrome hangs with “Wird in einer aktuellen Browsersitzung geöffnet”, fully quit Chrome and restart it.

## References

*   Tech stack and architecture: `/Users/bodo.te/dev/woodle/woodle-tech-stack.md`
*   Test strategy: `/Users/bodo.te/dev/woodle/test-strategie.md`
*   Product spec: `/Users/bodo.te/dev/woodle/woodle-create-poll-date-spec.md`

```
docker run --rm -it -v "$PWD":/data -w /data jetbrains/qodana-jvm-community:latest
```

```
docker stop woodle-localstack && docker rm woodle-localstack
```

```
./gradlew test --tests '*E2E*'
```

```
./gradlew bootRun --args='--woodle.s3.enabled=true --woodle.s3.endpoint=http://localhost:4566 --woodle.s3.region=eu-central-1 --woodle.s3.pathStyle=true --woodle.s3.bucket=woodle'
```

```
docker run -d --name woodle-localstack -p 4566:4566 localstack/localstack:3.1.0
docker exec woodle-localstack awslocal s3 mb s3://woodle
```