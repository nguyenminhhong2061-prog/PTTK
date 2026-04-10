package com.quizapp.submission.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body khi học sinh bắt đầu làm bài.
 */
@Data
public class StartExamRequest {

    @NotBlank(message = "examId không được để trống")
    private String examId;

    @NotBlank(message = "studentId không được để trống")
    private String studentId;
}
