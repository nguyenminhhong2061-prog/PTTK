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
public class StudentScoreListResponse {
    private String examId;
    private String examTitle;
    private Integer totalSubmitted;
    private Integer page;
    private Integer limit;
    private List<StudentScoreItem> students;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentScoreItem {
        private Integer rank;
        private String studentId;
        private Double score;
        private Integer correctCount;
        private Integer totalQuestions;
        private Boolean passed;
        private Double durationMinutes;
        private LocalDateTime submittedAt;
    }
}
