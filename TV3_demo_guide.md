# Demo Guide — TV3: Outbox Pattern + Event-Driven

Hướng dẫn chạy demo cho giáo viên xem 3 kịch bản: **Happy path**, **RabbitMQ down**, **Statistics down**.

Script sử dụng: `scripts/tv3-seed-demo-data.sh`

> **Bản này đã cập nhật sau khi test thực tế** — có 3 chỗ cần sửa trong script/code trước khi demo. Xem mục 0.2 bên dưới, làm 1 lần duy nhất, xong thì demo ổn định lâu dài.

---

## 0. Chuẩn bị trước buổi demo (làm trước, không làm trực tiếp trước mặt giáo viên)

### 0.1. Khởi động toàn bộ hệ thống

```bash
cd /path/to/repo   # thư mục gốc, nơi có docker-compose.yml
docker compose up --build -d
```

Đợi tất cả service healthy:

```bash
docker compose ps
```

Tất cả các dòng `*-db` phải hiện `(healthy)`, các service backend phải `Up`.

### 0.2. Áp dụng 3 bản vá bắt buộc (chỉ cần làm 1 lần)

Các lỗi này đã được phát hiện qua test thực tế và **phải sửa trước khi demo**, nếu không kịch bản 2 và 3 có thể chạy sai ngay trước mặt giáo viên.

#### Vá #1 — `scripts/tv3-seed-demo-data.sh`: log lẫn vào giá trị biến

Các hàm `log()`, `ok()`, `warn()` đang in ra stdout, khiến chúng bị command substitution (`$(...)`) bắt nhầm vào giá trị `submission_id`. Tìm đoạn:

```bash
log()  { echo -e "\n\033[1;36m▶ $*\033[0m"; }
ok()   { echo -e "\033[1;32m  ✓ $*\033[0m"; }
warn() { echo -e "\033[1;33m  ⚠ $*\033[0m"; }
die()  { echo -e "\033[1;31m✗ $*\033[0m" >&2; exit 1; }
```

Sửa thành (thêm `>&2` vào 3 hàm đầu):

```bash
log()  { echo -e "\n\033[1;36m▶ $*\033[0m" >&2; }
ok()   { echo -e "\033[1;32m  ✓ $*\033[0m" >&2; }
warn() { echo -e "\033[1;33m  ⚠ $*\033[0m" >&2; }
die()  { echo -e "\033[1;31m✗ $*\033[0m" >&2; exit 1; }
```

#### Vá #2 — `scripts/tv3-seed-demo-data.sh`: mật khẩu MySQL không khớp `.env`

Script lấy `DB_PASSWORD` từ biến môi trường shell, nhưng Docker Compose lại lấy từ file `.env`. Nếu không export tay, script mặc định `changeme`, có thể sai với mật khẩu thật → lỗi `Access denied`.

Thêm đoạn sau ngay sau `set -euo pipefail`, và **xoá** phần khai báo `SCRIPT_DIR`/`PROJECT_DIR` bị lặp lại ở phía dưới (giữ nguyên dòng `cd "$PROJECT_DIR"`):

```bash
set -euo pipefail

# ─── Tự động load .env nếu có, để DB_PASSWORD khớp với giá trị thật MySQL đang dùng ─
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
if [ -f "${PROJECT_DIR}/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "${PROJECT_DIR}/.env"
  set +a
fi
```

#### Vá #3 — `OutboxRelayJob.java`: giới hạn retry quá thấp

File: `services/submission-service/src/main/java/.../job/OutboxRelayJob.java`

RabbitMQ thường mất 8–15 giây để thực sự sẵn sàng nhận kết nối sau khi container khởi động lại (không phải chỉ vài trăm ms như Docker báo `Started`). Với `MAX_RETRY_COUNT = 5` và job chạy mỗi 2 giây, cửa sổ retry chỉ ~10 giây — không đủ, khiến event bị đánh dấu `FAILED` vĩnh viễn trước khi RabbitMQ kịp hồi phục.

Sửa:

```java
private static final int MAX_RETRY_COUNT = 5;
```

thành:

