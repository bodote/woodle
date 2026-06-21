///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS software.amazon.awssdk:cloudwatchlogs:2.42.33
//DEPS software.amazon.awssdk:cloudwatch:2.42.33
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//DEPS org.slf4j:slf4j-nop:1.7.36

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Woodle access-log analyzer.
 *
 * Reads the API Gateway access-log group (/aws/apigateway/woodle-<env>) from CloudWatch Logs,
 * aggregates requests-per-day, distinct source IPs and error events, and (optionally) pulls the
 * CloudFront "Requests" metric for the frontend distribution.
 *
 * Usage:
 *   jbang tools/WoodleLogStats.java [--env prod] [--days 14] [--region eu-central-1]
 *                                   [--cf-dist ECIPVF7FI5V4B] [--show-errors 30]
 *
 * Notes / caveats:
 *  - The API Gateway log carries ALL traffic that reaches the HTTP API: real /v1 API calls,
 *    server-rendered frontend poll routes proxied via CloudFront, AND bot/scanner noise.
 *    There is no Host header in the access log, so woodle.click vs api.woodle.click cannot be
 *    split apart here; we split by path instead (/v1* = API, everything else = frontend/other).
 *  - Distinct-IP counts for the *static* frontend (woodle.click cached assets) are NOT available:
 *    CloudFront access logging is disabled on the distribution. --cf-dist only yields aggregate
 *    request counts from CloudWatch metrics (no per-IP data).
 */
public class WoodleLogStats {

