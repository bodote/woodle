package io.github.bodote.woodle.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Observability infrastructure")
class ObservabilityInfrastructureTest {

    @Test
    @DisplayName("enables API access logs with latency fields and Lambda active tracing")
    void enablesApiAccessLogsWithLatencyFieldsAndLambdaActiveTracing() throws IOException {
        String template = Files.readString(Path.of("infra/template.yaml"));

        assertTrue(
                template.contains("ApiAccessLogGroup:"),
                "Expected dedicated CloudWatch LogGroup for API access logs");
        assertTrue(
                template.contains("AccessLogSettings:"),
                "Expected HTTP API access log settings");
        assertTrue(
                template.contains("responseLatency"),
                "Expected API access logs to include responseLatency");
        assertTrue(
                template.contains("integrationLatency"),
                "Expected API access logs to include integrationLatency");
        assertTrue(
                template.contains("requestId"),
                "Expected API access logs to include requestId for correlation");
        assertTrue(
                template.contains("Tracing: Active"),
                "Expected Lambda function tracing mode to be Active");
    }
}
