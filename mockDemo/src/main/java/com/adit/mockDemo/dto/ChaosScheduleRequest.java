package com.adit.mockDemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Define a time window for a chaos rule")
public class ChaosScheduleRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Builder.Default
    private Boolean enabled = true;

    @Pattern(regexp = "^[1-7](,[1-7])*$",
            message = "daysOfWeek must be CSV of 1-7 (1=Mon, 7=Sun). E.g. '1,2,3,4,5'")
    @Builder.Default
    @Schema(example = "1,2,3,4,5", description = "Days active: 1=Mon … 7=Sun")
    private String daysOfWeek = "1,2,3,4,5,6,7";

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
            message = "startTime must be HH:mm format (UTC)")
    @Builder.Default
    @Schema(example = "09:00")
    private String startTime = "00:00";

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
            message = "endTime must be HH:mm format (UTC)")
    @Builder.Default
    @Schema(example = "17:00")
    private String endTime = "23:59";

    @Schema(description = "Optional: absolute start date (ISO-8601 UTC)")
    private Instant activeFrom;

    @Schema(description = "Optional: absolute end date (ISO-8601 UTC)")
    private Instant activeUntil;
}