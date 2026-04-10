package com.quizapp.submission.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Cấu hình WebClient để gọi Exam Service.
 * URL được lấy từ biến môi trường EXAM_SERVICE_URL (inject qua Docker Compose).
 */
@Configuration
public class WebClientConfig {

    @Value("${exam.service.url:http://exam-service:8080}")
    private String examServiceUrl;

    @Bean
    public WebClient examWebClient() {
        return WebClient.builder()
                .baseUrl(examServiceUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
