package io.github.bodote.woodle.application.model;

import io.github.bodote.woodle.domain.model.EventType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class WizardState {

    public static final String SESSION_KEY = "wizardState";

    private String authorName;
    private String authorEmail;
    private String title;
    private String description;
    private EventType eventType = EventType.ALL_DAY;
    private Integer durationMinutes;
    private List<LocalDate> dates = new ArrayList<>();
    private List<LocalTime> startTimes = new ArrayList<>();
    private LocalDate expiresAtOverride;

    public String authorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String authorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EventType eventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Integer durationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public List<LocalDate> dates() {
        return dates;
    }

    public void setDates(List<LocalDate> dates) {
        this.dates = new ArrayList<>(dates);
    }

    public List<LocalTime> startTimes() {
        return startTimes;
    }

    public void setStartTimes(List<LocalTime> startTimes) {
        this.startTimes = new ArrayList<>(startTimes);
    }

    public List<DayOption> dayOptions() {
        List<DayOption> values = new ArrayList<>();
        if (eventType != EventType.INTRADAY) {
            for (LocalDate date : dates) {
                values.add(new DayOption(date, List.of()));
            }
            return values;
        }
        LocalDate previousDate = null;
        List<LocalTime> currentTimes = new ArrayList<>();
        int startTimeIndex = 0;
        for (LocalDate date : dates) {
            if (previousDate == null || !previousDate.equals(date)) {
                if (previousDate != null) {
                    values.add(new DayOption(previousDate, currentTimes));
                }
                previousDate = date;
                currentTimes = new ArrayList<>();
            }
            if (startTimeIndex < startTimes.size()) {
                currentTimes.add(startTimes.get(startTimeIndex));
                startTimeIndex++;
            }
        }
        if (previousDate != null) {
            values.add(new DayOption(previousDate, currentTimes));
        }
        return values;
    }

    public void setDayOptions(List<DayOption> dayOptions) {
        List<LocalDate> flattenedDates = new ArrayList<>();
        List<LocalTime> flattenedStartTimes = new ArrayList<>();
        for (DayOption dayOption : dayOptions) {
            if (dayOption.day() == null) {
                continue;
            }
            if (eventType == EventType.INTRADAY && !dayOption.times().isEmpty()) {
                for (LocalTime time : dayOption.times()) {
                    flattenedDates.add(dayOption.day());
                    flattenedStartTimes.add(time);
                }
                continue;
            }
            flattenedDates.add(dayOption.day());
        }
        this.dates = flattenedDates;
        this.startTimes = flattenedStartTimes;
    }

    public LocalDate expiresAtOverride() {
        return expiresAtOverride;
    }

    public void setExpiresAtOverride(LocalDate expiresAtOverride) {
        this.expiresAtOverride = expiresAtOverride;
    }

    public static WizardState copyOf(WizardState source) {
        WizardState copy = new WizardState();
        copy.setAuthorName(source.authorName());
        copy.setAuthorEmail(source.authorEmail());
        copy.setTitle(source.title());
        copy.setDescription(source.description());
        copy.setEventType(source.eventType());
        copy.setDurationMinutes(source.durationMinutes());
        copy.setDates(source.dates());
        copy.setStartTimes(source.startTimes());
        copy.setExpiresAtOverride(source.expiresAtOverride());
        return copy;
    }

    public record DayOption(LocalDate day, List<LocalTime> times) {
        public DayOption {
            times = List.copyOf(times);
        }
    }
}
