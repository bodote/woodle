package io.github.bodote.woodle.adapter.in.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DisplayName("Playwright E2E")
class PollE2EIT {

    private static final String BUCKET = "woodle-e2e";
    private static final String NOT_HEADLESS_PROPERTY = "playwright.not-headless";

    // Initialised lazily so that GraalVM processTestAot can load this class
    // even when Docker is unavailable.  At test-execution time the
    // @Testcontainers(disabledWithoutDocker = true) extension will skip all
    // tests when localstack is null (Docker was not found).
    @Container
    static final LocalStackContainer localstack;

    static {
        LocalStackContainer container = null;
        try {
            container = new LocalStackContainer(
                    DockerImageName.parse("localstack/localstack:3.1.0"))
                    .withServices(LocalStackContainer.Service.S3);
        } catch (Exception ignored) {
            // Docker not reachable during AOT processing – tests will be skipped at runtime
        }
        localstack = container;
    }

    @DynamicPropertySource
    static void localstackProperties(DynamicPropertyRegistry registry) {
        if (localstack == null) return;
        registry.add("woodle.s3.enabled", () -> "true");
        registry.add("woodle.s3.endpoint",
                () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("woodle.s3.region", localstack::getRegion);
        registry.add("woodle.s3.accessKey", localstack::getAccessKey);
        registry.add("woodle.s3.secretKey", localstack::getSecretKey);
        registry.add("woodle.s3.bucket", () -> BUCKET);
    }

    @BeforeAll
    static void createBucket() {
        if (localstack == null) return;
        S3Client s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Autowired
    private Environment environment;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("full flow: create poll, admin edit, participant vote")
    void fullFlow() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(browserLaunchOptions());
            Page page = browser.newPage();

            page.navigate(baseUrl + "/poll/new");
            page.getByLabel("Ihr Name").fill("Max");
            page.getByLabel("Ihre E-Mail-Adresse").fill("max@example.com");
            page.getByLabel("Titel der Umfrage").fill("Team Meeting");
            page.getByLabel("Beschreibung").fill("Testbeschreibung");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter zum 2. Schritt")).click();

            page.locator("input[name='dateOption1']").fill("2026-02-10");
            page.locator("input[name='dateOption2']").fill("2026-02-11");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter")).click();

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Umfrage erstellen")).click();

            String adminUrl = page.url();
            assertTrue(adminUrl.contains("/poll/"));

            page.locator("#new-date").fill("2026-02-12");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Datum hinzufügen")).click();
            page.waitForSelector("#options-list >> text=2026-02-12");

            String participantUrl = toParticipantUrl(adminUrl);
            page.navigate(participantUrl);
            page.waitForSelector("#participant-name");
            Page.GetByRoleOptions editRowOptions = new Page.GetByRoleOptions().setName("Alice");
            page.locator("#participant-name").fill("Alice");
            page.locator("select[name^='vote_new_']").nth(0).selectOption("YES");
            page.locator("select[name^='vote_new_']").nth(1).selectOption("IF_NEEDED");
            page.locator("tr[data-add-row='true']")
                    .getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Speichern"))
                    .click();
            page.getByRole(AriaRole.ROW, editRowOptions).waitFor();

            page.getByRole(AriaRole.ROW, editRowOptions)
                    .getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Zeile bearbeiten: Alice"))
                    .click();
            page.getByRole(AriaRole.ROW, editRowOptions).getByRole(AriaRole.COMBOBOX).nth(1).selectOption("NO");
            page.getByRole(AriaRole.ROW, editRowOptions)
                    .getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Speichern"))
                    .click();
            page.getByRole(AriaRole.ROW, editRowOptions).waitFor();
            page.waitForSelector("tr[data-edit-row='Alice'] >> text=✗");
            assertTrue(page.locator("tr[data-edit-row='Alice']").innerText().contains("✗"));

            browser.close();
        }
    }

    @Test
    @DisplayName("static step-1 entrypoint continues to backend step-2")
    void staticStep1EntrypointContinuesToStep2() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(browserLaunchOptions());
            Page page = browser.newPage();

            page.navigate(baseUrl + "/poll/new-step1.html");
            page.getByLabel("Ihr Name").fill("Max");
            page.getByLabel("Ihre E-Mail-Adresse").fill("max@example.com");
            page.getByLabel("Titel der Umfrage").fill("Static Entry Poll");
            page.getByLabel("Beschreibung").fill("From static entry");
            APIResponse response = page.request().post(
                    baseUrl + "/poll/step-2",
                    RequestOptions.create().setForm(FormData.create()
                            .set("authorName", "Max")
                            .set("authorEmail", "max@example.com")
                            .set("pollTitle", "Static Entry Poll")
                            .set("description", "From static entry"))
            );

