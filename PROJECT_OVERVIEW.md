# Tổng Quan Dự Án — Hệ Thống Thi Trắc Nghiệm Online

> **Môn học**: Phát Triển Phần Mềm Hướng Dịch Vụ (PTTK)  
> **Trường**: Học viện Công nghệ Bưu chính Viễn thông — PTIT

---

## 1. Hệ Thống Hiện Tại Đang Làm Được Gì

Dự án đã xây dựng hoàn chỉnh một **hệ thống thi trắc nghiệm online** theo kiến trúc **Microservices**, gồm 3 backend service độc lập, 1 API Gateway và 1 Frontend, toàn bộ chạy bằng Docker Compose.

### Những gì đã hoàn thành

#### Phía Giáo viên

- Tạo, sửa, xóa **câu hỏi trắc nghiệm** (4 lựa chọn A/B/C/D, có đáp án đúng)
- Tạo **đề thi** từ ngân hàng câu hỏi, cấu hình thời gian làm bài
- **Công bố / Đóng** đề thi (chuyển trạng thái `DRAFT → PUBLISHED → CLOSED`)
- Xem **dashboard thống kê**: điểm trung bình, phân bố điểm, tỷ lệ đúng/sai từng câu, bảng xếp hạng học sinh

#### Phía Học sinh

- Xem danh sách đề thi đang mở (`PUBLISHED`)
- Bắt đầu làm bài với **đồng hồ đếm ngược** theo thời gian quy định
- **Auto-save** đáp án sau mỗi câu (không mất bài khi mất kết nối)
- **Nộp bài** — hệ thống chấm điểm tự động ngay lập tức
- Xem **điểm và đáp án chi tiết** sau khi nộp

#### Kiến trúc kỹ thuật

```
Browser → Frontend (React/Vite :3000)
              ↓
        API Gateway (Nginx :8080)
         ↙        ↓        ↘
 Exam Svc    Submission   Statistics
  (:5001)     Svc(:5002)   Svc(:5003)
     ↓              ↓
  exam_db    submission_db
  (MySQL)      (MySQL)
```

| Thành phần         | Tech                                | Vai trò                        |
| ------------------ | ----------------------------------- | ------------------------------ |
| Frontend           | React + Vite                        | Giao diện giáo viên & học sinh |
| API Gateway        | Nginx                               | Routing, CORS                  |
| Exam Service       | Java 17 + Spring Boot 3 + MySQL     | CRUD câu hỏi, đề thi           |
| Submission Service | Java 17 + Spring Boot 3 + MySQL     | Phiên làm bài, chấm điểm       |
| Statistics Service | Java 17 + Spring Boot 3 (stateless) | Tổng hợp thống kê, bảng điểm   |

#### Trạng thái hiện tại của Submission (2 trạng thái)

| Trạng thái    | Ý nghĩa                                                                 |
| ------------- | ----------------------------------------------------------------------- |
| `IN_PROGRESS` | Học sinh đang làm bài, có thể lưu đáp án, có thể resume nếu mất kết nối |
| `SUBMITTED`   | Đã nộp và chấm điểm xong, không thể sửa hay nộp lại                     |

Quá trình nộp bài hiện tại: `POST /submit` → **gọi đồng bộ** Exam Service lấy đáp án → chấm điểm → lưu kết quả → trả điểm — **tất cả trong 1 HTTP request, không có cơ chế xử lý lỗi giữa các service**.

---

## 2. Yêu Cầu Của Giảng Viên

> _Tổng hợp từ phản hồi trực tiếp của thầy trong buổi hướng dẫn._

### Yêu cầu về kiến trúc

Thầy yêu cầu dự án phải **thực sự áp dụng các pattern của Microservices**, không chỉ đơn thuần là chia code thành nhiều service rồi gọi nhau qua HTTP.

> _"Phải áp dụng các pattern vào, chứ không phải chỉ tách service ra."_

### Yêu cầu về cách chia việc

Thay vì mỗi người làm một use case riêng biệt, thầy yêu cầu:

