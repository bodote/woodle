package io.github.bodote.woodle.adapter.in.web;

public record CreatePollResponseDTO(
        String id,
        String adminUrl,
        String voteUrl
) {
}
