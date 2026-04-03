package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.application.port.in.SubmitVoteUseCase;
import io.github.bodote.woodle.application.port.in.command.SubmitVoteCommand;
import io.github.bodote.woodle.application.port.out.NewCommentEmail;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class SubmitVoteService implements SubmitVoteUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubmitVoteService.class);

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

        if (emailEnabled && poll.notifyOnComment()) {
            LOGGER.info("Sending new-entry notification for poll {} to {}", poll.pollId(), poll.authorEmail());
            boolean sent = pollEmailSender.sendNewComment(new NewCommentEmail(
                    poll.pollId(),
                    poll.adminSecret(),
                    poll.authorName(),
                    poll.authorEmail(),
                    poll.title(),
                    command.participantName(),
                    command.comment()
            ));
            if (!sent) {
                LOGGER.warn("New-entry notification email could not be sent for poll {}", poll.pollId());
            }
        } else {
            LOGGER.debug("Skipping new-entry notification for poll {} (emailEnabled={}, notifyOnComment={})",
                    poll.pollId(), emailEnabled, poll.notifyOnComment());
        }
    }

    @Override
    public void delete(UUID pollId, UUID responseId) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
        pollRepository.save(poll.removeResponse(responseId));
    }
}
