# Walkthrough — Outbox Pattern + Event-Driven (Pattern 3) cho Luồng Nộp Bài Thi

Tài liệu này mô tả phần đã implement cho **Vấn đề 3** trong `PROJECT_OVERVIEW.md` /
`CORE_PROBLEMS_AND_PATTERNS.md`: *"Điểm lưu xong nhưng thống kê bị mất"*.
Khớp với các điểm giao (contract) mà TV1 (Saga) đã để sẵn trong `GradeAndSaveStep.java`.

---

## 1. Nguyên lý

```
┌────────────────────────────────────────────┐
│         1 TRANSACTION DUY NHẤT             │
│  submissions: score=85, status=SUBMITTED   │
│  outbox_events: event="ExamSubmitted", PENDING │
│  ← Cả 2 thành công hoặc cả 2 rollback     │
└────────────────────────────────────────────┘
                    │
       OutboxRelayJob (mỗi 2 giây, chạy nền)
                    ▼
           RabbitMQ (exchange: quiz.events)
                    │
       Statistics Service — ExamEventConsumer
         ← Nếu Statistics đang down → message nằm chờ trong queue (durable)
         ← Khi Statistics restart → tự động nhận và xử lý hết tồn đọng
```

Việc "lưu điểm" và "ghi event" được gộp vào cùng một `@Transactional` (chính là
transaction đã có sẵn ở `GradeAndSaveStep.execute()`), nên không bao giờ xảy ra
tình huống lưu điểm thành công mà event bị mất — khác với gọi HTTP trực tiếp
sang Statistics Service (không có transaction chung giữa 2 service).

Việc *publish* sang RabbitMQ cố tình tách khỏi transaction đó (chạy ở
`OutboxRelayJob`, nền, độc lập) — vì gọi network bên trong transaction DB là
anti-pattern: nếu RabbitMQ chậm, transaction DB bị giữ mở lâu; nếu RabbitMQ
down, cả điểm số cũng bị rollback theo — đúng thứ mà Outbox Pattern sinh ra để
tránh.

---

## 2. File Đã Tạo Mới / Sửa

### Submission Service (publisher)
```
submission-service/src/main/java/com/quizapp/submission/
├── entity/OutboxEvent.java                 ← [NEW] Entity bảng outbox_events
├── repository/OutboxEventRepository.java   ← [NEW] findByStatusOrderByCreatedAtAsc
├── dto/event/ExamSubmittedEvent.java       ← [NEW] Payload event — contract với Statistics
├── config/RabbitMQConfig.java              ← [NEW] Khai báo exchange/queue/binding
├── service/OutboxService.java              ← [NEW] saveEvent() — dùng chung transaction với caller
├── job/OutboxRelayJob.java                 ← [NEW] @Scheduled — publish PENDING events mỗi 2s
├── saga/step/GradeAndSaveStep.java         ← [MODIFIED] Gọi outboxService.saveEvent() tại vị trí TODO của TV1
└── SubmissionServiceApplication.java       ← [MODIFIED] Thêm @EnableScheduling

submission-service/src/test/java/com/quizapp/submission/
├── service/OutboxServiceTest.java          ← [NEW]
└── job/OutboxRelayJobTest.java             ← [NEW]

submission-service/pom.xml                  ← [MODIFIED] + spring-boot-starter-amqp
submission-service/src/main/resources/application.properties ← [MODIFIED] + config RabbitMQ, outbox.relay
```

### Statistics Service (consumer)
```
statistics-service/src/main/java/com/quizapp/statistics/
├── config/RabbitMQConfig.java              ← [NEW] Khai báo lại đúng exchange/queue/binding
├── dto/event/ExamSubmittedEvent.java       ← [NEW] Mirror payload
├── consumer/ProcessedEventTracker.java     ← [NEW] Idempotency (in-memory, service stateless)
├── consumer/ExamEventConsumer.java         ← [NEW] @RabbitListener
└── service/StatisticsUpdateService.java    ← [NEW] Xử lý nghiệp vụ khi nhận event

statistics-service/pom.xml                             ← [MODIFIED] + spring-boot-starter-amqp
statistics-service/src/main/resources/application.properties ← [MODIFIED] + config RabbitMQ
```

### Hạ tầng chung
```
docker-compose.yml   ← [MODIFIED] + service rabbitmq (3-management), depends_on cho 2 service
.env.example          ← [MODIFIED] + RABBITMQ_USER / RABBITMQ_PASS / port
```

---

## 3. Điểm Giao Với TV1 (Saga)

