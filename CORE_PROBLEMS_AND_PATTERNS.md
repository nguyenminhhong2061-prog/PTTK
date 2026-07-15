# Vấn Đề Cốt Lõi & Microservices Patterns
## Bài toán: Học Sinh Nộp Bài Thi

> **Hệ thống**: Thi Trắc Nghiệm Online — PTIT 2026  
> **Tài liệu này**: Mô tả 3 vấn đề, 4 pattern giải quyết, và phân công chi tiết cho 4 thành viên.

---

## Bức Tranh Tổng Thể

Kỳ thi giữa kỳ với **200 sinh viên** cùng thi lúc 8h sáng. Lúc 8:55 (5 phút cuối), hàng loạt sinh viên cùng bấm "Nộp bài". Mỗi lần nộp bài phải đi qua chuỗi bước:

```
Sinh viên bấm "Nộp bài"
         │
         ▼
[Submission Service]
  Bước 1: Validate (còn IN_PROGRESS? chưa nộp?)
  Bước 2: Gọi HTTP → [Exam Service] lấy đáp án đúng
  Bước 3: Chấm điểm (so sánh đáp án)
  Bước 4: Lưu điểm vào submission_db
  Bước 5: Thông báo → [Statistics Service] cập nhật thống kê
         │
         ▼
  Trả điểm về cho sinh viên
```

Luồng này đi qua **3 service**, phải xử lý **200 request đồng thời**, và **không được mất dữ liệu**. Hệ thống hiện tại làm tất cả trong 1 HTTP request đồng bộ, không có bất kỳ cơ chế xử lý lỗi nào.

---

## VẤN ĐỀ 1: Chuỗi gọi bị đứt giữa chừng → Bài thi bị "treo"

### Ngữ cảnh

Exam Service chạy trên 1 container riêng. Khi 200 request đổ vào lúc 8:55, Exam Service có thể restart, bị quá tải, hoặc đơn giản là mất kết nối mạng nội bộ Docker trong vài giây.

### Vấn đề trong code hiện tại

```java
// SubmissionService.java — hiện tại
@Transactional
public SubmitResponse submit(String submissionId, SaveAnswersRequest lastAnswers) {
    Submission submission = getSubmissionOrThrow(submissionId);

    if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
        throw new IllegalStateException("Bài đã được nộp trước đó.");
    }

    // Lưu đáp án cuối
    if (lastAnswers != null) saveAnswers(submissionId, lastAnswers);

    // ← GỌI HTTP ĐẾN EXAM SERVICE (có thể fail bất cứ lúc nào)
    ExamServiceClient.ExamQuestionsDto examWithAnswers;
    try {
        examWithAnswers = examServiceClient.getExamQuestionsWithAnswers(submission.getExamId());
    } catch (Exception e) {
        throw new RuntimeException("Không thể chấm điểm lúc này...");
        // ← Chỉ throw exception, không có compensation
        // ← Status vẫn là IN_PROGRESS (may mắn trong trường hợp này)
        // ← Nhưng nếu sau này thêm bước đổi status trước khi gọi HTTP
        //    → bài sẽ bị TREO ở trạng thái trung gian mãi mãi
    }

    // Chấm điểm và lưu kết quả
    submission.setStatus(SubmissionStatus.SUBMITTED);
    submission.setScore(result.getScore());
    submissionRepository.save(submission);
}
```

### Hậu quả

- Không có trạng thái trung gian `GRADING` → không thể phân biệt "đang chấm" với "chưa nộp"
- Nếu thêm bước xử lý phức tạp hơn, status có thể bị kẹt → bài "treo" vĩnh viễn
- Admin phải sửa database bằng tay mỗi khi xảy ra lỗi

### Pattern giải quyết: **Saga Orchestration** (TV1)

Chia luồng thành các bước rõ ràng, mỗi bước có **compensation** (hành động rollback):

```
Bước 1: Validate                     | Compensation: —
Bước 2: Đổi status = GRADING         | Compensation: status = IN_PROGRESS
Bước 3: Gọi Exam Service lấy đáp án | Compensation: kích hoạt Bước 2
Bước 4: Chấm điểm                   | Compensation: —
Bước 5: Lưu điểm + Outbox event     | Compensation: xóa record nếu cần
```

