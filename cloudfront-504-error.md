# CloudFront 504 Error Summary

## What happened
- During Playwright E2E on `https://woodle.click`, the transition from step 1 to step 2 (`/poll/step-2`) returned a `504 Gateway Timeout` from CloudFront once.
- Immediate retry of the same flow succeeded.

## Observed behavior
- Error page text: `504 Gateway Timeout ERROR` / `The request could not be satisfied`.
- The issue was transient (single occurrence) and not reproducible in the next run.

## Root cause (most likely)
- Cold-start latency on the Lambda behind API Gateway/CloudFront caused the origin response to exceed the edge timeout window for that specific request.
- This is consistent with startup-heavy Java Lambda behavior and an initialization timeout/restart pattern.

## Supporting evidence from logs
- Lambda logs around that period showed:
  - `INIT_REPORT ... Status: timeout`
  - Followed by a new container start and successful request handling afterward.
- Subsequent requests completed normally, including full poll creation flows.

## Why this was not a functional regression
- No persistent routing or deployment misconfiguration was found.
- Same endpoint worked immediately on retry.
- Later end-to-end checks on both `qs` and `prod` passed.

## Recommended mitigation
- Keep Lambda warm (scheduled ping) for critical paths.
- Reduce cold-start impact (optimize startup path / memory tuning / runtime optimizations).
- Add client-side retry guidance for transient 5xx during wizard navigation.
- Monitor CloudWatch metrics (`5XX`, duration, init duration) to detect frequency.

