package com.quizapp.exam.dto.response;

import com.quizapp.exam.enums.AnswerOption;
import java.time.LocalDateTime;
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
public class QuestionResponse {
    private Long id;
    private String content;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private AnswerOption correctAnswer;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
