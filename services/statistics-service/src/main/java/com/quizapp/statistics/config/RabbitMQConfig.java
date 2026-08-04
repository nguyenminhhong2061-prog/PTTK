package com.quizapp.statistics.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pattern 3: Outbox + Event-Driven — hạ tầng RabbitMQ phía consumer.
 *
 * PHẢI khớp CHÍNH XÁC với submission-service.config.RabbitMQConfig
 * (tên exchange, tên queue, routing key) — đây là contract giữa 2 service.
 * Khai báo lại ở đây (idempotent — RabbitMQ không tạo trùng) để
 * Statistics Service tự đứng độc lập được, không phụ thuộc thứ tự khởi
 * động của submission-service.
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