            assertTrue(response.status() == 200);
            assertTrue(response.url().contains("/poll/step-2"));

            page.navigate(baseUrl + "/poll/step-2");
            assertTrue(page.url().contains("/poll/step-2"));
            assertTrue(page.content().contains("Mindestens zwei alternative Zeitpunkte"));
            browser.close();
        }
    }

    @Test
    @DisplayName("/poll/new redirect entrypoint continues to backend step-2")
    void pollNewRedirectEntrypointContinuesToStep2() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(browserLaunchOptions());
            Page page = browser.newPage();

            page.navigate(baseUrl + "/poll/new");
            assertTrue(page.url().contains("/poll/new-step1.html"));

            APIResponse response = page.request().post(
                    baseUrl + "/poll/step-2",
                    RequestOptions.create().setForm(FormData.create()
                            .set("authorName", "Max")
                            .set("authorEmail", "max@example.com")
                            .set("pollTitle", "Redirect Entry Poll")
                            .set("description", "From redirect entry"))
            );

            assertTrue(response.status() == 200);
            assertTrue(response.url().contains("/poll/step-2"));

            page.navigate(baseUrl + "/poll/step-2");
            assertTrue(page.url().contains("/poll/step-2"));
            assertTrue(page.content().contains("Mindestens zwei alternative Zeitpunkte"));
            browser.close();
        }
    }

    @Test
    @DisplayName("step-2 intraday keeps existing values when adding third proposal")
    void step2IntradayKeepsExistingValuesWhenAddingThirdProposal() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(browserLaunchOptions());
            Page page = browser.newPage();

            page.navigate(baseUrl + "/poll/new");
            page.getByLabel("Ihr Name").fill("Max");
            page.getByLabel("Ihre E-Mail-Adresse").fill("max@example.com");
            page.getByLabel("Titel der Umfrage").fill("Intraday Preserve");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter zum 2. Schritt")).click();

            page.getByLabel("Untertägig").check();
            page.waitForSelector("input[name='startTime1_1']");

            page.locator("input[name='dateOption1']").fill("2026-03-10");
            page.locator("input[name='dateOption2']").fill("2026-03-11");
            page.locator("input[name='startTime1_1']").fill("09:15");
            page.locator("input[name='startTime2_1']").fill("13:45");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Tag hinzufügen")).click();
            page.waitForSelector("input[name='dateOption3']");
            page.waitForSelector("input[name='startTime3_1']");

            assertTrue("2026-03-10".equals(page.locator("input[name='dateOption1']").inputValue()));
            assertTrue("2026-03-11".equals(page.locator("input[name='dateOption2']").inputValue()));
            assertTrue("09:15".equals(page.locator("input[name='startTime1_1']").inputValue()));
            assertTrue("13:45".equals(page.locator("input[name='startTime2_1']").inputValue()));

            browser.close();
        }
    }

    @Test
    @DisplayName("step-2 all-day keeps existing values when adding third proposal")
    void step2AllDayKeepsExistingValuesWhenAddingThirdProposal() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(browserLaunchOptions());
            Page page = browser.newPage();

            page.navigate(baseUrl + "/poll/new");
            page.getByLabel("Ihr Name").fill("Max");
            page.getByLabel("Ihre E-Mail-Adresse").fill("max@example.com");
            page.getByLabel("Titel der Umfrage").fill("All Day Preserve");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter zum 2. Schritt")).click();

            page.locator("input[name='dateOption1']").fill("2026-03-10");
            page.locator("input[name='dateOption2']").fill("2026-03-11");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Einen Tag hinzufügen")).click();
            page.waitForSelector("input[name='dateOption3']");

            assertTrue("2026-03-10".equals(page.locator("input[name='dateOption1']").inputValue()));
            assertTrue("2026-03-11".equals(page.locator("input[name='dateOption2']").inputValue()));

            browser.close();
        }
    }

    @Test
    @DisplayName("admin static page copy button copies participant link to clipboard")
    void adminStaticPageCopyButtonCopiesParticipantLinkToClipboard() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(browserLaunchOptions());
            Page page = browser.newPage();

            page.navigate(baseUrl + "/poll/new");
            page.getByLabel("Ihr Name").fill("Max");
            page.getByLabel("Ihre E-Mail-Adresse").fill("max@example.com");
            page.getByLabel("Titel der Umfrage").fill("Copy Test Poll");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter zum 2. Schritt")).click();

            page.locator("input[name='dateOption1']").fill("2026-04-10");
            page.locator("input[name='dateOption2']").fill("2026-04-11");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter")).click();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Umfrage erstellen")).click();

            page.waitForSelector("button[data-copy-target='participant-link']");
            page.evaluate("""
                    () => {
                        window.__copiedText = null;
                        Object.defineProperty(navigator, 'clipboard', {
                            configurable: true,
                            value: {
                                writeText: (text) => {
                                    window.__copiedText = text;
                                    return Promise.resolve();
                                }
                            }
                        });
                    }
                    """);
            page.locator("button[data-copy-target='participant-link']").click();

            String expectedParticipantLink = page.locator("#participant-link").innerText().trim();
            String copiedText = page.evaluate("() => window.__copiedText").toString();
            assertEquals(expectedParticipantLink, copiedText);
            browser.close();
        }
    }

    @Test
    @DisplayName("admin intraday edit adds and removes time inputs")
    void adminIntradayEditAddsAndRemovesTimeInputs() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(browserLaunchOptions());
            Page page = browser.newPage();

            page.navigate(baseUrl + "/poll/new");
            page.getByLabel("Ihr Name").fill("Max");
            page.getByLabel("Ihre E-Mail-Adresse").fill("max@example.com");
            page.getByLabel("Titel der Umfrage").fill("Intraday Admin Edit");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter zum 2. Schritt")).click();

            page.getByLabel("Untertägig").check();
            page.waitForSelector("input[name='startTime1_1']");
            page.locator("input[name='dateOption1']").fill("2026-05-10");
            page.locator("input[name='dateOption2']").fill("2026-05-11");
            page.locator("input[name='startTime1_1']").fill("09:00");
            page.locator("input[name='startTime2_1']").fill("10:00");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter")).click();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Umfrage erstellen")).click();
            page.waitForSelector("form#admin-options-form");

            assertEquals(1, page.locator("#admin-start-times input[name='startTime']").count());

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Uhrzeit hinzufügen")).click();
            assertEquals(2, page.locator("#admin-start-times input[name='startTime']").count());

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Uhrzeit entfernen")).click();
            assertEquals(1, page.locator("#admin-start-times input[name='startTime']").count());
            assertTrue(page.locator("#admin-remove-time").isDisabled());
            assertEquals(1, page.locator("#admin-start-times input[name='startTime']").count());
            browser.close();
        }
    }

    @Test
    @DisplayName("creates all-day poll with 30 date options from march 1 to march 30")
    void createsAllDayPollWithThirtyDateOptions() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(browserLaunchOptions());
            Page page = browser.newPage();

            page.navigate(baseUrl + "/poll/new");
            page.getByLabel("Ihr Name").fill("Max");
            page.getByLabel("Ihre E-Mail-Adresse").fill("max@example.com");
            page.getByLabel("Titel der Umfrage").fill("März mit 30 Tagen");
            page.getByLabel("Beschreibung").fill("E2E mit vielen Ganztags-Terminen");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter zum 2. Schritt")).click();

            for (int dayIndex = 3; dayIndex <= 30; dayIndex++) {
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Einen Tag hinzufügen")).click();
                page.waitForSelector("input[name='dateOption" + dayIndex + "']");
            }

            for (int dayIndex = 1; dayIndex <= 30; dayIndex++) {
                LocalDate date = LocalDate.of(2026, 3, dayIndex);
                page.locator("input[name='dateOption" + dayIndex + "']").fill(date.toString());
            }

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter")).click();
            assertEquals(30, page.locator(".summary-list li").count());
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Umfrage erstellen")).click();

            String participantUrl = toParticipantUrl(page.url());
            page.navigate(participantUrl);
            page.waitForSelector("#poll-votes-table");

            assertEquals(30, page.locator("#poll-votes-table thead th.votes-table__date").count());
            @SuppressWarnings("unchecked")
            Map<String, Object> scrollMetrics = (Map<String, Object>) page.evaluate("""
                    () => {
                        const wrap = document.querySelector('.votes-table-wrap');
                        const html = document.documentElement;
                        return {
                            viewportWidth: window.innerWidth,
                            wrapperClientWidth: wrap.clientWidth,
                            wrapperScrollWidth: wrap.scrollWidth,
                            pageScrollWidth: html.scrollWidth
                        };
                    }
                    """);
            int viewportWidth = ((Number) scrollMetrics.get("viewportWidth")).intValue();
            int wrapperClientWidth = ((Number) scrollMetrics.get("wrapperClientWidth")).intValue();
            int wrapperScrollWidth = ((Number) scrollMetrics.get("wrapperScrollWidth")).intValue();
            int pageScrollWidth = ((Number) scrollMetrics.get("pageScrollWidth")).intValue();
            assertTrue(wrapperScrollWidth > wrapperClientWidth);
            assertTrue(wrapperClientWidth <= viewportWidth);
            assertTrue(pageScrollWidth <= viewportWidth);
            browser.close();
        }
    }

    @Test
    @DisplayName("keeps first and last participant columns fixed while horizontal scroll moves date columns")
    void keepsFirstAndLastParticipantColumnsFixedWhileHorizontalScrollMovesDateColumns() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(browserLaunchOptions());
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(820, 900));

            page.navigate(baseUrl + "/poll/new");
            page.getByLabel("Ihr Name").fill("Max");
            page.getByLabel("Ihre E-Mail-Adresse").fill("max@example.com");
            page.getByLabel("Titel der Umfrage").fill("Sticky Columns");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter zum 2. Schritt")).click();

            for (int dayIndex = 3; dayIndex <= 20; dayIndex++) {
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Einen Tag hinzufügen")).click();
                page.waitForSelector("input[name='dateOption" + dayIndex + "']");
            }

            for (int dayIndex = 1; dayIndex <= 20; dayIndex++) {
                LocalDate date = LocalDate.of(2026, 4, dayIndex);
                page.locator("input[name='dateOption" + dayIndex + "']").fill(date.toString());
            }

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Weiter")).click();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Umfrage erstellen")).click();

            String participantUrl = toParticipantUrl(page.url());
            page.navigate(participantUrl);
            page.waitForSelector("#poll-votes-table");

            @SuppressWarnings("unchecked")
            Map<String, Object> positions = (Map<String, Object>) page.evaluate("""
                    () => {
                        const wrap = document.querySelector('.votes-table-wrap');
                        const row = document.querySelector('.votes-table tbody tr[data-add-row="true"]');
                        const cells = row.querySelectorAll('th, td');
                        const middleIndex = Math.floor(cells.length / 2);
                        const firstBefore = cells[0].getBoundingClientRect();
                        const middleBefore = cells[middleIndex].getBoundingClientRect();
                        const lastBefore = cells[cells.length - 1].getBoundingClientRect();

                        wrap.scrollLeft = wrap.scrollWidth - wrap.clientWidth;

                        const firstAfter = cells[0].getBoundingClientRect();
                        const middleAfter = cells[middleIndex].getBoundingClientRect();
                        const lastAfter = cells[cells.length - 1].getBoundingClientRect();

                        return {
                            firstBefore: firstBefore.left,
                            middleBefore: middleBefore.left,
                            lastBefore: lastBefore.left,
                            firstAfter: firstAfter.left,
                            middleAfter: middleAfter.left,
                            lastAfter: lastAfter.left
                        };
                    }
                    """);

            double firstBefore = ((Number) positions.get("firstBefore")).doubleValue();
            double middleBefore = ((Number) positions.get("middleBefore")).doubleValue();
            double lastBefore = ((Number) positions.get("lastBefore")).doubleValue();
            double firstAfter = ((Number) positions.get("firstAfter")).doubleValue();
            double middleAfter = ((Number) positions.get("middleAfter")).doubleValue();
            double lastAfter = ((Number) positions.get("lastAfter")).doubleValue();

            double firstShift = Math.abs(firstBefore - firstAfter);
            double middleShift = Math.abs(middleBefore - middleAfter);
            double lastShift = Math.abs(lastBefore - lastAfter);

            assertTrue(middleShift > 20.0);
            assertTrue(firstShift < 8.0,
                    () -> "Expected first sticky column to remain nearly fixed, but shifted by " + firstShift + "px");
            assertTrue(lastShift < 8.0,
                    () -> "Expected last sticky column to remain nearly fixed, but shifted by " + lastShift + "px");
            assertTrue(firstShift < middleShift * 0.25,
                    () -> "Expected first sticky column to move much less than middle cells, but shifts were first="
                            + firstShift + "px and middle=" + middleShift + "px");
            assertTrue(lastShift < middleShift * 0.25,
                    () -> "Expected last sticky column to move much less than middle cells, but shifts were last="
                            + lastShift + "px and middle=" + middleShift + "px");

            browser.close();
        }
    }

    private String toParticipantUrl(String adminUrl) {
        int staticIndex = adminUrl.indexOf("/poll/static/");
        String prefix = "/poll/static/";
        int index = staticIndex;
        if (index < 0) {
            index = adminUrl.indexOf("/poll/");
            prefix = "/poll/";
        }
        String path = adminUrl.substring(index + prefix.length());
        int lastDash = path.lastIndexOf('-');
        String pollId = path.substring(0, lastDash);
        return adminUrl.substring(0, index) + prefix + pollId;
    }

    private BrowserType.LaunchOptions browserLaunchOptions() {
        boolean notHeadless = environment.getProperty(NOT_HEADLESS_PROPERTY, Boolean.class, false);
        return new BrowserType.LaunchOptions().setHeadless(!notHeadless);
    }
}
