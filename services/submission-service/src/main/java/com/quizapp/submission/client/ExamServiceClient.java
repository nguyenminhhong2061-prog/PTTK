package com.quizapp.submission.client;

import com.quizapp.submission.exception.ExamServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * Client gọi Exam Service để lấy thông tin bài thi và câu hỏi.
 * Sử dụng Spring WebClient (reactive) với .block() để chờ đồng bộ.
 *
 * TV2 — Circuit Breaker Pattern:
 * - Thứ tự decorator (annotation đọc từ ngoài vào trong):
 *     @CircuitBreaker (đặt trên) → Retry bao ngoài, CircuitBreaker bao trong
 *   Nói cách khác: Retry là lớp ngoài cùng — nó thử lại toàn bộ CircuitBreaker.
 *   Khi Circuit OPEN → ném CallNotPermittedException → Retry bỏ qua (ignore-exceptions)
 *   → không retry vô ích → rơi vào fallback ngay lập tức (<1ms).
 * - Fallback method: ném ExamServiceUnavailableException để Saga (TV1) bắt
 *   và kích hoạt compensation (GRADING → IN_PROGRESS)
 * - Timeout: cấu hình trực tiếp trên WebClient (.timeout()) thay vì @TimeLimiter
 *   vì ExamServiceClient dùng blocking .block() không tương thích với @TimeLimiter
 */
@Component
@Slf4j
public class ExamServiceClient {

    /** Timeout cho mỗi lần gọi HTTP đến Exam Service */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public ExamServiceClient(@Qualifier("examWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Lấy chi tiết bài thi (không có đáp án).
     * Dùng khi học sinh bắt đầu làm bài.
     *
     * TV2: @CircuitBreaker + @Retry để tránh cascading failure
     */
    @CircuitBreaker(name = "examService", fallbackMethod = "getExamDetailFallback")
    @Retry(name = "examService")
    public ExamDetailDto getExamDetail(Long examId) {
        log.debug("Gọi Exam Service lấy detail examId={}", examId);
        return webClient.get()
                .uri("/exams/{examId}", examId)
                .retrieve()
                .bodyToMono(ExamDetailDto.class)
                .timeout(HTTP_TIMEOUT)
                .block();
    }

    /**
     * Lấy câu hỏi của bài thi — KHÔNG có đáp án (includeAnswers=false).
     * Dùng để hiển thị đề thi cho học sinh làm bài.
     *
     * TV2: @CircuitBreaker + @Retry để tránh cascading failure
     */
    @CircuitBreaker(name = "examService", fallbackMethod = "getExamQuestionsFallback")
    @Retry(name = "examService")
    public ExamQuestionsDto getExamQuestions(Long examId) {
        log.debug("Gọi Exam Service lấy câu hỏi (không đáp án) examId={}", examId);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/exams/{examId}/questions")
                        .queryParam("includeAnswers", "false")
                        .build(examId))
                .retrieve()
                .bodyToMono(ExamQuestionsDto.class)
                .timeout(HTTP_TIMEOUT)
                .block();
    }

    /**
     * Lấy câu hỏi CÓ đáp án đúng (includeAnswers=true).
     * Chỉ gọi nội bộ khi chấm điểm, KHÔNG expose ra bên ngoài.
     *
     * TV2: Đây là điểm giao quan trọng nhất với TV1 (Saga).
     * Khi Circuit OPEN, fallback ném ExamServiceUnavailableException
     * → Orchestrator bắt → compensation rollback GRADING → IN_PROGRESS
     */
    @CircuitBreaker(name = "examService", fallbackMethod = "getExamQuestionsWithAnswersFallback")
    @Retry(name = "examService")
    public ExamQuestionsDto getExamQuestionsWithAnswers(Long examId) {
        log.debug("Gọi Exam Service lấy câu hỏi CÓ đáp án examId={}", examId);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/exams/{examId}/questions")
                        .queryParam("includeAnswers", "true")
                        .build(examId))
                .retrieve()
                .bodyToMono(ExamQuestionsDto.class)
                .timeout(HTTP_TIMEOUT)
                .block();
    }

    // ── Fallback Methods ─────────────────────────────────────────────────────
    // Được gọi khi: (1) Circuit OPEN, (2) Hết số lần retry, (3) Timeout
    // Tất cả fallback đều ném ExamServiceUnavailableException
    // để Saga Orchestrator (TV1) bắt và kích hoạt compensation

    @SuppressWarnings("unused")
    ExamDetailDto getExamDetailFallback(Long examId, Exception ex) {
        log.warn("[Circuit Breaker] getExamDetail fallback kích hoạt cho examId={}: {}",
                examId, ex.getMessage());
        throw new ExamServiceUnavailableException(
                "Exam Service tạm thời không khả dụng khi lấy thông tin đề thi " + examId, ex);
    }

    @SuppressWarnings("unused")
    ExamQuestionsDto getExamQuestionsFallback(Long examId, Exception ex) {
        log.warn("[Circuit Breaker] getExamQuestions fallback kích hoạt cho examId={}: {}",
                examId, ex.getMessage());
        throw new ExamServiceUnavailableException(
                "Exam Service tạm thời không khả dụng khi lấy câu hỏi đề thi " + examId, ex);
    }

    @SuppressWarnings("unused")
    ExamQuestionsDto getExamQuestionsWithAnswersFallback(Long examId, Exception ex) {
        log.warn("[Circuit Breaker] getExamQuestionsWithAnswers fallback kích hoạt cho examId={}: {}. " +
                "Saga sẽ rollback trạng thái bài thi về IN_PROGRESS.", examId, ex.getMessage());
        throw new ExamServiceUnavailableException(
                "Exam Service tạm thời không khả dụng khi chấm điểm đề thi " + examId
                + ". Trạng thái bài thi đã được khôi phục, vui lòng thử lại sau.", ex);
    }

    // ── Inner DTOs (ánh xạ response từ Exam Service) ─────────────────────

    @Data
    @NoArgsConstructor
    public static class ExamDetailDto {
        private Long id;         // Long — khớp với Exam Service @GeneratedValue(IDENTITY)
        private String title;
        private String description;
        private Integer durationMinutes;
        private String status;   // "draft" | "published" | "closed"
        private Integer totalQuestions;
        private String createdBy;
    }

    @Data
    @NoArgsConstructor
    public static class ExamQuestionsDto {
        private Long examId;     // Long — khớp với Exam Service
        private Integer totalQuestions;
        private List<QuestionDto> questions;
    }

    @Data
    @NoArgsConstructor
    public static class QuestionDto {
        private Long questionId;
        private Integer orderIndex;
        private String content;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        /** Chỉ có giá trị khi includeAnswers=true */
        private String correctAnswer;  // "A" | "B" | "C" | "D"
    }
}
