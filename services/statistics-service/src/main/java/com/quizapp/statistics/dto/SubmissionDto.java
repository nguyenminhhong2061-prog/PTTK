package com.quizapp.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionDto {
    private String id;
    private String examId;
    private String studentId;
    private String status;
    private Double score;
    private Integer correctCount;
    private Integer totalQuestions;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime submittedAt;
    private List<AnswerDto> answers;
}
