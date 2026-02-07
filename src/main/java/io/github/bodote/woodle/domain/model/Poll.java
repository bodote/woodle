package io.github.bodote.woodle.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Poll(
        UUID pollId,
        String adminSecret,
        String title,
        String description,
        String authorName,
        String authorEmail,
        EventType eventType,
        Integer durationMinutes,
        List<PollOption> options,
        List<PollResponse> responses,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        LocalDate expiresAt
) {
    public Poll addResponse(PollResponse response) {
        List<PollResponse> updated = new java.util.ArrayList<>(responses);
        updated.add(response);
        return new Poll(pollId, adminSecret, title, description, authorName, authorEmail, eventType,
                durationMinutes, options, java.util.List.copyOf(updated), createdAt, updatedAt, expiresAt);
    }

    public Poll replaceResponse(PollResponse response) {
        List<PollResponse> updated = new java.util.ArrayList<>(responses);
        boolean replaced = false;
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).responseId().equals(response.responseId())) {
                updated.set(i, response);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            updated.add(response);
        }
        return new Poll(pollId, adminSecret, title, description, authorName, authorEmail, eventType,
                durationMinutes, options, java.util.List.copyOf(updated), createdAt, updatedAt, expiresAt);
    }

    public Poll withOptions(List<PollOption> newOptions) {
        return new Poll(pollId, adminSecret, title, description, authorName, authorEmail, eventType,
                durationMinutes, List.copyOf(newOptions), responses, createdAt, updatedAt, expiresAt);
    }
}
