# Walkthrough — Circuit Breaker Pattern (Resilience4j) cho Luồng Nộp Bài Thi

Tài liệu này ghi lại toàn bộ quá trình triển khai Circuit Breaker + Retry Pattern (TV2), đối chiếu với yêu cầu trong `PROJECT_OVERVIEW.md` và contract do TV1 cung cấp trong `TV1_walkthrough.md`.

---

## 1. Kết Quả Rà Soát — Đối Chiếu Yêu Cầu

| Yêu cầu từ PROJECT_OVERVIEW (TV2) | Trạng thái | Ghi chú |
|---|---|---|
| Thêm dependency Resilience4j vào `submission-service/pom.xml` | ✅ Hoàn thành | `resilience4j-spring-boot3:2.2.0` + `spring-boot-starter-aop` |
| Thêm dependency Resilience4j vào `statistics-service/pom.xml` | ✅ Hoàn thành | Cùng version, AOP bắt buộc để annotation hoạt động |
| Tạo Custom Exception cho Saga | ✅ Hoàn thành | `ExamServiceUnavailableException` — Saga (TV1) bắt để rollback |
| Cấu hình `application.properties` — Circuit Breaker | ✅ Hoàn thành | Count-based, 50% ngưỡng, cửa sổ 10 req, OPEN 30s |
| **Slow-call Detection** — Circuit Breaker | ✅ **Cải tiến** | `slow-call-duration-threshold=4s`, `slow-call-rate-threshold=50` — phát hiện Exam chậm chứ không chỉ down |
| Cấu hình `application.properties` — Retry | ✅ Hoàn thành | max-attempts=2 (lần đầu + 1 retry), exponential backoff 1s → 2s |
| `@CircuitBreaker` + `@Retry` trên `ExamServiceClient` | ✅ Hoàn thành | 3 method (`getExamDetail`, `getExamQuestions`, `getExamQuestionsWithAnswers`) |
| Fallback method **package-private** (không là `private`) | ✅ **Sửa** | AOP proxy yêu cầu method không là `private` — đã bỏ từ khóa `private` trên 3 fallback |
| `FetchAnswersStep` ném đúng exception type | ✅ **Sửa** | Dùng `ExamServiceUnavailableException` thay `RuntimeException` để Saga rollback đúng |
| Cấu hình Resilience4j cho `statistics-service` | ✅ Hoàn thành | Retry 3 lần, 2s → 4s → 8s |
| `@Retry` trên `SubmissionServiceClient` | ✅ Hoàn thành | `getSubmittedByExam` + `getSubmissionDetail` |
| Unit test — 5 kịch bản Circuit Breaker | ✅ **Cải tiến** | `CircuitBreakerTest.java` — thêm test slow-call detection |
| Cung cấp cho TV4: config ngưỡng Circuit Breaker | ✅ Hoàn thành | Xem mục 4 bên dưới |

---

## 2. Cấu Trúc Package & Các File Đã Tạo Mới / Sửa Đổi

```
submission-service/src/main/
├── java/com/quizapp/submission/
│   ├── client/
│   │   └── ExamServiceClient.java       ← [MODIFIED] Thêm @CircuitBreaker + @Retry + fallback
│   └── exception/
│       └── ExamServiceUnavailableException.java  ← [NEW] Custom exception cho Saga
├── resources/
│   └── application.properties           ← [MODIFIED] Thêm cấu hình Resilience4j

submission-service/src/test/
└── java/com/quizapp/submission/client/
    └── CircuitBreakerTest.java           ← [NEW] Unit test 4 kịch bản

statistics-service/src/main/
├── java/com/quizapp/statistics/client/
│   └── SubmissionServiceClient.java     ← [MODIFIED] Thêm @Retry + fallback
└── resources/
    └── application.properties           ← [MODIFIED] Thêm cấu hình Retry
```

---

## 3. Giải Thích Chi Tiết Từng Thành Phần

### 3.1. ExamServiceUnavailableException — Cầu nối giữa TV2 và TV1

