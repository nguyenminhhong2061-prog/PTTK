# Hệ thống thi trắc nghiệm online

Hệ thống thi trắc nghiệm online theo kiến trúc microservices.  

## Team Members

| Name | Student ID | Role | Contribution |
|------|------------|------|-------------|
| Nguyễn Minh Hồng | B21DCCN400 | TV1 | Exam Service (question bank, exam management) |
| Phùng Trung Kiên | B22DCCN433 | TV2 | Submission Service (exam session, grading) + analysis and design + architecture |
| Kim Duy Hưng | B22DCCN409 | TV3 | Statistics Service + Teacher analytics dashboard |

## Business Scope

- **Actors / Tac nhan**: Teacher, Student.
- **Teacher flow**: tạo câu hỏi, tạo đề thi, công bố / đóng đề thi, xem thống kê kết quả.
- **Student flow**: bắt đầu làm bài , lưu đáp án, nộp bài, xem kết quả.
- **System goal**: tách nghiệp vụ theo service để phát triển, test và triển khai.

## Architecture

```mermaid
graph LR
    U[User Browser] --> FE[Frontend - Vite :3000]
    FE --> GW[API Gateway - Nginx :8080]
    GW --> EX[Exam Service :5001]
    GW --> SUB[Submission Service :5002]
    GW --> ST[Statistics Service :5003]
    EX --> EXDB[(exam_db - MySQL)]
    SUB --> SUBDB[(submission_db - MySQL)]
    SUB --> EX
    ST --> SUB
```

| Component | Responsibility | Tech Stack | Port |
|---|---|---|---|
| `frontend` | Teacher/Student UI | React + Vite | 3000 |
| `gateway` | Single API entrypoint, routing | Nginx | 8080 |
| `exam-service` | Questions, exams, exam status | Spring Boot + MySQL | 5001 |
| `submission-service` | Exam session, answers, auto grading | Spring Boot + MySQL | 5002 |
| `statistics-service` | Overview, question analytics, leaderboard | Spring Boot (stateless) | 5003 |
| `exam-db` | Database of exam-service | MySQL 8.0 | 3306 (host) |
| `submission-db` | Database of submission-service | MySQL 8.0 | 3307 (host) |

## Repository Structure

```text
.
├── docker-compose.yml
├── .env.example
├── frontend/
├── gateway/
├── services/
│   ├── exam-service/
│   ├── submission-service/
│   └── statistics-service/
└── docs/
    └── api-specs/
```

## Quick Start

### 1) Prerequisites

- Docker Desktop (with Docker Compose)
- Git

### 2) Run full system

```bash
# from project root
cp .env.example .env
docker compose up --build -d
```

> PowerShell tren Windows: `copy .env.example .env`

### 3) Access URLs

- Frontend: `http://localhost:3000`
- API Gateway: `http://localhost:8080`
- Exam Service: `http://localhost:5001`
- Submission Service: `http://localhost:5002`
- Statistics Service: `http://localhost:5003`

## Health Check

```bash
curl http://localhost:8080/health            # gateway
curl http://localhost:5001/actuator/health   # exam-service
curl http://localhost:5002/health            # submission-service
curl http://localhost:5003/health            # statistics-service
```

## API Documentation (OpenAPI)

- [Exam Service Spec](docs/api-specs/exam-service.yaml)
- [Submission Service Spec](docs/api-specs/submission-service.yaml)
- [Statistics Service Spec](docs/api-specs/statistics-service.yaml)

## Service Notes

- Services communicate via Docker DNS names (`exam-service`, `submission-service`, `statistics-service`), not localhost.
- `statistics-service` is stateless and reads data from `submission-service`.
- Frontend in Docker is configured with internal proxy targets and startup wait to reduce early fetch failures.

## Common Commands

```bash
docker compose ps                 # show service status
docker compose logs -f gateway    # follow gateway logs
docker compose down               # stop all services
docker compose down -v            # stop and remove volumes
```

## Troubleshooting

- **Port already allocated**: đổi port trong `.env` (vd `SUBMISSION_DB_PORT=3310`) hoặc dùng container đang chiếm port.
- **Frontend "Failed to fetch" khi vừa khởi động**: đợi 10-20s để backend healthy, sau đó refresh lại.
- **Data không hiện trên dashboard**: kiểm tra đúng `examId`, và kiểm tra service health ở mục trên.

## License

This project uses the [MIT License](LICENSE).

