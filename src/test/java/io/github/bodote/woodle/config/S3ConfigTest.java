package io.github.bodote.woodle.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("S3Config")
class S3ConfigTest {

    @Test
    @DisplayName("applies region and endpoint override when endpoint is configured")
    void appliesRegionAndEndpointOverrideWhenEndpointIsConfigured() {
        S3Config config = new S3Config();

        try (S3Client client = config.s3Client("http://localhost:4566", "us-east-1", "dummy", "dummy", true)) {
            assertEquals(Region.US_EAST_1, client.serviceClientConfiguration().region());
            assertEquals(Optional.of(URI.create("http://localhost:4566")),
                    client.serviceClientConfiguration().endpointOverride());
        }
    }

    @Test
    @DisplayName("does not set endpoint override when endpoint is blank")
    void doesNotSetEndpointOverrideWhenEndpointIsBlank() {
        S3Config config = new S3Config();

        try (S3Client client = config.s3Client("   ", "eu-central-1", "dummy", "dummy", true)) {
            assertEquals(Region.EU_CENTRAL_1, client.serviceClientConfiguration().region());
            assertEquals(Optional.empty(), client.serviceClientConfiguration().endpointOverride());
        }
    }

    @Test
    @DisplayName("does not set endpoint override when endpoint is null")
    void doesNotSetEndpointOverrideWhenEndpointIsNull() {
        S3Config config = new S3Config();

        try (S3Client client = config.s3Client(null, "eu-central-1", "dummy", "dummy", true)) {
            assertEquals(Region.EU_CENTRAL_1, client.serviceClientConfiguration().region());
            assertEquals(Optional.empty(), client.serviceClientConfiguration().endpointOverride());
        }
    }
}
