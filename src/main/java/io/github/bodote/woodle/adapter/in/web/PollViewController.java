package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

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
    private final String publicBaseUrl;

    public PollViewController(ReadPollUseCase readPollUseCase,
                              @Value("${woodle.public-base-url:}") String publicBaseUrl) {
        this.readPollUseCase = readPollUseCase;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
    }

    @GetMapping("/poll/{pollId:[0-9a-fA-F\\-]{36}}")
    public String viewPoll(@PathVariable UUID pollId, Model model) {
        Poll poll = loadPublicPoll(pollId);
        applyParticipantModel(model, pollId, poll);
        return "poll/view";
    }

    @GetMapping("/poll/dynamic/{pollId:[0-9a-fA-F\\-]{36}}")
    public String viewPollDynamic(@PathVariable UUID pollId, Model model) {
        Poll poll = loadPublicPoll(pollId);
        applyParticipantModel(model, pollId, poll);
        return "poll/view";
    }

    @GetMapping("/poll/{pollId:[0-9a-fA-F\\-]{36}}-{adminSecret}")
    public String viewPollAdmin(@PathVariable UUID pollId,
                                @PathVariable String adminSecret,
                                @RequestParam(value = "emailFailed", defaultValue = "false") boolean emailFailed,
                                @RequestParam(value = "emailDisabled", defaultValue = "false") boolean emailDisabled,
                                Model model,
                                HttpServletRequest request) {
        Poll poll = loadAdminPoll(pollId, adminSecret);
        String origin = resolveOrigin(request);
        applyAdminModel(model, pollId, adminSecret, poll, origin);
        model.addAttribute("emailFailed", emailFailed);
        model.addAttribute("emailDisabled", emailDisabled);
        return "poll/view";
    }

    @GetMapping("/poll/dynamic/{pollId:[0-9a-fA-F\\-]{36}}-{adminSecret}")
    public String viewPollAdminDynamic(@PathVariable UUID pollId,
                                       @PathVariable String adminSecret,
                                       @RequestParam(value = "emailFailed", defaultValue = "false") boolean emailFailed,
                                       @RequestParam(value = "emailDisabled", defaultValue = "false") boolean emailDisabled,
                                       Model model,
                                       HttpServletRequest request) {
        return viewPollAdmin(pollId, adminSecret, emailFailed, emailDisabled, model, request);
    }

    @GetMapping("/poll/static/{pollId:[0-9a-fA-F\\-]{36}}")
    public String staticPollLoader(@PathVariable UUID pollId, Model model) {
        model.addAttribute("dynamicPollPath", "/poll/dynamic/" + pollId);
        model.addAttribute("readyPath", "/poll/dynamic/" + pollId + "/ready");
        model.addAttribute("fragmentPath", "/poll/dynamic/" + pollId + "/fragment");
        return "poll/static-loader";
    }

    @GetMapping("/poll/static/{pollId:[0-9a-fA-F\\-]{36}}-{adminSecret}")
    public String staticAdminPollLoader(@PathVariable UUID pollId,
                                        @PathVariable String adminSecret,
                                        @RequestParam(value = "emailFailed", defaultValue = "false") boolean emailFailed,
                                        @RequestParam(value = "emailDisabled", defaultValue = "false") boolean emailDisabled,
                                        Model model) {
        String basePath = "/poll/dynamic/" + pollId + "-" + adminSecret;
        String query = buildEmailQuery(emailFailed, emailDisabled);
        model.addAttribute("dynamicPollPath", basePath);
        model.addAttribute("readyPath", basePath + "/ready");
        model.addAttribute("fragmentPath", basePath + "/fragment" + query);
        model.addAttribute("emailFailed", emailFailed);
        model.addAttribute("emailDisabled", emailDisabled);
        return "poll/static-loader";
    }

    @GetMapping("/poll/dynamic/{pollId:[0-9a-fA-F\\-]{36}}/ready")
    @ResponseBody
    public ResponseEntity<String> participantReady(@PathVariable UUID pollId) {
        try {
            readPollUseCase.getPublic(pollId);
            return ResponseEntity.ok("ready");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/poll/dynamic/{pollId:[0-9a-fA-F\\-]{36}}-{adminSecret}/ready")
    @ResponseBody
    public ResponseEntity<String> adminReady(@PathVariable UUID pollId, @PathVariable String adminSecret) {
        try {
            readPollUseCase.getAdmin(pollId, adminSecret);
            return ResponseEntity.ok("ready");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/poll/dynamic/{pollId:[0-9a-fA-F\\-]{36}}/fragment")
    public String participantFragment(@PathVariable UUID pollId, Model model) {
        Poll poll = loadPublicPoll(pollId);
        applyParticipantModel(model, pollId, poll);
        return "poll/view :: pollContent";
    }

    @GetMapping("/poll/dynamic/{pollId:[0-9a-fA-F\\-]{36}}-{adminSecret}/fragment")
    public String adminFragment(@PathVariable UUID pollId,
                                @PathVariable String adminSecret,
                                @RequestParam(value = "emailFailed", defaultValue = "false") boolean emailFailed,
                                @RequestParam(value = "emailDisabled", defaultValue = "false") boolean emailDisabled,
                                Model model,
                                HttpServletRequest request) {
        Poll poll = loadAdminPoll(pollId, adminSecret);
        String origin = resolveOrigin(request);
        applyAdminModel(model, pollId, adminSecret, poll, origin);
        model.addAttribute("emailFailed", emailFailed);
        model.addAttribute("emailDisabled", emailDisabled);
        return "poll/view :: pollContent";
    }

    @GetMapping("/poll/{pollId:[0-9a-fA-F\\-]{36}}/responses/{responseId:[0-9a-fA-F\\-]{36}}/edit")
    public String editResponseRow(@PathVariable UUID pollId, @PathVariable UUID responseId, Model model) {
        Poll poll = loadPublicPoll(pollId);
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

    private Poll loadPublicPoll(UUID pollId) {
        try {
            return readPollUseCase.getPublic(pollId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found", exception);
        }
    }

    private Poll loadAdminPoll(UUID pollId, String adminSecret) {
        try {
            return readPollUseCase.getAdmin(pollId, adminSecret);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found", exception);
        }
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
        model.addAttribute("dateGroups", buildDateGroups(options));
        model.addAttribute("participantRows", buildParticipantRows(options, poll.responses()));
        model.addAttribute("summaryCells", buildSummaryCells(options, poll.responses()));
    }

    private void applyParticipantModel(Model model, UUID pollId, Poll poll) {
        model.addAttribute("poll", poll);
        model.addAttribute("adminView", false);
        model.addAttribute("pollId", pollId);
        applyParticipantView(model, poll);
    }

    private void applyAdminModel(Model model, UUID pollId, String adminSecret, Poll poll, String origin) {
        model.addAttribute("poll", poll);
        model.addAttribute("adminView", true);
        model.addAttribute("pollId", pollId);
        model.addAttribute("adminSecret", adminSecret);
        model.addAttribute("participantShareUrl", origin + "/poll/static/" + pollId);
        model.addAttribute("adminShareUrl", origin + "/poll/static/" + pollId + "-" + adminSecret);
    }

    private String buildEmailQuery(boolean emailFailed, boolean emailDisabled) {
        if (emailFailed) {
            return "?emailFailed=true";
        }
        if (emailDisabled) {
            return "?emailDisabled=true";
        }
        return "";
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

    private List<DateGroup> buildDateGroups(List<PollOption> options) {
        List<DateGroup> groups = new ArrayList<>();
        LocalDate currentDate = null;
        int startIndex = 0;
        for (int i = 0; i < options.size(); i++) {
            LocalDate date = options.get(i).date();
            if (currentDate == null) {
                currentDate = date;
                startIndex = i;
                continue;
            }
            if (!date.equals(currentDate)) {
                groups.add(new DateGroup(formatDateGroupLabel(currentDate), startIndex, i - startIndex));
                currentDate = date;
                startIndex = i;
            }
        }
        if (currentDate != null) {
            groups.add(new DateGroup(formatDateGroupLabel(currentDate), startIndex, options.size() - startIndex));
        }
        return groups;
    }

    private String formatDateGroupLabel(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("EEE, dd.MM.", Locale.GERMAN));
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
        return startTime.toString();
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

    private String resolveOrigin(HttpServletRequest request) {
        if (!publicBaseUrl.isBlank()) {
            return publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        }

        String forwarded = firstHeaderValue(request, "Forwarded");

        String forwardedHost = forwardedFieldValue(forwarded, "host");
        String xForwardedHost = firstHeaderValue(request, "X-Forwarded-Host");
        String scheme = firstNonBlank(
                forwardedFieldValue(forwarded, "proto"),
                firstHeaderValue(request, "X-Forwarded-Proto"),
                request.getScheme()
        );
        if (!hasText(scheme)) {
            scheme = "https";
        }

        String host = firstNonBlank(
                forwardedHost,
                xForwardedHost,
                request.getHeader("Host"),
                request.getServerName()
        );
        host = host == null ? "" : host.trim();

        if (host.contains(":")) {
            return scheme + "://" + host;
        }

        String forwardedPort = firstHeaderValue(request, "X-Forwarded-Port");
        boolean hasProxyHost = hasText(forwardedHost) || hasText(xForwardedHost);
        int port = hasProxyHost && "https".equalsIgnoreCase(scheme) ? 443 : request.getServerPort();
        if (hasProxyHost && "http".equalsIgnoreCase(scheme)) {
            port = 80;
        }
        Integer parsedForwardedPort = parsePort(forwardedPort);
        if (parsedForwardedPort != null) {
            port = parsedForwardedPort;
        }

        boolean defaultHttp = "http".equalsIgnoreCase(scheme) && port == 80;
        boolean defaultHttps = "https".equalsIgnoreCase(scheme) && port == 443;
        if (defaultHttp || defaultHttps) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String firstHeaderValue(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",", 2);
        return parts[0].trim();
    }

    private String forwardedFieldValue(String forwardedHeader, String key) {
        if (forwardedHeader == null || forwardedHeader.isBlank()) {
            return null;
        }
        String[] pairs = forwardedHeader.split(";");
        for (String pair : pairs) {
            String[] keyValue = pair.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if (!key.equalsIgnoreCase(keyValue[0].trim())) {
                continue;
            }
            String value = keyValue[1].trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                return value.substring(1, value.length() - 1).trim();
            }
            return value;
        }
        return null;
    }

    private Integer parsePort(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1 || parsed > 65535) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record MonthGroup(String label, int startIndex, int span) {
        public String getLabel() {
            return label;
        }

        public int getSpan() {
            return span;
        }
    }

    public record DateGroup(String label, int startIndex, int span) {
        public String getLabel() {
            return label;
        }

        public int getSpan() {
            return span;
        }
    }

    public record OptionHeader(PollOption option, String label) {
        public PollOption getOption() {
            return option;
        }

        public String getLabel() {
            return label;
        }
    }

    public record ParticipantRow(UUID responseId, String name, List<VoteCell> cells) {
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

    public record EditableRow(UUID responseId, String name, List<VoteCell> cells) {
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

    public record VoteCell(UUID optionId, PollVoteValue value, String symbol, String markerClass) {
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

    public record SummaryCell(UUID optionId, int count, boolean best) {
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
