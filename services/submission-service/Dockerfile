# Submission Service — Dockerfile
# Java 17 + Maven Wrapper (giống exam-service) — không cần cài Java/Maven trên máy host

# ── Stage 1: Build ────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy Maven Wrapper trước để cache layer
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw dependency:go-offline -q

# Copy source và build
COPY src/ src/
RUN ./mvnw clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy JAR từ stage build
COPY --from=builder /app/target/*.jar /app/app.jar

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
