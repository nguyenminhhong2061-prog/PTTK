package com.quizapp.submission.saga.step;

import com.quizapp.submission.client.ExamServiceClient;
import com.quizapp.submission.client.ExamServiceClient.ExamQuestionsDto;
import com.quizapp.submission.exception.ExamServiceUnavailableException;
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

        // Nếu Exam Service trả về body rỗng (không ném exception nhưng data không hợp lệ)
        // → dùng ExamServiceUnavailableException để Orchestrator nhận ra và chạy compensation.
        // Không dùng RuntimeException thô vì Orchestrator sẽ wrap thành SagaExecutionException
        // thay vì trả 503 + rollback IN_PROGRESS đúng cách.
        if (examQuestions == null || examQuestions.getQuestions() == null
                || examQuestions.getQuestions().isEmpty()) {
            throw new ExamServiceUnavailableException(
                    "Exam Service trả về danh sách câu hỏi rỗng hoặc không hợp lệ cho đề thi " + examId
                    + ". Không thể chấm điểm. Trạng thái bài thi đã được khôi phục.");
        }
        context.setExamQuestions(examQuestions);
        return null;
    }

    @Override
    public void compensate(SubmissionSagaContext context) {
        // Chỉ đọc dữ liệu từ Exam Service nên không cần compensate
    }
}
