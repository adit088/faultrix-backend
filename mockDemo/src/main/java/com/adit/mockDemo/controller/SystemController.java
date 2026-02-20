package com.adit.mockDemo.controller;

import com.adit.mockDemo.dto.SystemInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "System", description = "System information and health")
public class SystemController {

    private final HealthEndpoint healthEndpoint;

    @GetMapping("/info")
    @Operation(summary = "Get system information")
    public ResponseEntity<SystemInfoResponse> getSystemInfo() {

        Map<String, Object> health = new HashMap<>();
        health.put("status", healthEndpoint.health().getStatus().getCode());

        SystemInfoResponse response = SystemInfoResponse.builder()
                .version("1.0.0")
                .environment("development")
                .serverTime(Instant.now())
                .health(health)
                .build();

        return ResponseEntity.ok(response);
    }
}