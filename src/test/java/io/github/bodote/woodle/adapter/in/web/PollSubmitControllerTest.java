package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.adapter.in.web.PollSubmitController;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.CreatePollResult;
import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import io.github.bodote.woodle.adapter.in.web.WizardState;
import io.github.bodote.woodle.domain.model.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PollSubmitController.class)
@DisplayName("/poll/submit")
class PollSubmitControllerTest {

    @MockitoBean
    private CreatePollUseCase createPollUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("redirects to admin URL after submit")
    void redirectsToAdminUrlAfterSubmit() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String adminSecret = "Abc123XyZ789";
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateWithDates(
                EventType.ALL_DAY,
                List.of(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 11))
        );
        state.setExpiresAtOverride(LocalDate.of(2026, 3, 1));
        session.setAttribute(WizardState.SESSION_KEY, state);

        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret));

        mockMvc.perform(post("/poll/submit")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret));

        verify(createPollUseCase).create(any(CreatePollCommand.class));
    }
}
