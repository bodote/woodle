package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.model.WizardState;
import io.github.bodote.woodle.application.port.in.CreatePollResult;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import io.github.bodote.woodle.application.port.out.WizardStateRepository;
import io.github.bodote.woodle.domain.model.EventType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class PollSubmitController {

    private final CreatePollUseCase createPollUseCase;
    private final WizardStateRepository wizardStateRepository;

    public PollSubmitController(CreatePollUseCase createPollUseCase,
                                WizardStateRepository wizardStateRepository) {
        this.createPollUseCase = createPollUseCase;
        this.wizardStateRepository = wizardStateRepository;
    }

    @PostMapping("/poll/submit")
    public String submit(
            @org.springframework.web.bind.annotation.RequestParam(value = "draftId", required = false) UUID draftId,
            @org.springframework.web.bind.annotation.RequestParam(value = "expiresAt", required = false) java.time.LocalDate expiresAt,
            @org.springframework.web.bind.annotation.RequestParam(value = "authorName", required = false) String authorName,
            @org.springframework.web.bind.annotation.RequestParam(value = "authorEmail", required = false) String authorEmail,
            @org.springframework.web.bind.annotation.RequestParam(value = "pollTitle", required = false) String pollTitle,
            @org.springframework.web.bind.annotation.RequestParam(value = "description", required = false) String description,
            @org.springframework.web.bind.annotation.RequestParam(value = "eventType", required = false) EventType eventType,
            @org.springframework.web.bind.annotation.RequestParam(value = "durationMinutes", required = false) Integer durationMinutes,
            HttpServletRequest request,
            HttpSession session
    ) {
        WizardState state = null;
        if (draftId != null) {
            try {
                state = wizardStateRepository.findById(draftId).orElse(null);
            } catch (IllegalStateException ignored) {
                state = null;
            }
        }
        if (state == null) {
            Object value = session.getAttribute(WizardState.SESSION_KEY);
            if (value instanceof WizardState fromSession) {
                state = fromSession;
            }
        }
        if (state == null) {
            state = new WizardState();
        }
        applySubmitFallback(state, authorName, authorEmail, pollTitle, description, eventType, durationMinutes, request);
        if (isMissingRequiredBasics(state)) {
            return "redirect:/poll/new";
        }
        state.setExpiresAtOverride(expiresAt);

        CreatePollCommand command = new CreatePollCommand(
                state.authorName(),
                state.authorEmail(),
                state.title(),
                state.description(),
                state.eventType(),
                state.durationMinutes(),
                state.dates(),
                state.startTimes(),
                state.expiresAtOverride()
        );
        CreatePollResult result = createPollUseCase.create(command);
        if (draftId != null) {
            wizardStateRepository.delete(draftId);
        }
        session.removeAttribute(WizardState.SESSION_KEY);
        String target = "/poll/" + result.pollId() + "-" + result.adminSecret();
        if (result.notificationDisabled()) {
            target = target + "?emailDisabled=true";
        } else if (!result.notificationQueued()) {
            target = target + "?emailFailed=true";
        }
        return "redirect:" + target;
    }

    private void applySubmitFallback(WizardState state,
                                     String authorName,
                                     String authorEmail,
                                     String pollTitle,
                                     String description,
                                     EventType eventType,
                                     Integer durationMinutes,
                                     HttpServletRequest request) {
        if (state.authorName() == null && authorName != null && !authorName.isBlank()) {
            state.setAuthorName(authorName);
        }
        if (state.authorEmail() == null && authorEmail != null && !authorEmail.isBlank()) {
            state.setAuthorEmail(authorEmail);
        }
        if (state.title() == null && pollTitle != null && !pollTitle.isBlank()) {
            state.setTitle(pollTitle);
        }
        if (state.description() == null && description != null) {
            state.setDescription(description);
        }
        if (eventType != null) {
            state.setEventType(eventType);
        }
        if (state.durationMinutes() == null && durationMinutes != null) {
            state.setDurationMinutes(durationMinutes);
        }
        if (state.dates().isEmpty()) {
            state.setDates(extractDates(request.getParameterMap()));
        }
        if (state.startTimes().isEmpty()) {
            state.setStartTimes(extractStartTimes(request.getParameterMap(), state.eventType()));
        }
    }

    private boolean isMissingRequiredBasics(WizardState state) {
        return state.authorName() == null || state.authorName().isBlank()
                || state.authorEmail() == null || state.authorEmail().isBlank()
                || state.title() == null || state.title().isBlank();
    }

    private List<LocalDate> extractDates(Map<String, String[]> parameterMap) {
        List<Map.Entry<String, String[]>> entries = new ArrayList<>(parameterMap.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        List<LocalDate> dates = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : entries) {
            if (!entry.getKey().startsWith("dateOption")) {
                continue;
            }
            if (entry.getValue().length == 0) {
                continue;
            }
            String value = entry.getValue()[0];
            if (value == null || value.isBlank()) {
                continue;
            }
            dates.add(LocalDate.parse(value));
        }
        return dates;
    }

    private List<LocalTime> extractStartTimes(Map<String, String[]> parameterMap, EventType eventType) {
        if (eventType != EventType.INTRADAY) {
            return List.of();
        }
        List<Map.Entry<String, String[]>> entries = new ArrayList<>(parameterMap.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        List<LocalTime> times = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : entries) {
            if (!entry.getKey().startsWith("startTime")) {
                continue;
            }
            if (entry.getValue().length == 0) {
                continue;
            }
            String value = entry.getValue()[0];
            if (value == null || value.isBlank()) {
                continue;
            }
            times.add(LocalTime.parse(value));
        }
        return times;
    }
}