**Kết quả:** Exam Service fail ở Bước 3 → Saga tự kích hoạt compensation → status về `IN_PROGRESS` → sinh viên nộp lại được.

---

## VẤN ĐỀ 2: Một service chậm kéo chết toàn bộ hệ thống

### Ngữ cảnh

Spring Boot mặc định dùng thread pool có giới hạn (~200 threads). Mỗi request HTTP đang chờ chiếm 1 thread.

### Vấn đề

```
8:55:00 — 200 sinh viên nộp bài
           Mỗi request gọi Exam Service → Exam đang quá tải → không phản hồi
           Mỗi request chờ timeout mặc định = 30 giây

8:55:00 → 8:55:30:
  200 requests × 30 giây chờ = 200 threads BỊ KHÓA
  Thread pool của Submission Service HẾT THREAD

  Hệ quả:
  → Auto-save (PUT /answers) cũng không được xử lý
  → 200 sinh viên mất kết nối, mất bài chưa save
  → Exam Service chỉ bị chậm, nhưng Submission Service "chết" hẳn
```

**Đây là Cascading Failure** — 1 service gặp vấn đề kéo theo service khác sập.

### Pattern giải quyết: **Circuit Breaker + Retry + Timeout** (TV2)

```
CLOSED ─(5 lỗi liên tiếp)─► OPEN ─(30s)─► HALF-OPEN ─(test OK)─► CLOSED
(cho qua)                   (chặn ngay)    (thử lại)              (bình thường)
```

**Timeline sau khi có Circuit Breaker:**
```
8:55:00 — 5 request đầu fail → Circuit OPEN
8:55:05 — Request thứ 6 → Circuit Breaker trả lỗi NGAY (< 1ms, không gọi Exam)
           Submission Service KHÔNG bị block → vẫn SỐNG
           Auto-save vẫn hoạt động → sinh viên không mất bài
8:56:00 — Circuit Half-Open → test 1 request → Exam đã recover → CLOSED
```

---

## VẤN ĐỀ 3: Điểm lưu thành công nhưng thống kê bị mất

### Ngữ cảnh

Sau khi lưu điểm, Submission Service cần thông báo cho Statistics Service. Nhưng 2 hệ thống này dùng 2 database khác nhau, không có shared transaction.

### Vấn đề

```java
// Cách hiện tại (giả sử gọi HTTP đồng bộ):

submissionRepository.save(submission);        // ✓ Lưu điểm thành công

statisticsClient.notify(submission);          // ✗ Statistics đang restart!
// → Exception!
// → Điểm ĐÃ LƯU trong DB (không rollback được @Transactional)
// → Event BỊ MẤT hoàn toàn
// → Dashboard giáo viên thiếu bài, không tự recover được
```

Không có thứ tự nào đúng cả — nếu gửi event trước, lưu DB sau cũng sai (thống kê đếm 200 nhưng DB chỉ có 199).

### Pattern giải quyết: **Outbox + Event-Driven** (TV3 + TV4)

```
┌──────────────────────────────────────────────────────┐
│              1 TRANSACTION DUY NHẤT                  │
│  submissions: score=85, status=SUBMITTED  ✓          │
│  outbox:      event="ExamSubmitted", PENDING  ✓      │
│  ← Cả 2 thành công hoặc cả 2 rollback               │
│  ← Không bao giờ 1 có, 1 không                      │
└──────────────────────────────────────────────────────┘
              │
    Background Job (mỗi 2 giây)
              ▼
       RabbitMQ Queue
              │
    Statistics Consumer
    ← Nếu down → event nằm chờ trong queue
    ← Khi restart → tự xử lý tồn đọng
```

---

## PHÂN CÔNG CHI TIẾT 4 THÀNH VIÊN

---

### TV1 — Saga Orchestration
**Pattern giải quyết:** Vấn đề 1  
**Ranh giới:** Toàn bộ logic điều phối luồng nộp bài bên trong Submission Service  
**Độ khó:** 8/10 | **Quan trọng:** 10/10

