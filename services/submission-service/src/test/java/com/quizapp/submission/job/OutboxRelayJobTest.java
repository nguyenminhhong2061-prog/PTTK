package com.quizapp.submission.job;

import com.quizapp.submission.config.RabbitMQConfig;
import com.quizapp.submission.entity.OutboxEvent;
import com.quizapp.submission.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayJobTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxRelayJob relayJob;

    private OutboxEvent pendingEvent;

    @BeforeEach
    void setUp() {
        pendingEvent = OutboxEvent.builder()
                .id("evt-1")
                .eventType("ExamSubmitted")
                .aggregateId("sub-123")
                .payload("{\"submissionId\":\"sub-123\"}")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    @Test
    void relay_khongCoEventPending_khongLamGi() {
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of());

        relayJob.relay();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void relay_publishThanhCong_doiStatusSangSENT() {
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(pendingEvent));

        relayJob.relay();

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_EXAM_SUBMITTED),
                eq(pendingEvent.getPayload())
        );

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());
        assertEquals("SENT", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getSentAt());
    }

    @Test
    void relay_rabbitMQDown_tangRetryCount_khongMatEvent() {
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(pendingEvent));
        doThrow(new AmqpException("RabbitMQ không phản hồi"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        relayJob.relay();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());
        // Vẫn PENDING (chưa đến ngưỡng FAILED) — lần chạy tiếp theo của job sẽ tự thử lại
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
    }

    @Test
    void relay_quaSoLanRetryChoPhep_doiStatusSangFAILED() {
        pendingEvent.setRetryCount(4); // lần fail tiếp theo sẽ là lần thứ 5
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(pendingEvent));
        doThrow(new AmqpException("RabbitMQ không phản hồi"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        relayJob.relay();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals(5, captor.getValue().getRetryCount());
    }
}
