# Hướng Dẫn Kiểm Tra & Demo — TV2 (Circuit Breaker Pattern)

> **Mục tiêu**: Hướng dẫn TV2 tự kiểm tra code và trình bày demo cho giáo viên trên môi trường Windows (Terminal là PowerShell).  
> **Thời lượng demo ước tính**: 10–15 phút.

---

## Phần 1: Kiểm Tra Nhanh Unit Test (Không Cần Docker — Chạy <30 giây)

Đây là bước đầu tiên để xác nhận logic Circuit Breaker hoạt động đúng **trước khi** mở Docker. Mở PowerShell trong VS Code và chạy:

```powershell
cd D:\PTIT\nam4hk2\HKTPM\PTTK\services\submission-service
..\..\..\mvnw test -Dtest=CircuitBreakerTest -q
```

**Kết quả mong đợi — 5 test đều PASS:**
```text
✅ Test 1 PASSED: Circuit vẫn CLOSED sau 1 lần gọi thành công
✅ Test 2 PASSED: Circuit OPEN sau 5 lần fail, request thứ 6 bị chặn trong Xms
✅ Test 3 PASSED: Retry tự phục hồi sau 1 lần thất bại (max-attempts=2)
✅ Test 4 PASSED: Fallback ném đúng ExamServiceUnavailableException
✅ Test 5 PASSED: Slow-call detection — Circuit OPEN sau 4 lần gọi chậm
```

> [!TIP]
> Sử dụng `..\..\..\mvnw` thay vì `mvn` để dùng Maven wrapper của dự án, không cần cài Maven riêng.

---

## Phần 2: Khởi Động Hệ Thống (Docker Compose)

### 2.1. Build và chạy

```powershell
cd D:\PTIT\nam4hk2\HKTPM\PTTK

# Lần đầu: build lại toàn bộ
docker compose up --build

# Lần sau (không cần build lại):
docker compose up
```

> [!WARNING]
> Lần đầu build mất 3–5 phút. Lần sau sẽ nhanh hơn nhiều.

### 2.2. Xác nhận tất cả service đang chạy

Mở 3 tab trong browser:

| Endpoint | URL | Kết quả mong đợi |
|---|---|---|
| Submission Health | http://localhost:5002/health | `{"status":"UP"}` |
| **Circuit Breaker State** | http://localhost:5002/circuitbreakers | State: `CLOSED` |
| Exam Health | http://localhost:5001/actuator/health | `{"status":"UP"}` |
| Frontend | http://localhost:3000 | Giao diện web |

### 2.3. Migration DB bắt buộc (chỉ lần đầu)

```powershell
docker exec pttk-submission-db-1 mysql -u root -pchangeme -e "USE submission_db; ALTER TABLE submissions MODIFY COLUMN status ENUM('IN_PROGRESS', 'GRADING', 'SUBMITTED') NOT NULL;"
```

> [!NOTE]
> Bỏ qua bước này nếu DB đã được migrate ở các lần trước.

---

## ⚠️ Bước 0 — Chuẩn Bị Submission ID (BẮT BUỘC Trước Khi Demo)

> [!IMPORTANT]
> **Đây là bước hay bị bỏ qua nhất.** Kịch Bản C (demo chính) yêu cầu một `submissionId` có `status = IN_PROGRESS`. Nếu không có, mọi request sẽ fail ngay ở bước validate (HTTP 503 trong <0.2s) — không bao giờ đến được Exam Service, Circuit Breaker không bao giờ được kích hoạt.

### Kiểm tra trước: Đã có IN_PROGRESS submission chưa?

```powershell
docker exec pttk-submission-db-1 mysql -u root -pchangeme -e "SELECT id, exam_id, student_id, status FROM submission_db.submissions WHERE status='IN_PROGRESS' LIMIT 5;"
```

- **Nếu có kết quả** → Copy `id` bất kỳ → đó là `$SID` cần dùng → **Chuyển thẳng sang Phần 3**.
- **Nếu không có kết quả (bảng trống)** → Làm theo bước dưới để tạo mới.

### Tạo IN_PROGRESS Submission Mới

**Bước A**: Đảm bảo Exam Service đang chạy (bật lại nếu cần):
```powershell
docker start pttk-exam-service-1
```
Chờ ~20 giây cho Spring Boot boot xong. Kiểm tra: http://localhost:5001/actuator/health phải trả `UP`.

**Bước B**: Lấy một `examId` hợp lệ (published) từ exam DB:
```powershell
docker exec pttk-exam-db-1 mysql -u root -pchangeme -e "SELECT id, title, status FROM exam_db.exams WHERE status='published' LIMIT 5;"
```
Copy `id` (ví dụ: `3`) từ kết quả — đó là `EXAM_ID` cần dùng ở bước C.

