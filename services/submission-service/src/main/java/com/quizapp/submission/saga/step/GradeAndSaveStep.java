package com.quizapp.submission.saga.step;

import com.quizapp.submission.dto.request.SaveAnswersRequest;
import com.quizapp.submission.dto.response.SubmitResponse;
import com.quizapp.submission.client.ExamServiceClient.QuestionDto;
import com.quizapp.submission.entity.Answer;
import com.quizapp.submission.entity.Submission;
import com.quizapp.submission.enums.SubmissionStatus;
import com.quizapp.submission.repository.AnswerRepository;
import com.quizapp.submission.repository.SubmissionRepository;
import com.quizapp.submission.saga.SagaStep;
import com.quizapp.submission.saga.SubmissionSagaContext;
import com.quizapp.submission.service.GradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class GradeAndSaveStep implements SagaStep<SubmissionSagaContext, Void> {

    private final SubmissionRepository submissionRepository;
    private final AnswerRepository answerRepository;
    private final GradingService gradingService;

    @Override
    @Transactional
    public Void execute(SubmissionSagaContext context) {
        String submissionId = context.getSubmissionId();
        Submission submission = context.getSubmission();

        log.info("Saga Execute: Chấm điểm và hoàn tất bài nộp {}", submissionId);

        // 1. Lưu đáp án cuối cùng (nếu có) gửi kèm từ client
        SaveAnswersRequest lastAnswers = context.getLastAnswers();
        if (lastAnswers != null && lastAnswers.getAnswers() != null) {
            log.info("Saga: Lưu đáp án cuối cùng gửi kèm cho submission {}", submissionId);
            List<Answer> currentAnswers = answerRepository.findBySubmissionId(submissionId);
            Map<String, Answer> answerMap = currentAnswers.stream()
                    .collect(Collectors.toMap(Answer::getQuestionId, a -> a));

            for (SaveAnswersRequest.AnswerItem item : lastAnswers.getAnswers()) {
                Answer answer = answerMap.get(item.getQuestionId());
                if (answer != null) {
                    answer.setSelectedOption(item.getSelectedOption());
                }
            }
            answerRepository.saveAll(answerMap.values());
        }

        // 2. Chấm điểm bài thi
        List<Answer> studentAnswers = answerRepository.findBySubmissionId(submissionId);
        List<QuestionDto> questions = context.getExamQuestions().getQuestions();
        GradingService.GradingResult result = gradingService.grade(
                studentAnswers, 
                questions
        );

        // 3. Cập nhật thông tin bài nộp sang SUBMITTED
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setScore(result.getScore());
        submission.setCorrectCount(result.getCorrectCount());
        submission.setSubmittedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        // 4. Lưu lại kết quả chấm điểm từng câu hỏi
        answerRepository.saveAll(result.getGradedAnswers());

        // 5. Build SubmitResponse và set vào context để trả về cho controller
        SubmitResponse submitResponse = buildSubmitResponse(submission, result, questions);
        context.setSubmitResponse(submitResponse);

        // 6. Nơi dành cho TV3 tích hợp Outbox Event
        // TODO: TV3 sẽ viết code insert event vào bảng outbox tại đây
        // outboxService.saveEvent(new OutboxEvent(...));
        log.info("Saga Execute: Lưu điểm thành công cho submission {}: Score = {}", submissionId, result.getScore());

        return null;
    }

    private SubmitResponse buildSubmitResponse(
            Submission sub,
            GradingService.GradingResult result,
            List<QuestionDto> questions) {

        Map<String, String> correctAnswerMap = questions.stream()
                .collect(Collectors.toMap(
                        q -> String.valueOf(q.getQuestionId()),
                        q -> q.getCorrectAnswer() != null ? q.getCorrectAnswer() : ""));

        List<SubmitResponse.AnswerResult> answerResults = result.getGradedAnswers().stream()
                .map(a -> SubmitResponse.AnswerResult.builder()
                        .questionId(a.getQuestionId())
                        .orderIndex(a.getOrderIndex())
                        .selectedOption(a.getSelectedOption())
                        .correctAnswer(correctAnswerMap.get(a.getQuestionId()))
                        .isCorrect(a.getIsCorrect())
                        .build())
                .collect(Collectors.toList());

        return SubmitResponse.builder()
                .submissionId(sub.getId())
                .examId(sub.getExamId())
                .studentId(sub.getStudentId())
                .score(result.getScore())
                .correctCount(result.getCorrectCount())
                .totalQuestions(sub.getTotalQuestions())
                .passed(result.getScore() >= 50.0)
                .submittedAt(sub.getSubmittedAt())
                .answers(answerResults)
                .build();
    }

    @Override
    public void compensate(SubmissionSagaContext context) {
        // Giao dịch ở execute() được đánh dấu @Transactional.
        // Nếu bất kỳ lỗi nào xảy ra trong transaction này (bao gồm cả lỗi khi ghi vào outbox),
        // Spring Boot sẽ tự động rollback toàn bộ dữ liệu xuống database.
        // Do đó compensate ở bước này là rỗng.
    }
}
