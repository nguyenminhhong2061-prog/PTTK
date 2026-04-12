package com.quizapp.exam.controller;

import com.quizapp.exam.dto.request.ExamCreateRequest;
import com.quizapp.exam.dto.request.ExamStatusUpdateRequest;
import com.quizapp.exam.dto.response.ExamListResponse;
import com.quizapp.exam.dto.response.ExamQuestionsResponse;
import com.quizapp.exam.dto.response.ExamResponse;
import com.quizapp.exam.dto.response.ExamStatusUpdateResponse;
import com.quizapp.exam.enums.ExamStatus;
import com.quizapp.exam.service.ExamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @GetMapping
    public ExamListResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) ExamStatus status
    ) {
        return examService.list(createdBy, status, page, limit);
    }

    @PostMapping
    public ResponseEntity<ExamResponse> create(@Valid @RequestBody ExamCreateRequest req) {
        ExamResponse created = examService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{examId}")
    public ExamResponse getById(@PathVariable Long examId) {
        return examService.getById(examId);
    }

    @PutMapping("/{examId}")
    public ExamResponse update(@PathVariable Long examId, @Valid @RequestBody ExamCreateRequest req) {
        return examService.update(examId, req);
    }

    @PatchMapping("/{examId}/status")
    public ExamStatusUpdateResponse updateStatus(
            @PathVariable Long examId,
            @Valid @RequestBody ExamStatusUpdateRequest req
    ) {
        return examService.updateStatus(examId, req.getStatus());
    }

    @GetMapping("/{examId}/questions")
    public ExamQuestionsResponse getQuestions(
            @PathVariable Long examId,
            @RequestParam(defaultValue = "false") boolean includeAnswers
    ) {
        return examService.getQuestions(examId, includeAnswers);
    }
}
