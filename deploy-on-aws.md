# Deploy Woodle on AWS (Cost-Minimized at Idle)

## Goal
Deploy Woodle so that cost is as low as possible when no one is using the app, while keeping the setup simple and production-safe.

## Planned AWS Design

### Core Architecture
1. Frontend static site in Amazon S3 (`woodle-web-<env>` bucket).
2. Amazon CloudFront distribution in front of the frontend bucket.
3. API: Amazon API Gateway **HTTP API**.
4. Backend: AWS Lambda (Java/Spring) integrated with API Gateway.
5. Poll persistence: dedicated S3 bucket (`woodle-polls-<env>`), one object per poll.
6. IAM roles with least-privilege access.
7. CloudWatch Logs for API and Lambda.

### Domain targets (separate stacks per stage)
- QS stage frontend domain: `https://qs.woodle.click`
- QS stage API domain: `https://api.qs.woodle.click/v1`
- Production frontend domain: `https://woodle.click`
- Production API domain: `https://api.woodle.click/v1`

### Single-Domain UX Requirement
- Users must remain on the stage frontend host for the complete poll-creation flow (steps 1-3) and voting pages.
- QS example: `https://qs.woodle.click/...`
- Production example: `https://woodle.click/...`
- No browser-visible redirect to `https://*.execute-api.*.amazonaws.com/...` is allowed during normal user navigation.
- CloudFront must front both static assets and dynamic poll routes so app paths stay on the frontend domain.
- In admin view section **"Links zum Teilen"**, participant/admin links must be absolute URLs including protocol + host.
- URL generation must use the current request origin:
  - AWS production example: `https://woodle.click/poll/<pollId>`
  - AWS QS example: `https://qs.woodle.click/poll/<pollId>`
  - local example: `http://localhost:<port>/poll/<pollId>` (port must match the actual local server port, e.g. `8088`)

### Why this minimizes idle cost
- No EC2/ECS/RDS always-on compute.
- Lambda billed per request and execution duration.
- S3 billed mostly for storage and request volume.
- HTTP API is typically lower-cost than API Gateway REST API.
- No NAT Gateway (Lambda should run outside VPC unless strictly needed).

## Data Model and S3 Key Schema

### Buckets
- `woodle-web-<env>`: static frontend (HTML/CSS/JS/assets)
- `woodle-polls-<env>`: poll JSON documents

### Poll object key schema
Use one object per poll:

- `polls/{pollId}.json`

Where:
- `{pollId}`: URL-safe unique id, e.g. `01JY8T8KPF2VQ7D11W3S0G8N5B` (ULID)

### Optional secondary keys (only if needed later)
- `users/{ownerId}/polls/{pollId}` (index pointer object)
- `meta/recent/{yyyy}/{mm}/{dd}/{pollId}` (lightweight listing aid)

Start simple with only `polls/{pollId}.json`; add secondary keys only when query/listing needs become real.

## Poll JSON Shape (S3 object content)

```json
{
  "id": "01JY8T8KPF2VQ7D11W3S0G8N5B",
  "question": "Where should we have lunch?",
  "options": [
    {"id": "a", "text": "Italian"},
    {"id": "b", "text": "Sushi"}
  ],
  "settings": {
    "allowMultiple": false,
    "allowAddOption": false
  },
  "status": "OPEN",
  "createdAt": "2026-02-08T12:00:00Z",
  "updatedAt": "2026-02-08T12:00:00Z",
  "expiresAt": null,
  "version": 1
}
```

Notes:
- Keep payload compact; <1 KB is realistic for many polls.
- Store object as UTF-8 JSON (`Content-Type: application/json`).

## API Endpoints (HTTP API)

Base URL example:
- QS: `https://api.qs.woodle.click/v1`
- Production: `https://api.woodle.click/v1`

### Global API contract rules
- Content type: `application/json; charset=utf-8`
- Time format: RFC3339 UTC (example `2026-02-08T12:00:00Z`)
- Poll id format: ULID string
- `ETag` response header is returned for resources that can be updated
- `If-Match` request header is required for mutable admin operations

### Create poll
- `POST /v1/polls`

Request:
```json
{
  "authorName": "Alice",
  "authorEmail": "alice@example.com",
  "title": "Team lunch",
  "description": "Pick a date",
  "eventType": "ALL_DAY",
  "durationMinutes": null,
  "dates": ["2026-02-10", "2026-02-11"],
  "startTimes": [],
  "expiresAtOverride": null
}
```

Response `201 Created`:
```json
{
  "id": "01JY8T8KPF2VQ7D11W3S0G8N5B",
  "adminUrl": "/poll/01JY8T8KPF2VQ7D11W3S0G8N5B/admin",
  "voteUrl": "/poll/01JY8T8KPF2VQ7D11W3S0G8N5B",
  "etag": "\"3b6f...\""
}
```

