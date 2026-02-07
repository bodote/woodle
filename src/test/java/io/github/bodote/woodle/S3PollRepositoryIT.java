package io.github.bodote.woodle;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