#### Danh sách việc cần làm

**1. Cập nhật SubmissionStatus enum**
```java
// File: services/submission-service/src/main/java/com/quizapp/submission/enums/SubmissionStatus.java
// Thêm trạng thái GRADING
public enum SubmissionStatus {
    IN_PROGRESS,  // đang làm bài
    GRADING,      // ← THÊM MỚI: đang trong quá trình nộp + chấm điểm
    SUBMITTED     // đã nộp, có điểm
}
```

**2. Cập nhật Submission entity**
```java
// File: .../entity/Submission.java
// Đảm bảo cột status trong DB nhận giá trị "GRADING"
@Enumerated(EnumType.STRING)
private SubmissionStatus status = SubmissionStatus.IN_PROGRESS;
```

**3. Tạo SagaOrchestrator**
```java
// File MỚI: .../service/SagaOrchestrator.java
@Service
public class SagaOrchestrator {

    // Bước 1: Validate
    // Bước 2: Đổi status = GRADING (compensation: đổi lại IN_PROGRESS)
    // Bước 3: Gọi Exam Service lấy đáp án (nếu fail → gọi compensation Bước 2)
    // Bước 4: Chấm điểm
    // Bước 5: Lưu điểm + ghi outbox event (phối hợp OutboxRepository của TV3)
    //         Đổi status = SUBMITTED
}
```

**4. Refactor SubmissionService.submit()**
```java
// File: .../service/SubmissionService.java
// Xóa logic nộp bài cũ, thay bằng:
public SubmitResponse submit(String submissionId, SaveAnswersRequest lastAnswers) {
    return sagaOrchestrator.execute(submissionId, lastAnswers);
}
```

**5. Cập nhật logic saveAnswers() — kiểm tra GRADING**
```java
// Hiện tại chỉ check IN_PROGRESS, cần thêm:
if (submission.getStatus() == SubmissionStatus.GRADING) {
    throw new IllegalStateException("Bài đang được chấm điểm, vui lòng chờ.");
}
```

**6. Viết unit test**
```java
// File MỚI: .../test/SagaOrchestratorTest.java
// Test case 1: Happy path — nộp bài thành công → status = SUBMITTED
// Test case 2: Exam Service fail ở Bước 3 → status rollback = IN_PROGRESS
// Test case 3: Nộp bài khi status = GRADING → throw exception
// Test case 4: Nộp bài khi đã SUBMITTED → throw exception
```

**Điểm giao với TV3:** Ở Bước 5, gọi `outboxRepository.save(event)` — TV3 sẽ implement interface này. TV1 chỉ cần gọi, không cần biết chi tiết bên trong.

**Deliverable:** Demo tắt Exam Service đang giữa chừng nộp bài → log hiển thị "Saga compensation: rollback status IN_PROGRESS" → sinh viên nộp lại thành công.

---

### TV2 — Resilience Layer: Circuit Breaker + Retry + Timeout
**Pattern giải quyết:** Vấn đề 2  
**Ranh giới:** Toàn bộ lớp HTTP client giữa các service — không để service nào bị block vô hạn  
**Độ khó:** 7/10 | **Quan trọng:** 9/10

#### Danh sách việc cần làm

**1. Thêm dependency Resilience4j**
```xml
<!-- File: services/submission-service/pom.xml -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Làm tương tự cho services/statistics-service/pom.xml -->
```

**2. Cấu hình Circuit Breaker + Retry + Timeout**
```yaml
# File: services/submission-service/src/main/resources/application.properties
# (hoặc application.yml)

# Circuit Breaker cho ExamServiceClient
resilience4j.circuitbreaker.instances.examService.slidingWindowSize=10
resilience4j.circuitbreaker.instances.examService.failureRateThreshold=50
resilience4j.circuitbreaker.instances.examService.waitDurationInOpenState=30s
resilience4j.circuitbreaker.instances.examService.permittedNumberOfCallsInHalfOpenState=3

# Retry cho ExamServiceClient
resilience4j.retry.instances.examService.maxAttempts=3
resilience4j.retry.instances.examService.waitDuration=1s
resilience4j.retry.instances.examService.enableExponentialBackoff=true

# Timeout
resilience4j.timelimiter.instances.examService.timeoutDuration=5s
```

