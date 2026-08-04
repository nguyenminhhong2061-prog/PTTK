package com.quizapp.statistics.consumer;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Idempotency check cho ExamEventConsumer.
 *
 * Statistics Service là stateless (không có DB riêng — xem PROJECT_OVERVIEW,
 * bảng kiến trúc kỹ thuật), nên thay vì 1 bảng `processed_events` trong DB,
 * ta dùng 1 bộ nhớ đệm trong process. Vì consumer hiện tại chỉ log lại việc
 * đã nhận điểm mới (không ghi đè state quan trọng nào), xử lý trùng 1 event
 * sau khi service restart là vô hại — cái cần tránh chỉ là xử lý trùng
 * NHIỀU LẦN trong cùng 1 lần chạy do RabbitMQ redeliver khi ack chậm.
 *
 * Giới hạn kích thước (LRU đơn giản) để tránh phình bộ nhớ vô hạn khi
 * chạy lâu dài.
 */
@Component
public class ProcessedEventTracker {

    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Boolean> processedIds =
            Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED;
                }
            });

    /** @return true nếu event NÀY được xử lý lần đầu (chưa từng thấy trước đó) */
    public boolean markProcessedIfNew(String eventKey) {
        synchronized (processedIds) {
            if (processedIds.containsKey(eventKey)) {
                return false;
            }
            processedIds.put(eventKey, Boolean.TRUE);
            return true;
        }
    }
}
