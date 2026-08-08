package com.quizapp.submission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.submission.entity.OutboxEvent;
import com.quizapp.submission.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pattern 3: Outbox + Event-Driven.
 *
 * QUAN TRỌNG: saveEvent() KHÔNG tự mở transaction riêng — nó chủ đích
 * dùng chung transaction với nơi gọi nó (GradeAndSaveStep.execute(), vốn
 * đã có @Transactional). Nhờ vậy, ghi điểm vào bảng `submissions` và ghi
 * event vào bảng `outbox_events` luôn atomic: nếu 1 trong 2 lỗi, Spring
 * rollback CẢ HAI — không bao giờ xảy ra tình huống "lưu điểm xong nhưng
 * event bị mất" (Vấn đề 3 trong PROJECT_OVERVIEW).
 *
 * Việc thực sự publish event sang RabbitMQ được tách riêng ra
 * OutboxRelayJob, chạy nền, KHÔNG nằm trong transaction này — vì gọi
 * RabbitMQ (network call) bên trong transaction DB là một anti-pattern
 * (giữ transaction mở quá lâu, và nếu RabbitMQ down thì lại rollback luôn
 * cả điểm số — đúng vấn đề mà Outbox pattern sinh ra để giải quyết).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void saveEvent(String eventType, String aggregateId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            OutboxEvent event = OutboxEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payload(json)
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .retryCount(0)
                    .build();

            outboxEventRepository.save(event);
            log.info("Outbox: đã ghi event {} (aggregateId={}) vào bảng outbox_events, chờ relay",
                    eventType, aggregateId);
        } catch (Exception | StackOverflowError e) {
            // Ném lại lỗi để @Transactional bao ngoài (GradeAndSaveStep) rollback toàn bộ,
            // bao gồm cả điểm số vừa lưu — tránh tình huống lưu điểm thành công nhưng
            // không thể tạo được event tương ứng.
            log.error("Outbox: lỗi khi serialize/ghi event {} cho aggregateId={}: {}",
                    eventType, aggregateId, e.getMessage());
            throw new IllegalStateException("Không thể ghi outbox event: " + e.getMessage(), e);
        }
    }
}
