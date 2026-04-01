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
    @DisplayName("routes static poll loader paths to S3 and keeps dynamic poll paths on API")
    void routesStaticLoaderPathsToS3AndDynamicPollPathsToApi() throws IOException {
        String template = Files.readString(Path.of("infra/template.yaml"));

        assertTrue(
                template.contains("PathPattern: /poll/static/*"),
                "Expected CloudFront behavior for static poll loader paths");
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
        assertTrue(
                template.contains("StaticPollLoaderRewriteFunction"),
                "Expected CloudFront Function for static poll loader rewrite");
        assertTrue(
                template.contains("FunctionAssociations:"),
                "Expected CloudFront Function association on static loader behavior");
        assertTrue(
                template.contains("/poll/static/loader.html"),
                "Expected rewrite target to static poll loader object");
        assertTrue(
                template.contains("NoReferrerResponseHeadersPolicy"),
                "Expected CloudFront response headers policy for no-referrer");
        assertTrue(
                template.contains("ReferrerPolicy: no-referrer"),
                "Expected CloudFront to enforce Referrer-Policy=no-referrer");
    }

    @Test
    @DisplayName("serves static unavailable page for upstream 5xx errors")
    void servesStaticUnavailablePageForUpstream5xxErrors() throws IOException {
        String template = Files.readString(Path.of("infra/template.yaml"));
        String unavailablePage = Files.readString(Path.of("src/main/resources/static/error-unavailable.html"));

        assertTrue(
                template.contains("ErrorCode: 500"),
                "Expected CloudFront custom error response for 500");
        assertTrue(
                template.contains("ErrorCode: 502"),
                "Expected CloudFront custom error response for 502");
        assertTrue(
                template.contains("ErrorCode: 503"),
                "Expected CloudFront custom error response for 503");
        assertTrue(
                template.contains("ErrorCode: 504"),
                "Expected CloudFront custom error response for 504");
        assertTrue(
                template.contains("ResponseCode: 503"),
                "Expected CloudFront to keep an outage status code for fallback page");
        assertTrue(
                template.contains("ResponsePagePath: /error-unavailable.html"),
                "Expected static unavailable fallback page path");
        assertTrue(
                unavailablePage.contains("vorübergehend nicht verfügbar"),
                "Expected user-facing unavailable message on static fallback page");
    }
}
