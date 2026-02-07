package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class PollViewController {

    private final ReadPollUseCase readPollUseCase;

    public PollViewController(ReadPollUseCase readPollUseCase) {
        this.readPollUseCase = readPollUseCase;
    }

    @GetMapping("/poll/{pollId:[0-9a-fA-F\\-]{36}}")
    public String viewPoll(@PathVariable UUID pollId, Model model) {
        Poll poll = readPollUseCase.getPublic(pollId);
        model.addAttribute("poll", poll);
        model.addAttribute("adminView", false);
        model.addAttribute("pollId", pollId);
        applyParticipantView(model, poll);
        return "poll/view";
    }

    @GetMapping("/poll/{pollId:[0-9a-fA-F\\-]{36}}-{adminSecret}")
    public String viewPollAdmin(@PathVariable UUID pollId, @PathVariable String adminSecret, Model model) {
        Poll poll = readPollUseCase.getAdmin(pollId, adminSecret);
        model.addAttribute("poll", poll);
        model.addAttribute("adminView", true);
        model.addAttribute("pollId", pollId);
        model.addAttribute("adminSecret", adminSecret);
        return "poll/view";
    }

    @GetMapping("/poll/{pollId:[0-9a-fA-F\\-]{36}}/responses/{responseId:[0-9a-fA-F\\-]{36}}/edit")
    public String editResponseRow(@PathVariable UUID pollId, @PathVariable UUID responseId, Model model) {
        Poll poll = readPollUseCase.getPublic(pollId);
        PollResponse response = poll.responses().stream()
                .filter(candidate -> candidate.responseId().equals(responseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Response not found"));

        List<PollOption> options = poll.options().stream()
                .sorted(Comparator.comparing(PollOption::date)
                        .thenComparing(PollOption::startTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        model.addAttribute("pollId", pollId);
        model.addAttribute("response", response);
        model.addAttribute("voteOptions", options);
        model.addAttribute("voteOptionHeaders", buildOptionHeaders(options));
        model.addAttribute("editRow", new EditableRow(
                response.responseId(),
                response.participantName(),
                buildVoteCells(options, response.votes())
        ));
        return "poll/participant-row-edit :: row";
    }

    private void applyParticipantView(Model model, Poll poll) {
        List<PollOption> options = poll.options().stream()
                .sorted(Comparator.comparing(PollOption::date)
                        .thenComparing(PollOption::startTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        List<LocalDate> dates = options.stream().map(PollOption::date).toList();
        model.addAttribute("voteOptionHeaders", buildOptionHeaders(options));
        model.addAttribute("voteOptions", options);
        model.addAttribute("monthGroups", buildMonthGroups(dates));
        model.addAttribute("participantRows", buildParticipantRows(options, poll.responses()));
        model.addAttribute("summaryCells", buildSummaryCells(options, poll.responses()));
    }

    private List<MonthGroup> buildMonthGroups(List<LocalDate> dates) {
        List<MonthGroup> groups = new ArrayList<>();
        YearMonth current = null;
        int startIndex = 0;
        for (int i = 0; i < dates.size(); i++) {
            YearMonth month = YearMonth.from(dates.get(i));
            if (current == null) {
                current = month;
                startIndex = i;
                continue;
            }
            if (!month.equals(current)) {
                groups.add(new MonthGroup(labelFor(current), startIndex, i - startIndex));
                current = month;
                startIndex = i;
            }
        }
        if (current != null) {
            groups.add(new MonthGroup(labelFor(current), startIndex, dates.size() - startIndex));
        }
        return groups;
    }

    private String labelFor(YearMonth month) {
        String name = month.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN);
        return name.substring(0, 1).toUpperCase(Locale.GERMAN) + name.substring(1) + " " + month.getYear();
    }

    private List<OptionHeader> buildOptionHeaders(List<PollOption> options) {
        return options.stream()
                .map(option -> new OptionHeader(option, formatOptionLabel(option)))
                .toList();
    }

    private String formatOptionLabel(PollOption option) {
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("EEE, dd.MM.", Locale.GERMAN);
        String dayLabel = option.date().format(dayFormatter);
        LocalTime startTime = option.startTime();
        if (startTime == null) {
            return dayLabel;
        }
        return dayLabel + " " + startTime;
    }

    private List<ParticipantRow> buildParticipantRows(List<PollOption> options, List<PollResponse> responses) {
        return responses.stream()
                .map(response -> new ParticipantRow(
                        response.responseId(),
                        response.participantName(),
                        buildVoteCells(options, response.votes())
                ))
                .toList();
    }

    private List<VoteCell> buildVoteCells(List<PollOption> options, List<PollVote> votes) {
        Map<UUID, PollVoteValue> byOptionId = votes.stream()
                .collect(Collectors.toMap(PollVote::optionId, PollVote::value));
        List<VoteCell> cells = new ArrayList<>();
        for (PollOption option : options) {
            PollVoteValue value = byOptionId.get(option.optionId());
            cells.add(new VoteCell(option.optionId(), value, symbolFor(value)));
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

    record MonthGroup(String label, int startIndex, int span) {
    }

    record OptionHeader(PollOption option, String label) {
    }

    record ParticipantRow(UUID responseId, String name, List<VoteCell> cells) {
    }

    record EditableRow(UUID responseId, String name, List<VoteCell> cells) {
    }

    record VoteCell(UUID optionId, PollVoteValue value, String symbol) {
    }

    record SummaryCell(UUID optionId, int count, boolean best) {
    }
}