> _"Chọn 1 bài toán cụ thể, rồi xem trong flow của bài toán đó có thể áp dụng được những pattern nào — giống như bài toán đặt vé, một flow nhưng áp dụng được nhiều pattern. Sau đó chia việc ra theo pattern."_

### Ý nghĩa thực tế

Thầy muốn nhóm hiểu và minh chứng được:

- **Tại sao** phải dùng pattern (vấn đề cụ thể gặp phải)
- **Pattern đó giải quyết vấn đề như thế nào** trong ngữ cảnh thực tế
- Mỗi thành viên **chịu trách nhiệm implement** một pattern cụ thể, không phải implement toàn bộ một use case

---

## 3. Bài Toán Đặt Ra: "Học Sinh Nộp Bài Thi"

### Tại sao chọn bài toán này?

Đây là luồng phức tạp và quan trọng nhất trong hệ thống — tương tự bài toán **đặt vé** mà thầy ví dụ:

| Bài toán đặt vé                          | Bài toán nộp bài thi                            |
| ---------------------------------------- | ----------------------------------------------- |
| Nhiều người cùng đặt vé một chuyến       | Nhiều học sinh cùng nộp bài một đề              |
| Gọi nhiều service: payment, seat, ticket | Gọi nhiều service: submission, exam, statistics |
| Không được mất đơn hàng                  | Không được mất bài nộp / điểm số                |
| Cần rollback nếu thanh toán thất bại     | Cần rollback nếu lấy đáp án thất bại            |

### Ngữ cảnh thực tế

Kỳ thi giữa kỳ môn Mạng máy tính với **200 sinh viên** cùng thi lúc 8h sáng:

```
8:55 — 5 phút cuối, 200 sinh viên cùng bấm "Nộp bài"
       Submission Service nhận 200 request đồng thời
       Mỗi request phải gọi sang Exam Service để lấy đáp án đúng
       Exam Service bắt đầu bị quá tải → bắt đầu chậm / không phản hồi
```

### 3 Vấn Đề Cốt Lõi Được Xác Định

---

#### Vấn đề 1 — Chuỗi gọi dịch vụ bị đứt giữa chừng → bài thi bị "treo"

**Luồng nộp bài đi qua nhiều bước:**

```
Bước 1: Validate (còn IN_PROGRESS? chưa nộp?)
Bước 2: Gọi Exam Service lấy đáp án đúng    ← có thể fail!
Bước 3: Chấm điểm
Bước 4: Lưu điểm, đổi status = SUBMITTED
```

**Vấn đề:** Khi **Bước 2 fail** (Exam Service đang down), không có cơ chế phục hồi:

```java
// Code hiện tại trong SubmissionService.java
try {
    examWithAnswers = examServiceClient.getExamQuestionsWithAnswers(submission.getExamId());
} catch (Exception e) {
    throw new RuntimeException("Không thể chấm điểm lúc này...");
    // ← Chỉ throw exception, không rollback trạng thái
    // ← Nếu đã có bước đổi status trung gian → bài bị TREO mãi mãi
}
```

**Hậu quả:** Sinh viên thấy lỗi, không có điểm, không nộp lại được → admin phải sửa database bằng tay.

---

#### Vấn đề 2 — Exam Service chậm kéo chết Submission Service

**Cascading Failure:**

```
8:55:00 — 200 sinh viên nộp bài → 200 request vào Submission Service
           Mỗi request gọi Exam Service → Exam chậm → chờ 30s (timeout mặc định)
           200 × 30s = mỗi request chiếm 1 thread trong 30 giây

8:55:30 — Thread pool của Submission Service HẾT → Submission ĐỨNG
           Auto-save không hoạt động → 200 sinh viên mất bài chưa save
           Exam Service chỉ bị chậm, nhưng Submission lại "chết" hẳn
```

---

#### Vấn đề 3 — Điểm lưu xong nhưng thống kê bị mất

**Sau khi chấm điểm, cần làm 2 việc nhưng không đảm bảo tính nhất quán:**

