package com.quizapp.submission.client;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

/**
 * Client gọi Exam Service để lấy thông tin bài thi và câu hỏi.
 * Sử dụng Spring WebClient (reactive) với .block() để chờ đồng bộ.
 */
@Component
public class ExamServiceClient {

    private final WebClient webClient;

    public ExamServiceClient(@Qualifier("examWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Lấy chi tiết bài thi (không có đáp án).
     * Dùng khi học sinh bắt đầu làm bài.
     */
    public ExamDetailDto getExamDetail(Long examId) {
        return webClient.get()
                .uri("/exams/{examId}", examId)
                .retrieve()
                .bodyToMono(ExamDetailDto.class)
                .block();
    }

    /**
     * Lấy câu hỏi của bài thi — KHÔNG có đáp án (includeAnswers=false).
     * Dùng để hiển thị đề thi cho học sinh làm bài.
     */
    public ExamQuestionsDto getExamQuestions(Long examId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/exams/{examId}/questions")
                        .queryParam("includeAnswers", "false")
                        .build(examId))
                .retrieve()
                .bodyToMono(ExamQuestionsDto.class)
                .block();
    }

    /**
     * Lấy câu hỏi CÓ đáp án đúng (includeAnswers=true).
     * Chỉ gọi nội bộ khi chấm điểm, KHÔNG expose ra bên ngoài.
     */
    public ExamQuestionsDto getExamQuestionsWithAnswers(Long examId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/exams/{examId}/questions")
                        .queryParam("includeAnswers", "true")
                        .build(examId))
                .retrieve()
                .bodyToMono(ExamQuestionsDto.class)
                .block();
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
        private String questionId;
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
