# Statistics Service

## Overview

Statistics Service is responsible for aggregating exam result data for teacher analytics.

- **Business domain**: exam reporting and analytics (overview metrics, question-level insights, leaderboard).
- **Data ownership**: this service is stateless and does not own a local database. It reads submission data from `submission-service`.
- **Exposed operations**:
  - exam overview statistics
  - per-question analytics
  - student score ranking

## Tech Stack

| Component | Choice |
|-----------|--------|
| Language | Java 17 |
| Framework | Spring Boot 3.2 (Spring Web, Spring WebFlux, Actuator, Lombok) |
| Database | None (stateless, no local DB) |

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/statistics/exams/{examId}` | Get overview statistics for one exam |
| GET | `/statistics/exams/{examId}/questions` | Get question analytics for one exam |
| GET | `/statistics/exams/{examId}/students` | Get student leaderboard for one exam |

Full API specification: `docs/api-specs/statistics-service.yaml`

## Running Locally

```bash
# From project root
docker compose up statistics-service --build
```

For full system run (gateway + backend + frontend):

```bash
docker compose up --build
```

## Project Structure

```text
statistics-service/
├── Dockerfile
├── readme.md
├── pom.xml
└── src/
    └── main/
        ├── java/      # source code
        └── resources/ # application.properties
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Internal application port | `8080` |
| `SUBMISSION_SERVICE_URL` | Base URL of submission-service | `http://submission-service:8080` |
