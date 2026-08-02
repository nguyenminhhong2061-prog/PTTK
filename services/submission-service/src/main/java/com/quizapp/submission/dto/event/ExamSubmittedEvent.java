package com.quizapp.submission.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payload của event "ExamSubmitted" — được serialize thành JSON, ghi vào bảng
 * outbox, và cuối cùng publish sang RabbitMQ (routing key "exam.submitted").
 *
 * Đây là CONTRACT giữa Submission Service (publisher — TV3) và Statistics
 * Service (consumer — TV4). Đổi field ở đây bắt buộc phải đồng bộ với
 * ExamSubmittedEvent bên statistics-service.
 */
@Data
@Builder
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
