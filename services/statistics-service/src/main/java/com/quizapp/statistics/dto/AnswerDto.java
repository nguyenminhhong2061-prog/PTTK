package com.quizapp.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerDto {
    private String questionId;
    private Integer orderIndex;
    private String selectedOption;
    private String correctAnswer;
    private Boolean isCorrect;
}
