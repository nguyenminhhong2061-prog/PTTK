package com.quizapp.submission.controller;

import com.quizapp.submission.dto.request.SaveAnswersRequest;
import com.quizapp.submission.dto.request.StartExamRequest;
import com.quizapp.submission.dto.response.ApiResponse;
import com.quizapp.submission.dto.response.SubmissionDetailResponse;
import com.quizapp.submission.dto.response.SubmissionStartResponse;
import com.quizapp.submission.dto.response.SubmitResponse;
import com.quizapp.submission.entity.Submission;
import com.quizapp.submission.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller cho Submission Service.
 * Base path: /submissions
 */
@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
@Slf4j
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * POST /submissions
     * Học sinh bắt đầu làm bài. Trả về 201 (mới) hoặc 200 (resume).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SubmissionStartResponse>> startExam(
            @Valid @RequestBody StartExamRequest request) {
        try {
            SubmissionStartResponse response = submissionService.startExam(request);
            // 201 nếu mới tạo, 200 nếu resume
            boolean isNew = response.getStartedAt().isAfter(LocalDateTime.now().minusSeconds(5));
            return ResponseEntity
                    .status(isNew ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(ApiResponse.success(response));
        } catch (IllegalStateException e) {
            // 409 Conflict — đã nộp bài
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            // 400 Bad Request — bài thi không hợp lệ
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            // 503 — không kết nối được Exam Service
            log.error("Lỗi khi bắt đầu làm bài: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /submissions
     * Lấy danh sách bài nộp. Filter theo studentId, examId, status.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Submission>>> listSubmissions(
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String examId,
            @RequestParam(required = false) String status) {
        List<Submission> submissions = submissionService.listSubmissions(studentId, examId, status);
        return ResponseEntity.ok(ApiResponse.success(submissions));
    }

    /**
     * GET /submissions/{submissionId}
     * Xem chi tiết một bài nộp.
     */
    @GetMapping("/{submissionId}")
    public ResponseEntity<ApiResponse<SubmissionDetailResponse>> getSubmission(
            @PathVariable String submissionId) {
        try {
            SubmissionDetailResponse response = submissionService.getById(submissionId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * PUT /submissions/{submissionId}/answers
     * Lưu đáp án tạm thời trong quá trình làm bài.
     */
    @PutMapping("/{submissionId}/answers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveAnswers(
            @PathVariable String submissionId,
            @Valid @RequestBody SaveAnswersRequest request) {
        try {
            int savedCount = submissionService.saveAnswers(submissionId, request);
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("message", "Đáp án đã được lưu tạm",
                           "savedCount", savedCount,
                           "savedAt", LocalDateTime.now().toString())));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /submissions/{submissionId}/submit
     * Nộp bài và chấm điểm tự động.
     */
    @PostMapping("/{submissionId}/submit")
    public ResponseEntity<ApiResponse<SubmitResponse>> submitExam(
            @PathVariable String submissionId,
            @RequestBody(required = false) SaveAnswersRequest lastAnswers) {
        try {
            SubmitResponse response = submissionService.submit(submissionId, lastAnswers);
            return ResponseEntity.ok(ApiResponse.success("Nộp bài thành công!", response));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Lỗi khi nộp bài {}: {}", submissionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
