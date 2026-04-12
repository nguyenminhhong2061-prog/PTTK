package com.quizapp.exam.service;

import com.quizapp.exam.dto.request.QuestionCreateRequest;
import com.quizapp.exam.dto.response.QuestionListResponse;
import com.quizapp.exam.dto.response.QuestionResponse;
import com.quizapp.exam.entity.Question;
import com.quizapp.exam.exception.ConflictException;
import com.quizapp.exam.exception.NotFoundException;
import com.quizapp.exam.repository.ExamQuestionRepository;
import com.quizapp.exam.repository.QuestionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final ExamQuestionRepository examQuestionRepository;

    public QuestionService(QuestionRepository questionRepository, ExamQuestionRepository examQuestionRepository) {
        this.questionRepository = questionRepository;
        this.examQuestionRepository = examQuestionRepository;
    }

    @Transactional
    public QuestionResponse create(QuestionCreateRequest req) {
        Question q = Question.builder()
                .content(req.getContent())
                .optionA(req.getOptionA())
                .optionB(req.getOptionB())
                .optionC(req.getOptionC())
                .optionD(req.getOptionD())
                .correctAnswer(req.getCorrectAnswer())
                .createdBy(req.getCreatedBy())
                .build();
        q = questionRepository.saveAndFlush(q);
        return toResponse(q, true);
    }

    @Transactional(readOnly = true)
    public QuestionListResponse list(String createdBy, int page1Based, int limit) {
        int page0 = Math.max(0, page1Based - 1);
        int size = Math.min(Math.max(limit, 1), 100);
        PageRequest pr = PageRequest.of(page0, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Question> page = (createdBy == null || createdBy.isBlank())
                ? questionRepository.findAll(pr)
                : questionRepository.findByCreatedBy(createdBy, pr);

        return QuestionListResponse.builder()
                .total(page.getTotalElements())
                .page(page0 + 1)
                .limit(size)
                .data(page.map(q -> toResponse(q, true)).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public QuestionResponse getById(Long id) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Question not found with ID: " + id));
        return toResponse(q, true);
    }

    @Transactional
    public QuestionResponse update(Long id, QuestionCreateRequest req) {
        Question q = questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Question not found with ID: " + id));

        q.setContent(req.getContent());
        q.setOptionA(req.getOptionA());
        q.setOptionB(req.getOptionB());
        q.setOptionC(req.getOptionC());
        q.setOptionD(req.getOptionD());
        q.setCorrectAnswer(req.getCorrectAnswer());
        q.setCreatedBy(req.getCreatedBy());
        q = questionRepository.saveAndFlush(q);
        return toResponse(q, true);
    }

    @Transactional
    public void delete(Long id) {
        if (!questionRepository.existsById(id)) {
            throw new NotFoundException("Question not found with ID: " + id);
        }
        if (examQuestionRepository.existsInNonDraftExam(id)) {
            throw new ConflictException("Cannot delete a question used by a published or closed exam");
        }
        examQuestionRepository.deleteDraftMappingsByQuestionId(id);
        questionRepository.deleteById(id);
    }

    static QuestionResponse toResponse(Question q, boolean includeCorrectAnswer) {
        return QuestionResponse.builder()
                .id(q.getId())
                .content(q.getContent())
                .optionA(q.getOptionA())
                .optionB(q.getOptionB())
                .optionC(q.getOptionC())
                .optionD(q.getOptionD())
                .correctAnswer(includeCorrectAnswer ? q.getCorrectAnswer() : null)
                .createdBy(q.getCreatedBy())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }
}
