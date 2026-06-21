# Weekly cleanup of expired polls

## Goal
Delete poll objects from S3 once their `expiresAt` date has passed. Run automatically
once a week in AWS, with no manual intervention.

## Trigger mechanism (decided)
The Lambda runs the Spring app behind the **AWS Lambda Web Adapter** (see
`Dockerfile.lambda`). The adapter forwards a **non-HTTP** Lambda invocation (e.g. an
EventBridge Scheduler event) as an HTTP `POST` to its pass-through path (`/events` by
default, env `AWS_LWA_PASS_THROUGH_PATH`).

So: **EventBridge Scheduler (weekly) → invokes the Lambda directly → Web Adapter POSTs
the schedule payload to `/events` → a Spring controller runs the cleanup.**

No API destination / connection resource is needed (simpler than calling the public URL).

### Security
The public HTTP API proxies all paths to the Lambda, so `/events` is *also* reachable
publicly. Guard it with a shared secret carried **in the request body** (EventBridge →
direct Lambda invoke cannot set HTTP headers, only the event payload, which the adapter
maps to the body). The schedule's `Input` JSON carries the token; the controller compares
it to a configured value. If the configured token is blank, the endpoint is disabled
(rejects everything) — safe default.

## Design (hexagonal, matches existing structure)

### Port (out) — `PollRepository`
Add two methods:
- `List<UUID> findExpiredPollIds(LocalDate asOf)` — ids whose `expiresAt` is non-null and
  strictly before `asOf` (repository "query method", mirrors `countActivePolls`).
- `void deleteById(UUID pollId)`.

### Adapters (out)
- `S3PollRepository`:
  - `findExpiredPollIds(asOf)`: paginate `polls/` keys (reuse the `countActivePolls`
    loop), `getObject` each `.json`, deserialize to `PollDAO`, keep ids where
    `expiresAt != null && expiresAt.isBefore(asOf)`.
  - `deleteById(id)`: `s3Client.deleteObject` on key `polls/{id}.json`.
- `InMemoryPollRepository`: implement both over the in-memory map.

### Port (in) — new use case
- `CleanupExpiredPollsUseCase` with `int cleanupExpiredPolls()` (returns number deleted).

### Application service
- `CleanupExpiredPollsService implements CleanupExpiredPollsUseCase`, constructed with
  `(PollRepository, Clock)`. Logic: `today = LocalDate.now(clock)`;
  `ids = repo.findExpiredPollIds(today)`; delete each; log a summary; return `ids.size()`.
  `Clock` keeps the "now" decision testable.

### Adapter (in) — controller
- `PollCleanupController` (`adapter.in.web`), `@PostMapping("/events")`:
  - body → `CleanupEventDTO(String task, String token)` (DTO suffix required by ArchUnit
    in `adapter.in.web`).
  - configured token via `@Value("${woodle.cleanup.token:}")`.
  - reject (`403`) if configured token blank, or token mismatch.
  - act only when `task == "cleanup-expired-polls"`; otherwise `403`.
  - on success call the use case, return `200` with `{"deleted": n}`.

### Wiring — `ApplicationConfig`
- `@Bean @ConditionalOnMissingBean Clock clock()` → `Clock.systemUTC()`.
- `@Bean CleanupExpiredPollsUseCase cleanupExpiredPollsUseCase(PollRepository, Clock)`.

### Config — `application.properties`
- `woodle.cleanup.token=${WOODLE_CLEANUP_TOKEN:}`.

### Infra — `infra/template.yaml`
- New parameter `CleanupToken` (`Type: String`, `NoEcho: true`, `Default: ""`).
- `AppFunction` env: `WOODLE_CLEANUP_TOKEN: !Ref CleanupToken`.
- `AppFunction.Events.WeeklyCleanup` of `Type: ScheduleV2`:
  - `ScheduleExpression: cron(0 3 ? * SUN *)` (Sundays 03:00 UTC).
  - `Input: !Sub '{"task":"cleanup-expired-polls","token":"${CleanupToken}"}'`.
- IAM: `s3:DeleteObject` on `polls/*` already granted — no change.
- `aws-deploy.sh`: optionally add `CleanupToken=${WOODLE_CLEANUP_TOKEN}` to
  `PARAMETER_OVERRIDES` (kept out of scope unless wanted; default empty = disabled).

## Test-first plan (write tests, watch them fail, then implement)
1. `CleanupExpiredPollsServiceTest` — fixed `Clock` + mock `PollRepository`: deletes exactly
   the ids returned by `findExpiredPollIds(today)`, returns the count; zero case deletes nothing.
2. `InMemoryPollRepositoryTest` (new) — `findExpiredPollIds` filters by date; `deleteById`
   removes; null `expiresAt` never expires; boundary (expiresAt == today → not expired).
3. `S3PollRepositoryTest` additions — `deleteById` issues `deleteObject` with key
   `polls/{id}.json`; `findExpiredPollIds` lists + filters by deserialized `expiresAt`.
4. `PollCleanupControllerTest` (`@WebMvcTest`) — valid token+task → 200 + use case invoked;
   wrong token → 403, use case never called; blank configured token → 403.

## Out of scope
- No deletion of orphaned `drafts/` (separate concern; drafts already deleted on completion).
- No change to expiry calculation in `CreatePollService`.