```java
private static final int MAX_RETRY_COUNT = 30; // 30 × 2s = 60s cửa sổ retry, đủ dư cho RabbitMQ restart
```

Rebuild lại service này sau khi sửa:

```bash
docker compose up --build -d submission-service
docker compose ps submission-service   # đợi tới khi Up/healthy
```

> Chỉ rebuild `submission-service`, các service/container khác không bị ảnh hưởng, dữ liệu demo đã seed trước đó không mất.

### 0.3. Mở sẵn 3 cửa sổ / tab terminal (để giáo viên nhìn thấy real-time)

| Cửa sổ | Nội dung | Lệnh |
|---|---|---|
| Terminal A | Chạy script demo | (dùng ở bước dưới) |
| Terminal B | Theo dõi log statistics-service live | `docker compose logs -f statistics-service` |
| Terminal C | Theo dõi trạng thái queue RabbitMQ real-time | `watch -n 1 'docker compose exec -T rabbitmq rabbitmqctl list_queues name messages messages_ready consumers'` |

> **Không dùng RabbitMQ Management UI (`localhost:15672`) để đo số liệu lúc demo** — số liệu ở đó (messages/messages_ready/consumers) được cache và chỉ refresh mỗi ~5 giây, nên có thể hiển thị sai lệch ngay tại thời điểm bạn vừa dừng/khởi động service. Dùng `rabbitmqctl` (Terminal C ở trên) để có số liệu tức thời, chính xác theo giây — đây chính là công cụ script bản cập nhật đang dùng để kiểm tra kịch bản 3.
>
> RabbitMQ UI vẫn hữu ích để xem trực quan tổng thể (danh sách queue, exchange...), chỉ không nên dùng để "bắt" đúng khoảnh khắc trạng thái thay đổi.

### 0.4. Kiểm tra quyền chạy script

```bash
chmod +x scripts/tv3-seed-demo-data.sh
```

### 0.5. Chạy thử toàn bộ 3 kịch bản trước ít nhất 1 lần

```bash
bash scripts/tv3-seed-demo-data.sh scenario1
bash scripts/tv3-seed-demo-data.sh scenario2
bash scripts/tv3-seed-demo-data.sh scenario3
```

Nếu cả 3 lệnh chạy xong không có dòng `⚠`, bạn có thể yên tâm demo trực tiếp với timing đã canh sẵn.

> Script tự chờ (`wait_for`) Gateway/Exam/Submission/Statistics sẵn sàng trước khi seed, và tự thoát báo lỗi rõ ràng nếu service nào không phản hồi sau 60s.

---

## 1. Kịch bản 1 — Happy path

**Mục tiêu cho giáo viên thấy:** nộp bài → event được ghi vào outbox → tự động publish qua RabbitMQ → Statistics nhận và xử lý, toàn bộ trong chưa tới 1 giây, không cần polling hay gọi trực tiếp giữa các service.

### Chạy

```bash
bash scripts/tv3-seed-demo-data.sh scenario1
```

### Những gì diễn ra và nên giải thích lúc demo

1. Script tạo 5 câu hỏi, 1 bài thi đã publish, 1 phiên làm bài đã lưu đáp án (`in_progress`).
2. Script gọi `POST /submissions/{id}/submit` — đây là hành động "nộp bài" thật sự, trả về điểm ngay lập tức.
3. Script query trực tiếp bảng `outbox_events` trong `submission-db` và in ra:
   ```
   id | event_type | status | retry_count
   ```
   → Chỉ vào cột `status` (kỳ vọng: `SENT`, `retry_count = 0`).
4. Script `grep` log của `statistics-service` theo `submissionId` để chứng minh Statistics Service đã thực sự nhận được event — thực tế đo được thời gian xử lý chưa tới 1 giây kể từ lúc nộp bài.

### Điểm nhấn khi thuyết trình

- Nói rõ: **transaction lưu điểm và transaction ghi outbox_events là CÙNG MỘT transaction DB** — publish sang RabbitMQ xảy ra SAU, độc lập (đây chính là Outbox Pattern).
- Chỉ vào Terminal B (log statistics-service live) để giáo viên thấy dòng log xuất hiện gần như ngay lập tức sau khi nộp bài.

