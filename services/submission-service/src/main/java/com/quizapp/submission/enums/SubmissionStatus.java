package com.quizapp.submission.enums;

/**
 * Trạng thái của một bài nộp.
 * IN_PROGRESS: học sinh đang làm bài
 * SUBMITTED:   học sinh đã nộp, có điểm số
 */
public enum SubmissionStatus {
    IN_PROGRESS,
    GRADING,
    SUBMITTED
}
