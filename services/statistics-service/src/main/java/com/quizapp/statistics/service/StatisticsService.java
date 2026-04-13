package com.quizapp.statistics.service;

import com.quizapp.statistics.client.SubmissionServiceClient;
import com.quizapp.statistics.dto.AnswerDto;
import com.quizapp.statistics.dto.SubmissionDto;
import com.quizapp.statistics.dto.response.ExamStatisticsResponse;
import com.quizapp.statistics.dto.response.QuestionStatisticsResponse;
import com.quizapp.statistics.dto.response.StudentScoreListResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private final SubmissionServiceClient client;

    public StatisticsService(SubmissionServiceClient client) {
        this.client = client;
    }

    public ExamStatisticsResponse getOverview(String examId) {
        List<SubmissionDto> submissions = client.getSubmittedByExam(examId);

        int totalSubmitted = submissions.size();
        if (totalSubmitted == 0) {
            return ExamStatisticsResponse.builder()
                    .examId(examId)
                    .totalParticipants(0)
                    .totalSubmitted(0)
                    .averageScore(0.0)
                    .highestScore(0.0)
                    .lowestScore(0.0)
                    .passRate(0.0)
                    .passCount(0L)
                    .failCount(0L)
                    .scoreDistribution(Collections.emptyList())
                    .averageDurationMinutes(0.0)
                    .generatedAt(LocalDateTime.now(ZoneId.of("UTC")))
                    .build();
        }

        double avgScore = submissions.stream().mapToDouble(s -> s.getScore() != null ? s.getScore() : 0.0).average().orElse(0);
        double maxScore = submissions.stream().mapToDouble(s -> s.getScore() != null ? s.getScore() : 0.0).max().orElse(0);
        double minScore = submissions.stream().mapToDouble(s -> s.getScore() != null ? s.getScore() : 0.0).min().orElse(0);

        long passCount = submissions.stream().filter(s -> s.getScore() != null && s.getScore() >= 50.0).count();
        double passRate = (double) passCount / totalSubmitted * 100;
        long failCount = totalSubmitted - passCount;

        double avgDuration = submissions.stream()
                .filter(s -> s.getSubmittedAt() != null && s.getStartedAt() != null)
                .mapToLong(s -> ChronoUnit.MINUTES.between(s.getStartedAt(), s.getSubmittedAt()))
                .average().orElse(0);

        // Score Distribution
        int[] counts = new int[5]; // 0-20, 21-40, 41-60, 61-80, 81-100
        for (SubmissionDto s : submissions) {
            double score = s.getScore() != null ? s.getScore() : 0.0;
            if (score <= 20) counts[0]++;
            else if (score <= 40) counts[1]++;
            else if (score <= 60) counts[2]++;
            else if (score <= 80) counts[3]++;
            else counts[4]++;
        }

        List<ExamStatisticsResponse.ScoreRange> distribution = Arrays.asList(
                new ExamStatisticsResponse.ScoreRange("0-20", counts[0], (double) counts[0] / totalSubmitted * 100),
                new ExamStatisticsResponse.ScoreRange("21-40", counts[1], (double) counts[1] / totalSubmitted * 100),
                new ExamStatisticsResponse.ScoreRange("41-60", counts[2], (double) counts[2] / totalSubmitted * 100),
                new ExamStatisticsResponse.ScoreRange("61-80", counts[3], (double) counts[3] / totalSubmitted * 100),
                new ExamStatisticsResponse.ScoreRange("81-100", counts[4], (double) counts[4] / totalSubmitted * 100)
        );

        return ExamStatisticsResponse.builder()
                .examId(examId)
                .totalParticipants(totalSubmitted) // Mocked total participants from the total amount of submissions
                .totalSubmitted(totalSubmitted)
                .averageScore(Math.round(avgScore * 100.0) / 100.0)
                .highestScore(maxScore)
                .lowestScore(minScore)
                .passRate(Math.round(passRate * 100.0) / 100.0)
                .passCount(passCount)
                .failCount(failCount)
                .scoreDistribution(distribution)
                .averageDurationMinutes(Math.round(avgDuration * 100.0) / 100.0)
                .generatedAt(LocalDateTime.now(ZoneId.of("UTC")))
                .build();
    }

    public QuestionStatisticsResponse getQuestionStats(String examId) {
        List<SubmissionDto> submissions = client.getSubmittedByExamWithAnswers(examId);
        int totalSubmitted = submissions.size();
        
        if (totalSubmitted == 0) {
            return QuestionStatisticsResponse.builder()
                    .examId(examId)
                    .totalSubmitted(0)
                    .questions(Collections.emptyList())
                    .build();
        }

        List<AnswerDto> allAnswers = submissions.stream()
                .filter(s -> s.getAnswers() != null)
                .flatMap(s -> s.getAnswers().stream())
                .collect(Collectors.toList());

        Map<String, List<AnswerDto>> groupedByQuestion = allAnswers.stream()
                .collect(Collectors.groupingBy(AnswerDto::getQuestionId));

        List<QuestionStatisticsResponse.QuestionStatItem> questionStats = groupedByQuestion.entrySet().stream().map(entry -> {
            String questionId = entry.getKey();
            List<AnswerDto> answers = entry.getValue();

            long correctCount = answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
            long skipCount = answers.stream().filter(a -> a.getSelectedOption() == null || a.getSelectedOption().isEmpty() || a.getSelectedOption().equals("SKIPPED")).count();
            long incorrectCount = answers.size() - correctCount - skipCount;

            double correctRate = (double) correctCount / totalSubmitted * 100;
            double incorrectRate = (double) incorrectCount / totalSubmitted * 100;
            double skipRate = (double) skipCount / totalSubmitted * 100;

            String difficulty = "medium";
            if (correctRate >= 70) difficulty = "easy";
            else if (correctRate < 40) difficulty = "hard";

            // Count distribution
            QuestionStatisticsResponse.OptionDistribution dist = new QuestionStatisticsResponse.OptionDistribution();
            for (AnswerDto a : answers) {
                String opt = a.getSelectedOption();
                if (opt == null || opt.isEmpty() || opt.equals("SKIPPED")) dist.setSkipped(dist.getSkipped() + 1);
                else {
                    switch (opt) {
                        case "A": dist.setA(dist.getA() + 1); break;
                        case "B": dist.setB(dist.getB() + 1); break;
                        case "C": dist.setC(dist.getC() + 1); break;
                        case "D": dist.setD(dist.getD() + 1); break;
                        default: dist.setSkipped(dist.getSkipped() + 1); break; // fallback
                    }
                }
            }

            // Get correctAnswer from the first answer available
            String correctAnswer = answers.isEmpty() ? "" : answers.get(0).getCorrectAnswer();
            Integer orderIndex = answers.isEmpty() ? 0 : answers.get(0).getOrderIndex();

            return QuestionStatisticsResponse.QuestionStatItem.builder()
                    .questionId(questionId)
                    .orderIndex(orderIndex)
                    .content("Question " + orderIndex) // Placeholder since content is from ExamService
                    .correctAnswer(correctAnswer)
                    .correctRate(Math.round(correctRate * 100.0) / 100.0)
                    .incorrectRate(Math.round(incorrectRate * 100.0) / 100.0)
                    .skipRate(Math.round(skipRate * 100.0) / 100.0)
                    .difficulty(difficulty)
                    .optionDistribution(dist)
                    .build();
        }).sorted(Comparator.comparingInt(q -> q.getOrderIndex() != null ? q.getOrderIndex() : 0))
        .collect(Collectors.toList());

        return QuestionStatisticsResponse.builder()
                .examId(examId)
                .totalSubmitted(totalSubmitted)
                .questions(questionStats)
                .build();
    }

    public StudentScoreListResponse getStudentLeaderboard(String examId, String sortBy, String sortOrder, int page, int limit) {
        List<SubmissionDto> submissions = client.getSubmittedByExam(examId);
        int totalSubmitted = submissions.size();
        
        List<StudentScoreListResponse.StudentScoreItem> students = submissions.stream().map(s -> {
            double score = s.getScore() != null ? s.getScore() : 0.0;
            double durationMinutes = 0;
            if (s.getStartedAt() != null && s.getSubmittedAt() != null) {
                durationMinutes = ChronoUnit.MINUTES.between(s.getStartedAt(), s.getSubmittedAt());
            }

            return StudentScoreListResponse.StudentScoreItem.builder()
                    .rank(0) // Will be updated later
                    .studentId(s.getStudentId())
                    .score(score)
                    .correctCount(s.getCorrectCount() != null ? s.getCorrectCount() : 0)
                    .totalQuestions(s.getTotalQuestions() != null ? s.getTotalQuestions() : 0)
                    .passed(score >= 50.0)
                    .durationMinutes(durationMinutes)
                    .submittedAt(s.getSubmittedAt())
                    .build();
        }).collect(Collectors.toList());

        // Sorting
        Comparator<StudentScoreListResponse.StudentScoreItem> comparator;
        if ("submittedAt".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                StudentScoreListResponse.StudentScoreItem::getSubmittedAt, 
                Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else if ("studentId".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                StudentScoreListResponse.StudentScoreItem::getStudentId, 
                Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else {
            // default is score
            comparator = Comparator.comparing(StudentScoreListResponse.StudentScoreItem::getScore);
        }

        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }

        students.sort(comparator);

        // Assign Rank (based on score usually, but let's just 1-index them after sort)
        for (int i = 0; i < students.size(); i++) {
            students.get(i).setRank(i + 1);
        }

        // Pagination
        int start = (page - 1) * limit;
        int end = Math.min(start + limit, students.size());
        
        List<StudentScoreListResponse.StudentScoreItem> pagedStudents;
        if (start < students.size()) {
            pagedStudents = students.subList(start, end);
        } else {
            pagedStudents = Collections.emptyList();
        }

        return StudentScoreListResponse.builder()
                .examId(examId)
                .totalSubmitted(totalSubmitted)
                .page(page)
                .limit(limit)
                .students(pagedStudents)
                .build();
    }
}
