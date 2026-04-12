package com.quizapp.exam.dto.response;

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
public class QuestionListResponse {
    private long total;
    private int page;
    private int limit;
    private List<QuestionResponse> data;
}

