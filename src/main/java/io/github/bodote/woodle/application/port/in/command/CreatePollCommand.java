package io.github.bodote.woodle.application.port.in.command;

import io.github.bodote.woodle.domain.model.EventType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

public record CreatePollCommand(
        String authorName,
        String authorEmail,
        String title,
        String description,
        EventType eventType,
        Integer durationMinutes,
        List<LocalDate> dates,
        List<LocalTime> startTimes,
        LocalDate expiresAtOverride
) {
    public CreatePollCommand {
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(authorEmail, "authorEmail");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(dates, "dates");
        Objects.requireNonNull(startTimes, "startTimes");
    }
}
