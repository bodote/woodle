package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.application.port.in.CreatePollResult;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import io.github.bodote.woodle.application.port.out.PollCreatedEmail;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class CreatePollService implements CreatePollUseCase {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int ADMIN_SECRET_LENGTH = 12;

    private final PollRepository pollRepository;
    private final PollEmailSender pollEmailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public CreatePollService(PollRepository pollRepository, PollEmailSender pollEmailSender) {
        this.pollRepository = pollRepository;
        this.pollEmailSender = pollEmailSender;
    }

    @Override
    public CreatePollResult create(CreatePollCommand command) {
        UUID pollId = UUID.randomUUID();
        String adminSecret = generateAdminSecret();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<PollOption> options = buildOptions(command);
        LocalDate expiresAt = calculateExpiry(command.dates(), command.expiresAtOverride());

        Poll poll = new Poll(
                pollId,
                adminSecret,
                command.title(),
                command.description(),
                command.authorName(),
                command.authorEmail(),
                command.eventType(),
                command.durationMinutes(),
                options,
                List.of(),
                now,
                now,
                expiresAt
        );

        pollRepository.save(poll);
        boolean notificationQueued = pollEmailSender.sendPollCreated(new PollCreatedEmail(
                pollId,
                adminSecret,
                command.authorName(),
                command.authorEmail(),
                command.title()
        ));
        return new CreatePollResult(pollId, adminSecret, notificationQueued);
    }

    private List<PollOption> buildOptions(CreatePollCommand command) {
        List<LocalDate> dates = command.dates();
        List<LocalTime> times = command.startTimes();
        Integer durationMinutes = command.durationMinutes();
        return java.util.stream.IntStream.range(0, dates.size())
                .mapToObj(index -> {
                    LocalDate date = dates.get(index);
                    LocalTime startTime = index < times.size() ? times.get(index) : null;
                    LocalTime endTime = startTime == null || durationMinutes == null
                            ? null
                            : startTime.plusMinutes(durationMinutes);
                    return new PollOption(UUID.randomUUID(), date, startTime, endTime);
                })
                .toList();
    }

    private String generateAdminSecret() {
        StringBuilder builder = new StringBuilder(ADMIN_SECRET_LENGTH);
        for (int i = 0; i < ADMIN_SECRET_LENGTH; i++) {
            builder.append(BASE62.charAt(secureRandom.nextInt(BASE62.length())));
        }
        return builder.toString();
    }

    private LocalDate calculateExpiry(List<LocalDate> dates, LocalDate override) {
        if (override != null) {
            return override;
        }
        LocalDate lastDate = dates.stream()
                .max(Comparator.naturalOrder())
                .orElse(LocalDate.now(ZoneOffset.UTC));
        return lastDate.plusWeeks(4);
    }
}
