package com.quizapp.submission.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO tóm tắt một bài nộp — dùng cho GET /submissions (danh sách).
 * Không expose entity trực tiếp để tránh vòng lặp JSON và lộ dữ liệu thừa.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionSummaryResponse {

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
}
