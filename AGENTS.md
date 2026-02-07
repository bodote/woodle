This project uses a test-first, incremental workflow. Use this process for all new functionality and refactors.

## TDD Gate (Required)

- For any new feature or behavior change, add the first API-level test and make it **fail intentionally** (compile
  succeeds, assertions fail).
- Stop after the first failing test and self-review it for correctness. If adjustments are needed, update the test.
- If the test looks correct, proceed directly with implementation without waiting for user confirmation.
- Work independently by default: make educated guesses and move forward; only ask questions when blocked or when a
  decision is irreversible or would change agreed public APIs/data schemas.

## Qodana

Run Qodana via Docker (project root, no token required):

```bash
docker run --rm -it -v "$PWD":/data -w /data jetbrains/qodana-jvm-community:latest
```

Always use JSpecify for nullability checks. Add `@NullMarked` in the base package via `package-info.java` to indicate
that the remaining unannotated type usages are not nullable. Create `package-info.java` only in the base package unless
another annotation beyond `@org.jspecify.annotations.NullMarked` is required. Avoid annotating every class individually.
Do not use `Optional` as a method parameter; only use `Optional` as a return type.

Use `gradle` as the build tool.

When running Gradle, `./gradlew` or `git` in this sandboxed environment, always use elevated permissions due to
restrictions on `~/.jenv` and `~/.gradle` and other files in the users home directory

Run Python scripts with `python3 path/to/script.py` (use `python3`, not `python`).

All Data Transfer classes/records must use the `DTO` suffix in their name.
The `DTO` suffix is reserved for public API transfer types only; internal/domain/application types must not use `DTO`.

- When introducing or updating templates, stop calling `String#formatted`. Always feed your text block into `TemplateRenderer.render(template, Map.of(...))` (or a similar wrapper) so placeholder names stay explicit and a single map drives every template.

When moving a Java class to a new package, **never** delete and recreate it; use `git mv` instead.

Testing note: `@MockBean` is deprecated in latest Spring Boot versions. Use `@MockitoBean` instead.

# Principles for writing automated tests

**High priority:** Test the module's public API first (e.g., REST endpoints via `@WebMvcTest`), and mock only external
dependencies. Avoid internal-only tests unless API-level tests cannot reach the 0.95 coverage target.

1. **Write tests before production code**
    - Define or extend tests that express the desired behaviour and edge cases.
    - Only then implement or change production code.

2. **Align on test cases first**
    - Discuss and agree test scenarios (including edge cases) before implementation.
    - Treat tests as the primary executable specification.

3. **Make tests fail meaningfully first**
    - Prefer failures where the code is implemented but *wrong* (e.g. incorrect return values), so the failing
      assertions describe behavioural gaps rather than "not implemented" errors.

4. **Work in small, focused steps**
    - Change one small, coherent piece of behaviour at a time.
    - Keep each test or code change narrowly scoped and immediately verifiable.
    - **NEVER create large commits** with many production classes at once. Each production class with logic should have
      its test committed together.

5. **Avoid magic literals in tests**
    - When a value is used repeatedly in a test suite, define it once as a `const` and reuse it.
    - This reduces duplication and keeps tests readable and maintainable.

6. **Avoid brittle implementation-detail expectations**
    - Prefer assertions about *observable behaviour* and final outputs.
    - Only assert on implementation details (like mock call counts) when the *behaviour* depends on them.
    - Focus on testing the public API and mock only external dependencies (e.g., for public REST API endpoints: use
      `@WebMvcTest`, exercise domain logic with real beans, and mock only external dependencies such as databases or
      external APIs).
    - For REST endpoints, prefer `@WebMvcTest` on the module API (like `BusinessResearchControllerTest`), and mock only
      external dependencies. For the Perplexity flow, the external boundary is the response from
      `openAiApi.chatCompletionEntity(chatRequest)`; prefer using `FixturePerplexityResponseProvider` instead of mocking
      internal components.
    - Avoid testing internal module parts directly when possible; only add internal tests if API-level tests cannot
      reach the 0.95 coverage target. This keeps tests less brittle and reduces churn during internal refactors.
    - Avoid reflection in tests; prefer small production changes that make code testable through stable APIs.

7. **Split tests by concern**
    - Place each logical group of tests in a separate file or nested class.
    - Use `@Nested` classes for logical grouping within a test file.

8. **HTML-Seiten testen (funktional, nicht visuell)**
    - Für HTML-Seiten sind automatisierte Tests erforderlich.
    - Wir testen keine Layout-Details wie Farben oder exakte Positionen.
    - Wir testen, dass die erforderlichen HTML-Elemente vorhanden sind und wie spezifiziert funktionieren.
    - Tooling: bevorzugt Spring MVC Tests mit HtmlUnit für Struktur und Verhalten; zusätzlich wenige Playwright E2E-Tests
      für zentrale Views.

## Test Classification (Naming Convention)

This project follows the **Maven Surefire/Failsafe convention** for separating test types:

| Type                  | File Pattern | Speed         | Dependencies       | Gradle Command                 |
|-----------------------|--------------|---------------|--------------------|--------------------------------|
| **Unit Tests**        | `*Test.java` | Fast (<1s)    | Mocked             | `./gradlew test`               |
| **Integration Tests** | `*IT.java`   | Slow (10-30s) | Real (DB, network) | `./gradlew test --tests '*IT'` |

### Unit Tests (`*Test.java`)

- Use **mocks** for all external dependencies (database, network, etc.)
- Run in milliseconds without Docker or external services
- Test business logic in isolation
- Example: `IngestionServiceTest.java`

