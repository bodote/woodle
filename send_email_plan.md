# AWS Lambda Email Sending Plan (SES)

## Goal
Enable the Woodle server (including AWS Lambda deployment mode) to send transactional emails via Amazon SES using an AWS-native, least-privilege setup.

## Scope for this iteration
- Send a poll-created confirmation email to the poll author after `POST /v1/polls` succeeds.
- Keep sending optional and environment-driven (`woodle.email.enabled`).
- Preserve local developer flow with a no-op default sender.

## Execution Steps

1. TDD gate: add first API-level failing test ✅
- Add a Spring MVC/API-level test that creates a poll through `POST /v1/polls` and expects an email send side effect.
- Keep compile green, fail via assertion (behavioral failure).
- Self-review the test for stable assertions and non-brittle expectations.

2. Introduce outbound email port in application layer ✅
- Add an application port for poll notification emails.
- Inject the port into `CreatePollService`.
- Trigger notification after poll persistence.

3. Implement infrastructure adapters ✅
- Add `NoopPollEmailSender` (default for local/test and when disabled).
- Add `SesPollEmailSender` using AWS SDK v2 SES client.
- Build plain text email content with explicit placeholders and deterministic link composition.

4. Wire configuration + dependencies ✅
- Add SES dependency (`software.amazon.awssdk:sesv2`).
- Add config properties in `application.properties`:
  - `woodle.email.enabled`
  - `woodle.email.from`
  - `woodle.email.subject-prefix`
- Add beans in `ApplicationConfig` (or dedicated email config):
  - SES client only when email enabled
  - sender bean selection (SES vs no-op)

5. Expand tests ✅
- Unit test `CreatePollService` to verify notification invocation and payload.
- Unit test SES adapter request mapping (destination, source, subject/body).
- Keep API-level test as primary behavior test.

6. Verification ✅
- Run targeted tests first, then broader suite.
- Ensure no regressions on existing poll API tests.

7. Deployment/IAM notes ✅
- Document least-privilege IAM policy for Lambda role (`ses:SendEmail` scoped to verified identity).
- Document SES prerequisites: verified identity, DKIM/SPF/DMARC, sandbox exit.

8. Admin UX fallback when email sending fails ✅
- Propagate email failure state from poll creation redirect to admin URL (`?emailFailed=true`).
- Render a visible warning in admin view so creators can manually share links if delivery fails.
- Cover with Web MVC tests for redirect behavior and warning rendering.

## Definition of Done
- Poll creation triggers one email send request when email is enabled.
- No-op behavior when disabled.
- Tests cover API behavior and service/adaptor mapping.
- Admin page shows warning when email delivery fails.
- Build and tests pass for changed scope.
