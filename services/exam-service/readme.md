# Exam Service
Phụ trách: Nguyễn Minh Hồng

# Description
Exam Service quản lý toàn bộ ngân hàng câu hỏi và đề thi: tạo/sửa/xóa câu hỏi, tạo/cập nhật đề thi, quản lý trạng thái đề và cung cấp bộ câu hỏi theo đề cho phía học sinh/chấm điểm.

## Tech Stack
| Component | Lựa chọn |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| ORM | Spring Data JPA + Hibernate |
| Database | MySQL 8.0 |
| Validation | Jakarta Bean Validation |
| Build | Maven 3.9 |

## API Endpoints
| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/health` | Health check |
| GET | `/questions` | Lấy danh sách câu hỏi (phân trang, filter `createdBy`) |
| POST | `/questions` | Tạo câu hỏi mới |
| GET | `/questions/{questionId}` | Xem chi tiết câu hỏi |
| PUT | `/questions/{questionId}` | Cập nhật câu hỏi |
| DELETE | `/questions/{questionId}` | Xóa câu hỏi |
| GET | `/exams` | Lấy danh sách đề thi (phân trang, filter `status`, `createdBy`) |
| POST | `/exams` | Tạo đề thi mới (mặc định `DRAFT`) |
| GET | `/exams/{examId}` | Xem chi tiết đề thi |
| PUT | `/exams/{examId}` | Cập nhật đề thi (chỉ khi `DRAFT`) |
| PATCH | `/exams/{examId}/status` | Đổi trạng thái đề thi |
| GET | `/exams/{examId}/questions` | Lấy câu hỏi của đề (`includeAnswers=false` mặc định) |

Full API spec: [docs/api-specs/exam-service.yaml](../../docs/api-specs/exam-service.yaml)

## Cấu Trúc Thư Mục
```text
exam-service/
|- Dockerfile
|- pom.xml
|- readme.md
`- src/main/
   |- java/com/quizapp/exam/
   |  |- ExamServiceApplication.java
   |  |- config/
   |  |  `- WebConfig.java
   |  |- controller/
   |  |  |- HealthController.java
   |  |  |- QuestionController.java
   |  |  `- ExamController.java
   |  |- dto/
   |  |  |- request/
   |  |  |  |- QuestionCreateRequest.java
   |  |  |  |- ExamCreateRequest.java
   |  |  |  `- ExamStatusUpdateRequest.java
   |  |  `- response/
   |  |     |- ApiResponse.java
   |  |     |- ErrorResponse.java
   |  |     |- HealthResponse.java
   |  |     |- QuestionResponse.java
   |  |     |- QuestionListResponse.java
   |  |     |- ExamResponse.java
   |  |     |- ExamListResponse.java
   |  |     |- ExamQuestionsResponse.java
   |  |     `- ExamStatusUpdateResponse.java
   |  |- entity/
   |  |  |- Question.java
   |  |  |- Exam.java
   |  |  `- ExamQuestion.java
   |  |- enums/
   |  |  |- AnswerOption.java
   |  |  `- ExamStatus.java
   |  |- exception/
   |  |  |- BadRequestException.java
   |  |  |- NotFoundException.java
   |  |  |- ConflictException.java
   |  |  `- GlobalExceptionHandler.java
   |  |- repository/
   |  |  |- QuestionRepository.java
   |  |  |- ExamRepository.java
   |  |  `- ExamQuestionRepository.java
   |  `- service/
   |     |- QuestionService.java
   |     `- ExamService.java
   `- resources/
      |- application.properties
      |- static/
      |- template/
```

## Giao Tiếp Nội Bộ
Service này không gọi service khác để xử lý nghiệp vụ chính.

Service khác (Submission Service) gọi Exam Service trong 2 trường hợp:
- Khi học sinh bắt đầu làm bài -> lấy câu hỏi không có đáp án (`includeAnswers=false`)
- Khi học sinh nộp bài -> lấy đáp án đúng để chấm điểm (`includeAnswers=true`)

URL nội bộ Docker: `http://exam-service:8080`

## Biến Môi Trường
| Biến | Mô tả | Mặc định |
|---|---|---|
| `DB_HOST` | Hostname MySQL | `localhost` |
| `DB_PORT` | Port MySQL | `3306` |
| `DB_NAME` | Tên database | `exam_db` |
| `DB_USER` | Username MySQL | `root` |
| `DB_PASSWORD` | Password MySQL | `verysecret` |
| `JPA_DDL_AUTO` | Chế độ tạo/cập nhật schema | `update` |

## Chạy Cục Bộ
```bash
# Với Docker Compose (khuyến nghị)
docker compose up exam-db exam-service --build

# Kiểm tra health
curl http://localhost:5001/health
```
