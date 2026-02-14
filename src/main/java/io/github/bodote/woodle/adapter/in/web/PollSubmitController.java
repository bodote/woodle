package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.CreatePollResult;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import io.github.bodote.woodle.application.port.out.WizardStateRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpSession;

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
            HttpSession session
    ) {
        WizardState state = null;
        if (draftId != null) {
            state = wizardStateRepository.findById(draftId).orElse(null);
        }
        if (state == null) {
            Object value = session.getAttribute(WizardState.SESSION_KEY);
            if (value instanceof WizardState fromSession) {
                state = fromSession;
            }
        }
        if (state == null) {
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
        return "redirect:/poll/" + result.pollId() + "-" + result.adminSecret();
    }
}
