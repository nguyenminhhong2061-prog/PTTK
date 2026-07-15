# Tổng Quan Dự Án — Hệ Thống Thi Trắc Nghiệm Online

> **Môn học**: Phát Triển Phần Mềm Hướng Dịch Vụ (PTTK)  
> **Trường**: Học viện Công nghệ Bưu chính Viễn thông — PTIT  
> **Nhóm**: Nguyễn Minh Hồng (B21DCCN400) · Phùng Trung Kiên (B22DCCN433) · Kim Duy Hưng (B22DCCN409)

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

| Thành phần | Tech | Vai trò |
|-----------|------|---------|
| Frontend | React + Vite | Giao diện giáo viên & học sinh |
| API Gateway | Nginx | Routing, CORS |
| Exam Service | Java 17 + Spring Boot 3 + MySQL | CRUD câu hỏi, đề thi |
| Submission Service | Java 17 + Spring Boot 3 + MySQL | Phiên làm bài, chấm điểm |
| Statistics Service | Java 17 + Spring Boot 3 (stateless) | Tổng hợp thống kê, bảng điểm |

#### Trạng thái hiện tại của Submission (2 trạng thái)

| Trạng thái | Ý nghĩa |
|-----------|---------|
| `IN_PROGRESS` | Học sinh đang làm bài, có thể lưu đáp án, có thể resume nếu mất kết nối |
| `SUBMITTED` | Đã nộp và chấm điểm xong, không thể sửa hay nộp lại |

Quá trình nộp bài hiện tại: `POST /submit` → **gọi đồng bộ** Exam Service lấy đáp án → chấm điểm → lưu kết quả → trả điểm — **tất cả trong 1 HTTP request, không có cơ chế xử lý lỗi giữa các service**.

---

## 2. Yêu Cầu Của Giảng Viên

> *Tổng hợp từ phản hồi trực tiếp của thầy trong buổi hướng dẫn.*

### Yêu cầu về kiến trúc
Thầy yêu cầu dự án phải **thực sự áp dụng các pattern của Microservices**, không chỉ đơn thuần là chia code thành nhiều service rồi gọi nhau qua HTTP.

> *"Phải áp dụng các pattern vào, chứ không phải chỉ tách service ra."*

### Yêu cầu về cách chia việc
Thay vì mỗi người làm một use case riêng biệt, thầy yêu cầu:

> *"Chọn 1 bài toán cụ thể, rồi xem trong flow của bài toán đó có thể áp dụng được những pattern nào — giống như bài toán đặt vé, một flow nhưng áp dụng được nhiều pattern. Sau đó chia việc ra theo pattern."*

### Ý nghĩa thực tế
Thầy muốn nhóm hiểu và minh chứng được:
- **Tại sao** phải dùng pattern (vấn đề cụ thể gặp phải)
- **Pattern đó giải quyết vấn đề như thế nào** trong ngữ cảnh thực tế
- Mỗi thành viên **chịu trách nhiệm implement** một pattern cụ thể, không phải implement toàn bộ một use case

---

## 3. Bài Toán Đặt Ra: "Học Sinh Nộp Bài Thi"

### Tại sao chọn bài toán này?

Đây là luồng phức tạp và quan trọng nhất trong hệ thống — tương tự bài toán **đặt vé** mà thầy ví dụ:

| Bài toán đặt vé | Bài toán nộp bài thi |
|----------------|---------------------|
| Nhiều người cùng đặt vé một chuyến | Nhiều học sinh cùng nộp bài một đề |
| Gọi nhiều service: payment, seat, ticket | Gọi nhiều service: submission, exam, statistics |
| Không được mất đơn hàng | Không được mất bài nộp / điểm số |
| Cần rollback nếu thanh toán thất bại | Cần rollback nếu lấy đáp án thất bại |

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

| Tính năng | Mục đích |
|-----------|---------|
| **Rate Limiting** — 10 req/s cho `/submit` | Ngăn 200 sinh viên bấm nộp liên tục, giảm tải cho backend |
| **Timeout toàn cục** — 10s | Không để request treo vô hạn, tránh thread pool exhaustion |
| **Retry với Exponential Backoff** | Tự retry khi network glitch tạm thời, trong suốt với người dùng |

---

### Phân Công 4 Thành Viên Theo Pattern

| Thành viên | Pattern | Công việc | Demo |
|-----------|---------|-----------|------|
| **TV1** | **Saga** | Thêm trạng thái `GRADING`. Refactor `submit()` thành Saga Orchestrator với compensation logic | Tắt Exam Service giữa chừng → bài tự rollback, sinh viên nộp lại được |
| **TV2** | **Circuit Breaker** | Tích hợp Resilience4j vào `ExamServiceClient`. Cấu hình ngưỡng, fallback | Giả lập Exam Service chậm → Submission vẫn sống, auto-save vẫn hoạt động |
| **TV3** | **Outbox + Event-Driven** | Tạo bảng `outbox`. Viết background job publish RabbitMQ. Viết Statistics consumer | Tắt Statistics → nộp bài → khởi động lại Statistics → thống kê tự đồng bộ |
| **TV4** | **API Gateway nâng cao** | Cấu hình rate limit, timeout, retry trong Nginx. Test end-to-end | Kịch bản 200 sinh viên nộp bài đồng thời |

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
