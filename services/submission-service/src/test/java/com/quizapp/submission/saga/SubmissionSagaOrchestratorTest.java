package com.quizapp.submission.saga;

import com.quizapp.submission.dto.request.SaveAnswersRequest;
import com.quizapp.submission.dto.response.SubmitResponse;
import com.quizapp.submission.saga.step.FetchAnswersStep;
import com.quizapp.submission.saga.step.GradeAndSaveStep;
import com.quizapp.submission.saga.step.MarkGradingStep;
import com.quizapp.submission.saga.step.ValidateSubmissionStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SubmissionSagaOrchestratorTest {

    @Mock
    private ValidateSubmissionStep validateStep;

    @Mock
    private MarkGradingStep markGradingStep;

    @Mock
    private FetchAnswersStep fetchAnswersStep;

    @Mock
    private GradeAndSaveStep gradeAndSaveStep;

    @InjectMocks
    private SubmissionSagaOrchestrator orchestrator;

    private String submissionId;
    private SaveAnswersRequest lastAnswers;

    @BeforeEach
    void setUp() {
        submissionId = "sub-123";
        lastAnswers = new SaveAnswersRequest();
    }

    @Test
    void testExecuteSaga_Success() {
        // Mock step actions
        doAnswer(invocation -> {
            SubmissionSagaContext context = invocation.getArgument(0);
            SubmitResponse response = SubmitResponse.builder()
                    .submissionId(submissionId)
                    .score(90.0)
                    .build();
            context.setSubmitResponse(response);
            return null;
        }).when(gradeAndSaveStep).execute(any(SubmissionSagaContext.class));

        // Act
        SubmitResponse response = orchestrator.executeSaga(submissionId, lastAnswers);

        // Assert
        assertNotNull(response);
        assertEquals(submissionId, response.getSubmissionId());
        assertEquals(90.0, response.getScore());

        // Verify that steps were executed in order and compensate was NOT called
        verify(validateStep, times(1)).execute(any(SubmissionSagaContext.class));
        verify(markGradingStep, times(1)).execute(any(SubmissionSagaContext.class));
        verify(fetchAnswersStep, times(1)).execute(any(SubmissionSagaContext.class));
        verify(gradeAndSaveStep, times(1)).execute(any(SubmissionSagaContext.class));
        verify(markGradingStep, never()).compensate(any(SubmissionSagaContext.class));
    }

    @Test
    void testExecuteSaga_Failure_Compensates() {
        // Mock Step 3 (FetchAnswersStep) to fail
        doThrow(new RuntimeException("Exam Service sập")).when(fetchAnswersStep).execute(any(SubmissionSagaContext.class));

        // Act & Assert
        SagaExecutionException exception = assertThrows(SagaExecutionException.class, () -> {
            orchestrator.executeSaga(submissionId, lastAnswers);
        });

        assertTrue(exception.getMessage().contains("Exam Service sập"));

        // Verify that Step 1 and Step 2 executed, Step 3 failed, Step 4 was never executed
        verify(validateStep, times(1)).execute(any(SubmissionSagaContext.class));
        verify(markGradingStep, times(1)).execute(any(SubmissionSagaContext.class));
        verify(fetchAnswersStep, times(1)).execute(any(SubmissionSagaContext.class));
        verify(gradeAndSaveStep, never()).execute(any(SubmissionSagaContext.class));

        // Verify that MarkGradingStep compensate WAS called to rollback GRADING status
        verify(markGradingStep, times(1)).compensate(any(SubmissionSagaContext.class));
    }
}
