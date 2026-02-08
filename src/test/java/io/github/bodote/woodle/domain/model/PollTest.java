package io.github.bodote.woodle.domain.model;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Poll")
class PollTest {

    @Test
    @DisplayName("adds a response")
    void addsAResponse() {
        PollOption option = TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10));
        Poll poll = TestFixtures.poll(
                UUID.randomUUID(),
                "secret",
                EventType.ALL_DAY,
                null,
                List.of(option),
                List.of()
        );

        PollResponse response = TestFixtures.response(
                UUID.randomUUID(),
                "Alice",
                List.of(new PollVote(option.optionId(), PollVoteValue.YES))
        );

        Poll updated = poll.addResponse(response);

        assertEquals(1, updated.responses().size());
    }

    @Test
    @DisplayName("replaces options")
    void replacesOptions() {
        Poll poll = TestFixtures.poll(
                UUID.randomUUID(),
                "secret",
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );

        List<PollOption> newOptions = List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 11)));
        Poll updated = poll.withOptions(newOptions);

        assertEquals(1, updated.options().size());
        assertEquals(LocalDate.of(2026, 2, 11), updated.options().get(0).date());
    }
}
