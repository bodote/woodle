# Static Loader Plan for Poll Pages

## Goal

Users should see a page **immediately** during slow cold starts, instead of waiting 10-20 seconds for the first response.

Desired flow:

1. Entry via `/poll/static/{pollId}`.
2. This URL always returns a static loader page from S3/CloudFront immediately.
3. The loader page polls the dynamic readiness URL in the background.
4. As soon as the backend is ready, HTMX loads the dynamic poll content and replaces the loader content on the same URL.
5. There is **no redirect** to a dynamic URL.

The text on the loader page:

`Bitte noch ein bischen Geduld, wir laden gerade die Umfrage`

## Architecture in the Current Project

- CloudFront in `infra/template.yaml` currently has a broad behavior `PathPattern: /poll*` pointing to `api-origin`.
- Static assets live in `src/main/resources/static` and are served via `web-bucket-origin`.
- Poll views are currently served via `@GetMapping("/poll/{pollId}")` in `PollViewController`.

For the new flow, we need a clean separation between the static entry route and the dynamic poll route.

## Implementation Plan

## Required Order (must happen exactly like this)

1. Create automated tests first, including the first intentionally failing test.
2. Implement the functionality.
3. Check test coverage.
4. If needed, improve tests/code until coverage and quality are sufficient.
5. Start the local server and verify with Playwright.
6. If there are failures, fix them locally and rerun Playwright until it works.
7. Deploy with native image.
8. Test again with Playwright against the deployed AWS URL.
9. If there are failures, fix and redeploy until it works.

## 1. Define URL Design and Routing

1. Public entry URL: `/poll/static/{pollId}`.
2. Dynamic fragment URL: `/poll/dynamic/{pollId}/fragment` (returns only the content to insert into the static page).
3. Readiness URL: `/poll/dynamic/{pollId}/ready` (lightweight, no template rendering logic).
4. Optional: keep full dynamic page `/poll/dynamic/{pollId}` only for internal/compatibility purposes, but do not use it as the user-facing target URL.

Reasoning: `CloudFront` can route `/poll/static/*` specifically to S3, while `/poll/dynamic/*` stays on the API.

## 2. Adjust CloudFront/SAM Template

File: `infra/template.yaml`

1. Add a new cache behavior for `PathPattern: /poll/static/*` to `web-bucket-origin`.
2. Restrict the existing API behavior from `/poll*` to `/poll/dynamic/*`.
3. Optional: add legacy redirects in the app (see step 3) so old links `/poll/{id}` do not break.
4. Control caching for the loader resource:
   - Loader HTML should ideally be short-lived or `no-store`.
   - CSS/JS should remain cacheable.

Infrastructure-level acceptance:

- Requests to `/poll/static/<uuid>` never hit Lambda.
- Requests to `/poll/dynamic/<uuid>/ready` and `/poll/dynamic/<uuid>/fragment` go to API/Lambda.

## 3. Add Backend Routes (Compatible Migration)

File: `src/main/java/io/github/bodote/woodle/adapter/in/web/PollViewController.java`

1. Add dynamic fragment route:
   - `GET /poll/dynamic/{pollId}/fragment`
   - Returns the HTML fragment that replaces the loader area.
2. Add readiness endpoint, for example:
   - `GET /poll/dynamic/{pollId}/ready`
   - `200 OK` if the poll is readable.
   - `404` if the poll does not exist.
   - `503` if infrastructure is not ready yet or times out.
3. Redirect legacy route `/poll/{pollId}` with `302` to `/poll/static/{pollId}`.

Recommendation: `/poll/static/{pollId}` is the canonical user URL.

## 4. Create Static Loader Page

New file (proposal):

- `src/main/resources/static/poll/static/index.html`

Additional path strategy:

- Either provide exactly one static HTML file per pattern via CloudFront Function URL rewrite
- or, more simply, rewrite `/poll/static/{id}` to one fixed object via CloudFront Function, for example `/poll/static/loader.html`.

Because S3 does not understand path parameters, a rewrite is required for "any `{id}` should serve the same file".

Loader page content:

1. Same base visual language as the rest of the pages:
   - same CSS file `/css/app.css`
   - same structure with `page-shell`, header, and card components.
2. Visible spinner (CSS animation, accessible).
3. Text exactly:
   - `Bitte noch ein bischen Geduld, wir laden gerade die Umfrage`
4. HTMX-based behavior:
   - read `pollId` from the URL.
   - a container (for example `#poll-content`) initially contains spinner + waiting text.
   - HTMX polls `/poll/dynamic/{pollId}/ready` every 1-2 seconds.
   - once `ready` = `200`, HTMX triggers a request to `/poll/dynamic/{pollId}/fragment`.
   - HTMX replaces the content of `#poll-content` with the dynamic fragment.
   - on `404`, show a clear error message instead of an endless spinner.
   - after a maximum wait time (for example 60s), show a "try again" hint.

## 5. Keep the Same Layout Language

Files:

- `src/main/resources/static/css/app.css`
- new loader HTML file

1. The loader uses existing design tokens/components instead of a separate theme.
2. Add only minimal CSS for spinner + status text.
3. No visual deviation from the Woodle look and feel (colors, typography, card layout).

## 6. TDD Implementation Steps (Project-Conformant)

Order must stay small and incremental:

