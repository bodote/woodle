package io.github.bodote.woodle.application.port.in.command;

import io.github.bodote.woodle.domain.model.PollVote;

import java.util.List;
import java.util.UUID;

public record SubmitVoteCommand(
        UUID pollId,
        String participantName,
        List<PollVote> votes,
        String comment,
        UUID responseId
) {
}
