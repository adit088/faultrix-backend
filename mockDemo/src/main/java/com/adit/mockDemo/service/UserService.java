package com.adit.mockDemo.service;

import com.adit.mockDemo.client.FaultrixClient;
import com.adit.mockDemo.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final FaultrixClient faultrixClient;

    public List<UserDto> getAllUsers() {
        log.info("GET /api/users - Fetching all users");
        return faultrixClient.fetchUsers();
    }
}