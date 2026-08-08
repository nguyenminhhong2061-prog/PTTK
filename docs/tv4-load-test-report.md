# Bao cao tai va tich hop TV4

## Moi truong chay

| Truong | Gia tri thuc te |
|---|---|
| Ngay/chay luc | _dien sau khi chay_ |
| Commit | _dien sau khi chay_ |
| Docker / CPU / RAM | _dien sau khi chay_ |
| Exam ID | _dien sau khi chay_ |
| k6 version | _dien sau khi chay_ |

Lenh tai lap:

```powershell
.\gateway\tests\verify-gateway.ps1
.\scripts\load-test\run-tv4-benchmark.ps1 -Profile both -ExamId <published-exam-id>
```

Artifact JSON va evidence he thong nam trong `scripts/load-test/results/` va duoc tao moi cho tung lan chay.

## Ket qua Gateway

| Kiem tra | Ket qua | Bang chung |
|---|---|---|
| `nginx -t` | _pass/fail_ | _log_ |
| 429 JSON va `Retry-After: 1` | _pass/fail_ | _gateway probe_ |
| 502 JSON | _pass/fail_ | _gateway probe_ |
| 504 JSON, mot upstream request | _pass/fail_ | _gateway probe_ |
| POST khong tu retry | _pass/fail_ | _request-count_ |

## So sanh k6

| Profile | Scenario | Requests | Response hop le | 429 | 503 | 504 | p50 | p95 | p99 | Ket luan |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Baseline | Steady 10 rps | _ | _ | _ | _ | _ | _ | _ | _ | _ |
| Protected | Steady 10 rps | _ | _ | _ | _ | _ | _ | _ | _ | _ |
| Baseline | Spike 200 VU | _ | _ | _ | _ | _ | _ | _ | _ | _ |
| Protected | Spike 200 VU | _ | _ | _ | _ | _ | _ | _ | _ | _ |

Ket qua protected dat neu `accepted_response > 95%`, p95 duoi 11 giay cho steady test, khong co status ngoai `200`, `409`, `429`, `503`, `504`, va Gateway/backend khong crash.

## Bang chung Outbox va Consumer

| Kich ban | Ket qua can ghi |
|---|---|
| RabbitMQ dung/hoi phuc | So event `PENDING` truoc va `SENT` sau khi relay chay |
| Statistics restart | So message ton dong truoc restart va processed events sau restart |
| Idempotency | Mot `eventId`/`submissionId` chi tao mot row |
| Poison event | Event nam trong DLQ sau ba lan thu |
| Circuit Breaker | Trang thai/log TV2; ghi `N/A - TV2 chua merge` neu chua co implementation |

## Ket luan

Dien ket luan sau khi dat du bang chung tren. Khong cong bo ket qua benchmark neu Docker, k6, TV2 Circuit Breaker hoac cac artifact `results/` chua san sang.
