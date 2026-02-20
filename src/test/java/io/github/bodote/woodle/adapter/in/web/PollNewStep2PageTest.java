package io.github.bodote.woodle.adapter.in.web;

import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.HttpMethod;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;

import java.net.URL;
import java.util.List;

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
            HtmlInput startTime1 = intraday.getFirstByXPath("//input[@name='startTime1_1']");
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
                            + "&startTime1_1=09:15"
                            + "&startTime2_1=13:45"
            );

            HtmlInput dateOption1 = addOnce.getFirstByXPath("//input[@name='dateOption1']");
            HtmlInput dateOption2 = addOnce.getFirstByXPath("//input[@name='dateOption2']");
            HtmlInput startTime1 = addOnce.getFirstByXPath("//input[@name='startTime1_1']");
            HtmlInput startTime2 = addOnce.getFirstByXPath("//input[@name='startTime2_1']");
            HtmlInput dateOption3 = addOnce.getFirstByXPath("//input[@name='dateOption3']");
            HtmlInput startTime3 = addOnce.getFirstByXPath("//input[@name='startTime3_1']");

            assertEquals("2026-03-10", dateOption1.getValueAttribute());
            assertEquals("2026-03-11", dateOption2.getValueAttribute());
            assertEquals("09:15", startTime1.getValueAttribute());
            assertEquals("13:45", startTime2.getValueAttribute());
            assertEquals("", dateOption3.getValueAttribute());
            assertEquals("", startTime3.getValueAttribute());
        }
    }

    @Test
    @DisplayName("intraday shows day and time controls with numbered day labels")
    void intradayShowsDayAndTimeControlsWithNumberedDayLabels() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            HtmlPage intraday = webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");
            HtmlPage dateOptions = webClient.getPage(BASE_URL + "/poll/step-2/options/add");

            assertNotNull(dateOptions.getFirstByXPath("//input[@name='dateOption1']"));
            assertNotNull(dateOptions.getFirstByXPath("//input[@name='dateOption2']"));
            assertTrue(!dateOptions.asNormalizedText().contains("Tag i"));
            assertTrue(!dateOptions.asNormalizedText().contains("Tag dayIndex"));
            assertTrue(dateOptions.asNormalizedText().contains("Tag 1"));
            assertTrue(dateOptions.asNormalizedText().contains("Tag 2"));
            assertTrue(!dateOptions.asNormalizedText().contains("Uhrzeit timeIndex"));
            assertTrue(dateOptions.asNormalizedText().contains("Uhrzeit 1"));
            assertTrue(dateOptions.asNormalizedText().contains("Uhrzeit hinzufügen"));
            assertTrue(dateOptions.asNormalizedText().contains("Uhrzeit entfernen"));
            assertTrue(intraday.asNormalizedText().contains("Tag hinzufügen"));
            assertTrue(intraday.asNormalizedText().contains("Tag löschen"));
        }
    }

    @Test
    @DisplayName("adds and removes time rows for a specific intraday day")
    void addsAndRemovesTimeRowsForSpecificIntradayDay() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");

            HtmlPage added = webClient.getPage(
                    BASE_URL + "/poll/step-2/options/time/add?day=1"
                            + "&dateOption1=2026-04-10"
                            + "&dateOption2=2026-04-11"
                            + "&startTime1_1=09:00"
                            + "&startTime2_1=14:00"
            );

            assertEquals(2, added.getByXPath("//input[starts-with(@name,'startTime1_')]").size());
            HtmlInput addedSecondTime = added.getFirstByXPath("//input[@name='startTime1_2']");
            assertNotNull(addedSecondTime);

            HtmlPage removed = webClient.getPage(
                    BASE_URL + "/poll/step-2/options/time/remove?day=1"
                            + "&dateOption1=2026-04-10"
                            + "&dateOption2=2026-04-11"
                            + "&startTime1_1=09:00"
                            + "&startTime1_2=10:00"
                            + "&startTime2_1=14:00"
            );

            assertEquals(1, removed.getByXPath("//input[starts-with(@name,'startTime1_')]").size());
        }
    }

    @Test
    @DisplayName("intraday day delete button is disabled when only one day remains")
    void intradayDayDeleteButtonIsDisabledWhenOnlyOneDayRemains() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");
            webClient.getPage(BASE_URL + "/poll/step-2/options/remove");

            HtmlPage intraday = webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");
            assertEquals(1, intraday.getByXPath("//input[starts-with(@name,'dateOption')]").size());

            HtmlButton deleteDayButton = intraday.getFirstByXPath("//button[normalize-space()='Tag löschen']");
            assertNotNull(deleteDayButton);
            assertNotNull(deleteDayButton.getAttributeNode("disabled"));
        }
    }

    @Test
    @DisplayName("intraday shows copy-times button only for first day")
    void intradayShowsCopyTimesButtonOnlyForFirstDay() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");
            HtmlPage intraday = webClient.getPage(BASE_URL + "/poll/step-2/options/add");

            HtmlButton copyTimesButton = intraday.getFirstByXPath("//button[normalize-space()='Kopiere Uhrzeiten']");
            assertNotNull(copyTimesButton);
            assertEquals(1, intraday.getByXPath("//button[normalize-space()='Kopiere Uhrzeiten']").size());
        }
    }

    @Test
    @DisplayName("intraday copies first day times to all following days")
    void intradayCopiesFirstDayTimesToAllFollowingDays() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");
            webClient.getPage(BASE_URL + "/poll/step-2/options/add");
            webClient.getPage(BASE_URL + "/poll/step-2/options/time/add?day=1");

            HtmlPage copied = webClient.getPage(
                    BASE_URL + "/poll/step-2/options/time/copy"
                            + "?dateOption1=2026-04-10"
                            + "&dateOption2=2026-04-11"
                            + "&dateOption3=2026-04-12"
                            + "&startTime1_1=09:00"
                            + "&startTime1_2=11:30"
                            + "&startTime2_1=14:00"
            );

            HtmlInput day2Time1 = copied.getFirstByXPath("//input[@name='startTime2_1']");
            HtmlInput day2Time2 = copied.getFirstByXPath("//input[@name='startTime2_2']");
            HtmlInput day3Time1 = copied.getFirstByXPath("//input[@name='startTime3_1']");
            HtmlInput day3Time2 = copied.getFirstByXPath("//input[@name='startTime3_2']");
            assertNotNull(day2Time1);
            assertNotNull(day2Time2);
            assertNotNull(day3Time1);
            assertNotNull(day3Time2);
            assertEquals("09:00", day2Time1.getValueAttribute());
            assertEquals("11:30", day2Time2.getValueAttribute());
            assertEquals("09:00", day3Time1.getValueAttribute());
            assertEquals("11:30", day3Time2.getValueAttribute());
        }
    }

    @Test
    @DisplayName("reopening step-2 preserves existing intraday dates and times per day")
    void reopeningStep2PreservesExistingIntradayDatesAndTimesPerDay() throws Exception {
        try (WebClient webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()) {
            webClient.getPage(BASE_URL + "/poll/step-2/event-type?eventType=INTRADAY");
            webClient.getPage(BASE_URL + "/poll/step-2/options/time/add?day=1");

            WebRequest submitStep2 = new WebRequest(new URL(BASE_URL + "/poll/step-3"), HttpMethod.POST);
            submitStep2.setRequestParameters(List.of(
                    new NameValuePair("eventType", "INTRADAY"),
                    new NameValuePair("dateOption1", "2026-02-08"),
                    new NameValuePair("dateOption2", "2026-02-14"),
                    new NameValuePair("startTime1_1", "10:50"),
                    new NameValuePair("startTime1_2", "13:50"),
                    new NameValuePair("startTime2_1", "10:50")
            ));
            webClient.getPage(submitStep2);

            HtmlPage reopenedStep2 = webClient.getPage(BASE_URL + "/poll/step-2");
            HtmlInput dateOption1 = reopenedStep2.getFirstByXPath("//input[@name='dateOption1']");
            HtmlInput dateOption2 = reopenedStep2.getFirstByXPath("//input[@name='dateOption2']");
            HtmlInput startTime1_1 = reopenedStep2.getFirstByXPath("//input[@name='startTime1_1']");
            HtmlInput startTime1_2 = reopenedStep2.getFirstByXPath("//input[@name='startTime1_2']");
            HtmlInput startTime2_1 = reopenedStep2.getFirstByXPath("//input[@name='startTime2_1']");

            assertNotNull(dateOption1);
            assertNotNull(dateOption2);
            assertNotNull(startTime1_1);
            assertNotNull(startTime1_2);
            assertNotNull(startTime2_1);
            assertEquals("2026-02-08", dateOption1.getValueAttribute());
            assertEquals("2026-02-14", dateOption2.getValueAttribute());
            assertEquals("10:50", startTime1_1.getValueAttribute());
            assertEquals("13:50", startTime1_2.getValueAttribute());
            assertEquals("10:50", startTime2_1.getValueAttribute());
        }
    }
}
