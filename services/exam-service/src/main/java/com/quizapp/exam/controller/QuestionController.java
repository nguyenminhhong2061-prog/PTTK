package com.quizapp.exam.controller;

import com.quizapp.exam.dto.request.QuestionCreateRequest;
import com.quizapp.exam.dto.response.QuestionListResponse;
import com.quizapp.exam.dto.response.QuestionResponse;
import com.quizapp.exam.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public QuestionListResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String createdBy
    ) {
        return questionService.list(createdBy, page, limit);
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> create(@Valid @RequestBody QuestionCreateRequest req) {
        QuestionResponse created = questionService.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{questionId}")
    public QuestionResponse getById(@PathVariable("questionId") Long questionId) {
        return questionService.getById(questionId);
    }

    @PutMapping("/{questionId}")
    public QuestionResponse update(
            @PathVariable("questionId") Long questionId,
            @Valid @RequestBody QuestionCreateRequest req
    ) {
        return questionService.update(questionId, req);
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(@PathVariable("questionId") Long questionId) {
        questionService.delete(questionId);
        return ResponseEntity.noContent().build();
    }
}
