# Woodle Tech Stack & Code Structure

## Programming Language & Framework
- Java
- Spring Boot
- Thymeleaf as the HTML template engine
- HTMX (https://htmx.org) for dynamic interactions
- HTMX is served as a local static asset (`/js/vendor/htmx.min.js`), not from third-party CDNs.

## Runtime & Deployment
- No permanently running Spring Boot server.
- Deployment as AWS Lambda functions so costs occur only when the app is used.

## Build & Quality
- Build tool: Gradle (`build.gradle`).
- Code coverage: JaCoCo is configured in `build.gradle` and used for coverage reports and coverage checks.
- Mutation testing: Pitest is configured in `build.gradle` (`gradle pitest`).

## Identification & Data Model
- No user management.
- Every new poll gets a UUID.
- The poll's master data and later participant selections/responses are stored under that UUID.
- Author access uses an additional secret component in the admin URL, alongside the UUID.

## URL Patterns
- New poll: `<sitename>/poll/new`
- Participant link: `<sitename>/poll/<UUID>`
- Admin link: `<sitename>/poll/<UUID>-<admin-secret>`
- `admin-secret` is a random, URL-safe string with about 12 characters (for example Base62).

## Persistence
- Storage is not in a database.
- Data is stored in Amazon S3 (for example JSON files per poll UUID).
- For local tests, an S3-compatible server is used via Testcontainers.

## Data Lifecycle
- Polls in S3 are fully deleted after their expiry date.

## Code Structure: Hexagonal Architecture

Goal: clear separation of domain logic, use cases, and technical adapters. There are no Modulith modules; the structure is purely hexagonal.

### Target Structure (Root Package)

```
io.github.bodote.woodle
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
├── domain
│   ├── model
│   ├── event
│   └── exception
├── adapter
│   ├── in
│   │   └── web
│   └── out
│       ├── persistence
│       └── integration
└── config
```

### Package Meanings
- `domain`: domain model, invariants, value objects, domain events. No dependency on Spring or technical details.
- `application`: use cases and ports. Defines the system's inbound and outbound interfaces.
- `adapter`: technical implementations of ports, for example web controllers, S3 persistence, and external integrations.
- `config`: Spring wiring and technical configuration.

### Dependency Rules
- `domain` depends on nothing else in the project.
- `application` may depend on `domain`, but never on `adapter` or `config`.
- `adapter` may depend on `application`, `domain`, and `config`.
- `config` may depend on all packages; no other package may depend on `config`.

### Architecture Tests (JUnit)
We verify the hexagonal rules with a JUnit architecture test (ArchUnit) so dependency boundaries stay measurable.
Dependency: `com.tngtech.archunit:archunit-junit5:latest`

Example:

```java
@AnalyzeClasses(packages = "io.github.bodote.woodle")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainHasNoOutgoingDependencies =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..",
                            "..adapter..",
                            "..config.."
                    );
}
```

### Naming Conventions
- `DTO` is allowed only for public API transfer types.
- Internal/domain/application types must not use the `DTO` suffix.
- `Optional` is used only as a return type, never as a method parameter.

## Lambda Integration
- API Gateway + Spring Cloud Function.
- HTTP requests are received through function adapters; there is no permanently running server.

## Email Delivery
- Optional email delivery after successful poll creation via Amazon SES (AWS SDK v2 `SesV2Client`).
- Configuration via:
  - `woodle.email.enabled`
  - `woodle.email.from`
  - `woodle.email.subject-prefix`
- Fallback when email is disabled: no-op sender without delivery.
- IAM permission in the Lambda context: `ses:SendEmail` restricted to the configured sender address.

## S3 Data Model (Review Recommendation)

Goal: exactly **one** JSON file per poll. The S3 key is the generated poll UUID.

### S3 Keys
- `polls/{pollId}.json` (a single document containing master data, options, and responses)

### poll.json (Whole Document)
```json
{
  "pollId": "UUID",
  "type": "date",
  "title": "string",
  "descriptionHtml": "string",
  "language": "de",
  "createdAt": "2026-02-06T12:00:00Z",
  "updatedAt": "2026-02-06T12:00:00Z",
  "author": {
    "name": "string",
    "email": "string"
  },
  "access": {
    "customSlug": "string|null",
    "passwordHash": "string|null",
    "resultsPublic": true,
    "adminToken": "string"
  },
  "permissions": {
    "voteChangePolicy": "ALL_CAN_EDIT|ONLY_OWN_CAN_EDIT|NONE_CAN_EDIT"
  },
  "notifications": {
    "onVote": true,
    "onComment": true
  },
  "resultsVisibility": {
    "onlyAuthor": false
  },
  "status": "DRAFT|OPEN|CLOSED",
  "expiresAt": "2026-03-10",
  "options": {
    "eventType": "ALL_DAY|INTRADAY",
    "durationMinutes": 180,
    "items": [
    {
      "optionId": "UUID",
      "date": "2026-02-10",
      "startTime": "11:00",
      "endTime": "14:00"
    }
    ]
  },
  "responses": [
    {
      "responseId": "UUID",
      "participantName": "string",
      "createdAt": "2026-02-06T12:00:00Z",
      "votes": [
        { "optionId": "UUID", "value": "YES|NO|IF_NEEDED" }
      ],
      "comment": "string|null"
    }
  ]
}
```

### Retention Period
- `expiresAt` = last date + 4 weeks.
- After that, fully delete all `polls/{pollId}/**` objects.

## Validation
- No user-account management with login/password.
- Access is via UUID and admin secret.
- Email addresses remain domain-relevant data and may be validated.

## Option Mutability
- For intraday events, `endTime` is stored explicitly and is not derived later from `startTime + durationMinutes`.
- Authors may add or remove dates/times after creation; existing times keep their originally stored values.
