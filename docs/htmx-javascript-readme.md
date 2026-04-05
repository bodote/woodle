# HTMX and Additional JavaScript in Woodle

## Summary

Woodle is intentionally built around server-rendered HTML, Thymeleaf fragments, and HTMX-driven partial updates. HTMX covers the main interaction model well:

- submit forms without full page reloads
- swap returned HTML fragments into the DOM
- keep routing simple with `hx-push-url`
- support inline editing and small admin actions with server-rendered responses

That is the default approach in this codebase. Additional JavaScript exists only where the browser must do work that HTMX does not handle well on its own, or where the page is intentionally static and cannot rely on server-side template rendering.

## Where HTMX Is Sufficient

HTMX is the right tool for the core CRUD-style UI flows:

- Step 1 submits to step 2 with `hx-post` and swaps the page body.
- Step 2 changes event-type-specific form sections with `hx-get`.
- Participant rows switch into edit mode with `hx-get` and save/delete with `hx-post` or `hx-delete`.
- Admin option changes update only the relevant fragment.
- Summary rows are refreshed with out-of-band swaps.

Examples:

- [src/main/resources/templates/poll/new-step1.html](/Users/bodo.te/dev/woodle/src/main/resources/templates/poll/new-step1.html)
- [src/main/resources/templates/poll/new-step2.html](/Users/bodo.te/dev/woodle/src/main/resources/templates/poll/new-step2.html)
- [src/main/resources/templates/poll/view.html](/Users/bodo.te/dev/woodle/src/main/resources/templates/poll/view.html)
- [src/main/resources/templates/poll/participant-row-edit.html](/Users/bodo.te/dev/woodle/src/main/resources/templates/poll/participant-row-edit.html)
- [src/main/resources/templates/poll/options-list.html](/Users/bodo.te/dev/woodle/src/main/resources/templates/poll/options-list.html)

These flows are fundamentally "server decides, browser swaps HTML". HTMX is sufficient there because the backend already owns validation, rendering, and resulting UI state.

## Why HTMX Alone Is Not Enough Here

HTMX is intentionally narrow. It is excellent for request/response HTML updates, but it does not aim to replace every browser capability. In Woodle, the extra JavaScript fills five concrete gaps.

### 1. Browser-local state and resilience

HTMX does not provide a built-in model for persisting draft form values in the browser. Woodle uses JavaScript on step 1 to:

- store author name, email, title, description, and notification preferences in `localStorage`
- restore those values on revisit
- keep a small title history for the datalist suggestions
- preserve the email input value even when HTMX replaces that field after validation

This logic lives in [src/main/resources/static/js/step1-runtime.js](/Users/bodo.te/dev/woodle/src/main/resources/static/js/step1-runtime.js).

Why JavaScript was used:

- `localStorage` access is purely client-side browser state
- datalist option generation is easier as a local DOM update than as repeated server round-trips
- the email field can be replaced by HTMX, so the browser needs to rehydrate the swapped element after `htmx:afterSwap`

Related fragment:

- [src/main/resources/templates/poll/step1-email-field.html](/Users/bodo.te/dev/woodle/src/main/resources/templates/poll/step1-email-field.html)

### 2. Runtime configuration for static pages

The real step-1 page is a static asset served from S3/CloudFront, not a Thymeleaf page. Because of that, the page cannot rely on server-side expression rendering at request time. JavaScript is used to read runtime globals and rewire endpoints at page load:

- `window.WOODLE_BACKEND_BASE_URL`
- `window.WOODLE_EMAIL_ENABLED`

From there, `step1-runtime.js` adjusts:

- form `action`
- `hx-post`
- active-count `hx-get`
- checkbox enabled/disabled state

Relevant files:

- [src/main/resources/static/poll/new-step1.html](/Users/bodo.te/dev/woodle/src/main/resources/static/poll/new-step1.html)
- [src/main/resources/static/runtime-config.js](/Users/bodo.te/dev/woodle/src/main/resources/static/runtime-config.js)
- [src/main/resources/static/js/step1-runtime.js](/Users/bodo.te/dev/woodle/src/main/resources/static/js/step1-runtime.js)

Why JavaScript was used:

- HTMX can consume attributes already present in the DOM, but it does not solve "this static page must adapt to deployment-time backend URLs and feature flags"
- the static-first architecture is deliberate for Lambda warm-up and CloudFront delivery speed

### 3. Retry orchestration and UX timing around transient backend failures

HTMX emits lifecycle events, but the policy for what to do after a `502`, `503`, or `504` is application-specific. Woodle uses JavaScript to:

- retry step-1 submit on transient gateway/backend errors
- retry the active poll count warm-up request
- delay the loading indicator to avoid flicker on fast responses

This also lives in [src/main/resources/static/js/step1-runtime.js](/Users/bodo.te/dev/woodle/src/main/resources/static/js/step1-runtime.js).

Why JavaScript was used:

- HTMX exposes the events, but the retry counters, retry delays, and request replay logic are custom behavior
- the UX rule "show the loading hint only after 200ms" is a timing concern outside normal HTML swapping

### 4. Layout measurement and responsive behavior

HTMX swaps HTML but does not measure element widths, track scroll position, or manage responsive affordances. Woodle adds JavaScript for participant-table usability:

- detect horizontal overflow
- show or hide the "scroll for more dates" hint
- keep sticky-edge state in sync while the table scrolls
- recompute hint layout on resize and after HTMX swaps
- watch element size changes with `ResizeObserver`

This logic lives in [src/main/resources/static/js/woodle-ui.js](/Users/bodo.te/dev/woodle/src/main/resources/static/js/woodle-ui.js).

Relevant template:

- [src/main/resources/templates/poll/view.html](/Users/bodo.te/dev/woodle/src/main/resources/templates/poll/view.html)

Why JavaScript was used:

- overflow detection depends on live DOM geometry
- CSS alone cannot express the full "measure widths, then resize and place the hint between sticky columns" behavior
- HTMX intentionally does not handle view-layer measurement logic

### 5. Imperative browser APIs and small client-side utilities

Some interactions are inherently browser-API driven rather than server-rendered:

- copying participant/admin links to the clipboard
- dynamically adding or removing intraday time input rows before submit
- resetting enhanced UI state after successful HTMX requests
- polling a readiness endpoint and then triggering a second request to replace the loading shell with real content

Relevant files:

- [src/main/resources/static/js/woodle-ui.js](/Users/bodo.te/dev/woodle/src/main/resources/static/js/woodle-ui.js)
- [src/main/resources/templates/poll/static-loader.html](/Users/bodo.te/dev/woodle/src/main/resources/templates/poll/static-loader.html)
- [src/main/resources/static/poll/static/loader.html](/Users/bodo.te/dev/woodle/src/main/resources/static/poll/static/loader.html)

Why JavaScript was used:

- clipboard support requires `navigator.clipboard` or a browser fallback
- adding/removing local form rows is immediate client-side composition, not a server-rendered state transition
- the loader flow is a small orchestration layer: poll `/ready`, inspect response text, then issue `htmx.ajax(...)` for the real fragment

## File-by-File Map

### `src/main/resources/static/js/step1-runtime.js`

Used for:

- local draft persistence
- title history datalist rendering
- runtime base URL wiring for the static page
- email-enabled feature behavior
- retry logic for transient failures
- delayed loading indicator
- restoration after HTMX field swaps

### `src/main/resources/static/js/woodle-ui.js`

Used for:

- clipboard copy actions
- admin intraday time row add/remove controls
- scroll hint measurement and positioning
- rebinding layout logic after HTMX swaps

### `src/main/resources/templates/poll/static-loader.html`
### `src/main/resources/static/poll/static/loader.html`

Used for:

- bootstrapping from a loading shell into the real poll fragment
- handling ready-state polling and 404 display logic

## Design Principle in This Repo

The codebase does not use JavaScript as a second application layer. The application state and rendered business UI still live primarily on the server.

The rule of thumb is:

- prefer HTMX when the interaction is "send user intent to the server and swap returned HTML"
- use JavaScript only when the browser itself must remember, measure, copy, retry, or orchestrate client-side behavior

That is why most of the UI is static HTML plus Thymeleaf plus HTMX, while the remaining JavaScript is small, targeted, and infrastructure-oriented rather than SPA-style.
