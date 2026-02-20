package com.adit.mockDemo.chaos.execution;

import com.adit.mockDemo.entity.ChaosSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleEvaluatorTest {

    private ScheduleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ScheduleEvaluator();
    }

    @Test
    void isActiveNow_emptySchedules_alwaysActive() {
        boolean result = evaluator.isActiveNow(Collections.emptyList());

        assertThat(result).isTrue();
    }

    @Test
    void isActiveNow_nullSchedules_alwaysActive() {
        boolean result = evaluator.isActiveNow(null);

        assertThat(result).isTrue();
    }

    @Test
    void isActiveNow_withinTimeWindow_active() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        LocalTime nowTime = now.toLocalTime();
        LocalTime start = nowTime.minusHours(1);
        LocalTime end = nowTime.plusHours(1);

        int currentDay = now.getDayOfWeek().getValue();

        ChaosSchedule schedule = ChaosSchedule.builder()
                .name("Test Schedule")
                .enabled(true)
                .daysOfWeek(String.valueOf(currentDay))
                .startTime(start.format(DateTimeFormatter.ofPattern("HH:mm")))
                .endTime(end.format(DateTimeFormatter.ofPattern("HH:mm")))
                .build();

        boolean result = evaluator.isActiveNow(List.of(schedule));

        assertThat(result).isTrue();
    }

    @Test
    void isActiveNow_outsideTimeWindow_inactive() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        LocalTime nowTime = now.toLocalTime();
        LocalTime start = nowTime.plusHours(2);
        LocalTime end = nowTime.plusHours(4);

        int currentDay = now.getDayOfWeek().getValue();

        ChaosSchedule schedule = ChaosSchedule.builder()
                .name("Test Schedule")
                .enabled(true)
                .daysOfWeek(String.valueOf(currentDay))
                .startTime(start.format(DateTimeFormatter.ofPattern("HH:mm")))
                .endTime(end.format(DateTimeFormatter.ofPattern("HH:mm")))
                .build();

        boolean result = evaluator.isActiveNow(List.of(schedule));

        assertThat(result).isFalse();
    }

    @Test
    void isActiveNow_wrongDayOfWeek_inactive() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int currentDay = now.getDayOfWeek().getValue();
        int differentDay = (currentDay % 7) + 1;

        ChaosSchedule schedule = ChaosSchedule.builder()
                .name("Test Schedule")
                .enabled(true)
                .daysOfWeek(String.valueOf(differentDay))
                .startTime("00:00")
                .endTime("23:59")
                .build();

        boolean result = evaluator.isActiveNow(List.of(schedule));

        assertThat(result).isFalse();
    }

    @Test
    void isActiveNow_allDaysAllHours_active() {
        ChaosSchedule schedule = ChaosSchedule.builder()
                .name("24/7 Schedule")
                .enabled(true)
                .daysOfWeek("1,2,3,4,5,6,7")
                .startTime("00:00")
                .endTime("23:59")
                .build();

        boolean result = evaluator.isActiveNow(List.of(schedule));

        assertThat(result).isTrue();
    }

    @Test
    void isActiveNow_anyScheduleActive_returnsTrue() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int currentDay = now.getDayOfWeek().getValue();
        int differentDay = (currentDay % 7) + 1;

        ChaosSchedule inactive = ChaosSchedule.builder()
                .name("Inactive")
                .enabled(true)
                .daysOfWeek(String.valueOf(differentDay))
                .startTime("00:00")
                .endTime("23:59")
                .build();

        ChaosSchedule active = ChaosSchedule.builder()
                .name("Active")
                .enabled(true)
                .daysOfWeek("1,2,3,4,5,6,7")
                .startTime("00:00")
                .endTime("23:59")
                .build();

        boolean result = evaluator.isActiveNow(List.of(inactive, active));

        assertThat(result).isTrue();
    }

    @Test
    void isActiveNow_beforeActiveFrom_inactive() {
        Instant future = Instant.now().plusSeconds(3600);
        int currentDay = ZonedDateTime.now(ZoneOffset.UTC).getDayOfWeek().getValue();

        ChaosSchedule schedule = ChaosSchedule.builder()
                .name("Future Schedule")
                .enabled(true)
                .daysOfWeek(String.valueOf(currentDay))
                .startTime("00:00")
                .endTime("23:59")
                .activeFrom(future)
                .build();

        boolean result = evaluator.isActiveNow(List.of(schedule));

        assertThat(result).isFalse();
    }

    @Test
    void isActiveNow_afterActiveUntil_inactive() {
        Instant past = Instant.now().minusSeconds(3600);
        int currentDay = ZonedDateTime.now(ZoneOffset.UTC).getDayOfWeek().getValue();

        ChaosSchedule schedule = ChaosSchedule.builder()
                .name("Expired Schedule")
                .enabled(true)
                .daysOfWeek(String.valueOf(currentDay))
                .startTime("00:00")
                .endTime("23:59")
                .activeUntil(past)
                .build();

        boolean result = evaluator.isActiveNow(List.of(schedule));

        assertThat(result).isFalse();
    }
}