package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.adapter.in.web.WizardState;
import io.github.bodote.woodle.domain.model.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(io.github.bodote.woodle.adapter.in.web.PollNewPageController.class)
@DisplayName("/poll/step-3")
class PollStep3PageTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("renders summary from session state")
    void rendersSummaryFromSessionState() throws Exception {
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateWithDates(
                EventType.ALL_DAY,
                List.of(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 11))
        );
        session.setAttribute(WizardState.SESSION_KEY, state);

        mockMvc.perform(get("/poll/step-3").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Liste Ihrer Auswahlmöglichkeiten")));
    }

    @Test
    @DisplayName("renders intraday summary with start times")
    void rendersIntradaySummaryWithStartTimes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateIntraday(
                List.of(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 11)),
                List.of(LocalTime.of(9, 0), LocalTime.of(13, 30)),
                90
        );
        session.setAttribute(WizardState.SESSION_KEY, state);

        mockMvc.perform(get("/poll/step-3").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026-02-10 09:00")))
                .andExpect(content().string(containsString("2026-02-11 13:30")));
    }
}
