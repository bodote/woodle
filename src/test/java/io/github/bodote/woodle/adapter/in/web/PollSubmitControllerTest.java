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
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
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
                .thenReturn(new CreatePollResult(pollId, adminSecret, true, false));

        mockMvc.perform(post("/poll/submit")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret));

        verify(createPollUseCase).create(any(CreatePollCommand.class));
    }

    @Test
    @DisplayName("adds emailFailed flag when poll email was not queued")
    void addsEmailFailedFlagWhenPollEmailWasNotQueued() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        String adminSecret = "EmailFailed123";
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateWithDates(
                EventType.ALL_DAY,
                List.of(LocalDate.of(2026, 2, 10))
        );
        session.setAttribute(WizardState.SESSION_KEY, state);

        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret, false, false));

        mockMvc.perform(post("/poll/submit")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret + "?emailFailed=true"));
    }

    @Test
    @DisplayName("adds emailDisabled flag when email delivery is disabled")
    void addsEmailDisabledFlagWhenEmailDeliveryIsDisabled() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        String adminSecret = "EmailDisabled123";
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateWithDates(
                EventType.ALL_DAY,
                List.of(LocalDate.of(2026, 2, 12))
        );
        session.setAttribute(WizardState.SESSION_KEY, state);

        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret, false, true));

        mockMvc.perform(post("/poll/submit")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret + "?emailDisabled=true"));
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
                .thenReturn(new CreatePollResult(pollId, adminSecret, true, false));

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

    @Test
    @DisplayName("submits with posted payload when draft lookup misses and session is empty")
    void submitsWithPostedPayloadWhenDraftLookupMissesAndSessionIsEmpty() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID draftId = UUID.fromString("00000000-0000-0000-0000-000000000779");
        String adminSecret = "FallbackSecret123";
        when(wizardStateRepository.findById(draftId)).thenReturn(java.util.Optional.empty());
        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret, true, false));

        mockMvc.perform(post("/poll/submit")
                        .param("draftId", draftId.toString())
                        .param("authorName", "Fallback Author")
                        .param("authorEmail", "fallback@example.com")
                        .param("pollTitle", "Fallback title")
                        .param("description", "Fallback description")
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-03-01")
                        .param("dateOption2", "2026-03-02"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret));

        verify(createPollUseCase, times(1)).create(any(CreatePollCommand.class));
        verify(wizardStateRepository, times(1)).delete(draftId);
    }

    @Test
    @DisplayName("submits with posted payload when draft lookup throws")
    void submitsWithPostedPayloadWhenDraftLookupThrows() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID draftId = UUID.fromString("00000000-0000-0000-0000-000000000780");
        String adminSecret = "DraftLookupError";
        when(wizardStateRepository.findById(draftId)).thenThrow(new IllegalStateException("s3 unavailable"));
        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret, true, false));

        mockMvc.perform(post("/poll/submit")
                        .param("draftId", draftId.toString())
                        .param("authorName", "Fallback Author")
                        .param("authorEmail", "fallback@example.com")
                        .param("pollTitle", "Fallback title")
                        .param("description", "Fallback description")
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-03-01")
                        .param("dateOption2", "2026-03-02"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret));

        verify(createPollUseCase, times(1)).create(any(CreatePollCommand.class));
        verify(wizardStateRepository, times(1)).delete(draftId);
    }

    @Test
    @DisplayName("parses intraday start times from posted payload when no state exists")
    void parsesIntradayStartTimesFromPostedPayloadWhenNoStateExists() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        String adminSecret = "IntradayFallback";
        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret, true, false));

        mockMvc.perform(post("/poll/submit")
                        .param("authorName", "Fallback Author")
                        .param("authorEmail", "fallback@example.com")
                        .param("pollTitle", "Fallback title")
                        .param("description", "Fallback description")
                        .param("eventType", "INTRADAY")
                        .param("durationMinutes", "90")
                        .param("dateOption1", "2026-03-01")
                        .param("dateOption2", "2026-03-02")
                        .param("startTime1", "09:00")
                        .param("startTime2", "14:30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret));

        org.mockito.ArgumentCaptor<CreatePollCommand> captor =
                org.mockito.ArgumentCaptor.forClass(CreatePollCommand.class);
        verify(createPollUseCase).create(captor.capture());
        CreatePollCommand command = captor.getValue();
        assertEquals(io.github.bodote.woodle.domain.model.EventType.INTRADAY, command.eventType());
        assertEquals(2, command.startTimes().size());
    }

    @Test
    @DisplayName("redirects to first step when neither wizard state nor required fallback payload exists")
    void redirectsWhenNoWizardStateAndFallbackPayloadMissing() throws Exception {
        mockMvc.perform(post("/poll/submit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));

        verify(createPollUseCase, never()).create(any(CreatePollCommand.class));
    }

    @Test
    @DisplayName("keeps existing session values when fallback payload is blank")
    void keepsExistingSessionValuesWhenFallbackPayloadIsBlank() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000006");
        String adminSecret = "KeepSessionValues";
        MockHttpSession session = new MockHttpSession();
        WizardState state = TestFixtures.wizardStateWithDates(
                EventType.INTRADAY,
                List.of(LocalDate.of(2026, 4, 1))
        );
        state.setAuthorName("Existing Name");
        state.setAuthorEmail("existing@example.com");
        state.setTitle("Existing Title");
        state.setDurationMinutes(60);
        state.setStartTimes(List.of(LocalTime.of(9, 15)));
        session.setAttribute(WizardState.SESSION_KEY, state);

        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret, true, false));

        mockMvc.perform(post("/poll/submit")
                        .session(session)
                        .param("authorName", " ")
                        .param("authorEmail", " ")
                        .param("pollTitle", " ")
                        .param("durationMinutes", "15")
                        .param("startTime1", "18:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret));

        org.mockito.ArgumentCaptor<CreatePollCommand> captor =
                org.mockito.ArgumentCaptor.forClass(CreatePollCommand.class);
        verify(createPollUseCase).create(captor.capture());
        CreatePollCommand command = captor.getValue();
        assertEquals("Existing Name", command.authorName());
        assertEquals("existing@example.com", command.authorEmail());
        assertEquals("Existing Title", command.title());
        assertEquals(60, command.durationMinutes());
        assertIterableEquals(List.of(LocalTime.of(9, 15)), command.startTimes());
    }

    @Test
    @DisplayName("ignores blank date and start time values in fallback parsing")
    void ignoresBlankDateAndStartTimeValuesInFallbackParsing() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000007");
        String adminSecret = "IgnoreBlankInputs";
        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret, true, false));

        mockMvc.perform(post("/poll/submit")
                        .param("authorName", "Fallback Author")
                        .param("authorEmail", "fallback@example.com")
                        .param("pollTitle", "Fallback title")
                        .param("eventType", "INTRADAY")
                        .param("dateOption1", "")
                        .param("dateOption2", "2026-05-02")
                        .param("startTime1", "")
                        .param("startTime2", "14:30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret));

        org.mockito.ArgumentCaptor<CreatePollCommand> captor =
                org.mockito.ArgumentCaptor.forClass(CreatePollCommand.class);
        verify(createPollUseCase, times(1)).create(captor.capture());
        CreatePollCommand command = captor.getValue();
        assertIterableEquals(List.of(LocalDate.of(2026, 5, 2)), command.dates());
        assertIterableEquals(List.of(LocalTime.of(14, 30)), command.startTimes());
    }

    @Test
    @DisplayName("redirects to first step when author name is missing")
    void redirectsWhenAuthorNameMissing() throws Exception {
        mockMvc.perform(post("/poll/submit")
                        .param("authorEmail", "fallback@example.com")
                        .param("pollTitle", "Fallback title")
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-03-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));

        verify(createPollUseCase, never()).create(any(CreatePollCommand.class));
    }

    @Test
    @DisplayName("redirects to first step when author email is missing")
    void redirectsWhenAuthorEmailMissing() throws Exception {
        mockMvc.perform(post("/poll/submit")
                        .param("authorName", "Fallback Author")
                        .param("pollTitle", "Fallback title")
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-03-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));

        verify(createPollUseCase, never()).create(any(CreatePollCommand.class));
    }

    @Test
    @DisplayName("redirects to first step when author name is blank")
    void redirectsWhenAuthorNameBlank() throws Exception {
        mockMvc.perform(post("/poll/submit")
                        .param("authorName", " ")
                        .param("authorEmail", "fallback@example.com")
                        .param("pollTitle", "Fallback title")
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-03-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));

        verify(createPollUseCase, never()).create(any(CreatePollCommand.class));
    }

    @Test
    @DisplayName("redirects to first step when author email is blank")
    void redirectsWhenAuthorEmailBlank() throws Exception {
        mockMvc.perform(post("/poll/submit")
                        .param("authorName", "Fallback Author")
                        .param("authorEmail", " ")
                        .param("pollTitle", "Fallback title")
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-03-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));

        verify(createPollUseCase, never()).create(any(CreatePollCommand.class));
    }

    @Test
    @DisplayName("redirects to first step when poll title is missing")
    void redirectsWhenPollTitleMissing() throws Exception {
        mockMvc.perform(post("/poll/submit")
                        .param("authorName", "Fallback Author")
                        .param("authorEmail", "fallback@example.com")
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-03-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));

        verify(createPollUseCase, never()).create(any(CreatePollCommand.class));
    }

    @Test
    @DisplayName("redirects to first step when poll title is blank")
    void redirectsWhenPollTitleBlank() throws Exception {
        mockMvc.perform(post("/poll/submit")
                        .param("authorName", "Fallback Author")
                        .param("authorEmail", "fallback@example.com")
                        .param("pollTitle", " ")
                        .param("eventType", "ALL_DAY")
                        .param("dateOption1", "2026-03-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));

        verify(createPollUseCase, never()).create(any(CreatePollCommand.class));
    }

    @Test
    @DisplayName("handles blank parameters in date and time extraction")
    void handlesParametersWithNoValuesInDateAndTimeExtraction() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000008");
        String adminSecret = "EmptyParameterValues";
        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(pollId, adminSecret, true, false));

        mockMvc.perform(post("/poll/submit")
                        .param("authorName", "Fallback Author")
                        .param("authorEmail", "fallback@example.com")
                        .param("pollTitle", "Fallback title")
                        .param("eventType", "INTRADAY")
                        .param("dateOption1", "")
                        .param("dateOption2", "2026-06-15")
                        .param("startTime1", "")
                        .param("startTime2", "10:15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + pollId + "-" + adminSecret));

        org.mockito.ArgumentCaptor<CreatePollCommand> captor =
                org.mockito.ArgumentCaptor.forClass(CreatePollCommand.class);
        verify(createPollUseCase, times(1)).create(captor.capture());
        CreatePollCommand command = captor.getValue();
        assertIterableEquals(List.of(LocalDate.of(2026, 6, 15)), command.dates());
        assertIterableEquals(List.of(LocalTime.of(10, 15)), command.startTimes());
    }
}
