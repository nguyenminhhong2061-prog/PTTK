# Walkthrough — Saga Pattern (Orchestration) cho Luồng Nộp Bài Thi

Tài liệu này là kết quả rà soát toàn bộ source code thực tế và đối chiếu với yêu cầu trong `PROJECT_OVERVIEW.md` để xác nhận tình trạng hoàn thành và cung cấp hướng dẫn tích hợp cho các thành viên.

---

## 1. Kết Quả Rà Soát — Đối Chiếu Yêu Cầu

| Yêu cầu từ PROJECT_OVERVIEW (TV1) | Trạng thái | Ghi chú |
|---|---|---|
| Migration thêm `GRADING` vào enum status | ✅ Hoàn thành | `SubmissionStatus.java` + DB ALTER thủ công |
| Tạo `SubmissionSagaOrchestrator` với đúng 4 step | ✅ Hoàn thành | Validate → MarkGrading → FetchAnswers → Grade |
| Mỗi step implement `SagaStep { execute(); compensate(); }` | ✅ Hoàn thành | Cả 4 step đều implement |
| Khi Exam Service fail → rollback về `IN_PROGRESS` | ✅ Đã kiểm chứng thực tế | DB hiển thị `IN_PROGRESS` sau khi tắt exam-service |
| Cung cấp cho TV2: method `getExamQuestionsWithAnswers(examId)` cố định | ✅ Hoàn thành | Gọi trong `FetchAnswersStep.execute()` |
| Cung cấp cho TV2: hook catch exception để trigger compensation | ✅ Hoàn thành | `try-catch` trong `Orchestrator` bao quanh Step 3+4 |
| Cung cấp cho TV3: `@Transactional` ở bước lưu kết quả | ✅ Hoàn thành | `GradeAndSaveStep.execute()` có `@Transactional` |
| Cung cấp cho TV4: HTTP 503 khi rollback | ✅ Hoàn thành | `SubmissionController` trả 503 khi catch Exception |
| Test: tắt Exam Service → bài thi tự rollback về `IN_PROGRESS` | ✅ Đã kiểm chứng thực tế | Kết quả DB đã được xác nhận |

> [!IMPORTANT]
> **Lưu ý quan trọng về Database:** Vì `spring.jpa.hibernate.ddl-auto=update`, Hibernate không tự động cập nhật giá trị của một cột ENUM đã tồn tại trong database. Khi **chạy lại hệ thống trên máy mới** hoặc **sau khi xóa Volume Docker**, cần chạy thủ công câu lệnh sau một lần sau khi hệ thống khởi động:
> ```sql
> ALTER TABLE submissions MODIFY COLUMN status ENUM('IN_PROGRESS', 'GRADING', 'SUBMITTED') NOT NULL;
> ```
> Kết nối tới: `host=localhost`, `port=3307`, `user=root`, `password=changeme`, `database=submission_db`.

---

## 2. Cấu Trúc Package & Các File Đã Tạo Mới

```
submission-service/src/main/java/com/quizapp/submission/
├── enums/
│   └── SubmissionStatus.java          ← [MODIFIED] Thêm GRADING
├── saga/
│   ├── SagaStep.java                  ← [NEW] Interface 2 phương thức execute/compensate
│   ├── SubmissionSagaContext.java      ← [NEW] Vùng nhớ chung giữa các step
│   ├── SubmissionSagaOrchestrator.java ← [NEW] Bộ điều phối trung tâm
│   ├── SagaExecutionException.java    ← [NEW] Exception khi Saga thất bại
│   ├── SagaRollbackException.java     ← [NEW] Exception khi cần rollback
│   └── step/
│       ├── ValidateSubmissionStep.java ← [NEW] Bước 1: Xác minh bài thi
│       ├── MarkGradingStep.java        ← [NEW] Bước 2: Khóa bài (có compensate)
│       ├── FetchAnswersStep.java       ← [NEW] Bước 3: Lấy đáp án từ Exam Service
│       └── GradeAndSaveStep.java       ← [NEW] Bước 4: Chấm điểm + lưu DB
└── controller/
    └── SubmissionController.java       ← [MODIFIED] Dùng Orchestrator thay vì SubmissionService.submit()

submission-service/src/test/java/com/quizapp/submission/
└── saga/
    └── SubmissionSagaOrchestratorTest.java ← [NEW] Unit test 2 kịch bản
```

---

## 3. Giải Thích Chi Tiết Từng Thành Phần

