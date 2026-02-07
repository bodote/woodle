package io.github.bodote.woodle;

import io.github.bodote.woodle.adapter.in.web.PollViewController;
import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(PollViewController.class)
@DisplayName("participant view")
class PollParticipantViewTest {

    @MockitoBean
    private ReadPollUseCase readPollUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("renders table based voting view with editable rows")
    void rendersTableBasedVotingViewWithEditableRows() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID response1Id = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID response2Id = UUID.fromString("00000000-0000-0000-0000-000000000302");
        PollOption option1 = TestFixtures.option(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                LocalDate.of(2026, 2, 20));
        PollOption option2 = TestFixtures.option(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                LocalDate.of(2026, 2, 22));
        PollOption option3 = TestFixtures.option(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                LocalDate.of(2026, 3, 1));

        PollResponse response1 = TestFixtures.response(
                response1Id,
                "Bodo",
                List.of(
                        new PollVote(option1.optionId(), PollVoteValue.YES),
                        new PollVote(option2.optionId(), PollVoteValue.IF_NEEDED),
                        new PollVote(option3.optionId(), PollVoteValue.NO)
                )
        );
        PollResponse response2 = TestFixtures.response(
                response2Id,
                "Elisa",
                List.of(
                        new PollVote(option1.optionId(), PollVoteValue.YES),
                        new PollVote(option2.optionId(), PollVoteValue.YES),
                        new PollVote(option3.optionId(), PollVoteValue.YES)
                )
        );

        Poll poll = TestFixtures.poll(
                pollId,
                List.of(option1, option2, option3),
                List.of(response1, response2)
        );

        when(readPollUseCase.getPublic(pollId)).thenReturn(poll);

        mockMvc.perform(get("/poll/" + pollId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Stimmabgaben zur Umfrage")))
                .andExpect(content().string(containsString("id=\"poll-votes-table\"")))
                .andExpect(content().string(containsString("Februar 2026")))
                .andExpect(content().string(containsString("März 2026")))
                .andExpect(content().string(containsString("data-date=\"2026-02-20\"")))
                .andExpect(content().string(containsString("data-date=\"2026-03-01\"")))
                .andExpect(content().string(containsString("Bodo")))
                .andExpect(content().string(containsString("Elisa")))
                .andExpect(content().string(containsString("data-edit-row=\"Bodo\"")))
                .andExpect(content().string(containsString("data-edit-row=\"Elisa\"")))
                .andExpect(content().string(containsString("id=\"row-" + response1Id + "\"")))
                .andExpect(content().string(containsString("hx-get=\"/poll/" + pollId + "/responses/" + response1Id + "/edit\"")))
                .andExpect(content().string(containsString("id=\"row-" + response2Id + "\"")))
                .andExpect(content().string(containsString("hx-get=\"/poll/" + pollId + "/responses/" + response2Id + "/edit\"")))
                .andExpect(content().string(containsString("data-add-row=\"true\"")))
                .andExpect(content().string(containsString("id=\"add-vote-form\"")))
                .andExpect(content().string(containsString("form=\"add-vote-form\"")))
                .andExpect(content().string(containsString("name=\"participantName\"")))
                .andExpect(content().string(containsString("name=\"vote_new_00000000-0000-0000-0000-000000000001\"")))
                .andExpect(content().string(containsString("value=\"YES\"")))
                .andExpect(content().string(containsString("value=\"IF_NEEDED\"")))
                .andExpect(content().string(containsString("value=\"NO\"")))
                .andExpect(content().string(containsString("class=\"summary-row\"")))
                .andExpect(content().string(containsString("Speichern")));
    }

    @Test
    @DisplayName("renders editable row fragment for a participant")
    void rendersEditableRowFragmentForParticipant() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000211");
        UUID responseId = UUID.fromString("00000000-0000-0000-0000-000000000311");
        PollOption option = TestFixtures.option(UUID.fromString("00000000-0000-0000-0000-000000000021"),
                LocalDate.of(2026, 2, 20));
        PollResponse response = TestFixtures.response(
                responseId,
                "Alice",
                List.of(new PollVote(option.optionId(), PollVoteValue.YES))
        );
        Poll poll = TestFixtures.poll(
                pollId,
                List.of(option),
                List.of(response)
        );

        when(readPollUseCase.getPublic(pollId)).thenReturn(poll);

        mockMvc.perform(get("/poll/" + pollId + "/responses/" + responseId + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"row-" + responseId + "\"")))
                .andExpect(content().string(containsString("name=\"responseId\"")))
                .andExpect(content().string(containsString("value=\"" + responseId + "\"")))
                .andExpect(content().string(containsString("name=\"participantName\"")))
                .andExpect(content().string(containsString("value=\"Alice\"")))
                .andExpect(content().string(containsString("name=\"vote_edit_" + option.optionId() + "\"")))
                .andExpect(content().string(containsString("value=\"YES\"")));
    }
}