---

## 2. Kịch bản 2 — RabbitMQ down khi nộp bài

**Mục tiêu cho giáo viên thấy:** hệ thống không mất dữ liệu / không mất event dù broker chết — đây là điểm mạnh cốt lõi của Outbox Pattern so với publish trực tiếp.

### Chạy

```bash
bash scripts/tv3-seed-demo-data.sh scenario2
```

### Những gì diễn ra và nên giải thích lúc demo

1. Script chủ động `docker compose stop rabbitmq`.
2. Script nộp bài **trong lúc RabbitMQ đang chết**. Nộp bài vẫn **thành công bình thường** (HTTP 200) — nhấn mạnh điểm này, vì học sinh hoàn toàn không biết/không bị ảnh hưởng bởi sự cố hạ tầng.
3. Query `outbox_events` lần 1 → kỳ vọng `status = PENDING` — event đã được ghi an toàn vào DB, chỉ chưa publish được.
4. Script khởi động lại RabbitMQ, sau đó **chờ đến khi `status` chuyển sang `SENT`** (thay vì chờ cố định), tối đa 30 giây.
5. Query `outbox_events` lần 2 → kỳ vọng `status` đã tự chuyển thành `SENT` **mà không cần ai can thiệp thủ công** — đây là `OutboxRelayJob` chạy nền, tự động quét và publish lại các event đang `PENDING` mỗi 2 giây.

### Điểm nhấn khi thuyết trình

- So sánh với cách làm "publish trực tiếp không qua outbox": nếu publish trực tiếp mà MQ down ngay lúc gọi, event sẽ **mất vĩnh viễn**. Với Outbox, event đã an toàn trong DB cùng transaction lưu điểm, nên không thể mất.
- Có thể nói thêm về thực nghiệm: RabbitMQ (bản kèm plugin quản trị) cần khoảng **8 giây** để thực sự mở lại cổng kết nối AMQP sau khi container khởi động — đây là lý do hệ thống cần có cơ chế **retry với cửa sổ đủ rộng** (job đã được cấu hình thử lại tối đa trong khoảng 1 phút) thay vì chỉ thử vài lần rồi bỏ cuộc.

---

## 3. Kịch bản 3 — Statistics Service down khi event được publish

**Mục tiêu cho giáo viên thấy:** RabbitMQ đóng vai trò buffer bền vững (durable queue) — dù consumer (Statistics) chết, message không mất, chỉ chờ trong queue đến khi consumer sống lại.

### Chạy

```bash
bash scripts/tv3-seed-demo-data.sh scenario3
```

### Những gì diễn ra và nên giải thích lúc demo

1. Script `docker compose stop statistics-service`.
2. Nộp bài trong lúc Statistics down. Vì outbox → RabbitMQ không phụ thuộc Statistics đang sống hay không, event vẫn publish thành công.
3. Query `outbox_events` → kỳ vọng `status = SENT` **ngay cả khi Statistics đang chết** — vì trách nhiệm của Submission Service chỉ dừng lại ở việc đẩy event vào RabbitMQ thành công.
4. Script dùng `rabbitmqctl list_queues` (đọc trực tiếp từ node RabbitMQ, số liệu tức thời — không bị trễ như Management UI) để in trạng thái queue `statistics.exam-submitted`:
   ```
   name                        messages   messages_ready   consumers
   statistics.exam-submitted   1          1                0
   ```
   → Chỉ cho giáo viên thấy `messages_ready = 1` và `consumers = 0` (không có ai đang lắng nghe) — message đang "kẹt lại" chờ trong queue, an toàn.
5. Script khởi động lại `statistics-service`, **chờ đến khi service thực sự healthy** (poll `/health`, không chờ cố định), rồi đợi thêm vài giây cho consumer kịp tiêu thụ.
6. In lại `rabbitmqctl list_queues` lần 2 → kỳ vọng `messages_ready = 0`, `consumers = 1` — message đã được tiêu thụ hết.
7. Log `statistics-service` được grep theo `submissionId` để chứng minh service đã tự động nhận lại và xử lý message tồn đọng ngay khi sống lại — không cần nộp bài lại, không cần gọi tay.

