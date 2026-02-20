package com.adit.mockDemo.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {

    @Parameter(description = "Cursor for pagination (ID of last item from previous page)")
    private Long cursor;

    @Parameter(description = "Number of items per page")
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit cannot exceed 100")
    @Builder.Default
    private Integer limit = 20;

    @Parameter(description = "Sort field (id, target, createdAt, updatedAt)")
    @Builder.Default
    private String sortBy = "id";

    @Parameter(description = "Sort direction (ASC or DESC)")
    @Builder.Default
    private String sortDirection = "ASC";

    @Parameter(description = "Filter by enabled status")
    private Boolean enabled;

    @Parameter(description = "Filter by tags (comma-separated)")
    private String tags;

    @Parameter(description = "Search in target or description")
    private String search;
}