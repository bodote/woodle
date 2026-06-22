///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Post-deploy smoke test for a live Woodle deployment.
 *
 * Intended to run right after a native (GraalVM) deploy. It exercises the code paths that
 * pass on the JVM but can break only in a real native image — and which the build's AOT
 * step does NOT catch (processTestAot only records declared hints; it never discovers a
 * missing one). It hits the API Gateway URL directly (not CloudFront) so caching cannot
 * mask backend errors. On the deployed stack a missing reflection hint or bad SpEL turns
 * into an HTTP 500, so a 200 on these template-rendering paths is the real signal.
 *
 * Native surface covered:
 *   - step-2 all-day option fragment render (step2-date-options, SpEL index lists),
 *   - step-2 intraday option fragment render (step2-datetime-options, timeIndexesByDay),
 *   - Jackson @RequestBody deserialization of CleanupEventDTO on /events,
 *   - full create flow -> PollDAO + WizardState document (de)serialization to S3,
 *   - participant + admin poll/view render (DateGroup / MonthGroup / OptionHeader).
 * Not covered: casting a vote (needs per-poll option ids -> too fragile for a smoke test);
 * those response-row template types (ParticipantRow / VoteCell) remain unverified here.
 *
 * Usage:  jbang tools/PostDeploySmokeTest.java <apiBaseUrl> [publicBaseUrl]
 *   apiBaseUrl     e.g. https://xxxx.execute-api.eu-central-1.amazonaws.com  (required)
 *   publicBaseUrl  e.g. https://qs.woodle.click  (optional; adds the static page check)
 * Exit:   0 = all checks passed, 1 = at least one failed, 2 = usage error.
 */
public class PostDeploySmokeTest {

