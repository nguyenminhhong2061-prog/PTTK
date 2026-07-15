package com.quizapp.submission.saga.step;

import com.quizapp.submission.client.ExamServiceClient;
import com.quizapp.submission.client.ExamServiceClient.ExamQuestionsDto;
import com.quizapp.submission.saga.SagaStep;
import com.quizapp.submission.saga.SubmissionSagaContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FetchAnswersStep implements SagaStep<SubmissionSagaContext, Void> {

    private final ExamServiceClient examServiceClient;

    @Override
    public Void execute(SubmissionSagaContext context) {
        Long examId = context.getSubmission().getExamId();
        log.info("Saga Execute: Gọi Exam Service lấy đáp án đúng cho exam ID {}", examId);
        ExamQuestionsDto examQuestions = examServiceClient.getExamQuestionsWithAnswers(examId);
        if (examQuestions == null || examQuestions.getQuestions() == null) {
            throw new RuntimeException("Đáp án trả về từ Exam Service trống hoặc không hợp lệ.");
        }
        context.setExamQuestions(examQuestions);
        return null;
    }

    @Override
    public void compensate(SubmissionSagaContext context) {
        // Chỉ đọc dữ liệu từ Exam Service nên không cần compensate
    }
}
