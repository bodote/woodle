package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.application.port.in.command.SubmitVoteCommand;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.application.service.SubmitVoteService;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("SubmitVoteService")
class SubmitVoteServiceTest {

    @Test
    @DisplayName("adds response to poll")
    void addsResponseToPoll() {
        UUID pollId = UUID.randomUUID();
        PollOption option = TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10));
        Poll poll = TestFixtures.poll(
                pollId,
                "secret",
                EventType.ALL_DAY,
                null,
                List.of(option),
                List.of()
        );

        CapturingRepo repo = new CapturingRepo(poll);
        SubmitVoteService service = new SubmitVoteService(repo);

        SubmitVoteCommand command = new SubmitVoteCommand(
                pollId,
                "Alice",
                List.of(new PollVote(option.optionId(), PollVoteValue.YES)),
                null,
                null
        );

        service.submit(command);

        assertEquals(1, repo.saved.responses().size());
    }

    @Test
    @DisplayName("updates existing response when responseId is provided")
    void updatesExistingResponseWhenResponseIdIsProvided() {
        UUID pollId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        PollOption option = new PollOption(UUID.randomUUID(), LocalDate.of(2026, 2, 10), null, null);
        PollResponse existingResponse = TestFixtures.response(
                responseId,
                "Alice",
                List.of(new PollVote(option.optionId(), PollVoteValue.NO))
        );
        Poll poll = TestFixtures.poll(
                pollId,
                "secret",
                EventType.ALL_DAY,
                null,
                List.of(option),
                List.of(existingResponse)
        );

        CapturingRepo repo = new CapturingRepo(poll);
        SubmitVoteService service = new SubmitVoteService(repo);

        SubmitVoteCommand command = new SubmitVoteCommand(
                pollId,
                "Alice",
                List.of(new PollVote(option.optionId(), PollVoteValue.YES)),
                null,
                responseId
        );

        service.submit(command);

        assertEquals(1, repo.saved.responses().size());
        assertEquals(PollVoteValue.YES, repo.saved.responses().get(0).votes().get(0).value());
        assertEquals(responseId, repo.saved.responses().get(0).responseId());
    }

    @Test
    @DisplayName("adds response with provided responseId when it is not found in poll")
    void addsResponseWithProvidedResponseIdWhenItIsNotFoundInPoll() {
        UUID pollId = UUID.randomUUID();
        UUID missingResponseId = UUID.randomUUID();
        PollOption option = TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10));
        PollResponse differentExistingResponse = TestFixtures.response(
                UUID.randomUUID(),
                "Bob",
                List.of(new PollVote(option.optionId(), PollVoteValue.NO))
        );
        Poll poll = TestFixtures.poll(
                pollId,
                "secret",
                EventType.ALL_DAY,
                null,
                List.of(option),
                List.of(differentExistingResponse)
        );

        CapturingRepo repo = new CapturingRepo(poll);
        SubmitVoteService service = new SubmitVoteService(repo);

        SubmitVoteCommand command = new SubmitVoteCommand(
                pollId,
                "Alice",
                List.of(new PollVote(option.optionId(), PollVoteValue.IF_NEEDED)),
                "comment",
                missingResponseId
        );

        service.submit(command);

        assertEquals(2, repo.saved.responses().size());
        PollResponse added = repo.saved.responses().stream()
                .filter(response -> response.responseId().equals(missingResponseId))
                .findFirst()
                .orElseThrow();
        assertEquals(PollVoteValue.IF_NEEDED, added.votes().getFirst().value());
        assertNotNull(added.createdAt());
    }

    @Test
    @DisplayName("throws when poll does not exist")
    void throwsWhenPollDoesNotExist() {
        UUID pollId = UUID.randomUUID();
        CapturingRepo repo = new CapturingRepo(null);
        SubmitVoteService service = new SubmitVoteService(repo);

        SubmitVoteCommand command = new SubmitVoteCommand(
                pollId,
                "Alice",
                List.of(),
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.submit(command));

        assertEquals("Poll not found", exception.getMessage());
    }

    private static final class CapturingRepo implements PollRepository {
        private Poll saved;
        private final Poll existing;

        private CapturingRepo(Poll existing) {
            this.existing = existing;
        }

        @Override
        public void save(Poll poll) {
            this.saved = poll;
        }

        @Override
        public Optional<Poll> findById(UUID pollId) {
            return Optional.ofNullable(existing);
        }
    }
}
