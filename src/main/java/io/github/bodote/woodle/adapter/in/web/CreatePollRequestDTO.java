package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.domain.model.EventType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

public record CreatePollRequestDTO(
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
    public CreatePollRequestDTO {
        Objects.requireNonNull(authorName, "authorName");
        Objects.requireNonNull(authorEmail, "authorEmail");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(dates, "dates");
    }
}
