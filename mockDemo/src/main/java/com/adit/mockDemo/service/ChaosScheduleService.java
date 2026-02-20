package com.adit.mockDemo.service;

import com.adit.mockDemo.dto.ChaosScheduleRequest;
import com.adit.mockDemo.dto.ChaosScheduleResponse;
import com.adit.mockDemo.entity.ChaosRuleEntity;
import com.adit.mockDemo.entity.ChaosSchedule;
import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.exception.ResourceNotFoundException;
import com.adit.mockDemo.exception.ValidationException;
import com.adit.mockDemo.repository.ChaosRuleRepository;
import com.adit.mockDemo.repository.ChaosScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChaosScheduleService {

    private final ChaosScheduleRepository scheduleRepository;
    private final ChaosRuleRepository     ruleRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public ChaosScheduleResponse createSchedule(Organization org,
                                                Long ruleId,
                                                ChaosScheduleRequest request) {
        log.info("POST schedule for rule {} - Org: {}", ruleId, org.getSlug());

        ChaosRuleEntity rule = getRuleForOrg(org, ruleId);
        validate(request);

        ChaosSchedule schedule = ChaosSchedule.builder()
                .chaosRule(rule)
                .organization(org)
                .name(request.getName())
                .enabled(request.getEnabled())
                .daysOfWeek(request.getDaysOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .activeFrom(request.getActiveFrom())
                .activeUntil(request.getActiveUntil())
                .build();

        return mapToResponse(scheduleRepository.save(schedule));
    }

    @Transactional(readOnly = true)
    public List<ChaosScheduleResponse> getSchedulesForRule(Organization org, Long ruleId) {
        ChaosRuleEntity rule = getRuleForOrg(org, ruleId);
        return scheduleRepository.findByChaosRule(rule)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public ChaosScheduleResponse updateSchedule(Organization org,
                                                Long ruleId,
                                                Long scheduleId,
                                                ChaosScheduleRequest request) {
        log.info("PUT schedule {} for rule {} - Org: {}", scheduleId, ruleId, org.getSlug());

        getRuleForOrg(org, ruleId); // validates ownership
        validate(request);

        ChaosSchedule schedule = scheduleRepository.findById(scheduleId)
                .filter(s -> s.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("ChaosSchedule", scheduleId.toString()));

        schedule.setName(request.getName());
        schedule.setEnabled(request.getEnabled());
        schedule.setDaysOfWeek(request.getDaysOfWeek());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setActiveFrom(request.getActiveFrom());
        schedule.setActiveUntil(request.getActiveUntil());

        return mapToResponse(scheduleRepository.save(schedule));
    }

    public void deleteSchedule(Organization org, Long ruleId, Long scheduleId) {
        log.info("DELETE schedule {} for rule {} - Org: {}", scheduleId, ruleId, org.getSlug());
        getRuleForOrg(org, ruleId);

        ChaosSchedule schedule = scheduleRepository.findById(scheduleId)
                .filter(s -> s.getOrganization().getId().equals(org.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("ChaosSchedule", scheduleId.toString()));

        scheduleRepository.delete(schedule);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ChaosRuleEntity getRuleForOrg(Organization org, Long ruleId) {
        return ruleRepository.findByOrganizationAndId(org, ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("ChaosRule", ruleId.toString()));
    }

    private void validate(ChaosScheduleRequest req) {
        LocalTime start = LocalTime.parse(req.getStartTime(), TIME_FMT);
        LocalTime end   = LocalTime.parse(req.getEndTime(),   TIME_FMT);
        if (!start.isBefore(end)) {
            throw new ValidationException(
                    "startTime (" + req.getStartTime() + ") must be before endTime (" + req.getEndTime() + ")");
        }

        if (req.getActiveFrom() != null && req.getActiveUntil() != null
                && !req.getActiveFrom().isBefore(req.getActiveUntil())) {
            throw new ValidationException("activeFrom must be before activeUntil");
        }
    }

    private ChaosScheduleResponse mapToResponse(ChaosSchedule s) {
        return ChaosScheduleResponse.builder()
                .id(s.getId())
                .chaosRuleId(s.getChaosRule().getId())
                .organizationId(s.getOrganization().getId())
                .name(s.getName())
                .enabled(s.getEnabled())
                .daysOfWeek(s.getDaysOfWeek())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .activeFrom(s.getActiveFrom())
                .activeUntil(s.getActiveUntil())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}