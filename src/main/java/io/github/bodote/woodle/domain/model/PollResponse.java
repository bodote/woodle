package io.github.bodote.woodle.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PollResponse(
        UUID responseId,
        String participantName,
        OffsetDateTime createdAt,
        List<PollVote> votes,
        String comment
) {
}