    static final DateTimeFormatter APIGW_TS =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);

    public static void main(String[] args) {
        Map<String, String> a = parseArgs(args);
        String env      = a.getOrDefault("env", "prod");
        int days        = Integer.parseInt(a.getOrDefault("days", "14"));
        Region region   = Region.of(a.getOrDefault("region", "eu-central-1"));
        String cfDist   = a.get("cf-dist");
        int showErrors  = Integer.parseInt(a.getOrDefault("show-errors", "30"));
        String lambdaLogGroup = a.get("lambda-log-group");

        String logGroup = "/aws/apigateway/woodle-" + env;
        Instant end   = Instant.now();
        Instant start = end.minus(Duration.ofDays(days));

        System.out.printf("Woodle log stats  env=%s  region=%s  window=%s .. %s (%d days)%n",
                env, region.id(), start, end, days);
        System.out.println("Log group: " + logGroup);
        System.out.println("=".repeat(78));

        // ---- aggregation state ----
        Set<String> allIps = new HashSet<>(), apiIps = new HashSet<>(), feIps = new HashSet<>();
        Map<String, int[]> perDay = new TreeMap<>();        // day -> [total, api, frontend, errors]
        Map<String, Integer> status = new TreeMap<>();
        Map<String, Integer> topPath = new HashMap<>();
        List<String[]> errors = new ArrayList<>();
        AtomicInteger count = new AtomicInteger();
        ObjectMapper mapper = new ObjectMapper();

        try (CloudWatchLogsClient logs = CloudWatchLogsClient.builder().region(region).build()) {
            FilterLogEventsRequest req = FilterLogEventsRequest.builder()
                    .logGroupName(logGroup)
                    .startTime(start.toEpochMilli())
                    .endTime(end.toEpochMilli())
                    .build();

            for (FilteredLogEvent ev : logs.filterLogEventsPaginator(req).events()) {
                JsonNode n;
                try { n = mapper.readTree(ev.message()); } catch (Exception e) { continue; }
                if (n == null || !n.hasNonNull("requestTime")) continue;
                count.incrementAndGet();

                String path = n.path("path").asText("");
                String ip   = n.path("sourceIp").asText("");
                String st   = n.path("status").asText("");
                String method = n.path("httpMethod").asText("");
                String day  = dayOf(n.path("requestTime").asText());
                boolean isApi = path.startsWith("/v1");

                allIps.add(ip);
                (isApi ? apiIps : feIps).add(ip);
                status.merge(st, 1, Integer::sum);
                topPath.merge(path, 1, Integer::sum);

                int[] d = perDay.computeIfAbsent(day, k -> new int[4]);
                d[0]++;
                if (isApi) d[1]++; else d[2]++;

                int code = parseInt(st);
                if (code >= 400) {
                    d[3]++;
                    errors.add(new String[]{n.path("requestTime").asText(), st, method, path, ip});
                }
            }
        }

        // ---- report ----
        System.out.printf("%nParsed %d access-log events%n", count.get());

        System.out.println("\n--- Distinct source IPs ---");
        System.out.printf("  total distinct IPs            : %d%n", allIps.size());
        System.out.printf("  distinct IPs on /v1 API       : %d%n", apiIps.size());
        System.out.printf("  distinct IPs on frontend/other: %d%n", feIps.size());

        System.out.println("\n--- Requests per day ---");
        System.out.printf("  %-12s %8s %8s %8s %8s%n", "day", "total", "/v1 API", "other", "errors");
        int[] tot = new int[4];
        for (var e : perDay.entrySet()) {
            int[] d = e.getValue();
            for (int i = 0; i < 4; i++) tot[i] += d[i];
            System.out.printf("  %-12s %8d %8d %8d %8d%n", e.getKey(), d[0], d[1], d[2], d[3]);
        }
        System.out.printf("  %-12s %8d %8d %8d %8d%n", "TOTAL", tot[0], tot[1], tot[2], tot[3]);

        System.out.println("\n--- HTTP status distribution ---");
        status.entrySet().stream()
                .sorted((x, y) -> y.getValue() - x.getValue())
                .forEach(e -> System.out.printf("  %-6s %d%n", e.getKey(), e.getValue()));

        System.out.println("\n--- Top 15 paths ---");
        topPath.entrySet().stream()
                .sorted((x, y) -> y.getValue() - x.getValue())
                .limit(15)
                .forEach(e -> System.out.printf("  %5d  %s%n", e.getValue(),
                        e.getKey().length() > 70 ? e.getKey().substring(0, 70) : e.getKey()));

        System.out.printf("%n--- Error events (status >= 400): %d total, showing up to %d ---%n",
                errors.size(), showErrors);
        errors.stream().limit(showErrors).forEach(e ->
                System.out.printf("  %s  %s %-5s %s  (%s)%n", e[0], e[1], e[2], e[3], e[4]));

        reportCleanup(env, region, lambdaLogGroup, start, end);

        if (cfDist != null) reportCloudFront(cfDist, start, end, days);
    }

    static final Pattern DELETED_PATTERN = Pattern.compile("deleted (\\d+) of (\\d+)");

    /**
     * Poll-cleanup observability: reads the app Lambda log group and surfaces the
     * POLL_CLEANUP events emitted by CleanupExpiredPollsService (started / found / deleted).
     * The function name is generated by CloudFormation, so the log group is discovered by
     * the stack-derived prefix /aws/lambda/woodle-<env>-AppFunction unless --lambda-log-group
     * overrides it.
     */
    static void reportCleanup(String env, Region region, String overrideLogGroup, Instant start, Instant end) {
        System.out.println("\n" + "=".repeat(78));
        System.out.println("Poll cleanup job (POLL_CLEANUP events from the app Lambda log)");
        try (CloudWatchLogsClient logs = CloudWatchLogsClient.builder().region(region).build()) {
            String logGroup = overrideLogGroup != null ? overrideLogGroup : findAppLogGroup(logs, env);
            if (logGroup == null) {
                System.out.println("  no Lambda log group found with prefix /aws/lambda/woodle-" + env
                        + "-AppFunction (pass --lambda-log-group to override)");
                return;
            }
            System.out.println("Log group: " + logGroup);
            FilterLogEventsRequest req = FilterLogEventsRequest.builder()
                    .logGroupName(logGroup)
                    .startTime(start.toEpochMilli())
                    .endTime(end.toEpochMilli())
                    .filterPattern("\"POLL_CLEANUP\"")
                    .build();

            int runs = 0, totalDeleted = 0, events = 0;
            for (FilteredLogEvent ev : logs.filterLogEventsPaginator(req).events()) {
                events++;
                String msg = ev.message().trim();
                int idx = msg.indexOf("POLL_CLEANUP");
                String shown = idx >= 0 ? msg.substring(idx) : msg;
                String ts = Instant.ofEpochMilli(ev.timestamp()).atZone(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"));
                System.out.printf("  %s  %s%n", ts, shown);
                if (shown.startsWith("POLL_CLEANUP started")) runs++;
                Matcher m = DELETED_PATTERN.matcher(shown);
                if (m.find()) totalDeleted += parseInt(m.group(1));
            }
            if (events == 0) {
                System.out.println("  no cleanup events in the selected window");
            } else {
                System.out.printf("  --- %d cleanup run(s), %d poll(s) deleted in window ---%n", runs, totalDeleted);
            }
        } catch (Exception e) {
            System.out.println("  could not read cleanup events: " + e.getMessage());
        }
    }

    /** Resolve the CloudFormation-generated app Lambda log group from the stack-derived prefix. */
    static String findAppLogGroup(CloudWatchLogsClient logs, String env) {
        DescribeLogGroupsResponse r = logs.describeLogGroups(DescribeLogGroupsRequest.builder()
                .logGroupNamePrefix("/aws/lambda/woodle-" + env + "-AppFunction")
                .build());
        return r.logGroups().stream()
                .map(LogGroup::logGroupName)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    /** Frontend request counts from CloudWatch (CloudFront metrics live in us-east-1). */
    static void reportCloudFront(String distId, Instant start, Instant end, int days) {
        System.out.println("\n" + "=".repeat(78));
        System.out.println("CloudFront frontend (woodle.click) daily request counts  dist=" + distId);
        System.out.println("(aggregate counts only — per-IP data needs CloudFront access logging, currently OFF)");
        try (CloudWatchClient cw = CloudWatchClient.builder().region(Region.US_EAST_1).build()) {
            GetMetricStatisticsResponse r = cw.getMetricStatistics(GetMetricStatisticsRequest.builder()
                    .namespace("AWS/CloudFront").metricName("Requests")
                    .dimensions(
                            Dimension.builder().name("DistributionId").value(distId).build(),
                            Dimension.builder().name("Region").value("Global").build())
                    .startTime(start).endTime(end)
                    .period(86400).statistics(Statistic.SUM).build());
            double sum = 0;
            for (Datapoint dp : r.datapoints().stream()
                    .sorted(Comparator.comparing(Datapoint::timestamp)).toList()) {
                sum += dp.sum();
                System.out.printf("  %s  %,.0f requests%n",
                        dp.timestamp().atZone(ZoneOffset.UTC).toLocalDate(), dp.sum());
            }
            System.out.printf("  %-12s %,.0f requests over %d days%n", "TOTAL", sum, days);
        }
    }

    static String dayOf(String requestTime) {
        // requestTime like: 08/Jun/2026:08:46:06 +0000
        String tsPart = requestTime.split(" ")[0];
        return LocalDateTime.parse(tsPart, APIGW_TS).toLocalDate().toString();
    }

    static int parseInt(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++)
            if (args[i].startsWith("--")) m.put(args[i].substring(2), args[i + 1]);
        return m;
    }
}
