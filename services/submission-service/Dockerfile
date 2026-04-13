# Submission Service — Dockerfile
# Java 17 + Maven multi-stage build
# Không cần cài Java/Maven trên máy host — Docker lo hết

# ── Stage 1: Build ────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom.xml trước để cache dependencies (tái dụng layer khi chỉ sửa code)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source và build
COPY src ./src
RUN mvn --version
RUN mvn clean package -DskipTests || (echo "=== MAVEN FAILED with exit $? ===" && exit 1)

# ── Stage 2: Runtime ──────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy JAR từ stage build
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]