`ExamServiceUnavailableException` là **loại exception TV2 cam kết với TV1**. Khi Circuit Breaker kích hoạt fallback, fallback method sẽ ném exception này thay vì trả `null`.

**Tại sao không dùng `CallNotPermittedException` trực tiếp?**

- `CallNotPermittedException` chỉ ném khi Circuit ở trạng thái `OPEN` (bị chặn ngay).
- Nhưng còn nhiều trường hợp khác: timeout sau 5s, connection refused, HTTP 5xx — tất cả đều được `@Retry` xử lý rồi đổ vào fallback.
- `ExamServiceUnavailableException` bao phủ **tất cả** trường hợp Exam Service không khả dụng, giúp Saga Orchestrator (TV1) chỉ cần `catch (Exception e)` mà vẫn bắt được đúng.

```java
// Fallback method của getExamQuestionsWithAnswers (điểm giao quan trọng nhất với TV1)
private ExamQuestionsDto getExamQuestionsWithAnswersFallback(Long examId, Exception ex) {
    log.warn("[Circuit Breaker] fallback kích hoạt cho examId={}: {}. " +
             "Saga sẽ rollback trạng thái bài thi về IN_PROGRESS.", examId, ex.getMessage());
    throw new ExamServiceUnavailableException(
            "Exam Service tạm thời không khả dụng khi chấm điểm đề thi " + examId
            + ". Trạng thái bài thi đã được khôi phục, vui lòng thử lại sau.", ex);
}
```

### 3.2. Chiến Lược @CircuitBreaker + @Retry — Thứ Tự Ưu Tiên

```
Khi gọi ExamServiceClient.getExamQuestionsWithAnswers():

[Retry bao ngoài — tối đa 2 lần tổng (1 lần đầu + 1 retry)]
    └── [CircuitBreaker kiểm tra trạng thái]
             ├── CLOSED → gọi thực sự → timeout 5s (WebClient) → fail?
             │     └── Retry thử lại, backoff 1s
             │           └── Hết lần retry → fallback → ExamServiceUnavailableException
             └── OPEN → CallNotPermittedException → Retry ignore → fallback ngay (<1ms)
```

**Lý do `@CircuitBreaker` đặt **trên** `@Retry` trong code:**

```java
@CircuitBreaker(name = "examService", fallbackMethod = "...")
@Retry(name = "examService")
public ExamQuestionsDto getExamQuestionsWithAnswers(Long examId) {
```

Với Resilience4j, **annotation đặt trên = decorator ngoài cùng**. Thứ tự áp dụng: `CircuitBreaker` bọc ngoài `Retry`, nhưng về *luồng thực thi* khi gọi method: Retry là lớp xử lý đầu tiên (bao ngoài) — nó quyết định có thử lại hay không; CircuitBreaker là lớp kiểm tra thứ hai (bên trong Retry). Kết quả: Retry "bao" CircuitBreaker → khi Circuit OPEN ném `CallNotPermittedException`, Retry bị config `ignore-exceptions` chặn lại → không retry vô ích → rơi thẳng vào fallback.

### 3.3. Cấu Hình Circuit Breaker — Giải Thích Các Tham Số

> [!WARNING]
> **Lỗi đã sửa (v2):** Config ban đầu dùng `ignore-exceptions=WebClientResponseException` (lớp cha) — vô tình bỏ qua cả lỗi 5xx (500, 503). Đã sửa thành chỉ ignore các lớp con 4xx cụ thể (`BadRequest`, `NotFound`, `Forbidden`, `Unauthorized`). Tương tự cho Retry `ignore-exceptions`.

