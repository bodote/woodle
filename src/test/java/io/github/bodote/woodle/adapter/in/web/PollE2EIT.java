package io.github.bodote.woodle.adapter.in.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Playwright E2E")
class PollE2EIT {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("full flow: create poll, admin edit, participant vote")
    void fullFlow() {
        String baseUrl = "http://localhost:" + port;

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
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
            Page.GetByRoleOptions rowOptions = new Page.GetByRoleOptions().setName("Ihr Name");
            Page.GetByRoleOptions editRowOptions = new Page.GetByRoleOptions().setName("Alice");
            page.getByRole(AriaRole.ROW, rowOptions).getByRole(AriaRole.TEXTBOX).fill("Alice");
            page.getByRole(AriaRole.ROW, rowOptions).getByRole(AriaRole.COMBOBOX).nth(0).selectOption("YES");
            page.getByRole(AriaRole.ROW, rowOptions).getByRole(AriaRole.COMBOBOX).nth(1).selectOption("IF_NEEDED");
            page.getByRole(AriaRole.ROW, rowOptions)
                    .getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Speichern"))
                    .click();
            page.waitForURL(participantUrl);

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
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
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

    private String toParticipantUrl(String adminUrl) {
        int index = adminUrl.indexOf("/poll/");
        String path = adminUrl.substring(index + "/poll/".length());
        int lastDash = path.lastIndexOf('-');
        String pollId = path.substring(0, lastDash);
        return adminUrl.substring(0, index) + "/poll/" + pollId;
    }
}
