package io.github.bodote.woodle.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PollOption(UUID optionId, LocalDate date, LocalTime startTime, LocalTime endTime) {
}