TV1 đã để sẵn transaction boundary và vị trí insert trong `GradeAndSaveStep.java`
(mục 3.7 của `TV1_walkthrough.md`). Việc tích hợp chỉ cần:

1. Inject thêm `OutboxService` vào constructor của `GradeAndSaveStep` (Lombok
   `@RequiredArgsConstructor` tự sinh, không phải sửa gì khác).
2. Build `ExamSubmittedEvent` từ chính `submission` entity đã có sẵn trong
   `context` (không cần query DB thêm).
3. Gọi `outboxService.saveEvent(...)` — nằm **bên trong** `execute()` vốn đã
   `@Transactional`, nên tự động ăn theo transaction đó.

Không đổi signature của `GradeAndSaveStep`, không đổi luồng của
`SubmissionSagaOrchestrator` → **không xung đột** với phần Saga đã merge.

## 4. Điểm Giao Với TV2 (Circuit Breaker) — chưa triển khai trong repo

Tại thời điểm implement Pattern 3, Circuit Breaker (TV2) chưa có trong code
(chưa thấy dependency Resilience4j / annotation trong `ExamServiceClient`).
Pattern 3 hoàn toàn độc lập với Pattern 2 — Outbox chỉ liên quan tới bước lưu
điểm + báo Statistics, không đụng tới `ExamServiceClient`/`FetchAnswersStep` mà
TV2 sẽ sửa. Khi TV2 triển khai xong, không cần sửa gì ở đây.

## 5. Điểm Giao Với TV4 (Consumer + Gateway)

Contract dùng chung, **không được đổi** nếu không báo trước cho cả 2 phía:

| Hạng mục | Giá trị |
|---|---|
| Exchange | `quiz.events` (TopicExchange, durable) |
| Queue | `statistics.exam-submitted` (durable) |
| Routing key | `exam.submitted` |
| Payload JSON | `{submissionId, examId, studentId, score, correctCount, totalQuestions, submittedAt}` |

Repo hiện đã có sẵn 1 bản consumer tối thiểu (`ExamEventConsumer` +
`StatisticsUpdateService`, chỉ log lại việc nhận bài nộp mới) để pattern chạy
được end-to-end ngay. Nếu TV4 cần mở rộng nghiệp vụ (cache invalidation, đếm
real-time...), sửa trong `StatisticsUpdateService.handleExamSubmitted()`,
không cần đụng tới `ExamEventConsumer` hay `RabbitMQConfig`.

---

## 6. Cách Test / Demo

### Migration DB
`spring.jpa.hibernate.ddl-auto=update` sẽ tự tạo bảng `outbox_events` khi
service khởi động lần đầu — không cần chạy tay như bảng `submissions` (TV1),
vì đây là bảng hoàn toàn mới, không phải ALTER cột ENUM có sẵn.

### Kịch bản 1 — Happy path
```bash
docker compose up --build
# Nộp 1 bài thi bất kỳ qua frontend/API
# Kiểm tra bảng outbox_events:
docker exec quiz-system-submission-db-1 mysql -u root -pchangeme \
  -e "USE submission_db; SELECT id, event_type, status, retry_count FROM outbox_events;"
# → status SENT sau tối đa 2 giây
# Xem log statistics-service:
docker compose logs statistics-service --tail=50
# → "Statistics: nhận bài nộp mới — submissionId=..., score=..."
```

### Kịch bản 2 — RabbitMQ down khi nộp bài (event không mất)
```bash
docker stop quiz-system-rabbitmq-1
# Nộp bài → outbox_events có row mới, status=PENDING (event vẫn được ghi
# bình thường vì OutboxService chỉ ghi DB, không gọi RabbitMQ)
docker start quiz-system-rabbitmq-1
# Đợi vài giây → status tự đổi SENT, không cần thao tác gì thêm
```

### Kịch bản 3 — Statistics Service down khi event được publish
```bash
docker stop quiz-system-statistics-service-1
# Nộp bài → OutboxRelayJob vẫn publish thành công sang RabbitMQ (status=SENT)
# Message nằm chờ trong queue "statistics.exam-submitted" (durable)
docker start quiz-system-statistics-service-1
# → Statistics tự nhận và xử lý hết message tồn đọng khi khởi động lại
```

### Kiểm tra qua RabbitMQ Management UI
`http://localhost:15672` (user/pass mặc định: `admin` / `changeme`) → tab
Queues → `statistics.exam-submitted` để xem message rate, số message
đang chờ (Ready), số consumer đang lắng nghe.

### Unit test
```bash
cd services/submission-service
./mvnw test -Dtest=OutboxServiceTest,OutboxRelayJobTest
```
