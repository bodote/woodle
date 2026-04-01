package io.github.bodote.woodle.application.port.out;

import java.util.UUID;

public record NewCommentEmail(
        UUID pollId,
        String adminSecret,
        String authorName,
        String authorEmail,
        String pollTitle,
        String participantName,
        String comment
) {
}