Validation failures:
- `400 Bad Request` when required fields are missing or invalid

### Get poll
- `GET /v1/polls/{pollId}`

Response `200 OK`:
- Poll JSON body (id, title, description, eventType, durationMinutes, options, expiresAt)
- `ETag` response header present

Not found:
- `404 Not Found` if `pollId` does not exist

### Active poll count
- `GET /v1/polls/active-count` (same-origin alias: `/poll/active-count`)

Response `200 OK`:
- plain text integer count of active poll objects (`polls/*.json`)

### Update poll metadata/settings
- `PUT /v1/polls/{pollId}`
- Require `If-Match: <etag>` header to prevent lost updates.

Request:
```json
{
  "title": "Team lunch next week",
  "description": "Updated description",
  "expiresAt": "2026-03-10"
}
```

Response:
- `200 OK` with updated poll + new `ETag`
- `428 Precondition Required` when `If-Match` is missing
- `412 Precondition Failed` on stale ETag
- `404 Not Found` if `pollId` does not exist

### Delete poll (optional)
- `DELETE /v1/polls/{pollId}`
- Require admin authorization strategy.

Response:
- `204 No Content`

### Cast vote
- `POST /v1/polls/{pollId}/votes`

Request:
```json
{
  "participantName": "Alice",
  "votes": [
    {"optionId": "4cb46dca-f86d-4ce9-a3ec-daf2560f8bea", "value": "YES"}
  ],
  "comment": null
}
```

Response:
- `200 OK` with updated aggregated poll result
- `400 Bad Request` for invalid option ids or invalid vote payload
- `404 Not Found` if `pollId` does not exist

## Error Model (Phase 1 Contract)

All non-2xx responses return a structured error body:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "question must not be blank",
    "requestId": "5c1fd1f3-8a38-4d84-9b5d-d85c2b7a8c9a"
  }
}
```

Error code mapping:
- `400`: `VALIDATION_ERROR`
- `404`: `POLL_NOT_FOUND`
- `409` or `412`: `CONFLICT` / `PRECONDITION_FAILED`
- `428`: `PRECONDITION_REQUIRED`
- `500`: `INTERNAL_ERROR`

## Concurrency and Consistency

For S3-backed writes:
1. Read poll object and ETag.
2. Apply mutation in Lambda.
3. Client must send `If-Match` from last `GET` response.
4. Write with conditional semantics so write succeeds only if ETag/version is unchanged.
5. Return `428` when `If-Match` is missing.
6. Return `412` when state changed; client retries after fresh `GET`.

This avoids silent overwrite on concurrent admin edits.

## CORS Policy (CloudFront Frontend -> API)

Allow only known frontend origins:
- `https://qs.woodle.click`
- `https://woodle.click`
- `https://<cloudfront-distribution-domain>` (for rollout/testing)

Allowed methods:
- `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`

Allowed request headers:
- `Content-Type`, `If-Match`, `Authorization`, `X-Requested-With`, `HX-Request`, `HX-Current-URL`, `HX-Target`, `HX-Trigger`, `HX-Trigger-Name`

Exposed response headers:
- `ETag`, `Location`, `x-amzn-RequestId`

CORS operational notes:
- Do not use wildcard origin in production.
- Keep `Access-Control-Max-Age` moderate (for example 300-600s) during rollout.

## Security

1. Keep poll bucket private (no public read).
2. Lambda execution role permissions restricted to:
   - `s3:GetObject`, `s3:PutObject`, optional `s3:DeleteObject`
   - resource scope: `arn:aws:s3:::woodle-polls-<env>/polls/*`
   - `s3:ListBucket` on bucket resource `arn:aws:s3:::woodle-polls-<env>` for active poll counting
3. Enable server-side encryption (SSE-S3 or SSE-KMS).
4. Enable API throttling and request size limits in API Gateway.
5. Add CORS rules only for required frontend origins.

## Cost Guardrails

1. Create AWS Budget with alert thresholds (for example 50%, 80%, 100%).
2. Enable Cost Anomaly Detection.
3. Set CloudWatch log retention (for example 7-14 days for non-prod).
4. Use S3 Lifecycle only if you want automatic poll expiration/cleanup.

## Deployment Steps (High-Level)

1. Create `woodle-web-<env>` and `woodle-polls-<env>` buckets.
2. Deploy frontend assets to `woodle-web-<env>`.
3. Create Lambda function and API Gateway HTTP API routes.
4. Grant Lambda least-privilege IAM access to poll bucket prefix.
5. Configure CloudFront + custom domain per stage (`qs.woodle.click` for QS, `woodle.click` for prod) and API custom domain per stage (`api.qs.woodle.click`, `api.woodle.click`) with Route53 + ACM.
6. Add budget/anomaly alerts.
7. Smoke test:
   - create poll
   - read poll
   - update with valid and stale ETag
   - cast vote

