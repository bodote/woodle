package io.github.bodote.woodle.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("S3PollRepository")
class S3PollRepositoryTest {

    @Test
    @DisplayName("returns empty for missing poll object")
    void returnsEmptyForMissingPollObject() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        S3PollRepository repository = new S3PollRepository(s3Client, new ObjectMapper(), "woodle");
        Optional<?> result = repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("throws when S3 returns infrastructure error")
    void throwsWhenS3ReturnsInfrastructureError() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("boom").build());

        S3PollRepository repository = new S3PollRepository(s3Client, new ObjectMapper(), "woodle");

        assertThrows(IllegalStateException.class,
                () -> repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    @DisplayName("throws when poll payload is invalid json")
    void throwsWhenPollPayloadIsInvalidJson() {
        S3Client s3Client = mock(S3Client.class);
        GetObjectResponse response = GetObjectResponse.builder().build();
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(
                response,
                AbortableInputStream.create(new ByteArrayInputStream("{invalid".getBytes(StandardCharsets.UTF_8)))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);

        S3PollRepository repository = new S3PollRepository(s3Client, new ObjectMapper(), "woodle");

        assertThrows(IllegalStateException.class,
                () -> repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001")));
    }
}
