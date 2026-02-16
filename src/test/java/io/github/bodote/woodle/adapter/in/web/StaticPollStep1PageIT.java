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
    @DisplayName("provides browser autofill hints for identity and poll fields")
    void providesBrowserAutofillHintsForIdentityAndPollFields() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");

            HtmlInput nameInput = page.getFirstByXPath("//input[@name='authorName']");
            HtmlInput emailInput = page.getFirstByXPath("//input[@name='authorEmail']");
            HtmlInput titleInput = page.getFirstByXPath("//input[@name='pollTitle']");
            HtmlTextArea descriptionInput = page.getFirstByXPath("//textarea[@name='description']");

            org.junit.jupiter.api.Assertions.assertEquals("name", nameInput.getAttribute("autocomplete"));
            org.junit.jupiter.api.Assertions.assertEquals("email", emailInput.getAttribute("autocomplete"));
            org.junit.jupiter.api.Assertions.assertEquals("email", emailInput.getAttribute("type"));
            org.junit.jupiter.api.Assertions.assertEquals("on", titleInput.getAttribute("autocomplete"));
            org.junit.jupiter.api.Assertions.assertEquals("poll-title-history", titleInput.getAttribute("list"));
            org.junit.jupiter.api.Assertions.assertEquals("on", descriptionInput.getAttribute("autocomplete"));
            org.junit.jupiter.api.Assertions.assertNotNull(
                    page.getFirstByXPath("//datalist[@id='poll-title-history']")
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.getWebResponse().getContentAsString().contains("woodle.poll.step1.titleHistory")
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.getWebResponse().getContentAsString().contains("woodle.poll.step1.authorName")
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.getWebResponse().getContentAsString().contains("woodle.poll.step1.authorEmail")
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.getWebResponse().getContentAsString().contains("woodle.poll.step1.pollTitle")
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.getWebResponse().getContentAsString().contains("woodle.poll.step1.description")
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.getWebResponse().getContentAsString().contains("woodle.poll.step1.notifyOnVote")
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.getWebResponse().getContentAsString().contains("woodle.poll.step1.notifyOnComment")
            );
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
    @DisplayName("contains HTMX transient error retry handler with ten retries for step-1 submit")
    void containsHtmxTransientErrorRetryHandlerWithTenRetriesForStep1Submit() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");
            String script = page.getElementById("step1-runtime-script").getTextContent();

            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("htmx:responseError"),
                    "Expected HTMX response error handler in runtime script"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("status === 502 || status === 503 || status === 504"),
                    "Expected transient 5xx status guard for retries"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("const maxTransientRetries = 10"),
                    "Expected retry limit of ten transient retries"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("const transientRetryDelayMs = 1000"),
                    "Expected step-2 retry delay to be 1000ms"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("requestSubmit"),
                    "Expected retry to re-submit the step-1 form"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("setTimeout(function ()"),
                    "Expected delayed loading indicator handling"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("}, 200)"),
                    "Expected loading hint delay threshold of 200ms"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("step1-loading-indicator--visible"),
                    "Expected explicit visible state class for delayed loading hint"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    page.asXml().contains("step1-loading-spinner"),
                    "Expected spinner element for step-2 loading hint"
            );
        }
    }

    @Test
    @DisplayName("submits step-1 via HTMX post to step-2")
    void submitsStep1ViaHtmxPostToStep2() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");
            org.htmlunit.html.HtmlForm form = page.getHtmlElementById("step1-form");

            org.junit.jupiter.api.Assertions.assertEquals(
                    "/poll/step-2",
                    form.getAttribute("hx-post"),
                    "Expected HTMX post target for step-2 transition"
            );
            org.junit.jupiter.api.Assertions.assertEquals(
                    "body",
                    form.getAttribute("hx-target"),
                    "Expected HTMX to swap the main document body"
            );
            org.junit.jupiter.api.Assertions.assertEquals(
                    "innerHTML",
                    form.getAttribute("hx-swap"),
                    "Expected HTMX body swap mode for step transitions"
            );
            org.junit.jupiter.api.Assertions.assertEquals(
                    "true",
                    form.getAttribute("hx-push-url"),
                    "Expected browser URL updates on HTMX navigation"
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

    @Test
    @DisplayName("contains active poll count transient retry handler with ten retries and 1000ms interval")
    void containsActivePollCountTransientRetryHandlerWithTenRetriesAnd1000msInterval() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");
            String script = page.getElementById("step1-runtime-script").getTextContent();

            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("maxActivePollCountRetries = 10"),
                    "Expected active poll count retry limit to be ten"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("activePollCountRetryDelayMs = 1000"),
                    "Expected active poll count retry delay to be 1000ms"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("event.target !== activePollCount"),
                    "Expected retry handler to scope to the active poll count request"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    script.contains("htmx.ajax(\"GET\", activeCountEndpoint"),
                    "Expected retry to re-issue active poll count GET request"
            );
        }
    }
}
