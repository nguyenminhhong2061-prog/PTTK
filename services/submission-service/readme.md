# Submission Service

## Mô tả

**Submission Service** là service chịu trách nhiệm quản lý toàn bộ vòng đời làm bài thi của học sinh.

### Nghiệp vụ (Business Domain)

 Nó xử lý mọi hoạt động phát sinh sau khi bài thi đã được giáo viên công bố, bao gồm: học sinh bắt đầu thi, lưu tiến trình, nộp bài và nhận điểm.

### Dữ liệu sở hữu (Data Ownership)

| Bảng         | Mô tả                                                                           |
|--------------|---------------------------------------------------------------------------------|
| `submissions`| Phiên làm bài của học sinh: trạng thái, thời gian bắt đầu/deadline, điểm số    |
| `answers`    | Đáp án của học sinh cho từng câu hỏi: lựa chọn đã chọn, kết quả đúng/sai       |

Service **không sở hữu** thông tin bài thi hay câu hỏi — những dữ liệu đó thuộc về **Exam Service** và chỉ được truy vấn khi cần.

### Các nghiệp vụ chính (Operations)

- **Bắt đầu thi**: Tạo phiên làm bài mới cho học sinh, hoặc resume phiên đang dở. Lấy danh sách câu hỏi (không có đáp án) từ Exam Service.
- **Lưu đáp án tạm**: Cho phép học sinh lưu tiến trình trong quá trình làm bài (auto-save). Dữ liệu được cập nhật từng phần theo từng câu hỏi.
- **Nộp bài & chấm điểm**: Khi học sinh nộp bài, service lấy đáp án đúng từ Exam Service, so sánh với lựa chọn của học sinh, tính điểm và cập nhật trạng thái `SUBMITTED`.
- **Truy vấn kết quả**: Cho phép xem chi tiết bài nộp (kèm đáp án đúng/sai) sau khi đã nộp bài.

---

## Tech Stack

| Component   | Lựa chọn                        |
|-------------|----------------------------------|
| Language    | Java 17                          |
| Framework   | Spring Boot 3.2                  |
| ORM         | Spring Data JPA + Hibernate      |
| Database    | MySQL 8.0                        |
| HTTP Client | Spring WebFlux WebClient         |
| Build       | Maven 3.9                        |

---

## API Endpoints

Tất cả response đều được bọc trong `ApiResponse<T>`:
```json
{ "success": true, "data": { ... } }
```

| Method | Endpoint                          | Mô tả                                          |
|--------|-----------------------------------|------------------------------------------------|
| GET    | `/health`                         | Health   check                                   |
| POST   | `/submissions`                    | Bắt đầu làm bài (tạo mới hoặc resume)         |
| GET    | `/submissions`                    | Lấy danh sách bài nộp (filter theo query param)|
| GET    | `/submissions/{id}`               | Xem chi tiết bài nộp                           |
| PUT    | `/submissions/{id}/answers`       | Lưu đáp án tạm thời                           |
| POST   | `/submissions/{id}/submit`        | Nộp bài và chấm điểm tự động                   |

**Query params của `GET /submissions`:**
- `studentId` — lọc theo học sinh
- `examId` — lọc theo bài thi
- `status` — lọc theo trạng thái (`IN_PROGRESS`, `SUBMITTED`)

> Full API spec: [`docs/api-specs/submission-service.yaml`](../../docs/api-specs/submission-service.yaml)

---

## Chạy Cục Bộ

```bash
# Với Docker Compose (khuyến nghị — cần Exam Service + MySQL chạy trước)
docker compose up submission-service --build

# Kiểm tra health
curl http://localhost:5002/health

---

## Cấu Trúc Thư Mục

```
submission-service/
├── Dockerfile
├── pom.xml
├── readme.md
└── src/main/
    ├── java/com/quizapp/submission/
    │   ├── SubmissionServiceApplication.java
    │   ├── client/
    │   │   └── ExamServiceClient.java        ← Gọi Exam Service qua WebClient
    │   ├── config/
    │   │   └── WebClientConfig.java          ← Cấu hình bean WebClient
    │   ├── controller/
    │   │   └── SubmissionController.java     ← REST endpoints (/submissions/*)
    │   ├── dto/
    │   │   ├── request/
    │   │   │   ├── StartExamRequest.java     ← Body cho POST /submissions
    │   │   │   └── SaveAnswersRequest.java   ← Body cho PUT /answers
    │   │   └── response/
    │   │       ├── ApiResponse.java          ← Wrapper chung cho mọi response
    │   │       ├── SubmissionStartResponse.java   ← Response bắt đầu thi
    │   │       ├── SubmissionSummaryResponse.java ← Response danh sách bài nộp
    │   │       ├── SubmissionDetailResponse.java  ← Response chi tiết bài nộp
    │   │       └── SubmitResponse.java       ← Response sau khi nộp bài + điểm
    │   ├── entity/
    │   │   ├── Submission.java               ← Entity phiên làm bài
    │   │   └── Answer.java                   ← Entity đáp án từng câu
    │   ├── enums/
    │   │   ├── AnswerOption.java             ← Enum A/B/C/D
    │   │   └── SubmissionStatus.java         ← Enum IN_PROGRESS / SUBMITTED
    │   ├── repository/
    │   │   ├── SubmissionRepository.java
    │   │   └── AnswerRepository.java
    │   └── service/
    │       ├── SubmissionService.java        ← Business logic chính
    │       └── GradingService.java           ← Logic chấm điểm
    └── resources/
        └── application.properties
```

---

## Biến Môi Trường

| Biến                | Mô tả                        | Mặc định                   |
|---------------------|------------------------------|----------------------------|
| `DB_HOST`           | Hostname MySQL               | `submission-db`            |
| `DB_PORT`           | Port MySQL                   | `3306`                     |
| `DB_NAME`           | Tên database                 | `submission_db`            |
| `DB_USER`           | Username MySQL               | `root`                     |
| `DB_PASSWORD`       | Password MySQL               | `changeme`                 |
| `EXAM_SERVICE_URL`  | URL của Exam Service         | `http://exam-service:8080` |



