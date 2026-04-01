package io.github.bodote.woodle.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bodote.woodle.application.model.WizardState;
import io.github.bodote.woodle.application.port.out.WizardStateRepository;
import io.github.bodote.woodle.domain.model.EventType;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public class S3WizardStateRepository implements WizardStateRepository {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final String bucketName;

    public S3WizardStateRepository(S3Client s3Client, ObjectMapper objectMapper, String bucketName) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.bucketName = bucketName;
    }

    @Override
    public UUID create(WizardState state) {
        UUID draftId = UUID.randomUUID();
        save(draftId, state);
        return draftId;
    }

    @Override
    public void save(UUID draftId, WizardState state) {
        String key = key(draftId);
        String json = writeJson(WizardStateDocument.from(WizardState.copyOf(state)));
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/json")
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Optional<WizardState> findById(UUID draftId) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key(draftId))
                .build();
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            String json = new String(response.readAllBytes(), StandardCharsets.UTF_8);
            WizardStateDocument document = objectMapper.readValue(json, WizardStateDocument.class);
            return Optional.of(document.toWizardState());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            throw new IllegalStateException("Failed to fetch wizard draft from S3", e);
        } catch (SdkException e) {
            throw new IllegalStateException("Failed to fetch wizard draft from S3", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize wizard draft", e);
        }
    }

    @Override
    public void delete(UUID draftId) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key(draftId))
                .build();
        s3Client.deleteObject(request);
    }

    private String writeJson(WizardStateDocument state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize wizard draft", e);
        }
    }

    private String key(UUID draftId) {
        return "drafts/" + draftId + ".json";
    }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private record WizardStateDocument(
            String authorName,
            String authorEmail,
            String title,
            String description,
            EventType eventType,
            Integer durationMinutes,
            java.util.List<java.time.LocalDate> dates,
            java.util.List<java.time.LocalTime> startTimes,
            java.util.List<WizardDayDocument> days,
            java.time.LocalDate expiresAtOverride
    ) {
        static WizardStateDocument from(WizardState state) {
            return new WizardStateDocument(
                    state.authorName(),
                    state.authorEmail(),
                    state.title(),
                    state.description(),
                    state.eventType(),
                    state.durationMinutes(),
                    null,
                    null,
                    toDayDocuments(state),
                    state.expiresAtOverride()
            );
        }

        WizardState toWizardState() {
            WizardState state = new WizardState();
            state.setAuthorName(authorName);
            state.setAuthorEmail(authorEmail);
            state.setTitle(title);
            state.setDescription(description);
            state.setEventType(eventType);
            state.setDurationMinutes(durationMinutes);
            if (days != null && !days.isEmpty()) {
                LegacySelection legacySelection = toLegacySelection(days, eventType);
                state.setDates(legacySelection.dates());
                state.setStartTimes(legacySelection.startTimes());
            } else {
                state.setDates(dates == null ? java.util.List.of() : dates);
                state.setStartTimes(startTimes == null ? java.util.List.of() : startTimes);
            }
            state.setExpiresAtOverride(expiresAtOverride);
            return state;
        }

        private static java.util.List<WizardDayDocument> toDayDocuments(WizardState state) {
            java.util.List<WizardDayDocument> values = new java.util.ArrayList<>();
            if (state.eventType() == EventType.INTRADAY) {
                java.time.LocalDate previousDate = null;
                java.util.List<java.time.LocalTime> currentTimes = new java.util.ArrayList<>();
                int startTimeIndex = 0;
                for (java.time.LocalDate date : state.dates()) {
                    if (previousDate == null || !previousDate.equals(date)) {
                        if (previousDate != null) {
                            values.add(new WizardDayDocument(previousDate, currentTimes));
                        }
                        previousDate = date;
                        currentTimes = new java.util.ArrayList<>();
                    }
                    if (startTimeIndex < state.startTimes().size()) {
                        currentTimes.add(state.startTimes().get(startTimeIndex));
                        startTimeIndex++;
                    }
                }
                if (previousDate != null) {
                    values.add(new WizardDayDocument(previousDate, currentTimes));
                }
                return values;
            }
            for (java.time.LocalDate date : state.dates()) {
                values.add(new WizardDayDocument(date, java.util.List.of()));
            }
            return values;
        }

        private static LegacySelection toLegacySelection(java.util.List<WizardDayDocument> days, EventType eventType) {
            java.util.List<java.time.LocalDate> selectionDates = new java.util.ArrayList<>();
            java.util.List<java.time.LocalTime> selectionStartTimes = new java.util.ArrayList<>();
            for (WizardDayDocument day : days) {
                if (day.day() == null) {
                    continue;
                }
                java.util.List<java.time.LocalTime> times = day.times() == null ? java.util.List.of() : day.times();
                if (eventType == EventType.INTRADAY && !times.isEmpty()) {
                    for (java.time.LocalTime time : times) {
                        selectionDates.add(day.day());
                        selectionStartTimes.add(time);
                    }
                } else {
                    selectionDates.add(day.day());
                }
            }
            return new LegacySelection(selectionDates, selectionStartTimes);
        }
    }

    private record WizardDayDocument(
            java.time.LocalDate day,
            java.util.List<java.time.LocalTime> times
    ) {
    }

    private record LegacySelection(
            java.util.List<java.time.LocalDate> dates,
            java.util.List<java.time.LocalTime> startTimes
    ) {
    }
}
