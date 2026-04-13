package com.quizapp.submission.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quizapp.submission.enums.AnswerOption;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response sau khi học sinh nộp bài (POST /submissions/{id}/submit).
 * Trả về điểm và đáp án đúng ngay lập tức.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmitResponse {

    private String submissionId;
    private Long examId;        // Long — khớp với Exam Service
    private String studentId;
    private Double score;
    private Integer correctCount;
    private Integer totalQuestions;
    /** true nếu score >= 50 */
    private Boolean passed;
    private LocalDateTime submittedAt;
    private List<AnswerResult> answers;

    @Data
    @Builder
    public static class AnswerResult {
        private String questionId;
        private Integer orderIndex;
        private AnswerOption selectedOption;
        private String correctAnswer;
        private Boolean isCorrect;
    }
}