```
Lưu điểm vào DB:       ✓ thành công
Báo Statistics Service: ✗ Statistics đang restart → mất sự kiện hoàn toàn
                           Không có cách nào recover
→ Dashboard giáo viên thiếu bài, số liệu sai
```

---

## 4. Cách Giải Quyết — 4 Microservices Patterns

### Pattern 1: Saga (Orchestration) — Giải quyết Vấn đề 1

**Nguyên lý:** Chia luồng nộp bài thành các bước rõ ràng. Mỗi bước có một **bước đền bù (compensation)** để tự rollback khi fail.

**Thêm trạng thái `GRADING`** để Saga hoạt động:

```
IN_PROGRESS ──► GRADING ──► SUBMITTED
                  │
                  │ (nếu Exam Service fail)
                  ▼
              IN_PROGRESS  ← compensation: rollback, sinh viên nộp lại được
```

**Luồng Saga:**

```
Bước 1: Validate submission          | Compensation: —
Bước 2: Đổi status = GRADING        | Compensation: đổi lại = IN_PROGRESS
Bước 3: Gọi Exam Service lấy đáp án | Compensation: kích hoạt Bước 2
         └─ Fail → tự rollback về IN_PROGRESS → sinh viên thấy "Thử lại sau 5s"
Bước 4: Chấm điểm                   | Compensation: —
Bước 5: Lưu kết quả + Outbox event  | —
```

**Kết quả:** Bài thi không bao giờ bị "treo" — luôn ở trạng thái có thể xử lý được.

---

### Pattern 2: Circuit Breaker — Giải quyết Vấn đề 2

**Nguyên lý:** Khi phát hiện Exam Service lỗi liên tiếp, tự động "ngắt mạch" — trả lỗi ngay (< 1ms) thay vì chờ timeout 30 giây.

**3 trạng thái:**

```
CLOSED ──(5 lỗi liên tiếp)──► OPEN ──(30s)──► HALF-OPEN ──(test OK)──► CLOSED
(bình thường)                 (ngắt mạch)      (thử lại)              (về bình thường)
```

**Timeline:**

```
8:55:05 — 5 request fail → Circuit OPEN
8:55:06 — Request tiếp theo → trả lỗi NGAY (< 1ms), không gọi Exam Service
           Submission Service KHÔNG bị block → vẫn SỐNG
           Auto-save vẫn hoạt động → sinh viên không mất bài
8:56:00 — Circuit HALF-OPEN → test 1 request → Exam Service đã recover
           Circuit CLOSED → hoạt động bình thường trở lại
```

**Thư viện:** Resilience4j (Spring Boot integration).

---

### Pattern 3: Outbox Pattern + Event-Driven — Giải quyết Vấn đề 3

**Nguyên lý:** Lưu điểm và ghi event vào **cùng 1 database transaction**. Background job riêng lo việc chuyển event sang Message Queue (RabbitMQ).

```
┌────────────────────────────────────────────┐
│         1 TRANSACTION DUY NHẤT             │
│  submissions: score=85, status=SUBMITTED   │
│  outbox:      event="ExamSubmitted", PENDING│
│  ← Cả 2 thành công hoặc cả 2 rollback     │
└────────────────────────────────────────────┘
                    │
       Background Job (mỗi 1-2 giây)
                    ▼
           RabbitMQ Message Queue
                    │
       Statistics Service (Consumer)
         ← Nếu Statistics đang down → event nằm chờ trong queue
         ← Khi Statistics restart → tự xử lý hết tồn đọng
```

**Thêm lợi ích:** Sau này thêm Notification Service (gửi email kết quả) chỉ cần subscribe thêm vào queue — **không cần sửa Submission Service**.

---

### Pattern 4: API Gateway nâng cao — Hỗ trợ tổng thể

Nâng cấp Nginx từ routing đơn giản thành lớp kiểm soát thực sự:

