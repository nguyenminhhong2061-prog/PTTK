package com.quizapp.statistics.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payload nhận được từ queue "statistics.exam-submitted".
 * PHẢI khớp field-by-field với ExamSubmittedEvent bên submission-service —
 * đây là contract publisher (TV3) ↔ consumer (TV4).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamSubmittedEvent {
    private String submissionId;
    private Long examId;
    private String studentId;
    private Double score;
    private Integer correctCount;
    private Integer totalQuestions;
    private LocalDateTime submittedAt;
}
