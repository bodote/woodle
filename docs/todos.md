# TODOs from Code Review

## Critical

### 1) Prevent lost updates on concurrent writes
- Problem:
  - Vote/admin updates use read-modify-write on the full poll document without optimistic locking.
  - Concurrent requests can overwrite each other (last writer wins).
- Where:
  - `src/main/java/io/github/bodote/woodle/application/service/SubmitVoteService.java`
  - `src/main/java/io/github/bodote/woodle/adapter/out/persistence/S3PollRepository.java`
- Action:
  - Add optimistic concurrency control (ETag/version + conditional write), or change persistence model to merge-safe writes.

## High / Medium

### 2) Harden vote input validation
- Problem:
  - Vote parsing trusts request params too much.
  - Invalid enum/UUID values can throw runtime exceptions.
  - Option IDs are not validated against poll options before saving.
- Where:
  - `src/main/java/io/github/bodote/woodle/adapter/in/web/PollVoteController.java`
  - `src/main/java/io/github/bodote/woodle/application/service/SubmitVoteService.java`
- Action:
  - Validate option IDs against allowed poll options.
  - Convert malformed values to controlled `400 Bad Request` responses.
  - Reject unknown vote params explicitly.

### 3) Avoid repeated writes when admin adds multiple intraday times
- Problem:
  - Admin add-option loops over start times and performs one use-case call/write per time.
  - This increases latency and race-window size.
- Where:
  - `src/main/java/io/github/bodote/woodle/adapter/in/web/PollAdminOptionsController.java`
- Action:
  - Add a bulk add operation in use-case/service/repository path and persist once.

### 4) Do not mask backend failures as `0` in active-count endpoint
- Problem:
  - `/v1/polls/active-count` and `/poll/active-count` return `"0"` when repository fails.
  - This hides outages and makes monitoring misleading.
- Where:
  - `src/main/java/io/github/bodote/woodle/adapter/in/web/PollApiController.java`
- Action:
  - Return `503` (or structured error) on backend failure and log with context.

### 5) Expand security headers baseline
- Problem:
  - Referrer policy is enforced, but broader browser hardening headers are missing.
- Where:
  - `src/main/java/io/github/bodote/woodle/config/ReferrerPolicyFilter.java`
  - `infra/template.yaml` (CloudFront response headers policy)
- Action:
  - Add CSP, HSTS, X-Content-Type-Options, and frame protections (`X-Frame-Options` or `frame-ancestors`).

### 6) Reduce loader polling pressure and improve resilience
- Problem:
  - Static loader polls every 1.5 seconds without backoff/retry cap.
  - Non-404 failures are not surfaced well for diagnosis.
- Where:
  - `src/main/resources/templates/poll/static-loader.html`
- Action:
  - Add capped retries + exponential backoff/jitter.
  - Show user-facing error for repeated non-404 failures.
  - Log/debug marker to distinguish backend unready vs. broken.

### 7) Keep adapter boundary clean for active-count
- Problem:
  - Web adapter depends directly on `PollRepository` for count instead of a use case.
- Where:
  - `src/main/java/io/github/bodote/woodle/adapter/in/web/PollApiController.java`
- Action:
  - Introduce `CountActivePollsUseCase` and move repository usage into application service.

### 8) Add Lambda observability and runtime guardrails in IaC
- Problem:
  - No explicit tracing/alarms/concurrency controls configured in template.
- Where:
  - `infra/template.yaml`
- Action:
  - Enable tracing.
  - Add CloudWatch alarms for errors, throttles, duration, and 5xx.
  - Define reserved/provisioned concurrency per stage where required.

