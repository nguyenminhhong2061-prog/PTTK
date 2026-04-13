package com.quizapp.submission.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body khi học sinh bắt đầu làm bài.
 */
@Data
public class StartExamRequest {

    /** ID bài thi (kiểu Long — khớp với Exam Service) */
    @NotNull(message = "examId không được để trống")
    private Long examId;

    @NotBlank(message = "studentId không được để trống")
    private String studentId;
}