    static final List<String> failures = new ArrayList<>();
    static final String TITLE = "SMOKE TEST - native post-deploy (safe to delete)";
    static String api;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: jbang tools/PostDeploySmokeTest.java <apiBaseUrl> [publicBaseUrl]");
            System.exit(2);
        }
        api = stripTrailingSlash(args[0]);
        String pub = args.length > 1 && !args[1].isBlank() ? stripTrailingSlash(args[1]) : null;

        System.out.println("Post-deploy smoke test against " + api);

        HttpClient base = client();
        waitForHealthy(base, api + "/poll/active-count");

        check("GET /poll/active-count == 200", () -> {
            HttpResponse<String> r = get(base, api + "/poll/active-count");
            return r.statusCode() == 200 ? null : "status " + r.statusCode();
        });

        check("POST /events with bad token == 403 (controller + DTO deserialization)", () -> {
            HttpResponse<String> r = postJson(base, api + "/events",
                    "{\"task\":\"cleanup-expired-polls\",\"token\":\"smoke-invalid-token\"}");
            return r.statusCode() == 403 ? null : "status " + r.statusCode();
        });

        if (pub != null) {
            check("GET " + pub + "/poll/new-step1.html == 200", () -> {
                HttpResponse<String> r = get(base, pub + "/poll/new-step1.html");
                return r.statusCode() == 200 ? null : "status " + r.statusCode();
            });
        }

        checkIntradayFragment();
        checkCreateAndView();

        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("OK - smoke test passed.");
            System.exit(0);
        }
        System.out.println("FAILED - smoke test (" + failures.size() + " problem(s)):");
        failures.forEach(f -> System.out.println("   - " + f));
        System.exit(1);
    }

    /** step-1 -> step-2 establishes the wizard session; the intraday switch renders the
     *  datetime option fragment (step2-datetime-options + timeIndexesByDay). */
    static void checkIntradayFragment() {
        check("step-2 intraday renders startTime1_1 (native Thymeleaf/SpEL)", () -> {
            HttpClient c = client();
            HttpResponse<String> s2 = postForm(c, api + "/poll/step-2", basicsBody());
            if (s2.statusCode() != 200) return "step-2 status " + s2.statusCode();
            if (!s2.body().contains("name=\"dateOption1\"")) return "no dateOption1 in step-2 (all-day) render";
            HttpResponse<String> intra = get(c, api + "/poll/step-2/event-type?eventType=INTRADAY");
            if (intra.statusCode() != 200) return "event-type status " + intra.statusCode();
            if (!intra.body().contains("name=\"startTime1_1\"")) return "no startTime1_1 in intraday fragment";
            return null;
        });
    }

    /** Full create flow then render both views, exercising PollDAO/WizardState (de)serialization
     *  to S3 and the poll/view template model types (DateGroup / MonthGroup / OptionHeader). */
    static void checkCreateAndView() {
        HttpClient c = client();
        String location;
        try {
            HttpResponse<String> s2 = postForm(c, api + "/poll/step-2", basicsBody());
            if (s2.statusCode() != 200) { fail("create: step-2 status " + s2.statusCode()); return; }
            HttpResponse<String> s3 = postForm(c, api + "/poll/step-3", step3AllDayBody());
            if (s3.statusCode() != 200) { fail("create: step-3 status " + s3.statusCode()); return; }
            HttpResponse<String> submit = postForm(c, api + "/poll/submit", submitBody());
            if (submit.statusCode() != 302) { fail("create: /poll/submit expected 302, got " + submit.statusCode()); return; }
            location = submit.headers().firstValue("Location").orElse("");
        } catch (Exception e) {
            fail("create flow: " + e);
            return;
        }

        int idx = location.indexOf("/poll/static/");
        if (idx < 0) { fail("create: unexpected redirect Location '" + location + "'"); return; }
        String path = location.substring(idx + "/poll/static/".length()).split("\\?")[0];
        if (path.length() < 38 || path.charAt(36) != '-') { fail("create: cannot parse poll id/secret from '" + path + "'"); return; }
        String pollId = path.substring(0, 36);
        String adminSecret = path.substring(37);
        System.out.println("  (created smoke poll " + pollId + ")");

        check("participant view GET /poll/{id} == 200 (poll/view, DateGroup)", () -> {
            HttpResponse<String> r = get(c, api + "/poll/" + pollId);
            if (r.statusCode() != 200) return "status " + r.statusCode();
            if (!r.body().contains("poll-votes-table")) return "no votes table in rendered participant view";
            return null;
        });

        check("admin view GET /poll/{id}-{secret} == 200 (poll/view admin)", () -> {
            HttpResponse<String> r = get(c, api + "/poll/" + pollId + "-" + adminSecret);
            return r.statusCode() == 200 ? null : "status " + r.statusCode();
        });
    }

    // --- form bodies -------------------------------------------------------------------

    static String basicsBody() {
        return form("authorName", "Smoke",
                "authorEmail", "smoke@example.com",
                "pollTitle", TITLE,
                "description", "Automated post-deploy smoke test");
    }

    static String step3AllDayBody() {
        return form("eventType", "ALL_DAY",
                "authorName", "Smoke",
                "authorEmail", "smoke@example.com",
                "pollTitle", TITLE,
                "description", "Automated post-deploy smoke test",
                "dateOption1", "2027-01-01",
                "dateOption2", "2027-01-02");
    }

    static String submitBody() {
        // expiresAt in the past makes the poll immediately eligible for the weekly cleanup,
        // so smoke-test polls do not accumulate in the QS bucket.
        return form("eventType", "ALL_DAY",
                "authorName", "Smoke",
                "authorEmail", "smoke@example.com",
                "pollTitle", TITLE,
                "description", "Automated post-deploy smoke test",
                "expiresAt", LocalDate.now().minusDays(1).toString(),
                "dateOption1", "2027-01-01",
                "dateOption2", "2027-01-02");
    }

    // --- helpers -----------------------------------------------------------------------

    /** Returns null when the check passes, or a short failure message otherwise. */
    interface Check {
        String run() throws Exception;
    }

    static void check(String name, Check c) {
        try {
            String problem = c.run();
            if (problem == null) {
                System.out.println("  PASS  " + name);
            } else {
                System.out.println("  FAIL  " + name + " -> " + problem);
                failures.add(name + " (" + problem + ")");
            }
        } catch (Exception e) {
            System.out.println("  FAIL  " + name + " -> " + e);
            failures.add(name + " (" + e + ")");
        }
    }

    static void fail(String message) {
        System.out.println("  FAIL  " + message);
        failures.add(message);
    }

    static void waitForHealthy(HttpClient c, String url) {
        for (int i = 1; i <= 12; i++) {
            try {
                if (get(c, url).statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // not reachable yet
            }
            System.out.println("  ... warm-up " + i + "/12");
            sleep(5);
        }
        System.out.println("  (warm-up did not reach 200; running checks anyway)");
    }

    static HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(new CookieManager())
                .build();
    }

    static HttpResponse<String> get(HttpClient c, String url) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> postForm(HttpClient c, String url, String body) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> postJson(HttpClient c, String url, String body) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