**3. Áp dụng annotation vào ExamServiceClient**
```java
// File: .../client/ExamServiceClient.java

@CircuitBreaker(name = "examService", fallbackMethod = "getAnswersFallback")
@Retry(name = "examService")
@TimeLimiter(name = "examService")
public ExamQuestionsDto getExamQuestionsWithAnswers(Long examId) {
    // HTTP call hiện tại
}

// Fallback method — được gọi khi Circuit OPEN hoặc hết retry
public ExamQuestionsDto getAnswersFallback(Long examId, Exception ex) {
    log.warn("Circuit open for examService, examId={}: {}", examId, ex.getMessage());
    // Ném exception để Saga Orchestrator (TV1) xử lý compensation
    throw new ExamServiceUnavailableException("Exam Service tạm thời không khả dụng");
}

// Áp dụng tương tự cho getExamDetail() và getExamQuestions()
```

**4. Áp dụng Retry + Timeout vào Statistics→Submission**
```java
// File: services/statistics-service/.../client/SubmissionServiceClient.java

@Retry(name = "submissionService")
@TimeLimiter(name = "submissionService")
public List<SubmissionDto> getSubmissions(Long examId, String status) {
    // HTTP call đến Submission Service
}

// Cấu hình trong application.properties của statistics-service
resilience4j.retry.instances.submissionService.maxAttempts=3
resilience4j.retry.instances.submissionService.waitDuration=2s
resilience4j.timelimiter.instances.submissionService.timeoutDuration=10s
```

**5. Thêm Actuator endpoint để monitor Circuit Breaker**
```properties
# application.properties
management.endpoints.web.exposure.include=health,circuitbreakers,retries
management.endpoint.health.show-details=always
```

**6. Viết integration test**
```java
// File MỚI: .../test/CircuitBreakerIntegrationTest.java
// Test 1: Mock Exam Service trả lỗi 5 lần → circuit OPEN → request thứ 6 fail nhanh (< 100ms)
// Test 2: Mock Exam Service timeout → TimeLimiter ngắt sau 5s thay vì 30s
// Test 3: Retry — Exam Service fail 2 lần, lần 3 OK → request vẫn thành công
// Test 4: Statistics → Submission timeout → retry 3 lần → fallback
```

**Điểm giao với TV1:** Khi fallback được gọi, throw `ExamServiceUnavailableException`. TV1 catch exception này trong Saga Orchestrator để kích hoạt compensation.

**Deliverable:** Demo: `docker stop quiz-system-exam-service-1` trong lúc nộp bài → log hiển thị circuit OPEN → auto-save vẫn nhận request bình thường → `docker start quiz-system-exam-service-1` → circuit tự CLOSED lại.

---

### TV3 — Outbox Pattern + RabbitMQ Infrastructure (Phía Publisher)
**Pattern giải quyết:** Vấn đề 3 (phần ghi event)  
**Ranh giới:** Từ lúc lưu điểm đến khi message vào được RabbitMQ queue — không quan tâm ai consume  
**Độ khó:** 8/10 | **Quan trọng:** 8/10

#### Danh sách việc cần làm

**1. Thêm RabbitMQ vào Docker Compose**
```yaml
# File: docker-compose.yml — thêm service mới
rabbitmq:
  image: rabbitmq:3-management
  ports:
    - "5672:5672"      # AMQP protocol
    - "15672:15672"    # Management UI (http://localhost:15672)
  environment:
    RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-admin}
    RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASS:-changeme}
  healthcheck:
    test: ["CMD", "rabbitmq-diagnostics", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5
  networks:
    - app-network

# Cập nhật submission-service: thêm depends_on rabbitmq
# Cập nhật statistics-service: thêm depends_on rabbitmq
```

