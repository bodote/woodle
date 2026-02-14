# TODO: Fix PR #4 Follow-Up Findings

## Scope

This plan addresses exactly two review findings:

1. Session churn caused by automatic prewarm request on `/poll/new-step1.html`.
2. Supply-chain/runtime risk from loading HTMX via third-party CDN without integrity/CSP hardening.

## Execution Plan

### 1) Remove automatic prewarm side effect (P1)

Goal: Avoid creating `HttpSession` state for anonymous page views before the user interacts with the wizard.

Steps:

1. Add a failing test first:
   - New API-level test in web layer that verifies loading step 1 does not trigger an automatic request that initializes wizard session state.
   - Keep compile green and fail on assertion first (TDD gate).
2. Remove the page-load prewarm fetch in `/src/main/resources/static/poll/new-step1.html`.
   - Delete the unconditional `fetch(.../poll/step-2)` block.
3. Keep the existing submit flow unchanged:
   - Form still posts to `/poll/step-2`.
   - `PollNewPageController#handleStep1` continues to initialize wizard state only after user submit.
4. Optional hardening (if desired in same change):
   - Add a dedicated lightweight health/preload endpoint that does not touch session state.
   - Use that endpoint only if prewarm is still needed for latency goals.
5. Re-run relevant tests and ensure no regression in wizard flow.

### 2) Eliminate untrusted runtime CDN dependency (P2)

Goal: Serve HTMX from trusted app-controlled assets.

Steps:

1. Add a failing test first:
   - Extend static page test to assert no `https://unpkg.com/...` script is referenced.
   - Assert HTMX is loaded from a local path (for example `/js/htmx.min.js`).
2. Vendor HTMX into static assets:
   - Add minified HTMX file under `/src/main/resources/static/js/`.
   - Pin version in filename or via comment in file header.
3. Update `/src/main/resources/static/poll/new-step1.html`:
   - Replace CDN script tag with local script path.
   - Keep existing runtime wiring for `hx-get` behavior.
4. Optional defense-in-depth:
   - Add/verify response CSP headers to restrict script sources to self.
5. Re-run static page and controller tests.

## Validation Checklist

Run with elevated permissions in this environment:

1. `./gradlew test --tests 'io.github.bodote.woodle.adapter.in.web.StaticPollStep1PageIT'`
2. `./gradlew test --tests 'io.github.bodote.woodle.adapter.in.web.PollWizardFlowTest'`
3. `./gradlew test --tests 'io.github.bodote.woodle.adapter.in.web.PollApiControllerTest'`
4. `./gradlew test`
5. Optional: `./gradlew check`

## Done Criteria

- `new-step1.html` no longer performs automatic prewarm fetch to `/poll/step-2`.
- Step 1 page no longer references HTMX from `unpkg.com`; it uses a local static asset.
- Existing wizard and active count behavior still works.
- Targeted tests and full unit test suite pass.
