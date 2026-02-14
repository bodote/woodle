package io.github.bodote.woodle.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;

import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextArea;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("static step-1 page")
class StaticPollStep1PageIT {

    private static final String BASE_URL = "http://localhost";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("serves static /poll/new-step1.html with required fields")
    void servesStaticStep1PageWithRequiredFields() throws Exception {
        mockMvc.perform(get("/poll/new-step1.html"))
                .andExpect(status().isOk());

        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");

            HtmlInput nameInput = page.getFirstByXPath("//input[@name='authorName']");
            HtmlInput emailInput = page.getFirstByXPath("//input[@name='authorEmail']");
            HtmlInput titleInput = page.getFirstByXPath("//input[@name='pollTitle']");
            HtmlTextArea descriptionInput = page.getFirstByXPath("//textarea[@name='description']");
            org.htmlunit.html.HtmlElement identityRow = page.getFirstByXPath("//div[contains(@class,'dual-row')]");

            org.junit.jupiter.api.Assertions.assertNotNull(nameInput);
            org.junit.jupiter.api.Assertions.assertNotNull(emailInput);
            org.junit.jupiter.api.Assertions.assertNotNull(titleInput);
            org.junit.jupiter.api.Assertions.assertNotNull(descriptionInput);
            org.junit.jupiter.api.Assertions.assertNotNull(identityRow);
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Weiter zum 2. Schritt"));
        }
    }

    @Test
    @DisplayName("contains runtime backend config wiring for form post and HTMX email validation")
    void containsRuntimeBackendConfigWiring() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");

            org.junit.jupiter.api.Assertions.assertFalse(
                    page.getByXPath("//script[@src='/runtime-config.js']").isEmpty(),
                    "Expected runtime config script include for backend base URL"
            );
            org.junit.jupiter.api.Assertions.assertNotNull(
                    page.getElementById("step1-form"),
                    "Expected form id for runtime backend action wiring"
            );
        }
    }

    @Test
    @DisplayName("uses local HTMX script and does not include external font CDNs")
    void usesLocalHtmxScriptAndNoExternalFontCdns() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");
            String content = page.getWebResponse().getContentAsString();

            org.junit.jupiter.api.Assertions.assertTrue(
                    content.contains("<script src=\"/js/vendor/htmx.min.js\"></script>"),
                    "Expected local HTMX script include"
            );
            org.junit.jupiter.api.Assertions.assertFalse(
                    content.contains("https://unpkg.com/htmx.org"),
                    "Did not expect external HTMX CDN include"
            );
            org.junit.jupiter.api.Assertions.assertFalse(
                    content.contains("https://fonts.googleapis.com"),
                    "Did not expect Google Fonts include"
            );
            org.junit.jupiter.api.Assertions.assertFalse(
                    content.contains("https://fonts.gstatic.com"),
                    "Did not expect Google Fonts static include"
            );
        }
    }

    @Test
    @DisplayName("contains frontend runtime script")
    void containsFrontendRuntimeScript() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");

            var runtimeScript = page.getElementById("step1-runtime-script");
            org.junit.jupiter.api.Assertions.assertNotNull(
                    runtimeScript,
                    "Expected runtime script on static step-1 page"
            );
        }
    }

    @Test
    @DisplayName("does not prewarm step-2 on page load")
    void doesNotPrewarmStep2OnPageLoad() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");
            String content = page.getWebResponse().getContentAsString();

            org.junit.jupiter.api.Assertions.assertFalse(
                    content.contains("fetch(prewarmTarget"),
                    "Did not expect step-2 prewarm fetch on page load"
            );
            org.junit.jupiter.api.Assertions.assertFalse(
                    content.contains("const prewarmTarget"),
                    "Did not expect prewarm target declaration in static script"
            );
        }
    }

    @Test
    @DisplayName("contains active poll count footer with HTMX load trigger and spinner fallback")
    void containsActivePollCountFooterWithHtmxLoadTriggerAndSpinnerFallback() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");

            org.junit.jupiter.api.Assertions.assertNotNull(
                    page.getElementById("active-poll-count"),
                    "Expected active poll count target in footer"
            );

            org.junit.jupiter.api.Assertions.assertTrue(
                    page.asXml().contains("hx-get=\"/poll/active-count\""),
                    "Expected HTMX endpoint for active poll count"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.asXml().contains("hx-trigger=\"load\""),
                    "Expected HTMX load trigger for immediate count request"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.getWebResponse().getContentAsString().contains("poll-count-spinner"),
                    "Expected spinner fallback while count is loading"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.getElementById("step1-runtime-script").getTextContent()
                            .contains("setAttribute(\"hx-get\", base + \"/poll/active-count\")"),
                    "Expected runtime backend base wiring for active poll count endpoint"
            );
        }
    }
}
