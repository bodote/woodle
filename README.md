# Woodle

## Overview
Woodle is a date‑poll application. Users create a poll, share a participant link, and collect votes in a tabular view with inline editing. Admins manage options via a secret admin link.

## Tech Stack
- Java + Spring Boot
- Thymeleaf templates
- HTMX for partial updates
- Gradle build
- Amazon S3 persistence (no database)
- JaCoCo for coverage, Pitest for mutation testing

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
- `domain` depends on nothing else.
- `application` depends only on `domain`.
- `adapter` depends on `application` + `domain` (+ `config` if needed).
- `config` can depend on all; nothing depends on `config`.

## URL Patterns
- Create poll: `/poll/new`
- Participant link: `/poll/<UUID>`
- Admin link: `/poll/<UUID>-<admin-secret>`

## Persistence & Data Lifecycle
- No user accounts.
- Each poll has a UUID and a secret admin token.
- Polls are stored as a single JSON per UUID in S3.
- Polls are deleted after the expiry date.

## Product Spec (Date Poll)
The full, evolving product spec lives in:
- `/Users/bodo.te/dev/woodle/woodle-create-poll-date-spec.md`

It includes:
- Step‑1/2/3 creation flow
- Event type logic (all‑day vs. intraday)
- Admin view requirements
- Participant table with inline edit + add row

## Testing Strategy (Summary)
- Exactly one `@SpringBootTest` class (`*IT.java`) contains integration + Playwright E2E.
- Most tests are `@WebMvcTest` and focus on behavior/spec, not implementation details.
- HTML tests avoid layout details (colors, typography); they assert required elements, ordering, and behavior.
- Unit tests only when coverage cannot be achieved via `@WebMvcTest`.
- Coverage target: **95% instruction**, **90% branch**.

Full strategy:
- `/Users/bodo.te/dev/woodle/test-strategie.md`

## Running Tests
```bash
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
1. Start LocalStack (S3):
```bash
docker run -d --name woodle-localstack -p 4566:4566 localstack/localstack:3.1.0
docker exec woodle-localstack awslocal s3 mb s3://woodle
```

2. Start the app (S3 enabled):
```bash
./gradlew bootRun --args='--woodle.s3.enabled=true --woodle.s3.endpoint=http://localhost:4566 --woodle.s3.region=eu-central-1 --woodle.s3.pathStyle=true --woodle.s3.bucket=woodle'
```

3. Run E2E:
```bash
./gradlew test --tests '*E2E*'
```

4. Cleanup:
```bash
docker stop woodle-localstack && docker rm woodle-localstack
```

## Quality & Workflow
- Test‑first, incremental development.
- Qodana check:
```bash
docker run --rm -it -v "$PWD":/data -w /data jetbrains/qodana-jvm-community:latest
```
- Nullability: use JSpecify with `@NullMarked` in the base package via `package-info.java`.
- `DTO` suffix is reserved for public API transfer types only.
- Do not use `Optional` as a method parameter.

Operational note:
- If Playwright/Chrome hangs with “Wird in einer aktuellen Browsersitzung geöffnet”, fully quit Chrome and restart it.

## References
- Tech stack and architecture: `/Users/bodo.te/dev/woodle/woodle-tech-stack.md`
- Test strategy: `/Users/bodo.te/dev/woodle/test-strategie.md`
- Product spec: `/Users/bodo.te/dev/woodle/woodle-create-poll-date-spec.md`