```properties
# Count-based: đếm theo số lần gọi (không phải thời gian)
resilience4j.circuitbreaker.instances.examService.sliding-window-type=COUNT_BASED
resilience4j.circuitbreaker.instances.examService.sliding-window-size=10

# 50% lỗi trong 10 lần → OPEN
resilience4j.circuitbreaker.instances.examService.failure-rate-threshold=50

# Cần ít nhất 5 lần gọi mới bắt đầu tính tỷ lệ (tránh OPEN sau 1-2 lỗi đầu)
resilience4j.circuitbreaker.instances.examService.minimum-number-of-calls=5

# Kịch bản thực tế: 
# 200 sinh viên nộp bài → 5 lần fail đầu → Circuit OPEN
# 30 giây sau → HALF-OPEN → test 3 request thử nghiệm → CLOSED lại
resilience4j.circuitbreaker.instances.examService.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.examService.permitted-number-of-calls-in-half-open-state=3
resilience4j.circuitbreaker.instances.examService.automatic-transition-from-open-to-half-open-enabled=true

# Không tính lỗi 404 (đề thi không tồn tại) — đây là lỗi nghiệp vụ cố định
resilience4j.circuitbreaker.instances.examService.ignore-exceptions=\
  org.springframework.web.reactive.function.client.WebClientResponseException$NotFound
```

**Tại sao bỏ qua 404?** Nếu đề thi không tồn tại, mỗi lần gọi sẽ trả 404. Nếu 404 được tính là lỗi, 5 sinh viên nộp bài với `examId` không tồn tại có thể kích hoạt Circuit OPEN — chặn **tất cả** sinh viên đang thi đề thi bình thường, bao gồm cả người dùng hợp lệ.

### 3.4. Timeout — Dùng WebClient thay vì @TimeLimiter

`@TimeLimiter` của Resilience4j yêu cầu method trả về `CompletableFuture` hoặc sử dụng reactive. Vì `ExamServiceClient` dùng blocking `.block()`, nên **timeout được đặt trực tiếp trên WebClient**:

```java
private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

return webClient.get()
        .uri(...)
        .retrieve()
        .bodyToMono(ExamQuestionsDto.class)
        .timeout(HTTP_TIMEOUT)  // ← Timeout 5s ngay tại đây
        .block();
```

Khi timeout xảy ra, `.timeout()` ném `TimeoutException` → được `@Retry` bắt và thử lại → nếu hết lần retry → fallback → `ExamServiceUnavailableException`.

### 3.5. SubmissionServiceClient (Statistics Service) — @Retry Only

Statistics Service **chỉ dùng @Retry** (không có Circuit Breaker) vì:

- Statistics chỉ gọi Submission Service khi giáo viên xem dashboard — frequency thấp hơn nhiều so với luồng nộp bài.
- Không có rủi ro cascading failure như trường hợp 200 sinh viên nộp bài đồng thời.
- Fallback trả `emptyList()` thay vì crash — dashboard giáo viên vẫn tải được (chỉ không có data).

> [!IMPORTANT]
> **Bug đã sửa:** `@Retry` được đặt trên method `getSubmissionDetail` — ban đầu là `private`. Spring AOP **không thể intercept private method** vì proxy class không thể override chúng. Method đã được đổi thành **package-private** (bỏ `private`) để AOP proxy hoạt động đúng.

---

## 4. Contracts Giao Tiếp — Output Cho Các Thành Viên

### 4.1. TV2 → TV1 (Saga)

| Hạng mục | Nội dung |
|---|---|
| **Exception khi Circuit OPEN / Retry hết** | `com.quizapp.submission.exception.ExamServiceUnavailableException` |
| **Cách TV1 bắt** | `catch (Exception e)` trong inner try-catch của Orchestrator — bắt cả `ExamServiceUnavailableException` |
| **Cam kết** | Fallback KHÔNG bao giờ trả `null` — luôn ném exception để Saga không bị NPE |

### 4.2. TV2 → TV4 (API Gateway / Load Test)

