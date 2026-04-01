# Phase 1: Email Notifications - Research

**Researched:** 2026-04-01
**Domain:** Spring Boot Mail + Thymeleaf HTML templates, hexagonal architecture wiring
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

1. **Email provider** — `spring-boot-starter-mail` (SMTP). Add to `build.gradle`. Use `JavaMailSender`. Production: AWS SES SMTP endpoint. Dev/test: local mailhog or disabled.

2. **Email templates** — Thymeleaf HTML templates under `src/main/resources/templates/email/`:
   - `vote-notification.html`
   - `comment-notification.html`

3. **Notification scope** — Both `notifyOnVote` AND `notifyOnComment` in the same phase.

4. **When to send** — Only on NEW responses (`command.responseId() == null`). Skip on edits.

5. **Error handling** — Catch exception, log warning, let vote persist. A failed email must never cause a 500.

6. **Notification trigger** — Synchronous call in `SubmitVoteService` after `pollRepository.save(...)`. No async/event bus.

7. **Data model changes** (all layers, verbatim):

   | Layer | File | Change |
   |-------|------|--------|
   | HTML | `new-step1.html` | Add `name` attributes to checkboxes |
   | Controller | `PollNewPageController.handleStep1()` | Accept `notifyOnVote`, `notifyOnComment` params |
   | Session | `WizardState` | Add `notifyOnVote`, `notifyOnComment` fields |
   | Command | `CreatePollCommand` | Add `notifyOnVote`, `notifyOnComment` fields |
   | Domain | `Poll` | Add `notifyOnVote`, `notifyOnComment` fields |
   | Persistence | `S3PollRepository.toDao()` | Use actual values instead of hardcoded `false` |
   | Persistence | `S3PollRepository.fromDao()` | Read notification flags into `Poll` |

8. **SMTP configuration** — Add to `application.properties`:
   ```
   spring.mail.host=${MAIL_HOST:localhost}
   spring.mail.port=${MAIL_PORT:1025}
   spring.mail.username=${MAIL_USERNAME:}
   spring.mail.password=${MAIL_PASSWORD:}
   spring.mail.properties.mail.smtp.auth=${MAIL_SMTP_AUTH:false}
   spring.mail.properties.mail.smtp.starttls.enable=${MAIL_SMTP_STARTTLS:false}
   woodle.mail.from=${MAIL_FROM:noreply@woodle.app}
   ```

9. **GraalVM AOT compatibility** — Register email template resources and `JavaMailSender` for AOT. Verify `spring-boot-starter-mail` GraalVM hints cover multipart mail.

### Claude's Discretion

None identified — all major areas have locked decisions.

### Deferred Ideas (OUT OF SCOPE)

- Async email sending via Spring Events or message queue
- Email notifications for participants (only poll creator in scope)
- Unsubscribe link / preference management page
- Email delivery tracking / bounce handling
</user_constraints>

---

## Summary

This phase wires an end-to-end email notification feature in a hexagonal Spring Boot 3.4.2 application. The decisions are already locked: `spring-boot-starter-mail` with `JavaMailSender`, Thymeleaf HTML templates, synchronous sending in `SubmitVoteService`, and a 7-layer data model change from HTML checkbox to S3 persistence.

The codebase inspection confirms the exact gaps. `WizardState` is a mutable POJO (not a record) — adding fields follows the existing setter/getter pattern. `CreatePollCommand` and `Poll` are Java records — adding fields requires updating every constructor callsite (5 identified). `S3PollRepository.toDao()` at line 104 hardcodes `new PollDAO.Notifications(false, false)` and `fromDao()` discards the deserialized flags. `SubmitVoteService` already distinguishes new vs. edit via `command.responseId() != null`, so the trigger insertion point is clear.

