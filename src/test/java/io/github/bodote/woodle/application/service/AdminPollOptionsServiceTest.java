package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.application.service.AdminPollOptionsService;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("AdminPollOptionsService")
class AdminPollOptionsServiceTest {

    @Test
    @DisplayName("adds date option")
    void addsDateOption() {
        UUID pollId = UUID.randomUUID();
        Poll poll = TestFixtures.poll(
                pollId,
                TestFixtures.ADMIN_SECRET,
                EventType.ALL_DAY,
                null,
                List.of(),
                List.of()
        );

        CapturingRepo repo = new CapturingRepo(poll);
        AdminPollOptionsService service = new AdminPollOptionsService(repo);

        service.addDate(pollId, TestFixtures.ADMIN_SECRET, LocalDate.of(2026, 2, 12));

        assertEquals(1, repo.saved.options().size());
    }

    @Test
    @DisplayName("removes date option")
    void removesDateOption() {
        UUID pollId = UUID.randomUUID();
        PollOption option = TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10));
        Poll poll = TestFixtures.poll(
                pollId,
                TestFixtures.ADMIN_SECRET,
                EventType.ALL_DAY,
                null,
                List.of(option),
                List.of()
        );

        CapturingRepo repo = new CapturingRepo(poll);
        AdminPollOptionsService service = new AdminPollOptionsService(repo);

        service.removeOption(pollId, TestFixtures.ADMIN_SECRET, LocalDate.of(2026, 2, 10), null);

        assertEquals(0, repo.saved.options().size());
    }

    @Test
    @DisplayName("removes intraday option by date and time")
    void removesIntradayOptionByDateAndTime() {
        UUID pollId = UUID.randomUUID();
        PollOption option = TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10), LocalTime.of(9, 0), LocalTime.of(10, 30));
        Poll poll = TestFixtures.poll(
                pollId,
                TestFixtures.ADMIN_SECRET,
                EventType.INTRADAY,
                90,
                List.of(option),
                List.of()
        );

        CapturingRepo repo = new CapturingRepo(poll);
        AdminPollOptionsService service = new AdminPollOptionsService(repo);

        service.removeOption(pollId, TestFixtures.ADMIN_SECRET, LocalDate.of(2026, 2, 10), LocalTime.of(9, 0));

        assertEquals(0, repo.saved.options().size());
    }

    @Test
    @DisplayName("does not remove intraday option when start time does not match")
    void doesNotRemoveIntradayOptionWhenStartTimeDoesNotMatch() {
        UUID pollId = UUID.randomUUID();
        PollOption option = TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10), LocalTime.of(9, 0), LocalTime.of(10, 30));
        Poll poll = TestFixtures.poll(
                pollId,
                TestFixtures.ADMIN_SECRET,
                EventType.INTRADAY,
                90,
                List.of(option),
                List.of()
        );

        CapturingRepo repo = new CapturingRepo(poll);
        AdminPollOptionsService service = new AdminPollOptionsService(repo);

        service.removeOption(pollId, TestFixtures.ADMIN_SECRET, LocalDate.of(2026, 2, 10), LocalTime.of(9, 15));

        assertEquals(1, repo.saved.options().size());
    }

    @Test
    @DisplayName("keeps timed option when no start time is provided and date matches")
    void keepsTimedOptionWhenNoStartTimeIsProvidedAndDateMatches() {
        UUID pollId = UUID.randomUUID();
        PollOption differentDate = TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 9), LocalTime.of(8, 0), LocalTime.of(9, 30));
        PollOption sameDateTimed = TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10), LocalTime.of(9, 0), LocalTime.of(10, 30));
        Poll poll = TestFixtures.poll(
                pollId,
                TestFixtures.ADMIN_SECRET,
                EventType.INTRADAY,
                90,
                List.of(differentDate, sameDateTimed),
                List.of()
        );

        CapturingRepo repo = new CapturingRepo(poll);
        AdminPollOptionsService service = new AdminPollOptionsService(repo);

        service.removeOption(pollId, TestFixtures.ADMIN_SECRET, LocalDate.of(2026, 2, 10), null);

        assertEquals(2, repo.saved.options().size());
    }

    @Test
    @DisplayName("throws when poll is missing")
    void throwsWhenPollIsMissing() {
        UUID pollId = UUID.randomUUID();
        CapturingRepo repo = new CapturingRepo(null);
        AdminPollOptionsService service = new AdminPollOptionsService(repo);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addDate(pollId, TestFixtures.ADMIN_SECRET, LocalDate.of(2026, 2, 10))
        );

        assertEquals("Poll not found", exception.getMessage());
        assertNull(repo.saved);
    }

    @Test
    @DisplayName("throws when admin secret is invalid")
    void throwsWhenAdminSecretIsInvalid() {
        UUID pollId = UUID.randomUUID();
        Poll poll = TestFixtures.poll(
                pollId,
                TestFixtures.ADMIN_SECRET,
                EventType.ALL_DAY,
                null,
                List.of(),
                List.of()
        );
        CapturingRepo repo = new CapturingRepo(poll);
        AdminPollOptionsService service = new AdminPollOptionsService(repo);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addDate(pollId, "wrong-secret", LocalDate.of(2026, 2, 10))
        );

        assertEquals("Invalid admin secret", exception.getMessage());
        assertNull(repo.saved);
    }

    private static final class CapturingRepo implements PollRepository {
        private Poll saved;
        private final Poll existing;

        private CapturingRepo(Poll existing) {
            this.existing = existing;
        }

        @Override
        public void save(Poll poll) {
            this.saved = poll;
        }

        @Override
        public Optional<Poll> findById(UUID pollId) {
            return Optional.ofNullable(existing);
        }

        @Override
        public long countActivePolls() {
            return existing == null ? 0L : 1L;
        }
    }
}
