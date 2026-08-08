package com.quizapp.statistics.consumer;

import com.quizapp.statistics.dto.event.ExamSubmittedEvent;
import com.quizapp.statistics.repository.ProcessedEventRepository;
import com.quizapp.statistics.service.StatisticsUpdateService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class ExamSubmittedEventConsumerTest {

    private ProcessedEventRepository repository;
    private StatisticsUpdateService updateService;
    private ExamSubmittedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(ProcessedEventRepository.class);
        updateService = mock(StatisticsUpdateService.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        consumer = new ExamSubmittedEventConsumer(repository, updateService, validator);
    }

    @Test
    void newEventIsClaimedAndProcessed() {
        ExamSubmittedEvent event = validEvent();
        when(repository.claimEvent(
                anyString(), anyString(), anyString(), anyInt(), anyLong(),
                anyString(), anyDouble(), any(), any()
        )).thenReturn(1);

        consumer.handle(event);

        verify(updateService).onExamSubmitted(event);
    }

    @Test
    void duplicateEventIsAcknowledgedWithoutProcessingAgain() {
        ExamSubmittedEvent event = validEvent();
        when(repository.claimEvent(
                anyString(), anyString(), anyString(), anyInt(), anyLong(),
                anyString(), anyDouble(), any(), any()
        )).thenReturn(0);

        consumer.handle(event);

        verify(updateService, never()).onExamSubmitted(any());
    }

    @Test
    void missingRequiredFieldIsRejected() {
        ExamSubmittedEvent event = validEvent();
        event.setEventId(null);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.handle(event));
        verify(repository, never()).claimEvent(
                anyString(), anyString(), anyString(), anyInt(), anyLong(),
                anyString(), anyDouble(), any(), any()
        );
    }

    @Test
    void unsupportedEventVersionIsRejected() {
        ExamSubmittedEvent event = validEvent();
        event.setEventVersion(2);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.handle(event));
    }

    @Test
    void scoreOutsideRangeIsRejected() {
        ExamSubmittedEvent event = validEvent();
        event.setScore(101.0);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.handle(event));
    }

    private ExamSubmittedEvent validEvent() {
        return ExamSubmittedEvent.builder()
                .eventId("023379d2-ced1-4aa5-a4f8-338235e31e14")
                .eventType("EXAM_SUBMITTED")
                .eventVersion(1)
                .occurredAt(Instant.parse("2026-07-23T10:00:00Z"))
                .submissionId("119c8bf4-e482-4fad-872d-2d145c32a6b4")
                .examId(1L)
                .studentId("student-001")
                .score(85.0)
                .build();
    }
}
