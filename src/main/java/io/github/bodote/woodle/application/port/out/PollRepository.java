package io.github.bodote.woodle.application.port.out;

import io.github.bodote.woodle.domain.model.Poll;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollRepository {

    void save(Poll poll);

    Optional<Poll> findById(UUID pollId);

    long countActivePolls();

    /**
     * Ids of polls whose {@code expiresAt} is non-null and strictly before {@code asOf}.
     */
    List<UUID> findExpiredPollIds(LocalDate asOf);

    void deleteById(UUID pollId);
}