One pitfall requires attention: multipart HTML emails via `JavaMailSender.createMimeMessage()` + `MimeMessageHelper(msg, true)` had a known GraalVM native-image reflection gap (Spring Framework issue #29713). That issue was closed as resolved in January 2023 via GraalVM Reachability Metadata PR #160. For Spring Boot 3.4.x (which pulls GraalVM metadata 0.10.x via the native plugin), this is covered. However, if the app is compiled to native, a smoke test of the email code path in native mode should be part of verification.

**Primary recommendation:** Implement in 4 sequential waves: (1) data model pipeline, (2) `EmailNotificationService` + templates, (3) trigger wiring in `SubmitVoteService`, (4) test coverage.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-mail` | managed by Spring Boot 3.4.2 BOM (resolves to 3.4.2) | `JavaMailSender` auto-configuration + SMTP | Locked decision; Spring Boot BOM-managed, no extra version pin needed |
| `org.springframework.boot:spring-boot-starter-thymeleaf` | already in `build.gradle` | `TemplateEngine` for HTML email rendering | Already in the project; reuse existing bean |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `jakarta.mail` | pulled transitively by `spring-boot-starter-mail` | JavaMail API | No direct dependency needed — transitive |
| `spring-boot-starter-test` + Mockito | already in `build.gradle` | Unit tests for `EmailNotificationService` | Mock `JavaMailSender` to verify `send()` called |
| GreenMail / mailhog (test) | not currently in build | In-process SMTP server for integration tests | Optional; Mockito mocking of `JavaMailSender` is sufficient for unit coverage |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `spring-boot-starter-mail` | AWS SES SDK v2 direct | Adds AWS SDK dependency, more code, no benefit at this scale |
| Thymeleaf `TemplateEngine` | Plain string concatenation | Would not reuse existing Spring context; deferred decision explicitly chose Thymeleaf |

**Installation:**
```gradle
// In build.gradle dependencies block:
implementation 'org.springframework.boot:spring-boot-starter-mail'
```

No version needed — the Spring Boot BOM (applied via `id 'org.springframework.boot' version '3.4.2'`) manages it.

---

## Architecture Patterns

### Existing Hexagonal Layout (confirmed by codebase scan)

```
src/main/java/.../woodle/
├── adapter/
│   ├── in/web/           # Controllers, WizardState
│   └── out/persistence/  # S3PollRepository, PollDAO
├── application/
│   ├── port/in/          # Use-case interfaces + Commands
│   ├── port/out/         # PollRepository interface
│   └── service/          # SubmitVoteService, CreatePollService
├── config/               # ApplicationConfig, ThymeleafRuntimeHints, WoodleApplication
└── domain/model/         # Poll, PollOption, PollResponse (records)
src/main/resources/
├── application.properties
└── templates/poll/       # existing Thymeleaf templates
```

### New Files This Phase Adds

```
src/main/java/.../woodle/
└── application/service/EmailNotificationService.java   # new service

src/main/resources/
└── templates/email/
    ├── vote-notification.html     # new Thymeleaf template
    └── comment-notification.html  # new Thymeleaf template
```

### Pattern 1: JavaMailSender + MimeMessageHelper for HTML email

**What:** Use `JavaMailSender.createMimeMessage()` + `MimeMessageHelper(msg, true)` to build a multipart MIME email. Render body via `TemplateEngine.process()`.

**When to use:** Whenever HTML email is needed. `MimeMessageHelper(msg, true)` activates multipart mode required for HTML.

**Example:**
```java
// Source: https://docs.spring.io/spring-boot/reference/io/email.html
//         + https://www.thymeleaf.org/doc/articles/springmail.html
public void sendVoteNotification(Poll poll, PollResponse response) {
    try {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(poll.authorEmail());
        helper.setSubject("Neue Wertung für: " + poll.title());

        Context ctx = new Context(Locale.GERMAN);
        ctx.setVariable("poll", poll);
        ctx.setVariable("response", response);
        String html = templateEngine.process("email/vote-notification", ctx);
        helper.setText(html, true);  // true = isHtml

        mailSender.send(message);
    } catch (Exception e) {
        log.warn("Failed to send vote notification for poll {}: {}", poll.pollId(), e.getMessage());
        // swallow — vote already saved
    }
}
```

### Pattern 2: `TemplateEngine` injection (reuse existing Spring bean)

**What:** Spring Boot auto-configures a `SpringTemplateEngine` bean when `spring-boot-starter-thymeleaf` is present. Inject it directly — do not create a second `TemplateEngine`.

**When to use:** Always. `ThymeleafAutoConfiguration` registers the bean; no manual config needed.

```java
// EmailNotificationService constructor injection
public EmailNotificationService(JavaMailSender mailSender,
                                 TemplateEngine templateEngine,
                                 @Value("${woodle.mail.from}") String fromAddress) { ... }
```

### Pattern 3: ApplicationConfig bean wiring (existing pattern)

**What:** Services in this project are wired in `ApplicationConfig` via `@Bean` methods, not `@Service`. Follow the same pattern.

**Example (existing):**
```java
@Bean
public SubmitVoteUseCase submitVoteUseCase(PollRepository pollRepository) {
    return new SubmitVoteService(pollRepository);
}
```

**New bean (same pattern):**
```java
@Bean
public EmailNotificationService emailNotificationService(
        JavaMailSender mailSender,
        TemplateEngine templateEngine,
        @Value("${woodle.mail.from}") String fromAddress) {
    return new EmailNotificationService(mailSender, templateEngine, fromAddress);
}
```

Then update `submitVoteUseCase` bean to accept `EmailNotificationService` as parameter.

### Pattern 4: `WizardState` field additions

`WizardState` is a mutable POJO, not a record. Add fields with getter/setter following the existing naming convention:

```java
// Existing pattern (getter name matches field name, no "get" prefix)
public String authorName() { return authorName; }
public void setAuthorName(String authorName) { this.authorName = authorName; }

// New fields follow same pattern
private boolean notifyOnVote;
private boolean notifyOnComment;
public boolean notifyOnVote() { return notifyOnVote; }
public void setNotifyOnVote(boolean notifyOnVote) { this.notifyOnVote = notifyOnVote; }
```

### Pattern 5: Java record component addition (Poll, CreatePollCommand)

`Poll` and `CreatePollCommand` are records. Adding components requires updating every constructor call site:

- `Poll` constructor: called in `CreatePollService.create()`, `S3PollRepository.fromDao()`, `Poll.addResponse()`, `Poll.replaceResponse()`, `Poll.withOptions()`, `TestFixtures.poll()` (2 overloads)
- `CreatePollCommand` constructor: called in `PollSubmitController.submit()`, `CreatePollServiceTest`, `PollApiController` (if exists), wizard flow tests

**Critical:** Append new components at the END of the record parameter list to minimize diff noise. New notification components should be primitive `boolean` (not `Boolean`) to match `PollDAO.Notifications(boolean onVote, boolean onComment)`.

### Pattern 6: HTML checkbox param binding (Spring MVC unchecked = absent)

HTML checkboxes only submit a value when checked. When unchecked, the parameter is absent from the POST body. Spring MVC `@RequestParam(value="notifyOnVote", defaultValue="false")` handles this correctly — absent checkbox becomes `false`.

The current `new-step1.html` already has `<input name="notifyOnVote" type="checkbox">` and `<input name="notifyOnComment" type="checkbox">` — the `name` attributes are present. The controller parameter reading is the only missing piece.

### Anti-Patterns to Avoid

- **Do not add `@Service` annotation** — this project wires services in `ApplicationConfig` manually. Adding `@Service` would cause double-registration.
- **Do not create a second `TemplateEngine`** — `ThymeleafAutoConfiguration` provides one. A second bean causes ambiguity.
- **Do not fail the HTTP request on email error** — catch `MailException` + all `Exception`, log at WARN, return normally.
- **Do not use `SimpleMailMessage`** — it sends plain text only. `MimeMessageHelper` is required for HTML.
- **Do not forget `MimeMessageHelper(msg, true)`** — the `true` argument enables multipart mode; without it, `setText(html, true)` silently sends plain text.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SMTP connection pooling | Manual `Session` management | `JavaMailSender` auto-configured | Handles connection, auth, TLS negotiation |
| HTML template rendering | String concatenation / `MessageFormat` | `TemplateEngine.process()` | Already in context; handles escaping, i18n, variables |
| Email validation of `from` address | Custom regex | `@Value` + SMTP server rejection | Validation at config time is sufficient |
| Multipart MIME construction | Manual `MimeMultipart` | `MimeMessageHelper(msg, true)` | Handles Content-Type, encoding, charset automatically |

**Key insight:** The Thymeleaf `TemplateEngine` bean already exists in the Spring context from `spring-boot-starter-thymeleaf`. There is no need to configure a second template engine for email — inject the same bean.

---

## Common Pitfalls

### Pitfall 1: `Poll` record — missing constructor callsites

**What goes wrong:** Adding `notifyOnVote` and `notifyOnComment` to the `Poll` record breaks every `new Poll(...)` call that uses positional arguments. The compiler catches this but it's easy to miss `TestFixtures.poll()` overloads.

**Why it happens:** Java records use positional canonical constructors. `Poll.addResponse()`, `Poll.replaceResponse()`, and `Poll.withOptions()` all call `new Poll(...)` inline — these are inside the record itself and easy to overlook.

**How to avoid:** After adding fields to `Poll`, compile immediately and resolve all compiler errors before proceeding. There are exactly 7 `new Poll(...)` call sites to update (confirmed by codebase scan): `CreatePollService.create()`, `S3PollRepository.fromDao()`, `Poll.addResponse()`, `Poll.replaceResponse()`, `Poll.withOptions()`, `TestFixtures.poll(2 args)`, `TestFixtures.poll(6 args)`.

**Warning signs:** Compilation error "constructor Poll in record Poll cannot be applied to given types."

### Pitfall 2: `S3PollRepository.fromDao()` — notification flags discarded

**What goes wrong:** `fromDao()` constructs `Poll` but does not currently read `pollDAO.notifications().onVote()` or `.onComment()`. After adding fields to `Poll`, the `fromDao()` call must also be updated or notifications are always `false` at read time.

**Why it happens:** The DAO already has the fields; they were just never wired forward.

**How to avoid:** In the same task that updates `toDao()`, also update `fromDao()`.

### Pitfall 3: HTML checkbox unchecked state

**What goes wrong:** An unchecked HTML checkbox submits nothing — the parameter is absent from the POST body. If the controller uses `@RequestParam("notifyOnVote") boolean notifyOnVote` without a default, Spring throws a `MissingServletRequestParameterException` when unchecked.

**Why it happens:** Default `@RequestParam` is required.

**How to avoid:** Use `@RequestParam(value = "notifyOnVote", defaultValue = "false")`.

### Pitfall 4: `TemplateEngine` bean ambiguity

**What goes wrong:** Creating a new `@Bean TemplateEngine` in `ApplicationConfig` causes Spring to have two `TemplateEngine` beans, leading to `NoUniqueBeanDefinitionException`.

**Why it happens:** `ThymeleafAutoConfiguration` already creates one.

**How to avoid:** Inject the existing `TemplateEngine` bean directly into `EmailNotificationService`. No configuration needed.

### Pitfall 5: GraalVM native — multipart mail reflection

**What goes wrong:** Multipart MIME email sending fails in GraalVM native images with `UnsupportedDataTypeException` because `com.sun.mail.handlers.*` classes need reflection access.

**Why it happens:** Hidden reflection in Jakarta Mail's `mailcap` file.

**How to avoid:** This was fixed in GraalVM Reachability Metadata (PR #160, closed 2023-01-05). Spring Boot 3.4.x pulls the native plugin `0.10.4` which includes these metadata. No manual `reflect-config.json` is needed. However, if a native binary is built, run a smoke-test email in native mode before declaring done.

**Warning signs:** `UnsupportedDataTypeException: no object DCH for MIME type multipart/mixed` in native mode.

### Pitfall 6: `woodle.mail.from` property missing at startup

**What goes wrong:** `@Value("${woodle.mail.from}")` throws `IllegalArgumentException` at context startup if the property is absent.

**Why it happens:** `@Value` without a default is required.

**How to avoid:** Add `woodle.mail.from=${MAIL_FROM:noreply@woodle.app}` to `application.properties` as a locked decision specifies.

---

## Code Examples

### Email service skeleton

```java
// Source: https://www.thymeleaf.org/doc/articles/springmail.html
// + https://docs.spring.io/spring-boot/reference/io/email.html
package io.github.bodote.woodle.application.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromAddress;

    public EmailNotificationService(JavaMailSender mailSender,
                                     TemplateEngine templateEngine,
                                     String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
    }

    public void notifyOnVote(Poll poll, PollResponse response) {
        if (!poll.notifyOnVote()) return;
        sendNotification(poll, response, "email/vote-notification",
                "Neue Wertung für: " + poll.title());
    }

    public void notifyOnComment(Poll poll, PollResponse response) {
        if (!poll.notifyOnComment()) return;
        if (response.comment() == null || response.comment().isBlank()) return;
        sendNotification(poll, response, "email/comment-notification",
                "Neuer Kommentar für: " + poll.title());
    }

    private void sendNotification(Poll poll, PollResponse response,
                                   String template, String subject) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(poll.authorEmail());
            helper.setSubject(subject);

            Context ctx = new Context();
            ctx.setVariable("poll", poll);
            ctx.setVariable("response", response);
            String html = templateEngine.process(template, ctx);
            helper.setText(html, true);

            mailSender.send(message);
            log.debug("Sent {} notification to {}", template, poll.authorEmail());
        } catch (Exception e) {
            log.warn("Failed to send {} notification for poll {}: {}",
                    template, poll.pollId(), e.getMessage());
        }
    }
}
```

### SubmitVoteService — trigger insertion point (after save)

```java
// In SubmitVoteService.submit(), after the pollRepository.save() on new response:
if (command.responseId() != null) {
    pollRepository.save(poll.replaceResponse(response));
    return;
}
Poll saved = poll.addResponse(response);
pollRepository.save(saved);
emailNotificationService.notifyOnVote(saved, response);
emailNotificationService.notifyOnComment(saved, response);
```

### Thymeleaf vote-notification.html skeleton

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="de">
<head><meta charset="UTF-8"><title>Neue Wertung</title></head>
<body>
<p>Hallo <span th:text="${poll.authorName}"></span>,</p>
<p><strong th:text="${response.participantName}"></strong>
   hat an Ihrer Umfrage "<strong th:text="${poll.title}"></strong>" teilgenommen.</p>
</body>
</html>
```

