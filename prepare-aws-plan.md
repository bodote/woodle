# Prepare AWS Plan (Frontend S3/CloudFront + Backend API Lambda)

## Target Architecture
1. Static frontend (HTML/CSS/JS) hosted in S3 and served via CloudFront.
2. Backend as REST API on AWS Lambda behind API Gateway HTTP API.
3. Poll data persisted in S3 (`polls/{pollId}.json`).
4. No database.

## Guiding Constraints
1. Keep idle cost near zero (serverless only, no always-on compute).
2. Follow project TDD workflow for every behavior change.
3. Preserve existing domain/application logic where possible.

## Phase 1: Freeze API Contract First
1. Define API endpoints and payloads in `deploy-on-aws.md` (source of truth):
   - `POST /v1/polls`
   - `GET /v1/polls/{pollId}`
   - `PUT /v1/polls/{pollId}`
   - `POST /v1/polls/{pollId}/votes`
2. Define error model (400, 404, 409/412, 500).
3. Define concurrency behavior (ETag + `If-Match` for updates).
4. Define CORS policy for CloudFront origin.

Deliverable:
- Stable API contract section in docs before controller refactor.

## Phase 2: Introduce API Controllers (Keep Existing MVC Running)
1. Add new REST controllers under a new package (parallel to current Thymeleaf controllers).
2. Keep existing web controllers untouched initially to reduce risk.
3. Map current use cases (`CreatePollUseCase`, `ReadPollUseCase`, `SubmitVoteUseCase`, admin update use case) to JSON endpoints.
4. Add DTO classes with `DTO` suffix for public API payloads.

TDD steps per endpoint:
1. Add first API-level test and make it fail intentionally (assertion failure).
2. Self-review failing test correctness.
3. Implement minimal production code to pass.
4. Repeat endpoint-by-endpoint.

Deliverable:
- JSON API works locally while old MVC pages still work.

## Phase 3: Frontend Decoupling for Static Hosting
1. Start with an incremental split:
   - serve only `new-step1.html` as a static page from S3/CloudFront
   - keep all other pages backend-rendered initially
2. Make the static `new-step1.html` submit into the existing backend flow.
3. After API controllers are stable, migrate additional pages from Thymeleaf to static assets (HTML/JS/CSS).
4. Replace server-rendered form posts with `fetch()` calls to `/v1/...` API only for pages that are migrated.
5. Move view state handling (wizard progression, validation messages) into frontend JS as each page is migrated.
6. Keep visual/layout parity with current UX initially; optimize later.

TDD/Testing:
1. Add/adjust functional HTML tests to verify required elements and behavior.
2. Add focused Playwright E2E flow for:
   - create poll
   - open poll
   - vote
   - admin option update

Deliverable:
- `new-step1.html` is served statically and integrated with backend flow; remaining pages still work via backend rendering.

## Phase 4: Harden S3 Repository for Production Semantics
1. Keep key schema: `polls/{pollId}.json`.
2. Add optimistic concurrency handling for updates (ETag/version check).
3. Stop swallowing all exceptions in `findById`; distinguish not found vs transient errors.
4. Ensure strict JSON serialization compatibility for API DTOs and storage DAO.

TDD:
1. Add failing integration tests in `S3PollRepositoryIT` for concurrency + error semantics.
2. Implement minimal changes to pass.

Deliverable:
- Repository behavior is safe under concurrent writes and production-like failures.

## Phase 5: Lambda Runtime Integration
1. Add Lambda runtime adapter for Spring Boot API app.
2. Ensure routes are API-first (`/v1/**`) and work via Lambda event model.
3. Externalize all runtime config via env vars:
   - `WOODLE_S3_ENABLED=true`
   - `WOODLE_S3_REGION=<region>`
   - `WOODLE_S3_BUCKET=<bucket>`
   - no local endpoint in AWS
4. Package as Lambda container image.

Deliverable:
- App runs locally in Lambda-compatible container and serves API.

## Phase 6: Infrastructure as Code
1. Create IaC stack for:
   - S3 web bucket
   - S3 polls bucket (private)
   - CloudFront distribution
   - API Gateway HTTP API
   - Lambda function + IAM role
   - CloudWatch log groups + retention
   - Budget + anomaly alert basics
2. Lambda should run outside VPC unless a strict requirement appears.
3. IAM least privilege for poll bucket prefix only.

Deliverable:
- One repeatable deploy command for dev environment.

## Phase 7: Progressive Cutover
1. Deploy backend API first; smoke test with curl/Postman.
2. Deploy static frontend to S3/CloudFront.
3. Switch frontend API base URL to API Gateway custom domain.
4. Keep old monolith route as temporary fallback until confidence is high.
5. Remove legacy server-rendered paths when cutover is stable.

Deliverable:
- Fully split architecture in AWS.

## Local Test Coverage Before First AWS Deploy

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

## Definition of Done (per environment)
1. All tests pass (`test`, targeted `*IT`, key E2E).
2. Poll create/read/update/vote works via CloudFront frontend in AWS.
3. S3 objects stored under expected key schema.
4. Concurrency conflict returns expected status (409/412).
5. Budget alerts configured and verified.
6. No always-on compute resources in architecture.

## Suggested Execution Order for This Repo
1. Phase 1 + 2 first (API contract + API controllers).
2. Phase 4 next (S3 hardening), because API correctness depends on it.
3. Phase 3 frontend decoupling once API is stable.
4. Phase 5 + 6 deployability and infrastructure.
5. Phase 7 controlled cutover.
