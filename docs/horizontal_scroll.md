# Plan: Horizontal Scrolling Only for Date Columns in the Participant View

## Summary
The existing participant view remains a single HTMX-compatible table. Instead of splitting the table into multiple sections, the current structure should be adjusted so that only the date column area scrolls horizontally, while the left `Teilnehmende` column and the right `Bearbeiten` column remain permanently visible via CSS `position: sticky`. This is the smallest and most robust change because all existing HTMX row replacements continue to work on the same table structure.

## Planned Changes
- TDD first in `PollParticipantViewTest`:
  - Add a new API/view test with many date columns that intentionally fails at first.
  - The test should protect the new markup:
    - scroll container for the participant table exists.
    - sticky classes/attributes for the left name column and right `Bearbeiten` column exist.
    - header placeholder cells in the upper header rows are also marked as left/right sticky cells.
    - add-row, existing participant rows, edit fragment, and summary row remain usable in the same table model.
- Template change in `src/main/resources/templates/poll/view.html`:
  - keep the existing `votes-table-wrap` and use it semantically as the horizontal scroll container for the table.
  - add explicit sticky helper classes to the left and right header columns, including the currently empty `th` elements in rows 2 and 3.
  - keep rendering the middle date columns unchanged with `th:each`, so HTMX and server rendering remain unchanged.
- Fragment changes:
  - align `poll/participant-row.html`, `poll/participant-row-edit.html`, and `poll/summary-row.html` so the left name/summary column and right action/empty cell use the same sticky classes as the main view.
  - do not change HTMX endpoints, `hx-get`, `hx-post`, `hx-target`, or `hx-swap`; only add classes/markup needed for layout stability.
- CSS change in `src/main/resources/static/css/app.css`:
  - keep `votes-table-wrap` as the horizontal scroll container.
  - keep the table at `width: max-content` / `min-width: 100%` so horizontal scrolling only appears when needed.
  - pin the left column (`Teilnehmende`, participant name, summary label, add-row name) with `position: sticky; left: 0; z-index: ...; background: ...`.
  - pin the right column (`Bearbeiten`, buttons, right empty footer/header cell) with `position: sticky; right: 0; z-index: ...; background: ...`.
  - add clear background colors and separator edges/shadows for sticky columns so the scrolling date columns can visually move underneath without readability problems.
  - adjust the mobile rule at `max-width: 740px`: no `display: block` directly on `.votes-table`, because sticky behavior should remain attached to the real table; horizontal scrolling stays on the wrapper.
  - keep select widths in the date cells so the date columns keep their natural width and only the middle area scrolls.

## Public Interfaces / Behavior
- No change to URLs, HTMX contracts, controllers, or DTOs.
- The HTML structure of the participant view changes only by adding CSS classes for sticky left/right columns and scroll-container hardening.
- HTMX fragment responses remain complete `<tr>` fragments inside the same table.

## Test Plan
- New failing MockMvc test for participant view with many options:
  - checks scroll wrapper and sticky classes in header, body, and footer.
  - checks that `Teilnehmende` and `Bearbeiten` are still present in the HTML and are marked separately.
- Keep existing view tests green:
  - normal participant view.
  - edit-row fragment.
  - grouped intraday headers.
- Browser / E2E validation:
  - open participant view with many dates in a narrow viewport.
  - scroll horizontally and verify that date columns remain reachable and clickable.
  - save a new row with `Speichern`.
  - open an existing row with `Bearbeiten`, change it, and `Speichern` again.
  - verify that the name stays visible on the left and `Bearbeiten` / `Speichern` stays visible on the right.
- Before finishing, run `./gradlew check`.

## Assumptions
- Scope is only the participant view, not the admin view.
- The desired behavior should work on desktop and small viewports; there is no separate mobile-only layout change for this table.
- A CSS-based sticky solution is preferred over splitting the UI into multiple tables because it keeps HTMX fragments compatible and is substantially lower risk.
