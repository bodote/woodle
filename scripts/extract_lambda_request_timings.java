///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS info.picocli:picocli:4.7.6
//DEPS software.amazon.awssdk:cloudwatchlogs:2.32.18

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField;
import software.amazon.awssdk.services.cloudwatchlogs.model.StartQueryRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.StartQueryResponse;

@Command(
        name = "extract-lambda-request-timings",
        mixinStandardHelpOptions = true,
        description = "Correlates API Gateway access logs with Lambda REPORT logs to estimate cold-start and request timings.")
class extract_lambda_request_timings implements Runnable {

    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CLOUDWATCH_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Option(
            names = "--environment",
            defaultValue = "qs",
            description = "Deployment environment used for default log group names. Default: ${DEFAULT-VALUE}")
    String environment;

    @Option(
            names = "--region",
            defaultValue = "eu-central-1",
            description = "AWS region. Default: ${DEFAULT-VALUE}")
    String region;

    @Option(
            names = "--hours",
            defaultValue = "24",
            description = "Look back this many hours. Default: ${DEFAULT-VALUE}")
    long hours;

    @Option(
            names = "--limit",
            defaultValue = "50",
            description = "Maximum number of joined requests to print. Default: ${DEFAULT-VALUE}")
    int limit;

    @Option(
            names = "--api-log-group",
            description = "Override the API access log group. Default: /aws/apigateway/woodle-<environment>")
    String apiLogGroup;

    @Option(
            names = "--lambda-log-group",
            description = "Override the Lambda log group. If omitted, the newest matching AppFunction log group is auto-discovered.")
    String lambdaLogGroup;

    @Option(
            names = "--path-contains",
            description = "Only keep requests whose path contains this fragment.")
    String pathContains;

