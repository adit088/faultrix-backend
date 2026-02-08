package com.adit.mockDemo.client;

import com.adit.mockDemo.chaos.ChaosDecision;
import com.adit.mockDemo.chaos.ChaosEngine;
import com.adit.mockDemo.dto.UserDto;
import com.adit.mockDemo.exception.NtropiApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Component
public class NtropiClient {

    private final ChaosEngine chaosEngine;
    private final RestTemplate restTemplate;


    public NtropiClient(ChaosEngine chaosEngine, RestTemplate restTemplate) {
        this.chaosEngine = chaosEngine;
        this.restTemplate = restTemplate;
    }

    public List<UserDto> fetchUsers(){

        ChaosDecision decision = chaosEngine.evaluate("users-api");

        // How it's gonna act on delays
        if(decision.shouldDelay()){
            try{
                Thread.sleep(decision.getDelayMs());
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
        }

        
        // how it's gonna act on failure
        if(decision.shouldFail()){
            throw new NtropiApiException("chaos-induced failure");
        }

        String url = "http://localhost:8080/users";    // mock Ntropi API

        try {
            UserDto[] response = restTemplate.getForObject(url, UserDto[].class);

            return Arrays.asList(response);
        }catch (Exception e){
            throw new NtropiApiException(
                    "Failed to fetch users from Ntropi API",
                    e
            );
        }
    }

}
