package com.quizapp.submission.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quizapp.submission.enums.AnswerOption;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response chi tiết bài nộp (GET /submissions/{id}).
 * Nếu chưa nộp: không có correctAnswer.
 * Nếu đã nộp:   có correctAnswer và isCorrect cho từng câu.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionDetailResponse {

    private String id;
    private Long examId;        // Long — khớp với Exam Service
    private String studentId;
    private String status;
    private Double score;
    private Integer correctCount;
    private Integer totalQuestions;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime submittedAt;
    private List<AnswerDetail> answers;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AnswerDetail {
        private String questionId;
        private Integer orderIndex;
        private AnswerOption selectedOption;
        /** Chỉ có sau khi nộp bài */
        private String correctAnswer;
        /** Chỉ có sau khi nộp bài */
        private Boolean isCorrect;
    }
}
