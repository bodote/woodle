package io.github.bodote.woodle.application.service;

import io.github.bodote.woodle.application.port.in.AdminPollOptionsUseCase;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminPollOptionsService implements AdminPollOptionsUseCase {

    private final PollRepository pollRepository;

    public AdminPollOptionsService(PollRepository pollRepository) {
        this.pollRepository = pollRepository;
    }

    @Override
    public void addDate(UUID pollId, String adminSecret, LocalDate date, LocalTime startTime) {
        Poll poll = requireAdminPoll(pollId, adminSecret);
        if (poll.eventType() == EventType.INTRADAY && startTime == null) {
            throw new IllegalArgumentException("Start time is required for intraday polls");
        }
        LocalTime optionStartTime = poll.eventType() == EventType.INTRADAY ? startTime : null;
        LocalTime optionEndTime = optionStartTime == null || poll.durationMinutes() == null
                ? null
                : optionStartTime.plusMinutes(poll.durationMinutes());
        List<PollOption> options = new ArrayList<>(poll.options());
        options.add(new PollOption(UUID.randomUUID(), date, optionStartTime, optionEndTime));
        pollRepository.save(poll.withOptions(options));
    }

    @Override
    public void removeOption(UUID pollId, String adminSecret, LocalDate date, LocalTime startTime) {
        Poll poll = requireAdminPoll(pollId, adminSecret);
        List<PollOption> options = new ArrayList<>(poll.options());
        for (int i = 0; i < options.size(); i++) {
            PollOption option = options.get(i);
            if (!option.date().equals(date)) {
                continue;
            }
            if (startTime != null) {
                if (!startTime.equals(option.startTime())) {
                    continue;
                }
                options.remove(i);
                break;
            }
            if (option.startTime() == null) {
                options.remove(i);
                break;
            }
        }
        pollRepository.save(poll.withOptions(options));
    }

    private Poll requireAdminPoll(UUID pollId, String adminSecret) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
        if (!poll.adminSecret().equals(adminSecret)) {
            throw new IllegalArgumentException("Invalid admin secret");
        }
        return poll;
    }
}
