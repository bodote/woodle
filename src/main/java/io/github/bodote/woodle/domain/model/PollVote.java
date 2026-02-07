package io.github.bodote.woodle.domain.model;

import java.util.UUID;

public record PollVote(UUID optionId, PollVoteValue value) {
}
