package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.adapter.in.web.WizardState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @MockitoBean
    private io.github.bodote.woodle.application.port.out.WizardStateRepository wizardStateRepository;

    @Test
    @DisplayName("step-1 submit includes draft id for persisted wizard state")
    void step1SubmitIncludesDraftIdForPersistedWizardState() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(wizardStateRepository.create(org.mockito.ArgumentMatchers.any(WizardState.class)))
                .thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-000000000700"));

        mockMvc.perform(post("/poll/step-2")
                        .session(session)
                        .param("authorName", TestFixtures.AUTHOR_NAME)
                        .param("authorEmail", VALID_EMAIL)
                        .param("pollTitle", "Test")
                        .param("description", TestFixtures.DESCRIPTION))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"draftId\"")));
    }

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Umfragedaten")));

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung")));
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung")));

        WizardState updated = (WizardState) session.getAttribute(WizardState.SESSION_KEY);
        assertNotNull(updated);
        assertEquals(2, updated.dates().size());
    }

    @Test
    @DisplayName("step-2 submit keeps step-1 basics from posted hidden fields")
    void step2SubmitKeepsStep1BasicsFromPostedFields() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("authorName", TestFixtures.AUTHOR_NAME)
                        .param("authorEmail", VALID_EMAIL)
                        .param("pollTitle", "Test")
                        .param("description", TestFixtures.DESCRIPTION)
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", DATE_OPTION_1)
                        .param("dateOption2", DATE_OPTION_2))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"authorName\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"" + TestFixtures.AUTHOR_NAME + "\"")));

        WizardState updated = (WizardState) session.getAttribute(WizardState.SESSION_KEY);
        assertNotNull(updated);
        assertEquals(TestFixtures.AUTHOR_NAME, updated.authorName());
        assertEquals(VALID_EMAIL, updated.authorEmail());
        assertEquals("Test", updated.title());
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung")));

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung")));

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

    @Test
    @DisplayName("step-2 submit continues when draft lookup fails and posted basics exist")
    void step2SubmitContinuesWhenDraftLookupFailsAndPostedBasicsExist() throws Exception {
        MockHttpSession session = new MockHttpSession();
        java.util.UUID draftId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(wizardStateRepository.findById(draftId)).thenThrow(new IllegalStateException("s3 unavailable"));

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("draftId", draftId.toString())
                        .param("authorName", TestFixtures.AUTHOR_NAME)
                        .param("authorEmail", VALID_EMAIL)
                        .param("pollTitle", "Test")
                        .param("description", TestFixtures.DESCRIPTION)
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", DATE_OPTION_1)
                        .param("dateOption2", DATE_OPTION_2))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung")));
    }

    @Test
    @DisplayName("step-2 submit continues when draft save fails")
    void step2SubmitContinuesWhenDraftSaveFails() throws Exception {
        MockHttpSession session = new MockHttpSession();
        java.util.UUID draftId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000702");
        WizardState state = TestFixtures.wizardStateBasics();
        state.setAuthorEmail(VALID_EMAIL);
        state.setTitle("Test");
        session.setAttribute(WizardState.SESSION_KEY, state);
        doThrow(new IllegalStateException("s3 save unavailable"))
                .when(wizardStateRepository).save(eq(draftId), any(WizardState.class));

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("draftId", draftId.toString())
                        .param("authorName", TestFixtures.AUTHOR_NAME)
                        .param("authorEmail", VALID_EMAIL)
                        .param("pollTitle", "Test")
                        .param("description", TestFixtures.DESCRIPTION)
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", DATE_OPTION_1)
                        .param("dateOption2", DATE_OPTION_2))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung")));
    }

    @Test
    @DisplayName("step-2 submit loads state from draft repository and persists updates")
    void step2SubmitLoadsStateFromDraftRepositoryAndPersistsUpdates() throws Exception {
        MockHttpSession session = new MockHttpSession();
        java.util.UUID draftId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000703");
        WizardState draftState = TestFixtures.wizardStateBasics();
        draftState.setAuthorEmail(VALID_EMAIL);
        draftState.setTitle("Test");
        when(wizardStateRepository.findById(draftId)).thenReturn(java.util.Optional.of(draftState));

        mockMvc.perform(post("/poll/step-3")
                        .session(session)
                        .param("draftId", draftId.toString())
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", DATE_OPTION_1)
                        .param("dateOption2", DATE_OPTION_2))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung")));

        verify(wizardStateRepository).save(eq(draftId), any(WizardState.class));
    }

    @Test
    @DisplayName("step-3 get redirects to wizard start when session and draft id are missing")
    void step3GetRedirectsWhenSessionAndDraftIdAreMissing() throws Exception {
        mockMvc.perform(get("/poll/step-3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));
    }

    @Test
    @DisplayName("step-3 get loads wizard state from draft repository when session is empty")
    void step3GetLoadsWizardStateFromDraftRepositoryWhenSessionEmpty() throws Exception {
        MockHttpSession session = new MockHttpSession();
        java.util.UUID draftId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000704");
        WizardState draftState = TestFixtures.wizardStateBasics();
        draftState.setAuthorEmail(VALID_EMAIL);
        draftState.setTitle("Test");
        draftState.setDescription(TestFixtures.DESCRIPTION);
        when(wizardStateRepository.findById(draftId)).thenReturn(java.util.Optional.of(draftState));

        mockMvc.perform(get("/poll/step-3")
                        .session(session)
                        .param("draftId", draftId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Abstimmungszeitraum und Bestätigung")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"draftId\"")));
    }
}
