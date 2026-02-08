package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import io.github.bodote.woodle.application.service.CreatePollService;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.application.port.out.PollRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("CreatePollService")
class CreatePollServiceTest {

    @Test
    @DisplayName("uses expiresAt override when provided")
    void usesExpiresAtOverrideWhenProvided() {
        CapturingPollRepository repository = new CapturingPollRepository();
        CreatePollService service = new CreatePollService(repository);

        LocalDate override = LocalDate.of(2026, 3, 1);
        CreatePollCommand command = new CreatePollCommand(
                "Max",
                "max@invalid",
                "Test",
                "Desc",
                EventType.ALL_DAY,
                null,
                List.of(LocalDate.of(2026, 2, 10)),
                List.of(),
                override
        );

        service.create(command);

        Poll saved = repository.saved;
        assertNotNull(saved);
        assertEquals(override, saved.expiresAt());
        assertEquals(0, saved.responses().size());
    }

    private static final class CapturingPollRepository implements PollRepository {
        private Poll saved;

        @Override
        public void save(Poll poll) {
            this.saved = poll;
        }

        @Override
        public java.util.Optional<Poll> findById(java.util.UUID pollId) {
            return java.util.Optional.empty();
        }
    }
}