    @Option(
            names = "--csv",
            description = "Optional CSV output path for the correlated rows.")
    Path csvPath;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new extract_lambda_request_timings()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        validateArguments();

        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofHours(hours));
        String resolvedApiLogGroup = Optional.ofNullable(apiLogGroup)
                .filter(value -> !value.isBlank())
                .orElse("/aws/apigateway/woodle-" + environment);

        try (CloudWatchLogsClient logsClient = CloudWatchLogsClient.builder()
                .region(Region.of(region))
                .build()) {
            String resolvedLambdaLogGroup = Optional.ofNullable(lambdaLogGroup)
                    .filter(value -> !value.isBlank())
                    .orElseGet(() -> discoverLambdaLogGroup(logsClient));

            List<ApiLogRow> apiRows = queryApiRows(logsClient, resolvedApiLogGroup, start, end);
            List<LambdaReportRow> lambdaRows = queryLambdaRows(logsClient, resolvedLambdaLogGroup, start, end);
            List<JoinedRow> joinedRows = joinRows(apiRows, lambdaRows);

            if (pathContains != null && !pathContains.isBlank()) {
                joinedRows = joinedRows.stream()
                        .filter(row -> row.path().contains(pathContains))
                        .toList();
            }

            joinedRows = joinedRows.stream()
                    .sorted(Comparator.comparing(JoinedRow::requestTimestamp).reversed())
                    .limit(limit)
                    .toList();

            printHeader(resolvedApiLogGroup, resolvedLambdaLogGroup, start, end, apiRows.size(), lambdaRows.size(), joinedRows.size());
            printSummary(joinedRows);
            printRows(joinedRows);

            if (csvPath != null) {
                writeCsv(joinedRows, csvPath);
                System.out.println();
                System.out.println("CSV written to " + csvPath.toAbsolutePath());
            }
        }
    }

    private void validateArguments() {
        if (hours <= 0) {
            throw new CommandLine.ParameterException(
                    new CommandLine(this), "--hours must be greater than 0");
        }
        if (limit <= 0) {
            throw new CommandLine.ParameterException(
                    new CommandLine(this), "--limit must be greater than 0");
        }
    }

    private String discoverLambdaLogGroup(CloudWatchLogsClient logsClient) {
        String prefix = "/aws/lambda/woodle-" + environment + "-AppFunction";
        return logsClient.describeLogGroupsPaginator(DescribeLogGroupsRequest.builder()
                        .logGroupNamePrefix(prefix)
                        .build())
                .logGroups()
                .stream()
                .max(Comparator.comparing(logGroup -> Optional.ofNullable(logGroup.creationTime()).orElse(0L)))
                .map(logGroup -> logGroup.logGroupName())
                .orElseThrow(() -> new CommandLine.ExecutionException(
                        new CommandLine(this),
                        "No Lambda log group found for prefix " + prefix + ". Use --lambda-log-group explicitly."));
    }

    private List<ApiLogRow> queryApiRows(
            CloudWatchLogsClient logsClient,
            String logGroup,
            Instant start,
            Instant end) {
        return logsClient.filterLogEventsPaginator(FilterLogEventsRequest.builder()
                        .logGroupName(logGroup)
                        .startTime(start.toEpochMilli())
                        .endTime(end.toEpochMilli())
                        .build())
                .events()
                .stream()
                .map(this::toApiLogRow)
                .filter(Objects::nonNull)
                .filter(row -> !row.lambdaRequestId().isBlank())
                .toList();
    }

    private List<LambdaReportRow> queryLambdaRows(
            CloudWatchLogsClient logsClient,
            String logGroup,
            Instant start,
            Instant end) {
        String query = """
                fields @timestamp, @message
                | filter @message like /REPORT RequestId:/
                | parse @message /REPORT RequestId: (?<lambdaRequestId>[^\\s]+)\\s+Duration: (?<durationMs>[\\d.]+) ms\\s+Billed Duration: (?<billedDurationMs>[\\d.]+) ms(?:\\s+Memory Size: (?<memorySizeMb>\\d+) MB\\s+Max Memory Used: (?<maxMemoryMb>\\d+) MB)?(?:\\s+Init Duration: (?<initDurationMs>[\\d.]+) ms)?/
                | sort @timestamp desc
                | limit 5000
                """;

        return runInsightsQuery(logsClient, logGroup, start, end, query).stream()
                .map(extract_lambda_request_timings::toFieldMap)
                .map(fields -> new LambdaReportRow(
                        parseInstant(fields.get("@timestamp")),
                        defaultString(fields.get("lambdaRequestId")),
                        parseDecimal(fields.get("durationMs")),
                        parseDecimal(fields.get("billedDurationMs")),
                        parseNullableDecimal(fields.get("initDurationMs"))))
                .filter(row -> !row.lambdaRequestId().isBlank())
                .toList();
    }

    private List<List<ResultField>> runInsightsQuery(
            CloudWatchLogsClient logsClient,
            String logGroup,
            Instant start,
            Instant end,
            String query) {
        StartQueryResponse startResponse = logsClient.startQuery(StartQueryRequest.builder()
                .logGroupName(logGroup)
                .startTime(start.getEpochSecond())
                .endTime(end.getEpochSecond())
                .queryString(query)
                .build());

        String queryId = Objects.requireNonNull(startResponse.queryId(), "Missing CloudWatch Logs Insights queryId");
        GetQueryResultsResponse results = waitForQueryResults(logsClient, queryId);
        return results.results();
    }

    private GetQueryResultsResponse waitForQueryResults(CloudWatchLogsClient logsClient, String queryId) {
        for (int attempt = 1; attempt <= 60; attempt++) {
            GetQueryResultsResponse response = logsClient.getQueryResults(GetQueryResultsRequest.builder()
                    .queryId(queryId)
                    .build());
            switch (response.statusAsString()) {
                case "Complete":
                    return response;
                case "Failed":
                case "Cancelled":
                case "Timeout":
                    throw new CommandLine.ExecutionException(
                            new CommandLine(this),
                            "CloudWatch Logs Insights query failed with status " + response.statusAsString());
                default:
                    sleepSilently(1000L);
            }
        }
        throw new CommandLine.ExecutionException(new CommandLine(this), "Timed out waiting for CloudWatch Logs Insights query");
    }

    private List<JoinedRow> joinRows(List<ApiLogRow> apiRows, List<LambdaReportRow> lambdaRows) {
        Map<String, LambdaReportRow> lambdaByRequestId = lambdaRows.stream()
                .collect(Collectors.toMap(
                        LambdaReportRow::lambdaRequestId,
                        row -> row,
                        (left, right) -> left.requestTimestamp().isAfter(right.requestTimestamp()) ? left : right,
                        HashMap::new));

        return apiRows.stream()
                .map(apiRow -> {
                    LambdaReportRow lambdaRow = lambdaByRequestId.get(apiRow.lambdaRequestId());
                    if (lambdaRow == null) {
                        return null;
                    }
                    BigDecimal apiOverhead = apiRow.responseLatencyMs().subtract(apiRow.integrationLatencyMs()).max(BigDecimal.ZERO);
                    return new JoinedRow(
                            apiRow.requestTimestamp(),
                            apiRow.requestTimeText(),
                            apiRow.method(),
                            apiRow.path(),
                            apiRow.status(),
                            apiRow.apiRequestId(),
                            apiRow.lambdaRequestId(),
                            apiRow.responseLatencyMs(),
                            apiRow.integrationLatencyMs(),
                            apiOverhead,
                            lambdaRow.durationMs(),
                            lambdaRow.billedDurationMs(),
                            lambdaRow.initDurationMs());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private void printHeader(
            String apiLogGroup,
            String lambdaLogGroup,
            Instant start,
            Instant end,
            int apiRows,
            int lambdaRows,
            int joinedRows) {
        System.out.println("API log group:    " + apiLogGroup);
        System.out.println("Lambda log group: " + lambdaLogGroup);
        System.out.println("Time range:       " + TS_FORMATTER.format(start) + " -> " + TS_FORMATTER.format(end));
        System.out.println("API rows:         " + apiRows);
        System.out.println("Lambda rows:      " + lambdaRows);
        System.out.println("Joined rows:      " + joinedRows);
        System.out.println();
    }

    private void printSummary(List<JoinedRow> rows) {
        if (rows.isEmpty()) {
            System.out.println("No joined rows found in the selected time range.");
            return;
        }

        List<BigDecimal> initDurations = rows.stream()
                .map(JoinedRow::lambdaInitDurationMs)
                .filter(Objects::nonNull)
                .toList();

        System.out.println("Summary");
        System.out.println("  cold starts:            " + initDurations.size() + " / " + rows.size());
        System.out.println("  p50 api response ms:    " + formatDecimal(percentile(rows.stream().map(JoinedRow::apiResponseLatencyMs).toList(), 50)));
        System.out.println("  p95 api response ms:    " + formatDecimal(percentile(rows.stream().map(JoinedRow::apiResponseLatencyMs).toList(), 95)));
        System.out.println("  p50 integration ms:     " + formatDecimal(percentile(rows.stream().map(JoinedRow::apiIntegrationLatencyMs).toList(), 50)));
        System.out.println("  p95 integration ms:     " + formatDecimal(percentile(rows.stream().map(JoinedRow::apiIntegrationLatencyMs).toList(), 95)));
        System.out.println("  p50 lambda duration ms: " + formatDecimal(percentile(rows.stream().map(JoinedRow::lambdaDurationMs).toList(), 50)));
        System.out.println("  p95 lambda duration ms: " + formatDecimal(percentile(rows.stream().map(JoinedRow::lambdaDurationMs).toList(), 95)));
        if (!initDurations.isEmpty()) {
            System.out.println("  p50 init duration ms:   " + formatDecimal(percentile(initDurations, 50)));
            System.out.println("  p95 init duration ms:   " + formatDecimal(percentile(initDurations, 95)));
        }
        System.out.println();
    }

    private void printRows(List<JoinedRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        System.out.printf(
                Locale.ROOT,
                "%-19s %-6s %-30s %6s %10s %10s %10s %10s %10s%n",
                "timestamp",
                "method",
                "path",
                "status",
                "apiMs",
                "intMs",
                "ovhMs",
                "lambdaMs",
                "initMs");

        for (JoinedRow row : rows) {
            System.out.printf(
                    Locale.ROOT,
                    "%-19s %-6s %-30s %6d %10s %10s %10s %10s %10s%n",
                    TS_FORMATTER.format(row.requestTimestamp()),
                    truncate(row.method(), 6),
                    truncate(row.path(), 30),
                    row.status(),
                    formatDecimal(row.apiResponseLatencyMs()),
                    formatDecimal(row.apiIntegrationLatencyMs()),
                    formatDecimal(row.apiOverheadMs()),
                    formatDecimal(row.lambdaDurationMs()),
                    row.lambdaInitDurationMs() == null ? "-" : formatDecimal(row.lambdaInitDurationMs()));
        }
    }

    private void writeCsv(List<JoinedRow> rows, Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
                writer.println(
                        "timestamp,requestTime,method,path,status,apiRequestId,lambdaRequestId,apiResponseLatencyMs,apiIntegrationLatencyMs,apiOverheadMs,lambdaDurationMs,lambdaBilledDurationMs,lambdaInitDurationMs,coldStart");
                for (JoinedRow row : rows) {
                    writer.printf(
                            Locale.ROOT,
                            "%s,%s,%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                            csv(row.requestTimestamp().toString()),
                            csv(row.requestTimeText()),
                            csv(row.method()),
                            csv(row.path()),
                            row.status(),
                            csv(row.apiRequestId()),
                            csv(row.lambdaRequestId()),
                            csv(formatDecimal(row.apiResponseLatencyMs())),
                            csv(formatDecimal(row.apiIntegrationLatencyMs())),
                            csv(formatDecimal(row.apiOverheadMs())),
                            csv(formatDecimal(row.lambdaDurationMs())),
                            csv(formatDecimal(row.lambdaBilledDurationMs())),
                            csv(row.lambdaInitDurationMs() == null ? "" : formatDecimal(row.lambdaInitDurationMs())),
                            row.lambdaInitDurationMs() == null ? "false" : "true");
                }
            }
        } catch (IOException e) {
            throw new CommandLine.ExecutionException(new CommandLine(this), "Failed to write CSV to " + path, e);
        }
    }

    private static Map<String, String> toFieldMap(List<ResultField> fields) {
        Map<String, String> values = new LinkedHashMap<>();
        for (ResultField field : fields) {
            values.put(field.field(), field.value());
        }
        return values;
    }

    private ApiLogRow toApiLogRow(FilteredLogEvent event) {
        String message = defaultString(event.message()).trim();
        if (!message.startsWith("{") || !message.endsWith("}")) {
            return null;
        }

        String lambdaRequestId = extractJsonValue(message, "integrationRequestId");
        if (lambdaRequestId.isBlank() || "-".equals(lambdaRequestId)) {
            return null;
        }

        return new ApiLogRow(
                Instant.ofEpochMilli(event.timestamp()),
                extractJsonValue(message, "requestTime"),
                extractJsonValue(message, "httpMethod"),
                extractJsonValue(message, "path"),
                parseInteger(extractJsonValue(message, "status")),
                extractJsonValue(message, "requestId"),
                lambdaRequestId,
                parseDecimal(extractJsonValue(message, "responseLatency")),
                parseDecimal(extractJsonValue(message, "integrationLatency")));
    }

    private static String extractJsonValue(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return "";
        }
        return json.substring(valueStart, valueEnd);
    }

    private static Instant parseInstant(String value) {
        if (value.contains("T")) {
            return Instant.parse(value);
        }
        return LocalDateTime.parse(value, CLOUDWATCH_TIMESTAMP_FORMATTER).toInstant(ZoneOffset.UTC);
    }

    private static int parseInteger(String value) {
        return Integer.parseInt(value);
    }

    private static BigDecimal parseDecimal(String value) {
        return new BigDecimal(Optional.ofNullable(value).filter(v -> !v.isBlank()).orElse("0"));
    }

    private static BigDecimal parseNullableDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for CloudWatch Logs Insights query", e);
        }
    }

    private static BigDecimal percentile(Collection<BigDecimal> values, int percentile) {
        List<BigDecimal> sorted = values.stream()
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int index = (int) Math.ceil((percentile / 100.0d) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static String formatDecimal(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "~";
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    record ApiLogRow(
            Instant requestTimestamp,
            String requestTimeText,
            String method,
            String path,
            int status,
            String apiRequestId,
            String lambdaRequestId,
            BigDecimal responseLatencyMs,
            BigDecimal integrationLatencyMs) {}

    record LambdaReportRow(
            Instant requestTimestamp,
            String lambdaRequestId,
            BigDecimal durationMs,
            BigDecimal billedDurationMs,
            BigDecimal initDurationMs) {}

    record JoinedRow(
            Instant requestTimestamp,
            String requestTimeText,
            String method,
            String path,
            int status,
            String apiRequestId,
            String lambdaRequestId,
            BigDecimal apiResponseLatencyMs,
            BigDecimal apiIntegrationLatencyMs,
            BigDecimal apiOverheadMs,
            BigDecimal lambdaDurationMs,
            BigDecimal lambdaBilledDurationMs,
            BigDecimal lambdaInitDurationMs) {}
}
