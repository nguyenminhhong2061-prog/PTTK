package com.quizapp.submission.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Outbox Pattern — Pattern 3.
 *
 * Mỗi dòng là 1 event chờ được publish sang RabbitMQ. Được ghi vào CÙNG
 * database transaction với việc lưu điểm bài thi (xem GradeAndSaveStep),
 * nên "lưu điểm" và "ghi event" luôn atomic: cả 2 cùng thành công hoặc
 * cả 2 cùng rollback — không bao giờ có trường hợp lưu điểm xong mà
 * event bị mất.
 *
 * status:
 *   PENDING — chưa publish, đang chờ OutboxRelayJob xử lý
 *   SENT    — đã publish thành công sang RabbitMQ
 *   FAILED  — đã thử publish quá số lần cho phép (retryCount >= 5), cần can thiệp thủ công
 */
@Entity
@Table(name = "outbox_events")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OutboxEvent {

    @Id
    @Column(length = 36)
    private String id;

    /** Loại event, ví dụ "ExamSubmitted" */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** ID của entity gốc sinh ra event — ở đây là submissionId, dùng để trace/debug */
    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    /** Payload JSON đầy đủ của event, publish nguyên văn sang RabbitMQ */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
