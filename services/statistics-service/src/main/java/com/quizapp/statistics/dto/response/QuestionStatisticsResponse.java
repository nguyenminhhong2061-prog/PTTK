package com.quizapp.statistics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionStatisticsResponse {
    private String examId;
    private String examTitle;
    private Integer totalSubmitted;
    private List<QuestionStatItem> questions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionStatItem {
        private String questionId;
        private Integer orderIndex;
        private String content; // Có thể bỏ nếu submission service không trả về content
        private String correctAnswer;
        private Double correctRate;
        private Double incorrectRate;
        private Double skipRate;
        private String difficulty; // easy, medium, hard
        private OptionDistribution optionDistribution;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionDistribution {
        @Builder.Default private Integer A = 0;
        @Builder.Default private Integer B = 0;
        @Builder.Default private Integer C = 0;
        @Builder.Default private Integer D = 0;
        @Builder.Default private Integer skipped = 0;
    }
}
