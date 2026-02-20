package io.github.bodote.woodle.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bodote.woodle.adapter.in.web.WizardState;
import io.github.bodote.woodle.domain.model.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("S3WizardStateRepository")
class S3WizardStateRepositoryTest {

    @Test
    @DisplayName("saves and loads wizard draft by draft id")
    void savesAndLoadsWizardDraftByDraftId() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        UUID draftId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        String json = """
                {
                  "authorName":"Alice",
                  "authorEmail":"alice@example.com",
                  "title":"Kickoff",
                  "description":"desc",
                  "eventType":"ALL_DAY",
                  "durationMinutes":null,
                  "dates":["2026-02-20"],
                  "startTimes":[],
                  "expiresAtOverride":null
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
        S3WizardStateRepository repository = new S3WizardStateRepository(s3Client, objectMapper, "woodle");

        WizardState state = new WizardState();
        state.setAuthorName("Alice");
        state.setAuthorEmail("alice@example.com");
        state.setTitle("Kickoff");
        state.setDescription("desc");
        state.setEventType(EventType.ALL_DAY);
        state.setDates(List.of(LocalDate.of(2026, 2, 20)));

        repository.save(draftId, state);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals("woodle", requestCaptor.getValue().bucket());
        assertEquals("drafts/" + draftId + ".json", requestCaptor.getValue().key());

        Optional<WizardState> loaded = repository.findById(draftId);
        assertTrue(loaded.isPresent());
        assertEquals("Alice", loaded.orElseThrow().authorName());
        assertEquals("Kickoff", loaded.orElseThrow().title());
    }

    @Test
    @DisplayName("returns empty for missing draft object")
    void returnsEmptyForMissingDraftObject() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3WizardStateRepository repository = new S3WizardStateRepository(s3Client, objectMapper, "woodle");

        Optional<WizardState> loaded = repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000302"));
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("create generates draft id and persists document")
    void createGeneratesDraftIdAndPersistsDocument() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3WizardStateRepository repository = new S3WizardStateRepository(s3Client, objectMapper, "woodle");

        WizardState state = new WizardState();
        state.setAuthorName("Alice");
        state.setEventType(EventType.ALL_DAY);
        state.setDates(List.of(LocalDate.of(2026, 2, 20)));

        UUID draftId = repository.create(state);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals("drafts/" + draftId + ".json", requestCaptor.getValue().key());
    }

    @Test
    @DisplayName("delete removes draft object from S3")
    void deleteRemovesDraftObjectFromS3() {
        S3Client s3Client = mock(S3Client.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3WizardStateRepository repository = new S3WizardStateRepository(s3Client, objectMapper, "woodle");
        UUID draftId = UUID.fromString("00000000-0000-0000-0000-000000000303");

        repository.delete(draftId);

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertEquals("drafts/" + draftId + ".json", requestCaptor.getValue().key());
        assertEquals("woodle", requestCaptor.getValue().bucket());
    }

    @Test
    @DisplayName("throws IllegalStateException when S3 get fails")
    void throwsIllegalStateExceptionWhenS3GetFails() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").build());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3WizardStateRepository repository = new S3WizardStateRepository(s3Client, objectMapper, "woodle");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000304"))
        );
        assertTrue(ex.getMessage().contains("Failed to fetch wizard draft"));
    }

    @Test
    @DisplayName("throws IllegalStateException when SDK fails")
    void throwsIllegalStateExceptionWhenSdkFails() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("network").build());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3WizardStateRepository repository = new S3WizardStateRepository(s3Client, objectMapper, "woodle");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000305"))
        );
        assertTrue(ex.getMessage().contains("Failed to fetch wizard draft"));
    }

    @Test
    @DisplayName("throws IllegalStateException when JSON is invalid")
    void throwsIllegalStateExceptionWhenJsonIsInvalid() {
        S3Client s3Client = mock(S3Client.class);
        String invalidJson = "{this-is-not-json}";
        GetObjectResponse response = GetObjectResponse.builder().build();
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(
                response,
                AbortableInputStream.create(new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8)))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3WizardStateRepository repository = new S3WizardStateRepository(s3Client, objectMapper, "woodle");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000306"))
        );
        assertTrue(ex.getMessage().contains("Failed to deserialize wizard draft"));
    }

    @Test
    @DisplayName("maps null date and time lists to empty collections")
    void mapsNullDateAndTimeListsToEmptyCollections() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        String json = """
                {
                  "authorName":"Alice",
                  "authorEmail":"alice@example.com",
                  "title":"Kickoff",
                  "description":"desc",
                  "eventType":"ALL_DAY",
                  "durationMinutes":null,
                  "dates":null,
                  "startTimes":null,
                  "expiresAtOverride":null
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
        S3WizardStateRepository repository = new S3WizardStateRepository(s3Client, objectMapper, "woodle");

        WizardState loaded = repository.findById(UUID.fromString("00000000-0000-0000-0000-000000000307"))
                .orElseThrow();
        assertEquals(List.of(), loaded.dates());
        assertEquals(List.of(), loaded.startTimes());
    }

    @Test
    @DisplayName("saves intraday draft with grouped days and times structure")
    void savesIntradayDraftWithGroupedDaysAndTimesStructure() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        S3WizardStateRepository repository = new S3WizardStateRepository(s3Client, objectMapper, "woodle");

        WizardState state = new WizardState();
        state.setEventType(EventType.INTRADAY);
        state.setDates(List.of(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 2)
        ));
        state.setStartTimes(List.of(
                LocalTime.of(19, 21),
                LocalTime.of(20, 21),
                LocalTime.of(19, 21)
        ));

        repository.save(UUID.fromString("00000000-0000-0000-0000-000000000308"), state);

        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(any(PutObjectRequest.class), bodyCaptor.capture());
        String json = new String(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);

        JsonNode days = root.get("days");
        assertEquals(2, days.size());
        assertEquals(null, root.get("dates"));
        assertEquals(null, root.get("startTimes"));
        assertEquals("2026-02-01", asIsoDate(days.get(0).get("day")));
        assertEquals("19:21:00", asIsoTime(days.get(0).get("times").get(0)));
        assertEquals("20:21:00", asIsoTime(days.get(0).get("times").get(1)));
        assertEquals("2026-02-02", asIsoDate(days.get(1).get("day")));
        assertEquals("19:21:00", asIsoTime(days.get(1).get("times").get(0)));
    }

    private String asIsoDate(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        return String.format("%04d-%02d-%02d", node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }

    private String asIsoTime(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        int hour = node.get(0).asInt();
        int minute = node.get(1).asInt();
        int second = node.size() > 2 ? node.get(2).asInt() : 0;
        return String.format("%02d:%02d:%02d", hour, minute, second);
    }
}
