package com.quizapp.exam.dto.response;

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
public class ExamStatusUpdateResponse {
    private Long examId;
    private String status;
    private LocalDateTime updatedAt;
}
