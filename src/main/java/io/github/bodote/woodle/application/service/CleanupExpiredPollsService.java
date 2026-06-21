package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.application.port.in.CleanupExpiredPollsUseCase;
import io.github.bodote.woodle.application.port.out.PollRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class CleanupExpiredPollsService implements CleanupExpiredPollsUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupExpiredPollsService.class);

    private final PollRepository pollRepository;
    private final Clock clock;

    public CleanupExpiredPollsService(PollRepository pollRepository, Clock clock) {
        this.pollRepository = pollRepository;
        this.clock = clock;
    }

    @Override
    public int cleanupExpiredPolls() {
        LocalDate today = LocalDate.now(clock);
        LOGGER.info("POLL_CLEANUP started asOf={}", today);
        List<UUID> expiredPollIds = pollRepository.findExpiredPollIds(today);
        int found = expiredPollIds.size();
        LOGGER.info("POLL_CLEANUP found {} expired poll(s) asOf={}", found, today);
        int deleted = 0;
        for (UUID pollId : expiredPollIds) {
            try {
                pollRepository.deleteById(pollId);
                deleted++;
            } catch (RuntimeException e) {
                LOGGER.warn("POLL_CLEANUP failed to delete poll {}: {}", pollId, e.getMessage());
            }
        }
        LOGGER.info("POLL_CLEANUP deleted {} of {} expired poll(s) asOf={}", deleted, found, today);
        return deleted;
    }
}
