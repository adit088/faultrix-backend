package com.adit.mockDemo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChaosScheduleResponse {
    private Long    id;
    private Long    chaosRuleId;
    private Long    organizationId;
    private String  name;
    private Boolean enabled;
    private String  daysOfWeek;
    private String  startTime;
    private String  endTime;
    private Instant activeFrom;
    private Instant activeUntil;
    private Instant createdAt;
    private Instant updatedAt;
}