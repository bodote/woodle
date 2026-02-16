package io.github.bodote.woodle.adapter.in.web;

import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WebMvcTest(io.github.bodote.woodle.adapter.in.web.PollNewPageController.class)
@DisplayName("/poll/new step-2")
class PollNewStep2PageTest {

    private static final String BASE_URL = "http://localhost";

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private io.github.bodote.woodle.application.port.out.WizardStateRepository wizardStateRepository;

    @Test
    @DisplayName("renders step-2 date selection page")
    void rendersStep2DateSelectionPage() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage page = webClient.getPage(BASE_URL + "/poll/step-2");

            HtmlInput allDayOption = page.getFirstByXPath("//input[@name='eventType' and @value='ALL_DAY']");
            HtmlInput intradayOption = page.getFirstByXPath("//input[@name='eventType' and @value='INTRADAY']");
            HtmlInput dateOption1 = page.getFirstByXPath("//input[@name='dateOption1']");
            HtmlInput dateOption2 = page.getFirstByXPath("//input[@name='dateOption2']");
            HtmlInput durationMinutes = page.getFirstByXPath("//input[@name='durationMinutes']");
            HtmlInput startTime1 = page.getFirstByXPath("//input[@name='startTime1']");

            assertNotNull(allDayOption);
            assertNotNull(intradayOption);
            assertNotNull(dateOption1);
            assertNotNull(dateOption2);
            org.junit.jupiter.api.Assertions.assertNull(durationMinutes);
            org.junit.jupiter.api.Assertions.assertNull(startTime1);
            assertTrue(page.asNormalizedText().contains("Umfragedaten"));
            assertTrue(page.asNormalizedText().contains("Weiter"));
        }
    }

    @Test
    @DisplayName("updates date options statefully via HTMX")
    void updatesDateOptionsStatefullyViaHtmx() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage addOnce = webClient.getPage(BASE_URL + "/poll/step-2/options/add");
            assertNotNull(addOnce.getFirstByXPath("//div[@id='date-options']"));
            assertEquals(3, addOnce.getByXPath("//input[starts-with(@name,'dateOption')]").size());

            HtmlPage addTwice = webClient.getPage(BASE_URL + "/poll/step-2/options/add");
            assertEquals(4, addTwice.getByXPath("//input[starts-with(@name,'dateOption')]").size());

            HtmlPage removeOnce = webClient.getPage(BASE_URL + "/poll/step-2/options/remove");
            assertEquals(3, removeOnce.getByXPath("//input[starts-with(@name,'dateOption')]").size());
        }
    }

    @Test
    @DisplayName("switches to intraday options via HTMX")
    void switchesToIntradayOptionsViaHtmx() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage intraday = webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");
            HtmlInput durationMinutes = intraday.getFirstByXPath("//input[@name='durationMinutes']");
            HtmlInput startTime1 = intraday.getFirstByXPath("//input[@name='startTime1']");
            assertNotNull(durationMinutes);
            assertNotNull(startTime1);
            assertEquals(2, intraday.getByXPath("//input[starts-with(@name,'dateOption')]").size());

            HtmlPage allDay = webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=ALL_DAY");
            HtmlInput durationMissing = allDay.getFirstByXPath("//input[@name='durationMinutes']");
            HtmlInput timeMissing = allDay.getFirstByXPath("//input[@name='startTime1']");
            org.junit.jupiter.api.Assertions.assertNull(durationMissing);
            org.junit.jupiter.api.Assertions.assertNull(timeMissing);
        }
    }

    @Test
    @DisplayName("adds intraday options statefully via HTMX")
    void addsIntradayOptionsStatefullyViaHtmx() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");

            HtmlPage addOnce = webClient.getPage(BASE_URL + "/poll/step-2/options/add");
            assertEquals(3, addOnce.getByXPath("//input[starts-with(@name,'dateOption')]").size());
            assertEquals(3, addOnce.getByXPath("//input[starts-with(@name,'startTime')]").size());
        }
    }

    @Test
    @DisplayName("keeps entered date and time values when adding a third intraday option")
    void keepsEnteredDateAndTimeValuesWhenAddingThirdIntradayOption() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");

            HtmlPage addOnce = webClient.getPage(
                    BASE_URL + "/poll/step-2/options/add?"
                            + "&dateOption1=2026-03-10"
                            + "&dateOption2=2026-03-11"
                            + "&startTime1=09:15"
                            + "&startTime2=13:45"
            );

            HtmlInput dateOption1 = addOnce.getFirstByXPath("//input[@name='dateOption1']");
            HtmlInput dateOption2 = addOnce.getFirstByXPath("//input[@name='dateOption2']");
            HtmlInput startTime1 = addOnce.getFirstByXPath("//input[@name='startTime1']");
            HtmlInput startTime2 = addOnce.getFirstByXPath("//input[@name='startTime2']");
            HtmlInput dateOption3 = addOnce.getFirstByXPath("//input[@name='dateOption3']");
            HtmlInput startTime3 = addOnce.getFirstByXPath("//input[@name='startTime3']");

            assertEquals("2026-03-10", dateOption1.getValueAttribute());
            assertEquals("2026-03-11", dateOption2.getValueAttribute());
            assertEquals("09:15", startTime1.getValueAttribute());
            assertEquals("13:45", startTime2.getValueAttribute());
            assertEquals("", dateOption3.getValueAttribute());
            assertEquals("", startTime3.getValueAttribute());
        }
    }
}