### Backend Base URL for Frontend Runtime Config (Single Domain)

`aws-deploy.sh` writes `runtime-config.js` with `window.WOODLE_BACKEND_BASE_URL`.
It now selects the deployment stage by CLI argument:
- default (no argument): QS stage (`qs.woodle.click`)
- `-prod`: production stage (`woodle.click`)

- Default: empty value (`""`) so frontend posts to same origin (`https://<stage-domain>/...`).
- Optional override: set `WOODLE_BACKEND_BASE_URL` before running deploy (for split-domain setups).
- Safety: when override is set, deploy script normalizes it to `https://...` before upload.

For production single-domain UX, keep `WOODLE_BACKEND_BASE_URL` empty so forms use relative `/poll/...` paths via CloudFront.

QS default deploy example:

```bash
./aws-deploy.sh
```

Native-image deployment example (GraalVM build in Docker):

```bash
DEPLOY_RUNTIME=native ./aws-deploy.sh
```

Production deploy example:

```bash
./aws-deploy.sh -prod
```

For `DEPLOY_RUNTIME=native`, `aws-deploy.sh` runs a preflight check before deployment:
- verifies that the native Dockerfile exists
- verifies Docker buildx availability
- verifies that the selected buildx builder reports `linux/arm64` support

Optional dry run mode:
- set `DRY_RUN=true` to print resolved deployment configuration and run validations only
- script exits before any AWS (`aws`, `sam`) or Docker build/push actions

Optional split-domain override example:

```bash
WOODLE_BACKEND_BASE_URL=https://api.woodle.click \
./aws-deploy.sh -prod
```

### Post-Deploy Smoke Checklist (Single-Domain UX)

Run this in a browser against the target stage (replace host accordingly):

1. Open `https://qs.woodle.click/poll/new` (or `https://woodle.click/poll/new` for prod).
   - Expected URL stays on the same host.
2. Fill step 1 and submit to step 2.
   - Expected URL: `https://<stage-domain>/poll/step-2`.
3. Fill step 2 and submit to step 3.
   - Expected URL: `https://<stage-domain>/poll/step-3`.
4. Finish poll creation.
   - Expected participant/admin links are absolute and start with `https://<stage-domain>/poll/...`.
5. Open browser devtools (Network) and confirm:
   - No top-level navigation to `https://*.execute-api.*.amazonaws.com/...`.
   - No mixed-content warnings caused by `http://` backend links.

### Native Runtime Hardening Checklist (GraalVM on Lambda)

Use this checklist whenever `DEPLOY_RUNTIME=native` is enabled:

1. Dockerfile consistency:
   - builder stage and final runtime stage must be libc-compatible.
   - prefer Amazon Linux (glibc) in both stages.
2. Builder dependencies:
   - ensure `findutils` is installed (`xargs` must exist).
3. Cold start budget:
   - Lambda timeout must tolerate native + Spring startup during cold starts.
   - validate with CloudWatch `INIT_REPORT` after deployment.
4. CloudFront + domain safety:
   - if CloudFormation update fails with CNAME-already-associated errors, resolve DNS/CloudFront ownership before retrying.
5. Functional regression smoke:
   - verify participant row lifecycle end-to-end: create vote -> `Speichern` -> `Bearbeiten` -> change -> `Speichern`.

## Pre-Deploy Validation

What can be validated locally (high confidence):
1. Unit tests for domain and services.
2. API controller tests via MockMvc (`@WebMvcTest`).
3. S3 integration tests with LocalStack (`S3PollRepositoryIT`).
4. Frontend functional behavior via HtmlUnit + Playwright E2E.
5. End-to-end local stack: static frontend + local API + LocalStack S3.

What must be validated in AWS (cannot be fully proven locally):
1. IAM permissions and bucket policies.
2. API Gateway/Lambda integration behavior and timeout settings.
3. CloudFront caching/CORS/custom-domain TLS.
4. Real cold-start and latency behavior.
5. Cost/budget/anomaly alerts in actual billing pipeline.

## Definition of Done (Per Environment)
1. All tests pass (`test`, targeted `*IT`, key E2E).
2. Poll create/read/update/vote works via CloudFront frontend in AWS.
3. S3 objects stored under expected key schema.
4. Concurrency conflict returns expected status (`409`/`412`).
5. Budget alerts configured and verified.
6. No always-on compute resources in architecture.

## Suggested Execution Order
1. API contract and API controllers first.
2. S3 repository hardening next (concurrency and error semantics).
3. Frontend decoupling after API stability.
4. Lambda runtime integration and infrastructure deployment.
5. Progressive cutover and legacy path removal after validation.

## Future Evolution (only if needed)

- Add lightweight index objects for list/recent queries.
- Add signed admin tokens and stricter auth model.
- If query patterns become complex, evaluate DynamoDB later.