| Tính năng                                  | Mục đích                                                        |
| ------------------------------------------ | --------------------------------------------------------------- |
| **Rate Limiting** — 10 req/s cho `/submit` | Ngăn 200 sinh viên bấm nộp liên tục, giảm tải cho backend       |
| **Timeout toàn cục** — 10s                 | Không để request treo vô hạn, tránh thread pool exhaustion      |
| **Retry với Exponential Backoff**          | Tự retry khi network glitch tạm thời, trong suốt với người dùng |

---

### Phân Công 4 Thành Viên Theo Pattern

| Thành viên | Pattern                   | Công việc                                                                                     | Demo                                                                      |
| ---------- | ------------------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| **TV1**    | **Saga**                  | Thêm trạng thái `GRADING`. Refactor `submit()` thành Saga Orchestrator với compensation logic | Tắt Exam Service giữa chừng → bài tự rollback, sinh viên nộp lại được     |
| **TV2**    | **Circuit Breaker**       | Tích hợp Resilience4j vào `ExamServiceClient`. Cấu hình ngưỡng, fallback                      | Giả lập Exam Service chậm → Submission vẫn sống, auto-save vẫn hoạt động  |
| **TV3**    | **Outbox + Event-Driven** | Tạo bảng `outbox`. Viết background job publish RabbitMQ. Viết Statistics consumer             | Tắt Statistics → nộp bài → khởi động lại Statistics → thống kê tự đồng bộ |
| **TV4**    | **API Gateway nâng cao**  | Cấu hình rate limit, timeout, retry trong Nginx. Test end-to-end                              | Kịch bản 200 sinh viên nộp bài đồng thời                                  |

---

## 5. Chi Tiết Công Việc Từng Thành Viên

> Nguyên tắc chung: mỗi TV làm việc trên 1 bước của luồng `submit()`. **Input/Output** dưới đây chính là hợp đồng (contract) giữa các bước — cần thống nhất trước khi code song song để không bị khóa chờ nhau.

### TV1 — Saga (Orchestration)

**Ngữ cảnh:** Luồng nộp bài hiện tại là 1 khối code tuần tự, không có điểm dừng an toàn. Khi bước gọi Exam Service fail giữa chừng, hệ thống không biết "đang ở đâu" để quay lại — bài thi bị treo vĩnh viễn, admin phải sửa DB tay.

**Bài toán nhỏ phải giải quyết:** Biến luồng `submit()` từ 1 hàm nguyên khối thành chuỗi bước có trạng thái trung gian rõ ràng, mỗi bước biết cách tự rollback nếu bước sau nó thất bại.

**Cách Saga giải quyết:**

- Thêm trạng thái `GRADING` làm "điểm neo" giữa `IN_PROGRESS` và `SUBMITTED`.
- Tách `submit()` thành các step độc lập, mỗi step có 1 hàm `execute()` và 1 hàm `compensate()`.
- Khi step "gọi Exam Service" fail → Orchestrator tự gọi `compensate()` của step trước đó (đổi `GRADING` → `IN_PROGRESS`) → sinh viên nộp lại được, không mất trạng thái.

**Input cần có trước khi bắt đầu:**

| Từ đâu      | Cái gì                                          | Bắt buộc?  |
| ----------- | ----------------------------------------------- | ---------- |
| DB hiện tại | Bảng `submissions` với cột `status` (ENUM)      | Có sẵn     |
| Tự thiết kế | Thêm giá trị `GRADING` vào ENUM `status`        | TV1 tự làm |
| Tự thiết kế | Danh sách các bước trong Saga (đặt tên rõ ràng) | TV1 tự làm |

**Output — TV1 phải cung cấp cho các TV khác:**

| Cung cấp cho | Nội dung                                                                                                                                                                    | Định dạng                                 |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| TV2          | 1 method interface duy nhất mà Saga sẽ gọi để lấy đáp án, ví dụ `examServiceClient.getExamQuestionsWithAnswers(examId)` — TV2 sẽ bọc Circuit Breaker quanh chính method này | Interface Java (method signature cố định) |
| TV2          | Điểm "hook" trong Saga để biết khi nào cần trigger compensation nếu Circuit OPEN (catch `CallNotPermittedException` từ Resilience4j)                                        | Đoạn code catch exception trong step 3    |
| TV3          | Sau khi step 5 (lưu kết quả) chạy xong, đảm bảo nó nằm cùng 1 transaction với việc ghi vào bảng `outbox` mà TV3 tạo                                                         | Transaction boundary (`@Transactional`)   |
| TV4          | Trạng thái HTTP response khi Saga rollback (ví dụ 503 kèm message "Thử lại sau 5s") để TV4 test retry/timeout ở Gateway đúng case này                                       | HTTP status code + response body mẫu      |

