# Exam Service

Java 17 + Spring Boot microservice for managing Questions and Exams.

## Run with Docker Compose

```bash
docker compose up exam-db exam-service --build
```

Health:

```bash
curl http://localhost:5001/health
```

## Notes

- Database: MySQL (`exam_db`)
- IDs: UUID stored as `CHAR(36)` (String)
- Enums stored as strings to match MySQL `ENUM`

