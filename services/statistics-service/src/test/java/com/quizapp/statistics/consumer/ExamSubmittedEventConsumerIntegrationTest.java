package com.quizapp.statistics.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.statistics.dto.event.ExamSubmittedEvent;
import com.quizapp.statistics.repository.ProcessedEventRepository;
import com.quizapp.statistics.service.StatisticsUpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ExamSubmittedEventConsumerIntegrationTest {

    private static final String EXCHANGE = "quiz.events";
    private static final String QUEUE = "statistics.exam-submitted";
    private static final String ROUTING_KEY = "exam.submitted";
    private static final String DLQ = "statistics.exam-submitted.dlq";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("statistics_db")
            .withUsername("statistics")
            .withPassword("statistics");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management")
    ).withUser("guest", "guest");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ExamSubmittedEventConsumer consumer;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @MockBean
    private StatisticsUpdateService statisticsUpdateService;

    @BeforeEach
    void setUp() {
        stopListeners();
        drain(QUEUE);
        drain(DLQ);
        processedEventRepository.deleteAll();
        reset(statisticsUpdateService);
        startListeners();
    }

    @AfterEach
    void tearDown() {
        startListeners();
    }

    @Test
    void validEventIsProcessedOnlyOnceWhenDeliveredTwice() throws Exception {
        ExamSubmittedEvent event = validEvent();

        publish(event);
        publish(event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedEventRepository.count()).isEqualTo(1);
            verify(statisticsUpdateService, times(1)).onExamSubmitted(event);
        });
    }

    @Test
    void invalidEventIsDeadLetteredAfterListenerRetries() throws Exception {
        Map<String, Object> invalidEvent = Map.of(
                "eventType", "EXAM_SUBMITTED",
                "eventVersion", 1,
                "occurredAt", Instant.now().toString(),
                "submissionId", UUID.randomUUID().toString(),
                "examId", 1,
                "studentId", "student-001",
                "score", 85.0
        );

        publish(invalidEvent);

        Message deadLetter = await().atMost(Duration.ofSeconds(15))
                .until(() -> rabbitTemplate.receive(DLQ), message -> message != null);
        assertThat(new String(deadLetter.getBody())).contains("EXAM_SUBMITTED");
        assertThat(processedEventRepository.count()).isZero();
    }

    @Test
    void queuedEventIsProcessedAfterConsumerListenerRestarts() throws Exception {
        ExamSubmittedEvent event = validEvent();
        stopListeners();

        publish(event);

        await().during(Duration.ofMillis(750)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(processedEventRepository.count()).isZero());

        startListeners();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(processedEventRepository.count()).isEqualTo(1);
            verify(statisticsUpdateService, times(1)).onExamSubmitted(event);
        });
    }

    @Test
    void failedStatisticsUpdateRollsBackTheIdempotencyClaim() {
        ExamSubmittedEvent event = validEvent();
        doThrow(new IllegalStateException("projection update failed"))
                .when(statisticsUpdateService).onExamSubmitted(event);

        assertThatThrownBy(() -> consumer.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("projection update failed");

        assertThat(processedEventRepository.count()).isZero();
    }

    private void publish(Object payload) throws Exception {
        Message message = MessageBuilder.withBody(objectMapper.writeValueAsBytes(payload))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message);
    }

    private ExamSubmittedEvent validEvent() {
        return ExamSubmittedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("EXAM_SUBMITTED")
                .eventVersion(1)
                .occurredAt(Instant.now())
                .submissionId(UUID.randomUUID().toString())
                .examId(1L)
                .studentId("student-001")
                .score(85.0)
                .build();
    }

    private void drain(String queue) {
        while (rabbitTemplate.receive(queue) != null) {
            // Drain messages left by a previous test before starting listeners.
        }
    }

    private void stopListeners() {
        listenerRegistry.getListenerContainers().forEach(MessageListenerContainer::stop);
    }

    private void startListeners() {
        listenerRegistry.getListenerContainers().forEach(container -> {
            if (!container.isRunning()) {
                container.start();
            }
        });
    }
}
