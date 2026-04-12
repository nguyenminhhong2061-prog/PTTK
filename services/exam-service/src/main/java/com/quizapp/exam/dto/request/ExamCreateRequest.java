package com.quizapp.exam.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ExamCreateRequest {

    @NotBlank
    @Size(max = 255)
    @Size(min = 5)
    private String title;

    private String description;

    @NotNull
    @Min(1)
    @Max(300)
    private Integer durationMinutes;

    @NotNull
    @Size(min = 1)
    private List<@NotNull @Min(1) @Max(9999999999L) Long> questionIds;

    @NotBlank
    @Size(max = 100)
    private String createdBy;
}