> [!NOTE]
> Nếu bảng exams trống hoặc không có exam nào status `published`, hãy vào http://localhost:3000 → tạo bài thi mới qua giao diện → publish nó → rồi quay lại chạy lệnh trên.

**Bước C**: Tạo submission mới qua API (thay `EXAM_ID` bằng số lấy ở bước B):
```powershell
$result = Invoke-RestMethod `
  -Uri "http://localhost:5002/submissions" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"examId": EXAM_ID, "studentId": "demo-student-01"}'

# In submissionId ra màn hình
$SID = $result.data.submissionId
Write-Host "✅ Submission ID: $SID"
```

**Bước D**: Xác nhận submission đã tạo thành công:
```powershell
docker exec pttk-submission-db-1 mysql -u root -pchangeme -e "SELECT id, status FROM submission_db.submissions WHERE status='IN_PROGRESS' LIMIT 3;"
```

Bạn sẽ thấy submission mới với `status = IN_PROGRESS`.

> [!TIP]
> **Tại sao chỉ cần 1 submission ID cho cả 8 request?**
> Saga có bước compensation: khi Exam Service down và Step 3 thất bại, Orchestrator tự rollback bài về `IN_PROGRESS`. Nghĩa là sau mỗi lần request fail, bài thi tự "reset" về trạng thái ban đầu — request tiếp theo vẫn pass được bước validate.

---

## Phần 3: Kịch Bản Demo Cho Giáo Viên

> Cần mở **2 cửa sổ PowerShell** và **2 tab browser**. Chuẩn bị trước để demo mượt.

### 🎯 Demo Setup

**PowerShell 1** — Xem log Circuit Breaker realtime:
```powershell
docker logs -f pttk-submission-service-1 | Select-String -Pattern "\[Circuit|Saga|OPEN|CLOSED|HALF"
```

**PowerShell 2** — Dùng để chạy các lệnh request.

**Browser Tab 1** — Mở sẵn: http://localhost:5002/circuitbreakers  
**Browser Tab 2** — Mở sẵn: http://localhost:3000 (Frontend)

---

### 🎬 Kịch Bản A — Trạng Thái Bình Thường (Circuit CLOSED)

> **Nói với giáo viên**: *"Khi Exam Service hoạt động bình thường, Circuit Breaker ở trạng thái CLOSED — mọi request đi thẳng qua."*

**Bước 1**: Đảm bảo Exam Service đang chạy (từ Bước 0).  
**Bước 2**: Kiểm tra Circuit Breaker tại http://localhost:5002/circuitbreakers:
```json
{
  "examService": {
    "state": "CLOSED",
    "failureRate": "-1.0%",
    "numberOfBufferedCalls": 0
  }
}
```

> [!NOTE]
> `failureRate: "-1.0%"` là bình thường — Resilience4j hiển thị `-1` khi chưa đủ `minimum-number-of-calls` (5 lần) để tính tỷ lệ. Không phải lỗi.

**Bước 3**: Để học sinh nộp bài thật hoặc dùng API với `$SID` từ Bước 0:
```powershell
Invoke-RestMethod -Uri "http://localhost:5002/submissions/$SID/submit" -Method Post -ContentType "application/json" -Body '{"answers": []}'
```

**Bước 4**: F5 lại http://localhost:5002/circuitbreakers → thấy `numberOfBufferedCalls` tăng lên, `state` vẫn `CLOSED`.

---

### 🎬 Kịch Bản B — Retry Pattern (Chứng Minh Qua Unit Test)

> **Nói với giáo viên**: *"Khi Exam Service có lỗi thoáng qua (1 lần fail rồi tự recover), Retry pattern tự động thử lại — user không nhận thấy lỗi. Mình chứng minh bằng unit test vì kịch bản này khó inject lỗi tạm thời vào giữa luồng live."*

> [!NOTE]
> Không dùng `docker restart` để demo Retry vì Spring Boot mất 15–30s boot lại — trong thời gian đó Exam Service down hoàn toàn, Circuit Breaker sẽ OPEN ngay, che mất kịch bản Retry.

**Bước 1**: Chạy unit Test 3 riêng lẻ và giải thích output:
```powershell
cd D:\PTIT\nam4hk2\HKTPM\PTTK\services\submission-service
..\..\..\mvnw test -Dtest=CircuitBreakerTest#testRetry_SucceedsOnSecondAttempt -q
```

**Output mong đợi:**
```text
✅ Test 3 PASSED: Retry tự phục hồi sau 1 lần thất bại. Kết quả: Thành công ở lần 2 (tổng 2 lần gọi, đúng với max-attempts=2)
```

**Giải thích với giáo viên:**
- `max-attempts=2` = 1 lần gọi đầu + 1 lần retry (tổng 2 lần)
- Test giả lập: lần 1 fail, lần 2 thành công → kết quả vẫn trả về OK cho user
- Trong production: Exam Service tạm thời lỗi rồi recover, lần retry sẽ thành công
- Retry có `wait-duration=1s` (exponential backoff) để không spam request ngay lập tức

---

### 🎬 Kịch Bản C — Exam Service Down (Circuit OPEN — Kịch Bản Chính)

> **Nói với giáo viên**: *"Đây là kịch bản cốt lõi — 200 học sinh nộp bài đồng thời, Exam Service bị down. Thay vì 200 thread mỗi thread chờ đến 11s rồi chết theo timeout, thì sau 5 lần fail, Circuit lập tức OPEN, các request sau trả lỗi ngay trong <10ms — cứu toàn bộ thread pool."*

**Bước chuẩn bị**: Lấy `$SID` từ Bước 0. Nếu chưa có, làm Bước 0 trước.

**Bước 1**: Tạo thêm submission dự phòng (vì sau khi Circuit OPEN + Exam lên lại, cần submission mới để demo Kịch Bản D):
```powershell
# Lưu ý: Nếu $SID đã set từ Bước 0 thì bỏ qua lệnh này
# Chỉ chạy nếu cần tạo thêm submission
```

**Bước 2**: **Tắt hẳn Exam Service**:
```powershell
docker stop pttk-exam-service-1
```

**Bước 3**: Dán và chạy script này trong **PowerShell 2** (thay `PASTE_SID_HERE` bằng ID thật):
```powershell
$SID = "PASTE_SID_HERE"

