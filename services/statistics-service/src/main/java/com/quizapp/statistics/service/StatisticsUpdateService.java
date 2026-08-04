package com.quizapp.statistics.service;

import com.quizapp.statistics.dto.event.ExamSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Xử lý nghiệp vụ khi Statistics Service nhận được event "ExamSubmitted".
 *
 * Vì StatisticsService (xem StatisticsService.java) hiện tại tính toán
 * dashboard bằng cách gọi trực tiếp Submission Service qua WebClient
 * (pull-based, luôn ra số liệu mới nhất tại thời điểm gọi), việc nhận
 * event ở đây KHÔNG cần thiết để tính đúng số liệu — dữ liệu gốc đã nằm
 * an toàn trong submission_db. Event đóng vai trò là:
 *   1. Tín hiệu real-time để log lại/audit khi có bài nộp mới, hoặc
 *   2. Điểm mở rộng — nếu sau này cần cache số liệu để giảm tải WebClient
 *      call, đây là nơi invalidate cache tương ứng.
 *
 * Log rõ ràng ở đây chính là bằng chứng dùng để demo: khi RabbitMQ được
 * bật lại, các event tồn đọng sẽ dồn dập được xử lý và log ra đầy đủ.
 */
@Service
@Slf4j
public class StatisticsUpdateService {

    public void handleExamSubmitted(ExamSubmittedEvent event) {
        log.info(
                "Statistics: nhận bài nộp mới — submissionId={}, examId={}, studentId={}, score={}",
                event.getSubmissionId(), event.getExamId(), event.getStudentId(), event.getScore()
        );
        // Điểm mở rộng: cache invalidation / cập nhật counter cho dashboard real-time.
    }
}
