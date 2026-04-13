# 📚 Phân Tích Toàn Bộ Dự Án — Hệ Thống Thi Trắc Nghiệm Online

## 1. Tổng Quan Kiến Trúc

Dự án là hệ thống thi trắc nghiệm trực tuyến theo kiến trúc **Microservices (SOA)** với 3 service chính:

```
Frontend (React/Vite) :3000
    └── API Gateway (Nginx) :8080
            ├── /api/exams, /api/questions  → Exam Service     :5001
            ├── /api/submissions            → Submission Svc   :5002
            └── /api/statistics             → Statistics Svc   :5003

Submission Service → [gọi nội bộ] → Exam Service (http://exam-service:8080)
Statistics Service → [gọi nội bộ] → Submission Service (http://submission-service:8080)

Database:
  exam-db       (MySQL 8.0, port 3308 host, 3306 container)
  submission-db (MySQL 8.0, port 3307 host, 3306 container)
```

---

## 2. Phân Công Thành Viên

| TV | Phụ trách | Frontend |
|----|-----------|----------|
| **TV1** | Exam Service | Trang Giáo Viên (tạo đề, quản lý) |
| **TV2** | Submission Service | Trang Học Sinh (làm bài, xem kết quả) |
| **TV3** | Statistics Service | Dashboard thống kê giáo viên |

---

## 3. Backend — Submission Service (TV2)

### Tech Stack
- Java 17 + Spring Boot 3.2
- Spring Data JPA + Hibernate
- MySQL 8.0 (`submission_db`)
- Spring WebClient (gọi Exam Service)
- Maven 3.9

### Database Schema (`submission_db`)
```
submissions:
  id           VARCHAR(36) PK (UUID)
  exam_id      BIGINT (Long — khớp với Exam Service AUTO_INCREMENT)
  student_id   VARCHAR
  status       ENUM('IN_PROGRESS', 'SUBMITTED')
  score        DOUBLE (0-100)
  correct_count INT
  total_questions INT
  started_at   DATETIME
  deadline_at  DATETIME  ← startedAt + durationMinutes
  submitted_at DATETIME

answers:
  id              VARCHAR(36) PK (UUID)
  submission_id   FK → submissions.id
  question_id     VARCHAR(36)
  order_index     INT
  selected_option ENUM('A','B','C','D') NULL  ← null = bỏ qua
  is_correct      BOOLEAN NULL  ← null = chưa chấm
  UNIQUE(submission_id, question_id)
```

### API Endpoints (port 5002)
| Method | Path | Mô tả |
|--------|------|--------|
| GET | `/health` | Health check |
| POST | `/submissions` | Bắt đầu / Resume phiên thi |
| GET | `/submissions?studentId=&examId=&status=` | Lịch sử làm bài |
| GET | `/submissions/{id}` | Chi tiết bài nộp |
| PUT | `/submissions/{id}/answers` | Lưu đáp án tạm |
| POST | `/submissions/{id}/submit` | Nộp bài + chấm điểm |

### Luồng Nghiệp Vụ Chính
1. **POST /submissions** → Kiểm tra exam published → Gọi Exam Service lấy câu hỏi → Tạo Submission + Answer records rỗng → Trả về câu hỏi (không có đáp án)
2. **PUT /submissions/{id}/answers** → Cập nhật `selected_option` cho từng Answer
3. **POST /submissions/{id}/submit** → Gọi Exam Service lấy đáp án đúng (`includeAnswers=true`) → GradingService chấm điểm → Lưu score, isCorrect → Trả về kết quả

### Giao Tiếp Nội Bộ
- `ExamServiceClient` dùng WebClient gọi `http://exam-service:8080` (Docker DNS)
- URL cấu hình qua biến môi trường: `EXAM_SERVICE_URL`

---

## 4. Frontend — Trang Học Sinh (TV2)

### Tech Stack
- React 18 + Vite
- React Router v6
- Vanilla CSS (glassmorphism dark mode)
- Vite proxy để bypass CORS khi dev

### Vite Proxy (dev mode)
```
/api-exam/* → http://localhost:5001  (Exam Service)
/api-sub/*  → http://localhost:5002  (Submission Service)
```

### Cấu Trúc Pages
```
/                      → HomePage.jsx       (chọn vai trò + nhập mã)
/student               → StudentDashboard   (danh sách bài thi + lịch sử)
/student/exam/:examId  → ExamPage           (làm bài + đồng hồ đếm ngược)
/student/result/:id    → ResultPage         (xem điểm + chi tiết đáp án)
/teacher               → TeacherPlaceholder (placeholder - TV1, TV3 làm)
```

### Auth (Simple)
- Không có JWT — dùng `sessionStorage` key `quizUser = { role, id }`
- `ProtectedRoute` check role trong sessionStorage
- Học sinh nhập mã SV → lưu vào sessionStorage → redirect `/student`

### Trạng Thái Hiện Tại (Frontend)
- ✅ `HomePage` — Hoàn chỉnh, thiết kế đẹp (glassmorphism + animations)
- ✅ `StudentDashboard` — Hiện danh sách bài thi + lịch sử
- ✅ `ExamPage` — Làm bài, lưu đáp án tự động, đồng hồ đếm ngược, nộp bài
- ✅ `ResultPage` — Hiển thị điểm (score ring), chi tiết đúng/sai từng câu
- ✅ `Navbar`, `Timer` — Hoàn chỉnh
- ⚠️ `TeacherPlaceholder` — Chỉ là placeholder (TV1 + TV3 làm)

