package io.github.bodote.woodle.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bodote.woodle.adapter.out.persistence.S3PollRepository;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("S3PollRepository")
class S3PollRepositoryIT {

    private static final String BUCKET = "woodle-test";

    @Container
    static final LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.1.0"))
            .withServices(LocalStackContainer.Service.S3);

    @Test
    @DisplayName("stores poll as json in S3")
    void storesPollAsJsonInS3() throws IOException {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                ))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        S3PollRepository repository = new S3PollRepository(s3Client, new ObjectMapper(), BUCKET);

        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Poll poll = new Poll(
                pollId,
                "AdminSecret12",
                "Test",
                "Desc",
                "Alice",
                "alice@invalid",
                EventType.ALL_DAY,
                null,
                List.of(new PollOption(UUID.randomUUID(), LocalDate.of(2026, 2, 10), null, null)),
                List.of(),
                now,
                now,
                LocalDate.of(2026, 3, 10)
        );

        repository.save(poll);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(BUCKET)
                .key("polls/" + pollId + ".json")
                .build();

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest)) {
            String json = new String(response.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains(pollId.toString()));
            assertTrue(json.contains("\"title\":\"Test\""));
        }
    }

    @Test
    @DisplayName("returns empty when poll object does not exist")
    void returnsEmptyWhenPollObjectDoesNotExist() {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                ))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        S3PollRepository repository = new S3PollRepository(s3Client, new ObjectMapper(), BUCKET);

        Optional<Poll> result = repository.findById(UUID.fromString("00000000-0000-0000-0000-00000000abcd"));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("throws on missing bucket instead of masking infrastructure error")
    void throwsOnMissingBucket() {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                ))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        S3PollRepository repository = new S3PollRepository(s3Client, new ObjectMapper(), "missing-bucket");
        assertThrows(IllegalStateException.class,
                () -> repository.findById(UUID.fromString("00000000-0000-0000-0000-00000000dcba")));
    }

    @Test
    @DisplayName("throws on invalid poll json instead of returning empty")
    void throwsOnInvalidPollJson() {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                ))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        s3Client.putObject(
                PutObjectRequest.builder().bucket(BUCKET).key("polls/00000000-0000-0000-0000-0000000000aa.json").build(),
                RequestBody.fromString("{invalid-json")
        );

        S3PollRepository repository = new S3PollRepository(s3Client, new ObjectMapper(), BUCKET);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> repository.findById(UUID.fromString("00000000-0000-0000-0000-0000000000aa")));
        assertEquals("Failed to deserialize poll", exception.getMessage());
    }
}
