# Demo TV4: Consumer, Gateway va kiem thu tich hop

## Dieu kien chay

Khoi dong Docker Desktop, sau do chay `docker compose up --build -d`. Kiem tra `docker compose ps`, `docker compose exec gateway nginx -t`, va `docker compose config --quiet` truoc khi demo.

## 1. Saga rollback

1. Dung `exam-service`.
2. Gui `POST /api/submissions/{submissionId}/submit` qua Gateway.
3. Xac nhan HTTP 503 va submission duoc tra lai `IN_PROGRESS`.
4. Khoi dong lai `exam-service`, sau do nop lai submission thanh cong.

## 2. Circuit Breaker (phu thuoc TV2)

1. Tao loi lien tiep tu `exam-service` theo nguong TV2 da cau hinh.
2. Xac nhan log hoac Actuator cua Submission Service hien `OPEN`, sau do `HALF_OPEN` va `CLOSED` khi service phuc hoi.
3. Ghi lai trang thai vao artifact benchmark. Neu TV2 chua merge Circuit Breaker, danh dau kich ban nay la blocker, khong dua ra ket qua gia lap.

## 3. Transactional Outbox

1. Dung RabbitMQ, nop mot bai, va kiem tra `outbox_events` co ban ghi `PENDING`.
2. Khoi dong RabbitMQ, cho relay chay.
3. Xac nhan ban ghi chuyen `SENT` va event duoc Statistics xu ly.

## 4. Consumer restart

1. Dung `statistics-service`.
2. Publish hoac tao event `EXAM_SUBMITTED` moi qua Submission Service.
3. Kiem tra message con trong queue `statistics.exam-submitted`.
4. Khoi dong lai Statistics va xac nhan event duoc ghi mot lan trong `statistics_db.processed_events`.

## 5. Idempotency va DLQ

1. Publish cung mot event hop le hai lan; `processed_events` phai chi co mot row.
2. Publish payload thieu `eventId` hoac co `eventVersion` khac `1`.
3. Xac nhan consumer thu toi da ba lan va message chuyen sang `statistics.exam-submitted.dlq`.

## 6. Gateway under load

1. Chay `gateway/tests/verify-gateway.ps1` de kiem tra JSON 429/502/504, header `Retry-After`, va khong retry POST khi timeout.
2. Chay `scripts/load-test/run-tv4-benchmark.ps1 -Profile both -ExamId <id>`.
3. Doi chieu p50/p95/p99, ty le response hop le, 429, Outbox, queue va processed events trong `docs/tv4-load-test-report.md`.
