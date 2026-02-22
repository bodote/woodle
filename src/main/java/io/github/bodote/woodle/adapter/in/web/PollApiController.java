package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.CreatePollResult;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class PollApiController {

    private final CreatePollUseCase createPollUseCase;
    private final ReadPollUseCase readPollUseCase;
    private final PollRepository pollRepository;

    public PollApiController(CreatePollUseCase createPollUseCase,
                             ReadPollUseCase readPollUseCase,
                             PollRepository pollRepository) {
        this.createPollUseCase = createPollUseCase;
        this.readPollUseCase = readPollUseCase;
        this.pollRepository = pollRepository;
    }

    @PostMapping("/v1/polls")
    public ResponseEntity<CreatePollResponseDTO> createPoll(@RequestBody CreatePollRequestDTO requestDTO) {
        CreatePollCommand command = new CreatePollCommand(
                requestDTO.authorName(),
                requestDTO.authorEmail(),
                requestDTO.title(),
                requestDTO.description(),
                requestDTO.eventType(),
                requestDTO.durationMinutes(),
                requestDTO.dates(),
                requestDTO.startTimes() == null ? List.of() : requestDTO.startTimes(),
                requestDTO.expiresAtOverride()
        );

        CreatePollResult result = createPollUseCase.create(command);
        String pollId = result.pollId().toString();

        CreatePollResponseDTO responseDTO = new CreatePollResponseDTO(
                pollId,
                "/poll/" + pollId + "-" + result.adminSecret(),
                "/poll/" + pollId,
                result.notificationQueued()
        );

        return ResponseEntity.created(URI.create("/v1/polls/" + pollId))
                .body(responseDTO);
    }

    @GetMapping("/v1/polls/{pollId}")
    public PollResponseDTO getPoll(@PathVariable UUID pollId) {
        try {
            Poll poll = readPollUseCase.getPublic(pollId);
            List<PollOptionResponseDTO> options = poll.options().stream()
                    .map(this::toOptionResponse)
                    .toList();
            return new PollResponseDTO(
                    poll.pollId().toString(),
                    poll.title(),
                    poll.description(),
                    poll.eventType().name(),
                    poll.durationMinutes(),
                    options,
                    poll.expiresAt()
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found", ex);
        }
    }

    @GetMapping(value = {"/v1/polls/active-count", "/poll/active-count"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public String getActivePollCount() {
        try {
            return Long.toString(pollRepository.countActivePolls());
        } catch (IllegalStateException ignored) {
            return "0";
        }
    }

    private PollOptionResponseDTO toOptionResponse(PollOption option) {
        String startTime = option.startTime() == null ? null : option.startTime().toString();
        String endTime = option.endTime() == null ? null : option.endTime().toString();
        return new PollOptionResponseDTO(option.optionId().toString(), option.date(), startTime, endTime);
    }
}
