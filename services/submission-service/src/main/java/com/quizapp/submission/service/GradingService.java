package com.quizapp.submission.service;

import com.quizapp.submission.client.ExamServiceClient.QuestionDto;
import com.quizapp.submission.entity.Answer;
import com.quizapp.submission.enums.AnswerOption;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service chịu trách nhiệm chấm điểm tự động.
 * So sánh đáp án học sinh với đáp án đúng từ Exam Service.
 */
@Service
public class GradingService {

    /**
     * Chấm điểm toàn bộ bài làm.
     *
     * @param studentAnswers Danh sách đáp án học sinh (từ DB)
     * @param correctQuestions Danh sách câu hỏi có đáp án đúng (từ Exam Service)
     * @return GradingResult chứa điểm, số câu đúng và danh sách Answer đã được cập nhật isCorrect
     */
    public GradingResult grade(List<Answer> studentAnswers, List<QuestionDto> correctQuestions) {
        // Map questionId → correctAnswer (A/B/C/D)
        Map<String, String> correctAnswerMap = correctQuestions.stream()
                .collect(Collectors.toMap(QuestionDto::getQuestionId, QuestionDto::getCorrectAnswer));

        int correctCount = 0;
        for (Answer answer : studentAnswers) {
            String correctAnswer = correctAnswerMap.get(answer.getQuestionId());
            if (correctAnswer == null) continue;

            boolean isCorrect = answer.getSelectedOption() != null
                    && answer.getSelectedOption().name().equalsIgnoreCase(correctAnswer);

            answer.setIsCorrect(isCorrect);
            if (isCorrect) correctCount++;
        }

        int total = correctQuestions.size();
        double score = total > 0 ? ((double) correctCount / total) * 100.0 : 0.0;
        // Làm tròn 1 chữ số thập phân
        score = Math.round(score * 10.0) / 10.0;

        return new GradingResult(score, correctCount, studentAnswers);
    }

    /**
     * Kết quả chấm điểm.
     */
    @Data
    public static class GradingResult {
        private final double score;
        private final int correctCount;
        private final List<Answer> gradedAnswers; // answers đã có isCorrect được set
    }
}
