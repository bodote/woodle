package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.AdminPollOptionsUseCase;
import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Controller
public class PollAdminOptionsController {

    private final AdminPollOptionsUseCase adminPollOptionsUseCase;
    private final ReadPollUseCase readPollUseCase;

    public PollAdminOptionsController(AdminPollOptionsUseCase adminPollOptionsUseCase, ReadPollUseCase readPollUseCase) {
        this.adminPollOptionsUseCase = adminPollOptionsUseCase;
        this.readPollUseCase = readPollUseCase;
    }

    @PostMapping("/poll/{pollId:[0-9a-fA-F\\-]{36}}-{adminSecret}/options/add")
    public String addOption(
            @PathVariable UUID pollId,
            @PathVariable String adminSecret,
            @RequestParam("date") LocalDate date,
            @RequestParam(value = "startTime", required = false) List<LocalTime> startTimes,
            Model model
    ) {
        Poll existingPoll = readPollUseCase.getAdmin(pollId, adminSecret);
        if (existingPoll.eventType() == EventType.INTRADAY) {
            if (startTimes == null || startTimes.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time is required for intraday polls");
            }
            for (LocalTime startTime : startTimes) {
                adminPollOptionsUseCase.addDate(pollId, adminSecret, date, startTime);
            }
        } else {
            adminPollOptionsUseCase.addDate(pollId, adminSecret, date, null);
        }
        Poll poll = readPollUseCase.getAdmin(pollId, adminSecret);
        model.addAttribute("poll", poll);
        model.addAttribute("adminView", true);
        model.addAttribute("pollId", pollId);
        model.addAttribute("adminSecret", adminSecret);
        return "poll/options-list :: optionsList";
    }

    @PostMapping("/poll/{pollId:[0-9a-fA-F\\-]{36}}-{adminSecret}/options/remove")
    public String removeOption(
            @PathVariable UUID pollId,
            @PathVariable String adminSecret,
            @RequestParam("date") LocalDate date,
            @RequestParam(value = "startTime", required = false) LocalTime startTime,
            Model model
    ) {
        adminPollOptionsUseCase.removeOption(pollId, adminSecret, date, startTime);
        Poll poll = readPollUseCase.getAdmin(pollId, adminSecret);
        model.addAttribute("poll", poll);
        model.addAttribute("adminView", true);
        model.addAttribute("pollId", pollId);
        model.addAttribute("adminSecret", adminSecret);
        return "poll/options-list :: optionsList";
    }
}
