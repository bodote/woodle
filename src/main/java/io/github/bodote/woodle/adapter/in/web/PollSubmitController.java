package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.CreatePollResult;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpSession;

@Controller
public class PollSubmitController {

    private final CreatePollUseCase createPollUseCase;

    public PollSubmitController(CreatePollUseCase createPollUseCase) {
        this.createPollUseCase = createPollUseCase;
    }

    @PostMapping("/poll/submit")
    public String submit(
            @org.springframework.web.bind.annotation.RequestParam(value = "expiresAt", required = false) java.time.LocalDate expiresAt,
            HttpSession session
    ) {
        Object value = session.getAttribute(WizardState.SESSION_KEY);
        if (!(value instanceof WizardState state)) {
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
        session.removeAttribute(WizardState.SESSION_KEY);
        return "redirect:/poll/" + result.pollId() + "-" + result.adminSecret();
    }
}
