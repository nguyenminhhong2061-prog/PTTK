package com.quizapp.submission.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.quizapp.submission.dto.event.ExamSubmittedEvent;
import com.quizapp.submission.entity.OutboxEvent;
import com.quizapp.submission.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * TV4-owned contract test.  It deliberately tests the persisted Outbox JSON
 * without changing TV3's unit tests for the Outbox implementation.
 */
class Tv4OutboxEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void saveEvent_persistsVersionedExamSubmittedContract() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxService service = new OutboxService(repository, objectMapper);
        Instant occurredAt = Instant.parse("2026-08-07T00:00:00Z");
        ExamSubmittedEvent event = ExamSubmittedEvent.builder()
                .eventId("event-tv4-1")
                .eventType("EXAM_SUBMITTED")
                .eventVersion(1)
                .occurredAt(occurredAt)
                .submissionId("submission-tv4-1")
                .examId(42L)
                .studentId("student-tv4-1")
                .score(87.5)
                .build();

        service.saveEvent("EXAM_SUBMITTED", event.getSubmissionId(), event);

        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(outbox.capture());
        JsonNode json = objectMapper.readTree(outbox.getValue().getPayload());
        assertEquals("EXAM_SUBMITTED", outbox.getValue().getEventType());
        assertEquals("event-tv4-1", json.path("eventId").asText());
        assertEquals("EXAM_SUBMITTED", json.path("eventType").asText());
        assertEquals(1, json.path("eventVersion").asInt());
        assertEquals(occurredAt, objectMapper.treeToValue(json.path("occurredAt"), Instant.class));
        assertEquals("submission-tv4-1", json.path("submissionId").asText());
        assertEquals(42L, json.path("examId").asLong());
        assertEquals("student-tv4-1", json.path("studentId").asText());
        assertEquals(87.5, json.path("score").asDouble());
        assertNotNull(outbox.getValue().getId());
    }
}
