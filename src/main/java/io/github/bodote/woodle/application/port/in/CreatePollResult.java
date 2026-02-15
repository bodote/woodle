package io.github.bodote.woodle.application.port.in;

import java.util.UUID;

public record CreatePollResult(
        UUID pollId,
        String adminSecret,
        boolean notificationQueued,
        boolean notificationDisabled
) {
}
