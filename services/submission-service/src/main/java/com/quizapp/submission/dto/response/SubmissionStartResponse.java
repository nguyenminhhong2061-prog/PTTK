package com.quizapp.submission.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quizapp.submission.enums.AnswerOption;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response khi học sinh bắt đầu làm bài (POST /submissions).
 * Chứa thông tin phiên thi và danh sách câu hỏi (không có đáp án).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionStartResponse {

    private String submissionId;
    private String examId;
    private String examTitle;
    private String studentId;
    private String status;
    private Integer totalQuestions;
    private Integer durationMinutes;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private List<QuestionItem> questions;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionItem {
        private String questionId;
        private Integer orderIndex;
        private String content;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        /** Đáp án học sinh đã chọn (null nếu chưa trả lời — dùng khi resume) */
        private AnswerOption selectedOption;
    }
}
