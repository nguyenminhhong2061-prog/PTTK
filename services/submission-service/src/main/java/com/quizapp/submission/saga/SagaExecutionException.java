package com.quizapp.submission.saga;

public class SagaExecutionException extends RuntimeException {
    public SagaExecutionException(String message) {
        super(message);
    }

    public SagaExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
