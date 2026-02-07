# Usability Guide for HTML + htmx (Local)

This file is a working checklist and decision guide for building highly usable, accessible HTML + htmx pages in this repo. It is written for quick reference during implementation and review.

## Goals
- Make core user tasks fast and error-proof.
- Keep the UI understandable with minimal cognitive load.
- Ensure accessibility and keyboard-first usability.
- Prefer server-rendered HTML, with htmx for incremental updates.

## Core Principles
- Start from user tasks, not screens.
- Reduce steps, reduce form fields, reduce choices.
- Always show system status and feedback immediately.
- Make errors precise and actionable.
- Default to accessibility (semantic HTML, correct focus, ARIA only when needed).

## Page Structure (Baseline)
- Use semantic elements: `header`, `main`, `nav`, `section`, `form`.
- One `h1` per page; clear title reflecting the user goal.
- Consistent, visible primary action.
- Use `label` + `for` on every input.
- Avoid placeholder-only labels.

## htmx Interaction Rules
- Every htmx request must return complete, valid HTML for the target swap.
- Prefer `hx-target` on the trigger, not globally on the container.
- Prefer `hx-swap="outerHTML"` for self-contained components; `innerHTML` only for simple lists.
- Use `hx-indicator` to show progress for actions longer than ~300ms.
- When possible, use `hx-trigger="submit"` for forms and `hx-include` for extra context.
- Always consider idempotency and retry safety on POST endpoints.

## Feedback & Status
- On success: show confirmation near the action, not far away.
- On failure: show error message inline, adjacent to the field or action.
- Use server-side validation and return errors with the same HTML structure.
- For multi-step flows, show step indicators and let users go back.

## Accessibility Checklist
- Keyboard navigation works for all controls.
- Focus is visible and not removed in CSS.
- After htmx updates, move focus to the first actionable element.
- Use `aria-live="polite"` on status messages that update.
- Avoid `role` unless native HTML cannot express the semantics.
- Ensure color contrast meets WCAG AA.

## Forms
- Group related fields with `fieldset` + `legend`.
- Use field-level help text with `aria-describedby` when needed.
- Provide examples and constraints (formats, ranges) near inputs.
- Avoid optional fields unless they are clearly marked as optional.
- Preserve user input on server validation failures.

## Error Handling Pattern (Recommended)
- Return the same form HTML with:
  - Inline error summary at the top of the form.
  - Per-field error messages with clear instructions.
- On server errors (5xx), show a friendly message and a retry action.

## Multi-Step Flows
- Persist data between steps to avoid re-entry.
- Clearly label current step and total steps.
- Provide a direct back action that does not lose work.
- Validate per-step and avoid full restart on error.

## Navigation & Orientation
- Keep navigation minimal on task-focused pages.
- Highlight the current page in nav.
- Use breadcrumbs only when users need to understand hierarchy.

## Performance & Perceived Speed
- Keep server responses fast; show an indicator for slow actions.
- Avoid unnecessary DOM updates; only swap what changed.
- Cache where appropriate; avoid full page reloads for small changes.

## Content & Microcopy
- Use verbs for primary actions: "Create poll", "Add option".
- Avoid jargon; prefer user terms over internal names.
- Confirm destructive actions with clear language.

## Testing Guidance
- Write API-level tests for pages using `@WebMvcTest`.
- HTML tests should validate structure and behavior, not layout.
- Use HtmlUnit for DOM structure and htmx response fragments.
- Add Playwright tests only for key user flows.

## htmx Focus Management Pattern (Example)
- Server returns HTML with `autofocus` on the next field.
- For partial updates, include a small script to focus the correct element if needed.

## Review Checklist (Before Merge)
- Main user task can be completed without guesswork.
- Form errors are visible and specific.
- All pages usable with keyboard only.
- htmx swaps update only the necessary parts.
- No UI state is lost on validation errors.
- Tests cover the primary flow and at least one error path.

## Reference Pointers (Local)
- Align with test patterns in `src/test/java/...`.
- See HTML structure conventions in existing page templates.

