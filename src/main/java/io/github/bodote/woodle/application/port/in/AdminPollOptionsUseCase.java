package io.github.bodote.woodle.application.port.in;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface AdminPollOptionsUseCase {

    void addDate(UUID pollId, String adminSecret, LocalDate date);

    void removeOption(UUID pollId, String adminSecret, LocalDate date, LocalTime startTime);
}
