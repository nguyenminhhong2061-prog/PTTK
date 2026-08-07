# API Gateway

Nginx is the public entry point on `http://localhost:8080`. It handles CORS, strips the `/api` prefix, and routes requests through the Docker network.

| External path | Internal target |
|---|---|
| `/api/questions/*` | `http://exam-service:8080/questions/*` |
| `/api/exams/*` | `http://exam-service:8080/exams/*` |
| `/api/submissions/*` | `http://submission-service:8080/submissions/*` |
| `/api/statistics/*` | `http://statistics-service:8080/statistics/*` |
| `/health` | Gateway-local health response |

## Submit protection

`/api/submissions/{submissionId}/submit` has a dedicated policy:

- sustained rate: 10 requests/second per client IP;
- burst capacity: 20 requests with `nodelay`;
- upstream connect timeout: 5 seconds;
- upstream send/read timeout: 10 seconds;
- no upstream retry for the non-idempotent POST request.

Gateway-generated errors use JSON:

- `429 RATE_LIMIT_EXCEEDED`, with `Retry-After: 1`;
- `502 BAD_GATEWAY`;
- `504 GATEWAY_TIMEOUT`.

## Validation

```powershell
docker compose config --quiet
docker compose build gateway
docker compose run --rm gateway nginx -t
```
