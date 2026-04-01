package io.github.bodote.woodle.application.port.out;

import java.util.UUID;

public record PollCreatedEmail(
        UUID pollId,
        String adminSecret,
        String authorName,
        String authorEmail,
        String pollTitle
) {
}
