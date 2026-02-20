package com.adit.mockDemo.client;

import com.adit.mockDemo.dto.UserDto;
import com.adit.mockDemo.exception.FaultrixApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FaultrixClient {

    private final RestTemplate restTemplate;

    private static final String USERS_URL = "https://jsonplaceholder.typicode.com/users";

    public List<UserDto> fetchUsers() {
        log.info("Calling external API: GET {}", USERS_URL);

        try {
            ExternalUser[] response = restTemplate.getForObject(USERS_URL, ExternalUser[].class);

            if (response == null) {
                log.warn("External API returned null response");
                throw new FaultrixApiException("External API returned empty response");
            }

            List<UserDto> users = Arrays.stream(response)
                    .map(this::mapToDto)
                    .collect(Collectors.toList());

            log.info("Fetched {} users from external API", users.size());
            return users;

        } catch (FaultrixApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch users from external API: {}", USERS_URL, e);
            throw new FaultrixApiException("Failed to fetch users from external API: " + e.getMessage(), e);
        }
    }

    private UserDto mapToDto(ExternalUser ext) {
        return UserDto.builder()
                .id(ext.getId())
                .name(ext.getName())
                .email(ext.getEmail())
                .salary(0.0) // JSONPlaceholder has no salary field — sentinel value
                .build();
    }

    /**
     * Internal DTO matching JSONPlaceholder schema exactly.
     * Isolated here so external API changes don't leak into domain DTOs.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ExternalUser {
        private Long id;
        private String name;
        private String username;
        private String email;
        private String phone;
        private String website;
    }
}