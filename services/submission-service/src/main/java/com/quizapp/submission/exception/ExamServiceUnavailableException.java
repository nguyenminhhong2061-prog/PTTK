package com.quizapp.submission.exception;

/**
 * Exception ném ra khi Circuit Breaker cho ExamService ở trạng thái OPEN
 * hoặc khi request đến Exam Service thất bại sau khi đã hết số lần retry.
 *
 * Saga Orchestrator (TV1) bắt exception này trong inner try-catch
 * để kích hoạt compensation (rollback GRADING → IN_PROGRESS).
 *
 * Pattern: Circuit Breaker Fallback Exception (TV2)
 */
public class ExamServiceUnavailableException extends RuntimeException {

    public ExamServiceUnavailableException(String message) {
        super(message);
    }

    public ExamServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
