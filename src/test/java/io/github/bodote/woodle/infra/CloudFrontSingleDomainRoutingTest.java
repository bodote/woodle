package io.github.bodote.woodle.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CloudFront single-domain routing")
class CloudFrontSingleDomainRoutingTest {

    @Test
    @DisplayName("routes /poll* to API and keeps /poll/new-step1.html on S3 origin")
    void routesPollPathsToApiAndStep1ToS3() throws IOException {
        String template = Files.readString(Path.of("infra/template.yaml"));

        assertTrue(
                template.contains("PathPattern: /poll*"),
                "Expected CloudFront behavior for /poll* dynamic routes to API origin");
        assertTrue(
                template.contains("PathPattern: /poll/new-step1.html"),
                "Expected CloudFront behavior keeping /poll/new-step1.html on S3 origin");
        assertTrue(
                template.contains("PathPattern: /poll/new\n"),
                "Expected CloudFront behavior keeping /poll/new on S3 origin");
        assertTrue(
                template.contains("TargetOriginId: api-origin"),
                "Expected /poll* behavior to target API origin");
        assertTrue(
                template.contains("TargetOriginId: web-bucket-origin"),
                "Expected /poll/new-step1.html behavior to target web-bucket-origin");
    }
}
