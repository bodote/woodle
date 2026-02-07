package io.github.bodote.woodle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;

import org.htmlunit.HttpMethod;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlTextArea;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;

import java.net.URL;
import java.util.List;

@WebMvcTest(io.github.bodote.woodle.adapter.in.web.PollNewPageController.class)
@DisplayName("/poll/new step-1")
class PollNewPageTest {

    private static final String BASE_URL = "http://localhost";
    private static final String INVALID_EMAIL = "invalid-email";

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("renders step-1 create poll page")
    void rendersStep1CreatePollPage() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/new");

            HtmlInput nameInput = page.getFirstByXPath("//input[@name='authorName']");
            HtmlInput emailInput = page.getFirstByXPath("//input[@name='authorEmail']");
            HtmlInput titleInput = page.getFirstByXPath("//input[@name='pollTitle']");
            HtmlTextArea descriptionInput = page.getFirstByXPath("//textarea[@name='description']");

            HtmlInput notifyOnVote = page.getFirstByXPath("//input[@name='notifyOnVote']");
            HtmlInput notifyOnComment = page.getFirstByXPath("//input[@name='notifyOnComment']");
            HtmlInput maxVotesEnabled = page.getFirstByXPath("//input[@name='maxVotesEnabled']");
            HtmlInput maxVotesPerOption = page.getFirstByXPath("//input[@name='maxVotesPerOption']");
            HtmlInput customLinkEnabled = page.getFirstByXPath("//input[@name='customLinkEnabled']");
            HtmlInput customSlug = page.getFirstByXPath("//input[@name='customSlug']");
            HtmlInput passwordEnabled = page.getFirstByXPath("//input[@name='passwordEnabled']");
            HtmlInput password = page.getFirstByXPath("//input[@name='password']");
            HtmlInput passwordConfirm = page.getFirstByXPath("//input[@name='passwordConfirm']");
            HtmlInput resultsPublic = page.getFirstByXPath("//input[@name='resultsPublic']");
            HtmlInput votePolicyAll = page.getFirstByXPath("//input[@name='voteChangePolicy' and @value='ALL_CAN_EDIT']");
            HtmlInput votePolicyOwn = page.getFirstByXPath("//input[@name='voteChangePolicy' and @value='ONLY_OWN_CAN_EDIT']");
            HtmlInput votePolicyNone = page.getFirstByXPath("//input[@name='voteChangePolicy' and @value='NONE_CAN_EDIT']");
            HtmlInput onlyAuthor = page.getFirstByXPath("//input[@name='onlyAuthor']");

            org.junit.jupiter.api.Assertions.assertNotNull(nameInput);
            org.junit.jupiter.api.Assertions.assertNotNull(emailInput);
            org.junit.jupiter.api.Assertions.assertNotNull(titleInput);
            org.junit.jupiter.api.Assertions.assertNotNull(descriptionInput);
            org.junit.jupiter.api.Assertions.assertNotNull(notifyOnVote);
            org.junit.jupiter.api.Assertions.assertNotNull(notifyOnComment);
            org.junit.jupiter.api.Assertions.assertNull(maxVotesEnabled);
            org.junit.jupiter.api.Assertions.assertNull(maxVotesPerOption);
            org.junit.jupiter.api.Assertions.assertNull(customLinkEnabled);
            org.junit.jupiter.api.Assertions.assertNull(customSlug);
            org.junit.jupiter.api.Assertions.assertNull(passwordEnabled);
            org.junit.jupiter.api.Assertions.assertNull(password);
            org.junit.jupiter.api.Assertions.assertNull(passwordConfirm);
            org.junit.jupiter.api.Assertions.assertNull(resultsPublic);
            org.junit.jupiter.api.Assertions.assertNull(votePolicyAll);
            org.junit.jupiter.api.Assertions.assertNull(votePolicyOwn);
            org.junit.jupiter.api.Assertions.assertNull(votePolicyNone);
            org.junit.jupiter.api.Assertions.assertNull(onlyAuthor);
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Umfrage erstellen"));
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Weiter zum 2. Schritt"));
        }
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
            org.junit.jupiter.api.Assertions.assertTrue(page.asNormalizedText().contains("Umfrage erstellen (Schritt 1 von 3)"));
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
}
