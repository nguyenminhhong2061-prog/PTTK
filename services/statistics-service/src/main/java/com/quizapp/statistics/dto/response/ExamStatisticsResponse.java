package com.quizapp.statistics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamStatisticsResponse {
    private String examId;
    private String examTitle;
    private Integer totalParticipants;
    private Integer totalSubmitted;
    private Double averageScore;
    private Double highestScore;
    private Double lowestScore;
    private Double passRate;
    private Long passCount;
    private Long failCount;
    private List<ScoreRange> scoreDistribution;
    private Double averageDurationMinutes;
    private LocalDateTime generatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScoreRange {
        private String range;
        private Integer count;
        private Double percentage;
    }
}