### 3.1. SagaStep Interface — Hợp đồng thiết kế
[SagaStep.java](file:///c:/Users/Admin/Desktop/PTTK/mid-project-433409400/services/submission-service/src/main/java/com/quizapp/submission/saga/SagaStep.java) định nghĩa khuôn mẫu bắt buộc mà mọi giao dịch cục bộ trong Saga phải tuân theo:
```java
public interface SagaStep<T, R> {
    R execute(T context);    // Chạy tiến: thực hiện hành động chính
    void compensate(T context); // Chạy lùi: hoàn tác hành động đó
}
```
**Tại sao thiết kế vậy?** Generic type `<T, R>` cho phép mọi Step dùng chung 1 interface nhưng tự định nghĩa kiểu Context và kiểu kết quả phù hợp, không bị phụ thuộc vào nhau.

### 3.2. SubmissionSagaContext — Vùng nhớ chung
[SubmissionSagaContext.java](file:///c:/Users/Admin/Desktop/PTTK/mid-project-433409400/services/submission-service/src/main/java/com/quizapp/submission/saga/SubmissionSagaContext.java) là object được tạo ra ở đầu mỗi Saga và truyền qua tất cả các step:

| Trường | Kiểu | Mục đích |
|---|---|---|
| `submissionId` (final) | `String` | ID bài nộp — input từ Client, không thay đổi |
| `lastAnswers` (final) | `SaveAnswersRequest` | Đáp án cuối gửi kèm từ Client, không thay đổi |
| `submission` | `Submission` | Entity bài nộp — ValidateStep tìm từ DB và set vào đây để các step sau tái sử dụng, tránh truy vấn DB thừa |
| `examQuestions` | `ExamQuestionsDto` | Câu hỏi + đáp án đúng — FetchAnswersStep lấy từ Exam Service và lưu vào đây |
| `submitResponse` | `SubmitResponse` | Kết quả chấm điểm — GradeAndSaveStep tạo và set vào đây để Controller trả về Client |

### 3.3. Luồng Hoạt Động Của Orchestrator
[SubmissionSagaOrchestrator.java](file:///c:/Users/Admin/Desktop/PTTK/mid-project-433409400/services/submission-service/src/main/java/com/quizapp/submission/saga/SubmissionSagaOrchestrator.java) có một phương thức duy nhất `executeSaga()`. Cấu trúc `try-catch` lồng nhau là cốt lõi của toàn bộ cơ chế rollback:

```
Bước 1 (Validate)    → Không rollback  → Nếu lỗi: ném ra ngoài, không bài nào bị ảnh hưởng
Bước 2 (MarkGrading) → CÓ rollback     → Được bảo vệ bởi inner try-catch
    ↓
    [Inner try-catch bắt lỗi từ Bước 3 và 4]
    Bước 3 (FetchAnswers) → Đọc dữ liệu → Không cần rollback dữ liệu
    Bước 4 (GradeAndSave) → @Transactional → Spring tự rollback DB nếu lỗi trong block này
    ↓
    [Nếu Bước 3 hoặc 4 ném Exception]
    markGradingStep.compensate(context) ← Đây là điểm quan trọng nhất
    → Cập nhật DB: GRADING → IN_PROGRESS
    → Ném lỗi tiếp để Controller bắt và trả HTTP 503
```

### 3.4. ValidateSubmissionStep — Cổng gác đầu vào
[ValidateSubmissionStep.java](file:///c:/Users/Admin/Desktop/PTTK/mid-project-433409400/services/submission-service/src/main/java/com/quizapp/submission/saga/step/ValidateSubmissionStep.java) thực hiện 3 kiểm tra:
1. **Bài thi phải tồn tại:** `submissionRepository.findById(...)` — không tìm thấy thì ném `IllegalArgumentException` → Controller trả 404.
2. **Không được đã `SUBMITTED`:** Tránh nộp lại bài đã có điểm.
3. **Không được đang `GRADING`:** Tránh submit trùng lặp cùng lúc (idempotency).

**Không có compensate** vì bước này không ghi dữ liệu nào vào DB.

### 3.5. MarkGradingStep — Điểm neo + Cơ chế compensate
[MarkGradingStep.java](file:///c:/Users/Admin/Desktop/PTTK/mid-project-433409400/services/submission-service/src/main/java/com/quizapp/submission/saga/step/MarkGradingStep.java) là step có ý nghĩa quan trọng nhất trong toàn bộ Saga:

- **`@Transactional`** trên cả `execute()` và `compensate()` — đảm bảo mỗi lần thay đổi trạng thái là atomic.
- **execute():** `IN_PROGRESS → GRADING`. Hành động "khóa" bài thi để tránh nộp song song.
- **compensate():** `GRADING → IN_PROGRESS`. Hành động "mở khóa" khi hệ thống gặp sự cố, đây là thứ đảm bảo học sinh **không bao giờ bị kẹt bài thi**.

### 3.6. FetchAnswersStep — Điểm giao với TV2 (Circuit Breaker)
[FetchAnswersStep.java](file:///c:/Users/Admin/Desktop/PTTK/mid-project-433409400/services/submission-service/src/main/java/com/quizapp/submission/saga/step/FetchAnswersStep.java) gọi:
```java
ExamQuestionsDto examQuestions = examServiceClient.getExamQuestionsWithAnswers(examId);
```
**Cam kết với TV2:** Method `getExamQuestionsWithAnswers(Long examId)` trong [ExamServiceClient.java](file:///c:/Users/Admin/Desktop/PTTK/mid-project-433409400/services/submission-service/src/main/java/com/quizapp/submission/client/ExamServiceClient.java) là điểm TV2 sẽ đặt `@CircuitBreaker`. Khi mạch mở, Resilience4j ném `CallNotPermittedException` — đây là `Exception` được Orchestrator bắt và kích hoạt `compensate()`.

### 3.7. GradeAndSaveStep — Điểm giao với TV3 (Outbox)
[GradeAndSaveStep.java](file:///c:/Users/Admin/Desktop/PTTK/mid-project-433409400/services/submission-service/src/main/java/com/quizapp/submission/saga/step/GradeAndSaveStep.java) có `@Transactional` bao phủ toàn bộ `execute()`:
1. Lưu đáp án cuối từ Client.
2. Chấm điểm thi qua `GradingService`.
3. Cập nhật DB: status → `SUBMITTED`, score, correctCount, submittedAt.
4. Lưu isCorrect từng câu trả lời.
5. Build `SubmitResponse` và đưa vào Context.
6. **TODO cho TV3:** Dòng `// outboxService.saveEvent(...)` — TV3 sẽ chèn code ghi vào bảng `outbox` tại đây. Vì nằm trong `@Transactional`, nếu DB chính rollback thì outbox cũng rollback, và ngược lại.

---

## 4. Contracts Giao Tiếp — Input/Output Cho Các Thành Viên

### 4.1. TV1 → TV2 (Circuit Breaker)
| Hạng mục | Nội dung |
|---|---|
| **Interface cố định TV2 dùng** | `ExamServiceClient.getExamQuestionsWithAnswers(Long examId): ExamQuestionsDto` |
| **TV2 cần làm** | Thêm `@CircuitBreaker(name="examService", fallbackMethod="...")` vào method này |
| **Exception TV2 phải ném ra** | `io.github.resilience4j.circuitbreaker.CallNotPermittedException` |
| **Saga bắt bằng cách nào** | `catch (Exception e)` trong inner block của Orchestrator bắt tất cả exception |

### 4.2. TV1 → TV3 (Outbox)
| Hạng mục | Nội dung |
|---|---|
| **File TV3 cần sửa** | `GradeAndSaveStep.java` — dòng 80-82 (phần TODO) |
| **Transaction đã có** | `@Transactional` đánh dấu trên toàn `execute()` |
| **Data TV3 có thể truy cập** | `context.getSubmission()` — có `examId`, `studentId`, `id` (submissionId) |
| **Data TV3 cần** | `{examId, submissionId, score, studentId}` — đầy đủ trong `context.getSubmission()` và `context.getSubmitResponse()` |

### 4.3. TV1 → TV4 (API Gateway)
| Hạng mục | Nội dung |
|---|---|
| **HTTP Status khi rollback** | `503 Service Unavailable` |
| **JSON Response khi rollback** | `{"success": false, "message": "Hệ thống chấm điểm đang bận hoặc gặp sự cố. Trạng thái bài thi đã được khôi phục. Vui lòng thử lại sau."}` |
| **HTTP Status khi thành công** | `200 OK` |
| **JSON Response khi thành công** | `{"success": true, "message": "Nộp bài thành công!", "data": {SubmitResponse}}` |

---

## 5. Kết Quả Kiểm Thử Thực Tế

### Unit Test ([SubmissionSagaOrchestratorTest.java](file:///c:/Users/Admin/Desktop/PTTK/mid-project-433409400/services/submission-service/src/test/java/com/quizapp/submission/saga/SubmissionSagaOrchestratorTest.java))

| Test Case | Mục tiêu | Kết quả |
|---|---|---|
| `testExecuteSaga_Success` | 4 step chạy đúng thứ tự, compensate không bị gọi, response trả về điểm đúng | ✅ Pass |
| `testExecuteSaga_Failure_Compensates` | Khi Step 3 lỗi, compensate của Step 2 được gọi, Step 4 không chạy | ✅ Pass |

### Kiểm Thử Thủ Công (Đã Thực Hiện & Xác Nhận)
**Kịch bản Rollback (Tắt Exam Service → Nộp bài):**
```
Kết quả DB:
B21DCCN001 | exam_id=1 | status=IN_PROGRESS | score=NULL ← Tự rollback thành công
```
**Kịch bản Thành Công (Bật lại Exam Service → Nộp bài):**
```
Kết quả DB:
B21DCCN162 | exam_id=2 | status=SUBMITTED | score=10 | submitted_at=2026-07-15 15:14:11 ← Chấm điểm đúng
```

---

## 6. Lệnh Migration Database Bắt Buộc

> [!CAUTION]
> Câu lệnh này phải chạy **mỗi khi khởi tạo môi trường mới** (máy mới, xóa Docker Volume). Nếu bỏ qua, mọi request nộp bài sẽ đều bị lỗi `Data truncated for column 'status'` dù Exam Service đang hoạt động bình thường.

```bash
docker exec quiz-system-submission-db-1 mysql -u root -pchangeme \
  -e "USE submission_db; ALTER TABLE submissions MODIFY COLUMN status ENUM('IN_PROGRESS', 'GRADING', 'SUBMITTED') NOT NULL;"
```
