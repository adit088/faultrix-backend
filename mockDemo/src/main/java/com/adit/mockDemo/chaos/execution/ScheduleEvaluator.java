package com.adit.mockDemo.chaos.execution;

import com.adit.mockDemo.entity.ChaosSchedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates whether the current time falls within any active schedule window.
 * All comparisons are in UTC.
 *
 * Rules:
 * - If a rule has NO schedules → always active (backward compatible)
 * - If a rule HAS schedules → must match at least one active window
 */
@Component
@Slf4j
public class ScheduleEvaluator {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * @param schedules Active schedules for the rule (pre-filtered enabled=true)
     * @return true if chaos should be allowed to fire right now
     */
    public boolean isActiveNow(List<ChaosSchedule> schedules) {
        // No schedules = always active (backward compatible)
        if (schedules == null || schedules.isEmpty()) {
            return true;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        return schedules.stream().anyMatch(schedule -> isWindowActive(schedule, now));
    }

    private boolean isWindowActive(ChaosSchedule schedule, ZonedDateTime now) {
        // Check absolute date window first
        if (schedule.getActiveFrom() != null && now.toInstant().isBefore(schedule.getActiveFrom())) {
            log.trace("Schedule '{}' not yet active (activeFrom: {})", schedule.getName(), schedule.getActiveFrom());
            return false;
        }

        if (schedule.getActiveUntil() != null && now.toInstant().isAfter(schedule.getActiveUntil())) {
            log.trace("Schedule '{}' expired (activeUntil: {})", schedule.getName(), schedule.getActiveUntil());
            return false;
        }

        // Check day of week (1=Mon, 7=Sun — ISO standard)
        int todayIso = now.getDayOfWeek().getValue();
        Set<Integer> allowedDays = parseDays(schedule.getDaysOfWeek());

        if (!allowedDays.contains(todayIso)) {
            log.trace("Schedule '{}' not active today (day={}, allowed={})",
                    schedule.getName(), todayIso, allowedDays);
            return false;
        }

        // Check time window
        LocalTime nowTime   = now.toLocalTime();
        LocalTime startTime = LocalTime.parse(schedule.getStartTime(), TIME_FMT);
        LocalTime endTime   = LocalTime.parse(schedule.getEndTime(),   TIME_FMT);

        boolean inWindow = !nowTime.isBefore(startTime) && !nowTime.isAfter(endTime);

        if (!inWindow) {
            log.trace("Schedule '{}' outside time window ({} not in {}-{})",
                    schedule.getName(), nowTime, startTime, endTime);
        }

        return inWindow;
    }

    private Set<Integer> parseDays(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
    }
}