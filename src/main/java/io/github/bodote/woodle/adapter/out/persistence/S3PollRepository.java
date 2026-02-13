package io.github.bodote.woodle.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.io.IOException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import java.time.LocalTime;
import software.amazon.awssdk.core.exception.SdkException;

public class S3PollRepository implements PollRepository {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final String bucketName;

    public S3PollRepository(S3Client s3Client, ObjectMapper objectMapper, String bucketName) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.bucketName = bucketName;
    }

    @Override
    public void save(Poll poll) {
        PollDAO pollDAO = toDao(poll);
        String json = writeJson(pollDAO);
        String key = "polls/" + poll.pollId() + ".json";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/json")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Optional<Poll> findById(UUID pollId) {
        String key = "polls/" + pollId + ".json";
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            String json = new String(response.readAllBytes(), StandardCharsets.UTF_8);
            PollDAO pollDAO = objectMapper.readValue(json, PollDAO.class);
            return Optional.of(fromDao(pollDAO));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            throw new IllegalStateException("Failed to fetch poll from S3", e);
        } catch (SdkException e) {
            throw new IllegalStateException("Failed to fetch poll from S3", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize poll", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to deserialize poll", e);
        }
    }

    private String writeJson(PollDAO pollDAO) {
        try {
            return objectMapper.writeValueAsString(pollDAO);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize poll", e);
        }
    }

    private PollDAO toDao(Poll poll) {
        List<PollDAO.OptionItem> items = poll.options().stream()
                .map(this::toOptionItem)
                .toList();

        List<PollDAO.Response> responses = poll.responses().stream()
                .map(this::toResponse)
                .toList();

        return new PollDAO(
                poll.pollId(),
                "date",
                poll.title(),
                poll.description(),
                "de",
                poll.createdAt(),
                poll.updatedAt(),
                new PollDAO.Author(poll.authorName(), poll.authorEmail()),
                new PollDAO.Access(null, null, true, poll.adminSecret()),
                new PollDAO.Permissions("ALL_CAN_EDIT"),
                new PollDAO.Notifications(false, false),
                new PollDAO.ResultsVisibility(false),
                "OPEN",
                poll.expiresAt(),
                new PollDAO.Options(poll.eventType().name(), poll.durationMinutes(), items),
                responses
        );
    }

    private PollDAO.OptionItem toOptionItem(PollOption option) {
        String startTime = option.startTime() == null ? null : option.startTime().toString();
        String endTime = option.endTime() == null ? null : option.endTime().toString();
        return new PollDAO.OptionItem(option.optionId(), option.date(), startTime, endTime);
    }

    private Poll fromDao(PollDAO pollDAO) {
        List<PollOption> options = pollDAO.options().items().stream()
                .map(item -> new PollOption(item.optionId(), item.date(), parseTime(item.startTime()), parseTime(item.endTime())))
                .toList();
        List<PollResponse> responses = pollDAO.responses().stream()
                .map(this::fromResponse)
                .toList();
        return new Poll(
                pollDAO.pollId(),
                pollDAO.access().adminToken(),
                pollDAO.title(),
                pollDAO.descriptionHtml(),
                pollDAO.author().name(),
                pollDAO.author().email(),
                io.github.bodote.woodle.domain.model.EventType.valueOf(pollDAO.options().eventType()),
                pollDAO.options().durationMinutes(),
                options,
                responses,
                pollDAO.createdAt(),
                pollDAO.updatedAt(),
                pollDAO.expiresAt()
        );
    }

    private PollDAO.Response toResponse(PollResponse response) {
        List<PollDAO.Vote> votes = response.votes().stream()
                .map(vote -> new PollDAO.Vote(vote.optionId(), vote.value().name()))
                .toList();
        return new PollDAO.Response(response.responseId(), response.participantName(), response.createdAt(), votes,
                response.comment());
    }

    private PollResponse fromResponse(PollDAO.Response response) {
        List<PollVote> votes = response.votes().stream()
                .map(vote -> new PollVote(vote.optionId(), PollVoteValue.valueOf(vote.value())))
                .toList();
        return new PollResponse(response.responseId(), response.participantName(), response.createdAt(), votes,
                response.comment());
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalTime.parse(value);
    }
}
