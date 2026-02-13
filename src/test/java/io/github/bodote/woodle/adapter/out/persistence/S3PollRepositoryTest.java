package io.github.bodote.woodle.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@DisplayName("S3PollRepository")
class S3PollRepositoryTest {

    @Test
    @DisplayName("saves poll as json with expected key and content type")
    void savesPollAsJsonWithExpectedKeyAndContentType() throws IOException {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3PollRepository repository = new S3PollRepository(s3Client, objectMapper, "woodle");

        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        UUID optionId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        OffsetDateTime now = OffsetDateTime.of(2026, 2, 10, 10, 0, 0, 0, ZoneOffset.UTC);
        Poll poll = new Poll(
                pollId,
                "AdminSecret12",
                "Team Sync",
                "Weekly planning",
                "Alice",
                "alice@invalid",
                EventType.INTRADAY,
                30,
                List.of(new PollOption(optionId, LocalDate.of(2026, 2, 11), LocalTime.of(9, 0), LocalTime.of(9, 30))),
                List.of(new PollResponse(
                        UUID.fromString("00000000-0000-0000-0000-000000000101"),
                        "Bob",
                        now,
                        List.of(new PollVote(optionId, PollVoteValue.YES)),
                        "works for me"
                )),
                now,
                now,
                LocalDate.of(2026, 3, 11)
        );

        repository.save(poll);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest request = requestCaptor.getValue();
        assertEquals("woodle", request.bucket());
        assertEquals("polls/00000000-0000-0000-0000-000000000099.json", request.key());
        assertEquals("application/json", request.contentType());

        String json = new String(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"title\":\"Team Sync\""));
        assertTrue(json.contains("\"eventType\":\"INTRADAY\""));
        assertTrue(json.contains("\"startTime\":\"09:00\""));
        assertTrue(json.contains("\"endTime\":\"09:30\""));
    }

    @Test
    @DisplayName("returns empty for missing poll object")
    void returnsEmptyForMissingPollObject() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3PollRepository repository = new S3PollRepository(s3Client, objectMapper, "woodle");
        Optional<?> result = repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertTrue(result.isEmpty());
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture());
        GetObjectRequest request = requestCaptor.getValue();
        assertEquals("woodle", request.bucket());
        assertEquals("polls/00000000-0000-0000-0000-000000000001.json", request.key());
    }

    @Test
    @DisplayName("throws when S3 returns infrastructure error")
    void throwsWhenS3ReturnsInfrastructureError() {
        S3Client s3Client = mock(S3Client.class);
        S3Exception s3Exception = (S3Exception) S3Exception.builder().statusCode(500).message("boom").build();
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(s3Exception);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3PollRepository repository = new S3PollRepository(s3Client, objectMapper, "woodle");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertEquals("Failed to fetch poll from S3", exception.getMessage());
        assertSame(s3Exception, exception.getCause());
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

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3PollRepository repository = new S3PollRepository(s3Client, objectMapper, "woodle");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertEquals("Failed to deserialize poll", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    @DisplayName("reads poll and maps blank start or end times to null")
    void readsPollAndMapsBlankStartOrEndTimesToNull() {
        S3Client s3Client = mock(S3Client.class);
        String json = """
                {
                  "pollId":"00000000-0000-0000-0000-000000000021",
                  "type":"date",
                  "title":"Title",
                  "descriptionHtml":"Description",
                  "language":"de",
                  "createdAt":"2026-02-10T10:00:00Z",
                  "updatedAt":"2026-02-10T10:00:00Z",
                  "author":{"name":"Alice","email":"alice@invalid"},
                  "access":{"customSlug":null,"passwordHash":null,"resultsPublic":true,"adminToken":"secret"},
                  "permissions":{"voteChangePolicy":"ALL_CAN_EDIT"},
                  "notifications":{"onVote":false,"onComment":false},
                  "resultsVisibility":{"onlyAuthor":false},
                  "status":"OPEN",
                  "expiresAt":"2026-03-01",
                  "options":{"eventType":"INTRADAY","durationMinutes":30,"items":[
                    {"optionId":"00000000-0000-0000-0000-000000000022","date":"2026-02-10","startTime":" ","endTime":""}
                  ]},
                  "responses":[{"responseId":"00000000-0000-0000-0000-000000000023","participantName":"Bob","createdAt":"2026-02-10T10:00:00Z","votes":[{"optionId":"00000000-0000-0000-0000-000000000022","value":"YES"}],"comment":"ok"}]
                }
                """;
        GetObjectResponse response = GetObjectResponse.builder().build();
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(
                response,
                AbortableInputStream.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3PollRepository repository = new S3PollRepository(s3Client, objectMapper, "woodle");

        Optional<Poll> found = repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000021"));

        assertTrue(found.isPresent());
        Poll poll = found.orElseThrow();
        assertEquals("Title", poll.title());
        assertEquals(EventType.INTRADAY, poll.eventType());
        assertEquals(30, poll.durationMinutes());
        assertEquals("secret", poll.adminSecret());
        assertEquals(1, poll.options().size());
        assertEquals(1, poll.responses().size());
        assertNotNull(poll.responses().getFirst().createdAt());
        assertEquals(PollVoteValue.YES, poll.responses().getFirst().votes().getFirst().value());
        assertEquals("ok", poll.responses().getFirst().comment());
        assertEquals(LocalDate.of(2026, 2, 10), poll.options().getFirst().date());
        assertNull(poll.options().getFirst().startTime());
        assertNull(poll.options().getFirst().endTime());
    }

    @Test
    @DisplayName("throws deserialization error when poll payload contains invalid vote value")
    void throwsDeserializationErrorWhenPollPayloadContainsInvalidVoteValue() {
        S3Client s3Client = mock(S3Client.class);
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000009");
        String json = """
                {
                  "pollId":"00000000-0000-0000-0000-000000000009",
                  "type":"date",
                  "title":"Title",
                  "descriptionHtml":"Description",
                  "language":"de",
                  "createdAt":"2026-02-10T10:00:00Z",
                  "updatedAt":"2026-02-10T10:00:00Z",
                  "author":{"name":"Alice","email":"alice@invalid"},
                  "access":{"customSlug":null,"passwordHash":null,"resultsPublic":true,"adminToken":"secret"},
                  "permissions":{"voteChangePolicy":"ALL_CAN_EDIT"},
                  "notifications":{"onVote":false,"onComment":false},
                  "resultsVisibility":{"onlyAuthor":false},
                  "status":"OPEN",
                  "expiresAt":"2026-03-01",
                  "options":{"eventType":"ALL_DAY","durationMinutes":null,"items":[{"optionId":"00000000-0000-0000-0000-000000000010","date":"2026-02-10","startTime":null,"endTime":null}]},
                  "responses":[{"responseId":"00000000-0000-0000-0000-000000000011","participantName":"Bob","createdAt":"2026-02-10T10:00:00Z","votes":[{"optionId":"00000000-0000-0000-0000-000000000010","value":"INVALID_VALUE"}],"comment":null}]
                }
                """;
        GetObjectResponse response = GetObjectResponse.builder().build();
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(
                response,
                AbortableInputStream.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3PollRepository repository = new S3PollRepository(s3Client, objectMapper, "woodle");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findById(pollId)
        );
        assertEquals("Failed to deserialize poll", exception.getMessage());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }
}
