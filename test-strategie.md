# Test Strategy

## Context and Performance
- Spring uses a **context cache**; identical configurations are reused, which speeds up tests.
- Avoid unnecessary context reloads; `@DirtiesContext` clears the cache and slows down subsequent tests.
- Tests should run in a single process whenever possible so the context cache can be reused.

## Integration Test (single `@SpringBootTest` class)
- There should be **exactly one** class annotated with `@SpringBootTest`.
- This class contains **all integration tests** and **also the end-to-end tests (Playwright)**.
- If there are many tests: group them with `@Nested` and inner classes.
- **Naming pattern:** `.*IT.java`
- **Characteristic:** slow (full stack, including E2E)
- If a real HTTP server is required, use `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- Inject the port via `@LocalServerPort`.

## Default: `@WebMvcTest`
- Most tests should be `@WebMvcTest`.
- **Naming pattern:** `.*Test.java`
- External dependencies (database, S3, external services) are mocked.
- Goal: fast execution.
- Focus: **specification/behavior**, not implementation details.
- These tests should provide the **majority of test coverage**.
- Test slices exclude certain `@Configuration` classes; add required configuration explicitly.
- For HTML pages, **do not** test colors, font sizes, or layout details.
- Instead, verify:
  - that the **required HTML elements** are present,
  - optionally their **order** on the page,
  - and their **function/behavior**.
- **Exception:** for **pure layout/CSS changes** (for example spacing, alignment, colors, typography)
  that cause **no** behavior change and **no** change to HTML structure/DOM contracts,
  automated tests are exceptionally **not required**.

## Unit Tests (Exception)
- Only when coverage **cannot be achieved practically** with `@WebMvcTest`.
- May test implementation details if needed.
- Fast, but **more tightly coupled to implementation**.
- Should be used **as little as possible**.

## Context Hygiene
- Use `@DirtiesContext` only when a test mutates the application context; otherwise avoid it.
- If `@DirtiesContext` is necessary, apply it deliberately (with a clear reason in the test).

## Parallelism
- Parallel execution can cause unstable tests, especially with shared context or shared resources.
- Tests that mutate context or shared resources should **not** run in parallel.

## Testcontainers (Integration)
- Use Testcontainers for real backends (for example DB, S3 emulator).
- Define with `@Testcontainers` and `@Container`.
- Use `@ServiceConnection` where possible so Spring Boot configures connections automatically.

## Test Slices (Selection)
- Web: `@WebMvcTest`
- Data: `@DataJpaTest`, `@DataJdbcTest`, `@DataMongoTest`, etc.
- For real HTTP endpoints: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@LocalServerPort`

## Test Configuration
- Use `@TestConfiguration` for test-specific beans (in addition to main configuration).

## Coverage Target
- We target **95% test coverage**.
- Coverage is checked **regularly**.

## References
- [Spring Boot: Testing Spring Boot Applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- [Spring Boot: Test Slices](https://docs.spring.io/spring-boot/appendix/test-auto-configuration/slices.html)
- [Spring Boot: Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Spring Framework: Context Caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html)
- [Spring Framework: @DirtiesContext](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-dirtiescontext.html)