1. **First failing API test** for the new behavior:
   - for example in `PollViewControllerTest` or a new test for `/poll/dynamic/{pollId}/ready`.
   - assertions fail initially while compilation still succeeds.
2. Implement readiness + fragment route.
3. Make tests green.
4. Extend infrastructure test:
   - add new path patterns (`/poll/static/*`, `/poll/dynamic/*`) to `CloudFrontSingleDomainRoutingTest`.
5. Add HTML functional tests:
   - structure test for loader elements (spinner, text, HTMX attributes).
   - optional Playwright smoke test: loader appears first, then is replaced in place by dynamic content.

Note: for this change, prioritize API/WebMvc tests and infrastructure template tests.

## 6.1 Mandatory Delivery Workflow

Implementation follows this fixed order:

1. Create automated tests first, at minimum the first failing API test required by the TDD gate.
2. Implement the functionality.
3. Run the full test suite and check coverage.
4. If coverage/quality is insufficient, add targeted tests and harden the code.
5. Start the local server and test the end-to-end flow with Playwright.
6. If there are issues, iterate locally until Playwright is stably green.
7. Deploy native image (`DEPLOY_RUNTIME=native` path).
8. After deployment, test again with Playwright against the AWS URL.
9. If there are issues, fix and redeploy until the flow works reliably in AWS.

## 7. CloudFront URL Rewrite for Static ID Route

Because `/poll/static/{id}` should always return the same file, an edge rewrite is required.

Option A (recommended): CloudFront Function (Viewer Request)

1. If the path matches `/poll/static/<uuid>`, rewrite the URI internally to `/poll/static/loader.html`.
2. Leave the query string unchanged.
3. Pass through all other paths unchanged.

Option B: Lambda@Edge (only if CloudFront Function is insufficient).

Acceptance:

- Any `/poll/static/<uuid>` returns the same loader HTML.
- JavaScript still receives the original URL including the poll ID.

## 8. Backward Compatibility and Link Generation

1. Switch all newly generated participant links to `/poll/static/{pollId}`.
2. Switch the admin link to the static URL as well:
   - instead of a dynamic target URL, also use the static loader URL.
3. Adjust the admin email so it always contains the static link.
4. Continue supporting existing shared links `/poll/{pollId}` via redirect to `/poll/static/{pollId}`.

Affected places to check:

- URL generation in controller/template (`participantShareUrl`, `adminShareUrl` in `PollViewController`).
- Email generation in the outbound email flow (`PollCreatedEmail` / sender implementations).

## 9. Deployment and Smoke Checks

Before deployment (locally):

1. Run `./gradlew test` and `./gradlew check`.
2. Check coverage and improve it if needed (`./gradlew jacocoTestReport`).
3. Start the local server.
4. Run the Playwright scenario locally:
   - `/poll/static/{id}` renders the loader immediately.
   - waiting text is visible.
   - when readiness succeeds, content is replaced via HTMX.
   - no redirect URL to `/poll/dynamic/...` appears in the browser.
5. If it fails, fix and rerun Playwright locally until green.

After deployment in AWS (native):

1. Perform native deployment.
2. Open `https://qs.woodle.click/poll/static/<existing-id>`:
   - loader appears immediately.
3. During cold start:
   - loader remains visible; there is no empty browser tab.
4. Once the app is warm:
   - HTMX replaces the loader content with dynamic poll content on the same URL.
5. For a non-existent poll ID:
   - clean error path, no endless spinner.
6. Share links:
   - `Admin-Link` shows `/poll/static/{id}`.
   - `Link für Teilnehmende` shows `/poll/static/{id}`.
7. Admin email:
   - contains the static link `/poll/static/{id}`.
8. Run the Playwright scenario against AWS.
9. If it fails, fix, redeploy, and rerun Playwright until green.

Additionally, continue to run the existing guardrail smoke checks for poll editing.

## 10. Done Criteria

Implementation is done when:

1. `/poll/static/{id}` always renders a page immediately.
2. The loader page uses the Woodle layout and shows spinner + text
   `Bitte noch ein bischen Geduld, wir laden gerade die Umfrage`.
3. No redirect to a dynamic URL occurs.
4. Loader content is replaced via HTMX with dynamic content once readiness is positive.
5. Both share links (`Admin-Link`, `Link für Teilnehmende`) are static links.
6. The admin email contains the static link.
7. CloudFront routing is covered by tests (`CloudFrontSingleDomainRoutingTest`).
8. Web/API behavior is covered by tests (WebMvc + optional E2E smoke).
9. `./gradlew check` is green.
10. Coverage has been reviewed and improved with additional tests where needed.
11. Playwright is green locally.
12. Playwright is green after native deployment in AWS.

## Planned File Changes (During Implementation)

- `infra/template.yaml`
- `src/main/java/io/github/bodote/woodle/adapter/in/web/PollViewController.java`
- `src/main/resources/static/poll/static/loader.html` (or equivalent static path)
- `src/main/resources/static/css/app.css`
- `src/test/java/io/github/bodote/woodle/infra/CloudFrontSingleDomainRoutingTest.java`
- `src/test/java/io/github/bodote/woodle/adapter/in/web/...` (new/extended tests for dynamic/ready)
- `src/main/java/io/github/bodote/woodle/application/port/out/PollCreatedEmail.java`
- `src/main/java/io/github/bodote/woodle/adapter/out/email/...` (if link generation happens there)