**2. Thêm dependency RabbitMQ vào Submission Service**
```xml
<!-- File: services/submission-service/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**3. Tạo bảng outbox và OutboxEvent entity**
```java
// File MỚI: .../entity/OutboxEvent.java
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private String id;              // UUID
    private String eventType;       // "ExamSubmitted"
    private String aggregateId;     // submissionId
    @Column(columnDefinition = "TEXT")
    private String payload;         // JSON của submission result
    private String status;          // "PENDING" | "SENT" | "FAILED"
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private int retryCount;
}

// File MỚI: .../repository/OutboxEventRepository.java
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}
```

**4. Cấu hình RabbitMQ Exchange và Queue**
```java
// File MỚI: .../config/RabbitMQConfig.java
@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE = "quiz.events";
    public static final String QUEUE_EXAM_SUBMITTED = "statistics.exam-submitted";
    public static final String ROUTING_KEY = "exam.submitted";

    @Bean
    public TopicExchange quizEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue examSubmittedQueue() {
        return QueueBuilder.durable(QUEUE_EXAM_SUBMITTED).build();
    }

    @Bean
    public Binding binding(Queue examSubmittedQueue, TopicExchange quizEventsExchange) {
        return BindingBuilder.bind(examSubmittedQueue).to(quizEventsExchange).with(ROUTING_KEY);
    }
}
```

**5. Implement Outbox trong SagaOrchestrator (Bước 5)**
```java
// Phối hợp với TV1 — TV3 cung cấp method này, TV1 gọi trong Bước 5

// File: .../service/OutboxService.java (MỚI)
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    // Được gọi trong cùng @Transactional của SagaOrchestrator
    public void saveEvent(String eventType, String aggregateId, Object payload) {
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID().toString())
            .eventType(eventType)
            .aggregateId(aggregateId)
            .payload(objectMapper.writeValueAsString(payload))
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .retryCount(0)
            .build();
        outboxEventRepository.save(event);
        // Lưu vào CÙNG transaction với submission → đảm bảo nhất quán
    }
}
```

**6. Viết OutboxRelayJob — Background Publisher**
```java
// File MỚI: .../job/OutboxRelayJob.java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayJob {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 2000) // chạy mỗi 2 giây
    @Transactional
    public void relay() {
        List<OutboxEvent> pendingEvents =
            outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        for (OutboxEvent event : pendingEvents) {
            try {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ROUTING_KEY,
                    event.getPayload()
                );
                event.setStatus("SENT");
                event.setSentAt(LocalDateTime.now());
                log.info("Published event {} to RabbitMQ", event.getId());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= 5) {
                    event.setStatus("FAILED");
                    log.error("Event {} failed after 5 retries", event.getId());
                }
                log.warn("Failed to publish event {}: {}", event.getId(), e.getMessage());
            }
            outboxEventRepository.save(event);
        }
    }
}
```

**7. Viết test**
```java
// File MỚI: .../test/OutboxRelayJobTest.java
// Test 1: Nộp bài → row xuất hiện trong bảng outbox_events với status=PENDING
// Test 2: OutboxRelayJob chạy → row đổi status=SENT → message xuất hiện trong RabbitMQ
// Test 3: RabbitMQ down → row vẫn status=PENDING, retryCount tăng dần → không mất data
// Test 4: RabbitMQ restore → job tự publish tất cả PENDING events tồn đọng
```

**Điểm giao với TV4:** TV3 định nghĩa tên queue `statistics.exam-submitted` và format JSON của payload. TV4 viết consumer lắng nghe đúng queue này với đúng format đó.

**Deliverable:** `docker stop quiz-system-rabbitmq-1` → nộp bài → vào `outbox_events` table thấy rows PENDING. `docker start quiz-system-rabbitmq-1` → rows tự đổi thành SENT → RabbitMQ Management UI (`http://localhost:15672`) thấy messages.

---

### TV4 — Event Consumer (Statistics) + API Gateway nâng cao + Demo
**Pattern giải quyết:** Vấn đề 3 (phần đọc event) + hỗ trợ tổng thể  
**Ranh giới:** Xử lý event trong Statistics Service + lớp Gateway + kết nối toàn hệ thống  
**Độ khó:** 7/10 | **Quan trọng:** 8/10

