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

## AWS Native Deployment Guardrails

When changing AWS-native deployment (`DEPLOY_RUNTIME=native`, `Dockerfile.lambda.native`, Lambda runtime behavior), 

always run post-deploy AWS smoke checks (Playwright/manual) for poll edit lifecycle:
    - create poll through step 3
    - publish poll
    - participant `Speichern`
    - row `Bearbeiten`
    - edit and `Speichern` again

