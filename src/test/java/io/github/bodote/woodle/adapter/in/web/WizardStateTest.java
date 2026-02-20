package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.domain.model.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("WizardState")
class WizardStateTest {

    @Test
    @DisplayName("provides grouped day options for intraday legacy date/time lists")
    void providesGroupedDayOptionsForIntradayLegacyDateTimeLists() throws Exception {
        WizardState state = new WizardState();
        state.setEventType(EventType.INTRADAY);
        state.setDates(List.of(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 2)
        ));
        state.setStartTimes(List.of(
                LocalTime.of(10, 50),
                LocalTime.of(13, 50),
                LocalTime.of(10, 50)
        ));

        Method dayOptionsMethod = WizardState.class.getMethod("dayOptions");
        List<?> dayOptions = (List<?>) dayOptionsMethod.invoke(state);
        assertEquals(2, dayOptions.size());

        Object firstDay = dayOptions.get(0);
        Object secondDay = dayOptions.get(1);
        Method dayAccessor = firstDay.getClass().getMethod("day");
        Method timesAccessor = firstDay.getClass().getMethod("times");

        assertEquals(LocalDate.of(2026, 2, 1), dayAccessor.invoke(firstDay));
        assertEquals(2, ((List<?>) timesAccessor.invoke(firstDay)).size());
        assertEquals(LocalDate.of(2026, 2, 2), dayAccessor.invoke(secondDay));
        assertEquals(1, ((List<?>) timesAccessor.invoke(secondDay)).size());
    }

    @Test
    @DisplayName("accepts day options and exposes flattened legacy views")
    void acceptsDayOptionsAndExposesFlattenedLegacyViews() throws Exception {
        WizardState state = new WizardState();
        state.setEventType(EventType.INTRADAY);

        Class<?> dayOptionClass = Class.forName("io.github.bodote.woodle.adapter.in.web.WizardState$DayOption");
        Object firstDay = dayOptionClass
                .getDeclaredConstructor(LocalDate.class, List.class)
                .newInstance(LocalDate.of(2026, 2, 1), List.of(LocalTime.of(10, 50), LocalTime.of(13, 50)));
        Object secondDay = dayOptionClass
                .getDeclaredConstructor(LocalDate.class, List.class)
                .newInstance(LocalDate.of(2026, 2, 2), List.of(LocalTime.of(10, 50)));

        List<Object> dayOptions = new ArrayList<>();
        dayOptions.add(firstDay);
        dayOptions.add(secondDay);

        Method setDayOptionsMethod = WizardState.class.getMethod("setDayOptions", List.class);
        setDayOptionsMethod.invoke(state, dayOptions);

        assertEquals(List.of(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 2)
        ), state.dates());
        assertEquals(List.of(
                LocalTime.of(10, 50),
                LocalTime.of(13, 50),
                LocalTime.of(10, 50)
        ), state.startTimes());
    }
}
