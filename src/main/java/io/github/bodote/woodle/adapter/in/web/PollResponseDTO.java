package io.github.bodote.woodle.adapter.in.web;

import java.time.LocalDate;
import java.util.List;

public record PollResponseDTO(
        String id,
        String title,
        String description,
        String eventType,
        Integer durationMinutes,
        List<PollOptionResponseDTO> options,
        LocalDate expiresAt
) {
}
