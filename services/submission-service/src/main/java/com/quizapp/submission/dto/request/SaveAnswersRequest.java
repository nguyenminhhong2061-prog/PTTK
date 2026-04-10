package com.quizapp.submission.dto.request;

import com.quizapp.submission.enums.AnswerOption;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request body khi học sinh lưu đáp án (PUT /submissions/{id}/answers).
 * Có thể gửi toàn bộ hoặc từng phần đáp án.
 */
@Data
public class SaveAnswersRequest {

    @NotNull(message = "Danh sách đáp án không được null")
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        @NotNull(message = "questionId không được để trống")
        private String questionId;

        /** null = bỏ qua câu này */
        private AnswerOption selectedOption;
    }
}
