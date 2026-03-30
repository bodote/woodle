package io.github.bodote.woodle.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("30 day poll creation script")
class Create30DayPollScriptTest {

    @Test
    @DisplayName("supports choosing local or qs target via picocli")
    void supportsChoosingLocalOrQsTargetViaPicocli() throws IOException {
        Path scriptPath = Path.of("scripts/create_30_day_poll.java");

        assertTrue(Files.exists(scriptPath), "Expected JBang poll creation script under scripts/");

        String script = Files.readString(scriptPath);
        assertTrue(script.contains("//DEPS info.picocli:picocli:4.7.6"), "Expected Picocli dependency directive");
        assertTrue(script.contains("import picocli.CommandLine;"), "Expected picocli CommandLine import");
        assertTrue(script.contains("@Command("), "Expected Picocli command definition");
        assertTrue(script.contains("--target"), "Expected target option for environment selection");
        assertTrue(script.contains("LOCAL"), "Expected local target enum value");
        assertTrue(script.contains("QS"), "Expected QS target enum value");
        assertTrue(script.contains("http://localhost:8088"), "Expected local target base URL");
        assertTrue(script.contains("https://vmclrtrd73.execute-api.eu-central-1.amazonaws.com"),
                "Expected QS API Gateway base URL");
        assertTrue(script.contains("https://qs.woodle.click"), "Expected QS frontend base URL");
        assertTrue(script.contains("Content-Type"), "Expected response content type validation");
        assertTrue(script.contains("application/json"), "Expected JSON response validation");
        assertTrue(script.contains("Admin URL:"), "Expected explicit admin URL output");
        assertTrue(script.contains("Participant URL:"), "Expected explicit participant URL output");
        assertTrue(script.contains("frontendBaseUrl"), "Expected frontend URL resolution");
    }
}
