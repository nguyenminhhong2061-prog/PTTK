# exam-service

Rename this to match your actual service name (e.g., user-service, order-service).

## Overview

Describe the responsibility of this service:

- **What business domain does it cover?**  
  Online quiz/exam management domain (question bank + exam lifecycle).
- **What data does it own?**  
  `questions`, `exams`, and `exam_questions` (mapping exam-question with order index).
- **What operations does it expose?**  
  CRUD for questions, CRUD/list for exams, exam status transition, and retrieval of exam questions (with/without answers).

## Tech Stack

| Component | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 (Spring Web, Spring Data JPA, Validation, Actuator) |
| Database | MySQL 8 (`exam_db`) |

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/health` | Health check |
| GET | `/questions` | List questions (pagination + `createdBy` filter) |
| POST | `/questions` | Create question |
| GET | `/questions/{questionId}` | Get question detail |
| PUT | `/questions/{questionId}` | Update question |
| DELETE | `/questions/{questionId}` | Delete question |
| GET | `/exams` | List exams (pagination + `status`/`createdBy` filter) |
| POST | `/exams` | Create exam (`DRAFT`) |
| GET | `/exams/{examId}` | Get exam detail |
| PUT | `/exams/{examId}` | Update exam (only when `DRAFT`) |
| PATCH | `/exams/{examId}/status` | Update exam status (`DRAFT -> PUBLISHED -> CLOSED`) |
| GET | `/exams/{examId}/questions` | Get exam questions (`includeAnswers=false` by default) |

Full API specification: [docs/api-specs/exam-service.yaml](../../docs/api-specs/exam-service.yaml)

## Running Locally

```bash
# From project root
docker compose up exam-db exam-service --build

# Or run standalone (Spring Boot)
cd services/exam-service
./mvnw spring-boot:run
```

## Project Structure

```text
exam-service/
├── Dockerfile
├── readme.md
└── src/
    ├── main/
    │   ├── java/com/quizapp/exam/
    │   │   ├── config/
    │   │   ├── controller/
    │   │   ├── dto/
    │   │   ├── entity/
    │   │   ├── enums/
    │   │   ├── exception/
    │   │   ├── repository/
    │   │   └── service/
    │   └── resources/
    └── test/
```

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_HOST` | Database hostname | `localhost` |
| `DB_PORT` | Database port | `3306` |
| `DB_NAME` | Database name | `exam_db` |
| `DB_USER` | Database user | `root` |
| `DB_PASSWORD` | Database password | `verysecret` |
| `JPA_DDL_AUTO` | Hibernate schema mode | `update` |
| `SERVER_PORT` | Service port (configured in app) | `8080` |
