package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.application.service.ReadPollService;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ReadPollService")
class ReadPollServiceTest {

    private static final String ADMIN_SECRET = "secret";
    private static final LocalDate OPTION_DATE = LocalDate.of(2026, 2, 10);

    @Test
    @DisplayName("returns poll for public read when poll exists")
    void returnsPollForPublicReadWhenPollExists() {
        UUID pollId = UUID.randomUUID();
        Poll poll = poll(pollId);
        ReadPollService service = new ReadPollService(new FixedPollRepository(poll));

        Poll result = service.getPublic(pollId);

        assertEquals(poll, result);
    }

    @Test
    @DisplayName("throws when public poll is missing")
    void throwsWhenPublicPollIsMissing() {
        UUID pollId = UUID.randomUUID();
        ReadPollService service = new ReadPollService(new FixedPollRepository(null));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.getPublic(pollId));

        assertEquals("Poll not found", exception.getMessage());
    }

    @Test
    @DisplayName("returns poll for admin read with valid secret")
    void returnsPollForAdminReadWithValidSecret() {
        UUID pollId = UUID.randomUUID();
        Poll poll = poll(pollId);
        ReadPollService service = new ReadPollService(new FixedPollRepository(poll));

        Poll result = service.getAdmin(pollId, ADMIN_SECRET);

        assertEquals(poll, result);
    }

    @Test
    @DisplayName("rejects invalid admin secret")
    void rejectsInvalidAdminSecret() {
        UUID pollId = UUID.randomUUID();
        Poll poll = poll(pollId);
        ReadPollService service = new ReadPollService(new FixedPollRepository(poll));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.getAdmin(pollId, "wrong"));

        assertEquals("Invalid admin secret", exception.getMessage());
    }

    private static Poll poll(UUID pollId) {
        return TestFixtures.poll(
                pollId,
                ADMIN_SECRET,
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), OPTION_DATE)),
                List.of()
        );
    }

    private static final class FixedPollRepository implements PollRepository {
        private final Poll poll;

        private FixedPollRepository(Poll poll) {
            this.poll = poll;
        }

        @Override
        public void save(Poll poll) {
        }

        @Override
        public Optional<Poll> findById(UUID id) {
            return Optional.ofNullable(poll);
        }

        @Override
        public long countActivePolls() {
            return poll == null ? 0L : 1L;
        }
    }
}
