package com.quizapp.submission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.submission.entity.OutboxEvent;
import com.quizapp.submission.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(outboxEventRepository, new ObjectMapper());
    }

    @Test
    void saveEvent_ghiEventVoiStatusPending() {
        outboxService.saveEvent("ExamSubmitted", "sub-123", Map.of("score", 90.0));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertEquals("ExamSubmitted", saved.getEventType());
        assertEquals("sub-123", saved.getAggregateId());
        assertEquals("PENDING", saved.getStatus());
        assertEquals(0, saved.getRetryCount());
        assertNotNull(saved.getId());
        assertTrue(saved.getPayload().contains("90.0"));
    }

    @Test
    void saveEvent_neuSerializeLoi_thiNemException_deTransactionBaoNgoaiRollback() {
        // Đối tượng chứa tham chiếu vòng lặp khiến Jackson không serialize được
        Object[] cyclic = new Object[1];
        cyclic[0] = cyclic;

        assertThrows(IllegalStateException.class,
                () -> outboxService.saveEvent("ExamSubmitted", "sub-999", cyclic));

        // Không được lưu record nào khi serialize thất bại
        verify(outboxEventRepository, never()).save(any());
    }
}
