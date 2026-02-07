package io.github.bodote.woodle;

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

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ReadPollService")
class ReadPollServiceTest {

    @Test
    @DisplayName("rejects invalid admin secret")
    void rejectsInvalidAdminSecret() {
        UUID pollId = UUID.randomUUID();
        Poll poll = TestFixtures.poll(
                pollId,
                "secret",
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );

        ReadPollService service = new ReadPollService(new PollRepository() {
            @Override
            public void save(Poll poll) {
            }

            @Override
            public Optional<Poll> findById(UUID id) {
                return Optional.of(poll);
            }
        });

        assertThrows(IllegalArgumentException.class, () -> service.getAdmin(pollId, "wrong"));
    }
}
