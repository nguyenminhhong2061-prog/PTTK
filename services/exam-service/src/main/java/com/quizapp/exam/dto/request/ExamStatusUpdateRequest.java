package com.quizapp.exam.dto.request;

import com.quizapp.exam.enums.ExamStatus;
import jakarta.validation.constraints.NotNull;
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
public class ExamStatusUpdateRequest {

    @NotNull
    private ExamStatus status;
}

