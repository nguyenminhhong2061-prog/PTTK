package com.quizapp.statistics.dto.event;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSubmittedEvent {

    @NotBlank
    private String eventId;

    @NotBlank
    private String eventType;

    @NotNull
    private Integer eventVersion;

    @NotNull
    private Instant occurredAt;

    @NotBlank
    private String submissionId;

    @NotNull
    private Long examId;

    @NotBlank
    private String studentId;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double score;
}
