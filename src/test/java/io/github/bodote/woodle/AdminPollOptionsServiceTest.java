package io.github.bodote.woodle;

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
            return Optional.of(existing);
        }
    }
}