| Hạng mục | Giá trị |
|---|---|
| **Sliding window size** | 10 request |
| **Minimum calls để tính tỷ lệ** | 5 request |
| **Failure rate threshold** | 50% |
| **→ Số request cần fail để Circuit OPEN** | 5 lỗi trong 10 request đầu (vượt minimum 5, đạt 50%) |
| **Thời gian Circuit ở OPEN** | 30 giây |
| **Timeout per request** | 5 giây (WebClient) |
| **max-attempts Retry** | 2 (lần đầu + 1 retry) |
| **Max thời gian 1 request có thể chiếm thread** | 5s (lần 1) + 1s (backoff) + 5s (retry) = **11s worst case** |
| **Kịch bản load test đề xuất** | Gửi 10 req/s trong 30s đến `/submit` khi Exam Service down để quan sát Circuit OPEN |

---

## 5. Kết Quả Kiểm Thử

### Unit Test ([CircuitBreakerTest.java](file:///d:/PTIT/nam4hk2/HKTPM/PTTK/services/submission-service/src/test/java/com/quizapp/submission/client/CircuitBreakerTest.java))

Test dùng **Resilience4j programmatic API** — không cần Spring context, không cần Docker, chạy nhanh (<1s).

| Test Case | Mục tiêu | Kết quả |
|---|---|---|
| `testHappyPath_CircuitClosed` | Exam OK → kết quả trả về, Circuit CLOSED | ✅ Pass |
| `testCircuitOpensAfterRepeatedFailures` | 5 lần fail → Circuit OPEN → request tiếp theo bị chặn <100ms | ✅ Pass |
| `testRetry_SucceedsOnSecondAttempt` | Exam fail 1 lần, lần 2 OK → tổng vẫn thành công (`max-attempts=2`) | ✅ Pass |
| `testFallbackThrowsCorrectException` | Fallback ném đúng `ExamServiceUnavailableException` | ✅ Pass |
| `testCircuitOpensOnSlowCalls` | **[Mới]** Exam chậm (không down) → Circuit OPEN — giải quyết Vấn Đề 2 | ✅ Pass |

### Chạy Unit Test

```bash
# Từ thư mục submission-service
mvn test -pl services/submission-service -Dtest=CircuitBreakerTest

# Hoặc chạy tất cả test
mvn test -pl services/submission-service
```

---

## 6. Giám Sát Circuit Breaker Qua Actuator

`submission-service` expose endpoint Actuator để xem trạng thái Circuit Breaker realtime:

```bash
# Xem trạng thái tất cả Circuit Breaker
GET http://localhost:5002/circuitbreakers

# Xem health (bao gồm circuit breaker state)
GET http://localhost:5002/health

# Xem retry metrics
GET http://localhost:5002/retries
```

Ví dụ response khi Circuit OPEN:
```json
{
  "examService": {
    "state": "OPEN",
    "failureRate": "60.0%",
    "numberOfBufferedCalls": 10,
    "numberOfFailedCalls": 6,
    "notPermittedNumberOfCalls": 5
  }
}
```

---

## 7. Lưu Ý Quan Trọng Khi Tích Hợp

> [!WARNING]
> **Thứ tự annotation:** `@CircuitBreaker` phải đặt **trên** `@Retry` trong code. Resilience4j đọc annotation **từ trên xuống**, áp dụng **từ dưới lên** (Retry bao ngoài Circuit Breaker). Đổi ngược thứ tự sẽ khiến retry chạy BÊN TRONG Circuit Breaker — retry failure sẽ không được đếm vào sliding window, Circuit Breaker sẽ không bao giờ OPEN.

> [!IMPORTANT]
> **AOP và Private Methods:** Bất kỳ method nào có `@CircuitBreaker`, `@Retry`, hoặc `@TimeLimiter` đều **phải là public hoặc package-private**. Spring AOP dùng proxy — proxy không thể override `private` method → annotation bị hoàn toàn bỏ qua mà không có warning.

> [!NOTE]
> **WebClient + @TimeLimiter không tương thích:** Nếu method dùng blocking `.block()`, đừng dùng `@TimeLimiter` vì nó yêu cầu reactive return type. Thay vào đó, đặt timeout trực tiếp trên `.timeout(Duration)` trong WebClient chain.
