package io.github.bodote.woodle.application.port.in;

import io.github.bodote.woodle.application.port.in.command.SubmitVoteCommand;

import java.util.UUID;

public interface SubmitVoteUseCase {

    void submit(SubmitVoteCommand command);

    void delete(UUID pollId, UUID responseId);
}
