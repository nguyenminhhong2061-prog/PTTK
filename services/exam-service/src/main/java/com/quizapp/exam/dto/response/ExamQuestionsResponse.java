package com.quizapp.exam.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quizapp.exam.enums.AnswerOption;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestionsResponse {
    private Long examId;
    private int totalQuestions;
    private List<Item> questions;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private Long questionId;
        private Integer orderIndex;
        private String content;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private AnswerOption correctAnswer; // null when includeAnswers=false
    }
}
