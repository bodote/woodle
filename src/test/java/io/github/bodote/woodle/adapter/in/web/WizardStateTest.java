package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.model.WizardState;
import io.github.bodote.woodle.domain.model.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        Class<?> dayOptionClass = Class.forName("io.github.bodote.woodle.application.model.WizardState$DayOption");
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

    @Test
    @DisplayName("returns one day option per date for all-day polls")
    void returnsOneDayOptionPerDateForAllDayPolls() {
        WizardState state = new WizardState();
        state.setEventType(EventType.ALL_DAY);
        state.setDates(List.of(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 2)
        ));
        state.setStartTimes(List.of(LocalTime.of(9, 0)));

        List<WizardState.DayOption> options = state.dayOptions();

        assertEquals(2, options.size());
        assertEquals(LocalDate.of(2026, 3, 1), options.get(0).day());
        assertEquals(List.of(), options.get(0).times());
        assertEquals(LocalDate.of(2026, 3, 2), options.get(1).day());
        assertEquals(List.of(), options.get(1).times());
    }

    @Test
    @DisplayName("returns empty day options when intraday poll has no dates")
    void returnsEmptyDayOptionsWhenIntradayPollHasNoDates() {
        WizardState state = new WizardState();
        state.setEventType(EventType.INTRADAY);
        state.setDates(List.of());
        state.setStartTimes(List.of(LocalTime.of(9, 0)));

        assertEquals(List.of(), state.dayOptions());
    }

    @Test
    @DisplayName("intraday day options tolerate fewer times than dates")
    void intradayDayOptionsTolerateFewerTimesThanDates() {
        WizardState state = new WizardState();
        state.setEventType(EventType.INTRADAY);
        state.setDates(List.of(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 2)
        ));
        state.setStartTimes(List.of(LocalTime.of(9, 0)));

        List<WizardState.DayOption> options = state.dayOptions();

        assertEquals(2, options.size());
        assertEquals(List.of(LocalTime.of(9, 0)), options.get(0).times());
        assertEquals(List.of(), options.get(1).times());
    }

    @Test
    @DisplayName("setDayOptions skips null day entries")
    void setDayOptionsSkipsNullDayEntries() {
        WizardState state = new WizardState();
        state.setEventType(EventType.INTRADAY);
        state.setDayOptions(List.of(
                new WizardState.DayOption(null, List.of(LocalTime.of(9, 0))),
                new WizardState.DayOption(LocalDate.of(2026, 3, 1), List.of(LocalTime.of(10, 0)))
        ));

        assertEquals(List.of(LocalDate.of(2026, 3, 1)), state.dates());
        assertEquals(List.of(LocalTime.of(10, 0)), state.startTimes());
    }

    @Test
    @DisplayName("setDayOptions adds date once when intraday day has no times")
    void setDayOptionsAddsDateOnceWhenIntradayDayHasNoTimes() {
        WizardState state = new WizardState();
        state.setEventType(EventType.INTRADAY);
        state.setDayOptions(List.of(
                new WizardState.DayOption(LocalDate.of(2026, 3, 1), List.of())
        ));

        assertEquals(List.of(LocalDate.of(2026, 3, 1)), state.dates());
        assertEquals(List.of(), state.startTimes());
    }

    @Test
    @DisplayName("setDayOptions ignores times for all-day event type")
    void setDayOptionsIgnoresTimesForAllDayEventType() {
        WizardState state = new WizardState();
        state.setEventType(EventType.ALL_DAY);
        state.setDayOptions(List.of(
                new WizardState.DayOption(LocalDate.of(2026, 3, 1), List.of(LocalTime.of(9, 0), LocalTime.of(10, 0)))
        ));

        assertEquals(List.of(LocalDate.of(2026, 3, 1)), state.dates());
        assertEquals(List.of(), state.startTimes());
    }

    @Test
    @DisplayName("day option constructor rejects null times")
    void dayOptionConstructorRejectsNullTimes() {
        assertThrows(NullPointerException.class, () -> new WizardState.DayOption(LocalDate.of(2026, 3, 1), null));
    }
}
