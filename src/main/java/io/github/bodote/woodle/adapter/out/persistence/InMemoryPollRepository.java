package io.github.bodote.woodle.adapter.out.persistence;

import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.Poll;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPollRepository implements PollRepository {

    private final Map<UUID, Poll> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Poll poll) {
        storage.put(poll.pollId(), poll);
    }

    @Override
    public Optional<Poll> findById(UUID pollId) {
        return Optional.ofNullable(storage.get(pollId));
    }

    @Override
    public long countActivePolls() {
        return storage.size();
    }
}
