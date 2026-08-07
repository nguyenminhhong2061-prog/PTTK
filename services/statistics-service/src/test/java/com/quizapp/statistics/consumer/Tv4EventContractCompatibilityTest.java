package com.quizapp.statistics.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.quizapp.statistics.dto.event.ExamSubmittedEvent;
import com.quizapp.statistics.repository.ProcessedEventRepository;
import com.quizapp.statistics.service.StatisticsUpdateService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Tv4EventContractCompatibilityTest {

    private static final String PUBLISHED_EVENT_JSON = """
            {
              "eventId": "023379d2-ced1-4aa5-a4f8-338235e31e14",
              "eventType": "EXAM_SUBMITTED",
              "eventVersion": 1,
              "occurredAt": "2026-08-07T01:00:00Z",
              "submissionId": "119c8bf4-e482-4fad-872d-2d145c32a6b4",
              "examId": 1,
              "studentId": "student-001",
              "score": 85.0
            }
            """;

    @Test
    void consumerAcceptsTheVersionedExamSubmittedPublisherContract() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ExamSubmittedEvent event = objectMapper.readValue(PUBLISHED_EVENT_JSON, ExamSubmittedEvent.class);
        ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
        StatisticsUpdateService updateService = mock(StatisticsUpdateService.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ExamSubmittedEventConsumer consumer = new ExamSubmittedEventConsumer(repository, updateService, validator);

        when(repository.claimEvent(
                anyString(), anyString(), anyString(), anyInt(), anyLong(),
                anyString(), anyDouble(), any(), any()
        )).thenReturn(1);

        consumer.handle(event);

        assertEquals("023379d2-ced1-4aa5-a4f8-338235e31e14", event.getEventId());
        assertEquals("EXAM_SUBMITTED", event.getEventType());
        assertEquals(1, event.getEventVersion());
        assertEquals(Instant.parse("2026-08-07T01:00:00Z"), event.getOccurredAt());
        assertEquals("119c8bf4-e482-4fad-872d-2d145c32a6b4", event.getSubmissionId());
        assertEquals(1L, event.getExamId());
        assertEquals("student-001", event.getStudentId());
        assertEquals(85.0, event.getScore());
        verify(updateService).onExamSubmitted(event);
    }
}
