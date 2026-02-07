package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.application.port.in.SubmitVoteUseCase;
import io.github.bodote.woodle.application.port.in.command.SubmitVoteCommand;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class SubmitVoteService implements SubmitVoteUseCase {

    private final PollRepository pollRepository;

    public SubmitVoteService(PollRepository pollRepository) {
        this.pollRepository = pollRepository;
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
    }
}
