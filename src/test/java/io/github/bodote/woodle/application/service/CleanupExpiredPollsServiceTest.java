package io.github.bodote.woodle.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.bodote.woodle.application.port.out.PollRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CleanupExpiredPollsService")
class CleanupExpiredPollsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 21);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Test
    @DisplayName("deletes every expired poll and returns the count")
    void deletesEveryExpiredPollAndReturnsCount() {
        PollRepository pollRepository = mock(PollRepository.class);
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(pollRepository.findExpiredPollIds(TODAY)).thenReturn(List.of(first, second));
        CleanupExpiredPollsService service = new CleanupExpiredPollsService(pollRepository, FIXED_CLOCK);

        int deleted = service.cleanupExpiredPolls();

        assertEquals(2, deleted);
        verify(pollRepository).findExpiredPollIds(TODAY);
        verify(pollRepository).deleteById(first);
        verify(pollRepository).deleteById(second);
    }

    @Test
    @DisplayName("deletes nothing when no poll is expired")
    void deletesNothingWhenNoPollExpired() {
        PollRepository pollRepository = mock(PollRepository.class);
        when(pollRepository.findExpiredPollIds(TODAY)).thenReturn(List.of());
        CleanupExpiredPollsService service = new CleanupExpiredPollsService(pollRepository, FIXED_CLOCK);

        int deleted = service.cleanupExpiredPolls();

        assertEquals(0, deleted);
        verify(pollRepository, never()).deleteById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("returns the count of successfully deleted polls when one delete fails")
    void returnsSuccessfulDeleteCountWhenOneFails() {
        PollRepository pollRepository = mock(PollRepository.class);
        UUID ok = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID broken = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(pollRepository.findExpiredPollIds(TODAY)).thenReturn(List.of(ok, broken));
        doThrow(new IllegalStateException("S3 down")).when(pollRepository).deleteById(broken);
        CleanupExpiredPollsService service = new CleanupExpiredPollsService(pollRepository, FIXED_CLOCK);

        int deleted = service.cleanupExpiredPolls();

        assertEquals(1, deleted);
    }

    @Test
    @DisplayName("logs started, found and deleted events so the run is observable")
    void logsObservableCleanupEvents() {
        PollRepository pollRepository = mock(PollRepository.class);
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(pollRepository.findExpiredPollIds(TODAY)).thenReturn(List.of(first));
        CleanupExpiredPollsService service = new CleanupExpiredPollsService(pollRepository, FIXED_CLOCK);

        Logger logger = (Logger) LoggerFactory.getLogger(CleanupExpiredPollsService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.cleanupExpiredPolls();
        } finally {
            logger.detachAppender(appender);
        }

        List<String> messages = appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertTrue(messages.stream().anyMatch(m -> m.equals("POLL_CLEANUP started asOf=2026-06-21")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.equals("POLL_CLEANUP found 1 expired poll(s) asOf=2026-06-21")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.equals("POLL_CLEANUP deleted 1 of 1 expired poll(s) asOf=2026-06-21")), messages.toString());
    }
}
