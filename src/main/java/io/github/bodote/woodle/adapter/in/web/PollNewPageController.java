package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.model.WizardState;
import io.github.bodote.woodle.application.port.out.WizardStateRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;

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
    private static final String STEP2_TIME_COUNT_BY_DAY = "step2TimeCountByDay";
    private static final int STEP2_MIN_DATES = 1;
    private static final int STEP2_DEFAULT_DATES = 2;
    private static final int STEP2_MAX_DATES = 10;
    private static final int STEP2_MIN_TIMES_PER_DAY = 1;
    private static final int STEP2_MAX_TIMES_PER_DAY = 10;
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
        WizardState state = getOrInitWizard(session);
        int count = Math.max(getOrInitDateCount(session), state.dates().size());
        List<Integer> timeCountByDay;
        if (state.eventType() == io.github.bodote.woodle.domain.model.EventType.INTRADAY && !state.dates().isEmpty()) {
            count = intradayDayCountFromState(state);
            timeCountByDay = intradayTimeCountByDayFromState(state, count);
            session.setAttribute(STEP2_TIME_COUNT_BY_DAY, timeCountByDay);
        } else {
            timeCountByDay = getOrInitTimeCountByDay(session, count);
        }
        session.setAttribute(STEP2_DATE_COUNT, count);
        model.addAttribute("dateCount", count);
        applyStep2Model(model, state);
        applyOptionValuesModel(model, state, Map.of(), count, timeCountByDay);
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
        UUID draftId = null;
        try {
            draftId = wizardStateRepository.create(state);
        } catch (IllegalStateException ignored) {
            // Keep step-2 usable when draft persistence is temporarily unavailable.
        }
        session.setAttribute(WizardState.SESSION_KEY, state);
        model.addAttribute("dateCount", getOrInitDateCount(session));
        applyStep2Model(model, state);
        int dateCount = getOrInitDateCount(session);
        List<Integer> timeCountByDay = getOrInitTimeCountByDay(session, dateCount);
        applyOptionValuesModel(model, state, Map.of(), dateCount, timeCountByDay);
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
        List<Integer> timeCountByDay = getOrInitTimeCountByDay(session, count);
        applyOptionValuesModel(model, state, request.getParameterMap(), count, timeCountByDay);
        return resolveOptionsFragment(state);
    }

    @GetMapping("/poll/step-2/options/remove")
    public String removeDateOption(Model model, HttpSession session, HttpServletRequest request) {
        int count = Math.max(getOrInitDateCount(session) - 1, STEP2_MIN_DATES);
        session.setAttribute(STEP2_DATE_COUNT, count);
        model.addAttribute("dateCount", count);
        WizardState state = getOrInitWizard(session);
        model.addAttribute("eventType", state.eventType());
        List<Integer> timeCountByDay = getOrInitTimeCountByDay(session, count);
        applyOptionValuesModel(model, state, request.getParameterMap(), count, timeCountByDay);
        return resolveOptionsFragment(state);
    }

    @GetMapping("/poll/step-2/options/time/add")
    public String addIntradayTimeOption(@RequestParam("day") int day,
                                        Model model,
                                        HttpSession session,
                                        HttpServletRequest request) {
        WizardState state = getOrInitWizard(session);
        int count = getOrInitDateCount(session);
        List<Integer> timeCountByDay = getOrInitTimeCountByDay(session, count);
        if (day >= 1 && day <= count) {
            int current = timeCountByDay.get(day - 1);
            timeCountByDay.set(day - 1, Math.min(current + 1, STEP2_MAX_TIMES_PER_DAY));
            session.setAttribute(STEP2_TIME_COUNT_BY_DAY, timeCountByDay);
        }
        model.addAttribute("dateCount", count);
        model.addAttribute("eventType", state.eventType());
        applyOptionValuesModel(model, state, request.getParameterMap(), count, timeCountByDay);
        return resolveOptionsFragment(state);
    }

    @GetMapping("/poll/step-2/options/time/remove")
    public String removeIntradayTimeOption(@RequestParam("day") int day,
                                           Model model,
                                           HttpSession session,
                                           HttpServletRequest request) {
        WizardState state = getOrInitWizard(session);
        int count = getOrInitDateCount(session);
        List<Integer> timeCountByDay = getOrInitTimeCountByDay(session, count);
        if (day >= 1 && day <= count) {
            int current = timeCountByDay.get(day - 1);
            timeCountByDay.set(day - 1, Math.max(current - 1, STEP2_MIN_TIMES_PER_DAY));
            session.setAttribute(STEP2_TIME_COUNT_BY_DAY, timeCountByDay);
        }
        model.addAttribute("dateCount", count);
        model.addAttribute("eventType", state.eventType());
        applyOptionValuesModel(model, state, request.getParameterMap(), count, timeCountByDay);
        return resolveOptionsFragment(state);
    }

    @GetMapping("/poll/step-2/options/time/copy")
    public String copyIntradayTimesToFollowingDays(Model model, HttpSession session, HttpServletRequest request) {
        WizardState state = getOrInitWizard(session);
        int count = getOrInitDateCount(session);
        List<Integer> timeCountByDay = getOrInitTimeCountByDay(session, count);
        if (!timeCountByDay.isEmpty()) {
            int firstDayCount = timeCountByDay.getFirst();
            for (int day = 2; day <= count; day++) {
                timeCountByDay.set(day - 1, firstDayCount);
            }
            session.setAttribute(STEP2_TIME_COUNT_BY_DAY, timeCountByDay);
        }
        model.addAttribute("dateCount", count);
        model.addAttribute("eventType", state.eventType());
        applyOptionValuesModel(model, state, request.getParameterMap(), count, timeCountByDay);

        List<List<String>> copiedStartTimesByDay = resolveStartTimeValuesByDay(
                request.getParameterMap(),
                state,
                timeCountByDay
        );
        if (!copiedStartTimesByDay.isEmpty()) {
            List<String> firstDayValues = new ArrayList<>(copiedStartTimesByDay.getFirst());
            for (int day = 2; day <= copiedStartTimesByDay.size(); day++) {
                copiedStartTimesByDay.set(day - 1, new ArrayList<>(firstDayValues));
            }
            model.addAttribute("startTimeValuesByDay", copiedStartTimesByDay);
        }
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
        List<Integer> timeCountByDay = getOrInitTimeCountByDay(session, count);
        applyOptionValuesModel(model, state, request.getParameterMap(), count, timeCountByDay);
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
        IntradaySelection selection = extractIntradaySelection(request.getParameterMap());
        if (eventType == io.github.bodote.woodle.domain.model.EventType.INTRADAY) {
            if (selection.missingStartTime() || selection.optionDates().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time is required for intraday polls");
            }
            state.setDates(selection.optionDates());
            state.setStartTimes(selection.optionStartTimes());
        } else {
            state.setDates(extractDates(request.getParameterMap()));
            state.setStartTimes(List.of());
        }
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

    private void applyOptionValuesModel(Model model,
                                        WizardState state,
                                        Map<String, String[]> parameterMap,
                                        int count,
                                        List<Integer> timeCountByDay) {
        List<String> dateValues = extractInputValues(parameterMap, "dateOption", count);
        if (allBlank(dateValues)) {
            dateValues = dateValuesFromState(state, count);
        }
        model.addAttribute("dateValues", dateValues);

        Integer durationMinutes = firstInteger(parameterMap, "durationMinutes");
        model.addAttribute("durationMinutes", durationMinutes != null ? durationMinutes : state.durationMinutes());

        if (state.eventType() == io.github.bodote.woodle.domain.model.EventType.INTRADAY) {
            List<List<String>> startTimeValuesByDay = resolveStartTimeValuesByDay(parameterMap, state, timeCountByDay);
            model.addAttribute("timeCountByDay", timeCountByDay);
            model.addAttribute("startTimeValuesByDay", startTimeValuesByDay);
        }
    }

    private List<List<String>> resolveStartTimeValuesByDay(Map<String, String[]> parameterMap,
                                                            WizardState state,
                                                            List<Integer> timeCountByDay) {
        List<List<String>> startTimeValuesByDay = extractStartTimeInputValuesByDay(parameterMap, timeCountByDay);
        if (allBlankByDay(startTimeValuesByDay)) {
            startTimeValuesByDay = startTimeValuesByDayFromState(state, timeCountByDay);
        }
        return startTimeValuesByDay;
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
        List<LocalDate> dayDates = state.dates();
        if (state.eventType() == io.github.bodote.woodle.domain.model.EventType.INTRADAY) {
            dayDates = intradayDayDatesFromState(state);
        }
        List<String> values = new ArrayList<>();
        if (state.eventType() == io.github.bodote.woodle.domain.model.EventType.INTRADAY) {
            LocalDate previousDate = null;
            for (LocalDate date : state.dates()) {
                if (date.equals(previousDate)) {
                    continue;
                }
                values.add(date.toString());
                previousDate = date;
                if (values.size() == count) {
                    break;
                }
            }
            while (values.size() < count) {
                values.add("");
            }
            return values;
        }
        for (int i = 0; i < count; i++) {
            if (i < dayDates.size()) {
                values.add(dayDates.get(i).toString());
            } else {
                values.add("");
            }
        }
        return values;
    }

    private List<List<String>> extractStartTimeInputValuesByDay(Map<String, String[]> parameterMap,
                                                                 List<Integer> timeCountByDay) {
        List<List<String>> values = new ArrayList<>();
        for (int day = 1; day <= timeCountByDay.size(); day++) {
            int timeCount = timeCountByDay.get(day - 1);
            List<String> dayValues = new ArrayList<>();
            for (int time = 1; time <= timeCount; time++) {
                String[] raw = parameterMap.get("startTime" + day + "_" + time);
                if (raw == null || raw.length == 0 || raw[0] == null) {
                    dayValues.add("");
                    continue;
                }
                dayValues.add(raw[0]);
            }
            values.add(dayValues);
        }
        return values;
    }

    private List<List<String>> startTimeValuesByDayFromState(WizardState state, List<Integer> timeCountByDay) {
        List<List<LocalTime>> startTimesByDay = intradayStartTimesByDayFromState(state);
        List<List<String>> values = new ArrayList<>();
        for (int day = 0; day < timeCountByDay.size(); day++) {
            int timeCount = timeCountByDay.get(day);
            List<String> dayValues = new ArrayList<>();
            List<LocalTime> stateDayValues = day < startTimesByDay.size() ? startTimesByDay.get(day) : List.of();
            for (int time = 0; time < timeCount; time++) {
                if (time < stateDayValues.size() && stateDayValues.get(time) != null) {
                    dayValues.add(stateDayValues.get(time).toString());
                } else {
                    dayValues.add("");
                }
            }
            values.add(dayValues);
        }
        return values;
    }

    private List<LocalDate> intradayDayDatesFromState(WizardState state) {
        List<LocalDate> dayDates = new ArrayList<>();
        LocalDate previous = null;
        for (LocalDate date : state.dates()) {
            if (previous == null || !previous.equals(date)) {
                dayDates.add(date);
                previous = date;
            }
        }
        return dayDates;
    }

    private List<List<LocalTime>> intradayStartTimesByDayFromState(WizardState state) {
        List<Integer> entriesPerDay = new ArrayList<>();
        LocalDate previous = null;
        for (LocalDate date : state.dates()) {
            if (previous == null || !previous.equals(date)) {
                entriesPerDay.add(1);
                previous = date;
                continue;
            }
            int lastIndex = entriesPerDay.size() - 1;
            entriesPerDay.set(lastIndex, entriesPerDay.get(lastIndex) + 1);
        }

        List<List<LocalTime>> values = new ArrayList<>();
        int startTimeIndex = 0;
        for (int dayCount : entriesPerDay) {
            List<LocalTime> dayValues = new ArrayList<>();
            for (int i = 0; i < dayCount; i++) {
                if (startTimeIndex >= state.startTimes().size()) {
                    break;
                }
                dayValues.add(state.startTimes().get(startTimeIndex));
                startTimeIndex++;
            }
            values.add(dayValues);
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

    private boolean allBlankByDay(List<List<String>> valuesByDay) {
        for (List<String> values : valuesByDay) {
            if (!allBlank(values)) {
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

    private List<Integer> getOrInitTimeCountByDay(HttpSession session, int dateCount) {
        List<Integer> timeCountByDay = new ArrayList<>();
        Object value = session.getAttribute(STEP2_TIME_COUNT_BY_DAY);
        if (value instanceof List<?> rawValues) {
            for (Object rawValue : rawValues) {
                if (rawValue instanceof Integer count) {
                    timeCountByDay.add(Math.max(count, STEP2_MIN_TIMES_PER_DAY));
                }
            }
        }
        while (timeCountByDay.size() < dateCount) {
            timeCountByDay.add(STEP2_MIN_TIMES_PER_DAY);
        }
        while (timeCountByDay.size() > dateCount) {
            timeCountByDay.remove(timeCountByDay.size() - 1);
        }
        session.setAttribute(STEP2_TIME_COUNT_BY_DAY, timeCountByDay);
        return timeCountByDay;
    }

    private IntradaySelection extractIntradaySelection(Map<String, String[]> parameterMap) {
        List<Map.Entry<String, String[]>> entries = new ArrayList<>(parameterMap.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));

        Map<Integer, LocalDate> datesByDay = new java.util.TreeMap<>();
        Map<Integer, List<LocalTime>> timesByDay = new java.util.TreeMap<>();
        for (Map.Entry<String, String[]> entry : entries) {
            if (entry.getValue().length == 0) {
                continue;
            }
            String key = entry.getKey();
            String value = entry.getValue()[0];
            if (value == null || value.isBlank()) {
                continue;
            }
            if (key.startsWith("dateOption")) {
                Integer day = parseIndex(key, "dateOption");
                if (day != null) {
                    datesByDay.put(day, LocalDate.parse(value));
                }
                continue;
            }
            if (!key.startsWith("startTime")) {
                continue;
            }
            int separator = key.indexOf('_');
            if (separator > 0) {
                Integer day = parseIndex(key.substring(0, separator), "startTime");
                if (day != null) {
                    timesByDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(LocalTime.parse(value));
                }
                continue;
            }
            Integer day = parseIndex(key, "startTime");
            if (day != null) {
                timesByDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(LocalTime.parse(value));
            }
        }

        List<LocalDate> optionDates = new ArrayList<>();
        List<LocalTime> optionStartTimes = new ArrayList<>();
        boolean missingStartTime = false;
        for (Map.Entry<Integer, LocalDate> dateEntry : datesByDay.entrySet()) {
            List<LocalTime> dayTimes = timesByDay.getOrDefault(dateEntry.getKey(), List.of());
            if (dayTimes.isEmpty()) {
                missingStartTime = true;
                continue;
            }
            for (LocalTime dayTime : dayTimes) {
                optionDates.add(dateEntry.getValue());
                optionStartTimes.add(dayTime);
            }
        }
        return new IntradaySelection(optionDates, optionStartTimes, missingStartTime);
    }

    private Integer parseIndex(String raw, String prefix) {
        if (!raw.startsWith(prefix)) {
            return null;
        }
        String suffix = raw.substring(prefix.length());
        if (suffix.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int intradayDayCountFromState(WizardState state) {
        int dayCount = 0;
        LocalDate previousDate = null;
        for (LocalDate date : state.dates()) {
            if (!date.equals(previousDate)) {
                dayCount++;
                previousDate = date;
            }
        }
        return dayCount;
    }

    private List<Integer> intradayTimeCountByDayFromState(WizardState state, int dayCount) {
        List<Integer> counts = new ArrayList<>();
        LocalDate previousDate = null;
        int currentCount = 0;
        for (LocalDate date : state.dates()) {
            if (date.equals(previousDate)) {
                currentCount++;
                continue;
            }
            if (currentCount > 0) {
                counts.add(currentCount);
            }
            previousDate = date;
            currentCount = 1;
        }
        if (currentCount > 0) {
            counts.add(currentCount);
        }
        while (counts.size() < dayCount) {
            counts.add(STEP2_MIN_TIMES_PER_DAY);
        }
        while (counts.size() > dayCount) {
            counts.remove(counts.size() - 1);
        }
        return counts;
    }

    private record IntradaySelection(List<LocalDate> optionDates, List<LocalTime> optionStartTimes, boolean missingStartTime) {
    }
}
