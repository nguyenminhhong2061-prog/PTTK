package com.quizapp.statistics.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.statistics.config.RabbitMQConfig;
import com.quizapp.statistics.dto.event.ExamSubmittedEvent;
import com.quizapp.statistics.service.StatisticsUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Pattern 3: Outbox + Event-Driven — phía consumer.
 *
 * Lắng nghe queue "statistics.exam-submitted". Nếu RabbitMQ hoặc chính
 * service này down khi event được publish, message vẫn nằm an toàn trong
 * queue (durable) — khi service khởi động lại, @RabbitListener tự động
 * nhận và xử lý hết các message tồn đọng, không cần can thiệp thủ công.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExamEventConsumer {

    private final ProcessedEventTracker processedEventTracker;
    private final StatisticsUpdateService statisticsUpdateService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_EXAM_SUBMITTED)
    public void handleExamSubmitted(String messageJson) {
        ExamSubmittedEvent event;
        try {
            event = objectMapper.readValue(messageJson, ExamSubmittedEvent.class);
        } catch (Exception e) {
            // Message không parse được — không throw lại (sẽ bị requeue vô hạn),
            // chỉ log lỗi và bỏ qua message này.
            log.error("ExamEventConsumer: không parse được message, bỏ qua. Raw={}, lỗi={}",
                    messageJson, e.getMessage());
            return;
        }

        // IDEMPOTENCY CHECK — RabbitMQ có thể redeliver cùng 1 message (at-least-once delivery),
        // tránh xử lý trùng lặp bằng cách check theo submissionId.
        if (!processedEventTracker.markProcessedIfNew(event.getSubmissionId())) {
            log.warn("ExamEventConsumer: event của submission {} đã xử lý trước đó, bỏ qua (duplicate delivery)",
                    event.getSubmissionId());
            return;
        }

        statisticsUpdateService.handleExamSubmitted(event);
    }
}
