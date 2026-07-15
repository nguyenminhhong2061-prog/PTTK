package com.quizapp.submission.saga;

import com.quizapp.submission.dto.request.SaveAnswersRequest;
import com.quizapp.submission.dto.response.SubmitResponse;
import com.quizapp.submission.saga.step.ValidateSubmissionStep;
import com.quizapp.submission.saga.step.MarkGradingStep;
import com.quizapp.submission.saga.step.FetchAnswersStep;
import com.quizapp.submission.saga.step.GradeAndSaveStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionSagaOrchestrator {

    private final ValidateSubmissionStep validateStep;
    private final MarkGradingStep markGradingStep;
    private final FetchAnswersStep fetchAnswersStep;
    private final GradeAndSaveStep gradeAndSaveStep;

    public SubmitResponse executeSaga(String submissionId, SaveAnswersRequest lastAnswers) {
        SubmissionSagaContext context = new SubmissionSagaContext(submissionId, lastAnswers);
        
        try {
            // Step 1: Validate
            log.info("Saga [1/4] - Validating submission {}", submissionId);
            validateStep.execute(context);
            
            // Step 2: Đổi trạng thái sang GRADING
            log.info("Saga [2/4] - Marking grading for submission {}", submissionId);
            markGradingStep.execute(context);
            
            try {
                // Step 3: Lấy đáp án đúng (Gọi Exam Service)
                log.info("Saga [3/4] - Fetching correct answers for exam");
                fetchAnswersStep.execute(context);
                
                // Step 4: Chấm điểm và lưu kết quả (Submit hoàn tất)
                log.info("Saga [4/4] - Grading and saving results");
                gradeAndSaveStep.execute(context);
                
                return context.getSubmitResponse();
                
            } catch (Exception e) {
                log.error("Saga gặp sự cố tại Step 3 hoặc Step 4, bắt đầu rollback. Lỗi: {}", e.getMessage());
                // Chạy lệnh đền bù (compensate) cho Step 2 để khôi phục trạng thái IN_PROGRESS
                markGradingStep.compensate(context);
                throw e; // Ném tiếp lỗi ra để Controller bắt được và trả HTTP 503
            }
            
        } catch (Exception e) {
            log.error("Lỗi thực thi Saga nộp bài thi cho submission {}: {}", submissionId, e.getMessage());
            if (e instanceof SagaExecutionException || e instanceof SagaRollbackException) {
                throw e;
            }
            throw new SagaExecutionException("Không thể nộp bài lúc này: " + e.getMessage(), e);
        }
    }
}