**Việc cụ thể cần làm:**

1. Migration thêm `GRADING` vào enum status.
2. Tạo class `SubmissionSagaOrchestrator` với các step: Validate → MarkGrading → FetchAnswers → Grade → SaveResult.
3. Mỗi step implement interface `SagaStep { execute(); compensate(); }`.
4. Test: tắt Exam Service giữa chừng → xác nhận status quay về `IN_PROGRESS`, sinh viên gọi lại `/submit` thành công.

---

### TV2 — Circuit Breaker

**Ngữ cảnh:** Khi Exam Service chậm (không phải down hẳn), mỗi request tới nó chiếm 1 thread trong Submission Service suốt thời gian timeout (mặc định 30s). 200 request đồng thời × 30s = thread pool cạn kiệt → toàn bộ Submission Service treo, kể cả auto-save.

**Bài toán nhỏ phải giải quyết:** Phát hiện Exam Service đang lỗi trước khi nó kéo chết Submission Service, và cắt đứt việc gọi tiếp tới nó trong 1 khoảng thời gian để hệ thống tự hồi phục.

**Cách Circuit Breaker giải quyết:**

- Đếm số lỗi liên tiếp khi gọi Exam Service.
- Sau 5 lỗi → chuyển Circuit sang `OPEN` → mọi request tiếp theo bị chặn ngay lập tức (< 1ms), không chờ timeout 30s.
- Sau 30s → chuyển `HALF-OPEN`, thử 1 request → nếu OK thì `CLOSED` lại, nếu vẫn lỗi thì `OPEN` tiếp.

**Input cần có trước khi bắt đầu:**

| Từ đâu | Cái gì                                                                                                                                     | Bắt buộc?               |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------- |
| TV1    | Method signature chính xác của `examServiceClient.getExamQuestionsWithAnswers()` — không đổi signature, chỉ bọc `@CircuitBreaker` quanh nó | Phải chờ TV1 chốt trước |
| TV1    | Danh sách exception mà Saga cần bắt để biết khi nào cần compensation                                                                       | Từ TV1                  |

**Output — TV2 phải cung cấp cho các TV khác:**

| Cung cấp cho | Nội dung                                                                                                                                                               | Định dạng                       |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------- |
| TV1          | Loại exception ném ra khi Circuit ở trạng thái `OPEN` (mặc định Resilience4j: `CallNotPermittedException`) — để TV1 catch đúng exception này trong step `FetchAnswers` | Tên class exception             |
| TV4          | Config ngưỡng circuit breaker (số lỗi, thời gian OPEN) để TV4 tính toán kịch bản load test khớp với ngưỡng này                                                         | File `application.yml` cấu hình |

**Việc cụ thể cần làm:**

1. Thêm dependency Resilience4j vào `pom.xml`.
2. Cấu hình `application.yml`: `failure-rate-threshold`, `wait-duration-in-open-state`, `sliding-window-size`.
3. Annotate method trong `ExamServiceClient` với `@CircuitBreaker(name="examService", fallbackMethod="fallback")`.
4. Viết fallback method trả lỗi rõ ràng (không phải null) để TV1 bắt được.
5. Test: dùng Toxiproxy hoặc `Thread.sleep()` giả lập Exam Service chậm → đo thời gian phản hồi trước/sau khi có Circuit Breaker.

---

### TV3 — Outbox Pattern + Event-Driven

