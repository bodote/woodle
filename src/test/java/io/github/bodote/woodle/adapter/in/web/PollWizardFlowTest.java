package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.adapter.in.web.WizardState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(io.github.bodote.woodle.adapter.in.web.PollNewPageController.class)
@DisplayName("poll wizard flow")
class PollWizardFlowTest {

    private static final String VALID_EMAIL = "max@example.com";
    private static final String DATE_OPTION_1 = "2026-02-10";
    private static final String DATE_OPTION_2 = "2026-02-11";
    private static final String START_TIME_1 = "09:00";
    private static final String START_TIME_2 = "13:30";

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("step-1 submits and stores basics in session")
    void step1StoresBasicsInSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

                mockMvc.perform(post("/poll/step-2")
                        .session(session)
                        .param("authorName", TestFixtures.AUTHOR_NAME)
                        .param("authorEmail", VALID_EMAIL)
                        .param("pollTitle", "Test")
                        .param("description", TestFixtures.DESCRIPTION))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Umfragedaten (2 von 3)")));

        WizardState state = (WizardState) session.getAttribute(WizardState.SESSION_KEY);
        assertNotNull(state);
        assertEquals(TestFixtures.AUTHOR_NAME, state.authorName());
        assertEquals(VALID_EMAIL, state.authorEmail());
        assertEquals("Test", state.title());
        assertEquals(TestFixtures.DESCRIPTION, state.description());
    }

    @Test
    @DisplayName("step-2 submit renders step-3 directly")
    void step2SubmitRendersStep3Directly() throws Exception {
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateBasics();
        state.setAuthorEmail(VALID_EMAIL);
        state.setTitle("Test");
        session.setAttribute(WizardState.SESSION_KEY, state);

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-02-10")
                        .param("dateOption2", "2026-02-11"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung (3 von 3)")));
    }

    @Test
    @DisplayName("step-2 submits and stores dates in session")
    void step2StoresDatesInSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateBasics();
        state.setAuthorEmail(VALID_EMAIL);
        state.setTitle("Test");
        session.setAttribute(WizardState.SESSION_KEY, state);

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-02-10")
                        .param("dateOption2", "2026-02-11"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung (3 von 3)")));

        WizardState updated = (WizardState) session.getAttribute(WizardState.SESSION_KEY);
        assertNotNull(updated);
        assertEquals(2, updated.dates().size());
    }

    @Test
    @DisplayName("step-2 intraday submit stores start times in session")
    void step2IntradaySubmitStoresStartTimesInSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateBasics();
        state.setAuthorEmail(VALID_EMAIL);
        state.setTitle("Test");
        session.setAttribute(WizardState.SESSION_KEY, state);

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("eventType", "INTRADAY")
                        .param("durationMinutes", "90")
                        .param("dateOption1", DATE_OPTION_1)
                        .param("dateOption2", DATE_OPTION_2)
                        .param("startTime1", START_TIME_1)
                        .param("startTime2", START_TIME_2))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung (3 von 3)")));

        WizardState updated = (WizardState) session.getAttribute(WizardState.SESSION_KEY);
        assertNotNull(updated);
        assertEquals(2, updated.startTimes().size());
    }

    @Test
    @DisplayName("step-2 all-day submit ignores blank date options")
    void step2AllDaySubmitIgnoresBlankDateOptions() throws Exception {
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateBasics();
        state.setAuthorEmail(VALID_EMAIL);
        state.setTitle("Test");
        session.setAttribute(WizardState.SESSION_KEY, state);

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", DATE_OPTION_1)
                        .param("dateOption2", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung (3 von 3)")));

        WizardState updated = (WizardState) session.getAttribute(WizardState.SESSION_KEY);
        assertNotNull(updated);
        assertEquals(1, updated.dates().size());
    }

    @Test
    @DisplayName("step-2 intraday summary falls back to date when a start time is missing")
    void step2IntradaySummaryFallsBackToDateWhenStartTimeMissing() throws Exception {
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateBasics();
        state.setAuthorEmail(VALID_EMAIL);
        state.setTitle("Test");
        session.setAttribute(WizardState.SESSION_KEY, state);

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("eventType", "INTRADAY")
                        .param("durationMinutes", "90")
                        .param("dateOption1", DATE_OPTION_1)
                        .param("dateOption2", DATE_OPTION_2)
                        .param("startTime1", START_TIME_1)
                        .param("startTime2", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(DATE_OPTION_1 + " " + START_TIME_1)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(DATE_OPTION_2)));

        WizardState updated = (WizardState) session.getAttribute(WizardState.SESSION_KEY);
        assertNotNull(updated);
        assertEquals(1, updated.startTimes().size());
    }

    @Test
    @DisplayName("step-2 ignores date options with empty parameter arrays")
    void step2IgnoresDateOptionsWithEmptyParameterArrays() throws Exception {
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateBasics();
        state.setAuthorEmail(VALID_EMAIL);
        state.setTitle("Test");
        session.setAttribute(WizardState.SESSION_KEY, state);

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", DATE_OPTION_1)
                        .with(request -> {
                            request.addParameter("dateOption2", new String[]{});
                            return request;
                        }))
                .andExpect(status().isOk());

        WizardState updated = (WizardState) session.getAttribute(WizardState.SESSION_KEY);
        assertNotNull(updated);
        assertEquals(1, updated.dates().size());
    }

    @Test
    @DisplayName("step-2 intraday ignores start times with empty parameter arrays")
    void step2IntradayIgnoresStartTimesWithEmptyParameterArrays() throws Exception {
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateBasics();
        state.setAuthorEmail(VALID_EMAIL);
        state.setTitle("Test");
        session.setAttribute(WizardState.SESSION_KEY, state);

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("eventType", "INTRADAY")
                        .param("durationMinutes", "90")
                        .param("dateOption1", DATE_OPTION_1)
                        .param("dateOption2", DATE_OPTION_2)
                        .param("startTime1", START_TIME_1)
                        .with(request -> {
                            request.addParameter("startTime2", new String[]{});
                            return request;
                        }))
                .andExpect(status().isOk());

        WizardState updated = (WizardState) session.getAttribute(WizardState.SESSION_KEY);
        assertNotNull(updated);
        assertEquals(1, updated.startTimes().size());
    }
}
