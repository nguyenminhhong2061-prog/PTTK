package com.quizapp.statistics.service;

import com.quizapp.statistics.dto.event.ExamSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Hook for event-driven statistics updates. Reports are still calculated from
 * Submission Service, so the current implementation records an auditable log
 * and leaves cache invalidation/projections as future extensions.
 */
@Service
@Slf4j
public class StatisticsUpdateService {

    public void onExamSubmitted(ExamSubmittedEvent event) {
        log.info(
                "Statistics event accepted: eventId={}, submissionId={}, examId={}, studentId={}, score={}",
                event.getEventId(),
                event.getSubmissionId(),
                event.getExamId(),
                event.getStudentId(),
                event.getScore()
        );
    }
}
