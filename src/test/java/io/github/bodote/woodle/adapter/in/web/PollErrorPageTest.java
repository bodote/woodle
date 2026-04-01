package io.github.bodote.woodle.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;

import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Fehlerseite")
class PollErrorPageTest {

    private static final String TEST_URL = "http://localhost/poll/error?test-http-status=500";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("zeigt deutsche Fehlerseite mit HTTP-Status, Zeitstempel und Emoji")
    void showsGermanErrorPageWithStatusTimestampAndEmoji() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            HtmlPage page = webClient.getPage(TEST_URL);

            org.junit.jupiter.api.Assertions.assertEquals(500, page.getWebResponse().getStatusCode());
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Entschuldigung"));
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("🙇"));

            HtmlElement statusElement = page.getFirstByXPath("//h2[@id='error-status']");
            org.junit.jupiter.api.Assertions.assertNotNull(statusElement);
            org.junit.jupiter.api.Assertions.assertTrue(statusElement.asNormalizedText().contains("500"));

            HtmlElement timestampElement = page.getFirstByXPath("//dd[@id='error-timestamp']");
            org.junit.jupiter.api.Assertions.assertNotNull(timestampElement);
            org.junit.jupiter.api.Assertions.assertFalse(timestampElement.asNormalizedText().isBlank());
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Zeitpunkt"));
        }
    }

    @Test
    @DisplayName("returns bad request for invalid test-http-status parameter")
    void returnsBadRequestForInvalidTestHttpStatusParameter() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            org.htmlunit.Page page = webClient.getPage("http://localhost/poll/error?test-http-status=999");

            org.junit.jupiter.api.Assertions.assertEquals(400, page.getWebResponse().getStatusCode());
        }
    }
}
