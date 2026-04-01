# Native Deploy Gotchas (GraalVM)

This document captures failures we hit with `DEPLOY_RUNTIME=native` and how to avoid them.

## Agent usage

- Agent policy entrypoint: `AGENTS.md` (section `Native Thymeleaf + GraalVM Guardrails (Agent Required)`).
- Skill workflow: `.codex/skills/native-thymeleaf-graalvm/SKILL.md`.
- Keep this document as the canonical source of native incident patterns and prevention rules.

## 1) Thymeleaf/SpEL reflective calls can fail in native

### Symptom
- `POST /poll/step-2` returns `503` (CloudFront fallback page).
- Lambda logs contain:
  - `MissingReflectionRegistrationError`
  - `The program tried to reflectively invoke method ... ArrayList.size()`

### Root cause
- SpEL method calls in templates (for example `dateValues.size()`, `#lists.size(...)`) can become reflective invocation paths.
- GraalVM native image blocks reflection unless explicitly registered.

### Prevention
- In templates, avoid method calls for list sizing/bounds checks.
- Prefer controller-prepared model data with fixed/padded shape and direct index access.
- Keep template expressions simple (`${values[i - 1]}`) and push conditional/list-shaping logic into Java.

## 2) `@Bean` method self-invocation is brittle with AOT/native

### Symptom
- App fails during startup.
- Lambda logs contain:
  - `Email provider smtp is enabled but no JavaMailSender bean is available`
  - or `required a bean named 'javaMailSender' that could not be found`

### Root cause
- One `@Bean` method calling another `@Bean` method for fallback can be intercepted as bean lookup/proxy behavior.
- With AOT/native and conditional beans, this can fail even if local JVM tests look fine.

### Prevention
- Do not call `@Bean` methods from other `@Bean` methods for fallback construction.
- Extract shared creation logic into a private helper method and call the helper directly.
- Keep conditional bean wiring explicit and test SMTP/SES permutations.

## 3) Fast triage commands

Use these after native deploy when step flow fails:

```bash
# Confirm backend status quickly
curl -i https://qs.woodle.click/poll/active-count
curl -i -X POST https://qs.woodle.click/poll/step-2 \
  -d 'authorName=Max&authorEmail=max%40example.com&pollTitle=Test&description=Desc'

# Find current Lambda log group
aws logs describe-log-groups \
  --log-group-name-prefix /aws/lambda/woodle-qs \
  --region eu-central-1 \
  --query 'logGroups[].logGroupName' --output text

# Tail recent logs
aws logs tail <LOG_GROUP_NAME> --since 15m --region eu-central-1 --format short
```

## 4) Post-deploy smoke checklist (required for native)

After `DEPLOY_RUNTIME=native ./aws-deploy.sh`:

1. Open `/poll/new-step1.html`.
2. Fill step 1 and click `Weiter zum 2. Schritt`.
3. Verify transition to `/poll/step-2` succeeds (no `503`).
4. Verify `/poll/active-count` is `200`.
5. Continue step 2 -> step 3 once to ensure template rendering and session flow are healthy.

## 5) Current known anti-patterns

- Avoid in templates:
  - `#lists.size(...)`
  - `.size()` method calls in SpEL
- Avoid in config:
  - `@Bean` method A calling `@Bean` method B for runtime fallback

If these are needed, add explicit native runtime hints and tests first.

## 6) Thymeleaf property access can fail when a model type is missing runtime hints

### Symptom
- Public poll URL returns `503` on CloudFront fallback page (`error-unavailable.html`).
- Admin URL for the same poll can still work.
- Lambda logs show:
  - `Exception evaluating SpringEL expression: "group.span" (template: "poll/view" - line 42, col 21)`
  - `EL1008E: Property or field 'span' cannot be found on type '...PollViewController$DateGroup'`

### Root cause
- Native image did not include reflection metadata for one Thymeleaf model type:
  `PollViewController$DateGroup`.
- `MonthGroup` was registered, `DateGroup` was missing.
- Public page renders `dateGroups` (`group.span`), admin page does not, so behavior differs by route.

### Prevention
- Keep `ThymeleafRuntimeHints` in sync with every template-facing model/record.
- Add/maintain a test that fails if `DateGroup` (and similar model types) are not registered.
- When a CloudFront `503` appears, always check API Gateway URL directly to distinguish cache/routing from backend template errors.

## 7) Native deploy can reuse stale image layers unless cache is bypassed

### Symptom
- Deploy reports success, Lambda image tag changes, but runtime behavior/log signature stays unchanged.
- CloudWatch still shows old stack traces after deploy.

### Root cause
- `docker buildx build` in deploy path uses cache by default.
- For native builds, stale builder cache can keep an old compiled binary path alive across tags.

### Prevention
- For incident recovery deploys, force fresh native build:

```bash
docker buildx build --no-cache --platform linux/arm64 --provenance=false --sbom=false \
  -f Dockerfile.lambda.native -t <image-uri:new-tag> --push .
aws lambda update-function-code --function-name <function-name> --region eu-central-1 --image-uri <image-uri:new-tag>
```

- Verify rollout with both:
  - Lambda code hash changed (`aws lambda get-function-configuration ... --query CodeSha256`)
  - Live request to API Gateway URL (not only CloudFront URL)
