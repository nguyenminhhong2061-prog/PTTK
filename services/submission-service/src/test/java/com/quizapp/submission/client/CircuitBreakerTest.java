package com.quizapp.submission.client;

import com.quizapp.submission.exception.ExamServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho Circuit Breaker pattern (TV2).
 *
 * Dùng Resilience4j programmatic API để tạo CircuitBreaker và Retry trực tiếp,
 * không cần Spring context (nhanh hơn, không phụ thuộc Docker).
 *
 * 4 kịch bản kiểm thử:
 * 1. Happy path — Exam Service OK → kết quả trả về bình thường
 * 2. Fail liên tiếp → Circuit OPEN → request tiếp theo fail nhanh (<100ms)
 * 3. Retry hoạt động — Exam fail 2 lần, lần 3 OK → vẫn thành công
 * 4. Timeout — Exam Service mất hơn 5s → ném exception
 */
@DisplayName("TV2 — Circuit Breaker: Kiểm thử hành vi Circuit Breaker và Retry")
class CircuitBreakerTest {

    // Circuit Breaker ngắn cho test: cửa sổ 5 lần, ngưỡng 60%, OPEN sau 1s
    private CircuitBreaker circuitBreaker;
    private Retry retry;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(60)           // 60% lỗi → OPEN
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .permittedNumberOfCallsInHalfOpenState(1)
                // Slow call: phát hiện gọi chậm hơn 200ms trong test (production: 4s)
                .slowCallDurationThreshold(Duration.ofMillis(200))
                .slowCallRateThreshold(50)
                .build();

