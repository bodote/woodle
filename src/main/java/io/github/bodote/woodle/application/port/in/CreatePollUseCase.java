package io.github.bodote.woodle.application.port.in;

import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;

public interface CreatePollUseCase {

    CreatePollResult create(CreatePollCommand command);
}
