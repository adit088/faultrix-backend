package com.adit.mockDemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MockDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MockDemoApplication.class, args);
	}
}