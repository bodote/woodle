package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import io.github.bodote.woodle.application.port.out.PollCreatedEmail;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.application.port.out.PollRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CreatePollService")
class CreatePollServiceTest {

    private static final String AUTHOR_NAME = "Max";
    private static final String AUTHOR_EMAIL = "max@invalid";
    private static final String TITLE = "Test";
    private static final String DESCRIPTION = "Desc";
    private static final LocalDate DATE_ONE = LocalDate.of(2026, 2, 10);
    private static final LocalDate DATE_TWO = LocalDate.of(2026, 2, 20);

    @Test
    @DisplayName("uses expiresAt override when provided")
    void usesExpiresAtOverrideWhenProvided() {
        CapturingPollRepository repository = new CapturingPollRepository();
        CapturingPollEmailSender pollEmailSender = new CapturingPollEmailSender();
        CreatePollService service = new CreatePollService(repository, pollEmailSender, true);

        LocalDate override = LocalDate.of(2026, 3, 1);
        CreatePollCommand command = new CreatePollCommand(
                AUTHOR_NAME,
                AUTHOR_EMAIL,
                TITLE,
                DESCRIPTION,
                EventType.ALL_DAY,
                null,
                List.of(DATE_ONE),
                List.of(),
                override
        );

        service.create(command);

        Poll saved = repository.saved;
        assertNotNull(saved);
        assertEquals(override, saved.expiresAt());
        assertEquals(0, saved.responses().size());
        assertNotNull(pollEmailSender.lastEmail);
        assertTrue(pollEmailSender.lastResult);
    }

    @Test
    @DisplayName("derives expiresAt from latest option date when no override is provided")
    void derivesExpiresAtFromLatestOptionDateWhenNoOverrideIsProvided() {
        CapturingPollRepository repository = new CapturingPollRepository();
        CreatePollService service = new CreatePollService(repository, new CapturingPollEmailSender(), true);

        CreatePollCommand command = new CreatePollCommand(
                AUTHOR_NAME,
                AUTHOR_EMAIL,
                TITLE,
                DESCRIPTION,
                EventType.ALL_DAY,
                null,
                List.of(DATE_ONE, DATE_TWO),
                List.of(),
                null
        );

        service.create(command);

        Poll saved = repository.saved;
        assertNotNull(saved);
        assertEquals(LocalDate.of(2026, 3, 20), saved.expiresAt());
    }

    @Test
    @DisplayName("creates options with null start and end time when start time is missing")
    void createsOptionsWithNullStartAndEndTimeWhenStartTimeIsMissing() {
        CapturingPollRepository repository = new CapturingPollRepository();
        CreatePollService service = new CreatePollService(repository, new CapturingPollEmailSender(), true);

        CreatePollCommand command = new CreatePollCommand(
                AUTHOR_NAME,
                AUTHOR_EMAIL,
                TITLE,
                DESCRIPTION,
                EventType.INTRADAY,
                60,
                List.of(DATE_ONE, DATE_TWO),
                List.of(LocalTime.of(9, 0)),
                null
        );

        service.create(command);

        Poll saved = repository.saved;
        assertNotNull(saved);
        assertEquals(LocalTime.of(9, 0), saved.options().get(0).startTime());
        assertEquals(LocalTime.of(10, 0), saved.options().get(0).endTime());
        assertNull(saved.options().get(1).startTime());
        assertNull(saved.options().get(1).endTime());
    }

    @Test
    @DisplayName("does not calculate end time when duration is missing")
    void doesNotCalculateEndTimeWhenDurationIsMissing() {
        CapturingPollRepository repository = new CapturingPollRepository();
        CapturingPollEmailSender pollEmailSender = new CapturingPollEmailSender();
        CreatePollService service = new CreatePollService(repository, pollEmailSender, true);

        CreatePollCommand command = new CreatePollCommand(
                AUTHOR_NAME,
                AUTHOR_EMAIL,
                TITLE,
                DESCRIPTION,
                EventType.INTRADAY,
                null,
                List.of(DATE_ONE),
                List.of(LocalTime.of(14, 30)),
                null
        );

        var result = service.create(command);

        Poll saved = repository.saved;
        assertNotNull(saved);
        assertNotNull(result);
        assertNotNull(saved.adminSecret());
        assertEquals(12, saved.adminSecret().length());
        assertTrue(saved.adminSecret().matches("[0-9A-Za-z]{12}"));
        assertEquals(LocalTime.of(14, 30), saved.options().getFirst().startTime());
        assertNull(saved.options().getFirst().endTime());
        assertTrue(result.notificationQueued());
        assertFalse(result.notificationDisabled());
        assertNotNull(pollEmailSender.lastEmail);
        assertEquals(saved.pollId(), pollEmailSender.lastEmail.pollId());
        assertEquals(saved.adminSecret(), pollEmailSender.lastEmail.adminSecret());
    }

    @Test
    @DisplayName("marks notification as disabled when email sending is disabled")
    void marksNotificationAsDisabledWhenEmailSendingIsDisabled() {
        CapturingPollRepository repository = new CapturingPollRepository();
        CapturingPollEmailSender pollEmailSender = new CapturingPollEmailSender();
        CreatePollService service = new CreatePollService(repository, pollEmailSender, false);

        CreatePollCommand command = new CreatePollCommand(
                AUTHOR_NAME,
                AUTHOR_EMAIL,
                TITLE,
                DESCRIPTION,
                EventType.ALL_DAY,
                null,
                List.of(DATE_ONE),
                List.of(),
                null
        );

        var result = service.create(command);

        assertFalse(result.notificationQueued());
        assertTrue(result.notificationDisabled());
        assertNull(pollEmailSender.lastEmail);
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

        @Override
        public long countActivePolls() {
            return 0L;
        }
    }

    private static final class CapturingPollEmailSender implements PollEmailSender {
        private PollCreatedEmail lastEmail;
        private boolean lastResult;

        @Override
        public boolean sendPollCreated(PollCreatedEmail pollCreatedEmail) {
            this.lastEmail = pollCreatedEmail;
            this.lastResult = true;
            return true;
        }
    }
}