**Ngữ cảnh:** Sau khi chấm điểm xong, hệ thống cần lưu điểm VÀ báo cho Statistics Service — 2 hành động trên 2 hệ thống khác nhau (DB local vs. network call), không có transaction chung. Nếu Statistics đang down đúng lúc đó, event bị mất vĩnh viễn.

**Bài toán nhỏ phải giải quyết:** Đảm bảo "lưu điểm" và "gửi thông báo cho Statistics" luôn đồng bộ với nhau — hoặc cả 2 cùng thành công, hoặc cả 2 cùng không.

**Cách Outbox giải quyết:**

- Thay vì gọi Statistics Service trực tiếp, ghi 1 dòng event vào bảng `outbox` trong cùng transaction DB với việc lưu điểm.
- Vì cùng 1 transaction → 2 việc này luôn atomic (ăn cả, ngã cùng).
- 1 background job riêng, chạy định kỳ, đọc bảng `outbox`, đẩy sang RabbitMQ. Nếu Statistics down, event vẫn nằm trong queue chờ, không mất.

**Input cần có trước khi bắt đầu:**

| Từ đâu      | Cái gì                                                                                                                                                 | Bắt buộc?                         |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------- |
| TV1         | Vị trí chính xác trong Saga (step 5 — SaveResult) nơi cần chèn thêm câu lệnh ghi vào `outbox`, và xác nhận nó nằm trong cùng `@Transactional` boundary | Phải chờ TV1 chốt cấu trúc step 5 |
| Tự thiết kế | Schema bảng `outbox` (id, event_type, payload JSON, status, created_at)                                                                                | TV3 tự làm                        |

**Output — TV3 phải cung cấp cho các TV khác:**

| Cung cấp cho | Nội dung                                                                                          | Định dạng                                                |
| ------------ | ------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| TV1          | Đoạn code (1 dòng insert vào outbox) cần chèn vào cuối step `SaveResult` của Saga                 | Snippet Java để TV1 paste vào                            |
| — (độc lập)  | Statistics consumer không phụ thuộc trực tiếp vào TV2/TV4, chỉ cần TV1 cung cấp đúng event schema | Payload JSON: `{examId, submissionId, score, studentId}` |

**Việc cụ thể cần làm:**

1. Migration tạo bảng `outbox` (status: `PENDING`/`SENT`).
2. Sửa code lưu kết quả để ghi thêm 1 dòng outbox trong cùng transaction (phối hợp với TV1).
3. Viết scheduled job (`@Scheduled` mỗi 1-2s) đọc `outbox` status=`PENDING`, publish RabbitMQ, update status=`SENT`.
4. Cấu hình RabbitMQ trong `docker-compose.yml` (exchange, queue, binding).
5. Viết consumer bên Statistics Service — idempotent (xử lý trùng lặp an toàn, vì message queue có thể gửi lại).
6. Test: tắt Statistics → nộp bài → bật lại Statistics → xác nhận tự đồng bộ hết dữ liệu tồn đọng.

---

### TV4 — API Gateway nâng cao + Điều phối Integration Test

**Ngữ cảnh:** 3 pattern trên xử lý lỗi ở tầng service-to-service, nhưng chưa có lớp kiểm soát tải ở tầng ngoài cùng (client → Gateway). Nếu 200 sinh viên bấm nộp bài liên tục, lượng request có thể vượt xa khả năng xử lý dù backend đã resilient.

**Bài toán nhỏ phải giải quyết:** Kiểm soát lưu lượng request đổ vào `/submit` từ tầng Gateway, và kiểm chứng bằng số liệu thực tế rằng toàn bộ 4 pattern hoạt động đúng khi kết hợp với nhau trong kịch bản 200 sinh viên đồng thời.

**Cách Gateway pattern giải quyết:**

- Rate Limiting: giới hạn 10 req/s cho `/submit`, tránh spike đột ngột làm bão hòa backend trước khi Circuit Breaker kịp phản ứng.
- Timeout toàn cục 10s: đảm bảo không request nào ở tầng Gateway bị treo vô hạn.
- Retry với backoff: tự động thử lại khi lỗi mạng thoáng qua, giảm số lần sinh viên phải tự bấm nộp lại.