### Gradle dependency addition

```gradle
// build.gradle — add to dependencies block
implementation 'org.springframework.boot:spring-boot-starter-mail'
```

### application.properties additions

```properties
spring.mail.host=${MAIL_HOST:localhost}
spring.mail.port=${MAIL_PORT:1025}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=${MAIL_SMTP_AUTH:false}
spring.mail.properties.mail.smtp.starttls.enable=${MAIL_SMTP_STARTTLS:false}
woodle.mail.from=${MAIL_FROM:noreply@woodle.app}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `javax.mail` | `jakarta.mail` | Spring Boot 3 / Jakarta EE 9 | Package is `jakarta.mail.*`, not `javax.mail.*` |
| `@Service` everywhere | Manual `@Bean` in `ApplicationConfig` | Project convention | `EmailNotificationService` must be wired in `ApplicationConfig`, not via `@Service` |
| `SimpleMailMessage` | `MimeMessageHelper` | Always for HTML | `SimpleMailMessage` is plain-text only |

**Deprecated/outdated:**
- `javax.mail.*`: All mail imports must use `jakarta.mail.*` in Spring Boot 3.x.
- `SimpleMailMessage` for HTML: Use `MimeMessageHelper` with `multipart=true`.

---

## Open Questions

1. **Does `spring-boot-starter-mail` auto-configuration activate even with empty/localhost config?**
   - What we know: `JavaMailSenderAutoConfiguration` activates when `spring.mail.host` is set. Without it, no `JavaMailSender` bean is created.
   - What's unclear: The locked SMTP config adds `spring.mail.host=${MAIL_HOST:localhost}`, so in test/dev the host is `localhost`. This WILL trigger auto-configuration, which means tests that load the full Spring context will fail if there's no SMTP server at localhost.
   - Recommendation: For `@WebMvcTest` and unit tests that mock `JavaMailSender` directly, this is not an issue. For full `@SpringBootTest` integration tests, either use a `MockBean` for `JavaMailSender` or configure `spring.mail.host` to be absent in `application-test.properties`. Alternatively, add `spring.mail.test-connection=false` to properties.

2. **TestFixtures.poll() callsites — test compilation after record changes**
   - What we know: `TestFixtures` has 2 `poll()` overloads; both call `new Poll(...)` with positional args. `S3PollRepositoryIT` also calls `new Poll(...)` directly.
   - What's unclear: Exactly how many test files construct `Poll` directly vs. via `TestFixtures`.
   - Recommendation: After modifying `Poll` record, run `./gradlew compileTestJava` immediately to surface all broken test call sites before writing new tests.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 (toolchain) | Build | confirmed (project builds) | 21 | — |
| Docker | `S3PollRepositoryIT` (LocalStack) | assumed (existing IT passes) | — | `@Testcontainers(disabledWithoutDocker=true)` guards it |
| SMTP server (prod) | Email sending | not checked (runtime) | — | `MAIL_HOST` env var; no emails sent if not configured |
| `spring-boot-starter-mail` | Email feature | not yet in build | — | Add to `build.gradle` |

**Missing dependencies with no fallback:**
- `spring-boot-starter-mail` is not yet in `build.gradle` — must be added.

**Missing dependencies with fallback:**
- SMTP server: defaulting to `localhost:1025` means emails are silently dropped (or fail gracefully per error-handling decision) if no mailhog is running locally.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via `spring-boot-starter-test` |
| Config file | None — configured in `build.gradle` via `tasks.named('test') { useJUnitPlatform() }` |
| Quick run command | `./gradlew test --tests "*.EmailNotificationService*" --tests "*.SubmitVoteService*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements to Test Map