### Điểm nhấn khi thuyết trình

- Cho giáo viên xem trực tiếp 2 lần in `rabbitmqctl list_queues` (trước/sau) cạnh nhau — đây là bằng chứng trực quan và thuyết phục nhất của cả buổi demo: `messages_ready` từ `1` về `0`, `consumers` từ `0` lên `1`.
- Đây là điểm khác biệt với kịch bản 2: ở kịch bản 2, "chỗ chờ an toàn" là bảng `outbox_events` trong DB; ở kịch bản 3, "chỗ chờ an toàn" là chính RabbitMQ queue (durable).
- Nhấn mạnh: Submission Service khởi động lại nhanh (broker), nhưng Statistics Service (ứng dụng Spring Boot đầy đủ) cần thời gian boot lâu hơn — nên script chủ động chờ health check thay vì đoán số giây cố định, phản ánh đúng cách vận hành thực tế nên làm trong production.

---

## 4. (Tuỳ chọn) Xem trực tiếp bảng outbox_events bằng MySQL client

Nếu muốn giáo viên tự mắt thấy dữ liệu thay vì chỉ đọc output script, mở thêm 1 terminal:

```bash
docker compose exec -T submission-db mysql -uroot -p"$DB_PASSWORD" submission_db \
  -e "SELECT id, event_type, status, retry_count, created_at FROM outbox_events ORDER BY created_at DESC LIMIT 10;"
```

Có thể chạy lại lệnh này (hoặc dùng `watch`) trước/trong/sau mỗi kịch bản để thấy `status` đổi theo thời gian thực:

```bash
watch -n 1 'docker compose exec -T submission-db mysql -uroot -p"'"$DB_PASSWORD"'" submission_db -e "SELECT id, event_type, status, retry_count FROM outbox_events ORDER BY created_at DESC LIMIT 5;"'
```

---

## 5. Chạy tuần tự cả 3 kịch bản (nếu muốn demo liền mạch, không dừng lại giải thích)

```bash
bash scripts/tv3-seed-demo-data.sh all
```

Lưu ý: chế độ `all` chạy liên tiếp 3 kịch bản không dừng — chỉ nên dùng khi đã demo thử trước và tự tin về timing, hoặc dùng để quay video demo. Khi thuyết trình trực tiếp, nên chạy **từng kịch bản riêng lẻ** (`scenario1`, `scenario2`, `scenario3`) để có thời gian dừng lại giải thích từng bước cho giáo viên.

---

## 6. Dọn dẹp sau demo (tuỳ chọn)

Không bắt buộc — dữ liệu demo (`TV3 Demo - scenarioX - <timestamp>`) không ảnh hưởng đến dữ liệu khác vì mỗi lần chạy dùng `RUN_TAG` là timestamp riêng. Nếu muốn dọn:

```bash
docker compose down       # giữ lại volume dữ liệu
# hoặc
docker compose down -v    # xoá luôn dữ liệu DB, dùng khi muốn demo lại từ đầu sạch sẽ
```

---

## 7. Checklist nhanh trước khi bắt đầu demo thật

- [ ] Đã áp dụng cả 3 bản vá ở mục 0.2 (log stderr, load `.env`, `MAX_RETRY_COUNT = 30`) và rebuild `submission-service`
- [ ] `docker compose ps` — tất cả service `Up` / `(healthy)`
- [ ] Đã chạy thử cả 3 kịch bản một lượt, không còn dòng `⚠`
- [ ] Terminal B đang `logs -f statistics-service` sẵn sàng
- [ ] Terminal C đang `watch rabbitmqctl list_queues` sẵn sàng (không dùng RabbitMQ Management UI để đo lúc demo)
- [ ] Đã canh đúng timing thực tế: kịch bản 1 (~1s), kịch bản 2 (RabbitMQ cần ~8-10s để hồi phục), kịch bản 3 (Statistics cần chờ health, thường ~5-10s)