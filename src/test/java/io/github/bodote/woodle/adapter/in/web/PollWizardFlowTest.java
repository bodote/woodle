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
}
