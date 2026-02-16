package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.out.WizardStateRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class PollNewPageController {

    private static final String STEP2_DATE_COUNT = "step2DateCount";
    private static final int STEP2_MIN_DATES = 1;
    private static final int STEP2_DEFAULT_DATES = 2;
    private static final int STEP2_MAX_DATES = 10;
    private static final String EMAIL_ERROR_MESSAGE = "Bitte eine gültige E-Mail-Adresse eingeben";
    private final WizardStateRepository wizardStateRepository;

    public PollNewPageController(WizardStateRepository wizardStateRepository) {
        this.wizardStateRepository = wizardStateRepository;
    }

    @GetMapping("/poll/new")
    public String renderStep1(HttpSession session) {
        getOrInitWizard(session);
        return "redirect:/poll/new-step1.html";
    }

    @GetMapping("/poll/step-1/email-check")
    public String validateEmail(@RequestParam("authorEmail") String authorEmail, Model model, HttpSession session) {
        WizardState state = getOrInitWizard(session);
        applyStep1Model(model, state, authorEmail, isInvalidEmail(authorEmail));
        return "poll/new-step1 :: emailField";
    }

    @GetMapping("/poll/step-2")
    public String renderStep2(Model model, HttpSession session) {
        int count = getOrInitDateCount(session);
        model.addAttribute("dateCount", count);
        WizardState state = getOrInitWizard(session);
        applyStep2Model(model, state);
        applyOptionValuesModel(model, state, Map.of(), count);
        return "poll/new-step2";
    }

    @PostMapping("/poll/step-2")
    public String handleStep1(
            @RequestParam("authorName") String authorName,
            @RequestParam("authorEmail") String authorEmail,
            @RequestParam("pollTitle") String pollTitle,
            @RequestParam(value = "description", required = false) String description,
            Model model,
            HttpSession session
    ) {
        if (isInvalidEmail(authorEmail)) {
            WizardState state = getOrInitWizard(session);
            state.setAuthorName(authorName);
            state.setAuthorEmail(authorEmail);
            state.setTitle(pollTitle);
            state.setDescription(description);
            applyStep1Model(model, state, authorEmail, true);
            return "poll/new-step1";
        }
        WizardState state = getOrInitWizard(session);
        state.setAuthorName(authorName);
        state.setAuthorEmail(authorEmail);
        state.setTitle(pollTitle);
        state.setDescription(description);
        UUID draftId = wizardStateRepository.create(state);
        session.setAttribute(WizardState.SESSION_KEY, state);
        model.addAttribute("dateCount", getOrInitDateCount(session));
        applyStep2Model(model, state);
        applyOptionValuesModel(model, state, Map.of(), getOrInitDateCount(session));
        model.addAttribute("draftId", draftId);
        return "poll/new-step2";
    }

    @GetMapping("/poll/step-2/options/add")
    public String addDateOption(Model model, HttpSession session, HttpServletRequest request) {
        int count = Math.min(getOrInitDateCount(session) + 1, STEP2_MAX_DATES);
        session.setAttribute(STEP2_DATE_COUNT, count);
        model.addAttribute("dateCount", count);
        WizardState state = getOrInitWizard(session);
        model.addAttribute("eventType", state.eventType());
        applyOptionValuesModel(model, state, request.getParameterMap(), count);
        return resolveOptionsFragment(state);
    }

    @GetMapping("/poll/step-2/options/remove")
    public String removeDateOption(Model model, HttpSession session, HttpServletRequest request) {
        int count = Math.max(getOrInitDateCount(session) - 1, STEP2_MIN_DATES);
        session.setAttribute(STEP2_DATE_COUNT, count);
        model.addAttribute("dateCount", count);
        WizardState state = getOrInitWizard(session);
        model.addAttribute("eventType", state.eventType());
        applyOptionValuesModel(model, state, request.getParameterMap(), count);
        return resolveOptionsFragment(state);
    }

    @GetMapping("/poll/step-2/event-type")
    public String setEventType(@RequestParam("eventType") io.github.bodote.woodle.domain.model.EventType eventType,
                               Model model,
                               HttpSession session,
                               HttpServletRequest request) {
        WizardState state = getOrInitWizard(session);
        state.setEventType(eventType);
        session.setAttribute(WizardState.SESSION_KEY, state);
        model.addAttribute("eventType", eventType);
        int count = getOrInitDateCount(session);
        model.addAttribute("dateCount", count);
        applyOptionValuesModel(model, state, request.getParameterMap(), count);
        return "poll/step2-event-options :: eventOptions";
    }

    @GetMapping("/poll/step-3")
    public String renderStep3(@RequestParam(value = "draftId", required = false) UUID draftId,
                              Model model,
                              HttpSession session) {
        WizardState state = resolveWizardState(session, draftId);
        if (state == null) {
            return "redirect:/poll/new";
        }
        populateStep3Model(model, state);
        model.addAttribute("draftId", draftId);
        return "poll/new-step3";
    }

    @PostMapping("/poll/step-3")
    public String handleStep2(
            @RequestParam(value = "draftId", required = false) UUID draftId,
            @RequestParam(value = "eventType", defaultValue = "ALL_DAY") io.github.bodote.woodle.domain.model.EventType eventType,
            @RequestParam(value = "durationMinutes", required = false) Integer durationMinutes,
            @RequestParam(value = "authorName", required = false) String authorName,
            @RequestParam(value = "authorEmail", required = false) String authorEmail,
            @RequestParam(value = "pollTitle", required = false) String pollTitle,
            @RequestParam(value = "description", required = false) String description,
            HttpSession session,
            Model model,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        WizardState state = resolveWizardState(session, draftId);
        if (state == null) {
            state = new WizardState();
        }
        applyStep1Fallback(state, authorName, authorEmail, pollTitle, description);
        state.setEventType(eventType);
        state.setDurationMinutes(durationMinutes);
        state.setDates(extractDates(request.getParameterMap()));
        state.setStartTimes(extractStartTimes(request.getParameterMap(), eventType));
        session.setAttribute(WizardState.SESSION_KEY, state);
        if (draftId != null) {
            try {
                wizardStateRepository.save(draftId, state);
            } catch (IllegalStateException ignored) {
                // Keep the flow alive even if draft persistence is temporarily unavailable.
            }
        }
        populateStep3Model(model, state);
        model.addAttribute("draftId", draftId);
        return "poll/new-step3";
    }

    private void populateStep3Model(Model model, WizardState state) {
        model.addAttribute("authorName", state.authorName());
        model.addAttribute("authorEmail", state.authorEmail());
        model.addAttribute("pollTitle", state.title());
        model.addAttribute("description", state.description());
        model.addAttribute("dates", state.dates());
        model.addAttribute("eventType", state.eventType());
        model.addAttribute("durationMinutes", state.durationMinutes());
        model.addAttribute("startTimes", state.startTimes());
        if (state.eventType() == io.github.bodote.woodle.domain.model.EventType.INTRADAY) {
            model.addAttribute("dateSummaries", buildIntradaySummaries(state));
        }
        if (!state.dates().isEmpty()) {
            LocalDate lastDate = state.dates().stream().max(Comparator.naturalOrder()).orElse(null);
            if (lastDate != null) {
                model.addAttribute("expiresAt", lastDate.plusWeeks(4));
            }
        }
    }

    private WizardState getOrInitWizard(HttpSession session) {
        Object value = session.getAttribute(WizardState.SESSION_KEY);
        if (value instanceof WizardState state) {
            return state;
        }
        WizardState state = new WizardState();
        session.setAttribute(WizardState.SESSION_KEY, state);
        return state;
    }

    private WizardState resolveWizardState(HttpSession session, UUID draftId) {
        Object value = session.getAttribute(WizardState.SESSION_KEY);
        if (value instanceof WizardState state) {
            return state;
        }
        if (draftId == null) {
            return null;
        }
        try {
            return wizardStateRepository.findById(draftId)
                    .map(state -> {
                        session.setAttribute(WizardState.SESSION_KEY, state);
                        return state;
                    })
                    .orElse(null);
        } catch (IllegalStateException ignored) {
            return null;
        }
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

    private List<LocalTime> extractStartTimes(Map<String, String[]> parameterMap,
                                              io.github.bodote.woodle.domain.model.EventType eventType) {
        if (eventType != io.github.bodote.woodle.domain.model.EventType.INTRADAY) {
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

    private List<String> buildIntradaySummaries(WizardState state) {
        List<String> summaries = new ArrayList<>();
        List<LocalDate> dates = state.dates();
        List<LocalTime> times = state.startTimes();
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            LocalTime time = i < times.size() ? times.get(i) : null;
            if (time != null) {
                summaries.add(date + " " + time);
            } else {
                summaries.add(date.toString());
            }
        }
        return summaries;
    }

    private void applyStep1Model(Model model, WizardState state, String emailOverride, boolean emailError) {
        model.addAttribute("authorName", state.authorName());
        model.addAttribute("authorEmail", emailOverride != null ? emailOverride : state.authorEmail());
        model.addAttribute("pollTitle", state.title());
        model.addAttribute("description", state.description());
        model.addAttribute("emailError", emailError);
        if (emailError) {
            model.addAttribute("emailErrorMessage", EMAIL_ERROR_MESSAGE);
        }
    }

    private void applyStep2Model(Model model, WizardState state) {
        model.addAttribute("eventType", state.eventType());
        model.addAttribute("authorName", state.authorName());
        model.addAttribute("authorEmail", state.authorEmail());
        model.addAttribute("pollTitle", state.title());
        model.addAttribute("description", state.description());
        model.addAttribute("durationMinutes", state.durationMinutes());
    }

    private void applyOptionValuesModel(Model model, WizardState state, Map<String, String[]> parameterMap, int count) {
        List<String> dateValues = extractInputValues(parameterMap, "dateOption", count);
        if (allBlank(dateValues)) {
            dateValues = dateValuesFromState(state, count);
        }
        model.addAttribute("dateValues", dateValues);

        Integer durationMinutes = firstInteger(parameterMap, "durationMinutes");
        model.addAttribute("durationMinutes", durationMinutes != null ? durationMinutes : state.durationMinutes());

        if (state.eventType() == io.github.bodote.woodle.domain.model.EventType.INTRADAY) {
            List<String> startTimeValues = extractInputValues(parameterMap, "startTime", count);
            if (allBlank(startTimeValues)) {
                startTimeValues = startTimeValuesFromState(state, count);
            }
            model.addAttribute("startTimeValues", startTimeValues);
        }
    }

    private List<String> extractInputValues(Map<String, String[]> parameterMap, String prefix, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String[] raw = parameterMap.get(prefix + i);
            if (raw == null || raw.length == 0 || raw[0] == null) {
                values.add("");
                continue;
            }
            values.add(raw[0]);
        }
        return values;
    }

    private List<String> dateValuesFromState(WizardState state, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i < state.dates().size()) {
                values.add(state.dates().get(i).toString());
            } else {
                values.add("");
            }
        }
        return values;
    }

    private List<String> startTimeValuesFromState(WizardState state, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i < state.startTimes().size()) {
                values.add(state.startTimes().get(i).toString());
            } else {
                values.add("");
            }
        }
        return values;
    }

    private boolean allBlank(List<String> values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Integer firstInteger(Map<String, String[]> parameterMap, String key) {
        String[] raw = parameterMap.get(key);
        if (raw == null || raw.length == 0 || raw[0] == null || raw[0].isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw[0]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void applyStep1Fallback(WizardState state,
                                    String authorName,
                                    String authorEmail,
                                    String pollTitle,
                                    String description) {
        if (authorName != null && !authorName.isBlank()) {
            state.setAuthorName(authorName);
        }
        if (authorEmail != null && !authorEmail.isBlank()) {
            state.setAuthorEmail(authorEmail);
        }
        if (pollTitle != null && !pollTitle.isBlank()) {
            state.setTitle(pollTitle);
        }
        if (description != null) {
            state.setDescription(description);
        }
    }

    private boolean isInvalidEmail(String authorEmail) {
        if (authorEmail == null || authorEmail.isBlank()) {
            return true;
        }
        String trimmed = authorEmail.trim();
        return !trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private String resolveOptionsFragment(WizardState state) {
        if (state.eventType() == io.github.bodote.woodle.domain.model.EventType.INTRADAY) {
            return "poll/step2-datetime-options :: dateTimeOptions";
        }
        return "poll/step2-date-options :: dateOptions";
    }

    private int getOrInitDateCount(HttpSession session) {
        Object value = session.getAttribute(STEP2_DATE_COUNT);
        if (value instanceof Integer count) {
            return count;
        }
        session.setAttribute(STEP2_DATE_COUNT, STEP2_DEFAULT_DATES);
        return STEP2_DEFAULT_DATES;
    }
}
