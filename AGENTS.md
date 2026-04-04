This project uses a test-first, incremental workflow. Use this process for all new functionality.

## TDD Gate (Required)

- For any new feature or behavior change, add the first API-level test and make it **fail intentionally** (compile
  succeeds, assertions fail).
- Stop after the first failing test and self-review it for correctness. If adjustments are needed, update the test.
- If the test looks correct, proceed directly with implementation without waiting for user confirmation.
- Work independently by default: make educated guesses and move forward; only ask questions when blocked or when a
  decision is irreversible or would change agreed public APIs/data schemas.


## Java source code 
Always use JSpecify for nullability checks. Add `@NullMarked` in the base package via `package-info.java` to indicate
that the remaining unannotated type usages are not nullable. Create `package-info.java` only in the base package unless
another annotation beyond `@org.jspecify.annotations.NullMarked` is required. Avoid annotating every class individually.
Do not use `Optional` as a method parameter; only use `Optional` as a return type.

## Build tool
Use `gradle` as the build tool.

When running Gradle, `./gradlew` or `git` in this sandboxed environment, always use elevated permissions due to
restrictions on `~/.jenv` and `~/.gradle` and other files in the users home directory

## other tools
Run Python scripts with `python3 path/to/script.py` (use `python3`, not `python`).
If you need to install a python package always use a virtual environment for that.

All Data Transfer classes/records must use the `DTO` suffix in their name.
The `DTO` suffix is reserved for public API transfer types only; internal/domain/application types must not use `DTO`.

When moving a Java class to a new package, **never** delete and recreate it; use `git mv` instead.

## Tooling Notes

- If Playwright/Chrome hangs with the message “Wird in einer aktuellen Browsersitzung geöffnet”, fully quit Chrome and restart it. This usually unblocks the Playwright launch.

## Step-1 Static File Architecture — Do Not Refactor Away

`src/main/resources/static/poll/new-step1.html` is intentionally a **static file** served from S3/CloudFront,
**not** a Thymeleaf template rendered by Lambda. This is a deliberate Lambda warm-up strategy:

1. The static HTML is delivered instantly from CloudFront (no Lambda cold start).
2. While the user fills out the form, HTMX fires a background request to `/poll/active-count` ("Anzahl aktiver Umfragen").
3. That request spins up the Lambda container in the background.
4. By the time the user clicks "Weiter zum 2. Schritt", the Lambda is already warm and answers immediately.

**Do not convert step 1 to a Thymeleaf-rendered route.** The duplicate between
`templates/poll/new-step1.html` (used for HTMX fragments only) and `static/poll/new-step1.html`
(the actual page) is an accepted trade-off. Keep them in sync manually when changing shared logic.

## AWS Native Deployment Guardrails

When changing AWS-native deployment (`DEPLOY_RUNTIME=native`, `Dockerfile.lambda.native`, Lambda runtime behavior), 

always run post-deploy AWS smoke checks (Playwright/manual) for poll edit lifecycle:
    - create poll through step 3
    - publish poll
    - participant `Speichern`
    - row `Bearbeiten`
    - edit and `Speichern` again

