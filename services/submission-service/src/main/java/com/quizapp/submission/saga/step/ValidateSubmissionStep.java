package com.quizapp.submission.saga.step;

import com.quizapp.submission.entity.Submission;
import com.quizapp.submission.enums.SubmissionStatus;
import com.quizapp.submission.repository.SubmissionRepository;
import com.quizapp.submission.saga.SagaStep;
import com.quizapp.submission.saga.SubmissionSagaContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ValidateSubmissionStep implements SagaStep<SubmissionSagaContext, Void> {

    private final SubmissionRepository submissionRepository;

    @Override
    public Void execute(SubmissionSagaContext context) {
        String submissionId = context.getSubmissionId();
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài nộp với ID: " + submissionId));

        if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
            throw new IllegalStateException("Bài thi đã được nộp và chấm điểm trước đó.");
        }

        if (submission.getStatus() == SubmissionStatus.GRADING) {
            throw new IllegalStateException("Bài thi đang trong quá trình chấm điểm. Vui lòng thử lại sau.");
        }

        // Lưu submission vào context để các step sau không cần tìm lại
        context.setSubmission(submission);
        return null;
    }

    @Override
    public void compensate(SubmissionSagaContext context) {
        // Validate không thay đổi dữ liệu nên không cần compensate
    }
}
