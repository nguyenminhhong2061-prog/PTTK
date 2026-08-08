package com.quizapp.statistics.consumer;

import com.quizapp.statistics.dto.event.ExamSubmittedEvent;
import com.quizapp.statistics.repository.ProcessedEventRepository;
import com.quizapp.statistics.service.StatisticsUpdateService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExamSubmittedEventConsumer {

    static final String SUPPORTED_EVENT_TYPE = "EXAM_SUBMITTED";
    static final int SUPPORTED_EVENT_VERSION = 1;

    private final ProcessedEventRepository processedEventRepository;
    private final StatisticsUpdateService statisticsUpdateService;
    private final Validator validator;

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    @Transactional
    public void handle(ExamSubmittedEvent event) {
        validate(event);

        int inserted = processedEventRepository.claimEvent(
                event.getEventId(),
                event.getSubmissionId(),
                event.getEventType(),
                event.getEventVersion(),
                event.getExamId(),
                event.getStudentId(),
                event.getScore(),
                event.getOccurredAt(),
                LocalDateTime.now()
        );

        if (inserted == 0) {
            log.info(
                    "Duplicate event skipped: eventId={}, submissionId={}",
                    event.getEventId(),
                    event.getSubmissionId()
            );
            return;
        }

        statisticsUpdateService.onExamSubmitted(event);
    }

    void validate(ExamSubmittedEvent event) {
        if (event == null) {
            reject("Event payload must not be null");
        }

        Set<ConstraintViolation<ExamSubmittedEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            reject("Invalid EXAM_SUBMITTED event: " + details);
        }

        if (!SUPPORTED_EVENT_TYPE.equals(event.getEventType())) {
            reject("Unsupported eventType: " + event.getEventType());
        }
        if (event.getEventVersion() != SUPPORTED_EVENT_VERSION) {
            reject("Unsupported eventVersion: " + event.getEventVersion());
        }
    }

    private void reject(String message) {
        throw new AmqpRejectAndDontRequeueException(message);
    }
}
