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
    @DisplayName("contains frontend prewarm script for step-2 backend route")
    void containsFrontendPrewarmScriptForStep2BackendRoute() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new-step1.html");

            var prewarmScript = page.getElementById("step1-prewarm-script");
            org.junit.jupiter.api.Assertions.assertNotNull(
                    prewarmScript,
                    "Expected prewarm script on static step-1 page"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                    prewarmScript.getTextContent().contains("/poll/step-2"),
                    "Expected prewarm script to target step-2 backend route"
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
                    page.getElementById("step1-prewarm-script").getTextContent()
                            .contains("setAttribute(\"hx-get\", base + \"/poll/active-count\")"),
                    "Expected runtime backend base wiring for active poll count endpoint"
            );
        }
    }
}
