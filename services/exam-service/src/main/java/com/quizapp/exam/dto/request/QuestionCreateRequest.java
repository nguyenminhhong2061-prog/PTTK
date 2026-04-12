package com.quizapp.exam.dto.request;

import com.quizapp.exam.enums.AnswerOption;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class QuestionCreateRequest {

    @NotBlank
    @Size(min = 10)
    private String content;

    @NotBlank
    @Size(max = 500)
    private String optionA;

    @NotBlank
    @Size(max = 500)
    private String optionB;

    @NotBlank
    @Size(max = 500)
    private String optionC;

    @NotBlank
    @Size(max = 500)
    private String optionD;

    @NotNull
    private AnswerOption correctAnswer;

    @NotBlank
    @Size(max = 100)
    private String createdBy;
}
