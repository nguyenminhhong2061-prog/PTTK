package com.quizapp.exam.controller;

import com.quizapp.exam.dto.response.HealthResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Convenience health endpoint matching the assignment's example response.
 * Actuator health is also mapped to /health via application.properties.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return HealthResponse.builder()
                .status("ok")
                .service("exam-service")
                .timestamp(Instant.now())
                .build();
    }
}
