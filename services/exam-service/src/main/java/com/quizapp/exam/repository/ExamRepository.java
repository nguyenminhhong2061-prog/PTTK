package com.quizapp.exam.repository;

import com.quizapp.exam.entity.Exam;
import com.quizapp.exam.enums.ExamStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Page<Exam> findByCreatedBy(String createdBy, Pageable pageable);

    Page<Exam> findByStatus(ExamStatus status, Pageable pageable);

    Page<Exam> findByCreatedByAndStatus(String createdBy, ExamStatus status, Pageable pageable);
}
