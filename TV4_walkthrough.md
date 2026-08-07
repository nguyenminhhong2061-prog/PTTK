# Hướng dẫn phần Thành viên 4 — Event Consumer, API Gateway và Load Test

## 1. Các hạng mục đã triển khai

- Giới hạn tần suất tại Nginx cho `POST /api/submissions/{id}/submit`: duy trì 10 request/giây trên mỗi IP, cho phép burst 20 request.
- Gateway chờ kết nối đến upstream tối đa 5 giây; thời gian gửi và đọc response tối đa 10 giây.
- Response lỗi dạng JSON cho HTTP `429`, `502` và `504`. Response `429` có header `Retry-After: 1`.
- RabbitMQ consumer tiếp nhận event `EXAM_SUBMITTED`.
- Lưu trạng thái idempotency bền vững trong bảng `statistics_db.processed_events`.
- Thử xử lý message tối đa 3 lần, sau đó chuyển message lỗi sang `statistics.exam-submitted.dlq`.
- Kịch bản k6 cho tải ổn định và spike 200 người dùng đồng thời.

## 2. Event contract

Payload JSON thống nhất giữa Outbox publisher của TV3 và consumer của TV4:

```json
{
  "eventId": "023379d2-ced1-4aa5-a4f8-338235e31e14",
  "eventType": "EXAM_SUBMITTED",
  "eventVersion": 1,
  "occurredAt": "2026-07-23T10:00:00Z",
  "submissionId": "119c8bf4-e482-4fad-872d-2d145c32a6b4",
  "examId": 1,
  "studentId": "student-001",
  "score": 85.0
}
```

| Tài nguyên | Giá trị mặc định |
|---|---|
| Exchange | `quiz.events` |
| Queue | `statistics.exam-submitted` |
| Routing key | `exam.submitted` |
| Dead Letter Queue | `statistics.exam-submitted.dlq` |

Outbox Relay của TV3 phải publish JSON đúng contract trên. Tên exchange, queue, routing key và DLQ đều được cấu hình bằng biến môi trường, vì vậy có thể điều chỉnh khi tích hợp mà không cần sửa code.

## 3. Idempotency và xử lý lỗi

Consumer sử dụng câu lệnh MySQL `INSERT IGNORE` để claim event theo cách atomic. Hai trường `event_id` và `submission_id` đều có unique constraint:

- Kết quả insert bằng `1`: đây là event mới, consumer tiếp tục xử lý và commit transaction.
- Kết quả insert bằng `0`: event đã tồn tại, consumer ghi log duplicate, không xử lý lại và ACK message.
- Nếu bước xử lý sau khi claim bị lỗi, transaction sẽ rollback cả row vừa insert. RabbitMQ có thể gửi lại message để consumer thử lại.
- Event sai contract, không hỗ trợ hoặc vẫn thất bại sau tối đa 3 lần sẽ bị reject và chuyển vào DLQ.

Các REST API của Statistics Service vẫn tính báo cáo từ dữ liệu lấy qua Submission Service. `statistics_db` chỉ lưu trạng thái xử lý event, không sao chép toàn bộ dữ liệu nghiệp vụ của Submission Service.

## 4. Khởi động và kiểm tra hệ thống

Tạo file môi trường và khởi động các container:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

Các địa chỉ kiểm tra:

```text
Gateway health:       http://localhost:8080/health
Statistics health:    http://localhost:5003/health
RabbitMQ Management:  http://localhost:15672
```

Tài khoản RabbitMQ mặc định trong `.env.example`:

```text
Username: admin
Password: changeme
```

Kiểm tra Docker Compose và Nginx:

```powershell
docker compose config --quiet
docker compose exec gateway nginx -t
```

## 5. Chạy kiểm thử Statistics Service

Máy cần có Java 17 và Docker đang hoạt động. Do repository không có `mvnw.cmd`, chạy Maven Wrapper bằng Git Bash:

```powershell
cd D:\PROJECT\PTTK\services\statistics-service
& "C:\Program Files\Git\bin\bash.exe" ./mvnw test
```

Các test bao gồm:

- Event hợp lệ được claim và xử lý.
- Event trùng không được xử lý lần hai.
- Event thiếu trường bắt buộc bị reject.
- Event version không hỗ trợ bị reject.
- Điểm ngoài khoảng `0–100` bị reject.
- Unique constraint trên `event_id` và `submission_id` được kiểm tra bằng MySQL Testcontainer.

## 6. Kiểm tra consumer thủ công

Mở RabbitMQ Management tại `http://localhost:15672`, sau đó:

1. Kiểm tra hai queue `statistics.exam-submitted` và `statistics.exam-submitted.dlq` đã được tạo.
2. Mở exchange `quiz.events`.
3. Chọn chức năng publish message.
4. Nhập routing key `exam.submitted`.
5. Publish payload JSON đúng contract tại mục 2.

Theo dõi consumer:

```powershell
docker compose logs --tail=100 statistics-service
```

Kiểm tra dữ liệu đã ghi:

```powershell
docker compose exec statistics-db mysql `
  -uroot `
  -pchangeme `
  statistics_db `
  -e "SELECT * FROM processed_events;"
```

Publish lại chính event đó. Kết quả mong đợi:

- Log Statistics thông báo event duplicate.
- Bảng `processed_events` vẫn chỉ có một row tương ứng.

