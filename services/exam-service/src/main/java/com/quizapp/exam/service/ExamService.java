package com.quizapp.exam.service;

import com.quizapp.exam.dto.request.ExamCreateRequest;
import com.quizapp.exam.dto.response.ExamQuestionsResponse;
import com.quizapp.exam.dto.response.ExamListResponse;
import com.quizapp.exam.dto.response.ExamResponse;
import com.quizapp.exam.dto.response.ExamStatusUpdateResponse;
import com.quizapp.exam.entity.Exam;
import com.quizapp.exam.entity.ExamQuestion;
import com.quizapp.exam.entity.Question;
import com.quizapp.exam.enums.ExamStatus;
import com.quizapp.exam.exception.BadRequestException;
import com.quizapp.exam.exception.ConflictException;
import com.quizapp.exam.exception.NotFoundException;
import com.quizapp.exam.repository.ExamQuestionRepository;
import com.quizapp.exam.repository.ExamRepository;
import com.quizapp.exam.repository.QuestionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final ExamQuestionRepository examQuestionRepository;

    public ExamService(
            ExamRepository examRepository,
            QuestionRepository questionRepository,
            ExamQuestionRepository examQuestionRepository
    ) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.examQuestionRepository = examQuestionRepository;
    }

    @Transactional
    public ExamResponse create(ExamCreateRequest req) {
        Exam exam = Exam.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .durationMinutes(req.getDurationMinutes())
                .status(ExamStatus.DRAFT)
                .createdBy(req.getCreatedBy())
                .build();

        List<Question> questionsInOrder = loadQuestionsInOrder(req.getQuestionIds());
        attachQuestions(exam, questionsInOrder);

        exam = examRepository.saveAndFlush(exam);
        return toResponse(exam, questionsInOrder.size());
    }

    @Transactional(readOnly = true)
    public ExamListResponse list(String createdBy, ExamStatus status, int page1Based, int limit) {
        int page0 = Math.max(0, page1Based - 1);
        int size = Math.min(Math.max(limit, 1), 100);
        PageRequest pr = PageRequest.of(page0, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Exam> page;
        boolean hasCreatedBy = createdBy != null && !createdBy.isBlank();
        if (hasCreatedBy && status != null) {
            page = examRepository.findByCreatedByAndStatus(createdBy, status, pr);
        } else if (hasCreatedBy) {
            page = examRepository.findByCreatedBy(createdBy, pr);
        } else if (status != null) {
            page = examRepository.findByStatus(status, pr);
        } else {
            page = examRepository.findAll(pr);
        }

        List<Long> examIds = page.getContent().stream().map(Exam::getId).toList();
        Map<Long, Long> counts = countQuestionsByExamIds(examIds);

        return ExamListResponse.builder()
                .total(page.getTotalElements())
                .page(page0 + 1)
                .limit(size)
                .data(page.getContent().stream()
                        .map(e -> toResponse(e, counts.getOrDefault(e.getId(), 0L).intValue()))
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public ExamResponse getById(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bài thi với ID: " + examId));
        int totalQuestions = (int) examQuestionRepository.countByExam_Id(examId);
        return toResponse(exam, totalQuestions);
    }

    @Transactional
    public ExamResponse update(Long examId, ExamCreateRequest req) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bài thi với ID: " + examId));
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new ConflictException("Không thể sửa bài thi đã công bố");
        }

        exam.setTitle(req.getTitle());
        exam.setDescription(req.getDescription());
        exam.setDurationMinutes(req.getDurationMinutes());
        exam.setCreatedBy(req.getCreatedBy());

        // Replace the question list in a deterministic order (as provided by client).
        // Flush removals first so MySQL unique constraints do not conflict with re-inserts.
        exam.getExamQuestions().clear();
        examRepository.saveAndFlush(exam);

        List<Question> questionsInOrder = loadQuestionsInOrder(req.getQuestionIds());
        attachQuestions(exam, questionsInOrder);

        exam = examRepository.saveAndFlush(exam);
        return toResponse(exam, questionsInOrder.size());
    }

    @Transactional
    public ExamStatusUpdateResponse updateStatus(Long examId, ExamStatus newStatus) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bài thi với ID: " + examId));

        ExamStatus current = exam.getStatus();
        if (newStatus == null) {
            throw new BadRequestException("status is required");
        }
        if (newStatus == ExamStatus.DRAFT) {
            throw new BadRequestException("Invalid status: draft");
        }
        if (current == ExamStatus.CLOSED) {
            throw new BadRequestException("Invalid status transition: closed -> " + newStatus.toJson());
        }
        if (current == newStatus) {
            throw new BadRequestException("Invalid status transition: " + current.toJson() + " -> " + newStatus.toJson());
        }

        if (current == ExamStatus.DRAFT && newStatus == ExamStatus.PUBLISHED) {
            long count = examQuestionRepository.countByExam_Id(examId);
            if (count < 1) {
                throw new BadRequestException("Cannot publish an exam with zero questions");
            }
        }

        // Allowed: DRAFT->PUBLISHED, PUBLISHED->CLOSED.
        if (current == ExamStatus.DRAFT && newStatus != ExamStatus.PUBLISHED) {
            throw new BadRequestException("Invalid status transition: draft -> " + newStatus.toJson());
        }
        if (current == ExamStatus.PUBLISHED && newStatus != ExamStatus.CLOSED) {
            throw new BadRequestException("Invalid status transition: published -> " + newStatus.toJson());
        }

        exam.setStatus(newStatus);
        exam = examRepository.saveAndFlush(exam);
        return ExamStatusUpdateResponse.builder()
                .examId(exam.getId())
                .status(exam.getStatus().toJson())
                .updatedAt(exam.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public ExamQuestionsResponse getQuestions(Long examId, boolean includeAnswers) {
        if (!examRepository.existsById(examId)) {
            throw new NotFoundException("Không tìm thấy bài thi với ID: " + examId);
        }

        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdWithQuestionOrderByOrderIndexAsc(examId);
        List<ExamQuestionsResponse.Item> items = new ArrayList<>(examQuestions.size());

        for (ExamQuestion eq : examQuestions) {
            Question q = eq.getQuestion();
            items.add(ExamQuestionsResponse.Item.builder()
                    .questionId(q.getId())
                    .orderIndex(eq.getOrderIndex())
                    .content(q.getContent())
                    .optionA(q.getOptionA())
                    .optionB(q.getOptionB())
                    .optionC(q.getOptionC())
                    .optionD(q.getOptionD())
                    .correctAnswer(includeAnswers ? q.getCorrectAnswer() : null)
                    .build());
        }

        return ExamQuestionsResponse.builder()
                .examId(examId)
                .totalQuestions(items.size())
                .questions(items)
                .build();
    }

    private void attachQuestions(Exam exam, List<Question> questionsInOrder) {
        int i = 0;
        for (Question q : questionsInOrder) {
            int orderIndex = i + 1; // 1-based to match API docs
            exam.getExamQuestions().add(ExamQuestion.builder()
                    .exam(exam)
                    .question(q)
                    .orderIndex(orderIndex)
                    .build());
            i++;
        }
    }

    private List<Question> loadQuestionsInOrder(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            throw new BadRequestException("questionIds is required");
        }

        // Client order defines orderIndex; reject duplicates to avoid silent behavior.
        Set<Long> orderedUniqueIds = new LinkedHashSet<>(questionIds);
        if (orderedUniqueIds.size() != questionIds.size()) {
            throw new BadRequestException("questionIds must not contain duplicates");
        }
        List<Question> found = questionRepository.findAllById(orderedUniqueIds);

        Map<Long, Question> byId = new HashMap<>(found.size());
        for (Question q : found) {
            byId.put(q.getId(), q);
        }

        List<String> missing = new ArrayList<>();
        List<Question> ordered = new ArrayList<>(orderedUniqueIds.size());
        for (Long id : orderedUniqueIds) {
            Question q = byId.get(id);
            if (q == null) {
                missing.add(String.valueOf(id));
            } else {
                ordered.add(q);
            }
        }

        if (!missing.isEmpty()) {
            throw new NotFoundException("Không tìm thấy câu hỏi với ID: " + String.join(", ", missing));
        }

        return ordered;
    }

    private Map<Long, Long> countQuestionsByExamIds(List<Long> examIds) {
        if (examIds == null || examIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : examQuestionRepository.countQuestionsByExamIds(examIds)) {
            Long examId = ((Number) row[0]).longValue();
            Long cnt = ((Number) row[1]).longValue();
            counts.put(examId, cnt);
        }
        return counts;
    }

    static ExamResponse toResponse(Exam exam, int totalQuestions) {
        return ExamResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .durationMinutes(exam.getDurationMinutes())
                .status(exam.getStatus())
                .createdBy(exam.getCreatedBy())
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .totalQuestions(totalQuestions)
                .build();
    }
}
