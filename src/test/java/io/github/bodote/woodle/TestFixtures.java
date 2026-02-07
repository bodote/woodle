package io.github.bodote.woodle;

import io.github.bodote.woodle.adapter.in.web.WizardState;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

final class TestFixtures {

    static final String AUTHOR_NAME = "Max";
    static final String AUTHOR_EMAIL = "max@invalid";
    static final String TITLE = "Team Meeting";
    static final String DESCRIPTION = "Desc";
    static final String ADMIN_SECRET = "AdminSecret12";
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-02-01T10:00:00Z");
    static final LocalDate EXPIRES_AT = LocalDate.of(2026, 3, 10);

    private TestFixtures() {
    }

    static PollOption option(UUID optionId, LocalDate date) {
        return new PollOption(optionId, date, null, null);
    }

    static PollOption option(UUID optionId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        return new PollOption(optionId, date, startTime, endTime);
    }

    static PollResponse response(UUID responseId, String name, List<PollVote> votes) {
        return new PollResponse(responseId, name, NOW, votes, null);
    }

    static Poll poll(UUID pollId, List<PollOption> options, List<PollResponse> responses) {
        return new Poll(
                pollId,
                ADMIN_SECRET,
                TITLE,
                DESCRIPTION,
                AUTHOR_NAME,
                AUTHOR_EMAIL,
                EventType.ALL_DAY,
                null,
                options,
                responses,
                NOW,
                NOW,
                EXPIRES_AT
        );
    }

    static Poll poll(UUID pollId, String adminSecret, EventType eventType, Integer durationMinutes,
                     List<PollOption> options, List<PollResponse> responses) {
        return new Poll(
                pollId,
                adminSecret,
                TITLE,
                DESCRIPTION,
                AUTHOR_NAME,
                AUTHOR_EMAIL,
                eventType,
                durationMinutes,
                options,
                responses,
                NOW,
                NOW,
                EXPIRES_AT
        );
    }

    static WizardState wizardStateBasics() {
        WizardState state = new WizardState();
        state.setAuthorName(AUTHOR_NAME);
        state.setAuthorEmail(AUTHOR_EMAIL);
        state.setTitle("Test");
        state.setDescription(DESCRIPTION);
        return state;
    }

    static WizardState wizardStateWithDates(EventType eventType, List<LocalDate> dates) {
        WizardState state = wizardStateBasics();
        state.setEventType(eventType);
        state.setDates(dates);
        return state;
    }

    static WizardState wizardStateIntraday(List<LocalDate> dates, List<LocalTime> times, int durationMinutes) {
        WizardState state = wizardStateWithDates(EventType.INTRADAY, dates);
        state.setDurationMinutes(durationMinutes);
        state.setStartTimes(times);
        return state;
    }
}
