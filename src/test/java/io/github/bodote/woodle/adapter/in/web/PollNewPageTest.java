package io.github.bodote.woodle.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;

import org.htmlunit.HttpMethod;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;

import java.net.URL;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(io.github.bodote.woodle.adapter.in.web.PollNewPageController.class)
@DisplayName("/poll/new step-1")
class PollNewPageTest {

    private static final String BASE_URL = "http://localhost";
    private static final String INVALID_EMAIL = "invalid-email";

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("redirects /poll/new to static step-1 page")
    void redirectsPollNewToStaticStep1Page() throws Exception {
        mockMvc.perform(get("/poll/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new-step1.html"));
    }

    @Test
    @DisplayName("rejects invalid email address on submit")
    void rejectsInvalidEmailAddressOnSubmit() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            WebRequest request = new WebRequest(new URL(BASE_URL + "/poll/step-2"), HttpMethod.POST);
            request.setRequestParameters(List.of(
                    new NameValuePair("authorName", "Max"),
                    new NameValuePair("authorEmail", INVALID_EMAIL),
                    new NameValuePair("pollTitle", "Test Poll"),
                    new NameValuePair("description", "Desc")
            ));

            HtmlPage page = webClient.getPage(request);

            HtmlInput emailInput = page.getFirstByXPath("//input[@name='authorEmail' and contains(@class,'input-error')]");
            org.junit.jupiter.api.Assertions.assertNotNull(emailInput);
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Bitte eine gültige E-Mail-Adresse eingeben"));
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Umfrage erstellen"));
        }
    }

    @Test
    @DisplayName("validates email format on blur via HTMX")
    void validatesEmailFormatOnBlurViaHtmx() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/step-1/email-check?authorEmail=" + INVALID_EMAIL);

            HtmlInput emailInput = page.getFirstByXPath("//input[@name='authorEmail' and contains(@class,'input-error')]");
            org.junit.jupiter.api.Assertions.assertNotNull(emailInput);
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Bitte eine gültige E-Mail-Adresse eingeben"));
        }
    }

    @Test
    @DisplayName("keeps email field valid on blur for a valid address")
    void keepsEmailFieldValidOnBlurForValidAddress() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/step-1/email-check?authorEmail=max@example.com");

            HtmlInput emailInput = page.getFirstByXPath("//input[@name='authorEmail' and not(contains(@class,'input-error'))]");
            org.junit.jupiter.api.Assertions.assertNotNull(emailInput);
            org.junit.jupiter.api.Assertions.assertFalse(page.asNormalizedText().contains("Bitte eine gültige E-Mail-Adresse eingeben"));
        }
    }

    @Test
    @DisplayName("treats blank email on blur as invalid")
    void treatsBlankEmailOnBlurAsInvalid() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/step-1/email-check?authorEmail=");

            HtmlInput emailInput = page.getFirstByXPath("//input[@name='authorEmail' and contains(@class,'input-error')]");
            org.junit.jupiter.api.Assertions.assertNotNull(emailInput);
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Bitte eine gültige E-Mail-Adresse eingeben"));
        }
    }
}
