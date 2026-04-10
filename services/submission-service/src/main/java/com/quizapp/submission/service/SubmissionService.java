package com.quizapp.submission.service;

import com.quizapp.submission.client.ExamServiceClient;
import com.quizapp.submission.client.ExamServiceClient.QuestionDto;
import com.quizapp.submission.dto.request.SaveAnswersRequest;
import com.quizapp.submission.dto.request.StartExamRequest;
import com.quizapp.submission.dto.response.SubmissionDetailResponse;
import com.quizapp.submission.dto.response.SubmissionStartResponse;
import com.quizapp.submission.dto.response.SubmitResponse;
import com.quizapp.submission.entity.Answer;
import com.quizapp.submission.entity.Submission;
import com.quizapp.submission.enums.AnswerOption;
import com.quizapp.submission.enums.SubmissionStatus;
import com.quizapp.submission.repository.AnswerRepository;
import com.quizapp.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AnswerRepository answerRepository;
    private final ExamServiceClient examServiceClient;
    private final GradingService gradingService;

    // ── Bắt đầu làm bài ─────────────────────────────────────────────────

    @Transactional
    public SubmissionStartResponse startExam(StartExamRequest request) {
        String examId = request.getExamId();
        String studentId = request.getStudentId();

        // 1. Kiểm tra đã có submission cho bài thi này chưa
        Optional<Submission> existing = submissionRepository
                .findByStudentIdAndExamId(studentId, examId);

        if (existing.isPresent()) {
            Submission sub = existing.get();
            if (sub.getStatus() == SubmissionStatus.SUBMITTED) {
                throw new IllegalStateException("Bạn đã nộp bài thi này rồi. Không thể làm lại.");
            }
            // Đang IN_PROGRESS → resume phiên cũ
            log.info("Resume submission {} for student {}", sub.getId(), studentId);
            return buildStartResponse(sub, examId);
        }

        // 2. Lấy thông tin bài thi từ Exam Service
        ExamServiceClient.ExamDetailDto examDetail;
        ExamServiceClient.ExamQuestionsDto examQuestions;
        try {
            examDetail = examServiceClient.getExamDetail(examId);
            examQuestions = examServiceClient.getExamQuestions(examId);
        } catch (Exception e) {
            log.error("Không thể kết nối Exam Service: {}", e.getMessage());
            throw new RuntimeException("Không thể lấy thông tin bài thi. Vui lòng thử lại sau.");
        }

        // 3. Kiểm tra bài thi có trạng thái published không
        if (!"published".equalsIgnoreCase(examDetail.getStatus())) {
            throw new IllegalArgumentException("Bài thi chưa được công bố hoặc đã đóng.");
        }

        // 4. Tạo Submission mới
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime deadlineAt = startedAt.plusMinutes(examDetail.getDurationMinutes());

        Submission submission = Submission.builder()
                .examId(examId)
                .studentId(studentId)
                .status(SubmissionStatus.IN_PROGRESS)
                .totalQuestions(examQuestions.getTotalQuestions())
                .startedAt(startedAt)
                .deadlineAt(deadlineAt)
                .build();

        submissionRepository.save(submission);

        // 5. Tạo Answer record rỗng cho từng câu hỏi
        List<Answer> answers = examQuestions.getQuestions().stream()
                .map(q -> Answer.builder()
                        .submission(submission)
                        .questionId(q.getQuestionId())
                        .orderIndex(q.getOrderIndex())
                        .selectedOption(null)
                        .isCorrect(null)
                        .build())
                .collect(Collectors.toList());

        answerRepository.saveAll(answers);
        submission.setAnswers(answers);

        log.info("Created new submission {} for student {} on exam {}", submission.getId(), studentId, examId);

        // 6. Map sang response (kèm câu hỏi)
        return buildStartResponseWithQuestions(submission, examDetail, examQuestions.getQuestions(), answers);
    }

    // ── Lưu đáp án tạm ──────────────────────────────────────────────────

    @Transactional
    public int saveAnswers(String submissionId, SaveAnswersRequest request) {
        Submission submission = getSubmissionOrThrow(submissionId);

        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Bài đã nộp, không thể chỉnh sửa đáp án.");
        }

        // Lấy map questionId → Answer
        List<Answer> currentAnswers = answerRepository.findBySubmissionId(submissionId);
        Map<String, Answer> answerMap = currentAnswers.stream()
                .collect(Collectors.toMap(Answer::getQuestionId, a -> a));

        // Cập nhật từng đáp án
        int savedCount = 0;
        for (SaveAnswersRequest.AnswerItem item : request.getAnswers()) {
            Answer answer = answerMap.get(item.getQuestionId());
            if (answer != null) {
                answer.setSelectedOption(item.getSelectedOption());
                if (item.getSelectedOption() != null) savedCount++;
            }
        }

        answerRepository.saveAll(answerMap.values());
        return savedCount;
    }

    // ── Nộp bài & chấm điểm ─────────────────────────────────────────────

    @Transactional
    public SubmitResponse submit(String submissionId, SaveAnswersRequest lastAnswers) {
        Submission submission = getSubmissionOrThrow(submissionId);

        if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
            throw new IllegalStateException("Bài đã được nộp trước đó.");
        }

        // Kiểm tra hết giờ (vẫn cho nộp nếu hết giờ, nhưng ghi nhận)
        boolean overtime = LocalDateTime.now().isAfter(submission.getDeadlineAt());
        if (overtime) {
            log.warn("Student {} submitted after deadline for submission {}", submission.getStudentId(), submissionId);
        }

        // Lưu đáp án cuối cùng nếu có
        if (lastAnswers != null && lastAnswers.getAnswers() != null) {
            saveAnswers(submissionId, lastAnswers);
        }

        // Lấy đáp án đúng từ Exam Service (internal call)
        ExamServiceClient.ExamQuestionsDto examWithAnswers;
        try {
            examWithAnswers = examServiceClient.getExamQuestionsWithAnswers(submission.getExamId());
        } catch (Exception e) {
            log.error("Không thể lấy đáp án từ Exam Service: {}", e.getMessage());
            throw new RuntimeException("Không thể chấm điểm lúc này. Vui lòng thử lại sau.");
        }

        // Chấm điểm
        List<Answer> studentAnswers = answerRepository.findBySubmissionId(submissionId);
        GradingService.GradingResult result = gradingService.grade(studentAnswers, examWithAnswers.getQuestions());

        // Cập nhật submission
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setScore(result.getScore());
        submission.setCorrectCount(result.getCorrectCount());
        submission.setSubmittedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        // Cập nhật isCorrect cho từng Answer
        answerRepository.saveAll(result.getGradedAnswers());

        log.info("Submission {} graded: score={}, correct={}/{}", submissionId, result.getScore(),
                result.getCorrectCount(), submission.getTotalQuestions());

        // Build response
        return buildSubmitResponse(submission, result, examWithAnswers.getQuestions());
    }

    // ── Query ────────────────────────────────────────────────────────────

    public SubmissionDetailResponse getById(String submissionId) {
        Submission submission = getSubmissionOrThrow(submissionId);
        List<Answer> answers = answerRepository.findBySubmissionId(submissionId);
        return buildDetailResponse(submission, answers);
    }

    public List<Submission> listSubmissions(String studentId, String examId, String status) {
        if (studentId != null && examId != null) {
            return submissionRepository.findByStudentIdAndExamId(studentId, examId)
                    .map(List::of)
                    .orElse(List.of());
        }
        if (studentId != null) {
            return submissionRepository.findByStudentId(studentId);
        }
        if (examId != null) {
            if (status != null) {
                return submissionRepository.findByExamIdAndStatus(
                        examId, SubmissionStatus.valueOf(status.toUpperCase()));
            }
            return submissionRepository.findByExamId(examId);
        }
        return submissionRepository.findAll();
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private Submission getSubmissionOrThrow(String submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy bài nộp với ID: " + submissionId));
    }

    private SubmissionStartResponse buildStartResponse(Submission sub, String examId) {
        List<Answer> answers = answerRepository.findBySubmissionId(sub.getId());
        ExamServiceClient.ExamQuestionsDto examQuestions = examServiceClient.getExamQuestions(examId);

        return buildStartResponseWithQuestions(sub, null, examQuestions.getQuestions(), answers);
    }

    private SubmissionStartResponse buildStartResponseWithQuestions(
            Submission sub,
            ExamServiceClient.ExamDetailDto examDetail,
            List<QuestionDto> questions,
            List<Answer> currentAnswers) {

        Map<String, AnswerOption> selectedMap = currentAnswers.stream()
                .filter(a -> a.getSelectedOption() != null)
                .collect(Collectors.toMap(Answer::getQuestionId, Answer::getSelectedOption));

        List<SubmissionStartResponse.QuestionItem> questionItems = questions.stream()
                .map(q -> SubmissionStartResponse.QuestionItem.builder()
                        .questionId(q.getQuestionId())
                        .orderIndex(q.getOrderIndex())
                        .content(q.getContent())
                        .optionA(q.getOptionA())
                        .optionB(q.getOptionB())
                        .optionC(q.getOptionC())
                        .optionD(q.getOptionD())
                        .selectedOption(selectedMap.get(q.getQuestionId()))
                        .build())
                .collect(Collectors.toList());

        return SubmissionStartResponse.builder()
                .submissionId(sub.getId())
                .examId(sub.getExamId())
                .examTitle(examDetail != null ? examDetail.getTitle() : null)
                .studentId(sub.getStudentId())
                .status(sub.getStatus().name())
                .totalQuestions(sub.getTotalQuestions())
                .durationMinutes(examDetail != null ? examDetail.getDurationMinutes() : null)
                .startedAt(sub.getStartedAt())
                .deadlineAt(sub.getDeadlineAt())
                .questions(questionItems)
                .build();
    }

    private SubmissionDetailResponse buildDetailResponse(Submission sub, List<Answer> answers) {
        List<SubmissionDetailResponse.AnswerDetail> answerDetails = answers.stream()
                .map(a -> SubmissionDetailResponse.AnswerDetail.builder()
                        .questionId(a.getQuestionId())
                        .orderIndex(a.getOrderIndex())
                        .selectedOption(a.getSelectedOption())
                        .isCorrect(sub.getStatus() == SubmissionStatus.SUBMITTED ? a.getIsCorrect() : null)
                        .build())
                .collect(Collectors.toList());

        return SubmissionDetailResponse.builder()
                .id(sub.getId())
                .examId(sub.getExamId())
                .studentId(sub.getStudentId())
                .status(sub.getStatus().name())
                .score(sub.getScore())
                .correctCount(sub.getCorrectCount())
                .totalQuestions(sub.getTotalQuestions())
                .startedAt(sub.getStartedAt())
                .deadlineAt(sub.getDeadlineAt())
                .submittedAt(sub.getSubmittedAt())
                .answers(answerDetails)
                .build();
    }

    private SubmitResponse buildSubmitResponse(
            Submission sub,
            GradingService.GradingResult result,
            List<QuestionDto> questions) {

        Map<String, String> correctAnswerMap = questions.stream()
                .collect(Collectors.toMap(QuestionDto::getQuestionId, QuestionDto::getCorrectAnswer));

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
}
