package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.application.port.in.SubmitVoteUseCase;
import io.github.bodote.woodle.application.port.in.command.SubmitVoteCommand;
import io.github.bodote.woodle.application.port.out.NewCommentEmail;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class SubmitVoteService implements SubmitVoteUseCase {

    private final PollRepository pollRepository;
    private final PollEmailSender pollEmailSender;
    private final boolean emailEnabled;

    public SubmitVoteService(PollRepository pollRepository,
                             PollEmailSender pollEmailSender,
                             boolean emailEnabled) {
        this.pollRepository = pollRepository;
        this.pollEmailSender = pollEmailSender;
        this.emailEnabled = emailEnabled;
    }

    @Override
    public void submit(SubmitVoteCommand command) {
        Poll poll = pollRepository.findById(command.pollId())
                .orElseThrow(() -> new IllegalArgumentException("Poll not found"));

        PollResponse existingResponse = null;
        if (command.responseId() != null) {
            for (PollResponse response : poll.responses()) {
                if (response.responseId().equals(command.responseId())) {
                    existingResponse = response;
                    break;
                }
            }
        }

        PollResponse response = new PollResponse(
                command.responseId() != null ? command.responseId() : UUID.randomUUID(),
                command.participantName(),
                existingResponse != null ? existingResponse.createdAt() : OffsetDateTime.now(ZoneOffset.UTC),
                command.votes(),
                command.comment()
        );

        if (command.responseId() != null) {
            pollRepository.save(poll.replaceResponse(response));
            return;
        }

        pollRepository.save(poll.addResponse(response));

        if (emailEnabled
                && poll.notifyOnComment()
                && command.comment() != null
                && !command.comment().isBlank()) {
            pollEmailSender.sendNewComment(new NewCommentEmail(
                    poll.pollId(),
                    poll.adminSecret(),
                    poll.authorName(),
                    poll.authorEmail(),
                    poll.title(),
                    command.participantName(),
                    command.comment()
            ));
        }
    }

    @Override
    public void delete(UUID pollId, UUID responseId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
        pollRepository.save(poll.removeResponse(responseId));
    }
}
