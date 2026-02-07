package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.Poll;

import java.util.UUID;

public class ReadPollService implements ReadPollUseCase {

    private final PollRepository pollRepository;

    public ReadPollService(PollRepository pollRepository) {
        this.pollRepository = pollRepository;
    }

    @Override
    public Poll getPublic(UUID pollId) {
        return pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
    }

    @Override
    public Poll getAdmin(UUID pollId, String adminSecret) {
        Poll poll = getPublic(pollId);
        if (!poll.adminSecret().equals(adminSecret)) {
            throw new IllegalArgumentException("Invalid admin secret");
        }
        return poll;
    }
}
