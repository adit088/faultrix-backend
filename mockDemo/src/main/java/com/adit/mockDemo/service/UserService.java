package com.adit.mockDemo.service;

import com.adit.mockDemo.chaos.ChaosDecision;
import com.adit.mockDemo.chaos.ChaosEngine;
import com.adit.mockDemo.client.NtropiClient;
import com.adit.mockDemo.dto.UserDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final NtropiClient ntropiClient;
    private final ChaosEngine chaosEngine;



    public UserService(NtropiClient ntropiClient, ChaosEngine chaosEngine) {
        this.ntropiClient = ntropiClient;
        this.chaosEngine = chaosEngine;
    }

    public List<UserDto> getAllUsers(){

        ChaosDecision decision = chaosEngine.evaluate("users-api");

        if(decision.shouldFail()){
            throw new RuntimeException("chaos injected failure");
        }

        if (decision.shouldDelay()) {
            try {
                Thread.sleep(decision.getDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return ntropiClient.fetchUsers();
    }
}
