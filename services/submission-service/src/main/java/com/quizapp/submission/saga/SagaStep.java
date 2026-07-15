package com.quizapp.submission.saga;

/**
 * Interface cho một bước (Step) trong chuỗi Saga.
 * @param <T> Kiểu dữ liệu của Saga Context
 * @param <R> Kiểu dữ liệu kết quả trả về của Execute
 */
public interface SagaStep<T, R> {
    
    /**
     * Thực hiện hành động chính (chạy tiến).
     */
    R execute(T context);

    /**
     * Thực hiện hành động đền bù (chạy lùi/rollback) khi các bước sau đó bị lỗi.
     */
    void compensate(T context);
}
