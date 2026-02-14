package io.github.bodote.woodle.application.port.out;

import io.github.bodote.woodle.domain.model.Poll;

import java.util.Optional;
import java.util.UUID;

public interface PollRepository {

    void save(Poll poll);

    Optional<Poll> findById(UUID pollId);

    long countActivePolls();
}
