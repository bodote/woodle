package io.github.bodote.woodle.application.port.in;

import io.github.bodote.woodle.application.port.in.command.SubmitVoteCommand;

public interface SubmitVoteUseCase {

    void submit(SubmitVoteCommand command);
}
