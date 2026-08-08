package com.quizapp.statistics.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ProcessedEventRepositoryTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("statistics_db")
            .withUsername("statistics")
            .withPassword("statistics");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ProcessedEventRepository repository;

    @Test
    void claimIsAtomicForEventAndSubmissionIdentifiers() {
        int firstClaim = claim(
                "023379d2-ced1-4aa5-a4f8-338235e31e14",
                "119c8bf4-e482-4fad-872d-2d145c32a6b4"
        );
        int duplicateEvent = claim(
                "023379d2-ced1-4aa5-a4f8-338235e31e14",
                "568f6f98-36b9-4443-8489-1e703d65d3ef"
        );
        int duplicateSubmission = claim(
                "f6711c9a-75de-45d1-8e03-235b7217e0cc",
                "119c8bf4-e482-4fad-872d-2d145c32a6b4"
        );

        assertThat(firstClaim).isEqualTo(1);
        assertThat(duplicateEvent).isZero();
        assertThat(duplicateSubmission).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }

    private int claim(String eventId, String submissionId) {
        return repository.claimEvent(
                eventId,
                submissionId,
                "EXAM_SUBMITTED",
                1,
                1L,
                "student-001",
                85.0,
                Instant.parse("2026-07-23T10:00:00Z"),
                LocalDateTime.of(2026, 7, 23, 17, 0)
        );
    }
}
