package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.adapter.in.web.PollVoteController;
import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.application.port.in.SubmitVoteUseCase;
import io.github.bodote.woodle.application.port.in.command.SubmitVoteCommand;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(PollVoteController.class)
@DisplayName("/poll/{id}/vote")
class PollVoteControllerTest {

    private static final String POLL_ID = "00000000-0000-0000-0000-000000000100";

    @MockitoBean
    private SubmitVoteUseCase submitVoteUseCase;

    @MockitoBean
    private ReadPollUseCase readPollUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("submits votes and redirects")
    void submitsVotesAndRedirects() throws Exception {
        mockMvc.perform(post("/poll/" + POLL_ID + "/vote")
                        .param("participantName", "Alice")
                        .param("vote_11111111-1111-1111-1111-111111111111", "YES"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + POLL_ID));

        verify(submitVoteUseCase).submit(any(SubmitVoteCommand.class));
    }

    @Test
    @DisplayName("accepts vote_new parameters from participant table")
    void acceptsVoteNewParametersFromParticipantTable() throws Exception {
        UUID optionId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        mockMvc.perform(post("/poll/" + POLL_ID + "/vote")
                        .param("participantName", "Alice")
                        .param("vote_new_" + optionId, "IF_NEEDED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + POLL_ID));

        ArgumentCaptor<SubmitVoteCommand> captor = ArgumentCaptor.forClass(SubmitVoteCommand.class);
        verify(submitVoteUseCase).submit(captor.capture());
        SubmitVoteCommand command = captor.getValue();
        assertEquals(1, command.votes().size());
        assertEquals(optionId, command.votes().get(0).optionId());
        assertEquals(io.github.bodote.woodle.domain.model.PollVoteValue.IF_NEEDED, command.votes().get(0).value());
    }

    @Test
    @DisplayName("accepts vote_edit parameters with response id")
    void acceptsVoteEditParametersWithResponseId() throws Exception {
        UUID optionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID responseId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        mockMvc.perform(post("/poll/" + POLL_ID + "/vote")
                        .param("participantName", "Alice")
                        .param("responseId", responseId.toString())
                        .param("vote_edit_" + optionId, "NO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + POLL_ID));

        ArgumentCaptor<SubmitVoteCommand> captor = ArgumentCaptor.forClass(SubmitVoteCommand.class);
        verify(submitVoteUseCase).submit(captor.capture());
        SubmitVoteCommand command = captor.getValue();
        assertEquals(responseId, command.responseId());
        assertEquals(1, command.votes().size());
        assertEquals(optionId, command.votes().get(0).optionId());
        assertEquals(io.github.bodote.woodle.domain.model.PollVoteValue.NO, command.votes().get(0).value());
    }

    @Test
    @DisplayName("renders updated row for htmx edit submit")
    void rendersUpdatedRowForHtmxEditSubmit() throws Exception {
        UUID pollId = UUID.fromString(POLL_ID);
        UUID optionId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID responseId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        PollOption option = TestFixtures.option(optionId, LocalDate.of(2026, 2, 10));
        PollResponse response = TestFixtures.response(
                responseId,
                "Alice",
                List.of(new PollVote(optionId, PollVoteValue.YES))
        );
        Poll poll = TestFixtures.poll(
                pollId,
                "secret",
                EventType.ALL_DAY,
                null,
                List.of(option),
                List.of(response)
        );

        when(readPollUseCase.getPublic(pollId)).thenReturn(poll);

        mockMvc.perform(post("/poll/" + POLL_ID + "/vote")
                        .header("HX-Request", "true")
                        .param("participantName", "Alice")
                        .param("responseId", responseId.toString())
                        .param("vote_edit_" + optionId, "YES"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"row-" + responseId + "\"")))
                .andExpect(content().string(containsString("data-edit-row=\"Alice\"")))
                .andExpect(content().string(containsString("✓")));
    }

    @Test
    @DisplayName("renders symbols for IF_NEEDED and NO in htmx edit row")
    void rendersSymbolsForIfNeededAndNoInHtmxEditRow() throws Exception {
        UUID pollId = UUID.fromString(POLL_ID);
        UUID optionOne = UUID.fromString("77777777-7777-7777-7777-777777777771");
        UUID optionTwo = UUID.fromString("77777777-7777-7777-7777-777777777772");
        UUID responseId = UUID.fromString("77777777-7777-7777-7777-777777777773");

        Poll poll = TestFixtures.poll(
                pollId,
                "secret",
                EventType.INTRADAY,
                30,
                List.of(
                        TestFixtures.option(optionOne, LocalDate.of(2026, 2, 10), LocalTime.of(9, 0), LocalTime.of(9, 30)),
                        TestFixtures.option(optionTwo, LocalDate.of(2026, 2, 10), LocalTime.of(9, 30), LocalTime.of(10, 0))
                ),
                List.of(TestFixtures.response(
                        responseId,
                        "Alice",
                        List.of(
                                new PollVote(optionOne, PollVoteValue.IF_NEEDED),
                                new PollVote(optionTwo, PollVoteValue.NO)
                        )
                ))
        );
        when(readPollUseCase.getPublic(pollId)).thenReturn(poll);

        mockMvc.perform(post("/poll/" + POLL_ID + "/vote")
                        .header("HX-Request", "true")
                        .param("participantName", "Alice")
                        .param("responseId", responseId.toString())
                        .param("vote_edit_" + optionOne, "IF_NEEDED")
                        .param("vote_edit_" + optionTwo, "NO"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("(✓)")))
                .andExpect(content().string(containsString("✗")));
    }

    @Test
    @DisplayName("returns server error when htmx edit references unknown response id")
    void returnsValidationErrorWhenHtmxEditReferencesUnknownResponseId() throws Exception {
        UUID pollId = UUID.fromString(POLL_ID);
        UUID optionId = UUID.fromString("88888888-8888-8888-8888-888888888881");
        UUID missingResponseId = UUID.fromString("88888888-8888-8888-8888-888888888882");

        Poll poll = TestFixtures.poll(
                pollId,
                List.of(TestFixtures.option(optionId, LocalDate.of(2026, 2, 10))),
                List.of()
        );
        when(readPollUseCase.getPublic(pollId)).thenReturn(poll);

                mockMvc.perform(post("/poll/" + POLL_ID + "/vote")
                        .header("HX-Request", "true")
                        .param("participantName", "Alice")
                        .param("responseId", missingResponseId.toString())
                        .param("vote_edit_" + optionId, "YES"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Response not found"));
    }

    @Test
    @DisplayName("redirects when htmx header is true but response id is missing")
    void redirectsWhenHtmxHeaderIsTrueButResponseIdIsMissing() throws Exception {
        UUID optionId = UUID.fromString("99999999-9999-9999-9999-999999999991");

        mockMvc.perform(post("/poll/" + POLL_ID + "/vote")
                        .header("HX-Request", "true")
                        .param("participantName", "Alice")
                        .param("vote_edit_" + optionId, "YES"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/" + POLL_ID));

        verify(submitVoteUseCase).submit(any(SubmitVoteCommand.class));
        verifyNoInteractions(readPollUseCase);
    }
}
