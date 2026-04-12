package com.quizapp.exam.repository;

import com.quizapp.exam.entity.ExamQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    @Query("""
            select eq
            from ExamQuestion eq
            join fetch eq.question q
            where eq.exam.id = :examId
            order by eq.orderIndex asc
            """)
    List<ExamQuestion> findByExamIdWithQuestionOrderByOrderIndexAsc(@Param("examId") Long examId);

    long countByExam_Id(Long examId);

    boolean existsByQuestion_Id(Long questionId);

    void deleteByExam_Id(Long examId);

    @Query("""
            select (count(eq) > 0)
            from ExamQuestion eq
            where eq.question.id = :questionId
              and eq.exam.status = com.quizapp.exam.enums.ExamStatus.PUBLISHED
            """)
    boolean existsInPublishedExam(@Param("questionId") Long questionId);

    @Query("""
            select (count(eq) > 0)
            from ExamQuestion eq
            where eq.question.id = :questionId
              and eq.exam.status <> com.quizapp.exam.enums.ExamStatus.DRAFT
            """)
    boolean existsInNonDraftExam(@Param("questionId") Long questionId);

    @Modifying
    @Query("""
            delete from ExamQuestion eq
            where eq.question.id = :questionId
              and eq.exam.status = com.quizapp.exam.enums.ExamStatus.DRAFT
            """)
    void deleteDraftMappingsByQuestionId(@Param("questionId") Long questionId);

    @Query("""
            select eq.exam.id, count(eq)
            from ExamQuestion eq
            where eq.exam.id in :examIds
            group by eq.exam.id
            """)
    List<Object[]> countQuestionsByExamIds(@Param("examIds") List<Long> examIds);
}
