package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.application.port.in.SubmitVoteUseCase;
import io.github.bodote.woodle.application.port.in.command.SubmitVoteCommand;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class PollVoteController {

    private final SubmitVoteUseCase submitVoteUseCase;
    private final ReadPollUseCase readPollUseCase;

    public PollVoteController(SubmitVoteUseCase submitVoteUseCase, ReadPollUseCase readPollUseCase) {
        this.submitVoteUseCase = submitVoteUseCase;
        this.readPollUseCase = readPollUseCase;
    }

    @PostMapping("/poll/{pollId}/vote")
    public String submitVote(
            @PathVariable UUID pollId,
            @RequestParam("participantName") String participantName,
            @RequestParam(value = "comment", required = false) String comment,
            @RequestParam(value = "responseId", required = false) UUID responseId,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model,
            @RequestParam Map<String, String> params
    ) {
        List<PollVote> votes = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String optionId = extractOptionId(key);
            if (optionId == null) {
                continue;
            }
            PollVoteValue value = PollVoteValue.valueOf(entry.getValue());
            votes.add(new PollVote(UUID.fromString(optionId), value));
        }
        submitVoteUseCase.submit(new SubmitVoteCommand(pollId, participantName, votes, comment, responseId));
        if ("true".equalsIgnoreCase(hxRequest) && responseId != null) {
            Poll poll = readPollUseCase.getPublic(pollId);
            PollResponse response = poll.responses().stream()
                    .filter(candidate -> candidate.responseId().equals(responseId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Response not found"));
            List<PollOption> options = poll.options().stream()
                    .sorted(Comparator.comparing(PollOption::date)
                            .thenComparing(PollOption::startTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
            model.addAttribute("pollId", pollId);
            model.addAttribute("row", new ParticipantRow(
                    response.responseId(),
                    response.participantName(),
                    buildVoteCells(options, response.votes())
            ));
            model.addAttribute("summaryCells", buildSummaryCells(options, poll.responses()));
            return "poll/participant-row-update :: payload";
        }
        return "redirect:/poll/" + pollId;
    }

    private String extractOptionId(String key) {
        if (key.startsWith("vote_new_")) {
            return key.substring("vote_new_".length());
        }
        if (key.startsWith("vote_edit_")) {
            return key.substring("vote_edit_".length());
        }
        if (key.startsWith("vote_")) {
            return key.substring("vote_".length());
        }
        return null;
    }

    private List<VoteCell> buildVoteCells(List<PollOption> options, List<PollVote> votes) {
        Map<UUID, PollVoteValue> byOptionId = votes.stream()
                .collect(Collectors.toMap(PollVote::optionId, PollVote::value));
        List<VoteCell> cells = new ArrayList<>();
        for (PollOption option : options) {
            PollVoteValue value = byOptionId.get(option.optionId());
            cells.add(new VoteCell(option.optionId(), value, symbolFor(value), markerClassFor(value)));
        }
        return cells;
    }

    private List<SummaryCell> buildSummaryCells(List<PollOption> options, List<PollResponse> responses) {
        Map<UUID, Long> yesCounts = responses.stream()
                .flatMap(response -> response.votes().stream())
                .filter(vote -> vote.value() == PollVoteValue.YES)
                .collect(Collectors.groupingBy(PollVote::optionId, Collectors.counting()));
        long max = yesCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<SummaryCell> cells = new ArrayList<>();
        for (PollOption option : options) {
            long count = yesCounts.getOrDefault(option.optionId(), 0L);
            cells.add(new SummaryCell(option.optionId(), (int) count, count == max && count > 0));
        }
        return cells;
    }

    private String symbolFor(PollVoteValue value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case YES -> "✓";
            case IF_NEEDED -> "(✓)";
            case NO -> "✗";
        };
    }

    private String markerClassFor(PollVoteValue value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case YES -> "votes-table__marker--yes";
            case IF_NEEDED -> "votes-table__marker--if-needed";
            case NO -> "votes-table__marker--no";
        };
    }

    record ParticipantRow(UUID responseId, String name, List<VoteCell> cells) {
        public UUID getResponseId() {
            return responseId;
        }

        public String getName() {
            return name;
        }

        public List<VoteCell> getCells() {
            return cells;
        }
    }

    record VoteCell(UUID optionId, PollVoteValue value, String symbol, String markerClass) {
        public UUID getOptionId() {
            return optionId;
        }

        public PollVoteValue getValue() {
            return value;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getMarkerClass() {
            return markerClass;
        }
    }

    record SummaryCell(UUID optionId, int count, boolean best) {
        public UUID getOptionId() {
            return optionId;
        }

        public int getCount() {
            return count;
        }

        public boolean isBest() {
            return best;
        }
    }
}
