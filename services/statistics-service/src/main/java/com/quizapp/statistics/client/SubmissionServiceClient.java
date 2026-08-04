package com.quizapp.statistics.client;

import com.quizapp.statistics.dto.SubmissionDto;
import com.quizapp.statistics.dto.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class SubmissionServiceClient {

    private final WebClient webClient;

    public SubmissionServiceClient(WebClient.Builder webClientBuilder, 
                                   @Value("${submission.service.url:http://submission-service:8080}") String submissionServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(submissionServiceUrl).build();
    }

    public List<SubmissionDto> getSubmittedByExam(String examId) {
        try {
            ApiResponse<List<SubmissionDto>> response = webClient.get()
                    .uri("/submissions?examId={examId}&status=SUBMITTED", examId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<SubmissionDto>>>() {})
                    .block();

            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            // Log error
            System.err.println("Error calling Submission Service: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Fetch submitted list, then hydrate each record with answers from detail endpoint.
     * This is used by question analytics because list endpoint does not include answers.
     */
    public List<SubmissionDto> getSubmittedByExamWithAnswers(String examId) {
        List<SubmissionDto> summaries = getSubmittedByExam(examId);
        if (summaries.isEmpty()) {
            return Collections.emptyList();
        }

        return summaries.stream()
                .map(summary -> getSubmissionDetail(summary.getId()).orElse(summary))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private java.util.Optional<SubmissionDto> getSubmissionDetail(String submissionId) {
        if (submissionId == null || submissionId.isBlank()) {
            return java.util.Optional.empty();
        }

        try {
            ApiResponse<SubmissionDto> response = webClient.get()
                    .uri("/submissions/{submissionId}", submissionId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiResponse<SubmissionDto>>() {})
                    .block();

            if (response != null && response.isSuccess() && response.getData() != null) {
                return java.util.Optional.of(response.getData());
            }
        } catch (Exception e) {
            System.err.println("Error calling Submission detail for " + submissionId + ": " + e.getMessage());
        }

        return java.util.Optional.empty();
    }
}
