package io.github.bodote.woodle.adapter.out.persistence;

import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InMemoryPollRepository")
class InMemoryPollRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 21);

    @Test
    @DisplayName("findExpiredPollIds returns only polls whose expiresAt is strictly before the cutoff")
    void findExpiredPollIdsReturnsOnlyExpired() {
        InMemoryPollRepository repository = new InMemoryPollRepository();
        UUID expired = id(1);
        UUID expiresToday = id(2);
        UUID future = id(3);
        UUID noExpiry = id(4);
        repository.save(poll(expired, TODAY.minusDays(1)));
        repository.save(poll(expiresToday, TODAY));
        repository.save(poll(future, TODAY.plusDays(1)));
        repository.save(poll(noExpiry, null));

        List<UUID> result = repository.findExpiredPollIds(TODAY);

        assertEquals(List.of(expired), result);
    }

    @Test
    @DisplayName("deleteById removes the poll")
    void deleteByIdRemovesPoll() {
        InMemoryPollRepository repository = new InMemoryPollRepository();
        UUID pollId = id(1);
        repository.save(poll(pollId, TODAY.minusDays(1)));
        assertTrue(repository.findById(pollId).isPresent());

        repository.deleteById(pollId);

        assertFalse(repository.findById(pollId).isPresent());
    }

    private static UUID id(int last) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + last);
    }

    private static Poll poll(UUID pollId, LocalDate expiresAt) {
        OffsetDateTime now = OffsetDateTime.parse("2026-02-01T10:00:00Z");
        return new Poll(pollId, "AdminSecret12", "Title", "Desc", "Max", "max@invalid",
                EventType.ALL_DAY, null, List.of(), List.of(), now, now, expiresAt, false);
    }
}
