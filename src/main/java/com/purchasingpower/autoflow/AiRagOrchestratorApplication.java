package com.purchasingpower.autoflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiRagOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiRagOrchestratorApplication.class, args);
    }
}