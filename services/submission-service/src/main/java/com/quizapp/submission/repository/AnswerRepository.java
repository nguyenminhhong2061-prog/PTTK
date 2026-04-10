package com.quizapp.submission.repository;

import com.quizapp.submission.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, String> {

    /** Lấy tất cả đáp án của một bài nộp */
    List<Answer> findBySubmissionId(String submissionId);

    /** Lấy đáp án cho một câu hỏi cụ thể trong một bài nộp */
    java.util.Optional<Answer> findBySubmissionIdAndQuestionId(String submissionId, String questionId);
}