---

## 5. Exam Service (TV1) — Đã Hoàn Chỉnh

- `Long id` (AUTO_INCREMENT IDENTITY) — **quan trọng: examId là Long, không phải UUID**
- Enum status: `DRAFT`, `PUBLISHED`, `CLOSED` (uppercase)
- Endpoint `/exams/{examId}/questions?includeAnswers=true|false` — dùng cho Submission Service
- ExamStatus trong API trả về là **uppercase** string (`"PUBLISHED"`, `"DRAFT"`)

---

## 6. Các Điểm Cần Lưu Ý & Vấn Đề Tiềm Ẩn

### ⚠️ QUAN TRỌNG: Kiểu dữ liệu examId
- Exam Service dùng `Long id` (AUTO_INCREMENT)
- Submission Service dùng `Long examId` trong entity ✅
- Frontend gọi `startExam(Number(examId), user.id)` — convert sang Number ✅
- Nhưng `examApi.js` → `GET /exams?status=PUBLISHED` — tiếng Anh uppercase

### ⚠️ Status Case Mismatch (Frontend vs Backend)
- Exam Service trả status: `"PUBLISHED"`, `"DRAFT"`, `"CLOSED"` (UPPERCASE)
- `examApi.js`: `getExams('PUBLISHED')` → đúng ✅
- `StudentDashboard`: check `s.status === 'SUBMITTED'` và `s.status === 'IN_PROGRESS'` → đúng ✅
- Submission Service enum: `IN_PROGRESS`, `SUBMITTED` → trả về `.name()` = uppercase ✅

### ⚠️ Response Format khác nhau
- Submission Service bọc trong `ApiResponse<T>`: `{ success, message, data: {...} }`
- `submissionApi.js` handler: `return data` (toàn bộ object `ApiResponse`)
- `ExamPage`: `const sub = res.data` — lấy field `.data` từ ApiResponse ✅
- `StudentDashboard`: `subRes.data || subRes.submissions || []` — cần `.data` ✅

### ⚠️ Exam Service không bọc trong ApiResponse
- `examApi.js` handler trả về `res.json()` trực tiếp
- `StudentDashboard`: `examsRes.data || examsRes.exams || []` — cần kiểm tra format thực tế

### ⚠️ ResultPage — data access qua nhiều tầng
```js
const score = result.score ?? result.data?.score ?? 0;
```
- State `result` có thể là `ApiResponse.data` (từ submit) hoặc `ApiResponse` (từ getSubmission)

### ⚠️ Frontend hiện tại gọi 2 proxy khác nhau (dev mode)
- `/api-exam` → port 5001 (Exam Service trực tiếp)
- `/api-sub` → port 5002 (Submission Service trực tiếp)
- Khi deploy qua Docker → Frontend gọi qua Gateway port 8080, cần đổi BASE URL

### ⚠️ Exam Service cần trả `totalQuestions` trong response
- `ExamController.getById()` trả `ExamResponse` — cần có field `totalQuestions`
- `StudentDashboard` hiển thị `exam.totalQuestions`

---

## 7. Flow End-to-End Học Sinh

```
1. Vào trang chủ → Click "Học Sinh" → Nhập mã SV → sessionStorage → /student
2. StudentDashboard:
   - GET /api-exam/exams?status=PUBLISHED → danh sách bài thi
   - GET /api-sub/submissions?studentId=X → lịch sử của học sinh
3. Click "Bắt đầu thi" → /student/exam/:examId → ExamPage:
   - POST /api-sub/submissions { examId: Long, studentId: String }
   → Submission Service kết nối Exam Service lấy câu hỏi
   → Hiển thị câu hỏi (không có đáp án)
4. Học sinh chọn đáp án → PUT /api-sub/submissions/{id}/answers (auto-save)
5. Nộp bài → POST /api-sub/submissions/{id}/submit
   → Submission Service lấy đáp án đúng → chấm điểm → trả về kết quả
6. Redirect → /student/result/:submissionId → ResultPage (hiển thị điểm + review)
```

---

## 8. Cấu Hình Docker

| Service | Container Port | Host Port |
|---------|---------------|-----------|
| frontend | 3000 | 3000 |
| gateway (nginx) | 80 | 8080 |
| exam-service | 8080 | 5001 |
| submission-service | 8080 | 5002 |
| statistics-service | 8080 | 5003 |
| exam-db | 3306 | **3308** |
| submission-db | 3306 | 3307 |

> Lưu ý: `exam-db` map ra host port **3308** (không phải 3306)

---

## 9. Điểm Cần Hoàn Thiện (TODO cho TV2)

- [ ] Kiểm tra format response `GET /exams` từ Exam Service — có bọc pagination không?
- [ ] Test end-to-end với `docker compose up --build`
- [ ] Đảm bảo `examTitle` được hiển thị đúng trong `StudentDashboard` lịch sử (cần examMap)
- [ ] Xử lý edge case: học sinh truy cập `/student/exam/:examId` với bài thi đã nộp → redirect về result
- [ ] Frontend khi deploy Docker: đổi proxy sang gateway (`:8080/api/...`)