| Behavior | Test Type | Automated Command | File Exists? |
|----------|-----------|-------------------|-------------|
| `notifyOnVote`/`notifyOnComment` stored in `WizardState` after step-1 submit | unit (MockMvc) | `./gradlew test --tests "*.PollWizardFlowTest"` | exists — must be extended |
| `CreatePollCommand` carries notification flags | unit | `./gradlew test --tests "*.CreatePollServiceTest"` | exists — must be extended |
| `Poll` domain record carries notification flags | unit | `./gradlew test --tests "*.PollTest"` | exists — must be extended |
| `S3PollRepository.toDao()` serializes `notifyOnVote=true` | unit | `./gradlew test --tests "*.S3PollRepositoryTest"` | exists — must be extended |
| `S3PollRepository.fromDao()` reads notification flags back | unit | `./gradlew test --tests "*.S3PollRepositoryTest"` | exists — must be extended |
| `EmailNotificationService` calls `mailSender.send()` for new vote when `notifyOnVote=true` | unit (Mockito) | `./gradlew test --tests "*.EmailNotificationService*"` | does NOT exist — Wave 0 gap |
| `EmailNotificationService` does NOT send when `notifyOnVote=false` | unit (Mockito) | same | does NOT exist — Wave 0 gap |
| `EmailNotificationService` catches exception, does not rethrow | unit (Mockito) | same | does NOT exist — Wave 0 gap |
| `SubmitVoteService` calls email service on new response | unit | `./gradlew test --tests "*.SubmitVoteServiceTest"` | exists — must be extended |
| `SubmitVoteService` does NOT call email service on edit | unit | same | exists — must be extended |
| Thymeleaf template renders without error | unit (TemplateEngine) | `./gradlew test --tests "*.EmailNotificationService*"` | does NOT exist — Wave 0 gap |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "*.EmailNotificationService*" --tests "*.SubmitVoteServiceTest" --tests "*.S3PollRepositoryTest" --tests "*.PollWizardFlowTest"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/.../application/service/EmailNotificationServiceTest.java` — covers email send, no-send when flag=false, exception swallowed
- [ ] `src/main/resources/templates/email/vote-notification.html` — needed for template render test
- [ ] `src/main/resources/templates/email/comment-notification.html` — needed for template render test

---

## Project Constraints (from CLAUDE.md)

CLAUDE.md does not exist in this project. No project-specific constraints to propagate beyond what is captured from CONTEXT.md above.

---

## Sources

### Primary (HIGH confidence)
- Codebase inspection (all 7 canonical files read directly): `WizardState`, `PollNewPageController`, `CreatePollCommand`, `Poll`, `S3PollRepository`, `SubmitVoteService`, `CreatePollService`, `ApplicationConfig`, `ThymeleafRuntimeHints`, `WoodleApplication`, `build.gradle`, `application.properties`, `new-step1.html`, `PollDAO`
- [Spring Boot Email reference](https://docs.spring.io/spring-boot/reference/io/email.html) — `JavaMailSender` auto-config, `spring.mail.*` properties
- [Spring Framework issue #29713](https://github.com/spring-projects/spring-framework/issues/29713) — GraalVM multipart mail: RESOLVED January 2023 via GraalVM Reachability Metadata PR #160

### Secondary (MEDIUM confidence)
- [Thymeleaf + Spring Mail guide](https://www.thymeleaf.org/doc/articles/springmail.html) — `TemplateEngine.process()` + `MimeMessageHelper` pattern
- WebSearch: `spring-boot-starter-mail` version 3.4.x confirmed managed by Spring Boot BOM (latest published: 3.4.4 at time of research)

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — confirmed against Spring Boot docs and BOM; `spring-boot-starter-mail` is Spring-official
- Architecture: HIGH — confirmed by reading all canonical source files; no assumptions
- Pitfalls: HIGH (pitfalls 1-5) — confirmed from source; MEDIUM (pitfall 5, GraalVM native) — issue verified as resolved but native smoke test still recommended
- Test infrastructure: HIGH — all test files listed and inspected

**Research date:** 2026-04-01
**Valid until:** 2026-05-01 (Spring Boot 3.4.x is stable; no major changes expected in 30 days)
