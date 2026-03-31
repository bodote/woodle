///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS info.picocli:picocli:4.7.6

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "create_30_day_poll",
        mixinStandardHelpOptions = true,
        description = "Creates a poll with configurable upcoming day and optional intraday time options."
)
class create_30_day_poll implements Runnable {

    @Option(
            names = "--target",
            defaultValue = "LOCAL",
            description = "Where to create the poll: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})."
    )
    private Target target;

    @Option(
            names = "--base-url",
            description = "Optional explicit base URL override."
    )
    private String baseUrlOverride;

    @Option(
            names = "--tage",
            defaultValue = "30",
            description = "Number of day options to create starting from tomorrow (default: ${DEFAULT-VALUE})."
    )
    private int days;

    @Option(
            names = {"--untertaegig", "-u"},
            description = "Create an intraday poll with this many one-hour time slots per day, starting at 08:00."
    )
    private Integer intradaySlotsPerDay;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new create_30_day_poll()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            if (days < 1) {
                throw new CommandLine.ParameterException(new CommandLine(this), "--tage must be at least 1");
            }
            if (intradaySlotsPerDay != null && intradaySlotsPerDay < 1) {
                throw new CommandLine.ParameterException(
                        new CommandLine(this), "--untertaegig must be at least 1");
            }

            String payload = buildPayload(days, intradaySlotsPerDay);
            String baseUrl = baseUrlOverride != null && !baseUrlOverride.isBlank()
                    ? baseUrlOverride.trim()
                    : target.baseUrl;

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/polls"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Poll creation failed with status "
                        + response.statusCode()
                        + " and body: "
                        + response.body());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.contains("application/json")) {
                throw new IOException("Poll creation returned unexpected content type "
                        + contentType
                        + " with body: "
                        + response.body());
            }

            String participantUrl = requireUrl(response.body(), "voteUrl");
            String adminUrl = requireUrl(response.body(), "adminUrl");
            String frontendBaseUrl = target.frontendBaseUrl(baseUrl);

            System.out.println("Participant URL: " + joinUrl(frontendBaseUrl, participantUrl));
            System.out.println("Admin URL: " + joinUrl(frontendBaseUrl, adminUrl));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CommandLine.ExecutionException(new CommandLine(this), "Interrupted while creating poll", exception);
        } catch (IOException exception) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "Failed to create poll", exception);
        }
    }

    private static String requireUrl(String json, String fieldName) throws IOException {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IOException("Missing field '" + fieldName + "' in response body: " + json);
        }
        return matcher.group(1);
    }

    private static String joinUrl(String baseUrl, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private static String buildPayload(int days, Integer intradaySlotsPerDay) {
        boolean intraday = intradaySlotsPerDay != null;
        PayloadOptions payloadOptions = intraday
                ? buildIntradayOptions(days, intradaySlotsPerDay)
                : new PayloadOptions(buildDates(days), "");
        String eventType = intraday ? "INTRADAY" : "ALL_DAY";
        String durationMinutes = intraday ? "60" : "null";
        String title = intraday
                ? "Test-Poll mit %d Tagen und %d Terminen je Tag".formatted(days, intradaySlotsPerDay)
                : "Test-Poll mit %d Tagen".formatted(days);
        String description = intraday
                ? "Automatisch erzeugte untertägige Umfrage mit %d Tagen und %d einstündigen Terminen je Tag ab morgen."
                .formatted(days, intradaySlotsPerDay)
                : "Automatisch erzeugte Ganztags-Umfrage mit %d aufeinanderfolgenden Tagen ab morgen."
                .formatted(days);

        return """
                {
                  "authorName": "Codex Script",
                  "authorEmail": "codex@example.com",
                  "title": "%s",
                  "description": "%s",
                  "eventType": "%s",
                  "durationMinutes": %s,
                  "dates": [%s],
                  "startTimes": [%s],
                  "expiresAtOverride": null
                }
                """.formatted(
                title,
                description,
                eventType,
                durationMinutes,
                payloadOptions.dates(),
                payloadOptions.startTimes()
        );
    }

    private static String buildDates(int days) {
        StringBuilder dates = new StringBuilder();
        LocalDate startDate = LocalDate.now().plusDays(1);
        for (int offset = 0; offset < days; offset++) {
            if (offset > 0) {
                dates.append(',');
            }
            dates.append('"').append(startDate.plusDays(offset)).append('"');
        }
        return dates.toString();
    }

    private static PayloadOptions buildIntradayOptions(int days, int intradaySlotsPerDay) {
        StringBuilder dates = new StringBuilder();
        StringBuilder startTimes = new StringBuilder();
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalTime firstSlot = LocalTime.of(8, 0);
        for (int dayOffset = 0; dayOffset < days; dayOffset++) {
            LocalDate currentDate = startDate.plusDays(dayOffset);
            for (int slotOffset = 0; slotOffset < intradaySlotsPerDay; slotOffset++) {
                if (!dates.isEmpty()) {
                    dates.append(',');
                    startTimes.append(',');
                }
                dates.append('"').append(currentDate).append('"');
                startTimes.append('"').append(firstSlot.plusHours(slotOffset)).append('"');
            }
        }
        return new PayloadOptions(dates.toString(), startTimes.toString());
    }

    private record PayloadOptions(String dates, String startTimes) {
    }

    enum Target {
        LOCAL("http://localhost:8088", "http://localhost:8088"),
        QS("https://vmclrtrd73.execute-api.eu-central-1.amazonaws.com", "https://qs.woodle.click");

        private final String baseUrl;
        private final String frontendBaseUrl;

        Target(String baseUrl, String frontendBaseUrl) {
            this.baseUrl = baseUrl;
            this.frontendBaseUrl = frontendBaseUrl;
        }

        private String frontendBaseUrl(String resolvedBaseUrl) {
            if (this == LOCAL) {
                return resolvedBaseUrl;
            }
            return frontendBaseUrl;
        }
    }
}
