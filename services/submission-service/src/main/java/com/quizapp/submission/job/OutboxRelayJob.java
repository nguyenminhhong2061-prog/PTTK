package com.quizapp.submission.job;

import com.quizapp.submission.config.RabbitMQConfig;
import com.quizapp.submission.entity.OutboxEvent;
import com.quizapp.submission.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pattern 3: Outbox + Event-Driven — background publisher.
 *
 * Đọc các event đang PENDING trong bảng outbox_events, đẩy sang RabbitMQ.
 * Đây là điểm mấu chốt khiến pattern này "tự chữa lành":
 *   - Nếu RabbitMQ đang down khi request nộp bài xảy ra → event vẫn nằm
 *     an toàn trong bảng outbox_events (không mất, vì DB write đã commit).
 *   - Job này chạy lại mỗi {@code outbox.relay.fixed-delay-ms}, tự động
 *     publish hết các event tồn đọng ngay khi RabbitMQ hồi phục —
 *     KHÔNG cần can thiệp thủ công.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayJob {

    private static final int MAX_RETRY_COUNT = 30;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}")
    public void relay() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("OutboxRelayJob: tìm thấy {} event PENDING, bắt đầu publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishOne(event);
        }
    }

    /**
     * Publish 1 event và cập nhật trạng thái của nó. Lưu ý: không đánh dấu
     * @Transactional ở đây — method này được gọi nội bộ từ relay() trong
     * cùng class (self-invocation), Spring AOP proxy sẽ KHÔNG áp dụng được
     * transaction trong trường hợp đó. Không sao vì mỗi outboxEventRepository
     * .save(event) bên dưới vốn đã tự chạy trong transaction riêng của nó
     * (Spring Data JPA mặc định), và 1 event lỗi publish không ảnh hưởng
     * tới việc save trạng thái của các event khác trong cùng batch.
     */
    private void publishOne(OutboxEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ROUTING_KEY_EXAM_SUBMITTED,
                    event.getPayload()
            );
            
            event.setStatus("SENT");
            event.setSentAt(LocalDateTime.now());
            log.info("OutboxRelayJob: publish thành công event {} (aggregateId={})",
                    event.getId(), event.getAggregateId());
        } catch (Exception e) {
            event.setRetryCount(event.getRetryCount() + 1);
            if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                event.setStatus("FAILED");
                log.error("OutboxRelayJob: event {} thất bại sau {} lần thử, đánh dấu FAILED. Lỗi: {}",
                        event.getId(), event.getRetryCount(), e.getMessage());
            } else {
                log.warn("OutboxRelayJob: publish event {} thất bại (lần {}/{}): {}",
                        event.getId(), event.getRetryCount(), MAX_RETRY_COUNT, e.getMessage());
            }
        }
        outboxEventRepository.save(event);
    }
}
