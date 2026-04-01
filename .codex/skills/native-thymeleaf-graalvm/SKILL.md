---
name: native-thymeleaf-graalvm
description: Hardening workflow for Spring Boot + Thymeleaf + HTMX on GraalVM native (especially AWS Lambda). Use when changing Thymeleaf templates, SpEL expressions, runtime hints, @Bean wiring, native Docker/deploy scripts, or when triaging native-only 503/startup failures.
---

# Native Thymeleaf + GraalVM

## Workflow

1. Read `docs/native-deploy-gotchas.md` before proposing or applying changes.
2. Classify the requested change:
   - template expression/model-shaping,
   - runtime hints,
   - bean wiring/AOT behavior,
   - native deploy/runtime.
3. Apply the constraints below before writing code.
4. Add or update tests first (TDD) for behavior that changes.
5. Run focused verification for the touched area, then run broader checks as required by `AGENTS.md`.

## Non-negotiable Constraints

- Keep Thymeleaf templates free of method calls in SpEL.
- Do not use `#lists.size(...)` or `.size()` in template expressions.
- Precompute booleans, counts, and padded/fixed-shape collections in Java.
- Use only simple property/index access in templates.
- Do not call one `@Bean` method from another `@Bean` method to construct fallbacks.
- Keep `ThymeleafRuntimeHints` synchronized with all template-facing model types.

## Change Patterns

### Template logic

- Move list sizing, conditional branching, and structure normalization into controller/service code.
- Replace fragile template conditions with model flags (`hasDateValues`, `dateValuesCount`, etc.).

### Runtime hints

- When introducing template-facing model/record types, register them in `ThymeleafRuntimeHints` in the same change.
- Add or update tests to catch missing hint registrations for new template model types.

### Bean wiring

- Extract shared object creation into private helpers.
- Call helper methods from each `@Bean` method instead of bean-to-bean invocation.
- Test relevant conditional permutations (for example SMTP/SES).

### Native deploy and triage

- For suspicious stale behavior after deploy, force a no-cache native image build.
- Verify rollout via both Lambda code hash and a direct API Gateway request.
- If CloudFront returns `503`, confirm backend via API Gateway and inspect Lambda logs for:
  - `MissingReflectionRegistrationError`
  - SpEL evaluation errors such as `EL1008E`
  - missing bean errors in startup logs

## Quick Checklist Before Finishing

- `docs/native-deploy-gotchas.md` reviewed and relevant sections applied.
- No forbidden template SpEL method calls introduced.
- Runtime hints updated for any new template-facing types.
- No `@Bean` self-invocation fallback pattern introduced.
- Native smoke path validated for affected flow (`/poll/new-step1.html` -> step 2/3, `/poll/active-count`).