#### Danh sách việc cần làm

**1. Thêm RabbitMQ dependency vào Statistics Service**
```xml
<!-- File: services/statistics-service/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**2. Cấu hình RabbitMQ trong Statistics Service**
```java
// File MỚI: services/statistics-service/.../config/RabbitMQConfig.java
// Dùng cùng tên queue, exchange, routing key với TV3
// Chỉ cần khai báo lại để Spring tạo bean
public static final String QUEUE = "statistics.exam-submitted";
```

**3. Viết ExamEventConsumer**
```java
// File MỚI: services/statistics-service/.../consumer/ExamEventConsumer.java
@Component
@RequiredArgsConstructor
@Slf4j
public class ExamEventConsumer {

    private final ProcessedEventRepository processedEventRepository; // để idempotency
    private final StatisticsUpdateService statisticsUpdateService;

    @RabbitListener(queues = "statistics.exam-submitted")
    public void handleExamSubmitted(String messageJson) {
        ExamSubmittedEvent event = parseEvent(messageJson);

        // IDEMPOTENCY CHECK — tránh xử lý cùng 1 event 2 lần
        if (processedEventRepository.existsByEventId(event.getSubmissionId())) {
            log.warn("Event {} already processed, skipping", event.getSubmissionId());
            return;
        }

        // Cập nhật thống kê
        statisticsUpdateService.updateStats(event);

        // Đánh dấu đã xử lý
        processedEventRepository.save(new ProcessedEvent(event.getSubmissionId()));
        log.info("Processed ExamSubmitted event for submission {}", event.getSubmissionId());
    }
}
```

**4. Viết StatisticsUpdateService**
```java
// File MỚI: .../service/StatisticsUpdateService.java
// Nhận event → cập nhật các chỉ số được cache/tính sẵn
// (không cần lưu DB riêng vì Statistics Service vẫn có thể gọi API của Submission)
// Đơn giản nhất: invalidate cache để lần query tiếp sẽ tính lại từ Submission Service
@Service
public class StatisticsUpdateService {
    public void updateStats(ExamSubmittedEvent event) {
        log.info("Received new submission for exam {}, score={}",
            event.getExamId(), event.getScore());
        // Có thể trigger cache invalidation hoặc cập nhật counter
    }
}
```

**5. Nâng cấp Nginx — Rate Limiting**
```nginx
# File: gateway/src/nginx.conf — thêm vào

# Định nghĩa zone rate limit: 10 request/giây mỗi IP
limit_req_zone $binary_remote_addr zone=submit_limit:10m rate=10r/s;

# Áp dụng cho endpoint nộp bài
location ~ ^/api/submissions/[^/]+/submit$ {
    limit_req zone=submit_limit burst=20 nodelay;
    limit_req_status 429;

    proxy_pass http://submission-service:8080/...;
}
```

**6. Nâng cấp Nginx — Timeout**
```nginx
# File: gateway/src/nginx.conf — thêm vào http block hoặc location block

proxy_connect_timeout  5s;   # timeout kết nối đến backend
proxy_read_timeout     15s;  # timeout chờ backend phản hồi
proxy_send_timeout     10s;  # timeout gửi request đến backend

# Khi timeout → Nginx trả 504 Gateway Timeout thay vì treo vô hạn
```

**7. Viết load test script**
```javascript
// File MỚI: scripts/load-test.js (dùng k6)
import http from 'k6/http';
import { sleep } from 'k6';

export let options = {
    vus: 50,        // 50 virtual users
    duration: '30s',
};

export default function () {
    // Mỗi VU nộp bài 1 lần
    let res = http.post(`http://localhost:8080/api/submissions/${__VU}/submit`, '{}', {
        headers: { 'Content-Type': 'application/json' },
    });

    console.log(`VU ${__VU}: status=${res.status}, time=${res.timings.duration}ms`);
    sleep(1);
}
// Chạy: k6 run scripts/load-test.js
```

**8. Chuẩn bị kịch bản demo**
```markdown
# File MỚI: DEMO.md

