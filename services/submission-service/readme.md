# Submission Service

> **Phụ trách**: Thành viên 2

## Mô tả

Submission Service quản lý toàn bộ quá trình học sinh làm bài thi: tạo phiên làm bài, lưu đáp án, nộp bài và chấm điểm tự động.

## Tech Stack

| Component  | Lựa chọn                         |
|------------|----------------------------------|
| Language   | Java 17                          |
| Framework  | Spring Boot 3.2                  |
| ORM        | Spring Data JPA + Hibernate      |
| Database   | MySQL 8.0                        |
| HTTP Client| Spring WebFlux WebClient         |
| Build      | Maven 3.9                        |

## API Endpoints

| Method | Endpoint                          | Mô tả                           |
|--------|-----------------------------------|---------------------------------|
| GET    | `/health`                         | Health check                    |
| POST   | `/submissions`                    | Bắt đầu làm bài (tạo phiên)    |
| GET    | `/submissions`                    | Lấy danh sách bài nộp          |
| GET    | `/submissions/{id}`               | Xem chi tiết bài nộp           |
| PUT    | `/submissions/{id}/answers`       | Lưu đáp án tạm thời            |
| POST   | `/submissions/{id}/submit`        | Nộp bài và chấm điểm tự động   |

> Full API spec: [`docs/api-specs/submission-service.yaml`](../../docs/api-specs/submission-service.yaml)

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
    │   │   └── ExamServiceClient.java    ← Gọi Exam Service
    │   ├── config/
    │   │   └── WebClientConfig.java
    │   ├── controller/
    │   │   └── SubmissionController.java
    │   ├── dto/
    │   │   ├── request/
    │   │   │   ├── StartExamRequest.java
    │   │   │   └── SaveAnswersRequest.java
    │   │   └── response/
    │   │       ├── ApiResponse.java
    │   │       ├── SubmissionStartResponse.java
    │   │       ├── SubmissionDetailResponse.java
    │   │       └── SubmitResponse.java
    │   ├── entity/
    │   │   ├── Submission.java
    │   │   └── Answer.java
    │   ├── enums/
    │   │   ├── AnswerOption.java
    │   │   └── SubmissionStatus.java
    │   ├── repository/
    │   │   ├── SubmissionRepository.java
    │   │   └── AnswerRepository.java
    │   └── service/
    │       ├── SubmissionService.java
    │       └── GradingService.java
    └── resources/
        └── application.properties
```

## Giao Tiếp Nội Bộ

Service này gọi **Exam Service** trong 2 trường hợp:
1. Khi học sinh **bắt đầu làm bài** → lấy câu hỏi (không có đáp án)
2. Khi học sinh **nộp bài** → lấy đáp án đúng để chấm điểm

URL nội bộ Docker: `http://exam-service:8080`

## Biến Môi Trường

| Biến                | Mô tả                       | Mặc định              |
|---------------------|-----------------------------|-----------------------|
| `DB_HOST`           | Hostname MySQL              | `submission-db`       |
| `DB_PORT`           | Port MySQL                  | `3306`                |
| `DB_NAME`           | Tên database                | `submission_db`       |
| `DB_USER`           | Username MySQL              | `root`                |
| `DB_PASSWORD`       | Password MySQL              | `changeme`            |
| `EXAM_SERVICE_URL`  | URL của Exam Service        | `http://exam-service:8080` |

## Chạy Cục Bộ

```bash
# Với Docker Compose (khuyến nghị — cần Exam Service + MySQL chạy trước)
docker compose up submission-service --build

# Kiểm tra health
curl http://localhost:5002/health
```
