package io.github.bodote.woodle.adapter.in.web;

import java.time.LocalDate;

public record PollOptionResponseDTO(
        String optionId,
        LocalDate date,
        String startTime,
        String endTime
) {
}