## Kịch bản demo buổi thuyết trình

### Demo 1: Saga Orchestration
1. Chạy hệ thống bình thường
2. Học sinh bắt đầu làm bài
3. docker stop quiz-system-exam-service-1
4. Học sinh bấm nộp bài
5. Hệ thống báo lỗi "Thử lại sau 5 giây", status vẫn là IN_PROGRESS
6. docker start quiz-system-exam-service-1
7. Học sinh nộp lại → thành công ✓

### Demo 2: Circuit Breaker
1. docker stop quiz-system-exam-service-1
2. Nộp bài liên tục → 5 request fail → xem log circuit OPEN
3. Auto-save vẫn hoạt động (test PUT /answers)
4. docker start → circuit tự CLOSED
5. GET http://localhost:5002/actuator/circuitbreakers → xem state

### Demo 3: Outbox + Event-Driven
1. docker stop quiz-system-rabbitmq-1
2. Nộp bài → SELECT * FROM outbox_events → thấy PENDING
3. docker start quiz-system-rabbitmq-1
4. Chờ 2 giây → outbox_events đổi SENT
5. Mở http://localhost:15672 → thấy messages trong queue

### Demo 4: Rate Limiting
1. Chạy: k6 run scripts/load-test.js
2. Quan sát: request > 10/s trả 429 Too Many Requests
3. Log Nginx: "limiting requests"
```

**Điểm giao với TV3:** Dùng đúng tên queue `statistics.exam-submitted` và đúng format JSON payload mà TV3 đã định nghĩa.

**Deliverable:** Demo đầy đủ 4 kịch bản liên tiếp trong buổi thuyết trình. Load test cho thấy hệ thống xử lý được 50 concurrent users mà không crash.

---

## Tóm Tắt Phân Công

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    PHÂN CÔNG 4 THÀNH VIÊN                               │
├──────────────┬───────────────────────┬────────────┬───────────┬─────────┤
│  Thành viên  │ Pattern               │ Quan trọng │ Độ khó   │ Service │
├──────────────┼───────────────────────┼────────────┼───────────┼─────────┤
│ TV1          │ Saga Orchestration    │   10/10    │  8/10    │ Submission│
│              │ (state machine +      │            │          │ Service  │
│              │  compensation logic)  │            │          │          │
├──────────────┼───────────────────────┼────────────┼───────────┼─────────┤
│ TV2          │ Circuit Breaker       │    9/10    │  7/10    │ Submission│
│              │ + Retry + Timeout     │            │          │ +Statistics│
│              │ (toàn bộ HTTP client) │            │          │ Service  │
├──────────────┼───────────────────────┼────────────┼───────────┼─────────┤
│ TV3          │ Outbox Pattern        │    8/10    │  8/10    │ Submission│
│              │ + RabbitMQ Setup      │            │          │ Service  │
│              │ (publisher side)      │            │          │          │
├──────────────┼───────────────────────┼────────────┼───────────┼─────────┤
│ TV4          │ Event Consumer        │    8/10    │  7/10    │ Statistics│
│              │ + API Gateway++       │            │          │ Service  │
│              │ + Demo toàn hệ thống  │            │          │ + Gateway│
└──────────────┴───────────────────────┴────────────┴───────────┴─────────┘
```

### Các điểm giao cần phối hợp

| # | TV A | TV B | Nội dung phối hợp |
|---|------|------|-------------------|
| 1 | **TV1** | **TV3** | TV3 cung cấp `OutboxService.saveEvent()` interface. TV1 gọi trong Bước 5 của Saga. Phải thống nhất signature và @Transactional scope |
| 2 | **TV2** | **TV1** | Khi Circuit Breaker kích hoạt fallback, throw `ExamServiceUnavailableException`. TV1 catch exception này để trigger Saga compensation |
| 3 | **TV3** | **TV4** | Phải thống nhất: tên queue (`statistics.exam-submitted`), tên exchange (`quiz.events`), và format JSON của payload |
