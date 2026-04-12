package com.quizapp.exam.repository;

import com.quizapp.exam.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    Page<Question> findByCreatedBy(String createdBy, Pageable pageable);
}
