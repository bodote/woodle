package io.github.bodote.woodle.adapter.out.persistence;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PollDAO(
        UUID pollId,
        String schemaVersion,
        String type,
        String title,
        String descriptionHtml,
        String language,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Author author,
        Access access,
        Permissions permissions,
        Notifications notifications,
        ResultsVisibility resultsVisibility,
        String status,
        LocalDate expiresAt,
        Options options,
        List<Response> responses
) {
    public record Author(String name, String email) {
    }

    public record Access(String customSlug, String passwordHash, boolean resultsPublic, String adminToken) {
    }

    public record Permissions(String voteChangePolicy) {
    }

    public record Notifications(boolean onVote, boolean onComment) {
    }

    public record ResultsVisibility(boolean onlyAuthor) {
    }

    public record Options(String eventType, Integer durationMinutes, List<OptionItem> items) {
    }

    public record OptionItem(UUID optionId, LocalDate date, String startTime, String endTime) {
    }

    public record Response(UUID responseId, String participantName, OffsetDateTime createdAt, List<Vote> votes,
                           String comment) {
    }

    public record Vote(UUID optionId, String value) {
    }
}
