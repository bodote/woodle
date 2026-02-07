package io.github.bodote.woodle.adapter.in.web;

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

    public LocalDate expiresAtOverride() {
        return expiresAtOverride;
    }

    public void setExpiresAtOverride(LocalDate expiresAtOverride) {
        this.expiresAtOverride = expiresAtOverride;
    }
}