**Input cần có trước khi bắt đầu** (TV4 là người chờ nhiều nhất, nên làm phần độc lập trước):

| Từ đâu | Cái gì                                                                                                         | Bắt buộc?                                                               |
| ------ | -------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| TV1    | HTTP status code + response format khi Saga rollback (để cấu hình Nginx không retry nhầm vào case đã rollback) | Cần trước khi viết retry rule, không cần để cấu hình rate limit/timeout |
| TV2    | Ngưỡng cấu hình Circuit Breaker (để tính toán kịch bản load test cho đủ số lỗi trigger OPEN)                   | Cần trước khi thiết kế load test script                                 |
| TV3    | Không phụ thuộc trực tiếp                                                                                      | —                                                                       |

**Output — TV4 phải cung cấp cho các TV khác:**

| Cung cấp cho | Nội dung                                                                                                                                                                                | Định dạng                                     |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| Cả nhóm      | Kết quả load test cuối cùng (200 sinh viên đồng thời) — tỷ lệ thành công, độ trễ trung bình, số lần Circuit Breaker trigger, số event outbox xử lý — làm bằng chứng demo cho giảng viên | Báo cáo (bảng số liệu + biểu đồ từ k6/JMeter) |

**Việc cụ thể cần làm:**

1. Cấu hình `limit_req` trong Nginx cho route `/submit` (10 req/s, burst cho phép).
2. Cấu hình `proxy_read_timeout 10s`.
3. Cấu hình retry (Nginx `proxy_next_upstream` hoặc client-side exponential backoff).
4. Viết script load test (k6 hoặc JMeter) giả lập 200 user đồng thời gọi `/submit`.
5. Chạy test tích hợp sau khi cả 3 TV khác xong — bước tổng hợp cuối cùng, cần lịch làm việc rõ với 3 người kia.
6. Tổng hợp báo cáo: trước/sau khi có 4 pattern, so sánh số liệu.

### Sơ đồ phụ thuộc giữa 4 người

```
TV1 (Saga) ──chốt interface──► TV2 (Circuit Breaker)
    │                              │
    └──chốt transaction boundary──► TV3 (Outbox)
    │
    └──chốt response format────────► TV4 (Gateway retry rule)

TV2 ──chốt config ngưỡng──► TV4 (load test script)

TV4 chạy integration test CUỐI CÙNG, sau khi TV1+TV2+TV3 đã merge code.
```

**Gợi ý lịch làm việc:** TV1 nên code xong khung Saga (kể cả khi step 3, step 5 còn là placeholder) trong 2-3 ngày đầu và commit lên nhánh chung sớm, để TV2 và TV3 không bị chặn quá lâu. TV4 có thể bắt đầu cấu hình Nginx (rate limit, timeout) song song ngay từ đầu vì không phụ thuộc ai, chỉ phần retry rule và load test cần chờ.

---

## Tóm Tắt

```
HIỆN TẠI
  ✅ Microservices cơ bản: 3 service hoạt động đầy đủ
  ✅ Giáo viên tạo đề, học sinh làm bài, chấm điểm tự động, thống kê
  ❌ Không có xử lý lỗi khi gọi service thất bại giữa chừng
  ❌ Cascading failure khi 1 service chậm
  ❌ Dữ liệu có thể mất đồng bộ giữa submission DB và thống kê

YÊU CẦU GIẢNG VIÊN
  → Chọn 1 bài toán phức tạp, áp dụng nhiều pattern vào đó
  → Chia việc theo pattern, không phải theo use case

BÀI TOÁN: Học sinh nộp bài thi
  → Tương tự bài toán đặt vé: 1 flow, nhiều service, cần reliability cao

GIẢI PHÁP: 4 Patterns
  Pattern 1: Saga          → Bài không bao giờ bị treo
  Pattern 2: Circuit Breaker → Service không kéo chết nhau
  Pattern 3: Outbox + Event  → Dữ liệu luôn nhất quán
  Pattern 4: API Gateway++   → Kiểm soát tải, timeout toàn cục
```
