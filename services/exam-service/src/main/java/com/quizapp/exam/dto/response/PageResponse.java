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
public class PageResponse<T> {
    private int page; // 1-based
    private int limit;
    private long totalItems;
    private int totalPages;
    private List<T> items;
}