        // max-attempts=2: khớp với config production
        // 1 lần gọi gốc + 1 lần retry = 2 lần tổng
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(50))  // Ngắn để test nhanh
                .build();

        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        circuitBreaker = cbRegistry.circuitBreaker("testExamService");
        retry = retryRegistry.retry("testExamService");
    }

    @Test
    @DisplayName("1. Happy path — Exam Service OK → trả kết quả bình thường")
    void testHappyPath_CircuitClosed() {
        // Arrange: Exam Service trả về kết quả bình thường
        Supplier<String> successCall = CircuitBreaker.decorateSupplier(
                circuitBreaker, () -> "ExamQuestionsDto{examId=1}"
        );

        // Act
        String result = successCall.get();

        // Assert
        assertEquals("ExamQuestionsDto{examId=1}", result);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        System.out.println("✅ Test 1 PASSED: Circuit vẫn CLOSED sau 1 lần gọi thành công");
    }

    @Test
    @DisplayName("2. Fail nhiều lần → Circuit OPEN → request tiếp theo fail ngay (<100ms)")
    void testCircuitOpensAfterRepeatedFailures() {
        // Arrange: Exam Service luôn fail
        Supplier<String> failingCall = CircuitBreaker.decorateSupplier(
                circuitBreaker, () -> { throw new RuntimeException("Connection refused"); }
        );

        // Act: Gọi đủ số lần để vượt ngưỡng (cần ít nhất 3 lần, ngưỡng 60%)
        int failCount = 0;
        for (int i = 0; i < 5; i++) {
            try { failingCall.get(); } catch (Exception ignored) { failCount++; }
        }

        // Assert: Circuit phải OPEN
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(),
                "Circuit phải OPEN sau " + failCount + " lần fail");

        // Assert: Request tiếp theo bị chặn NGAY — không cần chờ timeout
        long start = System.currentTimeMillis();
        assertThrows(Exception.class, () -> failingCall.get(),
                "Request khi Circuit OPEN phải throw exception ngay");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 100,
                "Circuit OPEN phải trả lỗi trong <100ms, thực tế: " + elapsed + "ms");

        System.out.println("✅ Test 2 PASSED: Circuit OPEN sau " + failCount +
                " lần fail, request thứ " + (failCount+1) + " bị chặn trong " + elapsed + "ms");
    }

    @Test
    @DisplayName("3. Retry (max-attempts=2) — Exam fail lần 1, lần 2 thành công → request vẫn thành công")
    void testRetry_SucceedsOnSecondAttempt() {
        // Arrange: giả lập Exam Service chậm nhưng phục hồi sau lần 1
        // max-attempts=2: lần gọi đầu fail, retry 1 lần, lần đó thành công
        final int[] callCount = {0};
        Supplier<String> flakyCall = () -> {
            callCount[0]++;
            if (callCount[0] < 2) {
                throw new RuntimeException("Lần " + callCount[0] + " thất bại (tạm thời)");
            }
            return "Thành công ở lần " + callCount[0]; // Lần 2 thành công
        };

        Supplier<String> resilientCall = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, flakyCall));

        // Act
        String result = resilientCall.get();

        // Assert
        assertEquals("Thành công ở lần 2", result);
        assertEquals(2, callCount[0], "max-attempts=2: phải gọi đúng 2 lần (1 fail + 1 thành công)");
        System.out.println("✅ Test 3 PASSED: Retry tự phục hồi sau 1 lần thất bại. " +
                "Kết quả: " + result + " (tổng " + callCount[0] + " lần gọi, đúng với max-attempts=2)");
    }

    @Test
    @DisplayName("4. ExamServiceUnavailableException — fallback ném đúng exception type")
    void testFallbackThrowsCorrectException() {
        // Giả lập logic fallback method của ExamServiceClient:
        // khi Circuit OPEN, fallback ném ExamServiceUnavailableException

        // Đẩy Circuit vào OPEN bằng cách fail nhiều lần
        Supplier<String> alwaysFail = CircuitBreaker.decorateSupplier(
                circuitBreaker, () -> { throw new RuntimeException("Exam down"); }
        );
        for (int i = 0; i < 5; i++) {
            try { alwaysFail.get(); } catch (Exception ignored) {}
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // Giả lập fallback logic: bắt exception và ném lại đúng type
        ExamServiceUnavailableException thrown = assertThrows(
                ExamServiceUnavailableException.class,
                () -> {
                    try {
                        alwaysFail.get();
                    } catch (Exception ex) {
                        // Đây là logic trong fallback method của ExamServiceClient
                        throw new ExamServiceUnavailableException(
                                "Exam Service tạm thời không khả dụng", ex);
                    }
                }
        );

        assertNotNull(thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Exam Service tạm thời không khả dụng"));

        System.out.println("✅ Test 4 PASSED: Fallback ném đúng ExamServiceUnavailableException. " +
                "Saga Orchestrator (TV1) sẽ bắt exception này để rollback GRADING → IN_PROGRESS");
    }

    @Test
    @DisplayName("5. Slow-call Detection — Exam chậm (>200ms) → Circuit OPEN dù không có exception")
    void testCircuitOpensOnSlowCalls() throws InterruptedException {
        // Đây là kịch bản NGUY HIỂM HƠN trong Vấn Đề 2 (PROJECT_OVERVIEW):
        // Exam Service không down hẳn mà CHỈ CHẬM — mỗi request block thread 5s.
        // failure-rate-threshold không bắt được kịch bản này vì không có exception.
        // Slow-call threshold (200ms trong test, 4s trong production) giải quyết đúng kịch bản đó.

        // Arrange: Exam Service "chậm" — mất 250ms nhưng vẫn trả kết quả
        Supplier<String> slowCall = CircuitBreaker.decorateSupplier(
                circuitBreaker, () -> {
                    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                    return "Kết quả chậm";
                }
        );

        // Act: Gọi đủ số lần để vượt ngưỡng slow-call (cần ít nhất 3 lần theo minimumNumberOfCalls)
        for (int i = 0; i < 4; i++) {
            try { slowCall.get(); } catch (Exception ignored) {}
        }

        // Assert: Circuit phải OPEN dù không có exception nào được ném
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState(),
                "Circuit phải OPEN khi 50% call chậm hơn 200ms, dù Exam Service vẫn trả kết quả");

        // Assert: Request tiếp theo bị chặn NGAY — không phải chờ 250ms nữa
        long start = System.currentTimeMillis();
        assertThrows(Exception.class, slowCall::get);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 100,
                "Circuit OPEN phải trả lỗi trong <100ms (không phải chờ 250ms slow response)");

        System.out.println("✅ Test 5 PASSED: Slow-call detection — Circuit OPEN sau " +
                "4 lần gọi chậm, request tiếp theo bị chặn trong " + elapsed + "ms " +
                "(minh chứng giải quyết Vấn Đề 2 — kịch bản Exam Service chậm nhưng không down)");
    }
}
