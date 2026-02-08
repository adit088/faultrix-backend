package com.adit.mockDemo.controller;

import com.adit.mockDemo.metrics.ChaosMetrics;
import com.adit.mockDemo.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;

@RestController
public class TrafficController {

    private final UserService userService;
    private final ChaosMetrics metrics;

    public TrafficController(UserService userService, ChaosMetrics metrics){
        this.userService = userService;
        this.metrics = metrics;
    }

    @GetMapping("/chaos/traffic")
    public void generateTraffic(@RequestParam(defaultValue = "1") int users){

        // 🔥 Start of a NEW experiment
        metrics.resetExperiment();
        metrics.setCurrentTraffic(users);

        for (int i = 0; i < users; i++){
            try{
                userService.getAllUsers();
            }catch (Exception ignored){}
        }
    }
}