Để kiểm tra DLQ, publish một payload thiếu `eventId` hoặc có `eventVersion` khác `1`. Sau khi hết retry, message phải xuất hiện trong `statistics.exam-submitted.dlq`.

## 7. Chạy load test

### 7.1. Chuẩn bị dữ liệu

Hệ thống phải có ít nhất một bài thi ở trạng thái `PUBLISHED`. Tạo 200 submission mới trước mỗi lần test:

```powershell
cd D:\PROJECT\PTTK
$env:BASE_URL = "http://localhost:8080"
$env:EXAM_ID = "1"
$env:STUDENT_COUNT = "200"
node scripts/load-test/prepare-data.js
```

Nếu không khai báo `EXAM_ID`, script tự chọn bài thi `PUBLISHED` đầu tiên.

Danh sách submission được ghi vào:

```text
scripts/load-test/fixtures/submissions.json
```

Một submission đã nộp thành công không thể dùng lại. Vì vậy phải chạy lại `prepare-data.js` trước mỗi lượt load test mới.

### 7.2. Kịch bản tải ổn định

Kịch bản này gửi tối đa 10 request/giây để đo latency khi tải nằm trong giới hạn:

```powershell
$env:SCENARIO = "steady"
k6 run scripts/load-test/submit-load-test.js
```

### 7.3. Kịch bản spike 200 người dùng

Tạo dữ liệu mới rồi chạy:

```powershell
node scripts/load-test/prepare-data.js
$env:SCENARIO = "spike"
k6 run scripts/load-test/submit-load-test.js
```

Kết quả được ghi vào `summary.json`. Trong spike test, HTTP `429` là kết quả mong đợi của rate limiter, không phải backend bị lỗi.

Script phân loại riêng:

- Submit thành công (`200`).
- Submission conflict (`409`).
- Bị giới hạn tần suất (`429`).
- Service không khả dụng (`503`).
- Gateway timeout (`504`).
- Response không mong đợi.

Khi gặp `504`, script chỉ gọi API GET để kiểm tra trạng thái submission, không tự động gửi lại POST vì backend có thể đã commit kết quả.

## 8. Thu thập số liệu cho báo cáo

k6 cung cấp:

- Tổng số request và request/giây.
- Phân bố HTTP status.
- Độ trễ p50, p95 và p99.
- Số request thành công, bị throttle hoặc gặp lỗi.

Các số liệu khác phải thu thập riêng:

| Số liệu | Nguồn |
|---|---|
| Trạng thái Circuit Breaker | Submission Actuator và log |
| Outbox `PENDING/SENT` | `submission_db.outbox_events` |
| Số message đang chờ | RabbitMQ Management |
| Event đã xử lý | `statistics_db.processed_events` |
| Event duplicate | Log Statistics Service |

Không mô tả các số liệu Circuit Breaker, Outbox hoặc database là do k6 tự động thu thập.

## 9. Kịch bản kiểm thử tích hợp cuối cùng

### Kịch bản 1 — Saga rollback

1. Dừng Exam Service.
2. Gửi request submit.
3. Xác nhận API trả `503`.
4. Kiểm tra submission được compensation về `IN_PROGRESS`.

### Kịch bản 2 — Circuit Breaker

1. Làm Exam Service lỗi hoặc phản hồi chậm liên tiếp.
2. Gửi tối thiểu 5 request để đạt ngưỡng tính lỗi.
3. Quan sát Circuit Breaker chuyển từ `CLOSED` sang `OPEN`.
4. Sau 30 giây, quan sát trạng thái `HALF_OPEN`.

### Kịch bản 3 — Transactional Outbox

1. Dừng RabbitMQ.
2. Nộp bài.
3. Kiểm tra Outbox Event vẫn ở trạng thái `PENDING`.
4. Khởi động RabbitMQ.
5. Kiểm tra relay publish lại event và chuyển trạng thái sang `SENT`.

### Kịch bản 4 — Statistics Service ngừng hoạt động

1. Dừng Statistics Service.
2. Publish các event mới.
3. Xác nhận message vẫn nằm trong queue.
4. Khởi động lại Statistics Service.
5. Xác nhận consumer xử lý các message tồn đọng.

### Kịch bản 5 — Idempotent Consumer

1. Publish cùng một event hai lần.
2. Kiểm tra chỉ có một row trong `processed_events`.
3. Kiểm tra log ghi nhận event duplicate.

### Kịch bản 6 — Gateway chịu tải

1. Chạy spike test với 200 virtual users.
2. Xác nhận Gateway trả một số response `429`.
3. Xác nhận Gateway và backend không crash.
4. Kiểm tra không có submission hoặc event bị xử lý trùng.

Các kịch bản liên quan đến Outbox chỉ có thể chạy đầy đủ sau khi TV3 merge producer và relay job.

## 10. Điều kiện hoàn thành phần TV4

- Maven test của Statistics Service pass.
- `docker compose config --quiet` và `nginx -t` pass.
- Gateway trả đúng `429`, `502` và `504` dạng JSON.
- Gateway không retry POST submit.
- Event mới được xử lý đúng một lần.
- Event trùng được ACK và bỏ qua.
- Poison message không lặp vô hạn mà được chuyển vào DLQ.
- Consumer nhận lại message tồn đọng sau khi restart.
- Steady test và spike test chạy lại được với dữ liệu mới.
- Outbox → RabbitMQ → Consumer chạy end-to-end sau khi tích hợp code TV3.
