package io.github.bodote.woodle.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Observability log extraction script")
class ObservabilityLogExtractionScriptTest {

    @Test
    @DisplayName("provides a reusable JBang script for correlating API and Lambda timings")
    void providesReusableJbangScriptForCorrelatingApiAndLambdaTimings() throws IOException {
        Path scriptPath = Path.of("scripts/extract_lambda_request_timings.java");

        assertTrue(Files.exists(scriptPath), "Expected JBang log extraction script under scripts/");

        String script = Files.readString(scriptPath);
        assertTrue(script.contains("//JAVA 21+"), "Expected JBang Java version directive");
        assertTrue(script.contains("@Command("), "Expected Picocli command definition");
        assertTrue(script.contains("--environment"), "Expected environment option");
        assertTrue(script.contains("--api-log-group"), "Expected explicit API log group override option");
        assertTrue(script.contains("--lambda-log-group"), "Expected explicit Lambda log group override option");
        assertTrue(script.contains("integrationRequestId"), "Expected correlation via API integrationRequestId");
        assertTrue(script.contains("Init Duration"), "Expected Lambda REPORT parsing for Init Duration");
    }
}
