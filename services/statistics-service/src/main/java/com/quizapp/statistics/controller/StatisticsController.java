package com.quizapp.statistics.controller;

import com.quizapp.statistics.dto.response.ApiResponse;
import com.quizapp.statistics.dto.response.ExamStatisticsResponse;
import com.quizapp.statistics.dto.response.QuestionStatisticsResponse;
import com.quizapp.statistics.dto.response.StudentScoreListResponse;
import com.quizapp.statistics.service.StatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/exams/{examId}")
    public ResponseEntity<ApiResponse<ExamStatisticsResponse>> getExamStatistics(
            @PathVariable String examId) {
        try {
            ExamStatisticsResponse data = statisticsService.getOverview(examId);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(ApiResponse.error("UPSTREAM_UNAVAILABLE", "Không thể lấy dữ liệu phân tích: " + e.getMessage()));
        }
    }

    @GetMapping("/exams/{examId}/questions")
    public ResponseEntity<ApiResponse<QuestionStatisticsResponse>> getQuestionStatistics(
            @PathVariable String examId) {
        try {
            QuestionStatisticsResponse data = statisticsService.getQuestionStats(examId);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(ApiResponse.error("UPSTREAM_UNAVAILABLE", "Không thể lấy phân tích câu hỏi: " + e.getMessage()));
        }
    }

    @GetMapping("/exams/{examId}/students")
    public ResponseEntity<ApiResponse<StudentScoreListResponse>> getStudentLeaderboard(
            @PathVariable String examId,
            @RequestParam(defaultValue = "score") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            StudentScoreListResponse data = statisticsService.getStudentLeaderboard(examId, sortBy, sortOrder, page, limit);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(ApiResponse.error("UPSTREAM_UNAVAILABLE", "Không thể lấy bảng điểm: " + e.getMessage()));
        }
    }
}
