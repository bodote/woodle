package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.adapter.in.web.PollSubmitController;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.CreatePollResult;
import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import io.github.bodote.woodle.application.port.out.WizardStateRepository;
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
import static org.mockito.Mockito.never;
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

    @MockitoBean
    private WizardStateRepository wizardStateRepository;

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

    @Test
    @DisplayName("reads wizard draft from repository using draft id")
    void readsWizardDraftFromRepositoryUsingDraftId() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID draftId = UUID.fromString("00000000-0000-0000-0000-000000000777");
        String adminSecret = "DraftSecret12";
        WizardState state = TestFixtures.wizardStateWithDates(
                EventType.ALL_DAY,
                List.of(LocalDate.of(2026, 2, 12), LocalDate.of(2026, 2, 13))
        );
        state.setAuthorName("Draft Author");
        state.setAuthorEmail("draft@example.com");
        state.setTitle("Draft title");

        when(wizardStateRepository.findById(draftId)).thenReturn(java.util.Optional.of(state));
        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret));

        mockMvc.perform(post("/poll/submit")
                        .param("draftId", draftId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret));

        verify(wizardStateRepository).delete(draftId);
        verify(createPollUseCase).create(any(CreatePollCommand.class));
    }

    @Test
    @DisplayName("redirects to first step when draft is missing and session is empty")
    void redirectsToFirstStepWhenDraftMissingAndSessionEmpty() throws Exception {
        UUID draftId = UUID.fromString("00000000-0000-0000-0000-000000000778");
        when(wizardStateRepository.findById(draftId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/poll/submit")
                        .param("draftId", draftId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));

        verify(createPollUseCase, never()).create(any(CreatePollCommand.class));
        verify(wizardStateRepository, never()).delete(draftId);
    }
}
