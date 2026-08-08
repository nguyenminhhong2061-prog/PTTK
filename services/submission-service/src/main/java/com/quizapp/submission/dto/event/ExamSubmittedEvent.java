package com.quizapp.submission.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Versioned contract emitted by the Submission Outbox and consumed by the
 * Statistics Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSubmittedEvent {
    private String eventId;
    private String eventType;
    private Integer eventVersion;
    private Instant occurredAt;
    private String submissionId;
    private Long examId;
    private String studentId;
    private Double score;
}
