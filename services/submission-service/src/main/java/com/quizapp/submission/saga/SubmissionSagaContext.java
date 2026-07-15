package com.quizapp.submission.saga;

import com.quizapp.submission.client.ExamServiceClient.ExamQuestionsDto;
import com.quizapp.submission.dto.request.SaveAnswersRequest;
import com.quizapp.submission.dto.response.SubmitResponse;
import com.quizapp.submission.entity.Submission;
import lombok.Data;

@Data
public class SubmissionSagaContext {
    private final String submissionId;
    private final SaveAnswersRequest lastAnswers;
    
    // Dữ liệu tạm sinh ra giữa các step
    private Submission submission;
    private ExamQuestionsDto examQuestions;
    private SubmitResponse submitResponse;
}
