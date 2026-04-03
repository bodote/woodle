package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.application.port.in.command.SubmitVoteCommand;
import io.github.bodote.woodle.application.port.out.NewCommentEmail;
import io.github.bodote.woodle.application.port.out.PollCreatedEmail;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("SubmitVoteService")
class SubmitVoteServiceTest {

    private static CapturingPollEmailSender noop() {
        return new CapturingPollEmailSender();
    }

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
        SubmitVoteService service = new SubmitVoteService(repo, noop(), true);

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
        SubmitVoteService service = new SubmitVoteService(repo, noop(), true);

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
        SubmitVoteService service = new SubmitVoteService(repo, noop(), true);

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
        SubmitVoteService service = new SubmitVoteService(repo, noop(), true);

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

    @Test
    @DisplayName("deletes existing response from poll")
    void deletesExistingResponseFromPoll() {
        UUID pollId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        PollOption option = TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10));
        Poll poll = TestFixtures.poll(
                pollId,
                "secret",
                EventType.ALL_DAY,
                null,
                List.of(option),
                List.of(
                        TestFixtures.response(responseId, "Alice", List.of(new PollVote(option.optionId(), PollVoteValue.YES))),
                        TestFixtures.response(UUID.randomUUID(), "Bob", List.of(new PollVote(option.optionId(), PollVoteValue.NO)))
                )
        );

        CapturingRepo repo = new CapturingRepo(poll);
        SubmitVoteService service = new SubmitVoteService(repo, noop(), true);

        service.delete(pollId, responseId);

        assertEquals(1, repo.saved.responses().size());
        assertEquals("Bob", repo.saved.responses().getFirst().participantName());
    }

    @Test
    @DisplayName("throws when response to delete does not exist")
    void throwsWhenResponseToDeleteDoesNotExist() {
        UUID pollId = UUID.randomUUID();
        UUID missingResponseId = UUID.randomUUID();
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
        SubmitVoteService service = new SubmitVoteService(repo, noop(), true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(pollId, missingResponseId)
        );

        assertEquals("Response not found", exception.getMessage());
    }

    // ── NEW comment notification tests ────────────────────────────────────────

    @Test
    @DisplayName("sends entry notification email when notifyOnComment is true and no comment")
    void sendsEntryEmailWhenNotifyOnCommentIsTrueWithoutComment() {
        UUID pollId = UUID.randomUUID();
        Poll poll = pollWithNotifyOnComment(pollId, true);
        CapturingRepo repo = new CapturingRepo(poll);
        CapturingPollEmailSender emailSender = new CapturingPollEmailSender();
        SubmitVoteService service = new SubmitVoteService(repo, emailSender, true);

        service.submit(new SubmitVoteCommand(pollId, "Alice", List.of(), null, null));

        assertNotNull(emailSender.lastCommentEmail);
        assertEquals("Alice", emailSender.lastCommentEmail.participantName());
        assertNull(emailSender.lastCommentEmail.comment());
        assertEquals(TestFixtures.AUTHOR_EMAIL, emailSender.lastCommentEmail.authorEmail());
        assertEquals(TestFixtures.TITLE, emailSender.lastCommentEmail.pollTitle());
    }

    @Test
    @DisplayName("sends entry notification email when notifyOnComment is true and comment is present")
    void sendsEntryEmailWhenNotifyOnCommentIsTrueWithComment() {
        UUID pollId = UUID.randomUUID();
        Poll poll = pollWithNotifyOnComment(pollId, true);
        CapturingRepo repo = new CapturingRepo(poll);
        CapturingPollEmailSender emailSender = new CapturingPollEmailSender();
        SubmitVoteService service = new SubmitVoteService(repo, emailSender, true);

        service.submit(new SubmitVoteCommand(pollId, "Alice", List.of(), "Great timing!", null));

        assertNotNull(emailSender.lastCommentEmail);
        assertEquals("Great timing!", emailSender.lastCommentEmail.comment());
    }

    @Test
    @DisplayName("does not send entry email when notifyOnComment is false")
    void doesNotSendEntryEmailWhenNotifyOnCommentIsFalse() {
        UUID pollId = UUID.randomUUID();
        Poll poll = pollWithNotifyOnComment(pollId, false);
        CapturingRepo repo = new CapturingRepo(poll);
        CapturingPollEmailSender emailSender = new CapturingPollEmailSender();
        SubmitVoteService service = new SubmitVoteService(repo, emailSender, true);

        service.submit(new SubmitVoteCommand(pollId, "Bob", List.of(), null, null));

        assertNull(emailSender.lastCommentEmail);
    }

    @Test
    @DisplayName("does not send entry email when email is disabled")
    void doesNotSendCommentEmailWhenEmailIsDisabled() {
        UUID pollId = UUID.randomUUID();
        Poll poll = pollWithNotifyOnComment(pollId, true);
        CapturingRepo repo = new CapturingRepo(poll);
        CapturingPollEmailSender emailSender = new CapturingPollEmailSender();
        SubmitVoteService service = new SubmitVoteService(repo, emailSender, false);

        service.submit(new SubmitVoteCommand(pollId, "Eve", List.of(), null, null));

        assertNull(emailSender.lastCommentEmail);
    }

    @Test
    @DisplayName("logs warning when entry notification email send fails")
    void logsWarningWhenEntryNotificationEmailSendFails() {
        UUID pollId = UUID.randomUUID();
        Poll poll = pollWithNotifyOnComment(pollId, true);
        CapturingRepo repo = new CapturingRepo(poll);
        FailingPollEmailSender failingSender = new FailingPollEmailSender();
        SubmitVoteService service = new SubmitVoteService(repo, failingSender, true);

        // should not throw even when email send returns false
        service.submit(new SubmitVoteCommand(pollId, "Grace", List.of(), null, null));

        assertEquals(1, repo.saved.responses().size());
    }

    @Test
    @DisplayName("does not send comment email when a responseId is provided")
    void doesNotSendCommentEmailWhenResponseIdIsProvided() {
        UUID pollId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        Poll poll = pollWithNotifyOnComment(pollId, true);
        CapturingRepo repo = new CapturingRepo(poll);
        CapturingPollEmailSender emailSender = new CapturingPollEmailSender();
        SubmitVoteService service = new SubmitVoteService(repo, emailSender, true);

        service.submit(new SubmitVoteCommand(pollId, "Frank", List.of(), "Updated comment", responseId));

        assertNull(emailSender.lastCommentEmail);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Poll pollWithNotifyOnComment(UUID pollId, boolean notifyOnComment) {
        OffsetDateTime now = OffsetDateTime.of(2026, 2, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        return new Poll(
                pollId,
                TestFixtures.ADMIN_SECRET,
                TestFixtures.TITLE,
                TestFixtures.DESCRIPTION,
                TestFixtures.AUTHOR_NAME,
                TestFixtures.AUTHOR_EMAIL,
                EventType.ALL_DAY,
                null,
                List.of(),
                List.of(),
                now,
                now,
                LocalDate.of(2026, 3, 10),
                notifyOnComment
        );
    }

    private static final class FailingPollEmailSender implements PollEmailSender {
        @Override
        public boolean sendPollCreated(PollCreatedEmail pollCreatedEmail) {
            return false;
        }

        @Override
        public boolean sendNewComment(NewCommentEmail email) {
            return false;
        }
    }

    private static final class CapturingPollEmailSender implements PollEmailSender {
        NewCommentEmail lastCommentEmail;

        @Override
        public boolean sendPollCreated(PollCreatedEmail pollCreatedEmail) {
            return false;
        }

        @Override
        public boolean sendNewComment(NewCommentEmail email) {
            this.lastCommentEmail = email;
            return true;
        }
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

        @Override
        public long countActivePolls() {
            return existing == null ? 0L : 1L;
        }
    }
}
