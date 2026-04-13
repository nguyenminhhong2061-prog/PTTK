package com.quizapp.submission.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.quizapp.submission.enums.AnswerOption;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Entity đại diện cho câu trả lời của học sinh cho một câu hỏi.
 * Được tạo tự động khi học sinh bắt đầu làm bài (selectedOption = null).
 * Được cập nhật khi học sinh chọn đáp án.
 * Sau khi nộp bài, isCorrect được set dựa trên việc chấm điểm.
 */
@Entity
@Table(
    name = "answers",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_submission_question",
        columnNames = {"submission_id", "question_id"}
    )
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Answer {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    @JsonIgnore  // Ngăn vòng lặp JSON: Answer → Submission → answers → Answer...
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Submission submission;

    @Column(name = "question_id", length = 36, nullable = false)
    private String questionId;

    /** Thứ tự câu hỏi trong bài thi */
    @Column(name = "order_index")
    private Integer orderIndex;

    /**
     * Đáp án học sinh chọn (A/B/C/D), null = bỏ qua câu này.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "selected_option", length = 1)
    private AnswerOption selectedOption;

    /**
     * Kết quả chấm (true/false), null = chưa chấm.
     */
    @Column(name = "is_correct")
    private Boolean isCorrect;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
