package com.quizapp.submission.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pattern 3: Outbox + Event-Driven — hạ tầng RabbitMQ phía publisher.
 *
 * Submission Service không consume gì cả, nó chỉ cần đảm bảo exchange/queue
 * tồn tại để publish không bị lỗi "queue not found" khi Statistics Service
 * chưa kịp khởi động và tự khai báo queue của nó.
 *
 * Tên exchange, tên queue, routing key là CONTRACT dùng chung với
 * Statistics Service (statistics-service phải khai báo giống hệt).
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "quiz.events";
    public static final String QUEUE_EXAM_SUBMITTED = "statistics.exam-submitted";
    public static final String ROUTING_KEY_EXAM_SUBMITTED = "exam.submitted";

    @Bean
    public TopicExchange quizEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue examSubmittedQueue() {
        return QueueBuilder.durable(QUEUE_EXAM_SUBMITTED).build();
    }

    @Bean
    public Binding examSubmittedBinding(Queue examSubmittedQueue, TopicExchange quizEventsExchange) {
        return BindingBuilder.bind(examSubmittedQueue)
                .to(quizEventsExchange)
                .with(ROUTING_KEY_EXAM_SUBMITTED);
    }
}
