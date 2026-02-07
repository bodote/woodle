package io.github.bodote.woodle.application.port.in;

import io.github.bodote.woodle.domain.model.Poll;

import java.util.UUID;

public interface ReadPollUseCase {

    Poll getPublic(UUID pollId);

    Poll getAdmin(UUID pollId, String adminSecret);
}
