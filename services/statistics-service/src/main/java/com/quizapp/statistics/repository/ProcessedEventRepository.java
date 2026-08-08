package com.quizapp.statistics.repository;

import com.quizapp.statistics.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO processed_events
                (event_id, submission_id, event_type, event_version, exam_id,
                 student_id, score, occurred_at, processed_at)
            VALUES
                (:eventId, :submissionId, :eventType, :eventVersion, :examId,
                 :studentId, :score, :occurredAt, :processedAt)
            """, nativeQuery = true)
    int claimEvent(
            @Param("eventId") String eventId,
            @Param("submissionId") String submissionId,
            @Param("eventType") String eventType,
            @Param("eventVersion") Integer eventVersion,
            @Param("examId") Long examId,
            @Param("studentId") String studentId,
            @Param("score") Double score,
            @Param("occurredAt") Instant occurredAt,
            @Param("processedAt") LocalDateTime processedAt
    );
}
