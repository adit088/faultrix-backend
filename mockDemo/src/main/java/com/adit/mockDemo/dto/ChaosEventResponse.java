package com.adit.mockDemo.dto;

import com.adit.mockDemo.chaos.execution.ChaosType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A recorded chaos injection event")
public class ChaosEventResponse {

    private Long id;
    private Long organizationId;
    private Long chaosRuleId;
    private String target;
    private String requestId;
    private ChaosType chaosType;
    private Boolean injected;
    private Integer httpStatus;
    private Integer delayMs;
    private Double failureRate;
    private Double blastRadius;
    private Instant occurredAt;
}