```java
// Unit test - uses mocks, runs fast
@DisplayName("IngestionService")
class IngestionServiceTest {
    @Test
    void ingestsBook() {
        VectorStore mockVectorStore = mock(VectorStore.class);
        // ... mock setup and assertions
    }
}
```

### Integration Tests (`*IT.java`)

- Use **real dependencies** for databases via Testcontainers. For network calls—especially expensive external AI/LLM
  APIs—prefer mocking the Spring AI abstraction (e.g., replace `org.springframework.ai.openai.api.OpenAiApi` created by
  `OpenAiApi.builder().build()` with a mock).
- Require Docker for database containers
- Test full integration with owned infrastructure; avoid real calls to third-party paid APIs
- Example: `IngestionServiceIT.java`

```java
// Integration test - uses real DB, runs slower
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class IngestionServiceIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>();

    @Autowired
    private IngestionService ingestionService;
}
```

## Running Tests

```bash
# Unit tests only (fast, run frequently during development)
./gradlew test

# Integration tests only (slow, run before commit)
./gradlew test --tests '*IT'

# Run specific test class
./gradlew test --tests 'de.bas.bodo.genai.ingestion.IngestionServiceTest'

# Run all tests with coverage
./gradlew test
./gradlew jacocoTestReport
```

## Test Profiles (local)

- Test resources always include the `test` profile via `src/test/resources/application.yml`.
- IntelliJ run configurations provide the remaining profiles via `SPRING_PROFILES_ACTIVE`:
    - `local` for default/mock runs (mocking is implicit unless `record-api-response` is set).
    - `local,record-api-response` to refresh recorded API responses.
- For CLI runs, set `SPRING_PROFILES_ACTIVE` explicitly if you need non-default profiles.

## Test Coverage

- Coverage is collected via **JaCoCo** (Gradle plugin)
- Both unit and integration tests contribute to coverage
- View coverage in VS Code/Cursor with **Coverage Gutters** extension
- Generate HTML report: `./gradlew jacocoTestReport`
- Verify coverage thresholds: `./gradlew check` (fails `:jacocoTestCoverageVerification` when below minimums)

## Mutation Testing (Pitest)

Use Pitest to check whether tests actually detect bugs by mutating bytecode. It helps reveal weak assertions and
untested branches that code coverage alone misses.

Run:

```bash
./gradlew pitest
```

Notes:

- If there are no mutable production classes yet, Pitest may report “No mutations found”; this is expected early in the
  project.
- Prefer targeting specific packages/classes once real logic exists.
- **Current behavior**: Pitest currently executes `*IT.java` tests as well (integration tests are not excluded yet).

### Coverage Requirements

| Metric               | Minimum Threshold |
|----------------------|-------------------|
| Instruction coverage | **95%**           |
| Branch coverage      | **90%**           |

### What MUST Be Tested

Every production class with **behaviour** (methods beyond getters/constructors) requires a dedicated test:

| Class Type                             | Test Required? | Notes                                                      |
|----------------------------------------|----------------|------------------------------------------------------------|
| Services, Controllers                  | ✅ **Yes**      | Always requires `*Test.java`                               |
| Spring `@Component` / `@EventListener` | ✅ **Yes**      | Including lifecycle beans like `DataInitializer`           |
| Utility classes                        | ✅ **Yes**      | e.g., `TextChunker`, parsers, validators                   |
| Records with factory methods           | ✅ **Yes**      | e.g., `RagResponse.success()`, `IngestionResult.failure()` |
| Simple DTOs / records (data only)      | ⚠️ Indirect    | Covered via tests of classes that use them                 |
| Enums                                  | ⚠️ Indirect    | Covered via tests of classes that use them                 |

### What Does NOT Need a Dedicated Test

- Simple records with only a canonical constructor (e.g., `BookContent(int id, String title, String text)`)
- Enums with no logic (e.g., `GuardResult`)
- `package-info.java` files
- Main application class (`GenaiApplication.main()`) - Spring Boot convention

### Detecting Orphan Classes (No Test)

Before committing, verify every new production class has coverage.
Manual review: each service/controller/component in prod should have a test.

## Common TDD Violations to Avoid

| Anti-Pattern                   | Problem                                       | Solution                                                     |
|--------------------------------|-----------------------------------------------|--------------------------------------------------------------|
| "Big Bang" commits             | Many classes added without tests for all      | Commit one feature at a time with its tests                  |
| "Glue code doesn't need tests" | Components like `DataInitializer` are skipped | All `@Component`, `@EventListener` classes need tests        |
| "It's just a record"           | Records with factory methods are untested     | Test factory methods and any non-trivial logic               |
| "Integration tests cover it"   | Unit tests skipped because E2E exists         | Unit tests are required; integration tests are supplementary |
| Redundant classes              | Copy-pasted classes that are never used       | Remove unused code; don't commit dead code                   |

### Module Data Ownership

Each shared resource (database, external API, file system) must be **owned by exactly one module**. This avoids
cross-module infrastructure coupling and clarifies the API boundary.

### Cross-Module Access Pattern

```
❌ WRONG: Multiple modules inject infrastructure directly
┌──────────────┐     ┌──────────────┐
│  Ingestion   │────▶│ VectorStore  │◀────│  Retrieval  │
└──────────────┘     └──────────────┘     └─────────────┘

✅ CORRECT: One module owns infrastructure, exposes API
┌──────────────┐     ┌──────────────────────┐
│  Ingestion   │────▶│     Retrieval        │
└──────────────┘     │  (owns VectorStore)  │
                    │  - addDocuments()    │
                     │  - retrieve()        │
                     └──────────────────────┘
```

## Tooling Notes

- If Playwright/Chrome hangs with the message “Wird in einer aktuellen Browsersitzung geöffnet”, fully quit Chrome and restart it. This usually unblocks the Playwright launch.