1..8 | ForEach-Object {
    Write-Host "=== Request $_ ==="
    $time = Measure-Command {
        try {
            $response = Invoke-WebRequest `
              -Uri "http://localhost:5002/submissions/$SID/submit" `
              -Method Post `
              -ContentType "application/json" `
              -Body '{"answers": []}' `
              -UseBasicParsing
            Write-Host "HTTP $($response.StatusCode) -" -NoNewline
        } catch {
            if ($_.Exception.Response) {
                Write-Host "HTTP $($_.Exception.Response.StatusCode.value__) -" -NoNewline
            } else {
                Write-Host "Error -" -NoNewline
            }
        }
    }
    Write-Host " $([math]::Round($time.TotalSeconds, 2))s`n"
}
```

**Bước 4**: Quan sát output — đây là điểm "Wow" nhất:
```text
=== Request 1 === HTTP 503 - ~9.1s   ← connection refused (Exam down), retry 1 lần, backoff
=== Request 2 === HTTP 503 - ~5.1s   ← chỉ 1 lần timeout (lần 2 retry bị chặn bởi CB vừa OPEN)
                 ↑ Sau Request 2: minimum-number-of-calls=5? Không — xem Note bên dưới
=== Request 3 === HTTP 503 - ~1.0s   ← Circuit OPEN → CallNotPermittedException → Retry wait 1s → fallback
=== Request 4 === HTTP 503 - ~1.0s   ← tương tự
=== Request 5 === HTTP 503 - ~1.0s
=== Request 6 === HTTP 503 - ~1.0s
=== Request 7 === HTTP 503 - ~1.0s
=== Request 8 === HTTP 503 - ~1.0s
```

> [!NOTE]
> **Giải thích thực tế (quan trọng khi giáo viên hỏi):**
> - Request 1 chậm ~9s vì: Exam Service vừa stop → connection refused nhanh hơn timeout 5s → Retry thử lần 2 → cũng refused → fallback.
> - Request 2-3 là đủ để Circuit OPEN (connection refused tích lũy vào sliding window).
> - Từ request 3 trở đi mất ~1s vì: Circuit OPEN → Retry nhận `CallNotPermittedException` → đây là `ignore-exceptions` nên không retry, nhưng `wait-duration=1s` của Retry vẫn áp dụng trước khi rơi vào fallback.
> - **Điểm "Wow" vẫn còn**: request 1 ~9s → từ request 3 chỉ ~1s. Thread không bị chặn 11s nữa — giảm 89% thời gian chờ mỗi request.

**Bước 5**: F5 lại http://localhost:5002/circuitbreakers — thấy rõ:
```json
{
  "examService": {
    "state": "OPEN",
    "failureRate": "100.0%",
    "numberOfBufferedCalls": 5,
    "numberOfFailedCalls": 5,
    "notPermittedNumberOfCalls": 3
  }
}
```

**Bước 6**: Xem DB — chứng minh Saga compensation hoạt động đúng (bài không kẹt ở `GRADING`):
```powershell
docker exec pttk-submission-db-1 mysql -u root -pchangeme -e "SELECT id, status, score FROM submission_db.submissions ORDER BY id DESC LIMIT 5;"
```
*(Bạn sẽ thấy submission vẫn ở `IN_PROGRESS` — đã rollback thành công, không kẹt ở `GRADING`)*

---

### 🎬 Kịch Bản D — Circuit HALF-OPEN → CLOSED (Tự Phục Hồi)

> **Nói với giáo viên**: *"Sau 30 giây, Circuit tự chuyển sang HALF-OPEN — cho 3 request thử nghiệm đi qua. Nếu Exam Service đã phục hồi, Circuit đóng lại hoàn toàn."*

**Bước 1**: Tạo submission mới cho Kịch Bản D (submission cũ đã bị rollback về IN_PROGRESS — có thể dùng lại hoặc tạo mới):
```powershell
# Kiểm tra xem $SID cũ còn dùng được không
docker exec pttk-submission-db-1 mysql -u root -pchangeme -e "SELECT id, status FROM submission_db.submissions WHERE status='IN_PROGRESS' LIMIT 3;"
```

**Bước 2**: Bật lại Exam Service:
```powershell
docker start pttk-exam-service-1
```

**Bước 3**: Chờ 30 giây để Circuit tự chuyển sang `HALF-OPEN` (config: `wait-duration-in-open-state=30s`).

> [!TIP]
> Trong lúc chờ, F5 liên tục http://localhost:5002/circuitbreakers để thấy trạng thái chuyển từ `OPEN` → `HALF_OPEN`.

**Bước 4**: Sau khi thấy `HALF_OPEN`, chạy lại lệnh nộp bài với `$SID` còn `IN_PROGRESS`:
```powershell
Invoke-RestMethod -Uri "http://localhost:5002/submissions/$SID/submit" -Method Post -ContentType "application/json" -Body '{"answers": []}'
```

**Bước 5**: F5 lại http://localhost:5002/circuitbreakers → trạng thái về `CLOSED`.

---

## Phần 4: Câu Hỏi Giáo Viên Hay Hỏi

| Câu hỏi | Trả lời gọn |
|---|---|
| "Tại sao dùng Circuit Breaker?" | 200 thread nộp bài đồng thời, Exam down → không CB thì 200 thread chờ timeout 11s mỗi cái → thread pool cạn → cả hệ thống treo. CB OPEN sau 5 lần → request sau trả lỗi <10ms, giải phóng thread ngay. |
| "CLOSED → OPEN cần bao nhiêu lần fail?" | Ít nhất 5 lần (minimum-number-of-calls=5), và fail rate đạt 50% trong cửa sổ 10 request. |
| "Sau khi OPEN bao lâu thì tự phục hồi?" | 30 giây → HALF-OPEN → cho 3 request thử → nếu OK thì CLOSED. |
| "Retry và Circuit Breaker khác nhau thế nào?" | Retry xử lý lỗi thoáng qua (1-2 lần fail rồi tự recover). CB xử lý khi service down hẳn — thay vì cứ retry vô ích, CB ngắt luôn sau ngưỡng. |
| "Tại sao fallback ném exception thay vì trả null?" | Saga Orchestrator (TV1) cần bắt exception để chạy compensation, rollback bài về IN_PROGRESS. Trả null → Orchestrator không biết có lỗi → bài kẹt ở GRADING mãi. |
| "Slow-call detection là gì?" | Nếu Exam Service không down hẳn mà chỉ chậm (response 4-5s mỗi lần) nhưng không ném exception — failure rate threshold không phát hiện được. Slow-call config giải quyết: nếu >50% call mất hơn 4s thì CB cũng OPEN. |

---

## Phần 5: Tip Lấy Submission ID Nhanh

```powershell
# Xem tất cả submission hiện có
docker exec pttk-submission-db-1 mysql -u root -pchangeme -e "SELECT id, student_id, exam_id, status FROM submission_db.submissions ORDER BY id DESC LIMIT 10;"

# Chỉ lấy IN_PROGRESS (dùng được cho demo)
docker exec pttk-submission-db-1 mysql -u root -pchangeme -e "SELECT id FROM submission_db.submissions WHERE status='IN_PROGRESS' LIMIT 1;"

# Xem exam IDs có sẵn (để tạo submission mới)
docker exec pttk-exam-db-1 mysql -u root -pchangeme -e "SELECT id, title, status FROM exam_db.exams LIMIT 10;"
```

**Lệnh tạo submission 1 dòng** (thay `1` bằng examId thật):
```powershell
$SID = (Invoke-RestMethod -Uri "http://localhost:5002/submissions" -Method Post -ContentType "application/json" -Body '{"examId": 1, "studentId": "demo-sv"}').data.submissionId; Write-Host "SID = $SID"
```
