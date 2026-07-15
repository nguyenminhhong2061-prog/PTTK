package com.quizapp.submission.saga;

public class SagaRollbackException extends RuntimeException {
    public SagaRollbackException(String message) {
        super(message);
    }

    public SagaRollbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
