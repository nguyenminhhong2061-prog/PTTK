package com.quizapp.submission.entity;

import com.quizapp.submission.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity đại diện cho một phiên làm bài của học sinh.
 * Mỗi học sinh chỉ có tối đa 1 Submission (IN_PROGRESS hoặc SUBMITTED) cho mỗi bài thi.
 */
@Entity
@Table(
    name = "submissions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_student_exam",
        columnNames = {"student_id", "exam_id"}
    )
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Submission {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "exam_id", length = 36, nullable = false)
    private String examId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.IN_PROGRESS;

    /** Điểm số (0-100), null khi chưa nộp */
    @Column(precision = 5)
    private Double score;

    /** Số câu đúng, null khi chưa nộp */
    @Column(name = "correct_count")
    private Integer correctCount;

    /** Tổng số câu hỏi của bài thi */
    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** Deadline = startedAt + durationMinutes, dùng để kiểm tra hết giờ */
    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Answer> answers = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }
}
