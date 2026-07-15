package com.quizapp.submission.saga.step;

import com.quizapp.submission.entity.Submission;
import com.quizapp.submission.enums.SubmissionStatus;
import com.quizapp.submission.repository.SubmissionRepository;
import com.quizapp.submission.saga.SagaStep;
import com.quizapp.submission.saga.SubmissionSagaContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarkGradingStep implements SagaStep<SubmissionSagaContext, Void> {

    private final SubmissionRepository submissionRepository;

    @Override
    @Transactional
    public Void execute(SubmissionSagaContext context) {
        Submission submission = context.getSubmission();
        if (submission == null) {
            submission = submissionRepository.findById(context.getSubmissionId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài nộp với ID: " + context.getSubmissionId()));
            context.setSubmission(submission);
        }

        log.info("Saga Execute: Chuyển trạng thái submission {} từ {} sang GRADING", submission.getId(), submission.getStatus());
        submission.setStatus(SubmissionStatus.GRADING);
        submissionRepository.save(submission);
        return null;
    }

    @Override
    @Transactional
    public void compensate(SubmissionSagaContext context) {
        Submission submission = context.getSubmission();
        if (submission == null) {
            submission = submissionRepository.findById(context.getSubmissionId()).orElse(null);
        }

        if (submission != null) {
            log.info("Saga Compensate: Rollback trạng thái submission {} về IN_PROGRESS", submission.getId());
            submission.setStatus(SubmissionStatus.IN_PROGRESS);
            submissionRepository.save(submission);
        }
    }
}
