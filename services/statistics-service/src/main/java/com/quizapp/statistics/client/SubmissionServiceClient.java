package com.quizapp.statistics.client;

import com.quizapp.statistics.dto.SubmissionDto;
import com.quizapp.statistics.dto.response.ApiResponse;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Client gọi Submission Service để lấy danh sách bài nộp.
 *
 * TV2 — Resilience Layer:
 * - @Retry: tự động retry tối đa 3 lần với exponential backoff (2s → 4s → 8s)
 *   khi Submission Service tạm thời chậm
 * - Timeout 10s: đủ rộng cho query phức tạp nhưng không để treo vô hạn
 * - Fallback thủ công trong catch block: trả về empty list thay vì crash
 */
@Component
@Slf4j
public class SubmissionServiceClient {

    /** Timeout cho mỗi lần gọi đến Submission Service */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public SubmissionServiceClient(WebClient.Builder webClientBuilder,
                                   @Value("${submission.service.url:http://submission-service:8080}") String submissionServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(submissionServiceUrl).build();
    }

    /**
     * Lấy danh sách bài đã nộp theo examId.
     * TV2: @Retry bảo vệ call này — nếu Submission Service tạm thời down,
     * tự retry tối đa 3 lần trước khi trả empty list.
     */
    @Retry(name = "submissionService", fallbackMethod = "getSubmittedByExamFallback")
    public List<SubmissionDto> getSubmittedByExam(String examId) {
        log.debug("Gọi Submission Service lấy danh sách bài nộp examId={}", examId);
        ApiResponse<List<SubmissionDto>> response = webClient.get()
                .uri("/submissions?examId={examId}&status=SUBMITTED", examId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<List<SubmissionDto>>>() {})
                .timeout(HTTP_TIMEOUT)
                .block();

        if (response != null && response.isSuccess() && response.getData() != null) {
            return response.getData();
        }
        return Collections.emptyList();
    }

    /**
     * Lấy danh sách bài nộp kèm chi tiết đáp án từng câu.
     * Dùng cho phân tích tỷ lệ đúng/sai từng câu hỏi.
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

    /**
     * Package-private (bỏ 'private') để Spring AOP proxy có thể intercept @Retry.
     * QUAN TRỌNG: @Retry trên private method KHÔNG hoạt động với Spring AOP —
     * proxy không thể override private method → annotation bị bỏ qua hoàn toàn.
     */
    @Retry(name = "submissionService", fallbackMethod = "getSubmissionDetailFallback")
    java.util.Optional<SubmissionDto> getSubmissionDetail(String submissionId) {
        if (submissionId == null || submissionId.isBlank()) {
            return java.util.Optional.empty();
        }

        log.debug("Gọi Submission Service lấy detail submissionId={}", submissionId);
        ApiResponse<SubmissionDto> response = webClient.get()
                .uri("/submissions/{submissionId}", submissionId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<SubmissionDto>>() {})
                .timeout(HTTP_TIMEOUT)
                .block();

        if (response != null && response.isSuccess() && response.getData() != null) {
            return java.util.Optional.of(response.getData());
        }
        return java.util.Optional.empty();
    }

    // ── Fallback Methods ─────────────────────────────────────────────────────
    // Được gọi sau khi đã hết số lần retry (3 lần) mà vẫn thất bại

    @SuppressWarnings("unused")
    List<SubmissionDto> getSubmittedByExamFallback(String examId, Exception ex) {
        log.warn("[Retry Exhausted] getSubmittedByExam fallback kích hoạt cho examId={}: {}. " +
                "Trả về danh sách rỗng.", examId, ex.getMessage());
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    java.util.Optional<SubmissionDto> getSubmissionDetailFallback(String submissionId, Exception ex) {
        log.warn("[Retry Exhausted] getSubmissionDetail fallback kích hoạt cho submissionId={}: {}. " +
                "Bỏ qua bài nộp này.", submissionId, ex.getMessage());
        return java.util.Optional.empty();
    }
}
